package pt.ipp.estg.cmu.vivaracing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters

/** Testes unitários da formatação de durações, distâncias e ritmo. */
class FormattersTest {

    @Test
    fun `duracao e apresentada em horas minutos e segundos`() {
        assertEquals("00:00:00", Formatters.formatDuration(0))
        assertEquals("00:01:05", Formatters.formatDuration(65))
        assertEquals("01:00:00", Formatters.formatDuration(3_600))
        assertEquals("02:03:04", Formatters.formatDuration(7_384))
    }

    @Test
    fun `duracao negativa e tratada como zero`() {
        assertEquals("00:00:00", Formatters.formatDuration(-10))
    }

    @Test
    fun `distancias abaixo de um quilometro sao apresentadas em metros`() {
        assertTrue(Formatters.formatDistance(850.0).endsWith("m"))
        assertTrue(Formatters.formatDistance(1_500.0).endsWith("km"))
    }

    @Test
    fun `ritmo de dez quilometros em cinquenta minutos e cinco minutos por quilometro`() {
        val pace = Formatters.formatPace(distanceMeters = 10_000.0, durationSeconds = 3_000)
        assertEquals("5:00 /km", pace)
    }

    @Test
    fun `ritmo indefinido quando nao ha distancia`() {
        assertEquals("--", Formatters.formatPace(0.0, 100))
    }
}
