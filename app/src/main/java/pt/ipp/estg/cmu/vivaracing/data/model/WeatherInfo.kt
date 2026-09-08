package pt.ipp.estg.cmu.vivaracing.data.model

/**
 * Informação meteorológica devolvida pela API REST da OpenWeatherMap para o
 * ponto de partida de uma prova. É usada para validar e enriquecer a prova
 * criada pelo utilizador, conforme exigido no enunciado.
 */
data class WeatherInfo(
    val locationName: String,
    val description: String,
    val iconCode: String,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    /**
     * Instante a que a informação diz respeito. Zero significa condições
     * atuais; um valor positivo identifica a janela de previsão escolhida.
     */
    val forecastFor: Long = 0L
) {
    val isForecast: Boolean get() = forecastFor > 0L
}

/** Resultado de pesquisa de locais devolvido pela API Nominatim (OpenStreetMap). */
data class PlaceSuggestion(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = ""
)
