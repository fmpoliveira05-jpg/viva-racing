package pt.ipp.estg.cmu.vivaracing.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import pt.ipp.estg.cmu.vivaracing.data.AppContainer
import pt.ipp.estg.cmu.vivaracing.ui.screens.alerts.AlertDetailScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.alerts.AlertFormScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.alerts.AlertListScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.amateur.AmateurRunDetailScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.amateur.AmateurRunListScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.home.HomeScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.info.AboutScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.map.MapScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.profile.ProfileScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.races.RaceDetailScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.races.RaceFormScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.races.RaceListScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.sensors.SensorsScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.social.AthleteDetailScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.social.AthletesScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.social.FriendsScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.settings.SettingsScreen
import pt.ipp.estg.cmu.vivaracing.ui.screens.tracking.TrackingScreen

/**
 * Grafo de navegação principal.
 *
 * Cada tipo de registo, ou seja, as provas, os alertas e as participações
 * amadoras, tem um ecrã de lista próprio e está também representado no mapa
 * da aplicação, numa
 * camada que pode ser ligada ou desligada. Cumpre-se assim o requisito do
 * enunciado de que todas as listagens sejam apresentadas nos dois formatos,
 * com ecrã de detalhe comum: os marcadores do mapa abrem exatamente o mesmo
 * detalhe a que as listas conduzem.
 */
@Composable
fun VivaRacingNavHost(
    navController: NavHostController,
    container: AppContainer,
    snackbarHostState: SnackbarHostState
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) },
                onOpenAlert = { navController.navigate(Destinations.alertDetailRoute(it)) },
                onOpenRoute = { navController.navigate(it) }
            )
        }

        // ------------------------------ Provas -----------------------------
        composable(Destinations.RACE_LIST) {
            RaceListScreen(
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) }
            )
        }

        // Mapa único, com camadas comutáveis para os três tipos de registo.
        composable(Destinations.MAP) {
            MapScreen(
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) },
                onOpenAlert = { navController.navigate(Destinations.alertDetailRoute(it)) },
                onOpenRun = { navController.navigate(Destinations.amateurRunDetailRoute(it)) }
            )
        }

        composable(
            route = Destinations.RACE_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_RACE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val raceId = backStackEntry.arguments?.getString(Destinations.ARG_RACE_ID).orEmpty()
            RaceDetailScreen(
                raceId = raceId,
                snackbarHostState = snackbarHostState,
                onOpenAlertForm = { navController.navigate(Destinations.alertFormRoute(it)) },
                onOpenAlert = { navController.navigate(Destinations.alertDetailRoute(it)) },
                onOpenRun = { navController.navigate(Destinations.amateurRunDetailRoute(it)) },
                onStartAmateurRun = {
                    navController.navigate(
                        Destinations.trackingRoute(raceId, Destinations.MODE_AMATEUR_RUN)
                    )
                }
            )
        }

        composable(Destinations.RACE_FORM) {
            RaceFormScreen(
                snackbarHostState = snackbarHostState,
                onRecordRoute = {
                    navController.navigate(
                        Destinations.trackingRoute(
                            Destinations.NEW_RACE_PLACEHOLDER,
                            Destinations.MODE_OFFICIAL_ROUTE
                        )
                    )
                },
                onSaved = { raceId ->
                    navController.popBackStack()
                    navController.navigate(Destinations.raceDetailRoute(raceId))
                }
            )
        }

        // ------------------------------ Alertas ----------------------------
        composable(Destinations.ALERT_LIST) {
            AlertListScreen(
                onOpenAlert = { navController.navigate(Destinations.alertDetailRoute(it)) }
            )
        }

        composable(
            route = Destinations.ALERT_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_ALERT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            AlertDetailScreen(
                alertId = backStackEntry.arguments?.getString(Destinations.ARG_ALERT_ID).orEmpty(),
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) }
            )
        }

        composable(
            route = Destinations.ALERT_FORM,
            arguments = listOf(navArgument(Destinations.ARG_RACE_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            AlertFormScreen(
                raceId = backStackEntry.arguments?.getString(Destinations.ARG_RACE_ID).orEmpty(),
                snackbarHostState = snackbarHostState,
                onSaved = { navController.popBackStack() }
            )
        }

        // ------------------------ Participações amadoras -------------------
        composable(Destinations.AMATEUR_LIST) {
            AmateurRunListScreen(
                onOpenRun = { navController.navigate(Destinations.amateurRunDetailRoute(it)) }
            )
        }

        composable(
            route = Destinations.AMATEUR_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_RUN_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            AmateurRunDetailScreen(
                runId = backStackEntry.arguments?.getString(Destinations.ARG_RUN_ID).orEmpty(),
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) }
            )
        }

        // --------------------- Gravação de percurso ------------------------
        composable(
            route = Destinations.TRACKING,
            arguments = listOf(
                navArgument(Destinations.ARG_RACE_ID) { type = NavType.StringType },
                navArgument(Destinations.ARG_MODE) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            TrackingScreen(
                raceId = backStackEntry.arguments?.getString(Destinations.ARG_RACE_ID).orEmpty(),
                mode = backStackEntry.arguments?.getString(Destinations.ARG_MODE).orEmpty(),
                snackbarHostState = snackbarHostState,
                onFinished = { navController.popBackStack() }
            )
        }

        // ---------------------------- Componente social --------------------
        composable(Destinations.FRIENDS) {
            FriendsScreen(snackbarHostState = snackbarHostState)
        }

        composable(Destinations.ATHLETES) {
            AthletesScreen(
                snackbarHostState = snackbarHostState,
                onOpenAthlete = { navController.navigate(Destinations.athleteDetailRoute(it)) }
            )
        }

        composable(
            route = Destinations.ATHLETE_DETAIL,
            arguments = listOf(
                navArgument(Destinations.ARG_ATHLETE_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            AthleteDetailScreen(
                athleteUid = backStackEntry.arguments
                    ?.getString(Destinations.ARG_ATHLETE_ID).orEmpty(),
                snackbarHostState = snackbarHostState,
                onOpenAlert = { navController.navigate(Destinations.alertDetailRoute(it)) }
            )
        }

        // ------------------------------ Restantes --------------------------
        composable(Destinations.PROFILE) {
            ProfileScreen(
                snackbarHostState = snackbarHostState,
                onOpenRun = { navController.navigate(Destinations.amateurRunDetailRoute(it)) },
                onOpenRace = { navController.navigate(Destinations.raceDetailRoute(it)) }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onOpenSystemStatus = { navController.navigate(Destinations.SENSORS) }
            )
        }

        composable(Destinations.SENSORS) { SensorsScreen() }

        composable(Destinations.ABOUT) { AboutScreen() }
    }
}
