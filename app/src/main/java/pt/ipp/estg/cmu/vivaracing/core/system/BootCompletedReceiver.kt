package pt.ipp.estg.cmu.vivaracing.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pt.ipp.estg.cmu.vivaracing.worker.WorkScheduler

/**
 * Reagenda o trabalho periódico de deteção de novidades após o dispositivo
 * reiniciar, garantindo que o utilizador continua a receber notificações mesmo
 * que nunca volte a abrir a aplicação.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkScheduler.schedulePeriodicWork(context)
        }
    }
}
