package pt.ipp.estg.cmu.vivaracing.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/** Últimas leituras recolhidas dos sensores do dispositivo. */
data class SensorReadings(
    val lightAvailable: Boolean = false,
    val lightLux: Float = 0f,
    val accelerometerAvailable: Boolean = false,
    val accelerationMagnitude: Float = 0f,
    val stepCounterAvailable: Boolean = false,
    val proximityAvailable: Boolean = false,
    val proximityCentimeters: Float = 0f,
    val movementDetected: Boolean = false
) {
    /**
     * Limiar abaixo do qual se considera que o ambiente está pouco iluminado.
     * Corresponde, aproximadamente, a iluminação de um interior ao anoitecer.
     */
    val isLowLight: Boolean get() = lightAvailable && lightLux < LOW_LIGHT_THRESHOLD_LUX

    companion object {
        const val LOW_LIGHT_THRESHOLD_LUX = 20f
    }
}

/**
 * Serviço do tipo *bound* que centraliza a leitura dos sensores do
 * dispositivo (luminosidade ambiente, acelerómetro e proximidade).
 *
 * A opção por um serviço ligado permite que vários ecrãs partilhem os mesmos
 * ouvintes de sensores: os `SensorEventListener` são registados uma única vez,
 * quando o primeiro cliente se liga, e libertados quando o último se desliga,
 * o que reduz o consumo de energia.
 *
 * A leitura de luminosidade é usada para ativar automaticamente o tema escuro
 * quando o utilizador está a acompanhar uma prova em condições de pouca luz.
 */
class SensorMonitorService : Service(), SensorEventListener {

    inner class LocalBinder : Binder() {
        val service: SensorMonitorService get() = this@SensorMonitorService
    }

    private val binder = LocalBinder()

    private val _readings = MutableStateFlow(SensorReadings())
    val readings: StateFlow<SensorReadings> = _readings.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(SENSOR_SERVICE) as? SensorManager
        sensorManager = manager
        lightSensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        _readings.value = _readings.value.copy(
            lightAvailable = lightSensor != null,
            accelerometerAvailable = accelerometer != null,
            proximityAvailable = proximitySensor != null,
            stepCounterAvailable = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        registerListeners()
        return binder
    }

    override fun onRebind(intent: Intent?) {
        registerListeners()
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        sensorManager?.unregisterListener(this)
        // Devolver true permite receber onRebind em ligações seguintes.
        return true
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }

    private fun registerListeners() {
        val manager = sensorManager ?: return
        lightSensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = sensorEvent.values.firstOrNull() ?: return
                _readings.value = _readings.value.copy(lightLux = lux)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = sensorEvent.values.getOrElse(0) { 0f }
                val y = sensorEvent.values.getOrElse(1) { 0f }
                val z = sensorEvent.values.getOrElse(2) { 0f }
                // Remove-se a componente da gravidade para estimar o movimento.
                val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                _readings.value = _readings.value.copy(
                    accelerationMagnitude = magnitude,
                    movementDetected = kotlin.math.abs(magnitude) > MOVEMENT_THRESHOLD
                )
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = sensorEvent.values.firstOrNull() ?: return
                _readings.value = _readings.value.copy(proximityCentimeters = distance)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val MOVEMENT_THRESHOLD = 1.5f
    }
}
