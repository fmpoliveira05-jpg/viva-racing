package pt.ipp.estg.cmu.vivaracing.data

import android.content.Context
import pt.ipp.estg.cmu.vivaracing.core.location.LocationProvider
import pt.ipp.estg.cmu.vivaracing.core.notifications.NotificationHelper
import pt.ipp.estg.cmu.vivaracing.core.preferences.UserPreferences
import pt.ipp.estg.cmu.vivaracing.core.system.ConnectivityObserver
import pt.ipp.estg.cmu.vivaracing.core.system.SystemStateMonitor
import pt.ipp.estg.cmu.vivaracing.data.local.VivaRacingDatabase
import pt.ipp.estg.cmu.vivaracing.data.remote.api.RetrofitProvider
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirebaseAuthService
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService
import pt.ipp.estg.cmu.vivaracing.data.repository.AlertRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.AmateurRunRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.AuthRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.PlacesRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.RaceRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.SocialRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.StorageRepository
import pt.ipp.estg.cmu.vivaracing.data.repository.WeatherRepository

/**
 * Contentor de dependências da aplicação.
 *
 * Optou-se por injeção manual em vez de uma biblioteca como o Hilt: o número
 * de dependências é reduzido, o grafo é inteiramente explícito e evita-se um
 * processador de anotações adicional no tempo de compilação. Todas as
 * instâncias são criadas de forma preguiçosa (`by lazy`) e vivem enquanto o
 * processo da aplicação existir.
 */
class AppContainer(context: Context) {

    private val applicationContext: Context = context.applicationContext

    // -------------------------------- Base ---------------------------------
    val database: VivaRacingDatabase by lazy { VivaRacingDatabase.getInstance(applicationContext) }
    val preferences: UserPreferences by lazy { UserPreferences(applicationContext) }
    val notificationHelper: NotificationHelper by lazy { NotificationHelper(applicationContext) }
    val locationProvider: LocationProvider by lazy { LocationProvider(applicationContext) }
    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(applicationContext) }
    val systemStateMonitor: SystemStateMonitor by lazy { SystemStateMonitor(applicationContext) }

    // ------------------------------- Remoto --------------------------------
    val authService: FirebaseAuthService by lazy { FirebaseAuthService() }
    val firestoreService: FirestoreService by lazy { FirestoreService() }

    // ----------------------------- Repositórios ----------------------------
    val authRepository: AuthRepository by lazy {
        AuthRepository(authService, firestoreService, database.userProfileDao())
    }

    val raceRepository: RaceRepository by lazy {
        RaceRepository(database.raceDao(), database.subscriptionDao(), firestoreService)
    }

    val alertRepository: AlertRepository by lazy {
        AlertRepository(database.alertDao(), firestoreService)
    }

    val amateurRunRepository: AmateurRunRepository by lazy {
        AmateurRunRepository(database.amateurRunDao(), firestoreService)
    }

    val socialRepository: SocialRepository by lazy {
        SocialRepository(
            friendDao = database.friendDao(),
            friendRequestDao = database.friendRequestDao(),
            athleteSubscriptionDao = database.athleteSubscriptionDao(),
            publicProfileDao = database.publicProfileDao(),
            firestoreService = firestoreService,
            authRepository = authRepository
        )
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(RetrofitProvider.weatherApi)
    }

    val placesRepository: PlacesRepository by lazy {
        PlacesRepository(RetrofitProvider.nominatimApi)
    }

    val storageRepository: StorageRepository by lazy {
        StorageRepository(applicationContext, RetrofitProvider.supabaseStorageApi)
    }
}
