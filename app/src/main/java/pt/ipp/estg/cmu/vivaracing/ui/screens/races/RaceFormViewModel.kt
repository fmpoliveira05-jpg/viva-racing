package pt.ipp.estg.cmu.vivaracing.ui.screens.races

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.core.util.KmlParser
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.PlaceSuggestion
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceVisibility
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.data.model.RouteSource
import pt.ipp.estg.cmu.vivaracing.data.model.VisibilityTokens
import pt.ipp.estg.cmu.vivaracing.data.model.WeatherInfo

/** Estado do formulário de criação de prova. */
data class RaceFormState(
    val name: String = "",
    val description: String = "",
    val type: RaceType = RaceType.RUNNING,
    val status: RaceStatus = RaceStatus.SCHEDULED,
    val visibility: RaceVisibility = RaceVisibility.PUBLIC,
    val city: String = "",
    val organizerPhone: String = "",
    val startDateTime: Long = System.currentTimeMillis(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val route: List<GeoPoint> = emptyList(),
    val routeSource: RouteSource = RouteSource.MANUAL,
    val distanceMeters: Double = 0.0,
    val photoUri: Uri? = null,
    val placeQuery: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val weather: WeatherInfo? = null,
    val isSearchingPlaces: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0
}

/**
 * ViewModel do formulário de criação de prova.
 *
 * Concentra três integrações distintas: a pesquisa de locais na API Nominatim,
 * a consulta meteorológica na OpenWeatherMap e o envio da fotografia para o
 * Supabase Storage. O percurso pode chegar de três origens: gravação ao vivo,
 * importação de um ficheiro KML ou seleção manual de um ponto no mapa.
 */
class RaceFormViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _state = MutableStateFlow(RaceFormState())
    val state: StateFlow<RaceFormState> = _state.asStateFlow()

    init {
        consumeRouteDraft()
    }

    // ---------------------------- Campos simples ---------------------------

    fun onNameChange(value: String) = update { it.copy(name = value, errorMessage = null) }
    fun onDescriptionChange(value: String) = update { it.copy(description = value) }
    fun onCityChange(value: String) = update { it.copy(city = value) }
    fun onPhoneChange(value: String) = update { it.copy(organizerPhone = value) }
    fun onTypeChange(value: RaceType) = update { it.copy(type = value) }
    fun onStatusChange(value: RaceStatus) = update { it.copy(status = value) }
    fun onVisibilityChange(value: RaceVisibility) = update { it.copy(visibility = value) }
    fun onDateTimeChange(value: Long) {
        update { it.copy(startDateTime = value) }
        val current = _state.value
        if (current.hasLocation) loadWeather(current.latitude, current.longitude)
    }
    fun onPhotoSelected(uri: Uri?) = update { it.copy(photoUri = uri) }

    fun onMapPointSelected(point: GeoPoint) {
        update { it.copy(latitude = point.latitude, longitude = point.longitude) }
        loadWeather(point.latitude, point.longitude)
        reverseGeocode(point.latitude, point.longitude)
    }

    // ------------------------- Pesquisa de locais --------------------------

    fun onPlaceQueryChange(value: String) {
        update { it.copy(placeQuery = value) }
    }

    fun searchPlaces() {
        val query = _state.value.placeQuery
        if (query.isBlank()) return
        update { it.copy(isSearchingPlaces = true, errorMessage = null) }
        viewModelScope.launch {
            container.placesRepository.search(query)
                .onSuccess { results ->
                    update { it.copy(isSearchingPlaces = false, suggestions = results) }
                }
                .onFailure { throwable ->
                    update {
                        it.copy(isSearchingPlaces = false, errorMessage = throwable.localizedMessage)
                    }
                }
        }
    }

    fun selectSuggestion(suggestion: PlaceSuggestion) {
        update {
            it.copy(
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                city = it.city.ifBlank { suggestion.displayName.substringBefore(',') },
                suggestions = emptyList(),
                placeQuery = suggestion.displayName
            )
        }
        loadWeather(suggestion.latitude, suggestion.longitude)
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            val location = container.locationProvider.lastKnownLocation()
            if (location == null) {
                update { it.copy(errorMessage = ERROR_NO_LOCATION) }
                return@launch
            }
            update { it.copy(latitude = location.latitude, longitude = location.longitude) }
            loadWeather(location.latitude, location.longitude)
            reverseGeocode(location.latitude, location.longitude)
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            container.placesRepository.reverse(latitude, longitude)
                .onSuccess { place ->
                    if (place != null) {
                        update {
                            it.copy(city = it.city.ifBlank { place.displayName.substringBefore(',') })
                        }
                    }
                }
        }
    }

    private fun loadWeather(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            container.weatherRepository
                .weatherForRace(latitude, longitude, _state.value.startDateTime)
                .onSuccess { info -> update { it.copy(weather = info) } }
        }
    }

    // ---------------------------- Percurso ---------------------------------

    /** Recupera o percurso gravado no ecrã de tracking, se existir. */
    fun consumeRouteDraft() {
        if (!RouteDraftHolder.hasDraft()) return
        val points = RouteDraftHolder.points
        update {
            it.copy(
                route = points,
                distanceMeters = RouteDraftHolder.distanceMeters,
                routeSource = RouteDraftHolder.source,
                latitude = if (it.hasLocation) it.latitude else points.first().latitude,
                longitude = if (it.hasLocation) it.longitude else points.first().longitude
            )
        }
        RouteDraftHolder.clear()
    }

    /** Importa um percurso a partir de um ficheiro KML escolhido pelo utilizador. */
    fun importKml(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        KmlParser.parse(input)
                    } ?: error("Ficheiro KML ilegivel")
                }
            }

            result.onSuccess { kmlRoute ->
                if (kmlRoute.points.isEmpty()) {
                    update { it.copy(errorMessage = ERROR_EMPTY_KML) }
                    return@onSuccess
                }
                val first = kmlRoute.points.first()
                update {
                    it.copy(
                        route = kmlRoute.points,
                        routeSource = RouteSource.KML_IMPORT,
                        distanceMeters = GeoUtils.routeLengthMeters(kmlRoute.points),
                        name = it.name.ifBlank { kmlRoute.name },
                        latitude = first.latitude,
                        longitude = first.longitude,
                        errorMessage = null
                    )
                }
                loadWeather(first.latitude, first.longitude)
            }.onFailure { throwable ->
                update { it.copy(errorMessage = throwable.localizedMessage ?: ERROR_EMPTY_KML) }
            }
        }
    }

    fun clearRoute() {
        update { it.copy(route = emptyList(), distanceMeters = 0.0, routeSource = RouteSource.MANUAL) }
    }

    // ------------------------------ Gravação -------------------------------

    fun save(onSaved: (String) -> Unit) {
        val current = _state.value
        if (current.name.isBlank()) {
            update { it.copy(errorMessage = ERROR_NAME_REQUIRED) }
            return
        }
        if (!current.hasLocation) {
            update { it.copy(errorMessage = ERROR_LOCATION_REQUIRED) }
            return
        }

        update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val photoUrl = current.photoUri?.let { uri ->
                container.storageRepository.uploadImage(uri, folder = "races")
                    .onFailure { Log.w(TAG, "Envio da fotografia da prova falhou", it) }
                    .getOrNull()
            }

            val authorUid = container.authRepository.currentUid.orEmpty()
            val race = Race(
                id = "",
                name = current.name.trim(),
                description = current.description.trim(),
                type = current.type,
                status = current.status,
                city = current.city.trim(),
                startDateTime = current.startDateTime,
                startLatitude = current.latitude,
                startLongitude = current.longitude,
                distanceMeters = if (current.distanceMeters > 0) {
                    current.distanceMeters
                } else {
                    GeoUtils.routeLengthMeters(current.route)
                },
                route = current.route,
                routeSource = current.routeSource,
                visibility = current.visibility,
                visibleTo = VisibilityTokens.forRecord(current.visibility, authorUid),
                photoUrl = photoUrl,
                organizerPhone = current.organizerPhone.trim(),
                authorId = authorUid,
                authorName = container.authRepository.currentDisplayName
            )

            container.raceRepository.createRace(race)
                .onSuccess { saved ->
                    update { it.copy(isSaving = false) }
                    onSaved(saved.id)
                }
                .onFailure { throwable ->
                    update {
                        it.copy(isSaving = false, errorMessage = throwable.localizedMessage)
                    }
                }
        }
    }

    fun clearError() = update { it.copy(errorMessage = null) }

    private inline fun update(block: (RaceFormState) -> RaceFormState) {
        _state.value = block(_state.value)
    }

    companion object {
        private const val TAG = "RaceFormViewModel"
        const val ERROR_NAME_REQUIRED = "error_name_required"
        const val ERROR_LOCATION_REQUIRED = "error_location_required"
        const val ERROR_NO_LOCATION = "error_no_location"
        const val ERROR_EMPTY_KML = "error_empty_kml"
    }
}
