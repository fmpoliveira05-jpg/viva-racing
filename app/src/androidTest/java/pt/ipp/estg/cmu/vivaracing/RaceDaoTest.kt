package pt.ipp.estg.cmu.vivaracing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.ipp.estg.cmu.vivaracing.data.local.VivaRacingDatabase
import pt.ipp.estg.cmu.vivaracing.data.local.entity.RaceEntity
import pt.ipp.estg.cmu.vivaracing.data.local.entity.SubscriptionEntity
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint

/**
 * Testes instrumentados da camada de persistência local.
 *
 * Validam a conversão do percurso para JSON, a operação de upsert e a consulta
 * que cruza provas com subscrições, que suporta o filtro de provas seguidas.
 */
@RunWith(AndroidJUnit4::class)
class RaceDaoTest {

    private lateinit var database: VivaRacingDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VivaRacingDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleRace(id: String, name: String) = RaceEntity(
        id = id,
        name = name,
        description = "",
        type = "RUNNING",
        status = "SCHEDULED",
        city = "Felgueiras",
        startDateTime = 1_700_000_000_000,
        startLatitude = 41.3706,
        startLongitude = -8.1926,
        distanceMeters = 10_000.0,
        route = listOf(GeoPoint(41.3706, -8.1926), GeoPoint(41.3800, -8.2000)),
        routeSource = "MANUAL",
        photoUrl = null,
        organizerPhone = "",
        authorId = "user-1",
        authorName = "Xico",
        createdAt = 0L,
        updatedAt = 0L,
        pendingSync = true
    )

    @Test
    fun insercaoEleituraPreservamOPercurso() = runBlocking {
        database.raceDao().upsert(sampleRace("race-1", "Maratona do Porto"))

        val stored = database.raceDao().findById("race-1")
        assertEquals("Maratona do Porto", stored?.name)
        assertEquals(2, stored?.route?.size)
        assertEquals(41.3706, stored!!.route.first().latitude, 0.00001)
    }

    @Test
    fun marcarComoSincronizadaLimpaOEstadoPendente() = runBlocking {
        database.raceDao().upsert(sampleRace("race-2", "Trail de Felgueiras"))
        assertTrue(database.raceDao().findPending().isNotEmpty())

        database.raceDao().markSynced("race-2")
        assertTrue(database.raceDao().findPending().isEmpty())
    }

    @Test
    fun consultaDeProvasSubscritasDevolveApenasAsSeguidas() = runBlocking {
        database.raceDao().upsert(sampleRace("race-3", "Volta a Felgueiras"))
        database.raceDao().upsert(sampleRace("race-4", "Corrida de Sao Silvestre"))
        database.subscriptionDao().upsert(
            SubscriptionEntity("race-4", "Corrida de Sao Silvestre", 1_700_000_000_000)
        )

        val subscribed = database.raceDao().observeSubscribed().first()
        assertEquals(1, subscribed.size)
        assertEquals("race-4", subscribed.first().id)
    }
}
