package pt.ipp.estg.cmu.vivaracing.ui.screens.social

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.ipp.estg.cmu.vivaracing.VivaRacingApp
import pt.ipp.estg.cmu.vivaracing.data.model.AthleteSubscription
import pt.ipp.estg.cmu.vivaracing.data.model.Friend
import pt.ipp.estg.cmu.vivaracing.data.model.FriendRequest
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile
import pt.ipp.estg.cmu.vivaracing.data.model.SocialLimits
import pt.ipp.estg.cmu.vivaracing.data.repository.SocialResult

/**
 * ViewModel da componente social.
 *
 * Concentra a lista de amigos, as duas caixas de pedidos (recebidos e
 * enviados), a pesquisa no diretório público e as subscrições de atletas.
 * Todas as listas provêm da base de dados local, pelo que continuam
 * disponíveis sem ligação à Internet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VivaRacingApp).container
    private val social = container.socialRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val currentUid: String? get() = container.authRepository.currentUid

    val friends: StateFlow<List<Friend>> = social.observeFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomingRequests: StateFlow<List<FriendRequest>> = social.observeIncomingRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outgoingRequests: StateFlow<List<FriendRequest>> = social.observeOutgoingRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val athletes: StateFlow<List<AthleteSubscription>> = social.observeAthleteSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Resultados da pesquisa, já depurados: o próprio utilizador, os amigos
     * atuais e as pessoas com pedidos pendentes não voltam a aparecer.
     */
    val searchResults: StateFlow<List<PublicProfile>> = _query
        .flatMapLatest { text -> social.searchProfiles(text) }
        .combine(social.observeFriends()) { profiles, friends ->
            val friendIds = friends.map { it.uid }.toSet()
            profiles.filter { it.uid != currentUid && !friendIds.contains(it.uid) }
        }
        .combine(social.observeIncomingRequests()) { profiles, incoming ->
            val ids = incoming.map { it.uid }.toSet()
            profiles.filter { !ids.contains(it.uid) }
        }
        .combine(social.observeOutgoingRequests()) { profiles, outgoing ->
            val ids = outgoing.map { it.uid }.toSet()
            profiles.filter { !ids.contains(it.uid) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val friendCount: StateFlow<Int> = social.observeFriendCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val maxFriends: Int = SocialLimits.MAX_FRIENDS
    val maxPendingRequests: Int = SocialLimits.MAX_PENDING_REQUESTS
    val maxAthletes: Int = SocialLimits.MAX_ATHLETE_SUBSCRIPTIONS

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun sendRequest(profile: PublicProfile) {
        viewModelScope.launch { publish(social.sendFriendRequest(profile.uid)) }
    }

    fun accept(request: FriendRequest) {
        viewModelScope.launch { publish(social.acceptFriendRequest(request)) }
    }

    fun decline(request: FriendRequest) {
        viewModelScope.launch { publish(social.declineFriendRequest(request)) }
    }

    fun cancel(request: FriendRequest) {
        viewModelScope.launch { publish(social.cancelFriendRequest(request)) }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch { publish(social.removeFriend(friend.uid)) }
    }

    fun subscribeAthlete(friend: Friend) {
        viewModelScope.launch {
            val profile = PublicProfile(friend.uid, friend.username, friend.city)
            publish(social.subscribeAthlete(profile, bibNumber = ""))
        }
    }

    fun unsubscribeAthlete(athleteUid: String) {
        viewModelScope.launch { publish(social.unsubscribeAthlete(athleteUid)) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun publish(result: SocialResult) {
        _message.value = when (result) {
            SocialResult.Success -> MESSAGE_SUCCESS
            SocialResult.AlreadyFriends -> MESSAGE_ALREADY_FRIENDS
            SocialResult.RequestAlreadySent -> MESSAGE_REQUEST_SENT
            SocialResult.FriendLimitReached -> MESSAGE_FRIEND_LIMIT
            SocialResult.RequestLimitReached -> MESSAGE_REQUEST_LIMIT
            SocialResult.AthleteLimitReached -> MESSAGE_ATHLETE_LIMIT
            SocialResult.GuestNotAllowed -> MESSAGE_GUEST
            is SocialResult.Failure -> result.message ?: MESSAGE_FAILURE
        }
    }

    companion object {
        const val MESSAGE_SUCCESS = "social_success"
        const val MESSAGE_ALREADY_FRIENDS = "social_already_friends"
        const val MESSAGE_REQUEST_SENT = "social_request_sent"
        const val MESSAGE_FRIEND_LIMIT = "social_friend_limit"
        const val MESSAGE_REQUEST_LIMIT = "social_request_limit"
        const val MESSAGE_ATHLETE_LIMIT = "social_athlete_limit"
        const val MESSAGE_GUEST = "social_guest"
        const val MESSAGE_FAILURE = "social_failure"
    }
}
