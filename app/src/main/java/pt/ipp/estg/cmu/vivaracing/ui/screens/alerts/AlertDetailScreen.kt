package pt.ipp.estg.cmu.vivaracing.ui.screens.alerts

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.util.AndroidInteractions
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.ui.components.LoadingBox
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.components.label

/** ViewModel do ecrã de detalhe de um alerta. */
class AlertDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _alert = MutableStateFlow<RaceAlert?>(null)
    val alert: StateFlow<RaceAlert?> = _alert.asStateFlow()

    private var boundId: String? = null

    fun bind(alertId: String) {
        if (boundId == alertId) return
        boundId = alertId
        viewModelScope.launch {
            container.alertRepository.observeById(alertId).collect { _alert.value = it }
        }
    }
}

/**
 * Detalhe de um alerta: informação do registo, fotografia associada e
 * localização exata onde o atleta foi observado.
 */
@Composable
fun AlertDetailScreen(
    alertId: String,
    onOpenRace: (String) -> Unit,
    viewModel: AlertDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(alertId) { viewModel.bind(alertId) }

    val alert by viewModel.alert.collectAsState()
    val current = alert

    if (current == null) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = current.type.label(), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = current.raceName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            current.athleteBib?.let { bib ->
                StatTile(
                    label = stringResource(R.string.label_bib),
                    value = bib.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            StatTile(
                label = stringResource(R.string.label_time),
                value = Formatters.formatTime(current.timestamp),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_date),
                value = Formatters.formatDate(current.timestamp),
                modifier = Modifier.weight(1f)
            )
        }

        if (current.message.isNotBlank()) {
            Text(text = current.message, style = MaterialTheme.typography.bodyLarge)
        }

        current.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            VivaRacingMap(
                markers = listOf(
                    MapMarker(
                        id = current.id,
                        position = GeoPoint(current.latitude, current.longitude),
                        title = current.type.name,
                        snippet = current.message
                    )
                ),
                initialCenter = GeoPoint(current.latitude, current.longitude),
                initialZoom = 16f
            )
        }

        Text(
            text = Formatters.formatCoordinates(current.latitude, current.longitude),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.label_published_by, current.authorName),
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = { onOpenRace(current.raceId) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_open_race))
        }

        OutlinedButton(
            onClick = {
                AndroidInteractions.share(
                    context = context,
                    subject = current.raceName,
                    message = "${current.type.name} • ${current.message} • " +
                        Formatters.formatCoordinates(current.latitude, current.longitude)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_share))
        }

        Spacer(Modifier.height(24.dp))
    }
}
