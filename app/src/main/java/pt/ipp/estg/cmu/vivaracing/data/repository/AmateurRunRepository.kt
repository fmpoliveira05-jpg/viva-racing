package pt.ipp.estg.cmu.vivaracing.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AmateurRunDao
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AmateurRunEntity
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService
import java.util.UUID

/**
 * Repositório das participações amadoras: tempos pessoais registados por
 * utilizadores que percorrem o trajeto de uma prova fora do evento oficial.
 */
class AmateurRunRepository(
    private val amateurRunDao: AmateurRunDao,
    private val firestoreService: FirestoreService
) {

    fun observeAll(): Flow<List<AmateurRun>> =
        amateurRunDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByRace(raceId: String): Flow<List<AmateurRun>> =
        amateurRunDao.observeByRace(raceId).map { list -> list.map { it.toDomain() } }

    fun observeByUser(userId: String): Flow<List<AmateurRun>> =
        amateurRunDao.observeByUser(userId).map { list -> list.map { it.toDomain() } }

    fun observeById(runId: String): Flow<AmateurRun?> =
        amateurRunDao.observeById(runId).map { it?.toDomain() }

    suspend fun cacheRemoteRuns(runs: List<AmateurRun>) {
        amateurRunDao.upsertAll(runs.map { AmateurRunEntity.fromDomain(it) })
        val visibleIds = runs.map { it.id }
        if (visibleIds.isEmpty()) {
            amateurRunDao.deleteAllSynced()
        } else {
            amateurRunDao.deleteMissing(visibleIds)
        }
    }

    suspend fun saveRun(run: AmateurRun): Result<AmateurRun> = runCatching {
        val prepared = run.copy(
            id = run.id.ifBlank { UUID.randomUUID().toString() },
            pendingSync = true
        )
        amateurRunDao.upsert(AmateurRunEntity.fromDomain(prepared))

        val synced = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.saveAmateurRun(prepared) }
                .onFailure { Log.w(TAG, "Escrita remota da participação falhou", it) }
                .isSuccess
        } ?: false

        if (synced) {
            amateurRunDao.markSynced(prepared.id)
            prepared.copy(pendingSync = false)
        } else {
            prepared
        }
    }

    suspend fun fetchRunsSince(
        visibilityTokens: List<String>,
        timestamp: Long
    ): List<AmateurRun> = runCatching {
        firestoreService.fetchAmateurRuns(visibilityTokens).filter { it.endTime > timestamp }
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "AmateurRunRepository"
        const val REMOTE_TIMEOUT_MILLIS = 8_000L
    }
}
