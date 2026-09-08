package pt.ipp.estg.cmu.vivaracing.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceVisibility
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.data.model.RouteSource
import pt.ipp.estg.cmu.vivaracing.data.model.Subscription
import pt.ipp.estg.cmu.vivaracing.data.model.UserProfile

/**
 * Entidades da base de dados local (Room). Funcionam como cache do Firestore,
 * garantindo que a aplicação continua utilizável sem ligação à Internet.
 *
 * O campo [pendingSync] identifica registos criados no dispositivo que ainda
 * não foram confirmados pelo servidor, permitindo assinalar esse estado na UI.
 */

@Entity(tableName = "races")
data class RaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val type: String,
    val status: String,
    val city: String,
    val startDateTime: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val distanceMeters: Double,
    val route: List<GeoPoint>,
    val routeSource: String,
    val visibility: String,
    val visibleTo: List<String>,
    val photoUrl: String?,
    val organizerPhone: String,
    val authorId: String,
    val authorName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean
) {
    fun toDomain(): Race = Race(
        id = id,
        name = name,
        description = description,
        type = RaceType.fromName(type),
        status = RaceStatus.fromName(status),
        city = city,
        startDateTime = startDateTime,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        distanceMeters = distanceMeters,
        route = route,
        routeSource = RouteSource.fromName(routeSource),
        visibility = RaceVisibility.fromName(visibility),
        visibleTo = visibleTo,
        photoUrl = photoUrl,
        organizerPhone = organizerPhone,
        authorId = authorId,
        authorName = authorName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = pendingSync
    )

    companion object {
        fun fromDomain(race: Race): RaceEntity = RaceEntity(
            id = race.id,
            name = race.name,
            description = race.description,
            type = race.type.name,
            status = race.status.name,
            city = race.city,
            startDateTime = race.startDateTime,
            startLatitude = race.startLatitude,
            startLongitude = race.startLongitude,
            distanceMeters = race.distanceMeters,
            route = race.route,
            routeSource = race.routeSource.name,
            visibility = race.visibility.name,
            visibleTo = race.visibleTo,
            photoUrl = race.photoUrl,
            organizerPhone = race.organizerPhone,
            authorId = race.authorId,
            authorName = race.authorName,
            createdAt = race.createdAt,
            updatedAt = race.updatedAt,
            pendingSync = race.pendingSync
        )
    }
}

@Entity(tableName = "alerts", indices = [Index("raceId"), Index("timestamp")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val raceId: String,
    val raceName: String,
    val type: String,
    val athleteBib: Int?,
    val athleteUserId: String,
    val athleteName: String,
    val message: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val authorId: String,
    val authorName: String,
    val photoUrl: String?,
    val visibleTo: List<String>,
    val pendingSync: Boolean
) {
    fun toDomain(): RaceAlert = RaceAlert(
        id = id,
        raceId = raceId,
        raceName = raceName,
        type = AlertType.fromName(type),
        athleteBib = athleteBib,
        athleteUserId = athleteUserId,
        athleteName = athleteName,
        message = message,
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
        authorId = authorId,
        authorName = authorName,
        photoUrl = photoUrl,
        visibleTo = visibleTo,
        pendingSync = pendingSync
    )

    companion object {
        fun fromDomain(alert: RaceAlert): AlertEntity = AlertEntity(
            id = alert.id,
            raceId = alert.raceId,
            raceName = alert.raceName,
            type = alert.type.name,
            athleteBib = alert.athleteBib,
            athleteUserId = alert.athleteUserId,
            athleteName = alert.athleteName,
            message = alert.message,
            latitude = alert.latitude,
            longitude = alert.longitude,
            timestamp = alert.timestamp,
            authorId = alert.authorId,
            authorName = alert.authorName,
            photoUrl = alert.photoUrl,
            visibleTo = alert.visibleTo,
            pendingSync = alert.pendingSync
        )
    }
}

@Entity(tableName = "amateur_runs", indices = [Index("raceId"), Index("userId")])
data class AmateurRunEntity(
    @PrimaryKey val id: String,
    val raceId: String,
    val raceName: String,
    val userId: String,
    val displayName: String,
    val anonymous: Boolean,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val averageSpeedKmh: Double,
    val steps: Int,
    val route: List<GeoPoint>,
    val visibleTo: List<String>,
    val pendingSync: Boolean
) {
    fun toDomain(): AmateurRun = AmateurRun(
        id = id,
        raceId = raceId,
        raceName = raceName,
        userId = userId,
        displayName = displayName,
        anonymous = anonymous,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        averageSpeedKmh = averageSpeedKmh,
        steps = steps,
        route = route,
        visibleTo = visibleTo,
        pendingSync = pendingSync
    )

    companion object {
        fun fromDomain(run: AmateurRun): AmateurRunEntity = AmateurRunEntity(
            id = run.id,
            raceId = run.raceId,
            raceName = run.raceName,
            userId = run.userId,
            displayName = run.displayName,
            anonymous = run.anonymous,
            startTime = run.startTime,
            endTime = run.endTime,
            durationSeconds = run.durationSeconds,
            distanceMeters = run.distanceMeters,
            averageSpeedKmh = run.averageSpeedKmh,
            steps = run.steps,
            route = run.route,
            visibleTo = run.visibleTo,
            pendingSync = run.pendingSync
        )
    }
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val raceId: String,
    val raceName: String,
    val subscribedAt: Long
) {
    fun toDomain(): Subscription = Subscription(raceId, raceName, subscribedAt)
}

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val email: String,
    val city: String,
    val phone: String,
    val bibNumber: String,
    val anonymousByDefault: Boolean,
    val photoUrl: String?
) {
    fun toDomain(): UserProfile =
        UserProfile(uid, username, email, city, phone, bibNumber, anonymousByDefault, photoUrl)

    companion object {
        fun fromDomain(profile: UserProfile): UserProfileEntity = UserProfileEntity(
            uid = profile.uid,
            username = profile.username,
            email = profile.email,
            city = profile.city,
            phone = profile.phone,
            bibNumber = profile.bibNumber,
            anonymousByDefault = profile.anonymousByDefault,
            photoUrl = profile.photoUrl
        )
    }
}

/**
 * Ponto individual capturado pelo serviço de localização durante uma sessão de
 * gravação. Fica isolado numa tabela própria para que a escrita contínua feita
 * pelo serviço não obrigue a reescrever o objeto completo do percurso.
 */
@Entity(tableName = "track_points", indices = [Index("sessionId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude, altitude, timestamp)
}
