package app.darek.ui.fuchy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.fuchy.Listing
import app.darek.fuchy.Offer
import app.darek.fuchy.PageRow
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.Notice
import app.darek.ui.common.NoticeTone
import app.darek.ui.common.SectionHeader

/** *Strona z fuchami*: the installed ones first, then the rest, each with what it offers (phase-F.md section 7). */
@Composable
fun PageScreen(
    onBack: () -> Unit,
    onInstall: (Listing) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageViewModel = viewModel(factory = PageViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DetailScaffold(
        title = stringResource(R.string.page_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(modifier = contentModifier.verticalScroll(rememberScrollState())) {
            val retry = stringResource(R.string.page_retry) to viewModel::retry
            when (state.load) {
                PageLoad.Loading -> Loading()
                PageLoad.NoInternet ->
                    Notice(
                        NoticeTone.NOTE,
                        stringResource(R.string.page_no_internet_title),
                        listOf(stringResource(R.string.page_no_internet_body)),
                        modifier = Modifier.padding(Spacing.base),
                        icon = Icons.Filled.Info,
                        action = retry,
                    )
                PageLoad.Failed ->
                    Notice(
                        NoticeTone.WARNING,
                        stringResource(R.string.page_failed_title),
                        listOf(stringResource(R.string.page_failed_body)),
                        modifier = Modifier.padding(Spacing.base),
                        icon = Icons.Filled.Warning,
                        action = retry,
                    )
                PageLoad.NewerFormat ->
                    Notice(
                        NoticeTone.NOTE,
                        stringResource(R.string.refusal_newer_darek),
                        emptyList(),
                        modifier = Modifier.padding(Spacing.base),
                        icon = Icons.Filled.Info,
                    )
                is PageLoad.Listed -> Listed(state, onInstall)
            }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.page_loading), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Listed(
    state: PageUiState,
    onInstall: (Listing) -> Unit,
) {
    if (state.autoUpdated.isNotEmpty()) {
        Notice(
            NoticeTone.NOTE,
            stringResource(R.string.page_auto_updated, state.autoUpdated.joinToString(", ")),
            emptyList(),
            modifier = Modifier.padding(Spacing.base),
            icon = Icons.Filled.Check,
        )
    }
    if (state.rows.isEmpty()) {
        Text(
            stringResource(R.string.page_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.base),
        )
        return
    }
    val (installed, available) = state.rows.partition { it.installed != null }
    if (installed.isNotEmpty()) SectionHeader(stringResource(R.string.fuchy_installed))
    installed.forEach { PageRowView(it, it.listing.id in state.waiting, onInstall) }
    if (available.isNotEmpty()) SectionHeader(stringResource(R.string.page_available))
    available.forEach { PageRowView(it, waits = false, onInstall) }
}

/** Not clickable as a whole: the button is the only target, and the text above it is one TalkBack stop. */
@Composable
private fun PageRowView(
    row: PageRow,
    waits: Boolean,
    onInstall: (Listing) -> Unit,
) {
    val listing = row.listing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.snug),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(listing.name, style = MaterialTheme.typography.bodyLarge)
            Approval(true)
            Text(
                listing.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (row.offer) {
                Offer.UpToDate -> State(stringResource(R.string.page_up_to_date), check = true)
                Offer.NeedsNewerDarek -> State(stringResource(R.string.refusal_newer_darek), check = false)
                is Offer.Update ->
                    if (waits) {
                        Text(
                            stringResource(R.string.page_update_waits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                Offer.Install -> Unit
            }
        }
        when (row.offer) {
            Offer.Install -> {
                val label = stringResource(R.string.page_install, listing.name)
                OutlinedButton(
                    onClick = { onInstall(listing) },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                label
                        },
                ) {
                    Text(stringResource(R.string.install_confirm))
                }
            }
            is Offer.Update -> {
                val label = stringResource(R.string.page_update, listing.name)
                Button(onClick = { onInstall(listing) }, modifier = Modifier.semantics { contentDescription = label }) {
                    Text(stringResource(R.string.page_update_button))
                }
            }
            Offer.UpToDate, Offer.NeedsNewerDarek -> Unit
        }
    }
}

@Composable
private fun State(
    text: String,
    check: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.hair), verticalAlignment = Alignment.CenterVertically) {
        if (check) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
