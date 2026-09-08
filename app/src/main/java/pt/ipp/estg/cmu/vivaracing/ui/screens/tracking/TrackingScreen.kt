@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.tracking

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.navigation.Destinations

/**
 * Ecrã de gravação de percurso.
 *
 * Serve dois cenários: registar o trajeto de uma prova oficial ao vivo e
 * cronometrar a participação amadora de um utilizador no percurso de uma
 * prova já registada. A recolha decorre num serviço em primeiro plano, pelo
 * que o utilizador pode sair da aplicação sem interromper a gravação.
 */
@Composable
fun TrackingScreen(
    raceId: String,
    mode: String,
    snackbarHostState: SnackbarHostState,
    onFinished: () -> Unit,
    viewModel: TrackingViewModel = viewModel()
) {
    LaunchedEffect(raceId, mode) { viewModel.bind(raceId, mode) }

    val state by viewModel.trackingState.collectAsState()
    val race by viewModel.race.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (mode == Destinations.MODE_AMATEUR_RUN) {
                stringResource(R.string.tracking_title_amateur, race?.name.orEmpty())
            } else {
                stringResource(R.string.tracking_title_official)
            },
            style = MaterialTheme.typography.titleLarge
        )

        if (!locationPermissions.allPermissionsGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.permission_location_rationale),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { locationPermissions.launchMultiplePermissionRequest() }) {
                        Text(stringResource(R.string.action_grant_permission))
                    }
                }
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_time),
                value = Formatters.formatDuration(state.elapsedSeconds),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_distance),
                value = Formatters.formatDistance(state.distanceMeters),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_average_speed),
                value = Formatters.formatSpeed(state.averageSpeedKmh),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_pace),
                value = Formatters.formatPace(state.distanceMeters, state.elapsedSeconds),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_steps),
                value = state.steps.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_points),
                value = state.points.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            VivaRacingMap(
                markers = emptyList(),
                route = state.points.ifEmpty { race?.route.orEmpty() },
                initialCenter = GeoUtils.centerOf(state.points.ifEmpty { race?.route.orEmpty() }),
                initialZoom = 16f,
                showMyLocation = true
            )
        }

        if (state.points.isEmpty() && state.isRunning) {
            Text(
                text = stringResource(R.string.tracking_waiting_gps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.isRunning) {
                Button(
                    onClick = viewModel::start,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.action_start_tracking))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (state.isPaused) viewModel.resume() else viewModel.pause()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (state.isPaused) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.Filled.Pause
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = if (state.isPaused) {
                            stringResource(R.string.action_resume)
                        } else {
                            stringResource(R.string.action_pause)
                        }
                    )
                }

                Button(
                    onClick = { viewModel.stopAndSave(onFinished) },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(R.string.action_finish))
                    }
                }
            }
        }

        if (state.isRunning) {
            OutlinedButton(
                onClick = { viewModel.discard(onFinished) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_discard))
            }
        }
    }
}
