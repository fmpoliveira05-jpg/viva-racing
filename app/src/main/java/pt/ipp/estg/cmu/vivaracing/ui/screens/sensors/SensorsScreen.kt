package pt.ipp.estg.cmu.vivaracing.ui.screens.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.system.TrackingQuality
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile
import pt.ipp.estg.cmu.vivaracing.ui.components.rememberSensorReadings
import java.util.Locale

/**
 * Ecrã de diagnóstico dos sensores.
 *
 * Mostra em tempo real as leituras recolhidas pelo serviço ligado de sensores
 * e explica como cada uma delas é aproveitada pela aplicação. Serve também
 * para demonstrar, na defesa do trabalho, o funcionamento dos sensores que não
 * dependem da localização nem do GPS.
 */
@Composable
fun SensorsScreen() {
    val readings by rememberSensorReadings()
    val context = LocalContext.current
    val container = (context.applicationContext as VivaRacingApp).container
    val initialSystemState = remember { container.systemStateMonitor.snapshot() }
    val systemState by container.systemStateMonitor.observe()
        .collectAsState(initial = initialSystemState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.sensors_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.sensor_light),
                value = String.format(Locale.US, "%.0f lx", readings.lightLux),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.sensor_acceleration),
                value = String.format(Locale.US, "%.2f m/s²", readings.accelerationMagnitude),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.sensor_proximity),
                value = String.format(Locale.US, "%.0f cm", readings.proximityCentimeters),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_system_state),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.system_state_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.system_battery),
                value = "${systemState.batteryPercent}%",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.system_thermal),
                value = thermalLabel(systemState.thermalStatus),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.system_profile),
                value = qualityLabel(systemState.trackingQuality),
                modifier = Modifier.weight(1f)
            )
        }

        SystemRow(
            title = stringResource(R.string.system_charging),
            value = booleanLabel(systemState.isCharging),
            description = stringResource(R.string.system_charging_usage)
        )
        SystemRow(
            title = stringResource(R.string.system_power_save),
            value = booleanLabel(systemState.powerSaveMode),
            description = stringResource(R.string.system_power_save_usage)
        )
        SystemRow(
            title = stringResource(R.string.system_metered),
            value = booleanLabel(systemState.isMeteredNetwork),
            description = stringResource(R.string.system_metered_usage)
        )
        SystemRow(
            title = stringResource(R.string.system_data_saver),
            value = booleanLabel(systemState.dataSaverEnabled),
            description = stringResource(R.string.system_data_saver_usage)
        )
        SystemRow(
            title = stringResource(R.string.system_background_restricted),
            value = booleanLabel(systemState.backgroundRestricted),
            description = stringResource(R.string.system_background_restricted_usage)
        )
        SystemRow(
            title = stringResource(R.string.system_battery_optimizations),
            value = booleanLabel(!systemState.ignoringBatteryOptimizations),
            description = stringResource(R.string.system_battery_optimizations_usage)
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_sensors),
            style = MaterialTheme.typography.titleMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sensor_light)) },
                supportingContent = { Text(stringResource(R.string.sensor_light_usage)) },
                trailingContent = {
                    Text(
                        if (readings.lightAvailable) {
                            stringResource(R.string.sensor_available)
                        } else {
                            stringResource(R.string.sensor_unavailable)
                        }
                    )
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sensor_acceleration)) },
                supportingContent = { Text(stringResource(R.string.sensor_acceleration_usage)) },
                trailingContent = {
                    Text(
                        if (readings.movementDetected) {
                            stringResource(R.string.sensor_moving)
                        } else {
                            stringResource(R.string.sensor_still)
                        }
                    )
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sensor_steps)) },
                supportingContent = { Text(stringResource(R.string.sensor_steps_usage)) },
                trailingContent = {
                    Text(
                        if (readings.stepCounterAvailable) {
                            stringResource(R.string.sensor_available)
                        } else {
                            stringResource(R.string.sensor_unavailable)
                        }
                    )
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sensor_light_theme_title)) },
                supportingContent = {
                    Text(
                        if (readings.isLowLight) {
                            stringResource(R.string.sensor_low_light_yes)
                        } else {
                            stringResource(R.string.sensor_low_light_no)
                        }
                    )
                }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SystemRow(title: String, value: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = { Text(value) }
        )
    }
}

@Composable
private fun booleanLabel(value: Boolean): String = if (value) {
    stringResource(R.string.system_yes)
} else {
    stringResource(R.string.system_no)
}

@Composable
private fun thermalLabel(status: Int): String = when {
    status >= 4 -> stringResource(R.string.system_thermal_critical)
    status == 3 -> stringResource(R.string.system_thermal_severe)
    status == 2 -> stringResource(R.string.system_thermal_moderate)
    status == 1 -> stringResource(R.string.system_thermal_light)
    else -> stringResource(R.string.system_thermal_none)
}

@Composable
private fun qualityLabel(quality: TrackingQuality): String = when (quality) {
    TrackingQuality.HIGH -> stringResource(R.string.system_profile_high)
    TrackingQuality.BALANCED -> stringResource(R.string.system_profile_balanced)
    TrackingQuality.LOW -> stringResource(R.string.system_profile_low)
}

