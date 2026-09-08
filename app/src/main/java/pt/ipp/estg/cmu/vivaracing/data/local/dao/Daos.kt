package pt.ipp.estg.cmu.vivaracing.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AlertEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AthleteSubscriptionEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.AmateurRunEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.FriendEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.FriendRequestEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.PublicProfileEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.RaceEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.SubscriptionEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.TrackPointEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.UserProfileEntity

/**
 * Objetos de acesso aos dados da base de dados local.
 *
 * Reúnem-se aqui os dez DAO do projeto, um por entidade, porque todos seguem
 * o mesmo desenho e ficam mais fáceis de comparar lado a lado do que
 * espalhados por dez ficheiros. As leituras destinadas à interface devolvem
 * `Flow`, de modo que qualquer escrita vinda da sincronização chega aos ecrãs
 * sem que estes voltem a consultar a base de dados. As leituras pontuais,
 * usadas pelos trabalhos periódicos, são funções suspensas e devolvem uma
 * fotografia do momento.
 *
 * A escrita passa quase sempre por `@Upsert`, uma vez que a sincronização
 * recebe do Firestore registos que tanto podem ser novos como já existentes,
 * e essa anotação resolve os dois casos sem exigir uma consulta prévia.
 */
@Dao
interface RaceDao {

    @Query("SELECT * FROM races ORDER BY startDateTime DESC")
    fun observeAll(): Flow<List<RaceEntity>>

    @Query("SELECT * FROM races WHERE id = :raceId LIMIT 1")
    fun observeById(raceId: String): Flow<RaceEntity?>

    @Query("SELECT * FROM races WHERE id = :raceId LIMIT 1")
    suspend fun findById(raceId: String): RaceEntity?

    @Query("SELECT * FROM races WHERE authorId = :authorId ORDER BY startDateTime DESC")
    fun observeByAuthor(authorId: String): Flow<List<RaceEntity>>

    @Query(
        "SELECT r.* FROM races AS r " +
            "INNER JOIN subscriptions AS s ON s.raceId = r.id " +
            "ORDER BY r.startDateTime DESC"
    )
    fun observeSubscribed(): Flow<List<RaceEntity>>

    @Query("SELECT COUNT(*) FROM races")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(race: RaceEntity)

    @Upsert
    suspend fun upsertAll(races: List<RaceEntity>)

    @Query("UPDATE races SET pendingSync = 0 WHERE id = :raceId")
    suspend fun markSynced(raceId: String)

    @Query("SELECT * FROM races WHERE pendingSync = 1")
    suspend fun findPending(): List<RaceEntity>

    @Query("DELETE FROM races WHERE id = :raceId")
    suspend fun deleteById(raceId: String)

    /**
     * Remove da cache as provas que deixaram de ser visíveis para o
     * utilizador, poupando os registos ainda por sincronizar.
     */
    @Query("DELETE FROM races WHERE pendingSync = 0 AND id NOT IN (:visibleIds)")
    suspend fun deleteMissing(visibleIds: List<String>)

    @Query("DELETE FROM races WHERE pendingSync = 0")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM races")
    suspend fun clear()
}

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE raceId = :raceId ORDER BY timestamp DESC")
    fun observeByRace(raceId: String): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE id = :alertId LIMIT 1")
    fun observeById(alertId: String): Flow<AlertEntity?>

    @Query(
        "SELECT a.* FROM alerts AS a " +
            "INNER JOIN subscriptions AS s ON s.raceId = a.raceId " +
            "ORDER BY a.timestamp DESC"
    )
    fun observeSubscribed(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun findNewerThan(since: Long): List<AlertEntity>

    @Upsert
    suspend fun upsert(alert: AlertEntity)

    @Upsert
    suspend fun upsertAll(alerts: List<AlertEntity>)

    @Query("UPDATE alerts SET pendingSync = 0 WHERE id = :alertId")
    suspend fun markSynced(alertId: String)

    @Query(
        "SELECT a.* FROM alerts AS a " +
            "INNER JOIN athlete_subscriptions AS s ON s.athleteUid = a.athleteUserId " +
            "ORDER BY a.timestamp DESC"
    )
    fun observeSubscribedAthletes(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE athleteUserId = :athleteUid ORDER BY timestamp DESC")
    fun observeByAthlete(athleteUid: String): Flow<List<AlertEntity>>

    @Query("DELETE FROM alerts WHERE pendingSync = 0 AND id NOT IN (:visibleIds)")
    suspend fun deleteMissing(visibleIds: List<String>)

    @Query("DELETE FROM alerts WHERE pendingSync = 0")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM alerts")
    suspend fun clear()
}

@Dao
interface AmateurRunDao {

    @Query("SELECT * FROM amateur_runs ORDER BY endTime DESC")
    fun observeAll(): Flow<List<AmateurRunEntity>>

    @Query("SELECT * FROM amateur_runs WHERE raceId = :raceId ORDER BY durationSeconds ASC")
    fun observeByRace(raceId: String): Flow<List<AmateurRunEntity>>

    @Query("SELECT * FROM amateur_runs WHERE userId = :userId ORDER BY endTime DESC")
    fun observeByUser(userId: String): Flow<List<AmateurRunEntity>>

    @Query("SELECT * FROM amateur_runs WHERE id = :runId LIMIT 1")
    fun observeById(runId: String): Flow<AmateurRunEntity?>

    @Upsert
    suspend fun upsert(run: AmateurRunEntity)

    @Upsert
    suspend fun upsertAll(runs: List<AmateurRunEntity>)

    @Query("UPDATE amateur_runs SET pendingSync = 0 WHERE id = :runId")
    suspend fun markSynced(runId: String)

    @Query("DELETE FROM amateur_runs WHERE pendingSync = 0 AND id NOT IN (:visibleIds)")
    suspend fun deleteMissing(visibleIds: List<String>)

    @Query("DELETE FROM amateur_runs WHERE pendingSync = 0")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM amateur_runs")
    suspend fun clear()
}

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE raceId = :raceId")
    fun observeIsSubscribed(raceId: String): Flow<Int>

    @Query("SELECT raceId FROM subscriptions")
    suspend fun findAllIds(): List<String>

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Upsert
    suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE raceId = :raceId")
    suspend fun deleteById(raceId: String)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    fun observeById(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    suspend fun findById(uid: String): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun findBySession(sessionId: String): List<TrackPointEntity>

    @Upsert
    suspend fun insert(point: TrackPointEntity)

    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM track_points")
    suspend fun clear()
}

@Dao
interface FriendDao {

    @Query("SELECT * FROM friends ORDER BY username COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FriendEntity>>

    @Query("SELECT uid FROM friends")
    suspend fun findAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM friends")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM friends WHERE uid = :uid")
    fun observeIsFriend(uid: String): Flow<Int>

    @Upsert
    suspend fun upsertAll(friends: List<FriendEntity>)

    @Query("DELETE FROM friends WHERE uid = :uid")
    suspend fun deleteById(uid: String)

    @Query("DELETE FROM friends")
    suspend fun clear()
}

@Dao
interface FriendRequestDao {

    @Query("SELECT * FROM friend_requests WHERE direction = :direction ORDER BY sentAt DESC")
    fun observeByDirection(direction: String): Flow<List<FriendRequestEntity>>

    @Query("SELECT COUNT(*) FROM friend_requests WHERE direction = :direction")
    fun observeCountByDirection(direction: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM friend_requests WHERE direction = :direction")
    suspend fun countByDirection(direction: String): Int

    @Upsert
    suspend fun upsertAll(requests: List<FriendRequestEntity>)

    @Query("DELETE FROM friend_requests WHERE direction = :direction")
    suspend fun clearDirection(direction: String)

    @Query("DELETE FROM friend_requests WHERE uid = :uid")
    suspend fun deleteById(uid: String)

    @Query("DELETE FROM friend_requests")
    suspend fun clear()
}

@Dao
interface AthleteSubscriptionDao {

    @Query("SELECT * FROM athlete_subscriptions ORDER BY username COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AthleteSubscriptionEntity>>

    @Query("SELECT athleteUid FROM athlete_subscriptions")
    suspend fun findAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM athlete_subscriptions WHERE athleteUid = :athleteUid")
    fun observeIsSubscribed(athleteUid: String): Flow<Int>

    @Upsert
    suspend fun upsertAll(subscriptions: List<AthleteSubscriptionEntity>)

    @Query("DELETE FROM athlete_subscriptions WHERE athleteUid = :athleteUid")
    suspend fun deleteById(athleteUid: String)

    @Query("DELETE FROM athlete_subscriptions")
    suspend fun clear()
}

@Dao
interface PublicProfileDao {

    @Query(
        "SELECT * FROM public_profiles " +
            "WHERE username LIKE '%' || :query || '%' COLLATE NOCASE " +
            "OR city LIKE '%' || :query || '%' COLLATE NOCASE " +
            "ORDER BY username COLLATE NOCASE ASC LIMIT 40"
    )
    fun search(query: String): Flow<List<PublicProfileEntity>>

    @Query("SELECT * FROM public_profiles WHERE uid = :uid LIMIT 1")
    suspend fun findById(uid: String): PublicProfileEntity?

    @Query("SELECT * FROM public_profiles WHERE uid = :uid LIMIT 1")
    fun observeById(uid: String): Flow<PublicProfileEntity?>

    @Upsert
    suspend fun upsertAll(profiles: List<PublicProfileEntity>)

    @Query("DELETE FROM public_profiles")
    suspend fun clear()
}
