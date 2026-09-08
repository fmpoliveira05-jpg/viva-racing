package pt.ipp.estg.cmu.vivaracing.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pt.ipp.estg.cmu.vivaracing.MainActivity
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.Constants

/**
 * Centraliza a criação de canais e o envio de notificações.
 *
 * Existem dois canais distintos para que o utilizador possa silenciar os
 * avisos da comunidade sem perder a notificação permanente exigida pelo
 * serviço em primeiro plano de gravação de percurso.
 */
@SuppressLint("MissingPermission")
class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val trackingChannel = NotificationChannel(
            Constants.CHANNEL_TRACKING,
            context.getString(R.string.notification_channel_tracking),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_tracking_description)
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            Constants.CHANNEL_ALERTS,
            context.getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_alerts_description)
        }

        manager.createNotificationChannel(trackingChannel)
        manager.createNotificationChannel(alertsChannel)
    }

    /** Notificação persistente associada ao serviço em primeiro plano. */
    fun buildTrackingNotification(title: String, content: String): Notification =
        NotificationCompat.Builder(context, Constants.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification_route)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(null))
            .build()

    fun updateTrackingNotification(title: String, content: String) {
        if (!hasPermission()) return
        manager.notify(Constants.NOTIFICATION_TRACKING, buildTrackingNotification(title, content))
    }

    /** Notificação de um alerta ou de uma nova participação publicada. */
    fun showCommunityNotification(
        id: Int,
        title: String,
        content: String,
        deepLinkRoute: String? = null
    ) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(deepLinkRoute))
            .build()
        manager.notify(Constants.NOTIFICATION_ALERT_BASE + (id and 0x0000FFFF), notification)
    }

    fun cancelTrackingNotification() {
        manager.cancel(Constants.NOTIFICATION_TRACKING)
    }

    private fun openAppIntent(deepLinkRoute: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLinkRoute != null) {
                putExtra(Constants.EXTRA_DEEP_LINK_ROUTE, deepLinkRoute)
            }
        }
        return PendingIntent.getActivity(
            context,
            deepLinkRoute?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
