package app.darek

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.darek.data.AppPreferences
import app.darek.fuchy.Fucha
import app.darek.fuchy.OwnFucha
import app.darek.listen.Listening
import app.darek.listen.hasMicrophone
import app.darek.listen.listeningNotificationBlock
import app.darek.speak.VoiceVolume
import app.darek.tip.TipJar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * `internal` rather than private: [MainApplication] holds the one [AppPreferences] over this store
 * and hands it to the container, because the theme is read from it before the first window exists.
 *
 * Kotlin note: `by preferencesDataStore(...)` is a property delegate on `Context` — it creates the
 * store once per process and hands the same instance to every caller. Creating a second DataStore
 * over the same file throws, so this must stay the only one.
 */
internal val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

internal fun Context.firstInstalledAt(): Instant {
    val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    return Instant.ofEpochMilli(info.firstInstallTime)
}

/**
 * Manual dependency injection — deliberately not Hilt.
 *
 * At the size a single-developer app reaches, constructing things by hand is clearer than a
 * generated graph: the whole object graph is this file, you can read it top to bottom, and there is
 * no annotation processor round trip in every build. Migrating to Hilt later is mechanical, because
 * everything here is already constructor injection.
 *
 * The rule that makes it work: **nothing constructs its own dependencies.** A ViewModel takes what it
 * needs through its constructor, and only this file calls a constructor.
 * The moment a class reaches for a singleton instead, it stops being testable without a device.
 *
 * Lives at the package root with the app shell: like `Navigation.kt`, it describes how the app hangs
 * together rather than belonging to any one screen.
 */
class AppContainer(
    context: Context,
    val preferences: AppPreferences,
    /** When this package was first installed on this phone, which the one-time markers are judged against. */
    val installedAt: Instant,
    private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /**
     * The tip (ADR-0007). Here rather than in a `ViewModel` because a purchase can finish after the
     * screen that opened Play's sheet is gone, and it still has to be consumed — see [TipJar].
     */
    val tipJar = TipJar(appContext, applicationScope)

    val ownFucha: Fucha = OwnFucha.load(appContext)

    /** Every fucha switched on: until fuchy can be installed or switched off, only Darek's own. */
    val fuchyOn: List<Fucha> = listOf(ownFucha)

    /** Here so the recogniser's model and the voice stay loaded between one *Słuchaj* and the next. */
    val listening =
        Listening(appContext, applicationScope, fuchyOn.flatMap { it.commands }) { VoiceVolume.get(appContext) }

    /** Stopped by the user, from Home, the notification or the tile, so no reboot asks again. */
    fun stopListening() {
        listening.stop()
        applicationScope.launch { preferences.setListeningWanted(null) }
    }

    /** Everything a start needs has been given, so a start from outside the app asks nothing. */
    suspend fun listeningStartable(): Boolean =
        preferences.backgroundDisclosedUnder.first() == installedAt &&
            appContext.hasMicrophone() &&
            appContext.listeningNotificationBlock() == null
}
