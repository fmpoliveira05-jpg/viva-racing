@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.alerts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.ui.components.AlertCard
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState
import pt.ipp.estg.cmu.vivaracing.ui.components.label

/** Listagem de alertas em formato de lista, com filtros por tipo. */
@Composable
fun AlertListScreen(
    onOpenAlert: (String) -> Unit,
    viewModel: AlertsViewModel = viewModel()
) {
    val alerts by viewModel.alerts.collectAsState()
    val onlySubscribed by viewModel.onlySubscribed.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = onlySubscribed,
                onClick = viewModel::toggleOnlySubscribed,
                label = { Text(stringResource(R.string.filter_subscribed)) }
            )
            AlertType.entries.forEach { type ->
                FilterChip(
                    selected = typeFilter == type,
                    onClick = {
                        viewModel.onTypeFilterChange(if (typeFilter == type) null else type)
                    },
                    label = { Text(type.label()) }
                )
            }
        }

        if (alerts.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_alerts_title),
                description = stringResource(R.string.empty_alerts_description),
                icon = Icons.Filled.NotificationsActive
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = alerts, key = { it.id }) { alert ->
                    AlertCard(alert = alert, onClick = { onOpenAlert(alert.id) })
                }
            }
        }
    }
}
