package pt.ipp.estg.cmu.vivaracing.core.util

import pt.ipp.estg.cmu.vivaracing.data.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Funções geográficas usadas no cálculo de distâncias e enquadramento de mapas. */
object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Distância em metros entre dois pontos, pela fórmula de Haversine. */
    fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val deltaLatitude = Math.toRadians(endLatitude - startLatitude)
        val deltaLongitude = Math.toRadians(endLongitude - startLongitude)
        val a = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
            cos(Math.toRadians(startLatitude)) * cos(Math.toRadians(endLatitude)) *
            sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun distanceMeters(start: GeoPoint, end: GeoPoint): Double =
        distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)

    /** Comprimento total de um percurso, somando a distância entre pontos consecutivos. */
    fun routeLengthMeters(route: List<GeoPoint>): Double {
        if (route.size < 2) return 0.0
        var total = 0.0
        for (index in 1 until route.size) {
            total += distanceMeters(route[index - 1], route[index])
        }
        return total
    }

    /**
     * Determina se um ponto se encontra a menos de [toleranceMeters] de
     * qualquer ponto do percurso. É usado para distinguir quem está a observar
     * a prova no terreno de quem apenas consulta atualizações a distância.
     */
    fun isNearRoute(
        latitude: Double,
        longitude: Double,
        route: List<GeoPoint>,
        toleranceMeters: Double = 250.0
    ): Boolean = route.any {
        distanceMeters(latitude, longitude, it.latitude, it.longitude) <= toleranceMeters
    }

    /** Centro geométrico aproximado de um conjunto de pontos. */
    fun centerOf(points: List<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        val minLatitude = points.minOf { it.latitude }
        val maxLatitude = points.maxOf { it.latitude }
        val minLongitude = points.minOf { it.longitude }
        val maxLongitude = points.maxOf { it.longitude }
        return GeoPoint((minLatitude + maxLatitude) / 2, (minLongitude + maxLongitude) / 2)
    }

    /**
     * Nível de zoom aproximado para enquadrar um conjunto de pontos num mapa,
     * evitando o uso de animações assíncronas de bounds no arranque do ecrã.
     */
    fun suggestedZoom(points: List<GeoPoint>): Float {
        if (points.size < 2) return 14f
        val latitudeSpan = points.maxOf { it.latitude } - points.minOf { it.latitude }
        val longitudeSpan = points.maxOf { it.longitude } - points.minOf { it.longitude }
        val span = max(latitudeSpan, longitudeSpan)
        return when {
            span <= 0.005 -> 15f
            span <= 0.02 -> 13f
            span <= 0.08 -> 11f
            span <= 0.3 -> 9f
            span <= 1.0 -> 7f
            else -> 5f
        }.let { min(it, 16f) }
    }
}
