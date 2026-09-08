package pt.ipp.estg.cmu.vivaracing.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import pt.ipp.estg.cmu.vivaracing.data.model.AthleteSubscription
import pt.ipp.estg.cmu.vivaracing.data.model.Friend
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequestDirection
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile

/**
 * Entidades da componente social. Tal como as restantes, funcionam como cache
 * local do Firestore, garantindo que a lista de amigos e de atletas seguidos
 * continua disponível sem ligação à Internet.
 */

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val city: String,
    val since: Long
) {
    fun toDomain(): Friend = Friend(uid, username, city, since)

    companion object {
        fun fromDomain(friend: Friend): FriendEntity =
            FriendEntity(friend.uid, friend.username, friend.city, friend.since)
    }
}

@Entity(tableName = "friend_requests")
data class FriendRequestEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val city: String,
    val sentAt: Long,
    val direction: String
) {
    fun toDomain(): FriendRequest = FriendRequest(
        uid = uid,
        username = username,
        city = city,
        sentAt = sentAt,
        direction = runCatching { FriendRequestDirection.valueOf(direction) }
            .getOrDefault(FriendRequestDirection.RECEIVED)
    )

    companion object {
        fun fromDomain(request: FriendRequest): FriendRequestEntity = FriendRequestEntity(
            uid = request.uid,
            username = request.username,
            city = request.city,
            sentAt = request.sentAt,
            direction = request.direction.name
        )
    }
}

@Entity(tableName = "athlete_subscriptions")
data class AthleteSubscriptionEntity(
    @PrimaryKey val athleteUid: String,
    val username: String,
    val bibNumber: String,
    val since: Long
) {
    fun toDomain(): AthleteSubscription =
        AthleteSubscription(athleteUid, username, bibNumber, since)

    companion object {
        fun fromDomain(subscription: AthleteSubscription): AthleteSubscriptionEntity =
            AthleteSubscriptionEntity(
                subscription.athleteUid,
                subscription.username,
                subscription.bibNumber,
                subscription.since
            )
    }
}

@Entity(tableName = "public_profiles")
data class PublicProfileEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val city: String,
    val photoUrl: String?,
    val updatedAt: Long,
    val raceIds: List<String> = emptyList()
) {
    fun toDomain(): PublicProfile =
        PublicProfile(uid, username, city, photoUrl, updatedAt, raceIds)

    companion object {
        fun fromDomain(profile: PublicProfile): PublicProfileEntity =
            PublicProfileEntity(
                profile.uid,
                profile.username,
                profile.city,
                profile.photoUrl,
                profile.updatedAt,
                profile.raceIds
            )
    }
}
