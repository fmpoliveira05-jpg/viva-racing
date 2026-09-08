package pt.ipp.estg.cmu.vivaracing.data.remote.dto

import com.google.gson.annotations.SerializedName
import pt.ipp.estg.cmu.vivaracing.data.model.PlaceSuggestion
import pt.ipp.estg.cmu.vivaracing.data.model.WeatherInfo

/**
 * Objetos de transferência de dados (DTO) que espelham exatamente a resposta
 * JSON das APIs REST consumidas com Retrofit. A conversão para modelos de
 * domínio é feita nestes ficheiros para que o resto da aplicação nunca dependa
 * do formato dos serviços externos.
 */

// ----------------------------- OpenWeatherMap -----------------------------

data class WeatherResponseDto(
    @SerializedName("weather") val weather: List<WeatherDescriptionDto>?,
    @SerializedName("main") val main: WeatherMainDto?,
    @SerializedName("wind") val wind: WeatherWindDto?,
    @SerializedName("name") val name: String?
) {
    fun toDomain(): WeatherInfo {
        val description = weather?.firstOrNull()
        return WeatherInfo(
            locationName = name.orEmpty(),
            description = description?.description.orEmpty().replaceFirstChar { it.uppercase() },
            iconCode = description?.icon.orEmpty(),
            temperatureCelsius = main?.temperature ?: 0.0,
            feelsLikeCelsius = main?.feelsLike ?: 0.0,
            humidityPercent = main?.humidity ?: 0,
            windSpeedKmh = (wind?.speed ?: 0.0) * 3.6
        )
    }
}

data class WeatherDescriptionDto(
    @SerializedName("main") val main: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("icon") val icon: String?
)

data class WeatherMainDto(
    @SerializedName("temp") val temperature: Double?,
    @SerializedName("feels_like") val feelsLike: Double?,
    @SerializedName("humidity") val humidity: Int?
)

data class WeatherWindDto(
    @SerializedName("speed") val speed: Double?
)

/**
 * Resposta do endpoint de previsão a cinco dias, com intervalos de três horas.
 * É usada para apresentar as condições esperadas na hora de partida da prova,
 * e não apenas as condições do momento em que a prova é registada.
 */
data class ForecastResponseDto(
    @SerializedName("list") val slots: List<ForecastSlotDto>?,
    @SerializedName("city") val city: ForecastCityDto?
)

data class ForecastCityDto(
    @SerializedName("name") val name: String?
)

data class ForecastSlotDto(
    @SerializedName("dt") val timestampSeconds: Long?,
    @SerializedName("main") val main: WeatherMainDto?,
    @SerializedName("weather") val weather: List<WeatherDescriptionDto>?,
    @SerializedName("wind") val wind: WeatherWindDto?
) {
    fun toDomain(locationName: String, forecastFor: Long): WeatherInfo {
        val description = weather?.firstOrNull()
        return WeatherInfo(
            locationName = locationName,
            description = description?.description.orEmpty()
                .replaceFirstChar { it.uppercase() },
            iconCode = description?.icon.orEmpty(),
            temperatureCelsius = main?.temperature ?: 0.0,
            feelsLikeCelsius = main?.feelsLike ?: 0.0,
            humidityPercent = main?.humidity ?: 0,
            windSpeedKmh = (wind?.speed ?: 0.0) * 3.6,
            forecastFor = forecastFor
        )
    }
}

// ------------------------- Nominatim (OpenStreetMap) -----------------------

data class NominatimPlaceDto(
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("lat") val latitude: String?,
    @SerializedName("lon") val longitude: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("type") val type: String?
) {
    fun toDomain(): PlaceSuggestion? {
        val lat = latitude?.toDoubleOrNull() ?: return null
        val lon = longitude?.toDoubleOrNull() ?: return null
        return PlaceSuggestion(
            displayName = displayName.orEmpty(),
            latitude = lat,
            longitude = lon,
            category = listOfNotNull(category, type).joinToString(" / ")
        )
    }
}
