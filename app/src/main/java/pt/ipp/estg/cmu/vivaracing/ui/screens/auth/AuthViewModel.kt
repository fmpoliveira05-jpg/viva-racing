package pt.ipp.estg.cmu.vivaracing.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.repository.AuthRepository

/** Estado do formulário de autenticação. */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val username: String = "",
    val city: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

/**
 * ViewModel partilhado pelos ecrãs de início de sessão e de registo.
 *
 * Estende `AndroidViewModel` para obter acesso ao contentor de dependências
 * através da instância de [VivaRacingApp], dispensando uma fábrica própria: o
 * `ViewModelProvider` predefinido do Android já sabe instanciar ViewModels com
 * um argumento do tipo `Application`.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository: AuthRepository =
        (application as VivaRacingApp).container.authRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, errorMessage = null)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, errorMessage = null)
    }

    fun onCityChange(value: String) {
        _uiState.value = _uiState.value.copy(city = value, errorMessage = null)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = ERROR_EMPTY_FIELDS)
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.signIn(state.email, state.password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: ERROR_GENERIC
                    )
                }
        }
    }

    fun signUp(onSuccess: () -> Unit) {
        val state = _uiState.value
        when {
            state.username.isBlank() || state.email.isBlank() || state.password.isBlank() -> {
                _uiState.value = state.copy(errorMessage = ERROR_EMPTY_FIELDS)
                return
            }

            state.password.length < MIN_PASSWORD_LENGTH -> {
                _uiState.value = state.copy(errorMessage = ERROR_SHORT_PASSWORD)
                return
            }

            state.password != state.confirmPassword -> {
                _uiState.value = state.copy(errorMessage = ERROR_PASSWORD_MISMATCH)
                return
            }
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.signUp(state.email, state.password, state.username, state.city)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: ERROR_GENERIC
                    )
                }
        }
    }

    /** Inicia sessão anónima, dando acesso apenas de leitura ao conteúdo público. */
    fun signInAsGuest() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            authRepository.signInAsGuest()
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: ERROR_GENERIC
                    )
                }
        }
    }

    fun resetPassword() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = ERROR_EMPTY_FIELDS)
            return
        }
        viewModelScope.launch {
            authRepository.sendPasswordReset(email)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(infoMessage = INFO_RESET_SENT)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage)
                }
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6

        // Códigos internos convertidos em texto traduzido pela camada de UI.
        const val ERROR_EMPTY_FIELDS = "error_empty_fields"
        const val ERROR_SHORT_PASSWORD = "error_short_password"
        const val ERROR_PASSWORD_MISMATCH = "error_password_mismatch"
        const val ERROR_GENERIC = "error_generic"
        const val INFO_RESET_SENT = "info_reset_sent"
    }
}
