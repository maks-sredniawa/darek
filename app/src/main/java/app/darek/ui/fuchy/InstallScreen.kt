package app.darek.ui.fuchy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.darek.R
import app.darek.fuchy.InstallPlan
import app.darek.listen.Persona
import app.darek.theme.Spacing
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.Notice
import app.darek.ui.common.NoticeTone

/**
 * The install screen, in phase-F.md section 3's order, with two departures: the description sits
 * under the name, and a refused file shows only its refusal, since nothing below it could be installed.
 */
@Composable
fun InstallScreen(
    viewModel: InstallViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == InstallStep.Installed) onDone() }

    DetailScaffold(
        title = stringResource(R.string.install_title),
        onBack = onDone,
        modifier = modifier,
    ) { contentModifier ->
        Column(
            modifier = contentModifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            when (val step = state.step) {
                InstallStep.Checking, InstallStep.Installed -> Checking()
                InstallStep.Unreadable, InstallStep.NoInternet, InstallStep.CannotCheck, InstallStep.CannotSave -> {
                    val reason =
                        when (step) {
                            InstallStep.Unreadable -> R.string.install_unreadable
                            InstallStep.NoInternet -> R.string.install_no_internet
                            InstallStep.CannotCheck -> R.string.install_cannot_check
                            else -> R.string.install_cannot_save
                        }
                    Refusal(listOf(stringResource(reason)))
                    Buttons(onCancel = onDone, onInstall = null, approved = true)
                }
                is InstallStep.Refused -> {
                    Refusal(step.refusals.map { refusalText(it) { id -> step.phrases[id] ?: id } }.distinct())
                    Buttons(onCancel = onDone, onInstall = null, approved = true)
                }
                is InstallStep.Ready -> {
                    Plan(step.plan, step.names, state.persona)
                    Buttons(onCancel = onDone, onInstall = viewModel::install, approved = step.plan.candidate.approved)
                }
            }
        }
    }
}

@Composable
private fun Checking() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Text(stringResource(R.string.install_checking), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Refusal(lines: List<String>) {
    val title = stringResource(R.string.install_refused_title)
    Notice(
        NoticeTone.REFUSAL,
        title,
        lines,
        modifier = Modifier.announced((listOf(title) + lines).joinToString(" ")),
        icon = Icons.Filled.Close,
    )
}

/**
 * Said by TalkBack when an outcome replaces a loading state. A pane title, not a live region:
 * Compose announces a live region only when an existing node changes, never when one first appears.
 */
internal fun Modifier.announced(outcome: String) = semantics { paneTitle = outcome }

@Composable
private fun Plan(
    plan: InstallPlan,
    names: Map<String, String>,
    persona: Persona,
) {
    val fucha = plan.candidate
    val verdict = stringResource(if (fucha.approved) R.string.fuchy_approved else R.string.install_unapproved_title)
    Text(
        fucha.name,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.announced("${fucha.name}. $verdict").semantics { heading() },
    )
    Text(
        stringResource(R.string.fuchy_author, fucha.author),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(fucha.description, style = MaterialTheme.typography.bodyLarge)
    if (fucha.approved) {
        Approval(true)
    } else {
        Notice(
            NoticeTone.WARNING,
            stringResource(R.string.install_unapproved_title),
            listOfNotNull(
                stringResource(R.string.install_unapproved_body),
                stringResource(R.string.install_replaces_approved).takeIf { plan.fresh && plan.replaces != null },
            ),
            icon = Icons.Filled.Warning,
        )
    }
    plan.replaces?.takeIf { !plan.fresh }?.let {
        Text(
            stringResource(R.string.install_update, it.fucha.version, fucha.version),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Section(R.string.install_does, fucha.commands.map { stringResource(R.string.install_command, it.spoken(persona)) })
    Section(R.string.install_permissions, fucha.permissions.map { stringResource(it.labelRes()) })
    Section(R.string.install_addresses, fucha.addresses, monospace = true)
    Section(R.string.install_sends, fucha.sends.map { stringResource(it.labelRes()) })

    if (fucha.profanity) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(stringResource(R.string.install_profanity), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (plan.clashes.isNotEmpty()) {
        Notice(
            NoticeTone.NOTE,
            stringResource(R.string.install_clash_title),
            plan.clashes.map { clashText(it) { id -> names[id] ?: id } },
            icon = Icons.Filled.Info,
        )
    }
    if (!plan.startsOn) Text(stringResource(R.string.install_starts_off), style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(if (fucha.approved) R.string.install_risk_approved else R.string.install_risk_unapproved),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.section),
    )
}

@Composable
private fun Section(
    title: Int,
    lines: List<String>,
    monospace: Boolean = false,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.base).semantics { heading() },
    )
    (lines.ifEmpty { listOf(stringResource(R.string.install_nothing)) }).forEach {
        Text(
            it,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospace && lines.isNotEmpty()) FontFamily.Monospace else null,
        )
    }
}

/** A refused file gets only *Zamknij*; an unapproved one never gets the filled button (ADR-0015, the strong warning). */
@Composable
private fun Buttons(
    onCancel: () -> Unit,
    onInstall: (() -> Unit)?,
    approved: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.base),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight, Alignment.End),
    ) {
        if (onInstall == null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.install_close)) }
            return@Row
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        if (approved) {
            Button(onClick = onInstall) { Text(stringResource(R.string.install_confirm)) }
        } else {
            OutlinedButton(
                onClick = onInstall,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.install_confirm_unapproved))
            }
        }
    }
}
