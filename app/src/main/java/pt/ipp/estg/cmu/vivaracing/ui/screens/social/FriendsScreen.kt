@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState

/**
 * Ecrã da componente social.
 *
 * Reúne, em quatro separadores, a lista de amigos, a caixa de pedidos
 * recebidos, os pedidos enviados e a pesquisa no diretório público de
 * utilizadores. Os limites definidos pela aplicação, ou seja, o número
 * máximo de amigos e de pedidos pendentes, são apresentados de forma
 * explícita, para que o
 * utilizador perceba porque é que uma ação pode ser recusada.
 */
@Composable
fun FriendsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: FriendsViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val friends by viewModel.friends.collectAsState()
    val incoming by viewModel.incomingRequests.collectAsState()
    val outgoing by viewModel.outgoingRequests.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val query by viewModel.query.collectAsState()
    val friendCount by viewModel.friendCount.collectAsState()
    val message by viewModel.message.collectAsState()

    val messageText = socialMessage(message)
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.tab_friends)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    // O distintivo é sobreposto ao rótulo, e não colocado ao
                    // lado: numa linha de quatro separadores não há largura
                    // para os dois elementos, e o número acabava cortado.
                    BadgedBox(
                        badge = {
                            if (incoming.isNotEmpty()) {
                                Badge { Text(incoming.size.toString()) }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.tab_requests))
                    }
                }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.tab_sent)) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(stringResource(R.string.tab_search)) }
            )
        }

        when (selectedTab) {
            0 -> FriendsTab(
                friends = friends,
                friendCount = friendCount,
                maxFriends = viewModel.maxFriends,
                onRemove = viewModel::removeFriend
            )

            1 -> RequestsTab(
                requests = incoming,
                maxPending = viewModel.maxPendingRequests,
                primaryIcon = Icons.Filled.Check,
                onPrimary = viewModel::accept,
                onSecondary = viewModel::decline,
                emptyTitle = stringResource(R.string.empty_requests_title),
                emptyDescription = stringResource(R.string.empty_requests_description)
            )

            2 -> RequestsTab(
                requests = outgoing,
                maxPending = viewModel.maxPendingRequests,
                primaryIcon = null,
                onPrimary = {},
                onSecondary = viewModel::cancel,
                emptyTitle = stringResource(R.string.empty_sent_title),
                emptyDescription = stringResource(R.string.empty_sent_description)
            )

            else -> SearchTab(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                results = results,
                onSendRequest = { viewModel.sendRequest(it) }
            )
        }
    }
}

@Composable
private fun FriendsTab(
    friends: List<pt.ipp.estg.cmu.vivaracing.data.model.Friend>,
    friendCount: Int,
    maxFriends: Int,
    onRemove: (pt.ipp.estg.cmu.vivaracing.data.model.Friend) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CapacityHeader(
            label = stringResource(R.string.friends_capacity, friendCount, maxFriends),
            progress = if (maxFriends == 0) 0f else friendCount.toFloat() / maxFriends
        )

        if (friends.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_friends_title),
                description = stringResource(R.string.empty_friends_description),
                icon = Icons.Filled.Group
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = friends, key = { it.uid }) { friend ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(friend.username) },
                        supportingContent = {
                            Text(
                                friend.city.ifBlank {
                                    stringResource(R.string.unknown_location)
                                }
                            )
                        },
                        // Tudo o que diz respeito a seguir a pessoa enquanto
                        // atleta, ou seja, consultar o histórico de passagens
                        // e ligar ou desligar a subscrição, vive na secção de
                        // atletas. Aqui fica apenas a gestão da amizade.
                        trailingContent = {
                            IconButton(onClick = { onRemove(friend) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.action_remove_friend
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestsTab(
    requests: List<pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest>,
    maxPending: Int,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onPrimary: (pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest) -> Unit,
    onSecondary: (pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest) -> Unit,
    emptyTitle: String,
    emptyDescription: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CapacityHeader(
            label = stringResource(R.string.requests_capacity, requests.size, maxPending),
            progress = if (maxPending == 0) 0f else requests.size.toFloat() / maxPending
        )

        if (requests.isEmpty()) {
            EmptyState(
                title = emptyTitle,
                description = emptyDescription,
                icon = Icons.Filled.PersonAdd
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = requests, key = { it.uid }) { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(request.username) },
                        supportingContent = {
                            Text(Formatters.formatDateTime(request.sentAt))
                        },
                        trailingContent = {
                            Row {
                                if (primaryIcon != null) {
                                    IconButton(onClick = { onPrimary(request) }) {
                                        Icon(
                                            primaryIcon,
                                            contentDescription = stringResource(
                                                R.string.action_accept
                                            )
                                        )
                                    }
                                }
                                IconButton(onClick = { onSecondary(request) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_decline)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile>,
    onSendRequest: (pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.field_search_users)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        if (results.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_search_title),
                description = stringResource(R.string.empty_search_description),
                icon = Icons.Filled.Search
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = results, key = { it.uid }) { profile ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(profile.username) },
                        supportingContent = {
                            Text(
                                profile.city.ifBlank {
                                    stringResource(R.string.unknown_location)
                                }
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onSendRequest(profile) }) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = stringResource(
                                        R.string.action_send_friend_request
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CapacityHeader(label: String, progress: Float) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Converte os códigos internos de resultado social em texto traduzido. */
@Composable
fun socialMessage(code: String?): String? = when (code) {
    null -> null
    FriendsViewModel.MESSAGE_SUCCESS -> stringResource(R.string.social_success)
    FriendsViewModel.MESSAGE_ALREADY_FRIENDS -> stringResource(R.string.social_already_friends)
    FriendsViewModel.MESSAGE_REQUEST_SENT -> stringResource(R.string.social_request_sent)
    FriendsViewModel.MESSAGE_FRIEND_LIMIT -> stringResource(R.string.social_friend_limit)
    FriendsViewModel.MESSAGE_REQUEST_LIMIT -> stringResource(R.string.social_request_limit)
    FriendsViewModel.MESSAGE_ATHLETE_LIMIT -> stringResource(R.string.social_athlete_limit)
    FriendsViewModel.MESSAGE_GUEST -> stringResource(R.string.social_guest)
    FriendsViewModel.MESSAGE_FAILURE -> stringResource(R.string.error_generic)
    else -> code
}
