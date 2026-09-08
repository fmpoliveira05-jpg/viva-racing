package pt.ipp.estg.cmu.vivaracing.core

/** Constantes partilhadas entre serviços, workers e camada de apresentação. */
object Constants {

    // Canais de notificação
    const val CHANNEL_TRACKING = "viva_racing_tracking"
    const val CHANNEL_ALERTS = "viva_racing_alerts"

    // Identificadores de notificação
    const val NOTIFICATION_TRACKING = 1001
    const val NOTIFICATION_ALERT_BASE = 2000

    // Ações do serviço de gravação de percurso
    const val ACTION_START_TRACKING = "pt.ipp.estg.cmu.vivaracing.START_TRACKING"
    const val ACTION_STOP_TRACKING = "pt.ipp.estg.cmu.vivaracing.STOP_TRACKING"
    const val ACTION_PAUSE_TRACKING = "pt.ipp.estg.cmu.vivaracing.PAUSE_TRACKING"
    const val ACTION_RESUME_TRACKING = "pt.ipp.estg.cmu.vivaracing.RESUME_TRACKING"

    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_SESSION_LABEL = "extra_session_label"

    // Trabalho periódico
    const val WORK_ALERT_POLLING = "viva_racing_alert_polling"
    const val WORK_RACE_SYNC = "viva_racing_race_sync"

    // Tópico do Firebase Cloud Messaging usado para avisos gerais
    const val FCM_TOPIC_RACES = "races"

    // Intervalos de atualização de localização (milissegundos)
    const val LOCATION_INTERVAL_NORMAL = 5_000L
    const val LOCATION_INTERVAL_SAVING = 20_000L
    const val LOCATION_INTERVAL_MINIMAL = 60_000L
    const val LOCATION_MIN_DISTANCE_METERS = 5f

    // Navegação para um alerta específico a partir de uma notificação
    const val EXTRA_DEEP_LINK_ROUTE = "extra_deep_link_route"
}
