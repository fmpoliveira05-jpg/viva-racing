package pt.ipp.estg.cmu.vivaracing.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AlertDao
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AlertEntity
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService
import java.util.UUID

/** Repositório dos alertas publicados durante as provas. */
class AlertRepository(
    private val alertDao: AlertDao,
    private val firestoreService: FirestoreService
) {

    fun observeAll(): Flow<List<RaceAlert>> =
        alertDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByRace(raceId: String): Flow<List<RaceAlert>> =
        alertDao.observeByRace(raceId).map { list -> list.map { it.toDomain() } }

    fun observeById(alertId: String): Flow<RaceAlert?> =
        alertDao.observeById(alertId).map { it?.toDomain() }

    fun observeSubscribed(): Flow<List<RaceAlert>> =
        alertDao.observeSubscribed().map { list -> list.map { it.toDomain() } }

    /** Alertas cujo atleta observado é um dos atletas subscritos. */
    fun observeSubscribedAthletes(): Flow<List<RaceAlert>> =
        alertDao.observeSubscribedAthletes().map { list -> list.map { it.toDomain() } }

    /** Alertas de localização de um atleta específico. */
    fun observeByAthlete(athleteUid: String): Flow<List<RaceAlert>> =
        alertDao.observeByAthlete(athleteUid).map { list -> list.map { it.toDomain() } }

    suspend fun cacheRemoteAlerts(alerts: List<RaceAlert>) {
        alertDao.upsertAll(alerts.map { AlertEntity.fromDomain(it) })
        val visibleIds = alerts.map { it.id }
        if (visibleIds.isEmpty()) alertDao.deleteAllSynced() else alertDao.deleteMissing(visibleIds)
    }

    suspend fun publishAlert(alert: RaceAlert): Result<RaceAlert> = runCatching {
        val prepared = alert.copy(
            id = alert.id.ifBlank { UUID.randomUUID().toString() },
            timestamp = if (alert.timestamp > 0) alert.timestamp else System.currentTimeMillis(),
            pendingSync = true
        )
        alertDao.upsert(AlertEntity.fromDomain(prepared))

        val synced = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
            runCatching { firestoreService.saveAlert(prepared) }
                .onFailure { Log.w(TAG, "Escrita remota do alerta falhou", it) }
                .isSuccess
        } ?: false

        if (synced) {
            alertDao.markSynced(prepared.id)
            prepared.copy(pendingSync = false)
        } else {
            prepared
        }
    }

    /**
     * Leitura pontual usada pelo trabalho periódico. A filtragem temporal é
     * feita no cliente: combinar um filtro de intervalo com o operador
     * `array-contains-any` da visibilidade exigiria um índice composto criado
     * manualmente na consola do Firebase.
     */
    suspend fun fetchAlertsSince(
        visibilityTokens: List<String>,
        timestamp: Long
    ): List<RaceAlert> = runCatching {
        firestoreService.fetchAlerts(visibilityTokens).filter { it.timestamp > timestamp }
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "AlertRepository"
        const val REMOTE_TIMEOUT_MILLIS = 8_000L
    }
}
