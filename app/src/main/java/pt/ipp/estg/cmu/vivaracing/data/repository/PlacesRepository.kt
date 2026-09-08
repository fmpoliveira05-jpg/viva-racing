package pt.ipp.estg.cmu.vivaracing.data.repository

import pt.ipp.estg.cmu.vivaracing.data.model.PlaceSuggestion
import pt.ipp.estg.cmu.vivaracing.data.remote.api.NominatimApi
import java.util.Locale

/**
 * Pesquisa e geocodificação inversa de locais através da API Nominatim do
 * OpenStreetMap. Permite ao utilizador escolher o local de partida da prova
 * sem ter de conhecer as coordenadas.
 */
class PlacesRepository(private val nominatimApi: NominatimApi) {

    suspend fun search(query: String): Result<List<PlaceSuggestion>> = runCatching {
        if (query.trim().length < MIN_QUERY_LENGTH) return@runCatching emptyList()
        nominatimApi.search(
            query = query.trim(),
            language = Locale.getDefault().language
        ).mapNotNull { it.toDomain() }
    }

    suspend fun reverse(latitude: Double, longitude: Double): Result<PlaceSuggestion?> =
        runCatching {
            nominatimApi.reverse(
                latitude = latitude,
                longitude = longitude,
                language = Locale.getDefault().language
            ).toDomain()
        }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
    }
}
