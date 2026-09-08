package pt.ipp.estg.cmu.vivaracing.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.VisibilityTokens
import pt.ipp.estg.cmu.vivaracing.ui.navigation.Destinations

/**
 * Trabalho periódico que deteta novidades da comunidade e notifica o
 * utilizador mesmo com a aplicação encerrada.
 *
 * Percorre quatro origens distintas, cada uma governada pela respetiva
 * preferência:
 *
 *  1. alertas publicados nas provas que o utilizador subscreveu;
 *  2. alertas de passagem dos atletas que subscreveu, identificados pelo
 *     campo `athleteUserId` do alerta;
 *  3. provas novas registadas por amigos;
 *  4. participações amadoras novas publicadas por amigos, que correspondem
 *     ao elemento de bonificação previsto no enunciado.
 *
 * A leitura respeita a visibilidade: os marcadores usados na consulta são os
 * mesmos da sincronização em primeiro plano, pelo que o trabalho nunca
 * acede a conteúdo privado de terceiros.
 */
class CommunityUpdatesWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as VivaRacingApp
        val container = app.container
        val preferences = container.preferences
        val settings = preferences.snapshot()

        val uid = container.authRepository.currentUid
        if (uid == null || container.authRepository.isGuest) {
            Log.i(TAG, "Sem sessão registada: nada a verificar")
            return Result.success()
        }

        if (!settings.notificationsEnabled) {
            Log.i(TAG, "Notificações desativadas nas preferencias")
            return Result.success()
        }

        val since = preferences.lastAlertCheck.takeIf { it > 0L }
            ?: (System.currentTimeMillis() - DEFAULT_WINDOW_MILLIS)

        return runCatching {
            val friendIds = container.socialRepository.friendIds().toSet()
            val subscribedRaceIds = container.database.subscriptionDao().findAllIds().toSet()
            val subscribedAthleteIds = container.socialRepository.athleteSubscriptionIds().toSet()

            val tokens = VisibilityTokens.forViewer(uid, friendIds, isGuest = false)

            // Sem este resumo, uma verificação que não notifica nada fica
            // indistinguível de uma verificação que não chegou a correr.
            Log.i(
                TAG,
                "Sessão $uid, ${friendIds.size} amigos, " +
                    "${subscribedRaceIds.size} provas subscritas, " +
                    "${subscribedAthleteIds.size} atletas seguidos"
            )

            var sent = 0

            // ---------------------------------------------- alertas
            val alerts = container.alertRepository.fetchAlertsSince(tokens, since)
            container.alertRepository.cacheRemoteAlerts(alerts)
            Log.i(TAG, "Alertas visíveis desde a última verificação: ${alerts.size}")

            alerts.asSequence()
                .filter { it.authorId != uid }
                .filter { !settings.notifyOnlyFriends || friendIds.contains(it.authorId) }
                .filter { alert ->
                    val fromSubscribedRace = subscribedRaceIds.contains(alert.raceId)
                    val aboutSubscribedAthlete = settings.notifyAthletePositions &&
                        alert.athleteUserId.isNotBlank() &&
                        subscribedAthleteIds.contains(alert.athleteUserId)
                    val fromSubscribedAthlete = settings.notifyAthletePositions &&
                        subscribedAthleteIds.contains(alert.authorId)

                    if (settings.notifyOnlySubscribed) {
                        fromSubscribedRace || aboutSubscribedAthlete || fromSubscribedAthlete
                    } else {
                        true
                    }
                }
                .take(MAX_NOTIFICATIONS)
                .forEach { alert ->
                    val title = when (alert.type) {
                        AlertType.RACE_START -> applicationContext.getString(
                            R.string.notification_race_started, alert.raceName
                        )

                        AlertType.RACE_FINISH -> applicationContext.getString(
                            R.string.notification_race_finished, alert.raceName
                        )

                        AlertType.INCIDENT -> applicationContext.getString(
                            R.string.notification_incident, alert.raceName
                        )

                        AlertType.ATHLETE_PASSING -> {
                            val athlete = alert.athleteName.ifBlank {
                                alert.athleteBib?.toString().orEmpty()
                            }
                            applicationContext.getString(
                                R.string.notification_athlete_passing, athlete, alert.raceName
                            )
                        }
                    }
                    container.notificationHelper.showCommunityNotification(
                        id = alert.id.hashCode(),
                        title = title,
                        content = alert.message.ifBlank {
                            applicationContext.getString(R.string.notification_open_details)
                        },
                        deepLinkRoute = Destinations.alertDetailRoute(alert.id)
                    )
                    sent++
                }

            // ------------------------------------- provas novas de amigos
            if (settings.notifyFriendActivity && friendIds.isNotEmpty()) {
                val races = container.raceRepository.fetchRacesSince(tokens, since)
                container.raceRepository.cacheRemoteRaces(races)

                races.asSequence()
                    .filter { it.authorId != uid && friendIds.contains(it.authorId) }
                    .take(MAX_NOTIFICATIONS)
                    .forEach { race ->
                        container.notificationHelper.showCommunityNotification(
                            id = race.id.hashCode(),
                            title = applicationContext.getString(
                                R.string.notification_friend_race, race.authorName
                            ),
                            content = applicationContext.getString(
                                R.string.notification_friend_race_body, race.name
                            ),
                            deepLinkRoute = Destinations.raceDetailRoute(race.id)
                        )
                        sent++
                    }
            }

            // ------------------------ participações amadoras de amigos
            if (settings.notifyFriendActivity) {
                val runs = container.amateurRunRepository.fetchRunsSince(tokens, since)
                container.amateurRunRepository.cacheRemoteRuns(runs)

                runs.asSequence()
                    .filter { it.userId != uid }
                    .filter {
                        friendIds.contains(it.userId) || subscribedAthleteIds.contains(it.userId)
                    }
                    .take(MAX_NOTIFICATIONS)
                    .forEach { run ->
                        val name = run.publicName(
                            applicationContext.getString(R.string.anonymous_athlete)
                        )
                        container.notificationHelper.showCommunityNotification(
                            id = run.id.hashCode(),
                            title = applicationContext.getString(
                                R.string.notification_new_amateur_run, name
                            ),
                            content = applicationContext.getString(
                                R.string.notification_new_amateur_run_body, run.raceName
                            ),
                            deepLinkRoute = Destinations.amateurRunDetailRoute(run.id)
                        )
                        sent++
                    }
            }

            Log.i(TAG, "Verificação concluida: $sent notificações enviadas")
            preferences.lastAlertCheck = System.currentTimeMillis()
            Result.success()
        }.getOrElse { throwable ->
            Log.w(TAG, "Verificação de novidades falhou", throwable)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "CommunityUpdatesWorker"
        const val MAX_NOTIFICATIONS = 5
        const val DEFAULT_WINDOW_MILLIS = 24L * 60 * 60 * 1000
    }
}
