package pt.ipp.estg.cmu.vivaracing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipp.estg.cmu.vivaracing.core.util.KmlParser
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint

/**
 * Testes da leitura das coordenadas de um bloco `<coordinates>` de um ficheiro KML.
 *
 * O KML guarda cada ponto como longitude,latitude[,altitude], ao contrário da ordem
 * habitual latitude/longitude, o que torna este passo fácil de inverter por engano.
 */
class KmlParserTest {

    @Test
    fun `le longitude e latitude pela ordem do KML`() {
        val points = KmlParser.parseCoordinates("-8.1926,41.3706,210")
        assertEquals(listOf(GeoPoint(latitude = 41.3706, longitude = -8.1926, altitude = 210.0)), points)
    }

    @Test
    fun `aceita pontos sem altitude e separados por espacos ou mudancas de linha`() {
        val raw = """
            -8.6109,41.1496
            -8.6110,41.1500,15
               -8.6120,41.1510
        """
        val points = KmlParser.parseCoordinates(raw)
        assertEquals(3, points.size)
        assertEquals(0.0, points[0].altitude, 0.0)
        assertEquals(15.0, points[1].altitude, 0.0)
        assertEquals(41.1510, points[2].latitude, 0.0)
    }

    @Test
    fun `ignora entradas incompletas ou que nao sao numeros`() {
        val points = KmlParser.parseCoordinates("-8.1,41.1 abc,def 42 -8.2,41.2")
        assertEquals(2, points.size)
        assertEquals(-8.2, points[1].longitude, 0.0)
    }

    @Test
    fun `texto vazio nao produz pontos`() {
        assertTrue(KmlParser.parseCoordinates("   \n  ").isEmpty())
    }
}
