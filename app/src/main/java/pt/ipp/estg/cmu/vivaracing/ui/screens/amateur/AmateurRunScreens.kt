package pt.ipp.estg.cmu.vivaracing.ui.screens.amateur

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.core.util.GeoUtils
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.ui.components.AmateurRunCard
import pt.ipp.estg.cmu.vivaracing.ui.components.EmptyState
import pt.ipp.estg.cmu.vivaracing.ui.components.LoadingBox
import pt.ipp.estg.cmu.vivaracing.ui.components.MapMarker
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.components.VivaRacingMap

/** ViewModel das listagens de participações amadoras. */
class AmateurRunsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    val runs: StateFlow<List<AmateurRun>> = container.amateurRunRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** ViewModel do detalhe de uma participação amadora. */
class AmateurRunDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _run = MutableStateFlow<AmateurRun?>(null)
    val run: StateFlow<AmateurRun?> = _run.asStateFlow()

    private var boundId: String? = null

    fun bind(runId: String) {
        if (boundId == runId) return
        boundId = runId
        viewModelScope.launch {
            container.amateurRunRepository.observeById(runId).collect { _run.value = it }
        }
    }
}

/**
 * Listagem das participações amadoras registadas pela comunidade.
 *
 * Os tempos aparecem com o nome do utilizador ou de forma anónima, consoante
 * a preferência definida por cada participante, tal como o enunciado exige.
 */
@Composable
fun AmateurRunListScreen(
    onOpenRun: (String) -> Unit,
    viewModel: AmateurRunsViewModel = viewModel()
) {
    val runs by viewModel.runs.collectAsState()

    if (runs.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_runs_title),
            description = stringResource(R.string.empty_runs_description),
            icon = Icons.Filled.EmojiEvents
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = runs, key = { it.id }) { run ->
            AmateurRunCard(run = run, onClick = { onOpenRun(run.id) })
        }
    }
}

/** Detalhe de uma participação amadora com o percurso efetivamente realizado. */
@Composable
fun AmateurRunDetailScreen(
    runId: String,
    onOpenRace: (String) -> Unit,
    viewModel: AmateurRunDetailViewModel = viewModel()
) {
    LaunchedEffect(runId) { viewModel.bind(runId) }

    val run by viewModel.run.collectAsState()
    val current = run
    val anonymousLabel = stringResource(R.string.anonymous_athlete)

    if (current == null) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = current.publicName(anonymousLabel),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = current.raceName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_time),
                value = Formatters.formatDuration(current.durationSeconds),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_distance),
                value = Formatters.formatDistance(current.distanceMeters),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_pace),
                value = Formatters.formatPace(current.distanceMeters, current.durationSeconds),
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_average_speed),
                value = Formatters.formatSpeed(current.averageSpeedKmh),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_steps),
                value = current.steps.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_date),
                value = Formatters.formatDate(current.endTime),
                modifier = Modifier.weight(1f)
            )
        }

        if (current.route.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                VivaRacingMap(
                    markers = listOfNotNull(
                        current.route.firstOrNull()?.let {
                            MapMarker("start", it, stringResource(R.string.marker_start))
                        },
                        current.route.lastOrNull()?.let {
                            MapMarker("finish", it, stringResource(R.string.marker_finish))
                        }
                    ),
                    route = current.route,
                    initialCenter = GeoUtils.centerOf(current.route),
                    initialZoom = GeoUtils.suggestedZoom(current.route)
                )
            }
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = { onOpenRace(current.raceId) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_open_race))
        }

        Spacer(Modifier.height(24.dp))
    }
}
