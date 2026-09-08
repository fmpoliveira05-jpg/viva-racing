package pt.ipp.estg.cmu.vivaracing.data.remote.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.AthleteSubscription
import pt.ipp.estg.cmu.vivaracing.data.model.Friend
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequestDirection
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.data.model.RaceVisibility
import pt.ipp.estg.cmu.vivaracing.data.model.RouteSource
import pt.ipp.estg.cmu.vivaracing.data.model.SocialCounters
import pt.ipp.estg.cmu.vivaracing.data.model.Subscription
import pt.ipp.estg.cmu.vivaracing.data.model.UserProfile
import pt.ipp.estg.cmu.vivaracing.data.model.VisibilityTokens

/**
 * Acesso à base de dados online Cloud Firestore.
 *
 * Organização das coleções:
 *
 *  - `races`, `alerts`, `amateurRuns` -> conteúdo da comunidade. Cada
 *    documento transporta o campo `visibleTo`, que materializa a visibilidade
 *    escolhida pelo autor (publica, apenas amigos ou privada);
 *  - `publicProfiles/{uid}` -> diretório PUBLICO de utilizadores, com apenas
 *    o nome e a localidade, usado na pesquisa de amigos;
 *  - `users/{uid}` -> perfil PRIVADO, com telemóvel e número de dorsal,
 *    acessível unicamente ao próprio;
 *  - `users/{uid}/subscriptions`, `/athleteSubscriptions`, `/friends`,
 *    `/friendRequests` e `/sentRequests` -> informação privada do utilizador.
 *
 * As consultas de conteúdo usam `whereArrayContainsAny` sobre `visibleTo`.
 * Deliberadamente não levam cláusula de ordenação: combinar este operador com
 * `orderBy` obrigaria a criar índices compostos manualmente na consola do
 * Firebase. A ordenação é feita pela base de dados local, que é a fonte
 * consumida pela interface.
 */
class FirestoreService {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
    }

    // ============================== Provas ================================

    fun observeRaces(visibilityTokens: List<String>): Flow<List<Race>> {
        if (visibilityTokens.isEmpty()) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection(COLLECTION_RACES)
                .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
                .limit(MAX_RACES)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Falha ao observar provas", error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { it.toRace() })
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun saveRace(race: Race) {
        db.collection(COLLECTION_RACES).document(race.id).set(race.toMap()).await()
    }

    /** Leitura pontual das provas visíveis, usada pelo trabalho periódico. */
    suspend fun fetchRaces(visibilityTokens: List<String>): List<Race> {
        if (visibilityTokens.isEmpty()) return emptyList()
        return db.collection(COLLECTION_RACES)
            .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
            .limit(MAX_BACKGROUND)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRace() }
    }

    suspend fun deleteRace(raceId: String) {
        db.collection(COLLECTION_RACES).document(raceId).delete().await()
    }

    // ============================== Alertas ===============================

    fun observeAlerts(visibilityTokens: List<String>): Flow<List<RaceAlert>> {
        if (visibilityTokens.isEmpty()) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection(COLLECTION_ALERTS)
                .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
                .limit(MAX_ALERTS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Falha ao observar alertas", error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { it.toAlert() })
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun saveAlert(alert: RaceAlert) {
        db.collection(COLLECTION_ALERTS).document(alert.id).set(alert.toMap()).await()
    }

    /** Leitura pontual usada pelo trabalho periódico do WorkManager. */
    suspend fun fetchAlerts(visibilityTokens: List<String>): List<RaceAlert> {
        if (visibilityTokens.isEmpty()) return emptyList()
        return db.collection(COLLECTION_ALERTS)
            .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
            .limit(MAX_BACKGROUND)
            .get()
            .await()
            .documents
            .mapNotNull { it.toAlert() }
    }

    // ======================= Participações amadoras =======================

    fun observeAmateurRuns(visibilityTokens: List<String>): Flow<List<AmateurRun>> {
        if (visibilityTokens.isEmpty()) return flowOf(emptyList())
        return callbackFlow {
            val registration = db.collection(COLLECTION_AMATEUR_RUNS)
                .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
                .limit(MAX_RUNS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Falha ao observar participações amadoras", error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { it.toAmateurRun() })
                }
            awaitClose { registration.remove() }
        }
    }

    suspend fun saveAmateurRun(run: AmateurRun) {
        db.collection(COLLECTION_AMATEUR_RUNS).document(run.id).set(run.toMap()).await()
    }

    suspend fun fetchAmateurRuns(visibilityTokens: List<String>): List<AmateurRun> {
        if (visibilityTokens.isEmpty()) return emptyList()
        return db.collection(COLLECTION_AMATEUR_RUNS)
            .whereArrayContainsAny(FIELD_VISIBLE_TO, visibilityTokens)
            .limit(MAX_BACKGROUND)
            .get()
            .await()
            .documents
            .mapNotNull { it.toAmateurRun() }
    }

    // ===================== Perfil privado do utilizador ===================

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection(COLLECTION_USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar perfil", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toProfile())
            }
        awaitClose { registration.remove() }
    }

    /**
     * Leitura pontual do perfil.
     *
     * É indispensável ao reiniciar sessão: sem ela, a aplicação concluiria que
     * o perfil não existe (a cache local foi limpa no encerramento de sessão)
     * e sobreporia o documento remoto com um perfil vazio.
     */
    suspend fun fetchProfile(uid: String): UserProfile? =
        db.collection(COLLECTION_USERS).document(uid).get().await().toProfile()

    suspend fun saveProfile(profile: UserProfile) {
        db.collection(COLLECTION_USERS).document(profile.uid).set(profile.toMap()).await()
    }

    // ===================== Diretório público de perfis ====================

    /**
     * Grava o perfil público do próprio utilizador.
     *
     * A escrita é feita em modo de fusão para não apagar os contadores
     * sociais publicados no mesmo documento (ver [publishSocialCounters]).
     */
    suspend fun savePublicProfile(profile: PublicProfile) {
        db.collection(COLLECTION_PUBLIC_PROFILES).document(profile.uid)
            .set(profile.toMap(), SetOptions.merge()).await()
    }

    /**
     * Lê os contadores sociais publicados por um utilizador.
     *
     * O Firestore não permite consultar as subcoleções privadas de outra
     * pessoa: tentar contar diretamente `users/{outro}/friends` ou
     * `users/{outro}/friendRequests` devolve PERMISSION_DENIED, porque essas
     * subcoleções só são legíveis pelo próprio. Para que os limites de
     * capacidade do destinatário possam ainda assim ser respeitados, cada
     * utilizador publica no seu perfil público, legível por qualquer sessão
     * autenticada, apenas os dois totais, sem revelar quem são os
     * seus amigos nem de quem recebeu pedidos.
     */
    suspend fun fetchSocialCounters(uid: String): SocialCounters {
        val document = db.collection(COLLECTION_PUBLIC_PROFILES).document(uid).get().await()
        return SocialCounters(
            friends = (document.getLong(FIELD_FRIEND_COUNT) ?: 0L).toInt().coerceAtLeast(0),
            pending = (document.getLong(FIELD_PENDING_COUNT) ?: 0L).toInt().coerceAtLeast(0)
        )
    }

    /**
     * Publica o resumo público do próprio utilizador: os totais sociais e a
     * lista de provas que subscreveu. A escrita incide sobre o seu próprio
     * documento, pelo que é permitida pelas regras de segurança sem qualquer
     * exceção adicional.
     */
    suspend fun publishAthleteSummary(
        uid: String,
        friends: Int? = null,
        pending: Int? = null,
        raceIds: List<String>? = null
    ) {
        val payload = mutableMapOf<String, Any>()
        if (friends != null) payload[FIELD_FRIEND_COUNT] = friends
        if (pending != null) payload[FIELD_PENDING_COUNT] = pending
        if (raceIds != null) payload[FIELD_RACE_IDS] = raceIds.take(MAX_PUBLISHED_RACE_IDS)
        if (payload.isEmpty()) return
        db.collection(COLLECTION_PUBLIC_PROFILES).document(uid)
            .set(payload.toMap(), SetOptions.merge()).await()
    }

    fun observePublicProfiles(): Flow<List<PublicProfile>> = callbackFlow {
        val registration = db.collection(COLLECTION_PUBLIC_PROFILES)
            .limit(MAX_PUBLIC_PROFILES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar o diretório de utilizadores", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toPublicProfile() })
            }
        awaitClose { registration.remove() }
    }

    suspend fun fetchPublicProfile(uid: String): PublicProfile? =
        db.collection(COLLECTION_PUBLIC_PROFILES).document(uid).get().await().toPublicProfile()

    // ======================= Subscrições de provas ========================

    fun observeSubscriptions(uid: String): Flow<List<Subscription>> = callbackFlow {
        val registration = db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_SUBSCRIPTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar subscrições de provas", error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().map { document ->
                        Subscription(
                            raceId = document.id,
                            raceName = document.getString(FIELD_RACE_NAME).orEmpty(),
                            subscribedAt = document.getLong(FIELD_SINCE) ?: 0L
                        )
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun subscribeRace(uid: String, raceId: String, raceName: String) {
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_SUBSCRIPTIONS).document(raceId)
            .set(mapOf(FIELD_RACE_NAME to raceName, FIELD_SINCE to now())).await()
    }

    suspend fun unsubscribeRace(uid: String, raceId: String) {
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_SUBSCRIPTIONS).document(raceId).delete().await()
    }

    suspend fun fetchSubscriptionIds(uid: String): List<String> =
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_SUBSCRIPTIONS).get().await().documents.map { it.id }

    // ======================= Subscrições de atletas =======================

    fun observeAthleteSubscriptions(uid: String): Flow<List<AthleteSubscription>> = callbackFlow {
        val registration = db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_ATHLETE_SUBSCRIPTIONS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar subscrições de atletas", error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().map { document ->
                        AthleteSubscription(
                            athleteUid = document.id,
                            username = document.getString(FIELD_USERNAME).orEmpty(),
                            bibNumber = document.getString(FIELD_BIB).orEmpty(),
                            since = document.getLong(FIELD_SINCE) ?: 0L
                        )
                    }
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun subscribeAthlete(uid: String, athlete: AthleteSubscription) {
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_ATHLETE_SUBSCRIPTIONS).document(athlete.athleteUid)
            .set(
                mapOf(
                    FIELD_USERNAME to athlete.username,
                    FIELD_BIB to athlete.bibNumber,
                    FIELD_SINCE to now()
                )
            ).await()
    }

    suspend fun unsubscribeAthlete(uid: String, athleteUid: String) {
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_ATHLETE_SUBSCRIPTIONS).document(athleteUid).delete().await()
    }

    suspend fun fetchAthleteSubscriptionIds(uid: String): List<String> =
        db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_ATHLETE_SUBSCRIPTIONS).get().await().documents.map { it.id }

    // ============================== Amigos ================================

    fun observeFriends(uid: String): Flow<List<Friend>> = callbackFlow {
        val registration = db.collection(COLLECTION_USERS).document(uid)
            .collection(COLLECTION_FRIENDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar amigos", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map { it.toFriend() })
            }
        awaitClose { registration.remove() }
    }

    fun observeFriendRequests(
        uid: String,
        direction: FriendRequestDirection
    ): Flow<List<FriendRequest>> = callbackFlow {
        val collection = if (direction == FriendRequestDirection.RECEIVED) {
            COLLECTION_FRIEND_REQUESTS
        } else {
            COLLECTION_SENT_REQUESTS
        }
        val registration = db.collection(COLLECTION_USERS).document(uid)
            .collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falha ao observar pedidos de amizade", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().map { it.toFriendRequest(direction) })
            }
        awaitClose { registration.remove() }
    }

    /**
     * Envia um pedido de amizade.
     *
     * São escritos dois documentos: um na caixa de entrada do destinatário e
     * outro no registo de pedidos enviados do remetente. O espelho é o que
     * permite ao remetente consultar e cancelar os seus próprios pedidos, uma
     * vez que o Firestore não permite consultar subcoleções de outros
     * utilizadores.
     */
    suspend fun sendFriendRequest(sender: PublicProfile, targetUid: String) {
        val payload = mapOf(
            FIELD_USERNAME to sender.username,
            FIELD_CITY to sender.city,
            FIELD_SINCE to now()
        )
        db.collection(COLLECTION_USERS).document(targetUid)
            .collection(COLLECTION_FRIEND_REQUESTS).document(sender.uid)
            .set(payload).await()

        val target = fetchPublicProfile(targetUid)
        val mirror = mapOf(
            FIELD_USERNAME to (target?.username ?: ""),
            FIELD_CITY to (target?.city ?: ""),
            FIELD_SINCE to now()
        )
        db.collection(COLLECTION_USERS).document(sender.uid)
            .collection(COLLECTION_SENT_REQUESTS).document(targetUid)
            .set(mirror).await()
    }

    /** Aceita um pedido, criando a amizade nos dois sentidos. */
    suspend fun acceptFriendRequest(me: PublicProfile, requester: PublicProfile) {
        val timestamp = now()
        db.collection(COLLECTION_USERS).document(me.uid)
            .collection(COLLECTION_FRIENDS).document(requester.uid)
            .set(
                mapOf(
                    FIELD_USERNAME to requester.username,
                    FIELD_CITY to requester.city,
                    FIELD_SINCE to timestamp
                )
            ).await()

        db.collection(COLLECTION_USERS).document(requester.uid)
            .collection(COLLECTION_FRIENDS).document(me.uid)
            .set(
                mapOf(
                    FIELD_USERNAME to me.username,
                    FIELD_CITY to me.city,
                    FIELD_SINCE to timestamp
                )
            ).await()

        clearRequestPair(requesterUid = requester.uid, targetUid = me.uid)
    }

    suspend fun declineFriendRequest(myUid: String, requesterUid: String) {
        clearRequestPair(requesterUid = requesterUid, targetUid = myUid)
    }

    suspend fun cancelFriendRequest(myUid: String, targetUid: String) {
        clearRequestPair(requesterUid = myUid, targetUid = targetUid)
    }

    suspend fun removeFriend(myUid: String, friendUid: String) {
        db.collection(COLLECTION_USERS).document(myUid)
            .collection(COLLECTION_FRIENDS).document(friendUid).delete().await()
        runCatching {
            db.collection(COLLECTION_USERS).document(friendUid)
                .collection(COLLECTION_FRIENDS).document(myUid).delete().await()
        }
    }

    private suspend fun clearRequestPair(requesterUid: String, targetUid: String) {
        runCatching {
            db.collection(COLLECTION_USERS).document(targetUid)
                .collection(COLLECTION_FRIEND_REQUESTS).document(requesterUid).delete().await()
        }
        runCatching {
            db.collection(COLLECTION_USERS).document(requesterUid)
                .collection(COLLECTION_SENT_REQUESTS).document(targetUid).delete().await()
        }
    }

    // ============================ Conversores =============================

    private fun DocumentSnapshot.toRace(): Race? {
        val name = getString("name") ?: return null
        return Race(
            id = id,
            name = name,
            description = getString("description").orEmpty(),
            type = RaceType.fromName(getString("type")),
            status = RaceStatus.fromName(getString("status")),
            city = getString("city").orEmpty(),
            startDateTime = getLong(FIELD_START_DATE_TIME) ?: 0L,
            startLatitude = getDouble("startLatitude") ?: 0.0,
            startLongitude = getDouble("startLongitude") ?: 0.0,
            distanceMeters = getDouble("distanceMeters") ?: 0.0,
            route = readRoute(get("route")),
            routeSource = RouteSource.fromName(getString("routeSource")),
            visibility = RaceVisibility.fromName(getString("visibility")),
            visibleTo = readTokens(get(FIELD_VISIBLE_TO)),
            photoUrl = getString("photoUrl"),
            organizerPhone = getString("organizerPhone").orEmpty(),
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            pendingSync = false
        )
    }

    private fun DocumentSnapshot.toAlert(): RaceAlert? {
        val raceId = getString("raceId") ?: return null
        return RaceAlert(
            id = id,
            raceId = raceId,
            raceName = getString(FIELD_RACE_NAME).orEmpty(),
            type = AlertType.fromName(getString("type")),
            athleteBib = getLong("athleteBib")?.toInt(),
            athleteUserId = getString("athleteUserId").orEmpty(),
            athleteName = getString("athleteName").orEmpty(),
            message = getString("message").orEmpty(),
            latitude = getDouble("latitude") ?: 0.0,
            longitude = getDouble("longitude") ?: 0.0,
            timestamp = getLong(FIELD_TIMESTAMP) ?: 0L,
            authorId = getString("authorId").orEmpty(),
            authorName = getString("authorName").orEmpty(),
            photoUrl = getString("photoUrl"),
            visibleTo = readTokens(get(FIELD_VISIBLE_TO)),
            pendingSync = false
        )
    }

    private fun DocumentSnapshot.toAmateurRun(): AmateurRun? {
        val raceId = getString("raceId") ?: return null
        return AmateurRun(
            id = id,
            raceId = raceId,
            raceName = getString(FIELD_RACE_NAME).orEmpty(),
            userId = getString("userId").orEmpty(),
            displayName = getString("displayName").orEmpty(),
            anonymous = getBoolean("anonymous") ?: false,
            startTime = getLong("startTime") ?: 0L,
            endTime = getLong(FIELD_END_TIME) ?: 0L,
            durationSeconds = getLong("durationSeconds") ?: 0L,
            distanceMeters = getDouble("distanceMeters") ?: 0.0,
            averageSpeedKmh = getDouble("averageSpeedKmh") ?: 0.0,
            steps = (getLong("steps") ?: 0L).toInt(),
            route = readRoute(get("route")),
            visibleTo = readTokens(get(FIELD_VISIBLE_TO)),
            pendingSync = false
        )
    }

    private fun DocumentSnapshot.toProfile(): UserProfile? {
        if (!exists()) return null
        return UserProfile(
            uid = id,
            username = getString(FIELD_USERNAME).orEmpty(),
            email = getString("email").orEmpty(),
            city = getString(FIELD_CITY).orEmpty(),
            phone = getString("phone").orEmpty(),
            bibNumber = getString(FIELD_BIB).orEmpty(),
            anonymousByDefault = getBoolean("anonymousByDefault") ?: false,
            photoUrl = getString("photoUrl")
        )
    }

    private fun DocumentSnapshot.toPublicProfile(): PublicProfile? {
        if (!exists()) return null
        val username = getString(FIELD_USERNAME).orEmpty()
        if (username.isBlank()) return null
        return PublicProfile(
            uid = id,
            username = username,
            city = getString(FIELD_CITY).orEmpty(),
            photoUrl = getString("photoUrl"),
            updatedAt = getLong("updatedAt") ?: 0L,
            raceIds = (get(FIELD_RACE_IDS) as? List<*>)
                .orEmpty()
                .mapNotNull { it as? String }
        )
    }

    private fun DocumentSnapshot.toFriend(): Friend = Friend(
        uid = id,
        username = getString(FIELD_USERNAME).orEmpty(),
        city = getString(FIELD_CITY).orEmpty(),
        since = getLong(FIELD_SINCE) ?: 0L
    )

    private fun DocumentSnapshot.toFriendRequest(
        direction: FriendRequestDirection
    ): FriendRequest = FriendRequest(
        uid = id,
        username = getString(FIELD_USERNAME).orEmpty(),
        city = getString(FIELD_CITY).orEmpty(),
        sentAt = getLong(FIELD_SINCE) ?: 0L,
        direction = direction
    )

    private fun readRoute(raw: Any?): List<GeoPoint> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { element ->
            val map = element as? Map<*, *> ?: return@mapNotNull null
            val lat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lon = (map["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
            GeoPoint(
                latitude = lat,
                longitude = lon,
                altitude = (map["alt"] as? Number)?.toDouble() ?: 0.0,
                timestamp = (map["ts"] as? Number)?.toLong() ?: 0L
            )
        }
    }

    private fun readTokens(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return listOf(VisibilityTokens.PUBLIC)
        val tokens = list.mapNotNull { it as? String }
        return tokens.ifEmpty { listOf(VisibilityTokens.PUBLIC) }
    }

    private fun List<GeoPoint>.toFirestoreList(): List<Map<String, Any>> = map { point ->
        mapOf(
            "lat" to point.latitude,
            "lon" to point.longitude,
            "alt" to point.altitude,
            "ts" to point.timestamp
        )
    }

    private fun Race.toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "description" to description,
        "type" to type.name,
        "status" to status.name,
        "city" to city,
        FIELD_START_DATE_TIME to startDateTime,
        "startLatitude" to startLatitude,
        "startLongitude" to startLongitude,
        "distanceMeters" to distanceMeters,
        "route" to route.toFirestoreList(),
        "routeSource" to routeSource.name,
        "visibility" to visibility.name,
        FIELD_VISIBLE_TO to visibleTo,
        "photoUrl" to photoUrl,
        "organizerPhone" to organizerPhone,
        "authorId" to authorId,
        "authorName" to authorName,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun RaceAlert.toMap(): Map<String, Any?> = mapOf(
        "raceId" to raceId,
        FIELD_RACE_NAME to raceName,
        "type" to type.name,
        "athleteBib" to athleteBib,
        "athleteUserId" to athleteUserId,
        "athleteName" to athleteName,
        "message" to message,
        "latitude" to latitude,
        "longitude" to longitude,
        FIELD_TIMESTAMP to timestamp,
        "authorId" to authorId,
        "authorName" to authorName,
        "photoUrl" to photoUrl,
        FIELD_VISIBLE_TO to visibleTo
    )

    private fun AmateurRun.toMap(): Map<String, Any?> = mapOf(
        "raceId" to raceId,
        FIELD_RACE_NAME to raceName,
        "userId" to userId,
        "displayName" to displayName,
        "anonymous" to anonymous,
        "startTime" to startTime,
        FIELD_END_TIME to endTime,
        "durationSeconds" to durationSeconds,
        "distanceMeters" to distanceMeters,
        "averageSpeedKmh" to averageSpeedKmh,
        "steps" to steps,
        "route" to route.toFirestoreList(),
        FIELD_VISIBLE_TO to visibleTo
    )

    private fun UserProfile.toMap(): Map<String, Any?> = mapOf(
        FIELD_USERNAME to username,
        "email" to email,
        FIELD_CITY to city,
        "phone" to phone,
        FIELD_BIB to bibNumber,
        "anonymousByDefault" to anonymousByDefault,
        "photoUrl" to photoUrl
    )

    private fun PublicProfile.toMap(): Map<String, Any?> = mapOf(
        FIELD_USERNAME to username,
        FIELD_CITY to city,
        "photoUrl" to photoUrl,
        "updatedAt" to now()
    )

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TAG = "FirestoreService"

        const val COLLECTION_RACES = "races"
        const val COLLECTION_ALERTS = "alerts"
        const val COLLECTION_AMATEUR_RUNS = "amateurRuns"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_PUBLIC_PROFILES = "publicProfiles"
        const val COLLECTION_SUBSCRIPTIONS = "subscriptions"
        const val COLLECTION_ATHLETE_SUBSCRIPTIONS = "athleteSubscriptions"
        const val COLLECTION_FRIENDS = "friends"
        const val COLLECTION_FRIEND_REQUESTS = "friendRequests"
        const val COLLECTION_SENT_REQUESTS = "sentRequests"

        const val FIELD_START_DATE_TIME = "startDateTime"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIELD_END_TIME = "endTime"
        const val FIELD_RACE_NAME = "raceName"
        const val FIELD_VISIBLE_TO = "visibleTo"
        const val FIELD_USERNAME = "username"
        const val FIELD_CITY = "city"
        const val FIELD_BIB = "bibNumber"
        const val FIELD_SINCE = "since"
        const val FIELD_FRIEND_COUNT = "friendCount"
        const val FIELD_PENDING_COUNT = "pendingCount"
        const val FIELD_RACE_IDS = "raceIds"

        /**
         * Limite de provas publicadas no perfil público. Mantém o documento
         * pequeno e alinha-se com o número de provas que um atleta acompanha
         * em simultâneo na prática.
         */
        const val MAX_PUBLISHED_RACE_IDS = 60

        const val MAX_RACES = 300L
        const val MAX_ALERTS = 500L
        const val MAX_RUNS = 300L
        const val MAX_PUBLIC_PROFILES = 300L
        const val MAX_BACKGROUND = 80L
    }
}
