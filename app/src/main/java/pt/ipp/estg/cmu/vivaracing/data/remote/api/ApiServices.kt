package pt.ipp.estg.cmu.vivaracing.data.remote.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import pt.ipp.estg.cmu.vivaracing.data.remote.dto.ForecastResponseDto
import pt.ipp.estg.cmu.vivaracing.data.remote.dto.NominatimPlaceDto
import pt.ipp.estg.cmu.vivaracing.data.remote.dto.WeatherResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface da API REST da OpenWeatherMap.
 *
 * É usada para validar o local de partida de uma prova e para apresentar as
 * condições meteorológicas previstas no ecrã de detalhe.
 */
interface WeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "pt"
    ): WeatherResponseDto

    /**
     * Previsão a cinco dias em intervalos de três horas. Permite validar as
     * condições esperadas para a data e hora de partida da prova.
     */
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "pt"
    ): ForecastResponseDto
}

/**
 * Interface da API de geocodificação Nominatim (OpenStreetMap).
 *
 * Permite pesquisar locais para decidir onde realizar a atividade física e
 * obter o nome da localidade a partir de coordenadas (geocodificação inversa).
 */
interface NominatimApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 8,
        @Query("addressdetails") addressDetails: Int = 0,
        @Query("accept-language") language: String = "pt"
    ): List<NominatimPlaceDto>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "jsonv2",
        @Query("zoom") zoom: Int = 14,
        @Query("accept-language") language: String = "pt"
    ): NominatimPlaceDto
}

/**
 * Interface da API de armazenamento do Supabase.
 *
 * O upload é feito diretamente sobre a API REST do serviço, o que dispensa
 * bibliotecas adicionais e reutiliza o cliente OkHttp já presente no projeto.
 */
interface SupabaseStorageApi {

    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadObject(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) path: String,
        @Header("Authorization") authorization: String,
        @Header("apikey") apiKey: String,
        @Header("x-upsert") upsert: String = "true",
        @Body body: RequestBody
    ): Response<ResponseBody>
}
