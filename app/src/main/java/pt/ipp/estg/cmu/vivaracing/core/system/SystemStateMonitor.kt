package pt.ipp.estg.cmu.vivaracing.core.system

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Perfil de recolha de dados adotado em função do estado do dispositivo.
 *
 * Traduz várias condições do sistema numa única decisão operacional, evitando
 * que cada componente tenha de reavaliar isoladamente bateria, temperatura e
 * modo de poupança.
 */
enum class TrackingQuality {
    /** GPS de alta precisão, cadência normal. */
    HIGH,

    /** Prioridade equilibrada, cadência mais espaçada. */
    BALANCED,

    /** Cadência mínima; usado em sobreaquecimento ou bateria crítica. */
    LOW
}

/** Fotografia do estado do dispositivo relevante para a aplicação. */
data class SystemState(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val powerSaveMode: Boolean = false,
    val thermalStatus: Int = 0,
    val isOnline: Boolean = true,
    val isMeteredNetwork: Boolean = false,
    val dataSaverEnabled: Boolean = false,
    val backgroundRestricted: Boolean = false,
    val ignoringBatteryOptimizations: Boolean = true
) {

    val isBatteryLow: Boolean get() = !isCharging && batteryPercent in 1..LOW_BATTERY_PERCENT

    val isBatteryCritical: Boolean get() = !isCharging && batteryPercent in 1..CRITICAL_BATTERY_PERCENT

    val isOverheating: Boolean
        get() = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

    val isWarm: Boolean
        get() = thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE

    /**
     * Indica se as transferências pesadas, sobretudo as fotografias, devem
     * ser adiadas. Cobre tanto a rede limitada como a poupança de dados
     * ativada pelo utilizador nas definições do Android.
     */
    val shouldDeferHeavyTransfers: Boolean
        get() = !isOnline || dataSaverEnabled || (isMeteredNetwork && isBatteryLow)

    /** Perfil de recolha resultante das condições atuais. */
    val trackingQuality: TrackingQuality
        get() = when {
            isOverheating || isBatteryCritical -> TrackingQuality.LOW
            powerSaveMode || isBatteryLow || isWarm -> TrackingQuality.BALANCED
            else -> TrackingQuality.HIGH
        }

    companion object {
        const val LOW_BATTERY_PERCENT = 20
        const val CRITICAL_BATTERY_PERCENT = 8
    }
}

/**
 * Observa o estado do dispositivo e traduz esse estado em decisões concretas
 * de funcionamento.
 *
 * O enunciado pede estratégias de adaptabilidade a eventos do sistema. Além
 * da bateria fraca, foram consideradas as condições que mais afetam uma
 * aplicação que corre durante horas com o GPS ativo em dispositivos Android
 * modernos:
 *
 *  - **estado térmico** (`PowerManager.getCurrentThermalStatus`, API 29+): o
 *    sistema reduz o desempenho quando o dispositivo aquece; a aplicação
 *    antecipa-se e espaça a aquisição de posição;
 *  - **poupança de dados e rede limitada**: as fotografias só são enviadas
 *    quando a ligação não é limitada, evitando consumir o plano de dados do
 *    utilizador;
 *  - **estado de carregamento**: com o dispositivo a carregar, a precisão
 *    máxima é mantida mesmo com bateria baixa;
 *  - **restrição de execução em segundo plano**
 *    (`ActivityManager.isBackgroundRestricted`, API 28+) e isenção de
 *    otimizações de bateria: permitem avisar o utilizador de que as
 *    notificações com a aplicação encerrada podem não chegar.
 */
class SystemStateMonitor(private val context: Context) {

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun snapshot(): SystemState {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }

        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
        ) != true

        val dataSaver = connectivityManager.restrictBackgroundStatus ==
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activityManager.isBackgroundRestricted
        } else {
            false
        }

        return SystemState(
            batteryPercent = percent,
            isCharging = charging,
            powerSaveMode = powerManager.isPowerSaveMode,
            thermalStatus = thermal,
            isOnline = online,
            isMeteredNetwork = metered,
            dataSaverEnabled = dataSaver,
            backgroundRestricted = restricted,
            ignoringBatteryOptimizations =
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    /**
     * Emite o estado sempre que ocorre um evento relevante do sistema.
     *
     * Os eventos escutados são os que o Android continua a difundir para
     * recetores registados em tempo de execução, evitando sondagem periódica.
     */
    fun observe(): Flow<SystemState> = callbackFlow {
        trySend(snapshot())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                trySend(snapshot())
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager.OnThermalStatusChangedListener { trySend(snapshot()) }.also {
                powerManager.addThermalStatusListener(it)
            }
        } else {
            null
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
                runCatching { powerManager.removeThermalStatusListener(thermalListener) }
            }
        }
    }.distinctUntilChanged()
}
