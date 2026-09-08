package pt.ipp.estg.cmu.vivaracing.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
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
import pt.ipp.estg.cmu.vivaracing.data.model.AmateurRun
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.UserProfile
import pt.ipp.estg.cmu.vivaracing.ui.components.AmateurRunCard
import pt.ipp.estg.cmu.vivaracing.ui.components.RaceCard
import pt.ipp.estg.cmu.vivaracing.ui.components.StatTile

/** Estado editável do perfil do utilizador. */
data class ProfileFormState(
    val username: String = "",
    val city: String = "",
    val phone: String = "",
    val bibNumber: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false
)

/**
 * ViewModel do perfil.
 *
 * O perfil constitui a informação privada do utilizador no Firebase: fica
 * guardado em `users/{uid}`, acessível apenas ao próprio através das regras de
 * segurança, é replicado localmente em Room para consulta offline.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container
    private val uid = container.authRepository.currentUid.orEmpty()

    private val _form = MutableStateFlow(ProfileFormState())
    val form: StateFlow<ProfileFormState> = _form.asStateFlow()

    val email: String = container.authRepository.currentEmail

    val myRaces: StateFlow<List<Race>> = container.raceRepository.observeRacesByAuthor(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myRuns: StateFlow<List<AmateurRun>> = container.amateurRunRepository.observeByUser(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.authRepository.observeProfile(uid).collect { profile ->
                if (profile != null && !_form.value.isSaving) {
                    _form.value = _form.value.copy(
                        username = profile.username,
                        city = profile.city,
                        phone = profile.phone,
                        bibNumber = profile.bibNumber
                    )
                }
            }
        }
    }

    fun onUsernameChange(value: String) { _form.value = _form.value.copy(username = value) }
    fun onCityChange(value: String) { _form.value = _form.value.copy(city = value) }
    fun onPhoneChange(value: String) { _form.value = _form.value.copy(phone = value) }
    fun onBibChange(value: String) {
        _form.value = _form.value.copy(bibNumber = value.filter { it.isDigit() })
    }

    fun save(onSaved: () -> Unit) {
        val current = _form.value
        _form.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val profile = UserProfile(
                uid = uid,
                username = current.username.trim(),
                email = email,
                city = current.city.trim(),
                phone = current.phone.trim(),
                bibNumber = current.bibNumber.trim(),
                anonymousByDefault = container.preferences.snapshot().anonymousResults
            )
            container.authRepository.updateProfile(profile)
            _form.value = _form.value.copy(isSaving = false, saved = true)
            onSaved()
        }
    }
}

/** Ecrã de perfil com edição de dados pessoais e histórico do utilizador. */
@Composable
fun ProfileScreen(
    snackbarHostState: SnackbarHostState,
    onOpenRun: (String) -> Unit,
    onOpenRace: (String) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val form by viewModel.form.collectAsState()
    val myRaces by viewModel.myRaces.collectAsState()
    val myRuns by viewModel.myRuns.collectAsState()

    val savedMessage = stringResource(R.string.message_profile_saved)

    LaunchedEffect(form.saved) {
        if (form.saved) snackbarHostState.showSnackbar(savedMessage)
    }

    val totalDistance = myRuns.sumOf { it.distanceMeters }
    val totalSeconds = myRuns.sumOf { it.durationSeconds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = viewModel.email,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = stringResource(R.string.label_my_runs),
                value = myRuns.size.toString(),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_total_distance),
                value = Formatters.formatDistance(totalDistance),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.label_total_time),
                value = Formatters.formatDuration(totalSeconds),
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider()

        OutlinedTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChange,
            label = { Text(stringResource(R.string.field_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.city,
            onValueChange = viewModel::onCityChange,
            label = { Text(stringResource(R.string.field_city)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text(stringResource(R.string.field_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.bibNumber,
            onValueChange = viewModel::onBibChange,
            label = { Text(stringResource(R.string.field_bib_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { viewModel.save {} },
            enabled = !form.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_save_profile))
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_my_races),
            style = MaterialTheme.typography.titleMedium
        )
        if (myRaces.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_my_races),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            myRaces.forEach { race ->
                RaceCard(race = race, onClick = { onOpenRace(race.id) })
            }
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.section_my_runs),
            style = MaterialTheme.typography.titleMedium
        )
        if (myRuns.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_runs_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            myRuns.forEach { run ->
                AmateurRunCard(run = run, onClick = { onOpenRun(run.id) })
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
