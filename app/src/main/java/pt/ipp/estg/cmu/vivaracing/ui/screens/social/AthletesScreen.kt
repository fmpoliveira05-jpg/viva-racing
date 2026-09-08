package pt.ipp.estg.cmu.vivaracing.ui.screens.social

import android.app.Application
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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.SocialLimits
import pt.ipp.estg.cmu.vivaracing.data.repository.SocialResult
import pt.ipp.estg.cmu.vivaracing.ui.components.AlertCard
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState
import pt.ipp.estg.cmu.vivaracing.ui.components.GuestNotice
import pt.ipp.estg.cmu.vivaracing.ui.components.LocalSession
import pt.ipp.estg.cmu.vivaracing.ui.components.rememberGuestExitAction

/**
 * Linha do diretório de atletas.
 *
 * Um atleta é, para efeitos da aplicação, qualquer utilizador registado: o
 * diretório é alimentado pela coleção publica de perfis, e não pela lista de
 * subscrições, para que seja possível encontrar e seguir alguém que ainda não
 * se segue.
 */
data class AthleteRow(
    val profile: PublicProfile,
    val isSubscribed: Boolean,
    val passages: Int
)

/**
 * ViewModel do diretório de atletas.
 *
 * Segue a mesma estrutura do ecrã de provas: uma consulta de texto sobre a
 * cache local, combinada com o estado de subscrição e com a contagem de
 * passagens já registadas para cada pessoa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AthletesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container
    private val social = container.socialRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val currentUid: String? get() = container.authRepository.currentUid

    /** Apenas os atletas subscritos, usado no cabeçalho de capacidade. */
    val subscribedCount: StateFlow<Int> = social.observeAthleteSubscriptions()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val athletes: StateFlow<List<AthleteRow>> =
        _query
            .flatMapLatest { text -> social.searchProfiles(text) }
            .combine(social.observeAthleteSubscriptions()) { profiles, subscriptions ->
                val subscribed = subscriptions.map { it.athleteUid }.toSet()
                profiles
                    .filter { it.uid != currentUid }
                    .map { profile ->
                        AthleteRow(
                            profile = profile,
                            isSubscribed = subscribed.contains(profile.uid),
                            passages = 0
                        )
                    }
            }
            .combine(container.alertRepository.observeAll()) { rows, alerts ->
                // Só contam as passagens registadas em provas que o próprio
                // atleta subscreveu: é esse o conjunto que constitui o seu
                // histórico público.
                rows.map { row ->
                    val ownRaces = row.profile.raceIds.toSet()
                    row.copy(
                        passages = alerts.count {
                            it.athleteUserId == row.profile.uid && it.raceId in ownRaces
                        }
                    )
                }
                    .sortedWith(
                        compareByDescending<AthleteRow> { it.isSubscribed }
                            .thenByDescending { it.passages }
                            .thenBy { it.profile.username.lowercase() }
                    )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val maxAthletes: Int = SocialLimits.MAX_ATHLETE_SUBSCRIPTIONS

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun toggleSubscription(row: AthleteRow) {
        viewModelScope.launch {
            val result = if (row.isSubscribed) {
                social.unsubscribeAthlete(row.profile.uid)
            } else {
                social.subscribeAthlete(row.profile, bibNumber = "")
            }
            _message.value = messageKey(result)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun messageKey(result: SocialResult): String = when (result) {
        SocialResult.Success -> FriendsViewModel.MESSAGE_SUCCESS
        SocialResult.AthleteLimitReached -> FriendsViewModel.MESSAGE_ATHLETE_LIMIT
        SocialResult.GuestNotAllowed -> FriendsViewModel.MESSAGE_GUEST
        is SocialResult.Failure -> result.message ?: FriendsViewModel.MESSAGE_FAILURE
        else -> FriendsViewModel.MESSAGE_FAILURE
    }
}

/**
 * ViewModel do detalhe de um atleta.
 *
 * Ao contrário da versão anterior, funciona para qualquer utilizador
 * registado, subscrito ou não, porque lê o perfil do diretório público em
 * vez de depender da lista de subscrições.
 */
class AthleteDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container
    private val social = container.socialRepository

    private val _alerts = MutableStateFlow<List<RaceAlert>>(emptyList())
    val alerts: StateFlow<List<RaceAlert>> = _alerts.asStateFlow()

    private val _profile = MutableStateFlow<PublicProfile?>(null)
    val profile: StateFlow<PublicProfile?> = _profile.asStateFlow()

    private val _subscribed = MutableStateFlow(false)
    val subscribed: StateFlow<Boolean> = _subscribed.asStateFlow()

    private val _bibNumber = MutableStateFlow("")
    val bibNumber: StateFlow<String> = _bibNumber.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _canViewHistory = MutableStateFlow(false)
    val canViewHistory: StateFlow<Boolean> = _canViewHistory.asStateFlow()

    private var boundId: String? = null

    /**
     * O histórico de passagens não é informação aberta.
     *
     * Só fica visível a quem segue o atleta ou a quem partilha com ele pelo
     * menos uma prova subscrita, situação em que ambos estão a acompanhar o
     * mesmo evento e faz sentido ver as passagens um do outro. Mesmo assim,
     * limita-se aos alertas de provas que o próprio atleta subscreveu: uma
     * passagem registada numa prova que ele não acompanha não lhe pertence.
     */
    fun bind(athleteUid: String) {
        if (boundId == athleteUid) return
        boundId = athleteUid

        viewModelScope.launch {
            combine(
                container.alertRepository.observeByAthlete(athleteUid),
                social.observePublicProfile(athleteUid),
                social.observeIsAthleteSubscribed(athleteUid),
                container.raceRepository.observeSubscriptions()
            ) { alerts, profile, following, mySubscriptions ->
                val athleteRaces = profile?.raceIds.orEmpty().toSet()
                val myRaces = mySubscriptions.map { it.raceId }.toSet()
                val sharesRace = athleteRaces.any { myRaces.contains(it) }
                val allowed = following || sharesRace
                HistoryState(
                    profile = profile,
                    canView = allowed,
                    alerts = if (allowed) {
                        alerts.filter { athleteRaces.contains(it.raceId) }
                    } else {
                        emptyList()
                    }
                )
            }.collect { state ->
                _profile.value = state.profile
                _canViewHistory.value = state.canView
                _alerts.value = state.alerts
            }
        }
        viewModelScope.launch {
            social.observeIsAthleteSubscribed(athleteUid).collect { _subscribed.value = it }
        }
        viewModelScope.launch {
            social.observeAthleteSubscriptions().collect { list ->
                _bibNumber.value = list.firstOrNull { it.athleteUid == athleteUid }
                    ?.bibNumber.orEmpty()
            }
        }
    }

    private data class HistoryState(
        val profile: PublicProfile?,
        val canView: Boolean,
        val alerts: List<RaceAlert>
    )

    fun toggleSubscription() {
        val uid = boundId ?: return
        viewModelScope.launch {
            val result = if (_subscribed.value) {
                social.unsubscribeAthlete(uid)
            } else {
                val profile = _profile.value ?: PublicProfile(uid = uid)
                social.subscribeAthlete(profile, bibNumber = _bibNumber.value)
            }
            _message.value = when (result) {
                SocialResult.Success -> FriendsViewModel.MESSAGE_SUCCESS
                SocialResult.AthleteLimitReached -> FriendsViewModel.MESSAGE_ATHLETE_LIMIT
                SocialResult.GuestNotAllowed -> FriendsViewModel.MESSAGE_GUEST
                is SocialResult.Failure -> result.message ?: FriendsViewModel.MESSAGE_FAILURE
                else -> FriendsViewModel.MESSAGE_FAILURE
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/**
 * Diretório de atletas.
 *
 * Espelha a organização do ecrã de provas: pesquisa no topo, lista em baixo e
 * um ecrã de detalhe comum. Subscrever um atleta é diferente de subscrever
 * uma prova, uma vez que passa a receber avisos das posições dessa pessoa
 * em qualquer prova, e não apenas numa.
 */
@Composable
fun AthletesScreen(
    snackbarHostState: SnackbarHostState,
    onOpenAthlete: (String) -> Unit,
    viewModel: AthletesViewModel = viewModel()
) {
    val session = LocalSession.current
    val onCreateAccount = rememberGuestExitAction()

    val query by viewModel.query.collectAsState()
    val athletes by viewModel.athletes.collectAsState()
    val subscribed by viewModel.subscribedCount.collectAsState()
    val message by viewModel.message.collectAsState()

    val feedback = message?.let { socialMessage(it) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (session.isGuest) {
            GuestNotice(onCreateAccount = onCreateAccount)
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.field_search_athletes)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (session.isRegistered) {
            Text(
                text = stringResource(
                    R.string.athletes_capacity,
                    subscribed,
                    viewModel.maxAthletes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (athletes.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_athletes_title),
                description = stringResource(R.string.empty_athletes_description),
                icon = Icons.Filled.DirectionsRun
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = athletes, key = { it.profile.uid }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(row.profile.username) },
                        supportingContent = {
                            Column {
                                Text(
                                    row.profile.city.ifBlank {
                                        stringResource(R.string.unknown_location)
                                    }
                                )
                                Text(
                                    stringResource(R.string.athlete_passage_count, row.passages),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                // O olho abre o detalhe do atleta, onde fica o
                                // histórico de passagens; o segundo ícone liga
                                // ou desliga a subscrição sem sair da lista.
                                IconButton(onClick = { onOpenAthlete(row.profile.uid) }) {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = stringResource(
                                            R.string.action_view_athlete_alerts
                                        )
                                    )
                                }
                                if (session.isRegistered) {
                                    IconButton(
                                        onClick = { viewModel.toggleSubscription(row) }
                                    ) {
                                        Icon(
                                            imageVector = if (row.isSubscribed) {
                                                Icons.Filled.PersonRemove
                                            } else {
                                                Icons.Filled.PersonAdd
                                            },
                                            contentDescription = stringResource(
                                                if (row.isSubscribed) {
                                                    R.string.action_unfollow_athlete
                                                } else {
                                                    R.string.action_follow_athlete
                                                }
                                            ),
                                            tint = if (row.isSubscribed) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                LocalContentColor.current
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Detalhe de um atleta, com o histórico das suas passagens.
 *
 * Cada passagem corresponde a um alerta publicado por um observador que o
 * identificou no percurso, pelo que a lista reconstitui a progressão dessa
 * pessoa ao longo das provas em que participou.
 */
@Composable
fun AthleteDetailScreen(
    athleteUid: String,
    snackbarHostState: SnackbarHostState,
    onOpenAlert: (String) -> Unit,
    viewModel: AthleteDetailViewModel = viewModel()
) {
    LaunchedEffect(athleteUid) { viewModel.bind(athleteUid) }

    val session = LocalSession.current
    val alerts by viewModel.alerts.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val bibNumber by viewModel.bibNumber.collectAsState()
    val canViewHistory by viewModel.canViewHistory.collectAsState()
    val message by viewModel.message.collectAsState()

    val feedback = message?.let { socialMessage(it) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = profile?.username ?: stringResource(R.string.unknown_author),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = profile?.city?.ifBlank { null }
                        ?: stringResource(R.string.unknown_location),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bibNumber.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.athlete_bib, bibNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (session.isRegistered) {
                    AssistChip(
                        onClick = { viewModel.toggleSubscription() },
                        label = {
                            Text(
                                if (subscribed) {
                                    stringResource(R.string.action_unfollow_athlete)
                                } else {
                                    stringResource(R.string.action_follow_athlete)
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (subscribed) {
                                    Icons.Filled.PersonRemove
                                } else {
                                    Icons.Filled.PersonAdd
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.section_athlete_passages),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // O histórico só é revelado a quem segue o atleta ou a quem acompanha
        // a mesma prova. Fora disso, a aplicação explica o que falta fazer em
        // vez de apresentar uma lista vazia sem justificação.
        if (!canViewHistory) {
            EmptyState(
                title = stringResource(R.string.athlete_history_locked_title),
                description = stringResource(R.string.athlete_history_locked_description),
                icon = Icons.Filled.Lock
            )
            return@Column
        }

        if (alerts.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_alerts_title),
                description = stringResource(R.string.empty_athlete_alerts_description),
                icon = Icons.Filled.NotificationsActive
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = alerts, key = { it.id }) { alert ->
                AlertCard(alert = alert, onClick = { onOpenAlert(alert.id) })
            }
        }
    }
}
