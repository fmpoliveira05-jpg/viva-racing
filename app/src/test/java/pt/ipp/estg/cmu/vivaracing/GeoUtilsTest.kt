package pt.ipp.estg.cmu.vivaracing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint

/**
 * Testes unitários das funções geográficas.
 *
 * Estas funções sustentam o cálculo da distância dos percursos e a regra que
 * determina se um utilizador está suficientemente perto da prova para publicar
 * alertas, pelo que são validadas de forma isolada.
 */
class GeoUtilsTest {

    @Test
    fun `distancia entre o mesmo ponto e zero`() {
        val distance = GeoUtils.distanceMeters(41.3706, -8.1926, 41.3706, -8.1926)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `distancia entre Felgueiras e o Porto e proxima de 40 km`() {
        val felgueiras = GeoPoint(41.3706, -8.1926)
        val porto = GeoPoint(41.1496, -8.6109)
        val distance = GeoUtils.distanceMeters(felgueiras, porto)
        assertTrue("Distancia obtida: $distance", distance in 38_000.0..44_000.0)
    }

    @Test
    fun `comprimento de percurso soma os trocos consecutivos`() {
        val route = listOf(
            GeoPoint(41.0000, -8.0000),
            GeoPoint(41.0100, -8.0000),
            GeoPoint(41.0200, -8.0000)
        )
        val total = GeoUtils.routeLengthMeters(route)
        val firstLeg = GeoUtils.distanceMeters(route[0], route[1])
        val secondLeg = GeoUtils.distanceMeters(route[1], route[2])
        assertEquals(firstLeg + secondLeg, total, 0.001)
    }

    @Test
    fun `percurso com menos de dois pontos tem comprimento nulo`() {
        assertEquals(0.0, GeoUtils.routeLengthMeters(emptyList()), 0.0)
        assertEquals(0.0, GeoUtils.routeLengthMeters(listOf(GeoPoint(41.0, -8.0))), 0.0)
    }

    @Test
    fun `proximidade ao percurso respeita a tolerancia definida`() {
        val route = listOf(GeoPoint(41.0000, -8.0000), GeoPoint(41.0010, -8.0000))
        assertTrue(GeoUtils.isNearRoute(41.0005, -8.0000, route, toleranceMeters = 250.0))
        assertFalse(GeoUtils.isNearRoute(41.5000, -8.0000, route, toleranceMeters = 250.0))
    }

    @Test
    fun `centro de um conjunto de pontos e o ponto medio dos extremos`() {
        val points = listOf(
            GeoPoint(41.0, -8.0),
            GeoPoint(43.0, -6.0)
        )
        val center = GeoUtils.centerOf(points)
        assertEquals(42.0, center!!.latitude, 0.0001)
        assertEquals(-7.0, center.longitude, 0.0001)
    }
}
