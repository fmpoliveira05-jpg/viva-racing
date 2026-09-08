package pt.ipp.estg.cmu.vivaracing.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.data.AppContainer
import pt.ipp.estg.cmu.vivaracing.data.repository.SessionState

/**
 * Entrada do menu lateral.
 *
 * @param registeredOnly indica que a entrada só faz sentido para utilizadores
 * com conta, sendo ocultada em modo convidado.
 */
private data class DrawerEntry(
    val route: String,
    val labelResource: Int,
    val icon: ImageVector,
    val registeredOnly: Boolean = false
)

private val drawerEntries = listOf(
    DrawerEntry(Destinations.HOME, R.string.screen_home, Icons.Filled.Home),
    DrawerEntry(Destinations.RACE_LIST, R.string.screen_races, Icons.Filled.DirectionsRun),
    DrawerEntry(Destinations.ALERT_LIST, R.string.screen_alerts, Icons.Filled.NotificationsActive),
    DrawerEntry(Destinations.AMATEUR_LIST, R.string.screen_amateur, Icons.Filled.EmojiEvents),
    DrawerEntry(Destinations.MAP, R.string.screen_map, Icons.Filled.Map),
    DrawerEntry(Destinations.ATHLETES, R.string.screen_athletes, Icons.Filled.Visibility),
    DrawerEntry(Destinations.FRIENDS, R.string.screen_friends, Icons.Filled.Group, true),
    DrawerEntry(Destinations.PROFILE, R.string.screen_profile, Icons.Filled.Person, true),
    DrawerEntry(Destinations.SETTINGS, R.string.screen_settings, Icons.Filled.Settings),
    DrawerEntry(Destinations.ABOUT, R.string.screen_about, Icons.Filled.Info)
)

/**
 * Menu lateral da aplicação.
 *
 * Dá acesso às duas representações exigidas pelo enunciado para cada
 * listagem, a lista e o mapa, e adapta-se ao tipo de sessão: em modo
 * convidado são
 * ocultadas as áreas que exigem conta, uma vez que o visitante apenas consulta
 * conteúdo público.
 */
@Composable
fun AppDrawer(
    container: AppContainer,
    session: SessionState,
    currentRoute: String?,
    pendingRequests: Int,
    onDestinationClicked: (String) -> Unit,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            DrawerHeader(
                username = if (session.isGuest) {
                    stringResource(R.string.guest_user)
                } else {
                    container.authRepository.currentDisplayName
                        .ifBlank { stringResource(R.string.unknown_author) }
                }
            )

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            drawerEntries
                .filter { !it.registeredOnly || session.isRegistered }
                .forEach { entry ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(entry.labelResource)) },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        badge = {
                            if (entry.route == Destinations.FRIENDS && pendingRequests > 0) {
                                Badge { Text(pendingRequests.toString()) }
                            }
                        },
                        selected = currentRoute == entry.route,
                        onClick = { onDestinationClicked(entry.route) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                label = {
                    Text(
                        if (session.isGuest) {
                            stringResource(R.string.action_exit_guest)
                        } else {
                            stringResource(R.string.action_sign_out)
                        }
                    )
                },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                selected = false,
                onClick = onSignOut,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

/**
 * Cabeçalho do menu: apenas o símbolo da aplicação e o nome de utilizador.
 * O endereço de correio eletrónico ficava permanentemente a vista de quem
 * estivesse perto do ecrã sem que isso trouxesse qualquer utilidade; quem
 * precisar de o consultar encontra-o no perfil.
 */
@Composable
private fun DrawerHeader(username: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(text = username, style = MaterialTheme.typography.titleMedium)
    }
}
