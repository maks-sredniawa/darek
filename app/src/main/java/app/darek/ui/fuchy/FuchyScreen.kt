package app.darek.ui.fuchy

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.fuchy.Fucha
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.CheckSwitch
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.LinkRow
import app.darek.ui.common.LinkTarget
import app.darek.ui.common.SectionHeader
import app.darek.ui.support.SupportConfig
import kotlinx.coroutines.launch

/** Darek's own fucha, then the installed ones, then the way to add one (phase-F.md, checkpoint C). */
@Composable
fun FuchyScreen(
    onBack: () -> Unit,
    onInstall: (Uri) -> Unit,
    onPickCity: (fuchaId: String, switchOn: Boolean) -> Unit,
    onOpenPage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FuchyViewModel = viewModel(factory = FuchyViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val context = LocalContext.current
    val noMailApp = stringResource(R.string.support_no_mail_app_write_to, SupportConfig.SUPPORT_ADDRESS)
    val report: (Fucha) -> Unit = { fucha ->
        if (!context.reportFucha(fucha)) scope.launch { snackbars.showSnackbar(noMailApp) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                is FuchyEvent.SwitchedOff ->
                    snackbars.showSnackbar(resources.getString(R.string.fuchy_switched_off, it.off.name, it.by.name))
                is FuchyEvent.Blocked ->
                    snackbars.showSnackbar(
                        resources.getString(
                            R.string.fuchy_blocked,
                            it.fucha.name,
                            resources.getString(it.permission.labelRes()),
                        ),
                    )
                is FuchyEvent.PickCity -> onPickCity(it.fucha.id, true)
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onInstall) }

    DetailScaffold(
        title = stringResource(R.string.fuchy_title),
        onBack = onBack,
        modifier = modifier,
        snackbars = snackbars,
    ) { contentModifier ->
        Column(modifier = contentModifier.verticalScroll(rememberScrollState())) {
            if (!state.loaded) return@Column
            state.own?.let {
                SectionHeader(stringResource(R.string.fuchy_own))
                FuchaRowView(
                    it,
                    onSwitch = viewModel::switch,
                    onRemove = null,
                    onPickCity = onPickCity,
                    onReport = report,
                )
            }
            SectionHeader(stringResource(R.string.fuchy_installed))
            if (state.installed.isEmpty()) {
                Text(
                    stringResource(R.string.fuchy_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.base),
                )
            }
            state.installed.forEach {
                FuchaRowView(
                    it,
                    onSwitch = viewModel::switch,
                    onRemove = viewModel::askRemove,
                    onPickCity = onPickCity,
                    onReport = report,
                )
            }
            SectionHeader(stringResource(R.string.fuchy_add))
            LinkRow(
                title = stringResource(R.string.page_title),
                subtitle = stringResource(R.string.page_hint),
                target = LinkTarget.Inside,
                onClick = onOpenPage,
            )
            LinkRow(
                title = stringResource(R.string.fuchy_from_file),
                subtitle = stringResource(R.string.fuchy_from_file_hint),
                target = LinkTarget.Outside,
                // A messenger or file manager hands over a generic type, so every file is offered (ADR-0015).
                onClick = { pick.launch(arrayOf("*/*")) },
            )
        }
    }

    state.override?.let { override ->
        val names =
            (listOfNotNull(state.own) + state.installed).associate {
                it.installed.fucha.id to
                    it.installed.fucha.name
            }
        AlertDialog(
            onDismissRequest = viewModel::dismissOverride,
            title = { Text(stringResource(R.string.fuchy_override_title, override.fucha.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    override.warnings.forEach { Text(clashText(it) { id -> names[id] ?: id }) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::override) { Text(stringResource(R.string.fuchy_override_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverride) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.grantAsk?.let { ask ->
        val permissions = ask.permissions.map { stringResource(it.labelRes()) }.joinToString(", ")
        AlertDialog(
            onDismissRequest = viewModel::dismissGrant,
            title = { Text(stringResource(R.string.grant_title, ask.fucha.name, permissions)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    Text(stringResource(R.string.grant_addresses))
                    ask.fucha.addresses.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    if (ask.fucha.sends.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.grant_sends,
                                ask.fucha.sends
                                    .map { stringResource(it.labelRes()) }
                                    .joinToString(", "),
                            ),
                        )
                    }
                    Text(stringResource(R.string.grant_where_to_change))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::grant) { Text(stringResource(R.string.grant_allow)) } },
            dismissButton = {
                TextButton(onClick = viewModel::dismissGrant) { Text(stringResource(R.string.grant_deny)) }
            },
        )
    }

    state.removing?.let { fucha ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRemove,
            title = { Text(stringResource(R.string.fuchy_remove_title, fucha.name)) },
            text = { Text(stringResource(R.string.fuchy_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::remove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.fuchy_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemove) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The whole text and the switch are one toggleable, so TalkBack reads the reason a fucha is off with its switch. */
@Composable
private fun FuchaRowView(
    row: FuchaRow,
    onSwitch: (Fucha, Boolean) -> Unit,
    onRemove: ((Fucha) -> Unit)?,
    onPickCity: (fuchaId: String, switchOn: Boolean) -> Unit,
    onReport: (Fucha) -> Unit,
) {
    val fucha = row.installed.fucha
    FuchaSwitchRow(row, onSwitch, onRemove)
    row.installed.state?.city?.takeIf { fucha.needsCity }?.let { city ->
        LinkRow(
            title = stringResource(R.string.fuchy_city),
            subtitle = city.label(),
            target = LinkTarget.Inside,
            onClick = { onPickCity(fucha.id, false) },
            modifier = Modifier.padding(start = Spacing.base),
        )
    }
    LinkRow(
        title = stringResource(R.string.report_fucha),
        subtitle = stringResource(R.string.report_fucha_hint),
        target = LinkTarget.Outside,
        onClick = { onReport(fucha) },
        modifier = Modifier.padding(start = Spacing.base),
    )
}

@Composable
private fun FuchaSwitchRow(
    row: FuchaRow,
    onSwitch: (Fucha, Boolean) -> Unit,
    onRemove: ((Fucha) -> Unit)?,
) {
    val fucha = row.installed.fucha
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.base, end = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .toggleable(value = row.installed.on, role = Role.Switch, onValueChange = { onSwitch(fucha, it) })
                    .padding(vertical = Spacing.snug),
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.hair)) {
                Text(fucha.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.fuchy_author, fucha.author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Approval(fucha.approved)
                row.offBecause?.let {
                    Text(
                        offText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CheckSwitch(checked = row.installed.on)
        }
        if (onRemove != null) {
            IconButton(onClick = { onRemove(fucha) }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.fuchy_remove, fucha.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.size(LocalMinimumInteractiveComponentSize.current))
        }
    }
}

@Composable
private fun offText(because: OffBecause): String =
    when (because) {
        is OffBecause.Holds -> stringResource(R.string.fuchy_off_holds, because.other.name)
        is OffBecause.SoundsLike -> stringResource(R.string.fuchy_off_sounds_like, because.other.name)
        OffBecause.NearName -> stringResource(R.string.fuchy_off_name)
        is OffBecause.NotGranted ->
            stringResource(
                R.string.fuchy_off_not_granted,
                stringResource(because.permission.labelRes()),
            )
        is OffBecause.Never -> stringResource(R.string.fuchy_off_never, stringResource(because.permission.labelRes()))
    }
