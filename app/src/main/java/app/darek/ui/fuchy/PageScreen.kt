package app.darek.ui.fuchy

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.fuchy.CatalogueEntry
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.SectionHeader
import app.darek.ui.support.rateOnPlay

/** *Fuchy do pobrania* (phase-F.md section 7): installed fuchy first, then the rest. */
@Composable
fun PageScreen(
    onBack: () -> Unit,
    onInstall: (Uri) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageViewModel = viewModel(factory = PageViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.toInstall.collect(onInstall) }

    DetailScaffold(
        title = stringResource(R.string.page_title),
        onBack = onBack,
        modifier = modifier,
    ) { contentModifier ->
        when (val load = state.load) {
            PageLoad.Loading -> Centred(contentModifier, stringResource(R.string.page_loading), busy = true)
            is PageLoad.Failed ->
                Centred(contentModifier, stringResource(R.string.page_failed)) {
                    Button(onClick = viewModel::refresh, enabled = !load.retrying) {
                        if (load.retrying) {
                            CircularProgressIndicator(Modifier.size(Spacing.base), strokeWidth = Spacing.hair)
                        } else {
                            Text(stringResource(R.string.page_retry))
                        }
                    }
                }
            PageLoad.NewerFormat -> {
                val context = LocalContext.current
                Centred(contentModifier, stringResource(R.string.page_newer_format)) {
                    Button(onClick = { context.rateOnPlay() }) { Text(stringResource(R.string.page_open_play)) }
                }
            }
            is PageLoad.Read ->
                Column(modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (state.installed.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.fuchy_installed))
                        state.installed.forEach { PageRowView(it, onGet = viewModel::get) }
                    }
                    if (state.available.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.page_available))
                        state.available.forEach { PageRowView(it, onGet = viewModel::get) }
                    }
                    if (state.installed.isEmpty() && state.available.isEmpty()) {
                        Message(stringResource(R.string.page_empty))
                    }
                    if (state.hiddenProfanity > 0) {
                        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = Spacing.tight)) {
                            Text(
                                pluralStringResource(
                                    R.plurals.page_hidden_profanity,
                                    state.hiddenProfanity,
                                    state.hiddenProfanity,
                                ),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun PageRowView(
    row: PageRow,
    onGet: (CatalogueEntry) -> Unit,
) {
    val entry = row.entry
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.snug),
        verticalArrangement = Arrangement.spacedBy(Spacing.hair),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Spacing.hair),
        ) {
            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
            Secondary(entry.description)
            Secondary(stringResource(R.string.fuchy_author, entry.author))
            Approval(approved = row.installed?.fucha?.approved ?: true)
        }
        val status =
            when {
                row.failed -> stringResource(R.string.page_download_failed)
                row.action == RowAction.UP_TO_DATE ->
                    stringResource(R.string.page_up_to_date, row.installed?.fucha?.version ?: entry.version)
                row.action == RowAction.UPDATE -> stringResource(R.string.page_newer_version, entry.version)
                row.action == RowAction.REPLACE -> stringResource(R.string.page_unapproved_copy)
                row.action == RowAction.NEEDS_NEWER -> stringResource(R.string.refusal_newer_darek)
                else -> null
            }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val label =
            when (row.action) {
                RowAction.INSTALL -> R.string.page_install
                RowAction.UPDATE -> R.string.page_update
                RowAction.REPLACE -> R.string.page_replace
                RowAction.UP_TO_DATE, RowAction.NEEDS_NEWER -> null
            }
        if (label != null) {
            val described =
                stringResource(
                    when (row.action) {
                        RowAction.UPDATE -> R.string.page_update_named
                        RowAction.REPLACE -> R.string.page_replace_named
                        else -> R.string.page_install_named
                    },
                    entry.name,
                )
            FilledTonalButton(
                onClick = { onGet(entry) },
                enabled = !row.downloading,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(top = Spacing.tight)
                        .semantics { contentDescription = described },
            ) {
                if (row.downloading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(Spacing.base), strokeWidth = Spacing.hair)
                        Text(stringResource(R.string.page_downloading))
                    }
                } else {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun Secondary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(Spacing.base).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Centred(
    modifier: Modifier,
    text: String,
    busy: Boolean = false,
    action: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.base), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            if (busy) CircularProgressIndicator()
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            action()
        }
    }
}
