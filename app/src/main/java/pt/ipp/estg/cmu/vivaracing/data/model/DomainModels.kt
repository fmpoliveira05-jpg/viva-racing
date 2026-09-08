package pt.ipp.estg.cmu.vivaracing.data.model

/**
 * Modelos de domínio da aplicação.
 *
 * Estes modelos são independentes da tecnologia de persistência: as entidades
 * Room e os documentos Firestore convertem-se de e para estas classes, o que
 * mantém a camada de apresentação isolada de detalhes de armazenamento.
 */

/** Ponto geográfico com marca temporal, usado em percursos e alertas. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = 0L
)

/** Tipo de prova registada pela comunidade. */
enum class RaceType {
    RUNNING, MARATHON, TRAIL, CYCLING, OTHER;

    companion object {
        fun fromName(value: String?): RaceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

/** Estado do ciclo de vida de uma prova. */
enum class RaceStatus {
    SCHEDULED, ONGOING, FINISHED;

    companion object {
        fun fromName(value: String?): RaceStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SCHEDULED
    }
}

/** Natureza do alerta publicado por um observador da prova. */
enum class AlertType {
    RACE_START, ATHLETE_PASSING, RACE_FINISH, INCIDENT;

    companion object {
        fun fromName(value: String?): AlertType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ATHLETE_PASSING
    }
}

/** Origem do percurso associado a uma prova. */
enum class RouteSource {
    LIVE_RECORDING, KML_IMPORT, MANUAL;

    companion object {
        fun fromName(value: String?): RouteSource =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MANUAL
    }
}

/** Prova registada por um utilizador e partilhada com toda a comunidade. */
data class Race(
    val id: String,
    val name: String,
    val description: String = "",
    val type: RaceType = RaceType.RUNNING,
    val status: RaceStatus = RaceStatus.SCHEDULED,
    val city: String = "",
    val startDateTime: Long = 0L,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val route: List<GeoPoint> = emptyList(),
    val routeSource: RouteSource = RouteSource.MANUAL,
    val visibility: RaceVisibility = RaceVisibility.PUBLIC,
    val visibleTo: List<String> = listOf(VisibilityTokens.PUBLIC),
    val photoUrl: String? = null,
    val organizerPhone: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val pendingSync: Boolean = false
)

/** Alerta publicado durante a prova (início, passagem de atleta, fim, incidente). */
data class RaceAlert(
    val id: String,
    val raceId: String,
    val raceName: String = "",
    val type: AlertType = AlertType.ATHLETE_PASSING,
    val athleteBib: Int? = null,
    val athleteUserId: String = "",
    val athleteName: String = "",
    val message: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val authorId: String = "",
    val authorName: String = "",
    val photoUrl: String? = null,
    val visibleTo: List<String> = listOf(VisibilityTokens.PUBLIC),
    val pendingSync: Boolean = false
)

/** Participação amadora: tempo pessoal de um utilizador no percurso de uma prova. */
data class AmateurRun(
    val id: String,
    val raceId: String,
    val raceName: String = "",
    val userId: String = "",
    val displayName: String = "",
    val anonymous: Boolean = false,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averageSpeedKmh: Double = 0.0,
    val steps: Int = 0,
    val route: List<GeoPoint> = emptyList(),
    val visibleTo: List<String> = listOf(VisibilityTokens.PUBLIC),
    val pendingSync: Boolean = false
) {
    /** Nome a apresentar publicamente, respeitando a preferência de anonimato. */
    fun publicName(anonymousLabel: String): String = if (anonymous) anonymousLabel else displayName
}

/** Perfil privado do utilizador autenticado. */
data class UserProfile(
    val uid: String,
    val username: String = "",
    val email: String = "",
    val city: String = "",
    val phone: String = "",
    val bibNumber: String = "",
    val anonymousByDefault: Boolean = false,
    val photoUrl: String? = null
)

/** Subscrição de um utilizador a uma prova. */
data class Subscription(
    val raceId: String,
    val raceName: String = "",
    val subscribedAt: Long = 0L
)
