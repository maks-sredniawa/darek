package app.darek

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.darek.data.AppPreferences
import app.darek.data.ThemeMode
import app.darek.theme.applyThemeMode
import app.darek.ui.settings.AppLanguage
import app.darek.ui.settings.launchLanguage
import app.darek.ui.settings.setAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Holds the one [AppContainer] for the process, applies the stored theme before any window exists,
 * and decides the first-launch language: below 13 it applies it here too, from 13 `MainActivity` does.
 *
 * Kotlin note: `by lazy` computes on first read and caches — like a memoised getter.
 */
class MainApplication : Application() {
    /**
     * The scope for work that outlives any screen. `SupervisorJob` so one failed child does not
     * cancel its siblings — the closest JS analogue is `Promise.allSettled` semantics rather than
     * `Promise.all`.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The preference store. Built in [onCreate], because the theme is read from it there. */
    lateinit var preferences: AppPreferences
        private set

    /**
     * The theme mode as it was on disk at process start, so the very first composition paints in the
     * right palette rather than flashing light and repainting.
     */
    var startupThemeMode: ThemeMode = ThemeMode.SYSTEM
        private set

    /** When this package was first installed on this phone, which the one-time markers are judged against. */
    lateinit var installedAt: Instant
        private set

    private var languageUnjudged = false

    private var languageSwitchPending = false

    val container: AppContainer by lazy { AppContainer(this, preferences, installedAt, applicationScope) }

    override fun onCreate() {
        super.onCreate()

        preferences = AppPreferences(preferencesStore)
        installedAt = firstInstalledAt()

        // A blocking wait, deliberately and exactly once. Everything else about DataStore is a Flow,
        // but the theme and the first-launch language have to be known before the first window is
        // created — a Flow collected in a composition arrives a frame later, which is a visible
        // flash of the wrong palette or language. One small file read at process start is the
        // cheaper of the two costs.
        val language =
            runBlocking {
                val language = async { launchLanguage(preferences, installedAt) }
                startupThemeMode = preferences.themeModeNow()
                language.await()
            }
        applyThemeMode(startupThemeMode)
        if (language.switchToPolish) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                languageSwitchPending = true
            } else {
                setAppLanguage(AppLanguage.POLISH)
            }
        }
        languageUnjudged = language.markJudged

        settleTipsOnEveryForeground()
    }

    /**
     * True once, on a launch from 13 whose switch to Polish is owed: from 13
     * `AppCompatDelegate.setApplicationLocales` reaches `LocaleManager` only through an open Activity,
     * so at process start it does nothing and `MainActivity` makes the switch.
     */
    fun takeLanguageSwitchPending(): Boolean = languageSwitchPending.also { languageSwitchPending = false }

    /**
     * Records that this install's first launch was judged, from `MainActivity.onCreate`. Below 33
     * AppCompat writes the switched locale to its file only when an Activity attaches, so a process
     * that never shows one leaves the marker stale and the launch is judged again next time.
     */
    fun markLanguageJudged() {
        if (!languageUnjudged) return
        languageUnjudged = false
        applicationScope.launch { preferences.markLanguageJudged(installedAt) }
    }

    /**
     * Play refunds a purchase nobody acknowledged within three days, so a tip that cleared while the
     * app was closed — or was paid just before a process death — is consumed the next time the user
     * opens the app, whichever screen that is (ADR-0007). On *start*, not at process creation: a
     * process the OS starts for anything else should not bind Play for nobody.
     */
    private fun settleTipsOnEveryForeground() {
        // Kotlin note: an `object :` expression is an anonymous class instance — the listener object
        // JS would pass inline. Added on the main thread, which `ProcessLifecycleOwner` requires.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    applicationScope.launch { container.tipJar.settlePending() }
                }
            },
        )
    }
}
