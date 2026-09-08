@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.races

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState
import pt.ipp.estg.cmu.vivaracing.ui.components.RaceCard
import pt.ipp.estg.cmu.vivaracing.ui.components.label

/**
 * Listagem de provas em formato de lista.
 *
 * Utiliza `LazyColumn` com chave por identificador, conforme recomendado nos
 * materiais da unidade curricular, para que a recomposição só afete os
 * elementos efetivamente alterados.
 */
@Composable
fun RaceListScreen(
    onOpenRace: (String) -> Unit,
    viewModel: RacesViewModel = viewModel()
) {
    val races by viewModel.races.collectAsState()
    val filter by viewModel.filter.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.action_search_races)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter.onlySubscribed,
                onClick = { viewModel.onOnlySubscribedChange(!filter.onlySubscribed) },
                label = { Text(stringResource(R.string.filter_subscribed)) }
            )
            RaceStatus.entries.forEach { status ->
                FilterChip(
                    selected = filter.status == status,
                    onClick = {
                        viewModel.onStatusChange(if (filter.status == status) null else status)
                    },
                    label = { Text(status.label()) }
                )
            }
            RaceType.entries.forEach { type ->
                FilterChip(
                    selected = filter.type == type,
                    onClick = {
                        viewModel.onTypeChange(if (filter.type == type) null else type)
                    },
                    label = { Text(type.label()) }
                )
            }
        }

        if (races.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_races_title),
                description = stringResource(R.string.empty_races_description),
                icon = Icons.Filled.DirectionsRun
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = races, key = { it.id }) { race ->
                    RaceCard(race = race, onClick = { onOpenRace(race.id) })
                }
            }
        }
    }
}
