@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.races

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceVisibility
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.ui.components.ErrorBanner
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap
import pt.ipp.estg.cmu.vivaracing.ui.components.label
import java.util.Calendar

/**
 * Formulário de registo de uma nova prova.
 *
 * Reúne num único ecrã as várias formas previstas no enunciado para definir o
 * percurso: gravação ao vivo com o dispositivo, importação de um ficheiro KML
 * ou marcação do ponto de partida diretamente no mapa.
 */
@Composable
fun RaceFormScreen(
    snackbarHostState: SnackbarHostState,
    onRecordRoute: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: RaceFormViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Recupera o percurso gravado quando o utilizador regressa ao formulário.
    LaunchedEffect(Unit) { viewModel.consumeRouteDraft() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.onPhotoSelected(uri) }

    val kmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importKml) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        formErrorMessage(state.errorMessage)?.let { ErrorBanner(message = it) }

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.field_race_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.field_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        RaceTypeSelector(
            selected = state.type,
            onSelected = viewModel::onTypeChange
        )

        RaceStatusSelector(
            selected = state.status,
            onSelected = viewModel::onStatusChange
        )

        RaceVisibilitySelector(
            selected = state.visibility,
            onSelected = viewModel::onVisibilityChange
        )

        Text(
            text = visibilityExplanation(state.visibility),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.city,
            onValueChange = viewModel::onCityChange,
            label = { Text(stringResource(R.string.field_city)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.organizerPhone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text(stringResource(R.string.field_organizer_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(Formatters.formatDate(state.startDateTime))
            }
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(Formatters.formatTime(state.startDateTime))
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_start_location),
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = state.placeQuery,
            onValueChange = viewModel::onPlaceQueryChange,
            label = { Text(stringResource(R.string.field_search_place)) },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = viewModel::searchPlaces) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (state.isSearchingPlaces) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }

        state.suggestions.forEach { suggestion ->
            ListItem(
                headlineContent = { Text(suggestion.displayName) },
                supportingContent = { Text(suggestion.category) },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { viewModel.selectSuggestion(suggestion) }) {
                Text(stringResource(R.string.action_use_this_place))
            }
        }

        FilledTonalButton(
            onClick = viewModel::useCurrentLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.action_use_current_location))
        }

        Card(modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)) {
            VivaRacingMap(
                markers = if (state.hasLocation) {
                    listOf(
                        MapMarker(
                            id = "start",
                            position = GeoPoint(state.latitude, state.longitude),
                            title = stringResource(R.string.marker_start)
                        )
                    )
                } else {
                    emptyList()
                },
                route = state.route,
                initialCenter = if (state.hasLocation) {
                    GeoPoint(state.latitude, state.longitude)
                } else {
                    null
                },
                onMapClick = viewModel::onMapPointSelected
            )
        }

        Text(
            text = if (state.hasLocation) {
                Formatters.formatCoordinates(state.latitude, state.longitude)
            } else {
                stringResource(R.string.hint_tap_map)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.weather?.let { weather ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.weather_title, weather.locationName),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${weather.description} • " +
                            String.format("%.1f", weather.temperatureCelsius) + " °C • " +
                            String.format("%.0f", weather.windSpeedKmh) + " km/h",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_route),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRecordRoute, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Timeline, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_record_route))
            }
            OutlinedButton(
                onClick = { kmlPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.action_import_kml))
            }
        }

        if (state.route.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.route_summary,
                    state.route.size,
                    Formatters.formatDistance(state.distanceMeters)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = viewModel::clearRoute) {
                Text(stringResource(R.string.action_clear_route))
            }
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(stringResource(R.string.action_add_photo))
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
            onClick = { viewModel.save(onSaved) },
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.action_save_race))
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDateTime
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        viewModel.onDateTimeChange(mergeDate(state.startDateTime, selected))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = remember {
            Calendar.getInstance().apply { timeInMillis = state.startDateTime }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateTimeChange(
                        mergeTime(state.startDateTime, timePickerState.hour, timePickerState.minute)
                    )
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

@Composable
private fun RaceTypeSelector(selected: RaceType, onSelected: (RaceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_race_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RaceType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RaceStatusSelector(selected: RaceStatus, onSelected: (RaceStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_race_status)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RaceStatus.entries.forEach { status ->
                DropdownMenuItem(
                    text = { Text(status.label()) },
                    onClick = {
                        onSelected(status)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Seletor de visibilidade da prova.
 *
 * A escolha determina quem consegue ver a prova e, por herança, os alertas e
 * as participações amadoras que lhe estão associados.
 */
@Composable
private fun RaceVisibilitySelector(
    selected: RaceVisibility,
    onSelected: (RaceVisibility) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = visibilityLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.field_visibility)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RaceVisibility.entries.forEach { visibility ->
                DropdownMenuItem(
                    text = { Text(visibilityLabel(visibility)) },
                    onClick = {
                        onSelected(visibility)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun visibilityLabel(visibility: RaceVisibility): String = when (visibility) {
    RaceVisibility.PUBLIC -> stringResource(R.string.visibility_public)
    RaceVisibility.FRIENDS -> stringResource(R.string.visibility_friends)
    RaceVisibility.PRIVATE -> stringResource(R.string.visibility_private)
}

@Composable
private fun visibilityExplanation(visibility: RaceVisibility): String = when (visibility) {
    RaceVisibility.PUBLIC -> stringResource(R.string.visibility_public_description)
    RaceVisibility.FRIENDS -> stringResource(R.string.visibility_friends_description)
    RaceVisibility.PRIVATE -> stringResource(R.string.visibility_private_description)
}

@Composable
private fun formErrorMessage(code: String?): String? = when (code) {
    null -> null
    RaceFormViewModel.ERROR_NAME_REQUIRED -> stringResource(R.string.error_name_required)
    RaceFormViewModel.ERROR_LOCATION_REQUIRED -> stringResource(R.string.error_location_required)
    RaceFormViewModel.ERROR_NO_LOCATION -> stringResource(R.string.error_no_location)
    RaceFormViewModel.ERROR_EMPTY_KML -> stringResource(R.string.error_empty_kml)
    else -> code
}

/** Combina a data escolhida com a hora já definida. */
private fun mergeDate(current: Long, newDateMillis: Long): Long {
    val currentCalendar = Calendar.getInstance().apply { timeInMillis = current }
    val newCalendar = Calendar.getInstance().apply { timeInMillis = newDateMillis }
    newCalendar.set(Calendar.HOUR_OF_DAY, currentCalendar.get(Calendar.HOUR_OF_DAY))
    newCalendar.set(Calendar.MINUTE, currentCalendar.get(Calendar.MINUTE))
    newCalendar.set(Calendar.SECOND, 0)
    return newCalendar.timeInMillis
}

/** Combina a hora escolhida com a data já definida. */
private fun mergeTime(current: Long, hour: Int, minute: Int): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = current
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }
    return calendar.timeInMillis
}
