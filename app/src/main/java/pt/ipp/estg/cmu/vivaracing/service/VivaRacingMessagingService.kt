package pt.ipp.estg.cmu.vivaracing.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp

/**
 * Recebe as mensagens push enviadas através do Firebase Cloud Messaging.
 *
 * Permite que a organização de uma prova (ou o administrador da aplicação, a
 * partir da consola Firebase) difunda avisos para o tópico `races` mesmo com a
 * aplicação encerrada. Quando a mensagem inclui um bloco de dados com o
 * identificador do alerta, a notificação abre diretamente o ecrã de detalhe.
 */
class VivaRacingMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val container = (applicationContext as VivaRacingApp).container
        if (!container.preferences.snapshot().notificationsEnabled) return

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.notification_generic_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.notification_open_details)

        container.notificationHelper.showCommunityNotification(
            id = message.messageId?.hashCode() ?: title.hashCode(),
            title = title,
            content = body,
            deepLinkRoute = message.data["route"]
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Novo token de Firebase Cloud Messaging registado")
    }

    private companion object {
        const val TAG = "VivaRacingMessaging"
    }
}
