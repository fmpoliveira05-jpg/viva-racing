package pt.ipp.estg.cmu.vivaracing.ui.navigation

/**
 * Rotas de navegação da aplicação.
 *
 * As rotas com argumentos são definidas com o padrão esperado pelo componente
 * Navigation e acompanhadas de funções auxiliares que constroem o caminho
 * concreto, evitando a concatenação manual de cadeias de caracteres espalhada
 * pelos vários ecrãs.
 */
object Destinations {

    // Autenticação
    const val LOGIN = "login"
    const val REGISTER = "register"

    // Ecrãs principais
    const val HOME = "home"
    const val RACE_LIST = "races/list"
    const val ALERT_LIST = "alerts/list"
    const val AMATEUR_LIST = "amateur/list"

    /**
     * Mapa único da aplicação, com camadas comutáveis para provas, alertas e
     * participações amadoras. Substitui os três mapas separados que existiam.
     */
    const val MAP = "map"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SENSORS = "sensors"
    const val FRIENDS = "friends"
    const val ATHLETES = "athletes"
    const val ABOUT = "about"

    // Ecrãs de detalhe e formulários
    const val ARG_RACE_ID = "raceId"
    const val ARG_ALERT_ID = "alertId"
    const val ARG_RUN_ID = "runId"
    const val ARG_MODE = "mode"
    const val ARG_ATHLETE_ID = "athleteId"

    const val RACE_DETAIL = "race_detail/{$ARG_RACE_ID}"
    const val RACE_FORM = "race_form"
    const val ALERT_FORM = "alert_form/{$ARG_RACE_ID}"
    const val ALERT_DETAIL = "alert_detail/{$ARG_ALERT_ID}"
    const val AMATEUR_DETAIL = "amateur_detail/{$ARG_RUN_ID}"
    const val TRACKING = "tracking/{$ARG_RACE_ID}/{$ARG_MODE}"
    const val ATHLETE_DETAIL = "athlete_detail/{$ARG_ATHLETE_ID}"

    /** Modo de utilização do ecrã de gravação de percurso. */
    const val MODE_OFFICIAL_ROUTE = "official"
    const val MODE_AMATEUR_RUN = "amateur"

    const val NEW_RACE_PLACEHOLDER = "none"

    fun raceDetailRoute(raceId: String): String = "race_detail/$raceId"

    fun alertDetailRoute(alertId: String): String = "alert_detail/$alertId"

    fun alertFormRoute(raceId: String): String = "alert_form/$raceId"

    fun amateurRunDetailRoute(runId: String): String = "amateur_detail/$runId"

    fun trackingRoute(raceId: String, mode: String): String = "tracking/$raceId/$mode"

    fun athleteDetailRoute(athleteUid: String): String = "athlete_detail/$athleteUid"
}
