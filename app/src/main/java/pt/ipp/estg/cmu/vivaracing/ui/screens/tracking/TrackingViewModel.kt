package pt.ipp.estg.cmu.vivaracing.ui.screens.tracking

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.Constants
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RouteSource
import pt.ipp.estg.cmu.vivaracing.service.RouteTrackingService
import pt.ipp.estg.cmu.vivaracing.service.TrackingState
import pt.ipp.estg.cmu.vivaracing.ui.navigation.Destinations
import pt.ipp.estg.cmu.vivaracing.ui.screens.races.RouteDraftHolder
import java.util.UUID

/**
 * ViewModel do ecrã de gravação de percurso.
 *
 * Não guarda o percurso: essa responsabilidade é do serviço em primeiro plano,
 * que continua a executar mesmo com a aplicação em segundo plano. O ViewModel
 * limita-se a enviar comandos ao serviço através de `Intent` e a observar o
 * estado publicado por este.
 */
class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    val trackingState: StateFlow<TrackingState> = RouteTrackingService.state

    private val _race = MutableStateFlow<Race?>(null)
    val race: StateFlow<Race?> = _race.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var mode: String = Destinations.MODE_OFFICIAL_ROUTE
    private var raceId: String = Destinations.NEW_RACE_PLACEHOLDER

    fun bind(raceId: String, mode: String) {
        this.raceId = raceId
        this.mode = mode
        if (raceId != Destinations.NEW_RACE_PLACEHOLDER) {
            viewModelScope.launch {
                _race.value = container.raceRepository.findRace(raceId)
            }
        }
    }

    fun start() {
        val label = _race.value?.name
            ?: getApplication<Application>().getString(
                pt.ipp.estg.cmu.vivaracing.R.string.tracking_new_route
            )
        val intent = Intent(getApplication(), RouteTrackingService::class.java).apply {
            action = Constants.ACTION_START_TRACKING
            putExtra(Constants.EXTRA_SESSION_ID, UUID.randomUUID().toString())
            putExtra(Constants.EXTRA_SESSION_LABEL, label)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun pause() = sendCommand(Constants.ACTION_PAUSE_TRACKING)

    fun resume() = sendCommand(Constants.ACTION_RESUME_TRACKING)

    /**
     * Termina a gravação e persiste o resultado.
     *
     * No modo de percurso oficial o trajeto é devolvido ao formulário de
     * criação de prova; no modo amador é criada uma participação com o tempo
     * pessoal, respeitando a preferência de anonimato do utilizador.
     */
    fun stopAndSave(onFinished: () -> Unit) {
        val snapshot = trackingState.value
        sendCommand(Constants.ACTION_STOP_TRACKING)

        if (snapshot.points.isEmpty()) {
            RouteTrackingService.resetState()
            onFinished()
            return
        }

        if (mode == Destinations.MODE_OFFICIAL_ROUTE) {
            RouteDraftHolder.store(
                points = snapshot.points,
                distanceMeters = snapshot.distanceMeters,
                source = RouteSource.LIVE_RECORDING
            )
            RouteTrackingService.resetState()
            onFinished()
            return
        }

        val race = _race.value
        if (race == null) {
            RouteTrackingService.resetState()
            onFinished()
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            val anonymous = container.preferences.snapshot().anonymousResults
            val elapsed = snapshot.elapsedSeconds.coerceAtLeast(1L)
            val run = AmateurRun(
                id = "",
                raceId = race.id,
                raceName = race.name,
                userId = container.authRepository.currentUid.orEmpty(),
                displayName = container.authRepository.currentDisplayName,
                anonymous = anonymous,
                startTime = snapshot.startTime,
                endTime = System.currentTimeMillis(),
                durationSeconds = elapsed,
                distanceMeters = snapshot.distanceMeters,
                averageSpeedKmh = (snapshot.distanceMeters / elapsed) * 3.6,
                steps = snapshot.steps,
                route = snapshot.points,
                // A participação herda a visibilidade da prova percorrida.
                visibleTo = race.visibleTo
            )
            container.amateurRunRepository.saveRun(run)
            _isSaving.value = false
            RouteTrackingService.resetState()
            onFinished()
        }
    }

    fun discard(onFinished: () -> Unit) {
        sendCommand(Constants.ACTION_STOP_TRACKING)
        RouteTrackingService.resetState()
        onFinished()
    }

    private fun sendCommand(action: String) {
        val intent = Intent(getApplication(), RouteTrackingService::class.java).apply {
            this.action = action
        }
        getApplication<Application>().startService(intent)
    }
}
