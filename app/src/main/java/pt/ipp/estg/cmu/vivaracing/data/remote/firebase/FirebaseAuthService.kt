package pt.ipp.estg.cmu.vivaracing.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Encapsula a API de autenticação do Firebase (método Email/Palavra-passe).
 *
 * As chamadas são expostas como funções suspensas para poderem ser invocadas
 * de forma sequencial dentro das coroutines dos ViewModel, conforme
 * demonstrado nos materiais da unidade curricular.
 */
class FirebaseAuthService {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Indica se a sessão ativa é anónima, isto é, se o utilizador está a usar
     * a aplicação em modo convidado.
     */
    val isGuest: Boolean
        get() = auth.currentUser?.isAnonymous == true

    /** Emite o utilizador autenticado sempre que o estado de sessão muda. */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(result.user) { "Autenticacao sem utilizador associado" }
    }

    suspend fun signUp(email: String, password: String, username: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user) { "Registo sem utilizador associado" }
        val profileUpdate = UserProfileChangeRequest.Builder()
            .setDisplayName(username.trim())
            .build()
        user.updateProfile(profileUpdate).await()
        return user
    }

    /**
     * Inicia sessão anónima (modo convidado).
     *
     * A autenticação anónima do Firebase atribui um identificador temporário
     * ao dispositivo. Isto permite que as regras de segurança continuem a
     * exigir um utilizador autenticado para qualquer leitura, sem obrigar o
     * visitante a criar conta para consultar as provas públicas.
     */
    suspend fun signInAsGuest(): FirebaseUser {
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user) { "Sessao anonima sem utilizador associado" }
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        auth.signOut()
    }
}
