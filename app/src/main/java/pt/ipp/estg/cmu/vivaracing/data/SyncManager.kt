package pt.ipp.estg.cmu.vivaracing.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.data.model.Friend
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequestDirection
import pt.ipp.estg.cmu.vivaracing.data.model.VisibilityTokens
import pt.ipp.estg.cmu.vivaracing.data.repository.SessionState

/**
 * Responsável pela sincronização contínua entre o Firestore e a base de dados
 * local.
 *
 * A sincronização está organizada em duas camadas. A camada exterior observa
 * a lista de amigos; a camada interior observa o conteúdo da comunidade,
 * filtrado pelos marcadores de visibilidade a que o utilizador tem direito.
 * Sempre que a lista de amigos muda, os marcadores mudam com ela e os
 * ouvintes de conteúdo são reiniciados, e é isso que garante que uma prova
 * restrita a amigos aparece imediatamente após a amizade ser aceite e
 * desaparece quando esta termina.
 *
 * Em modo convidado não existem amigos nem informação privada: o utilizador
 * anónimo observa apenas o conteúdo público.
 */
class SyncManager(
    private val container: AppContainer,
    private val scope: CoroutineScope
) {

    private var syncJob: Job? = null

    fun start(session: SessionState) {
        stop()
        val uid = session.uid ?: return

        syncJob = scope.launch {
            if (session.isGuest) {
                container.socialRepository.clearLocalSocialData()
                syncContent(VisibilityTokens.forViewer(uid, emptyList(), isGuest = true))
                return@launch
            }

            launch { syncPrivateData(uid) }

            // A cada alteração da lista de amigos os ouvintes são recriados
            // com o novo conjunto de marcadores de visibilidade.
            container.socialRepository.observeFriends()
                .catch { Log.w(TAG, "Observação de amigos interrompida", it) }
                .collectLatest { friends: List<Friend> ->
                    val tokens = VisibilityTokens.forViewer(
                        uid = uid,
                        friendUids = friends.map { it.uid },
                        isGuest = false
                    )
                    coroutineScope { syncContent(tokens) }
                }
        }
    }

    /** Ouvintes do conteúdo da comunidade, filtrado por visibilidade. */
    private suspend fun syncContent(tokens: List<String>) = coroutineScope {
        launch {
            container.firestoreService.observeRaces(tokens)
                .catch { Log.w(TAG, "Sincronização de provas interrompida", it) }
                .collect { races -> container.raceRepository.cacheRemoteRaces(races) }
        }
        launch {
            container.firestoreService.observeAlerts(tokens)
                .catch { Log.w(TAG, "Sincronização de alertas interrompida", it) }
                .collect { alerts -> container.alertRepository.cacheRemoteAlerts(alerts) }
        }
        launch {
            container.firestoreService.observeAmateurRuns(tokens)
                .catch { Log.w(TAG, "Sincronização de participações interrompida", it) }
                .collect { runs -> container.amateurRunRepository.cacheRemoteRuns(runs) }
        }
        launch {
            container.firestoreService.observePublicProfiles()
                .catch { Log.w(TAG, "Sincronização do diretório interrompida", it) }
                .collect { profiles -> container.socialRepository.cachePublicProfiles(profiles) }
        }
    }

    /** Ouvintes da informação privada do utilizador autenticado. */
    private suspend fun syncPrivateData(uid: String) = coroutineScope {
        launch {
            var lastPublished: List<String>? = null
            container.firestoreService.observeSubscriptions(uid)
                .catch { Log.w(TAG, "Sincronização de subscrições interrompida", it) }
                .collect { subscriptions ->
                    container.raceRepository.cacheSubscriptions(subscriptions)
                    // As provas subscritas são publicadas no perfil público:
                    // delimitam o histórico visível do utilizador enquanto
                    // atleta e permitem que outra pessoa saiba se partilha
                    // alguma prova com ele.
                    val raceIds = subscriptions.map { it.raceId }.sorted()
                    if (raceIds != lastPublished) {
                        lastPublished = raceIds
                        container.socialRepository.publishSummary(uid, raceIds = raceIds)
                    }
                }
        }
        launch {
            container.firestoreService.observeAthleteSubscriptions(uid)
                .catch { Log.w(TAG, "Sincronização de atletas interrompida", it) }
                .collect { athletes ->
                    container.socialRepository.cacheAthleteSubscriptions(athletes)
                }
        }
        // Além de alimentar a cache local, estes dois ouvintes publicam os
        // totais no perfil público do próprio utilizador. É isso que permite que
        // outra pessoa verifique a capacidade disponível antes de lhe enviar
        // um pedido de amizade sem ler as suas subcoleções privadas, que as
        // regras de segurança reservam ao dono.
        launch {
            var lastPublished = -1
            container.firestoreService.observeFriends(uid)
                .catch { Log.w(TAG, "Sincronização de amigos interrompida", it) }
                .collect { friends ->
                    container.socialRepository.cacheFriends(friends)
                    if (friends.size != lastPublished) {
                        lastPublished = friends.size
                        container.socialRepository.publishSummary(uid, friends = friends.size)
                    }
                }
        }
        launch {
            var lastPublished = -1
            container.firestoreService
                .observeFriendRequests(uid, FriendRequestDirection.RECEIVED)
                .catch { Log.w(TAG, "Sincronização de pedidos recebidos interrompida", it) }
                .collect { requests ->
                    container.socialRepository.cacheRequests(
                        requests,
                        FriendRequestDirection.RECEIVED
                    )
                    if (requests.size != lastPublished) {
                        lastPublished = requests.size
                        container.socialRepository.publishSummary(uid, pending = requests.size)
                    }
                }
        }
        launch {
            container.firestoreService
                .observeFriendRequests(uid, FriendRequestDirection.SENT)
                .catch { Log.w(TAG, "Sincronização de pedidos enviados interrompida", it) }
                .collect { requests ->
                    container.socialRepository.cacheRequests(
                        requests,
                        FriendRequestDirection.SENT
                    )
                }
        }
        launch {
            container.firestoreService.observeProfile(uid)
                .catch { Log.w(TAG, "Sincronização de perfil interrompida", it) }
                .collect { profile ->
                    if (profile != null) container.authRepository.updateProfileCache(profile)
                }
        }
        launch { runCatching { container.raceRepository.pushPendingRaces() } }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
