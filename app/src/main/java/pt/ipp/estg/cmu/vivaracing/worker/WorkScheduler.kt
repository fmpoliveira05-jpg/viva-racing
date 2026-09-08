package pt.ipp.estg.cmu.vivaracing.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pt.ipp.estg.cmu.vivaracing.core.Constants
import java.util.concurrent.TimeUnit

/**
 * Agendamento centralizado do trabalho periódico.
 *
 * As restrições escolhidas seguem as boas práticas de eficiência energética
 * abordadas nas aulas: exige-se ligação à rede, a bateria não pode estar
 * fraca e é usada uma janela flexível, de modo a que o sistema possa agrupar
 * várias tarefas no mesmo despertar do dispositivo.
 */
object WorkScheduler {

    private const val REPEAT_INTERVAL_MINUTES = 30L
    private const val FLEX_INTERVAL_MINUTES = 10L
    private const val SYNC_INTERVAL_HOURS = 6L

    fun schedulePeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        val notificationConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val communityUpdates = PeriodicWorkRequestBuilder<CommunityUpdatesWorker>(
            REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES,
            FLEX_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(notificationConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag(Constants.WORK_ALERT_POLLING)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.WORK_ALERT_POLLING,
            ExistingPeriodicWorkPolicy.KEEP,
            communityUpdates
        )

        val uploadConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val pendingUploads = PeriodicWorkRequestBuilder<PendingUploadWorker>(
            SYNC_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(uploadConstraints)
            .addTag(Constants.WORK_RACE_SYNC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.WORK_RACE_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            pendingUploads
        )
    }

    fun cancelPeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(Constants.WORK_ALERT_POLLING)
        workManager.cancelUniqueWork(Constants.WORK_RACE_SYNC)
    }
}
