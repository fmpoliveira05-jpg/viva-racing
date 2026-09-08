package pt.ipp.estg.cmu.vivaracing.data.remote.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import pt.ipp.estg.cmu.vivaracing.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Constrói e expõe as instâncias de Retrofit usadas pela aplicação.
 *
 * Todas partilham o mesmo [OkHttpClient] (pool de ligações e cache de sockets
 * reutilizados) e diferem apenas no URL base e nos cabeçalhos exigidos por
 * cada serviço.
 */
object RetrofitProvider {

    private const val OPEN_WEATHER_BASE_URL = "https://api.openweathermap.org/"
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"

    /**
     * A política de utilização do Nominatim obriga a identificar a aplicação
     * através do cabeçalho User-Agent.
     */
    private const val USER_AGENT =
        "VivaRacing/1.0 (ESTG-IPP; Computacao Movel e Ubiqua; 8230148)"

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val weatherApi: WeatherApi by lazy {
        buildRetrofit(OPEN_WEATHER_BASE_URL).create(WeatherApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        buildRetrofit(NOMINATIM_BASE_URL).create(NominatimApi::class.java)
    }

    val supabaseStorageApi: SupabaseStorageApi by lazy {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/"
        buildRetrofit(baseUrl).create(SupabaseStorageApi::class.java)
    }
}
