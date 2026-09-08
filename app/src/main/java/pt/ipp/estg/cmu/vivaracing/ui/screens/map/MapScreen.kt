@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.screens.alerts.AlertsViewModel
import pt.ipp.estg.cmu.vivaracing.ui.screens.amateur.AmateurRunsViewModel
import pt.ipp.estg.cmu.vivaracing.ui.screens.races.RacesViewModel

private const val PREFIX_RACE = "race:"
private const val PREFIX_ALERT = "alert:"
private const val PREFIX_RUN = "run:"

/**
 * Mapa único da aplicação.
 *
 * Substitui os três mapas separados que existiam para provas, alertas e
 * participações amadoras. A informação georreferenciada da aplicação é a
 * mesma realidade vista de ângulos diferentes, ou seja, uma prova, os
 * alertas publicados ao longo do seu percurso e os tempos amadores
 * registados nesse percurso, pelo que faz mais sentido apresentá-la num
 * único mapa com camadas do que obrigar o utilizador a alternar entre três
 * ecrãs para comparar. Cada camada é ligada ou desligada de forma independente, o que
 * mantém disponível a leitura isolada de cada tipo de registo.
 *
 * O ecrã de lista de cada tipo continua a existir em separado, pelo que a
 * exigência do enunciado, cada listagem em lista e em mapa, com ecrã de
 * detalhe comum, mantém-se cumprida: tocar num marcador abre o mesmo detalhe
 * a que a lista conduz.
 */
@Composable
fun MapScreen(
    onOpenRace: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    racesViewModel: RacesViewModel = viewModel(),
    alertsViewModel: AlertsViewModel = viewModel(),
    runsViewModel: AmateurRunsViewModel = viewModel()
) {
    var showRaces by rememberSaveable { mutableStateOf(true) }
    var showAlerts by rememberSaveable { mutableStateOf(true) }
    var showRuns by rememberSaveable { mutableStateOf(true) }

    val races by racesViewModel.races.collectAsState()
    val alerts by alertsViewModel.alerts.collectAsState()
    val runs by runsViewModel.runs.collectAsState()

    val anonymousLabel = stringResource(R.string.anonymous_athlete)

    val raceMarkers = if (!showRaces) emptyList() else races
        .filter { it.startLatitude != 0.0 || it.startLongitude != 0.0 }
        .map { race ->
            MapMarker(
                id = PREFIX_RACE + race.id,
                position = GeoPoint(race.startLatitude, race.startLongitude),
                title = race.name,
                snippet = "${race.city} • ${Formatters.formatDate(race.startDateTime)}",
                hue = when (race.status) {
                    RaceStatus.SCHEDULED -> BitmapDescriptorFactory.HUE_AZURE
                    RaceStatus.ONGOING -> BitmapDescriptorFactory.HUE_GREEN
                    RaceStatus.FINISHED -> BitmapDescriptorFactory.HUE_ORANGE
                }
            )
        }

    val alertMarkers = if (!showAlerts) emptyList() else alerts
        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
        .map { alert ->
            MapMarker(
                id = PREFIX_ALERT + alert.id,
                position = GeoPoint(alert.latitude, alert.longitude),
                title = alert.raceName,
                snippet = "${alert.athleteBib?.let { "#$it • " } ?: ""}" +
                    Formatters.formatTime(alert.timestamp),
                hue = when (alert.type) {
                    AlertType.RACE_START -> BitmapDescriptorFactory.HUE_GREEN
                    AlertType.ATHLETE_PASSING -> BitmapDescriptorFactory.HUE_AZURE
                    AlertType.RACE_FINISH -> BitmapDescriptorFactory.HUE_VIOLET
                    AlertType.INCIDENT -> BitmapDescriptorFactory.HUE_RED
                }
            )
        }

    val runMarkers = if (!showRuns) emptyList() else runs
        .mapNotNull { run ->
            val start = run.route.firstOrNull() ?: return@mapNotNull null
            MapMarker(
                id = PREFIX_RUN + run.id,
                position = start,
                title = run.publicName(anonymousLabel),
                snippet = "${run.raceName} • ${Formatters.formatDuration(run.durationSeconds)}",
                hue = BitmapDescriptorFactory.HUE_YELLOW
            )
        }

    val markers = raceMarkers + alertMarkers + runMarkers

    Column(modifier = Modifier.fillMaxSize()) {
        LayerChips(
            showRaces = showRaces,
            showAlerts = showAlerts,
            showRuns = showRuns,
            onToggleRaces = { showRaces = !showRaces },
            onToggleAlerts = { showAlerts = !showAlerts },
            onToggleRuns = { showRuns = !showRuns }
        )

        if (!showRaces && !showAlerts && !showRuns) {
            EmptyState(
                title = stringResource(R.string.map_layers_empty_title),
                description = stringResource(R.string.map_layers_empty_description),
                icon = Icons.Filled.Map
            )
            return@Column
        }

        if (markers.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_map_title),
                description = stringResource(R.string.empty_map_description),
                icon = Icons.Filled.Map
            )
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            VivaRacingMap(
                markers = markers,
                initialCenter = GeoUtils.centerOf(markers.map { it.position }),
                initialZoom = GeoUtils.suggestedZoom(markers.map { it.position }),
                onMarkerClick = { id ->
                    // O identificador transporta o tipo de registo, o que
                    // permite encaminhar cada marcador para o ecrã de detalhe
                    // correspondente sem ambiguidade entre as três camadas.
                    when {
                        id.startsWith(PREFIX_RACE) -> onOpenRace(id.removePrefix(PREFIX_RACE))
                        id.startsWith(PREFIX_ALERT) -> onOpenAlert(id.removePrefix(PREFIX_ALERT))
                        id.startsWith(PREFIX_RUN) -> onOpenRun(id.removePrefix(PREFIX_RUN))
                    }
                }
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (showRaces) {
                        Text(
                            text = stringResource(R.string.map_hint_races, raceMarkers.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (showAlerts) {
                        Text(
                            text = stringResource(R.string.map_hint_alerts, alertMarkers.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (showRuns) {
                        Text(
                            text = stringResource(R.string.map_hint_runs, runMarkers.size),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** Seletor das camadas apresentadas no mapa. */
@Composable
private fun LayerChips(
    showRaces: Boolean,
    showAlerts: Boolean,
    showRuns: Boolean,
    onToggleRaces: () -> Unit,
    onToggleAlerts: () -> Unit,
    onToggleRuns: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LayerChip(
            selected = showRaces,
            onClick = onToggleRaces,
            label = stringResource(R.string.map_layer_races),
            icon = Icons.Filled.DirectionsRun
        )
        LayerChip(
            selected = showAlerts,
            onClick = onToggleAlerts,
            label = stringResource(R.string.map_layer_alerts),
            icon = Icons.Filled.NotificationsActive
        )
        LayerChip(
            selected = showRuns,
            onClick = onToggleRuns,
            label = stringResource(R.string.map_layer_runs),
            icon = Icons.Filled.EmojiEvents
        )
    }
}

@Composable
private fun LayerChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (selected) Icons.Filled.Check else icon,
                contentDescription = null
            )
        }
    )
}
