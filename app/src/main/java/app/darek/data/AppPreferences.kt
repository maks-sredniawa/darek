package app.darek.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.darek.listen.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Which colour scheme applies, regardless of what the phone is set to. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Everything the app remembers.
 *
 * **DataStore, not SharedPreferences.** SharedPreferences' `commit()` blocks and its `apply()` can
 * still write on the main thread at the wrong moment; DataStore is `suspend`-only and transactional,
 * so there is no API here that can stall a frame.
 *
 * Kotlin note: every read is a `Flow`, not a value. There is no "current value" to ask for
 * synchronously — the first emission arrives after a disk read. That matters at startup: a screen
 * collecting `themeMode` gets `SYSTEM` for one frame and the stored value on the next, which is a
 * visible flash. [themeModeNow] exists for the one caller that must have the answer *before* the
 * first frame, and it is `suspend` because reading a file is.
 *
 * ## Adding a preference
 *
 * A key, listed in `Keys.SETTINGS` or `Keys.KEPT`, a `Flow` that reads it with a default, and a
 * `suspend` setter. `AppPreferencesTest` fails until the key is in exactly one of the two lists, so
 * whoever adds it decides whether *Restore to default* resets it. Keep the default in the `Flow`
 * rather than writing it on first launch — a default that has been written to disk cannot be
 * changed in a later version without a migration, and a default that has not can.
 */
class AppPreferences(
    private val store: DataStore<Preferences>,
) {
    internal object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val TIP_ANSWERED_UNDER = longPreferencesKey("tip_answered_under")
        val LANGUAGE_JUDGED_UNDER = longPreferencesKey("language_judged_under")
        val MIC_DENIED_UNDER = longPreferencesKey("mic_denied_under")
        val PERSONA = stringPreferencesKey("persona")
        val LISTENING_WANTED_UNDER = longPreferencesKey("listening_wanted_under")
        val BACKGROUND_DISCLOSED_UNDER = longPreferencesKey("background_disclosed_under")
        val SETUP_SHOWN_UNDER = longPreferencesKey("setup_shown_under")

        /** What *Restore to default* removes: everything the user chose, so each reads its default again. */
        val SETTINGS: List<Preferences.Key<*>> =
            listOf(
                THEME_MODE,
                MATERIAL_YOU,
                PERSONA,
            )

        /**
         * What *Restore to default* leaves alone: live state, one-time asks, and anything pointing at
         * a grant the system holds, which a reset would orphan rather than undo.
         */
        val KEPT: List<Preferences.Key<*>> =
            listOf(
                TIP_ANSWERED_UNDER,
                LANGUAGE_JUDGED_UNDER,
                MIC_DENIED_UNDER,
                LISTENING_WANTED_UNDER,
                BACKGROUND_DISCLOSED_UNDER,
                SETUP_SHOWN_UNDER,
            )
    }

    /**
     * Light, dark or follow-the-system.
     *
     * Unknown stored values fall back to [ThemeMode.SYSTEM] rather than throwing: a preference file
     * survives a downgrade, and a crash loop at startup is a far worse outcome than the wrong theme.
     */
    val themeMode: Flow<ThemeMode> =
        store.data.map { prefs ->
            prefs[Keys.THEME_MODE]?.let { name ->
                runCatching { enumValueOf<ThemeMode>(name) }.getOrNull()
            } ?: ThemeMode.SYSTEM
        }

    /**
     * Whether to take the wallpaper's palette instead of the app's own (ADR-0006).
     *
     * Defaults **off**. With it on, nothing on Android 12+ reads the generated scheme at all, so the
     * app's identity would be invisible on almost every device that runs it.
     */
    val materialYou: Flow<Boolean> = store.data.map { it[Keys.MATERIAL_YOU] ?: false }

    /** Who answers (ADR-0016); an unknown stored name falls back to Darek, as [themeMode] does. */
    val persona: Flow<Persona> =
        store.data.map { prefs ->
            prefs[Keys.PERSONA]?.let { name ->
                runCatching { enumValueOf<Persona>(name) }.getOrNull()
            } ?: Persona.DAREK
        }

    /**
     * The `firstInstallTime` the home screen's one tip ask was answered under, either way (ADR-0007).
     * Auto Backup carries it to the user's next phone, where the install time differs, so
     * `tip/TipAsk.kt` counts it only on the install that gave it.
     */
    val tipAnsweredUnder: Flow<Instant?> =
        store.data.map { prefs ->
            prefs[Keys.TIP_ANSWERED_UNDER]?.let(Instant::ofEpochMilli)
        }

    /** The `firstInstallTime` whose first launch was last judged for the switch to Polish, switched or not (ADR-0012, `ui/settings/OpenInPolish.kt`). */
    val languageJudgedUnder: Flow<Instant?> =
        store.data.map { prefs ->
            prefs[Keys.LANGUAGE_JUDGED_UNDER]?.let(Instant::ofEpochMilli)
        }

    /**
     * The `firstInstallTime` under which the microphone was denied for good, so Home offers the
     * app's settings instead of a dialog the system will no longer show. A later launch cannot tell
     * this from the grant alone; it is cleared once the grant is found.
     */
    val micDeniedUnder: Flow<Instant?> =
        store.data.map { prefs ->
            prefs[Keys.MIC_DENIED_UNDER]?.let(Instant::ofEpochMilli)
        }

    /**
     * The `firstInstallTime` under which Listening was last started and not stopped by the user, so
     * the notification after a reboot offers *Słuchaj* only to someone who left Darek on
     * (`listen/AfterBoot.kt`). Under the install time so a restore onto a new phone does not ask.
     */
    val listeningWantedUnder: Flow<Instant?> = instantAt(Keys.LISTENING_WANTED_UNDER)

    /** The `firstInstallTime` under which the background microphone disclosure was accepted. */
    val backgroundDisclosedUnder: Flow<Instant?> = instantAt(Keys.BACKGROUND_DISCLOSED_UNDER)

    /** The `firstInstallTime` under which the background setup sheet was shown, once. */
    val setupShownUnder: Flow<Instant?> = instantAt(Keys.SETUP_SHOWN_UNDER)

    private fun instantAt(key: Preferences.Key<Long>): Flow<Instant?> =
        store.data.map { prefs -> prefs[key]?.let(Instant::ofEpochMilli) }

    /**
     * The theme mode, read once, before any Activity exists.
     *
     * The one place a `suspend` read is worth its cost: `MainApplication.onCreate` needs the answer
     * to call `applyThemeMode` *before* the first window is created, and a `Flow` collected in a
     * composition arrives a frame too late — which is a light flash on every cold start for a user
     * who chose dark.
     */
    suspend fun themeModeNow(): ThemeMode = themeMode.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPersona(persona: Persona) {
        store.edit { it[Keys.PERSONA] = persona.name }
    }

    suspend fun setMaterialYou(enabled: Boolean) {
        store.edit { it[Keys.MATERIAL_YOU] = enabled }
    }

    suspend fun markTipAnswered(installedAt: Instant) {
        store.edit { it[Keys.TIP_ANSWERED_UNDER] = installedAt.toEpochMilli() }
    }

    suspend fun setMicDenied(installedAt: Instant?) {
        store.edit { prefs ->
            if (installedAt ==
                null
            ) {
                prefs.remove(Keys.MIC_DENIED_UNDER)
            } else {
                prefs[Keys.MIC_DENIED_UNDER] = installedAt.toEpochMilli()
            }
        }
    }

    suspend fun setListeningWanted(installedAt: Instant?) = setInstant(Keys.LISTENING_WANTED_UNDER, installedAt)

    suspend fun markBackgroundDisclosed(installedAt: Instant) = setInstant(Keys.BACKGROUND_DISCLOSED_UNDER, installedAt)

    suspend fun markSetupShown(installedAt: Instant) = setInstant(Keys.SETUP_SHOWN_UNDER, installedAt)

    private suspend fun setInstant(
        key: Preferences.Key<Long>,
        at: Instant?,
    ) {
        store.edit { prefs -> if (at == null) prefs.remove(key) else prefs[key] = at.toEpochMilli() }
    }

    suspend fun markLanguageJudged(installedAt: Instant) {
        store.edit { it[Keys.LANGUAGE_JUDGED_UNDER] = installedAt.toEpochMilli() }
    }

    /**
     * Every setting back to how the app came, in one edit. The keys are removed rather than written,
     * so each reads its default again and stays free to change in a later version.
     */
    suspend fun restoreDefaults() {
        store.edit { prefs -> Keys.SETTINGS.forEach { prefs.remove(it) } }
    }
}
