package pt.ipp.estg.cmu.vivaracing.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.ipp.estg.cmu.vivaracing.data.local.dao.UserProfileDao
import pt.ipp.estg.cmu.vivaracing.data.local.entity.UserProfileEntity
import pt.ipp.estg.cmu.vivaracing.data.model.PublicProfile
import pt.ipp.estg.cmu.vivaracing.data.model.UserProfile
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirebaseAuthService
import pt.ipp.estg.cmu.vivaracing.data.remote.firebase.FirestoreService

/** Identidade da sessão ativa. */
data class SessionState(
    val uid: String? = null,
    val isGuest: Boolean = false
) {
    val isAuthenticated: Boolean get() = uid != null
    val isRegistered: Boolean get() = uid != null && !isGuest
}

/**
 * Coordena a autenticação com o Firebase e o perfil do utilizador.
 *
 * O perfil existe em duas representações: o documento privado `users/{uid}`,
 * com telemóvel e número de dorsal, e a entrada publica em `publicProfiles`,
 * que contém apenas o nome e a localidade e torna o utilizador pesquisável
 * por quem lhe quiser enviar um pedido de amizade.
 */
class AuthRepository(
    private val authService: FirebaseAuthService,
    private val firestoreService: FirestoreService,
    private val profileDao: UserProfileDao
) {

    val currentUid: String?
        get() = authService.currentUid

    val isGuest: Boolean
        get() = authService.isGuest

    val currentDisplayName: String
        get() = authService.currentUser?.displayName.orEmpty()

    val currentEmail: String
        get() = authService.currentUser?.email.orEmpty()

    /** Emite o estado de sessão sempre que este muda. */
    fun observeSession(): Flow<SessionState> =
        authService.observeAuthState().map { user ->
            SessionState(uid = user?.uid, isGuest = user?.isAnonymous == true)
        }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        profileDao.observeById(uid).map { entity -> entity?.toDomain() }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        authService.signIn(email, password)
        Unit
    }

    suspend fun signInAsGuest(): Result<Unit> = runCatching {
        authService.signInAsGuest()
        Unit
    }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        city: String
    ): Result<Unit> = runCatching {
        val user = authService.signUp(email, password, username)
        val profile = UserProfile(
            uid = user.uid,
            username = username.trim(),
            email = email.trim(),
            city = city.trim()
        )
        profileDao.upsert(UserProfileEntity.fromDomain(profile))
        firestoreService.saveProfile(profile)
        firestoreService.savePublicProfile(profile.toPublicProfile())
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> = runCatching {
        profileDao.upsert(UserProfileEntity.fromDomain(profile))
        firestoreService.saveProfile(profile)
        firestoreService.savePublicProfile(profile.toPublicProfile())
    }

    /** Atualiza apenas a cache local a partir de uma emissão do Firestore. */
    suspend fun updateProfileCache(profile: UserProfile) {
        profileDao.upsert(UserProfileEntity.fromDomain(profile))
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        authService.sendPasswordReset(email)
    }

    suspend fun signOut() {
        authService.signOut()
        profileDao.clear()
    }

    /**
     * Garante que existe um perfil para o utilizador autenticado.
     *
     * A leitura remota é feita **antes** de qualquer escrita. Sem esta ordem,
     * ao reiniciar sessão a aplicação encontraria a cache local vazia (limpa
     * no encerramento de sessão), concluiria que o perfil não existe e
     * sobreporia o documento do Firestore com um perfil em branco, perdendo
     * a localidade, o telemóvel e o número de dorsal.
     */
    suspend fun ensureProfile(uid: String) {
        if (authService.isGuest) return

        val remote = runCatching { firestoreService.fetchProfile(uid) }
            .onFailure { Log.w(TAG, "Leitura remota do perfil falhou", it) }
            .getOrNull()

        if (remote != null) {
            profileDao.upsert(UserProfileEntity.fromDomain(remote))
            runCatching { firestoreService.savePublicProfile(remote.toPublicProfile()) }
            return
        }

        if (profileDao.findById(uid) != null) return

        val created = UserProfile(
            uid = uid,
            username = currentDisplayName.ifBlank { currentEmail.substringBefore('@') },
            email = currentEmail
        )
        profileDao.upsert(UserProfileEntity.fromDomain(created))
        runCatching {
            firestoreService.saveProfile(created)
            firestoreService.savePublicProfile(created.toPublicProfile())
        }
    }

    /** Perfil público do utilizador atual, usado nas operações sociais. */
    suspend fun currentPublicProfile(): PublicProfile? {
        val uid = currentUid ?: return null
        if (authService.isGuest) return null
        val cached = profileDao.findById(uid)?.toDomain()
        if (cached != null) return cached.toPublicProfile()
        return runCatching { firestoreService.fetchPublicProfile(uid) }.getOrNull()
    }

    private fun UserProfile.toPublicProfile(): PublicProfile = PublicProfile(
        uid = uid,
        username = username,
        city = city,
        photoUrl = photoUrl
    )

    private companion object {
        const val TAG = "AuthRepository"
    }
}
