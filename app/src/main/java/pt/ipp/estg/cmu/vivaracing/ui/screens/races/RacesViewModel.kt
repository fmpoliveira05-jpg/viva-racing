package pt.ipp.estg.cmu.vivaracing.ui.screens.races

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
import pt.ipp.estg.cmu.vivaracing.data.model.Race
import pt.ipp.estg.cmu.vivaracing.data.model.RaceStatus
import pt.ipp.estg.cmu.vivaracing.data.model.RaceType

/** Critérios de filtragem aplicados a listagem de provas. */
data class RaceFilter(
    val query: String = "",
    val type: RaceType? = null,
    val status: RaceStatus? = null,
    val onlySubscribed: Boolean = false
)

/**
 * ViewModel partilhado pelo ecrã de lista e pelo ecrã de mapa das provas.
 *
 * A lista final resulta da combinação de dois fluxos: o conteúdo da base de
 * dados local e os filtros escolhidos pelo utilizador. Como a fonte é o Room,
 * a listagem continua disponível sem ligação à Internet.
 */
class RacesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container

    private val _filter = MutableStateFlow(RaceFilter())
    val filter: StateFlow<RaceFilter> = _filter.asStateFlow()

    val races: StateFlow<List<Race>> = combine(
        container.raceRepository.observeRaces(),
        container.raceRepository.observeSubscriptions(),
        _filter
    ) { races, subscriptions, filter ->
        val subscribedIds = subscriptions.map { it.raceId }.toSet()
        races.filter { race ->
            val matchesQuery = filter.query.isBlank() ||
                race.name.contains(filter.query, ignoreCase = true) ||
                race.city.contains(filter.query, ignoreCase = true)
            val matchesType = filter.type == null || race.type == filter.type
            val matchesStatus = filter.status == null || race.status == filter.status
            val matchesSubscription = !filter.onlySubscribed || subscribedIds.contains(race.id)
            matchesQuery && matchesType && matchesStatus && matchesSubscription
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _filter.value = _filter.value.copy(query = value)
    }

    fun onTypeChange(type: RaceType?) {
        _filter.value = _filter.value.copy(type = type)
    }

    fun onStatusChange(status: RaceStatus?) {
        _filter.value = _filter.value.copy(status = status)
    }

    fun onOnlySubscribedChange(value: Boolean) {
        _filter.value = _filter.value.copy(onlySubscribed = value)
    }
}
