@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package pt.ipp.estg.cmu.vivaracing.ui.screens.races

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.AndroidInteractions
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.ui.components.AlertCard
import pt.ipp.estg.cmu.vivaracing.ui.components.GuestNotice
import pt.ipp.estg.cmu.vivaracing.ui.components.LocalSession
import pt.ipp.estg.cmu.vivaracing.ui.components.rememberGuestExitAction
import pt.ipp.estg.cmu.vivaracing.ui.components.AmateurRunCard
import pt.ipp.estg.cmu.vivaracing.ui.components.LoadingBox
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.components.label

/**
 * Ecrã de detalhe de uma prova.
 *
 * Concentra a informação da prova, o percurso desenhado sobre o mapa, os
 * alertas publicados pela comunidade e a classificação das participações
 * amadoras. Disponibiliza também as integrações com aplicações nativas do
 * Android: marcador telefónico, mensagens, partilha e agenda de contactos.
 */
@Composable
fun RaceDetailScreen(
    raceId: String,
    snackbarHostState: SnackbarHostState,
    onOpenAlertForm: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenRun: (String) -> Unit,
    onStartAmateurRun: () -> Unit,
    viewModel: RaceDetailViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(raceId) { viewModel.bind(raceId) }

    val race by viewModel.race.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val runs by viewModel.runs.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val isNearRoute by viewModel.isNearRoute.collectAsState()
    val message by viewModel.message.collectAsState()

    val subscribedText = stringResource(R.string.message_subscribed)
    val unsubscribedText = stringResource(R.string.message_unsubscribed)

    LaunchedEffect(message) {
        when (message) {
            RaceDetailViewModel.MESSAGE_SUBSCRIBED -> snackbarHostState.showSnackbar(subscribedText)
            RaceDetailViewModel.MESSAGE_UNSUBSCRIBED ->
                snackbarHostState.showSnackbar(unsubscribedText)
        }
        if (message != null) viewModel.consumeMessage()
    }

    val session = LocalSession.current
    val onCreateAccount = rememberGuestExitAction()
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    val currentRace = race
    val inviteMessage = stringResource(
        R.string.invite_message,
        currentRace?.name.orEmpty(),
        Formatters.formatDateTime(currentRace?.startDateTime ?: 0L)
    )

    val contactPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val contact = AndroidInteractions.readContact(context, uri)
        if (contact != null && contact.phoneNumber.isNotBlank()) {
            AndroidInteractions.sendSms(context, contact.phoneNumber, inviteMessage)
        }
    }

    if (currentRace == null) {
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
        Text(text = currentRace.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${currentRace.type.label()} • ${currentRace.status.label()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        currentRace.photoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_distance),
                value = Formatters.formatDistance(currentRace.distanceMeters),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_start),
                value = Formatters.formatDateTime(currentRace.startDateTime),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_alerts),
                value = alerts.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        if (currentRace.description.isNotBlank()) {
            Text(text = currentRace.description, style = MaterialTheme.typography.bodyMedium)
        }

        weather?.let { info ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.weather_title, info.locationName),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(
                            R.string.weather_details,
                            info.description,
                            info.temperatureCelsius,
                            info.humidityPercent,
                            info.windSpeedKmh
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            VivaRacingMap(
                markers = buildList {
                    add(
                        MapMarker(
                            id = "start",
                            position = GeoPoint(
                                currentRace.startLatitude,
                                currentRace.startLongitude
                            ),
                            title = stringResource(R.string.marker_start),
                            snippet = currentRace.city,
                            hue = BitmapDescriptorFactory.HUE_GREEN
                        )
                    )
                    alerts.take(30).forEach { alert ->
                        add(
                            MapMarker(
                                id = alert.id,
                                position = GeoPoint(alert.latitude, alert.longitude),
                                title = alert.type.name,
                                snippet = alert.message,
                                hue = BitmapDescriptorFactory.HUE_ROSE
                            )
                        )
                    }
                },
                route = currentRace.route,
                initialCenter = GeoUtils.centerOf(
                    currentRace.route.ifEmpty {
                        listOf(GeoPoint(currentRace.startLatitude, currentRace.startLongitude))
                    }
                ),
                initialZoom = GeoUtils.suggestedZoom(currentRace.route),
                onMarkerClick = { id -> if (id != "start") onOpenAlert(id) }
            )
        }

        if (!session.isRegistered) {
            GuestNotice(onCreateAccount = onCreateAccount)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subscrever serve para acompanhar a prova de outra pessoa. Quem a
            // registou já recebe tudo o que nela acontece, pelo que a ação não
            // lhe é apresentada.
            if (currentRace.authorId != viewModel.currentUid) {
                Button(
                    onClick = viewModel::toggleSubscription,
                    enabled = session.isRegistered,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isSubscribed) {
                            Icons.Filled.NotificationsOff
                        } else {
                            Icons.Filled.NotificationAdd
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = if (isSubscribed) {
                            stringResource(R.string.action_unsubscribe)
                        } else {
                            stringResource(R.string.action_subscribe)
                        }
                    )
                }
            }
            FilledTonalButton(
                onClick = onStartAmateurRun,
                enabled = session.isRegistered,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_run_amateur))
            }
        }

        Button(
            onClick = { onOpenAlertForm(currentRace.id) },
            enabled = isNearRoute && session.isRegistered,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_publish_alert))
        }
        if (!isNearRoute && session.isRegistered) {
            Text(
                text = stringResource(R.string.hint_not_near_route),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_android_actions),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (currentRace.organizerPhone.isNotBlank()) {
                        AndroidInteractions.dial(context, currentRace.organizerPhone)
                    }
                },
                enabled = currentRace.organizerPhone.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_call))
            }

            OutlinedButton(
                onClick = {
                    AndroidInteractions.share(
                        context = context,
                        subject = currentRace.name,
                        message = inviteMessage
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_share))
            }
        }

        OutlinedButton(
            onClick = {
                if (contactsPermission.status.isGranted) {
                    contactPicker.launch(null)
                } else {
                    contactsPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.ContactPhone, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.action_invite_contact))
        }

        OutlinedButton(
            onClick = {
                AndroidInteractions.openInMaps(
                    context = context,
                    latitude = currentRace.startLatitude,
                    longitude = currentRace.startLongitude,
                    label = currentRace.name
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_open_external_maps))
        }

        if (currentRace.authorId == viewModel.currentUid) {
            OutlinedButton(
                onClick = { viewModel.deleteRace(onDeleted = {}) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_delete_race))
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_alerts),
            style = MaterialTheme.typography.titleMedium
        )
        if (alerts.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_alerts_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            alerts.take(10).forEach { alert ->
                AlertCard(alert = alert, onClick = { onOpenAlert(alert.id) })
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_leaderboard),
            style = MaterialTheme.typography.titleMedium
        )
        if (runs.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_runs_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            runs.forEachIndexed { index, run ->
                AmateurRunCard(
                    run = run,
                    position = index + 1,
                    onClick = { onOpenRun(run.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
