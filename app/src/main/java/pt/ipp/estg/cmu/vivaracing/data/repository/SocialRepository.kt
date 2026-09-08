package pt.ipp.estg.cmu.vivaracing.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AthleteSubscriptionDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.FriendDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.FriendRequestDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.PublicProfileDao
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AthleteSubscriptionEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.FriendEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.FriendRequestEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.PublicProfileEntity
import pt.ipp.estg.cmu.vivaracing.data.model.AthleteSubscription
import pt.ipp.estg.cmu.vivaracing.data.model.Friend
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequestDirection
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile
import pt.ipp.estg.cmu.vivaracing.data.model.SocialLimits
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService

/** Resultado tipificado das operações sociais, traduzido pela interface. */
sealed interface SocialResult {
    data object Success : SocialResult
    data object FriendLimitReached : SocialResult
    data object RequestLimitReached : SocialResult
    data object AthleteLimitReached : SocialResult
    data object AlreadyFriends : SocialResult
    data object RequestAlreadySent : SocialResult
    data object GuestNotAllowed : SocialResult
    data class Failure(val message: String?) : SocialResult
}

/**
 * Repositório da componente social: amigos, pedidos de amizade, diretório
 * público de utilizadores e subscrições de atletas.
 *
 * Tal como no resto da aplicação, a interface lê sempre da base de dados
 * local; o Firestore alimenta essa cache e recebe as escritas.
 */
class SocialRepository(
    private val friendDao: FriendDao,
    private val friendRequestDao: FriendRequestDao,
    private val athleteSubscriptionDao: AthleteSubscriptionDao,
    private val publicProfileDao: PublicProfileDao,
    private val firestoreService: FirestoreService,
    private val authRepository: AuthRepository
) {

    // ------------------------------ Leitura -------------------------------

    fun observeFriends(): Flow<List<Friend>> =
        friendDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeFriendCount(): Flow<Int> = friendDao.observeCount()

    fun observeIsFriend(uid: String): Flow<Boolean> =
        friendDao.observeIsFriend(uid).map { it > 0 }

    fun observeIncomingRequests(): Flow<List<FriendRequest>> =
        friendRequestDao.observeByDirection(FriendRequestDirection.RECEIVED.name)
            .map { list -> list.map { it.toDomain() } }

    fun observeOutgoingRequests(): Flow<List<FriendRequest>> =
        friendRequestDao.observeByDirection(FriendRequestDirection.SENT.name)
            .map { list -> list.map { it.toDomain() } }

    fun observeIncomingRequestCount(): Flow<Int> =
        friendRequestDao.observeCountByDirection(FriendRequestDirection.RECEIVED.name)

    fun searchProfiles(query: String): Flow<List<PublicProfile>> =
        publicProfileDao.search(query.trim()).map { list -> list.map { it.toDomain() } }

    /** Perfil público de um utilizador concreto, lido da cache local. */
    fun observePublicProfile(uid: String): Flow<PublicProfile?> =
        publicProfileDao.observeById(uid).map { entity -> entity?.toDomain() }

    fun observeAthleteSubscriptions(): Flow<List<AthleteSubscription>> =
        athleteSubscriptionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeIsAthleteSubscribed(athleteUid: String): Flow<Boolean> =
        athleteSubscriptionDao.observeIsSubscribed(athleteUid).map { it > 0 }

    suspend fun friendIds(): List<String> = friendDao.findAllIds()

    suspend fun athleteSubscriptionIds(): List<String> = athleteSubscriptionDao.findAllIds()

    // ------------------------- Atualização da cache -----------------------

    suspend fun cacheFriends(friends: List<Friend>) {
        friendDao.clear()
        friendDao.upsertAll(friends.map { FriendEntity.fromDomain(it) })
    }

    suspend fun cacheRequests(requests: List<FriendRequest>, direction: FriendRequestDirection) {
        friendRequestDao.clearDirection(direction.name)
        friendRequestDao.upsertAll(requests.map { FriendRequestEntity.fromDomain(it) })
    }

    suspend fun cacheAthleteSubscriptions(subscriptions: List<AthleteSubscription>) {
        athleteSubscriptionDao.clear()
        athleteSubscriptionDao.upsertAll(
            subscriptions.map { AthleteSubscriptionEntity.fromDomain(it) }
        )
    }

    suspend fun cachePublicProfiles(profiles: List<PublicProfile>) {
        publicProfileDao.upsertAll(profiles.map { PublicProfileEntity.fromDomain(it) })
    }

    suspend fun clearLocalSocialData() {
        friendDao.clear()
        friendRequestDao.clear()
        athleteSubscriptionDao.clear()
        publicProfileDao.clear()
    }

    // ------------------------------ Escrita -------------------------------

    /**
     * Envia um pedido de amizade, validando previamente os limites definidos
     * pela aplicação e o estado atual da relação entre os dois utilizadores.
     */
    suspend fun sendFriendRequest(targetUid: String): SocialResult {
        if (authRepository.isGuest) return SocialResult.GuestNotAllowed
        val me = authRepository.currentPublicProfile()
            ?: return SocialResult.GuestNotAllowed
        if (targetUid == me.uid) return SocialResult.Failure(null)

        return runCatching {
            // ---- Limites do próprio: dados locais, sempre verificáveis ----
            val friendIds = friendDao.findAllIds()
            if (friendIds.contains(targetUid)) return SocialResult.AlreadyFriends
            if (friendIds.size >= SocialLimits.MAX_FRIENDS) {
                return SocialResult.FriendLimitReached
            }

            val outgoing = friendRequestDao.countByDirection(FriendRequestDirection.SENT.name)
            if (outgoing >= SocialLimits.MAX_PENDING_REQUESTS) {
                return SocialResult.RequestLimitReached
            }

            // ---- Limites do destinatário: contadores públicos ----
            // As subcoleções privadas do destinatário não são legíveis, pelo
            // que a capacidade é verificada pelos totais que ele próprio
            // publica. A leitura é tolerante a falhas: se os contadores ainda
            // não existirem, o pedido segue e o limite continua a ser imposto
            // do lado de quem o aceita.
            val counters = runCatching { firestoreService.fetchSocialCounters(targetUid) }
                .getOrNull()
            if (counters != null) {
                if (counters.friends >= SocialLimits.MAX_FRIENDS) {
                    return SocialResult.FriendLimitReached
                }
                if (counters.pending >= SocialLimits.MAX_PENDING_REQUESTS) {
                    return SocialResult.RequestLimitReached
                }
            }

            firestoreService.sendFriendRequest(me, targetUid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    /**
     * Publica o resumo público do próprio utilizador, ou seja, os totais
     * sociais e as provas subscritas, para que outras pessoas possam
     * respeitar os seus limites de
     * capacidade e saber com que provas o partilham, sem lhe aceder aos dados
     * privados. Chamado pelo sincronizador sempre que as listas mudam.
     */
    suspend fun publishSummary(
        uid: String,
        friends: Int? = null,
        pending: Int? = null,
        raceIds: List<String>? = null
    ) {
        runCatching { firestoreService.publishAthleteSummary(uid, friends, pending, raceIds) }
    }

    suspend fun acceptFriendRequest(request: FriendRequest): SocialResult {
        val me = authRepository.currentPublicProfile()
            ?: return SocialResult.GuestNotAllowed

        return runCatching {
            if (friendDao.findAllIds().size >= SocialLimits.MAX_FRIENDS) {
                return SocialResult.FriendLimitReached
            }
            val requester = firestoreService.fetchPublicProfile(request.uid)
                ?: PublicProfile(uid = request.uid, username = request.username, city = request.city)
            firestoreService.acceptFriendRequest(me, requester)
            friendRequestDao.deleteById(request.uid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    suspend fun declineFriendRequest(request: FriendRequest): SocialResult {
        val uid = authRepository.currentUid ?: return SocialResult.GuestNotAllowed
        return runCatching {
            firestoreService.declineFriendRequest(uid, request.uid)
            friendRequestDao.deleteById(request.uid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    suspend fun cancelFriendRequest(request: FriendRequest): SocialResult {
        val uid = authRepository.currentUid ?: return SocialResult.GuestNotAllowed
        return runCatching {
            firestoreService.cancelFriendRequest(uid, request.uid)
            friendRequestDao.deleteById(request.uid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    suspend fun removeFriend(friendUid: String): SocialResult {
        val uid = authRepository.currentUid ?: return SocialResult.GuestNotAllowed
        return runCatching {
            firestoreService.removeFriend(uid, friendUid)
            friendDao.deleteById(friendUid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    suspend fun subscribeAthlete(profile: PublicProfile, bibNumber: String): SocialResult {
        val uid = authRepository.currentUid ?: return SocialResult.GuestNotAllowed
        if (authRepository.isGuest) return SocialResult.GuestNotAllowed
        return runCatching {
            if (athleteSubscriptionDao.findAllIds().size >= SocialLimits.MAX_ATHLETE_SUBSCRIPTIONS) {
                return SocialResult.AthleteLimitReached
            }
            firestoreService.subscribeAthlete(
                uid,
                AthleteSubscription(
                    athleteUid = profile.uid,
                    username = profile.username,
                    bibNumber = bibNumber
                )
            )
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }

    suspend fun unsubscribeAthlete(athleteUid: String): SocialResult {
        val uid = authRepository.currentUid ?: return SocialResult.GuestNotAllowed
        return runCatching {
            firestoreService.unsubscribeAthlete(uid, athleteUid)
            athleteSubscriptionDao.deleteById(athleteUid)
            SocialResult.Success
        }.getOrElse { SocialResult.Failure(it.localizedMessage) }
    }
}
