package pt.ipp.estg.cmu.vivaracing

import android.app.Application
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.core.Constants
import pt.ipp.estg.cmu.vivaracing.core.system.LocaleManager
import pt.ipp.estg.cmu.vivaracing.data.AppContainer
import pt.ipp.estg.cmu.vivaracing.data.SyncManager
import pt.ipp.estg.cmu.vivaracing.worker.WorkScheduler

/**
 * Classe de aplicação.
 *
 * Constrói o contentor de dependências, cria os canais de notificação, aplica
 * o idioma guardado nas preferências, agenda o trabalho periódico e liga ou
 * desliga a sincronização com o Firestore em função do estado de autenticação.
 */
class VivaRacingApp : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Âmbito de coroutines com o tempo de vida do processo. É usado para
     * trabalho que não deve ser cancelado quando um ecrã é destruído, como a
     * sincronização contínua com a base de dados online.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()

        container = AppContainer(this)
        syncManager = SyncManager(container, applicationScope)

        container.notificationHelper.createChannels()
        LocaleManager.apply(container.preferences.snapshot().language)
        WorkScheduler.schedulePeriodicWork(this)

        observeAuthentication()
        subscribeToBroadcastTopic()
    }

    private fun observeAuthentication() {
        applicationScope.launch {
            container.authRepository.observeSession().collect { session ->
                val uid = session.uid
                if (uid != null) {
                    container.authRepository.ensureProfile(uid)
                    syncManager.start(session)
                } else {
                    syncManager.stop()
                    container.socialRepository.clearLocalSocialData()
                }
            }
        }
    }

    private fun subscribeToBroadcastTopic() {
        FirebaseMessaging.getInstance()
            .subscribeToTopic(Constants.FCM_TOPIC_RACES)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Subscricao do tópico de difusao falhou", task.exception)
                }
            }
    }

    private companion object {
        const val TAG = "VivaRacingApp"
    }
}
