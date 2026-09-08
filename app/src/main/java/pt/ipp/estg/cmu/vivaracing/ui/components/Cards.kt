@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.core.util.Formatters
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType
import pt.ipp.estg.cmu.vivaracing.ui.theme.PendingSync
import pt.ipp.estg.cmu.vivaracing.ui.theme.StatusFinished
import pt.ipp.estg.cmu.vivaracing.ui.theme.StatusOngoing
import pt.ipp.estg.cmu.vivaracing.ui.theme.StatusScheduled

/** Ícone associado ao tipo de prova. */
fun RaceType.icon(): ImageVector = when (this) {
    RaceType.CYCLING -> Icons.Filled.DirectionsBike
    RaceType.TRAIL -> Icons.Filled.Terrain
    else -> Icons.Filled.DirectionsRun
}

/** Designação legível do tipo de prova, traduzida para o idioma ativo. */
@Composable
fun RaceType.label(): String = when (this) {
    RaceType.RUNNING -> stringResource(R.string.race_type_running)
    RaceType.MARATHON -> stringResource(R.string.race_type_marathon)
    RaceType.TRAIL -> stringResource(R.string.race_type_trail)
    RaceType.CYCLING -> stringResource(R.string.race_type_cycling)
    RaceType.OTHER -> stringResource(R.string.race_type_other)
}

@Composable
fun RaceStatus.label(): String = when (this) {
    RaceStatus.SCHEDULED -> stringResource(R.string.race_status_scheduled)
    RaceStatus.ONGOING -> stringResource(R.string.race_status_ongoing)
    RaceStatus.FINISHED -> stringResource(R.string.race_status_finished)
}

@Composable
fun AlertType.label(): String = when (this) {
    AlertType.RACE_START -> stringResource(R.string.alert_type_start)
    AlertType.ATHLETE_PASSING -> stringResource(R.string.alert_type_passing)
    AlertType.RACE_FINISH -> stringResource(R.string.alert_type_finish)
    AlertType.INCIDENT -> stringResource(R.string.alert_type_incident)
}

fun AlertType.icon(): ImageVector = when (this) {
    AlertType.RACE_START -> Icons.Filled.Flag
    AlertType.ATHLETE_PASSING -> Icons.Filled.NotificationsActive
    AlertType.RACE_FINISH -> Icons.Filled.Timer
    AlertType.INCIDENT -> Icons.Filled.Warning
}

/** Cartão de uma prova apresentado nas listagens. */
@Composable
fun RaceCard(
    race: Race,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = race.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = race.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = race.city.ifBlank { stringResource(R.string.unknown_location) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (race.pendingSync) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = stringResource(R.string.pending_sync),
                        tint = PendingSync
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(race.status.label()) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = when (race.status) {
                            RaceStatus.SCHEDULED -> StatusScheduled
                            RaceStatus.ONGOING -> StatusOngoing
                            RaceStatus.FINISHED -> StatusFinished
                        }
                    )
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text(race.type.label()) }
                )
            }

            Spacer(Modifier.height(8.dp))

            InfoLine(
                icon = Icons.Filled.Schedule,
                text = Formatters.formatDateTime(race.startDateTime)
            )
            if (race.distanceMeters > 0) {
                InfoLine(
                    icon = Icons.Filled.LocationOn,
                    text = Formatters.formatDistance(race.distanceMeters)
                )
            }
            InfoLine(
                icon = Icons.Filled.Person,
                text = race.authorName.ifBlank { stringResource(R.string.unknown_author) }
            )
        }
    }
}

/** Cartão de um alerta publicado durante uma prova. */
@Composable
fun AlertCard(
    alert: RaceAlert,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = alert.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.type.label() + (
                        alert.athleteBib?.let { " • ${stringResource(R.string.bib_short)} $it" } ?: ""
                        ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = alert.raceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alert.message.isNotBlank()) {
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = Formatters.formatDateTime(alert.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (alert.pendingSync) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.pending_sync),
                    tint = PendingSync
                )
            }
        }
    }
}

/** Cartão de uma participação amadora. */
@Composable
fun AmateurRunCard(
    run: AmateurRun,
    modifier: Modifier = Modifier,
    position: Int? = null,
    onClick: () -> Unit
) {
    val anonymousLabel = stringResource(R.string.anonymous_athlete)
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (position != null) {
                Text(
                    text = "$position.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.publicName(anonymousLabel),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = run.raceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${Formatters.formatDuration(run.durationSeconds)} • " +
                        Formatters.formatDistance(run.distanceMeters) + " • " +
                        Formatters.formatPace(run.distanceMeters, run.durationSeconds),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (run.pendingSync) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.pending_sync),
                    tint = PendingSync
                )
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
