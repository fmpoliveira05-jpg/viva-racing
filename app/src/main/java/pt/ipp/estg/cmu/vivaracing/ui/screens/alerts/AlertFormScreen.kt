@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    com.google.accompanist.permissions.ExperimentalPermissionsApi::class
)

package pt.ipp.estg.cmu.vivaracing.ui.screens.alerts

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.ui.components.ErrorBanner
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.components.label
import pt.ipp.estg.cmu.vivaracing.ui.screens.camera.CameraCaptureOverlay

/**
 * Formulário de publicação de um alerta durante uma prova.
 *
 * A fotografia pode ser captada com a CameraX (elemento de bonificação) ou
 * escolhida no seletor de imagens do sistema, sendo depois carregada para o
 * Supabase Storage.
 */
@Composable
fun AlertFormScreen(
    raceId: String,
    snackbarHostState: SnackbarHostState,
    onSaved: () -> Unit,
    viewModel: AlertFormViewModel = viewModel()
) {
    LaunchedEffect(raceId) { viewModel.bind(raceId) }

    val state by viewModel.state.collectAsState()
    var showCamera by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.onPhotoSelected(uri) }

    val publishedMessage = stringResource(R.string.message_alert_published)

    if (showCamera) {
        CameraCaptureOverlay(
            onImageCaptured = { uri ->
                viewModel.onPhotoSelected(uri)
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        alertErrorMessage(state.errorMessage)?.let { ErrorBanner(message = it) }

        Text(
            text = state.race?.name.orEmpty(),
            style = MaterialTheme.typography.titleLarge
        )

        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = !typeExpanded }
        ) {
            OutlinedTextField(
                value = state.type.label(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_alert_type)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                AlertType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label()) },
                        onClick = {
                            viewModel.onTypeChange(type)
                            typeExpanded = false
                        }
                    )
                }
            }
        }

        if (state.type == AlertType.ATHLETE_PASSING && state.athleteOptions.isNotEmpty()) {
            var athleteExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = athleteExpanded,
                onExpandedChange = { athleteExpanded = !athleteExpanded }
            ) {
                OutlinedTextField(
                    value = state.athleteName.ifBlank {
                        stringResource(R.string.athlete_not_linked)
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_athlete)) },
                    supportingText = { Text(stringResource(R.string.field_athlete_hint)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = athleteExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = athleteExpanded,
                    onDismissRequest = { athleteExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.athlete_not_linked)) },
                        onClick = {
                            viewModel.onAthleteSelected(null)
                            athleteExpanded = false
                        }
                    )
                    state.athleteOptions.forEach { athlete ->
                        DropdownMenuItem(
                            text = { Text(athlete.username) },
                            onClick = {
                                viewModel.onAthleteSelected(athlete)
                                athleteExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (state.type == AlertType.ATHLETE_PASSING) {
            OutlinedTextField(
                value = state.bibNumber,
                onValueChange = viewModel::onBibChange,
                label = { Text(stringResource(R.string.field_bib_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = state.message,
            onValueChange = viewModel::onMessageChange,
            label = { Text(stringResource(R.string.field_alert_message)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            VivaRacingMap(
                markers = if (state.hasLocation) {
                    listOf(
                        MapMarker(
                            id = "observer",
                            position = GeoPoint(state.latitude, state.longitude),
                            title = stringResource(R.string.marker_observer)
                        )
                    )
                } else {
                    emptyList()
                },
                route = state.race?.route.orEmpty(),
                initialCenter = if (state.hasLocation) {
                    GeoPoint(state.latitude, state.longitude)
                } else {
                    null
                },
                initialZoom = 15f
            )
        }

        Text(
            text = if (state.hasLocation) {
                Formatters.formatCoordinates(state.latitude, state.longitude)
            } else {
                stringResource(R.string.hint_waiting_location)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = viewModel::captureLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_refresh_location))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (cameraPermission.status.isGranted) {
                        showCamera = true
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_take_photo))
            }
            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_gallery))
            }
        }

        state.photoUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        }

        Button(
            onClick = {
                viewModel.publish {
                    coroutineScope.launch { snackbarHostState.showSnackbar(publishedMessage) }
                    onSaved()
                }
            },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.action_publish_alert))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun alertErrorMessage(code: String?): String? = when (code) {
    null -> null
    AlertFormViewModel.ERROR_NO_LOCATION -> stringResource(R.string.error_no_location)
    AlertFormViewModel.ERROR_RACE_MISSING -> stringResource(R.string.error_race_missing)
    AlertFormViewModel.ERROR_BIB_REQUIRED -> stringResource(R.string.error_bib_required)
    else -> code
}
