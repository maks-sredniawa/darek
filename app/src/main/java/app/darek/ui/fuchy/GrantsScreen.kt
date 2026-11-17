package app.darek.ui.fuchy

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.fuchy.GrantDefault
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.SectionHeader
import kotlinx.coroutines.delay

private const val CONFIRM_SECONDS = 10

@Composable
fun GrantsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GrantsViewModel = viewModel(factory = GrantsViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val afterTakeBack = remember { FocusRequester() }
    LaunchedEffect(state.tookBack) { if (state.tookBack != null) afterTakeBack.requestFocus() }

    DetailScaffold(
        title = stringResource(R.string.grants_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(modifier = contentModifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (!state.loaded) return@Column
            Text(
                stringResource(R.string.grants_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.base),
            )
            state.permissions.forEach { grants ->
                SectionHeader(stringResource(grants.permission.labelRes()))
                Column(Modifier.selectableGroup()) {
                    GrantDefault.entries.forEach { default ->
                        DefaultRow(
                            label = stringResource(default.labelRes()),
                            selected = grants.default == default,
                            onSelect = { viewModel.choose(grants.permission, default) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.grants_exceptions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(
                                start = Spacing.base,
                                end = Spacing.base,
                                top = Spacing.base,
                                bottom = Spacing.tight,
                            ).semantics { heading() },
                )
                val took = state.tookBack?.takeIf { it.permission == grants.permission }
                took?.let {
                    Text(
                        stringResource(
                            R.string.grants_took_back,
                            it.fucha.name,
                            stringResource(it.permission.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier
                                .padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.tight)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                val focusAt = took?.index?.coerceAtMost(grants.exceptions.lastIndex)
                if (grants.exceptions.isEmpty()) {
                    Text(
                        stringResource(R.string.grants_no_exceptions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(horizontal = Spacing.base)
                                .then(
                                    if (took !=
                                        null
                                    ) {
                                        Modifier.focusRequester(afterTakeBack).focusable()
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
                grants.exceptions.forEachIndexed { index, fucha ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = Spacing.base, end = Spacing.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fucha.name, style = MaterialTheme.typography.bodyLarge)
                            Approval(fucha.approved)
                        }
                        TextButton(
                            onClick = { viewModel.revoke(fucha, grants.permission) },
                            modifier = if (index == focusAt) Modifier.focusRequester(afterTakeBack) else Modifier,
                        ) {
                            Text(stringResource(R.string.grants_revoke))
                        }
                    }
                }
            }
        }
    }

    state.confirmingAll?.let { permission ->
        var left by remember(permission) { mutableIntStateOf(CONFIRM_SECONDS) }
        LaunchedEffect(permission) {
            while (left > 0) {
                delay(1_000)
                left--
            }
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissAll,
            title = { Text(stringResource(R.string.grants_all_title, stringResource(permission.labelRes()))) },
            text = { Text(stringResource(R.string.grants_all_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAll, enabled = left == 0) {
                    Text(
                        if (left == 0) {
                            stringResource(R.string.grants_all_confirm)
                        } else {
                            stringResource(R.string.grants_all_confirm_wait, left)
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAll) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DefaultRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = Spacing.base, vertical = Spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun GrantDefault.labelRes(): Int =
    when (this) {
        GrantDefault.ASK -> R.string.grants_ask
        GrantDefault.APPROVED -> R.string.grants_approved
        GrantDefault.ALL -> R.string.grants_all
        GrantDefault.NEVER -> R.string.grants_never
    }
