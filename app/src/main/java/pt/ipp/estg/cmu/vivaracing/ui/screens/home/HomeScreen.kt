package pt.ipp.estg.cmu.vivaracing.ui.screens.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.ui.components.AlertCard
import pt.ipp.estg.cmu.vivaracing.ui.components.RaceCard
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.navigation.Destinations

/** Resumo apresentado no ecrã inicial. */
data class HomeSummary(
    val totalRaces: Int = 0,
    val subscribedRaces: Int = 0,
    val recentAlerts: List<RaceAlert> = emptyList(),
    val upcomingRaces: List<Race> = emptyList(),
    val myRuns: List<AmateurRun> = emptyList()
)

/** ViewModel do ecrã inicial. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    val summary: StateFlow<HomeSummary> = combine(
        container.raceRepository.observeRaces(),
        container.raceRepository.observeSubscriptions(),
        container.alertRepository.observeAll(),
        container.amateurRunRepository.observeByUser(
            container.authRepository.currentUid.orEmpty()
        )
    ) { races, subscriptions, alerts, runs ->
        HomeSummary(
            totalRaces = races.size,
            subscribedRaces = subscriptions.size,
            recentAlerts = alerts.take(5),
            upcomingRaces = races
                .filter { it.status != RaceStatus.FINISHED }
                .sortedBy { it.startDateTime }
                .take(5),
            myRuns = runs.take(3)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())
}

/**
 * Ecrã inicial da aplicação.
 *
 * Funciona como painel de bordo: mostra indicadores agregados, as próximas
 * provas e a atividade mais recente da comunidade, com atalhos para as
 * listagens completas.
 */
@Composable
fun HomeScreen(
    onOpenRace: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = stringResource(R.string.label_races),
                    value = summary.totalRaces.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.label_subscriptions),
                    value = summary.subscribedRaces.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.label_my_runs),
                    value = summary.myRuns.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.home_welcome_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_welcome_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onOpenRoute(Destinations.MAP) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.screen_map))
                        }
                        OutlinedButton(
                            onClick = { onOpenRoute(Destinations.ALERT_LIST) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.screen_alerts))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.section_upcoming_races),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (summary.upcomingRaces.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.empty_races_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(items = summary.upcomingRaces, key = { "race_${it.id}" }) { race ->
                RaceCard(race = race, onClick = { onOpenRace(race.id) })
            }
        }

        item {
            Text(
                text = stringResource(R.string.section_recent_activity),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (summary.recentAlerts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.empty_alerts_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(items = summary.recentAlerts, key = { "alert_${it.id}" }) { alert ->
                AlertCard(alert = alert, onClick = { onOpenAlert(alert.id) })
            }
        }

        item {
            summary.myRuns.firstOrNull()?.let { run ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.home_last_run),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${run.raceName} • " +
                                Formatters.formatDuration(run.durationSeconds) + " • " +
                                Formatters.formatDistance(run.distanceMeters),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}
