package pt.ipp.estg.cmu.vivaracing.ui.screens.races

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.WeatherInfo

/**
 * ViewModel do ecrã de detalhe de uma prova.
 *
 * Reúne quatro fontes: a prova, os alertas publicados, as participações
 * amadoras e a informação meteorológica obtida na API REST. Determina ainda se
 * o utilizador se encontra junto ao percurso, o que condiciona a possibilidade
 * de publicar alertas de passagem de atletas, tal como exigido no enunciado.
 */
class RaceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _raceId = MutableStateFlow("")

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()

    private val _isNearRoute = MutableStateFlow(false)
    val isNearRoute: StateFlow<Boolean> = _isNearRoute.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _race = MutableStateFlow<Race?>(null)
    val race: StateFlow<Race?> = _race.asStateFlow()

    private val _alerts = MutableStateFlow<List<RaceAlert>>(emptyList())
    val alerts: StateFlow<List<RaceAlert>> = _alerts.asStateFlow()

    private val _runs = MutableStateFlow<List<AmateurRun>>(emptyList())
    val runs: StateFlow<List<AmateurRun>> = _runs.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    val currentUid: String? get() = container.authRepository.currentUid

    /** Liga o ViewModel a uma prova concreta. */
    fun bind(raceId: String) {
        if (_raceId.value == raceId) return
        _raceId.value = raceId

        viewModelScope.launch {
            container.raceRepository.observeRace(raceId).collect { race ->
                _race.value = race
                if (race != null && _weather.value == null) {
                    loadWeather(race)
                    evaluateProximity(race)
                }
            }
        }
        viewModelScope.launch {
            container.alertRepository.observeByRace(raceId).collect { _alerts.value = it }
        }
        viewModelScope.launch {
            container.amateurRunRepository.observeByRace(raceId).collect { _runs.value = it }
        }
        viewModelScope.launch {
            container.raceRepository.observeIsSubscribed(raceId).collect { _isSubscribed.value = it }
        }
    }

    private fun loadWeather(race: Race) {
        if (race.startLatitude == 0.0 && race.startLongitude == 0.0) return
        viewModelScope.launch {
            container.weatherRepository.weatherForRace(
                latitude = race.startLatitude,
                longitude = race.startLongitude,
                startDateTime = race.startDateTime
            ).onSuccess { _weather.value = it }
        }
    }

    /**
     * Verifica se o utilizador está junto ao percurso da prova. Apenas quem
     * está no terreno pode registar a passagem de um atleta; os restantes
     * consultam as atualizações publicadas por terceiros.
     */
    private fun evaluateProximity(race: Race) {
        viewModelScope.launch {
            val location = container.locationProvider.lastKnownLocation() ?: return@launch
            val reference = race.route.ifEmpty {
                listOf(
                    pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint(
                        race.startLatitude,
                        race.startLongitude
                    )
                )
            }
            _isNearRoute.value = GeoUtils.isNearRoute(
                latitude = location.latitude,
                longitude = location.longitude,
                route = reference
            )
        }
    }

    fun toggleSubscription() {
        val race = _race.value ?: return
        val uid = currentUid ?: return
        viewModelScope.launch {
            if (_isSubscribed.value) {
                container.raceRepository.unsubscribe(uid, race.id)
                _message.value = MESSAGE_UNSUBSCRIBED
            } else {
                container.raceRepository.subscribe(uid, race)
                _message.value = MESSAGE_SUBSCRIBED
            }
        }
    }

    fun deleteRace(onDeleted: () -> Unit) {
        val race = _race.value ?: return
        if (race.authorId != currentUid) return
        viewModelScope.launch {
            container.raceRepository.deleteRace(race.id)
            onDeleted()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        const val MESSAGE_SUBSCRIBED = "message_subscribed"
        const val MESSAGE_UNSUBSCRIBED = "message_unsubscribed"
    }
}
