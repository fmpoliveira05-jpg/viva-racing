package pt.ipp.estg.cmu.vivaracing.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AlertDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AthleteSubscriptionDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.AmateurRunDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.FriendDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.FriendRequestDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.PublicProfileDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.RaceDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.SubscriptionDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.TrackPointDao
import pt.ipp.estg.cmu.vivaracing.data.local.dao.UserProfileDao
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
 * Base de dados local em SQLite gerida pelo Room.
 *
 * Guarda a cópia local de toda a informação publica sincronizada a partir do
 * Firestore, bem como o perfil e as subscrições do utilizador autenticado.
 */
@Database(
    entities = [
        RaceEntity::class,
        AlertEntity::class,
        AmateurRunEntity::class,
        SubscriptionEntity::class,
        UserProfileEntity::class,
        TrackPointEntity::class,
        FriendEntity::class,
        FriendRequestEntity::class,
        AthleteSubscriptionEntity::class,
        PublicProfileEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VivaRacingDatabase : RoomDatabase() {

    abstract fun raceDao(): RaceDao
    abstract fun alertDao(): AlertDao
    abstract fun amateurRunDao(): AmateurRunDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun friendDao(): FriendDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun athleteSubscriptionDao(): AthleteSubscriptionDao
    abstract fun publicProfileDao(): PublicProfileDao

    companion object {

        private const val DATABASE_NAME = "viva_racing.db"

        /**
         * Migração 1 -> 2: acrescenta a contagem de passos às participações
         * amadoras, resultado da integração do sensor de contador de passos.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE amateur_runs ADD COLUMN steps INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migração 2 -> 3: introduz a componente social (amigos, pedidos de
         * amizade, subscrições de atletas e diretório público de perfis) e os
         * campos de visibilidade das provas, alertas e participações.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE races ADD COLUMN visibility TEXT NOT NULL DEFAULT 'PUBLIC'")
                db.execSQL("ALTER TABLE races ADD COLUMN visibleTo TEXT NOT NULL DEFAULT 'public'")

                db.execSQL("ALTER TABLE alerts ADD COLUMN athleteUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alerts ADD COLUMN athleteName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alerts ADD COLUMN visibleTo TEXT NOT NULL DEFAULT 'public'")

                db.execSQL("ALTER TABLE amateur_runs ADD COLUMN visibleTo TEXT NOT NULL DEFAULT 'public'")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS friends (" +
                        "uid TEXT NOT NULL, username TEXT NOT NULL, city TEXT NOT NULL, " +
                        "since INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS friend_requests (" +
                        "uid TEXT NOT NULL, username TEXT NOT NULL, city TEXT NOT NULL, " +
                        "sentAt INTEGER NOT NULL, direction TEXT NOT NULL, PRIMARY KEY(uid))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS athlete_subscriptions (" +
                        "athleteUid TEXT NOT NULL, username TEXT NOT NULL, " +
                        "bibNumber TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY(athleteUid))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS public_profiles (" +
                        "uid TEXT NOT NULL, username TEXT NOT NULL, city TEXT NOT NULL, " +
                        "photoUrl TEXT, updatedAt INTEGER NOT NULL, PRIMARY KEY(uid))"
                )
            }
        }

        /**
         * Migração 3 -> 4: acrescenta ao diretório público a lista de provas
         * subscritas por cada utilizador. É isso que permite determinar, sem
         * aceder a informação privada de terceiros, se dois utilizadores
         * partilham uma prova e quais as passagens que compõem o histórico
         * público de um atleta.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE public_profiles ADD COLUMN raceIds TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: VivaRacingDatabase? = null

        fun getInstance(context: Context): VivaRacingDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context): VivaRacingDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                VivaRacingDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
