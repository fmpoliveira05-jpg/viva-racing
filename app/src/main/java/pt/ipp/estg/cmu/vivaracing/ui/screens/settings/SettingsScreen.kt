@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.ipp.estg.cmu.vivaracing.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import pt.ipp.estg.cmu.vivaracing.R
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.core.preferences.AppPreferences
import pt.ipp.estg.cmu.vivaracing.core.preferences.ThemeMode
import pt.ipp.estg.cmu.vivaracing.core.preferences.UserPreferences
import pt.ipp.estg.cmu.vivaracing.core.system.LocaleManager

/** ViewModel do ecrã de definições. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: UserPreferences =
        (application as VivaRacingApp).container.preferences

    val settings: StateFlow<AppPreferences> = preferences.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.snapshot())

    fun setLanguage(tag: String) {
        preferences.setLanguage(tag)
        LocaleManager.apply(tag)
    }

    fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)
    fun setAnonymous(value: Boolean) = preferences.setAnonymousResults(value)
    fun setNotifications(value: Boolean) = preferences.setNotificationsEnabled(value)
    fun setOnlySubscribed(value: Boolean) = preferences.setNotifyOnlySubscribed(value)
    fun setOnlyFriends(value: Boolean) = preferences.setNotifyOnlyFriends(value)
    fun setAthletePositions(value: Boolean) = preferences.setNotifyAthletePositions(value)
    fun setFriendActivity(value: Boolean) = preferences.setNotifyFriendActivity(value)
    fun setHighAccuracy(value: Boolean) = preferences.setHighAccuracyTracking(value)
}

/**
 * Ecrã de definições da aplicação.
 *
 * Todas as opções são persistidas em `SharedPreferences` e observadas por um
 * `Flow`, pelo que qualquer alteração se reflete de imediato nos restantes
 * ecrãs sem necessidade de reiniciar a aplicação.
 */
@Composable
fun SettingsScreen(
    onOpenSystemStatus: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.section_appearance),
            style = MaterialTheme.typography.titleMedium
        )

        LanguageSelector(
            current = settings.language,
            onSelected = viewModel::setLanguage
        )

        ThemeSelector(
            current = settings.themeMode,
            onSelected = viewModel::setThemeMode
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_privacy),
            style = MaterialTheme.typography.titleMedium
        )

        SettingSwitch(
            title = stringResource(R.string.setting_anonymous_title),
            description = stringResource(R.string.setting_anonymous_description),
            checked = settings.anonymousResults,
            onCheckedChange = viewModel::setAnonymous
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_notifications),
            style = MaterialTheme.typography.titleMedium
        )

        SettingSwitch(
            title = stringResource(R.string.setting_notifications_title),
            description = stringResource(R.string.setting_notifications_description),
            checked = settings.notificationsEnabled,
            onCheckedChange = viewModel::setNotifications
        )

        SettingSwitch(
            title = stringResource(R.string.setting_only_subscribed_title),
            description = stringResource(R.string.setting_only_subscribed_description),
            checked = settings.notifyOnlySubscribed,
            onCheckedChange = viewModel::setOnlySubscribed
        )

        SettingSwitch(
            title = stringResource(R.string.setting_only_friends_title),
            description = stringResource(R.string.setting_only_friends_description),
            checked = settings.notifyOnlyFriends,
            onCheckedChange = viewModel::setOnlyFriends
        )

        SettingSwitch(
            title = stringResource(R.string.setting_friend_activity_title),
            description = stringResource(R.string.setting_friend_activity_description),
            checked = settings.notifyFriendActivity,
            onCheckedChange = viewModel::setFriendActivity
        )

        SettingSwitch(
            title = stringResource(R.string.setting_athlete_positions_title),
            description = stringResource(R.string.setting_athlete_positions_description),
            checked = settings.notifyAthletePositions,
            onCheckedChange = viewModel::setAthletePositions
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_energy),
            style = MaterialTheme.typography.titleMedium
        )

        SettingSwitch(
            title = stringResource(R.string.setting_high_accuracy_title),
            description = stringResource(R.string.setting_high_accuracy_description),
            checked = settings.highAccuracyTracking,
            onCheckedChange = viewModel::setHighAccuracy
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_power_saving_title))
                },
                supportingContent = {
                    Text(
                        if (settings.powerSavingEnabled) {
                            stringResource(R.string.setting_power_saving_active)
                        } else {
                            stringResource(R.string.setting_power_saving_inactive)
                        }
                    )
                }
            )
        }

        // O estado do dispositivo e as leituras dos sensores deixaram de ter
        // entrada própria no menu lateral: são informação de diagnóstico da
        // secção de energia, e não uma área funcional da aplicação.
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.screen_system)) },
                supportingContent = {
                    Text(stringResource(R.string.setting_system_status_description))
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenSystemStatus)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Seletor de idioma. Reproduz a estrutura demonstrada nas aulas com o
 * `ExposedDropdownMenuBox`, aplicando a escolha através da API de idiomas por
 * aplicação do AppCompat.
 */
@Composable
private fun LanguageSelector(current: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val labels = mapOf(
        AppPreferences.LANGUAGE_SYSTEM to stringResource(R.string.language_system),
        "pt" to stringResource(R.string.language_portuguese),
        "en" to stringResource(R.string.language_english),
        "es" to stringResource(R.string.language_spanish)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = labels[current] ?: labels.getValue(AppPreferences.LANGUAGE_SYSTEM),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.setting_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (tag, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(tag)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(current: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
        ThemeMode.AUTO_LIGHT_SENSOR to stringResource(R.string.theme_auto_sensor)
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = labels.getValue(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.setting_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(description) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        )
    }
}
