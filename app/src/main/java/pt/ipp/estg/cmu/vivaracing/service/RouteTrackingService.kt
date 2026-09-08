package pt.ipp.estg.cmu.vivaracing.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.Constants
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.core.system.TrackingQuality
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.local.entity.TrackPointEntity
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import java.util.UUID

/** Estado publicado pelo serviço de gravação de percurso. */
data class TrackingState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val sessionId: String = "",
    val label: String = "",
    val startTime: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val steps: Int = 0,
    val points: List<GeoPoint> = emptyList()
) {
    val averageSpeedKmh: Double
        get() = if (elapsedSeconds > 0) (distanceMeters / elapsedSeconds) * 3.6 else 0.0
}

/**
 * Serviço em primeiro plano responsável pela gravação do percurso.
 *
 * Cumpre três requisitos do enunciado em simultâneo:
 *  - regista o percurso de uma prova oficial ao vivo;
 *  - cronometra a participação amadora de um utilizador;
 *  - continua ativo com a aplicação em segundo plano, mostrando a notificação
 *    permanente obrigatória para serviços em primeiro plano.
 *
 * A cadência de aquisição de localização adapta-se ao estado do dispositivo:
 * com bateria fraca ou modo de poupança ativo, o intervalo aumenta e a
 * prioridade passa de GPS para uma estratégia equilibrada.
 */
class RouteTrackingService : LifecycleService(), SensorEventListener {

    private lateinit var app: VivaRacingApp

    private var locationJob: Job? = null
    private var tickerJob: Job? = null

    private var sensorManager: SensorManager? = null
    private var stepCounter: Sensor? = null
    private var initialStepCount: Float? = null

    private var systemStateJob: Job? = null
    private var currentQuality: TrackingQuality? = null
    private var lastPoint: GeoPoint? = null
    private var accumulatedSeconds = 0L
    private var lastResumeTime = 0L

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as VivaRacingApp
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            Constants.ACTION_START_TRACKING -> {
                val sessionId = intent.getStringExtra(Constants.EXTRA_SESSION_ID)
                    ?: UUID.randomUUID().toString()
                val label = intent.getStringExtra(Constants.EXTRA_SESSION_LABEL).orEmpty()
                startTracking(sessionId, label)
            }

            Constants.ACTION_PAUSE_TRACKING -> pauseTracking()
            Constants.ACTION_RESUME_TRACKING -> resumeTracking()
            Constants.ACTION_STOP_TRACKING -> stopTracking()
            else -> Log.w(TAG, "Intent sem ação reconhecida: ${intent?.action}")
        }

        // O serviço é recriado pelo sistema caso seja destruído por falta de
        // memória, mas sem reentregar o último intent: o estado da sessão esta
        // guardado nas preferências e na base de dados local.
        return START_STICKY
    }

    // ----------------------------- Ciclo de vida ---------------------------

    private fun startTracking(sessionId: String, label: String) {
        if (_state.value.isRunning) return

        val now = System.currentTimeMillis()
        accumulatedSeconds = 0L
        lastResumeTime = now
        lastPoint = null
        initialStepCount = null

        _state.value = TrackingState(
            isRunning = true,
            isPaused = false,
            sessionId = sessionId,
            label = label,
            startTime = now
        )
        app.container.preferences.activeTrackingSession = sessionId

        val notification = app.container.notificationHelper.buildTrackingNotification(
            title = getString(R.string.tracking_notification_title),
            content = getString(R.string.tracking_notification_content, label)
        )

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, Constants.NOTIFICATION_TRACKING, notification, serviceType)

        registerStepCounter()
        startLocationUpdates()
        startTicker()
        observeSystemState()
    }

    private fun pauseTracking() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        accumulatedSeconds += (System.currentTimeMillis() - lastResumeTime) / 1_000
        locationJob?.cancel()
        locationJob = null
        _state.value = _state.value.copy(isPaused = true)
        updateNotification()
    }

    private fun resumeTracking() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        lastResumeTime = System.currentTimeMillis()
        _state.value = _state.value.copy(isPaused = false)
        startLocationUpdates()
        updateNotification()
    }

    private fun stopTracking() {
        locationJob?.cancel()
        tickerJob?.cancel()
        systemStateJob?.cancel()
        locationJob = null
        tickerJob = null
        sensorManager?.unregisterListener(this)

        if (!_state.value.isPaused && _state.value.isRunning) {
            accumulatedSeconds += (System.currentTimeMillis() - lastResumeTime) / 1_000
        }

        _state.value = _state.value.copy(
            isRunning = false,
            isPaused = false,
            elapsedSeconds = accumulatedSeconds
        )
        app.container.preferences.activeTrackingSession = null

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationJob?.cancel()
        tickerJob?.cancel()
        systemStateJob?.cancel()
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    // -------------------------- Aquisição de dados -------------------------

    /**
     * Inicia a recolha de posições com os parâmetros adequados ao estado do
     * dispositivo.
     *
     * O perfil resulta da combinação de três fontes: a preferência do
     * utilizador, o estado energético e térmico reportado pelo sistema e a
     * marcação de poupança guardada pelo recetor de eventos. Sempre que o
     * estado muda, esta função é reexecutada e a subscrição de localização e
     * substituída.
     */
    private fun startLocationUpdates() {
        locationJob?.cancel()
        val settings = app.container.preferences.snapshot()
        val systemState = app.container.systemStateMonitor.snapshot()

        val quality = when {
            !settings.highAccuracyTracking -> TrackingQuality.BALANCED
            settings.powerSavingEnabled -> TrackingQuality.BALANCED
            else -> systemState.trackingQuality
        }

        val highAccuracy = quality == TrackingQuality.HIGH
        val interval = when (quality) {
            TrackingQuality.HIGH -> Constants.LOCATION_INTERVAL_NORMAL
            TrackingQuality.BALANCED -> Constants.LOCATION_INTERVAL_SAVING
            TrackingQuality.LOW -> Constants.LOCATION_INTERVAL_MINIMAL
        }
        currentQuality = quality

        Log.i(
            TAG,
            "Perfil $quality: localizacao a cada ${interval}ms " +
                "(bateria ${systemState.batteryPercent}%, termico ${systemState.thermalStatus})"
        )

        locationJob = lifecycleScope.launch {
            app.container.locationProvider
                .locationUpdates(intervalMillis = interval, highAccuracy = highAccuracy)
                .collect { location ->
                    val point = GeoPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        timestamp = System.currentTimeMillis()
                    )
                    onNewPoint(point)
                }
        }
    }

    private suspend fun onNewPoint(point: GeoPoint) {
        val previous = lastPoint
        val increment = if (previous == null) 0.0 else GeoUtils.distanceMeters(previous, point)

        // Descarta saltos irrealistas provocados por leituras de baixa precisão.
        if (previous != null && increment > MAX_JUMP_METERS) {
            Log.w(TAG, "Ponto descartado por salto de ${increment.toInt()} m")
            return
        }

        lastPoint = point
        val current = _state.value
        _state.value = current.copy(
            distanceMeters = current.distanceMeters + increment,
            points = current.points + point
        )

        app.container.database.trackPointDao().insert(
            TrackPointEntity(
                sessionId = current.sessionId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                timestamp = point.timestamp
            )
        )
        updateNotification()
    }

    /**
     * Reage a alterações do estado do dispositivo durante a gravação. Se o
     * perfil de recolha deixar de ser adequado, por sobreaquecimento, queda
     * de bateria ou ativação da poupança de energia, a subscrição de
     * localização é reconfigurada sem interromper a sessão.
     */
    private fun observeSystemState() {
        systemStateJob?.cancel()
        systemStateJob = lifecycleScope.launch {
            app.container.systemStateMonitor.observe().collect { state ->
                val settings = app.container.preferences.snapshot()
                val target = when {
                    !settings.highAccuracyTracking -> TrackingQuality.BALANCED
                    settings.powerSavingEnabled -> TrackingQuality.BALANCED
                    else -> state.trackingQuality
                }
                if (target != currentQuality && _state.value.isRunning && !_state.value.isPaused) {
                    Log.i(TAG, "Estado do sistema alterou o perfil para $target")
                    startLocationUpdates()
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                delay(1_000)
                val current = _state.value
                if (!current.isRunning) break
                if (current.isPaused) continue
                val elapsed = accumulatedSeconds + (System.currentTimeMillis() - lastResumeTime) / 1_000
                _state.value = current.copy(elapsedSeconds = elapsed)
            }
        }
    }

    private fun registerStepCounter() {
        val sensor = stepCounter ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        val baseline = initialStepCount ?: total.also { initialStepCount = it }
        val steps = (total - baseline).toInt().coerceAtLeast(0)
        _state.value = _state.value.copy(steps = steps)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateNotification() {
        val current = _state.value
        val content = getString(
            R.string.tracking_notification_progress,
            Formatters.formatDistance(current.distanceMeters),
            Formatters.formatDuration(current.elapsedSeconds)
        )
        app.container.notificationHelper.updateTrackingNotification(
            title = getString(R.string.tracking_notification_title),
            content = content
        )
    }

    companion object {
        private const val TAG = "RouteTrackingService"
        private const val MAX_JUMP_METERS = 300.0

        private val _state = MutableStateFlow(TrackingState())

        /** Estado observável pela camada de apresentação. */
        val state: StateFlow<TrackingState> = _state.asStateFlow()

        fun resetState() {
            _state.value = TrackingState()
        }
    }
}
