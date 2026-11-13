package app.darek.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.data.ThemeMode
import app.darek.listen.Persona
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.LinkRow
import app.darek.ui.common.LinkTarget
import app.darek.ui.common.SectionHeader
import app.darek.ui.common.SwitchRow
import app.darek.ui.support.AboutSection
import app.darek.ui.support.LanguageReportRow
import app.darek.work.hasAutostartSettings
import app.darek.work.isIgnoringBatteryOptimisations
import app.darek.work.openAutostartSettings
import app.darek.work.openBatteryOptimisationSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(modifier = contentModifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_persona))
            Row(modifier = Modifier.padding(horizontal = Spacing.base)) {
                for (persona in Persona.entries) {
                    FilterChip(
                        selected = state.persona == persona,
                        onClick = { viewModel.setPersona(persona) },
                        label = { Text(stringResource(persona.labelRes())) },
                        modifier = Modifier.padding(end = Spacing.tight),
                    )
                }
            }
            Text(
                stringResource(R.string.settings_persona_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.base),
            )

            SectionHeader(stringResource(R.string.settings_appearance))

            Row(modifier = Modifier.padding(horizontal = Spacing.base)) {
                for (mode in ThemeMode.entries) {
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(mode.labelRes())) },
                        modifier = Modifier.padding(end = Spacing.tight),
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.settings_material_you),
                subtitle = stringResource(R.string.settings_material_you_hint),
                checked = state.materialYou,
                onChange = viewModel::setMaterialYou,
            )

            SectionHeader(stringResource(R.string.settings_language))
            LanguageRow()
            LanguageReportRow()

            BackgroundRows()

            SectionHeader(stringResource(R.string.settings_data))
            RestoreDefaultsRow(onRestore = viewModel::restoreDefaults)

            AboutSection(onOpenSupport, onOpenLicences)

            // The developer-only section. In a release build this composable is a no-op that
            // renders nothing — see `src/release/…/DebugSettings.kt`. It is a source-set seam
            // rather than an `if (BuildConfig.DEBUG)` because with `isMinifyEnabled = false` a
            // statically-false branch is still compiled into the AAB, and its strings are still
            // inside the translation gate. A hide is not a strip.
            DebugSettings()
        }
    }
}

/**
 * Autostart on Xiaomi, permanently and with no claim about its state, since it cannot be read back;
 * the battery exemption on Xiaomi too, elsewhere only while it reads as missing (PLAN.md rule 4).
 */
@Composable
private fun BackgroundRows() {
    val context = LocalContext.current
    val xiaomi = remember { context.hasAutostartSettings() }
    var batteryMissing by remember { mutableStateOf(!context.isIgnoringBatteryOptimisations()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { batteryMissing = !context.isIgnoringBatteryOptimisations() }
    if (!xiaomi && !batteryMissing) return
    SectionHeader(stringResource(R.string.settings_background))
    if (xiaomi) {
        LinkRow(
            title = stringResource(R.string.setup_autostart),
            subtitle = stringResource(R.string.settings_autostart_hint),
            target = LinkTarget.Outside,
            onClick = { context.openAutostartSettings() },
        )
    }
    LinkRow(
        title = stringResource(R.string.setup_battery),
        subtitle = stringResource(R.string.settings_battery_hint),
        target = LinkTarget.Outside,
        onClick = { context.openBatteryOptimisationSettings() },
    )
}

@Composable
private fun LanguageRow() {
    // Read on every composition rather than held in state: `setAppLanguage` recreates the Activity,
    // so this composable is rebuilt from scratch and the fresh read is always correct.
    val current = currentAppLanguage()
    Row(modifier = Modifier.padding(horizontal = Spacing.base)) {
        FilterChip(
            selected = current == null,
            onClick = { setAppLanguage(null) },
            label = { Text(stringResource(R.string.settings_language_system)) },
            modifier = Modifier.padding(end = Spacing.tight),
        )
        for (language in AppLanguage.entries) {
            FilterChip(
                selected = current == language,
                onClick = { setAppLanguage(language) },
                label = { Text(stringResource(language.labelRes)) },
                modifier = Modifier.padding(end = Spacing.tight),
            )
        }
    }
}

/**
 * *Restore to default*, behind a confirmation because nothing brings the old values back. What it
 * resets and what it keeps is `AppPreferences.Keys.SETTINGS` and `KEPT`.
 */
@Composable
private fun RestoreDefaultsRow(onRestore: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    LinkRow(
        title = stringResource(R.string.settings_restore),
        subtitle = stringResource(R.string.settings_restore_hint),
        target = LinkTarget.Here,
        onClick = { confirming = true },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_restore_confirm_title)) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRestore()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun Persona.labelRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.persona_darek
        Persona.DARIA -> R.string.persona_daria
    }

private fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
