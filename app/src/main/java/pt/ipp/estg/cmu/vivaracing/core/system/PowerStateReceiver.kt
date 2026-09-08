package pt.ipp.estg.cmu.vivaracing.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import pt.ipp.estg.cmu.vivaracing.core.preferences.UserPreferences

/**
 * Adaptação a eventos do sistema relacionados com energia.
 *
 * Quando o sistema difunde `ACTION_BATTERY_LOW`, ou quando o modo de poupança
 * de energia é ativado, a aplicação passa a usar uma cadência de localização
 * mais espaçada e prioridade equilibrada em vez de GPS de alta precisão. O
 * estado é guardado nas preferências, sendo lido pelo serviço de gravação e
 * pelos trabalhos periódicos.
 */
class PowerStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val preferences = UserPreferences(context)
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                Log.i(TAG, "Bateria fraca: a reduzir a cadência de localização")
                preferences.setPowerSavingEnabled(true)
            }

            Intent.ACTION_BATTERY_OKAY -> {
                Log.i(TAG, "Bateria reposta: a retomar a cadência normal")
                preferences.setPowerSavingEnabled(false)
            }

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                preferences.setPowerSavingEnabled(powerManager.isPowerSaveMode)
            }
        }
    }

    private companion object {
        const val TAG = "PowerStateReceiver"
    }
}
