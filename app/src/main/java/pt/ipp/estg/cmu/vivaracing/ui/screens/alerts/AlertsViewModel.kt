package pt.ipp.estg.cmu.vivaracing.ui.screens.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.model.AlertType
import pt.ipp.estg.cmu.vivaracing.data.model.RaceAlert

/**
 * ViewModel partilhado pelos ecrãs de lista e de mapa de alertas.
 *
 * Por omissão mostra apenas os alertas das provas subscritas pelo utilizador,
 * que é o comportamento previsto no enunciado; o filtro pode ser desligado
 * para consultar toda a atividade publica da comunidade.
 */
class AlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _onlySubscribed = MutableStateFlow(true)
    val onlySubscribed: StateFlow<Boolean> = _onlySubscribed.asStateFlow()

    private val _typeFilter = MutableStateFlow<AlertType?>(null)
    val typeFilter: StateFlow<AlertType?> = _typeFilter.asStateFlow()

    val alerts: StateFlow<List<RaceAlert>> = combine(
        container.alertRepository.observeAll(),
        container.alertRepository.observeSubscribed(),
        _onlySubscribed,
        _typeFilter
    ) { all, subscribed, onlySubscribed, type ->
        val base = if (onlySubscribed) subscribed else all
        if (type == null) base else base.filter { it.type == type }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleOnlySubscribed() {
        _onlySubscribed.value = !_onlySubscribed.value
    }

    fun onTypeFilterChange(type: AlertType?) {
        _typeFilter.value = type
    }
}
