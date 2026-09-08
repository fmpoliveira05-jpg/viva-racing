@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.data.AppContainer
import pt.ipp.estg.cmu.vivaracing.data.repository.SessionState
import pt.ipp.estg.cmu.vivaracing.ui.components.GuestBanner
import pt.ipp.estg.cmu.vivaracing.ui.components.LocalSession
import pt.ipp.estg.cmu.vivaracing.ui.components.OfflineBanner
import pt.ipp.estg.cmu.vivaracing.ui.screens.auth.AuthNavHost

/**
 * Ponto de entrada da interface.
 *
 * Enquanto não existir sessão apresenta o grafo de autenticação. A partir do
 * momento em que o Firebase confirma um utilizador, registado ou anónimo,
 * comuta para a estrutura principal, disponibilizando o estado da sessão a
 * toda a árvore de composição através de [LocalSession].
 */
@Composable
fun VivaRacingRoot(
    container: AppContainer,
    deepLinkRoute: String? = null
) {
    val session by container.authRepository.observeSession()
        .collectAsState(
            initial = SessionState(
                uid = container.authService.currentUid,
                isGuest = container.authService.isGuest
            )
        )

    if (!session.isAuthenticated) {
        AuthNavHost()
    } else {
        CompositionLocalProvider(LocalSession provides session) {
            MainShell(
                container = container,
                session = session,
                deepLinkRoute = deepLinkRoute
            )
        }
    }
}

/**
 * Estrutura principal da aplicação: a combinação `Navigation Drawer` +
 * `Scaffold` + `Navigation` recomendada nos materiais da unidade curricular.
 */
@Composable
private fun MainShell(
    container: AppContainer,
    session: SessionState,
    deepLinkRoute: String?,
    navController: NavHostController = rememberNavController()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isOnline by container.connectivityObserver.observe().collectAsState(initial = true)
    val pendingRequests by container.socialRepository.observeIncomingRequestCount()
        .collectAsState(initial = 0)

    LaunchedEffect(deepLinkRoute) {
        if (!deepLinkRoute.isNullOrBlank()) {
            navController.navigate(deepLinkRoute)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                container = container,
                session = session,
                currentRoute = currentRoute,
                pendingRequests = pendingRequests,
                onDestinationClicked = { route ->
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    coroutineScope.launch {
                        drawerState.close()
                        container.authRepository.signOut()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = screenTitle(currentRoute)) },
                    navigationIcon = {
                        if (isTopLevelRoute(currentRoute)) {
                            IconButton(onClick = {
                                coroutineScope.launch { drawerState.open() }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.open_menu)
                                )
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                // O convidado não cria conteúdo, pelo que o botão de nova
                // prova não lhe é apresentado.
                val canCreate = session.isRegistered &&
                    (currentRoute == Destinations.RACE_LIST || currentRoute == Destinations.HOME)
                if (canCreate) {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(Destinations.RACE_FORM) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_new_race)) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                VivaRacingNavHost(
                    navController = navController,
                    container = container,
                    snackbarHostState = snackbarHostState
                )
                Column {
                    if (session.isGuest) GuestBanner()
                    if (!isOnline) OfflineBanner()
                }
            }
        }
    }
}

/** Rotas de topo mostram o ícone de menu; as restantes mostram o de retroceder. */
private fun isTopLevelRoute(route: String?): Boolean = route in setOf(
    Destinations.HOME,
    Destinations.RACE_LIST,
    Destinations.ALERT_LIST,
    Destinations.AMATEUR_LIST,
    Destinations.MAP,
    Destinations.FRIENDS,
    Destinations.ATHLETES,
    Destinations.PROFILE,
    Destinations.SETTINGS,
    Destinations.ABOUT
)

@Composable
private fun screenTitle(route: String?): String = when (route) {
    Destinations.HOME -> stringResource(R.string.screen_home)
    Destinations.RACE_LIST -> stringResource(R.string.screen_races)
    Destinations.ALERT_LIST -> stringResource(R.string.screen_alerts)
    Destinations.AMATEUR_LIST -> stringResource(R.string.screen_amateur)
    Destinations.MAP -> stringResource(R.string.screen_map)
    Destinations.FRIENDS -> stringResource(R.string.screen_friends)
    Destinations.ATHLETES -> stringResource(R.string.screen_athletes)
    Destinations.ATHLETE_DETAIL -> stringResource(R.string.screen_athlete_detail)
    Destinations.PROFILE -> stringResource(R.string.screen_profile)
    Destinations.SETTINGS -> stringResource(R.string.screen_settings)
    Destinations.SENSORS -> stringResource(R.string.screen_system)
    Destinations.ABOUT -> stringResource(R.string.screen_about)
    Destinations.RACE_FORM -> stringResource(R.string.screen_new_race)
    Destinations.RACE_DETAIL -> stringResource(R.string.screen_race_detail)
    Destinations.ALERT_DETAIL -> stringResource(R.string.screen_alert_detail)
    Destinations.ALERT_FORM -> stringResource(R.string.screen_new_alert)
    Destinations.AMATEUR_DETAIL -> stringResource(R.string.screen_amateur_detail)
    Destinations.TRACKING -> stringResource(R.string.screen_tracking)
    else -> stringResource(R.string.app_name)
}
