package pt.ipp.estg.cmu.vivaracing.ui.screens.alerts

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.AthleteSubscription
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert

/** Estado do formulário de publicação de um alerta. */
data class AlertFormState(
    val race: Race? = null,
    val type: AlertType = AlertType.ATHLETE_PASSING,
    val bibNumber: String = "",
    val athleteUserId: String = "",
    val athleteName: String = "",
    val athleteOptions: List<AthleteSubscription> = emptyList(),
    val message: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUri: Uri? = null,
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val hasLocation: Boolean get() = latitude != 0.0 || longitude != 0.0
}

/**
 * ViewModel do formulário de alertas.
 *
 * A localização do alerta é obtida do sensor de localização do dispositivo, e
 * não introduzida manualmente: o enunciado exige que o registo da passagem de
 * um atleta corresponda ao local onde o observador se encontra.
 */
class AlertFormViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _state = MutableStateFlow(AlertFormState())
    val state: StateFlow<AlertFormState> = _state.asStateFlow()

    private var boundRaceId: String? = null

    fun bind(raceId: String) {
        if (boundRaceId == raceId) return
        boundRaceId = raceId
        viewModelScope.launch {
            val race = container.raceRepository.findRace(raceId)
            _state.value = _state.value.copy(race = race)
            captureLocation()
        }
        viewModelScope.launch {
            container.socialRepository.observeAthleteSubscriptions().collect { athletes ->
                _state.value = _state.value.copy(athleteOptions = athletes)
            }
        }
    }

    fun onTypeChange(type: AlertType) {
        _state.value = _state.value.copy(type = type)
    }

    fun onBibChange(value: String) {
        _state.value = _state.value.copy(bibNumber = value.filter { it.isDigit() })
    }

    /**
     * Associa o alerta a um atleta subscrito.
     *
     * O alerta passa a transportar o identificador do atleta, e não apenas o
     * número de dorsal. É isto que permite notificar com exatidão quem
     * subscreveu essa pessoa, independentemente do dorsal que lhe tenha sido
     * atribuído em cada prova.
     */
    fun onAthleteSelected(athlete: AthleteSubscription?) {
        _state.value = if (athlete == null) {
            _state.value.copy(athleteUserId = "", athleteName = "")
        } else {
            _state.value.copy(
                athleteUserId = athlete.athleteUid,
                athleteName = athlete.username,
                bibNumber = athlete.bibNumber.ifBlank { _state.value.bibNumber }
            )
        }
    }

    fun onMessageChange(value: String) {
        _state.value = _state.value.copy(message = value)
    }

    fun onPhotoSelected(uri: Uri?) {
        _state.value = _state.value.copy(photoUri = uri)
    }

    fun captureLocation() {
        _state.value = _state.value.copy(isLocating = true)
        viewModelScope.launch {
            val location = container.locationProvider.lastKnownLocation()
            _state.value = if (location != null) {
                _state.value.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    isLocating = false
                )
            } else {
                _state.value.copy(isLocating = false, errorMessage = ERROR_NO_LOCATION)
            }
        }
    }

    fun publish(onPublished: () -> Unit) {
        val current = _state.value
        val race = current.race
        if (race == null) {
            _state.value = current.copy(errorMessage = ERROR_RACE_MISSING)
            return
        }
        if (!current.hasLocation) {
            _state.value = current.copy(errorMessage = ERROR_NO_LOCATION)
            return
        }
        if (current.type == AlertType.ATHLETE_PASSING && current.bibNumber.isBlank()) {
            _state.value = current.copy(errorMessage = ERROR_BIB_REQUIRED)
            return
        }

        _state.value = current.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val photoUrl = current.photoUri?.let { uri ->
                container.storageRepository.uploadImage(uri, folder = "alerts")
                    .onFailure { Log.w(TAG, "Envio da fotografia do alerta falhou", it) }
                    .getOrNull()
            }

            val alert = RaceAlert(
                id = "",
                raceId = race.id,
                raceName = race.name,
                type = current.type,
                athleteBib = current.bibNumber.toIntOrNull(),
                athleteUserId = current.athleteUserId,
                athleteName = current.athleteName,
                message = current.message.trim(),
                latitude = current.latitude,
                longitude = current.longitude,
                timestamp = System.currentTimeMillis(),
                authorId = container.authRepository.currentUid.orEmpty(),
                authorName = container.authRepository.currentDisplayName,
                photoUrl = photoUrl,
                // O alerta herda a visibilidade da prova a que pertence.
                visibleTo = race.visibleTo
            )

            container.alertRepository.publishAlert(alert)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false)
                    onPublished()
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        errorMessage = throwable.localizedMessage
                    )
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        private const val TAG = "AlertFormViewModel"
        const val ERROR_NO_LOCATION = "error_no_location"
        const val ERROR_RACE_MISSING = "error_race_missing"
        const val ERROR_BIB_REQUIRED = "error_bib_required"
    }
}
