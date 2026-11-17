package app.darek.ui.fuchy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.DetailScaffold
import app.darek.ui.common.LinkRow
import app.darek.ui.common.LinkTarget
import app.darek.ui.common.SectionHeader
import app.darek.weather.CitySearch

/** Asked once on the first switch-on, after the grant; opened again from the fucha's row to change it. */
@Composable
fun CityScreen(
    fuchaId: String,
    switchOn: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CityViewModel =
        viewModel(factory = CityViewModel.factory(fuchaId, switchOn), extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    DetailScaffold(
        title = stringResource(R.string.city_title),
        onBack = onDone,
        modifier = modifier,
    ) { contentModifier ->
        Column(modifier = contentModifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val fucha = state.fucha ?: return@Column
            Column(
                modifier = Modifier.padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.snug),
            ) {
                Text(stringResource(R.string.city_body, fucha.name), style = MaterialTheme.typography.bodyMedium)
                state.current?.let {
                    Text(stringResource(R.string.city_current, it.label()), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.snug),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::type,
                        label = { Text(stringResource(R.string.city_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = viewModel::search, enabled = state.query.isNotBlank() && !state.searching) {
                        Text(stringResource(R.string.city_search))
                    }
                }
            }
            if (state.searching) {
                CircularProgressIndicator(modifier = Modifier.padding(Spacing.base))
            }
            when (val result = state.result) {
                null -> Unit
                is CitySearch.Found ->
                    if (result.cities.isEmpty()) {
                        Message(stringResource(R.string.city_none))
                    } else {
                        SectionHeader(stringResource(R.string.city_pick))
                        result.cities.forEach { city ->
                            LinkRow(
                                title = city.name,
                                subtitle = city.region,
                                target = LinkTarget.Here,
                                onClick = { viewModel.pick(city) },
                            )
                        }
                    }
                CitySearch.NoInternet -> Message(stringResource(R.string.city_no_internet))
                CitySearch.Failed -> Message(stringResource(R.string.city_failed))
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Spacing.base).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
