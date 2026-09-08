package pt.ipp.estg.cmu.vivaracing.data.repository

import pt.ipp.estg.cmu.vivaracing.BuildConfig
import pt.ipp.estg.cmu.vivaracing.data.model.WeatherInfo
import pt.ipp.estg.cmu.vivaracing.data.remote.api.WeatherApi
import java.util.Locale
import kotlin.math.abs

/**
 * Consulta a API REST da OpenWeatherMap.
 *
 * Cumpre o requisito de usar uma API REST para validar e enriquecer a
 * informação de uma prova em dois momentos distintos: confirma que as
 * coordenadas escolhidas correspondem a um local reconhecido, devolvendo o
 * respetivo topónimo, e obtém as condições esperadas para a hora de partida.
 */
class WeatherRepository(private val weatherApi: WeatherApi) {

    suspend fun currentWeather(latitude: Double, longitude: Double): Result<WeatherInfo> =
        runCatching {
            require(BuildConfig.WEATHER_API_KEY.isNotBlank()) { "Chave da OpenWeatherMap em falta" }
            weatherApi.getCurrentWeather(
                latitude = latitude,
                longitude = longitude,
                apiKey = BuildConfig.WEATHER_API_KEY,
                language = Locale.getDefault().language
            ).toDomain()
        }

    /**
     * Condições previstas para o instante indicado.
     *
     * A API devolve janelas de três horas até cinco dias. Escolhe-se a janela
     * temporalmente mais próxima da partida; se a data estiver fora do
     * horizonte de previsão, ou se esta falhar, recorre-se às condições
     * atuais, para que o ecrã nunca fique sem informação.
     */
    suspend fun weatherForRace(
        latitude: Double,
        longitude: Double,
        startDateTime: Long
    ): Result<WeatherInfo> {
        val horizon = System.currentTimeMillis() + FORECAST_HORIZON_MILLIS
        if (startDateTime <= System.currentTimeMillis() || startDateTime > horizon) {
            return currentWeather(latitude, longitude)
        }

        val forecast = runCatching {
            require(BuildConfig.WEATHER_API_KEY.isNotBlank()) { "Chave da OpenWeatherMap em falta" }
            val response = weatherApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                apiKey = BuildConfig.WEATHER_API_KEY,
                language = Locale.getDefault().language
            )
            val locationName = response.city?.name.orEmpty()
            val closest = response.slots.orEmpty()
                .filter { it.timestampSeconds != null }
                .minByOrNull { abs((it.timestampSeconds!! * 1_000) - startDateTime) }
                ?: error("Previsao sem intervalos utilizaveis")

            closest.toDomain(
                locationName = locationName,
                forecastFor = closest.timestampSeconds!! * 1_000
            )
        }

        return if (forecast.isSuccess) forecast else currentWeather(latitude, longitude)
    }

    private companion object {
        /** A previsão gratuita da OpenWeatherMap cobre cinco dias. */
        const val FORECAST_HORIZON_MILLIS = 5L * 24 * 60 * 60 * 1000
    }
}
