package pt.ipp.estg.cmu.vivaracing.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import pt.ipp.estg.cmu.vivaracing.data.local.dao.RaceDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.SubscriptionDao
import pt.ipp.estg.cmu.vivaracing.data.local.entity.RaceEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.SubscriptionEntity
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.Subscription
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService
import java.util.UUID

/**
 * Repositório das provas registadas pela comunidade.
 *
 * Segue a estratégia de fonte única de verdade: a interface lê sempre da base
 * de dados local (Room) e a sincronização com o Firestore acontece em segundo
 * plano. Escritas feitas sem rede ficam marcadas como `pendingSync` e são
 * enviadas assim que a ligação for reposta.
 */
class RaceRepository(
    private val raceDao: RaceDao,
    private val subscriptionDao: SubscriptionDao,
    private val firestoreService: FirestoreService
) {

    fun observeRaces(): Flow<List<Race>> =
        raceDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRace(raceId: String): Flow<Race?> =
        raceDao.observeById(raceId).map { it?.toDomain() }

    fun observeRacesByAuthor(authorId: String): Flow<List<Race>> =
        raceDao.observeByAuthor(authorId).map { list -> list.map { it.toDomain() } }

    fun observeSubscribedRaces(): Flow<List<Race>> =
        raceDao.observeSubscribed().map { list -> list.map { it.toDomain() } }

    fun observeSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeIsSubscribed(raceId: String): Flow<Boolean> =
        subscriptionDao.observeIsSubscribed(raceId).map { it > 0 }

    suspend fun findRace(raceId: String): Race? = raceDao.findById(raceId)?.toDomain()

    /**
     * Atualiza a cache local a partir de uma emissão do Firestore.
     *
     * Além de inserir ou atualizar, remove as provas que deixaram de estar
     * visíveis para o utilizador, por exemplo quando uma amizade termina e
     * as provas restritas a amigos deixam de lhe ser acessíveis. Os registos
     * ainda por sincronizar são preservados.
     */
    suspend fun cacheRemoteRaces(races: List<Race>) {
        raceDao.upsertAll(races.map { RaceEntity.fromDomain(it) })
        val visibleIds = races.map { it.id }
        if (visibleIds.isEmpty()) raceDao.deleteAllSynced() else raceDao.deleteMissing(visibleIds)
    }

    suspend fun cacheSubscriptions(subscriptions: List<Subscription>) {
        subscriptionDao.clear()
        subscriptionDao.upsertAll(
            subscriptions.map { SubscriptionEntity(it.raceId, it.raceName, it.subscribedAt) }
        )
    }

    suspend fun createRace(race: Race): Result<Race> = runCatching {
        val identifier = race.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val prepared = race.copy(
            id = identifier,
            createdAt = if (race.createdAt > 0) race.createdAt else now,
            updatedAt = now,
            pendingSync = true
        )

        // 1. Escrita local imediata: a prova fica visível mesmo sem rede.
        raceDao.upsert(RaceEntity.fromDomain(prepared))

        // 2. Tentativa de escrita remota com limite de tempo. Se falhar, o
        //    Firestore mantém a operação em fila e o registo continua marcado
        //    como pendente na interface.
        val synced = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.saveRace(prepared) }
                .onFailure { Log.w(TAG, "Escrita remota da prova falhou", it) }
                .isSuccess
        } ?: false

        // 3. O autor fica automaticamente subscrito a sua própria prova. Não há
        //    botão de subscrição no detalhe de uma prova própria, pelo que sem
        //    esta linha o organizador ficaria de fora dos avisos do seu evento
        //    e a prova não entraria na lista publica de provas que acompanha.
        if (prepared.authorId.isNotBlank()) {
            runCatching { subscribe(prepared.authorId, prepared) }
        }

        if (synced) {
            raceDao.markSynced(prepared.id)
            prepared.copy(pendingSync = false)
        } else {
            prepared
        }
    }

    suspend fun subscribe(uid: String, race: Race): Result<Unit> = runCatching {
        subscriptionDao.upsert(
            SubscriptionEntity(race.id, race.name, System.currentTimeMillis())
        )
        withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.subscribeRace(uid, race.id, race.name) }
        }
        Unit
    }

    suspend fun unsubscribe(uid: String, raceId: String): Result<Unit> = runCatching {
        subscriptionDao.deleteById(raceId)
        withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.unsubscribeRace(uid, raceId) }
        }
        Unit
    }

    suspend fun deleteRace(raceId: String): Result<Unit> = runCatching {
        raceDao.deleteById(raceId)
        withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.deleteRace(raceId) }
        }
        Unit
    }

    /** Leitura pontual usada pelo trabalho periódico em segundo plano. */
    suspend fun fetchRacesSince(
        visibilityTokens: List<String>,
        timestamp: Long
    ): List<Race> = runCatching {
        firestoreService.fetchRaces(visibilityTokens).filter { it.createdAt > timestamp }
    }.getOrDefault(emptyList())

    /** Reenvia para o Firestore as provas que ficaram por sincronizar. */
    suspend fun pushPendingRaces() {
        raceDao.findPending().forEach { entity ->
            val result = runCatching { firestoreService.saveRace(entity.toDomain()) }
            if (result.isSuccess) raceDao.markSynced(entity.id)
        }
    }

    private companion object {
        const val TAG = "RaceRepository"
        const val REMOTE_TIMEOUT_MILLIS = 8_000L
    }
}
