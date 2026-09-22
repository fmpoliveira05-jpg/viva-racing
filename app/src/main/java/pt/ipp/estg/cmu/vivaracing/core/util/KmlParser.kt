package pt.ipp.estg.cmu.vivaracing.core.util

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import java.io.InputStream

/** Resultado da importação de um ficheiro KML. */
data class KmlRoute(
    val name: String,
    val points: List<GeoPoint>
)

/**
 * Leitor de ficheiros KML com o formato exportado pela generalidade das
 * plataformas de desporto (Google Earth, Strava, Wikiloc).
 *
 * A análise é feita com o XmlPullParser da plataforma Android, evitando
 * dependências externas e mantendo o consumo de memória baixo, uma vez que o
 * documento é percorrido em modo de fluxo.
 */
object KmlParser {

    private const val TAG_NAME = "name"
    private const val TAG_COORDINATES = "coordinates"

    fun parse(input: InputStream): KmlRoute {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var routeName = ""
        val points = mutableListOf<GeoPoint>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    TAG_NAME -> {
                        val value = parser.nextText().trim()
                        if (routeName.isBlank() && value.isNotBlank()) routeName = value
                    }

                    TAG_COORDINATES -> {
                        points += parseCoordinates(parser.nextText())
                    }
                }
            }
            eventType = parser.next()
        }

        return KmlRoute(name = routeName, points = points)
    }

    /**
     * O bloco `<coordinates>` de um KML contém triplos separados por espaços,
     * com a ordem longitude,latitude[,altitude]. É `internal` para poder ser
     * testado sem o XmlPullParser da plataforma.
     */
    internal fun parseCoordinates(raw: String): List<GeoPoint> =
        raw.split(Regex("\\s+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { triple ->
                val parts = triple.split(',')
                if (parts.size < 2) return@mapNotNull null
                val longitude = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                val latitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                val altitude = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                GeoPoint(latitude = latitude, longitude = longitude, altitude = altitude)
            }
            .toList()
}
