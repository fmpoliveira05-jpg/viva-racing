package pt.ipp.estg.cmu.vivaracing.data.model

/**
 * Modelos da componente social da aplicação: perfis públicos, amizades,
 * pedidos de amizade e subscrições de atletas.
 */

/**
 * Perfil público de um utilizador.
 *
 * Existe numa coleção separada do perfil privado para que a pesquisa de
 * utilizadores seja possível sem expor o telemóvel, o número de dorsal ou
 * quaisquer outros dados pessoais guardados em `users/{uid}`.
 */
data class PublicProfile(
    val uid: String,
    val username: String = "",
    val city: String = "",
    val photoUrl: String? = null,
    val updatedAt: Long = 0L,
    /**
     * Provas que este utilizador subscreveu, publicadas por ele próprio.
     *
     * Enquanto atleta, a pessoa é observada ao longo do percurso das provas em
     * que participa; para que outro utilizador possa saber se partilha alguma
     * prova com ela, e também para que o histórico de passagens fique
     * limitado a essas provas, a lista de identificadores é publicada no
     * perfil público.
     * Não expõe qualquer dado pessoal: são apenas identificadores de provas já
     * visíveis para a comunidade.
     */
    val raceIds: List<String> = emptyList()
)

/**
 * Totais sociais que cada utilizador publica no seu perfil público.
 *
 * Servem exclusivamente para que outra pessoa possa verificar, antes de enviar
 * um pedido de amizade, se o destinatário ainda tem capacidade disponível. São
 * apenas dois números: não revelam a identidade dos amigos nem de quem enviou
 * pedidos, informação que permanece restrita ao próprio.
 */
data class SocialCounters(
    val friends: Int = 0,
    val pending: Int = 0
)

/** Amizade estabelecida entre dois utilizadores. */
data class Friend(
    val uid: String,
    val username: String = "",
    val city: String = "",
    val since: Long = 0L
)

/** Estado de um pedido de amizade do ponto de vista do utilizador atual. */
enum class FriendRequestDirection { RECEIVED, SENT }

/** Pedido de amizade pendente. */
data class FriendRequest(
    val uid: String,
    val username: String = "",
    val city: String = "",
    val sentAt: Long = 0L,
    val direction: FriendRequestDirection = FriendRequestDirection.RECEIVED
)

/**
 * Subscrição de um atleta.
 *
 * Distingue-se da subscrição de uma prova: aqui o utilizador segue uma pessoa
 * e passa a receber avisos sobre as suas posições em qualquer prova.
 */
data class AthleteSubscription(
    val athleteUid: String,
    val username: String = "",
    val bibNumber: String = "",
    val since: Long = 0L
)

/** Limites impostos pela aplicação a componente social. */
object SocialLimits {

    /**
     * Número máximo de amigos.
     *
     * O valor não é arbitrário: as consultas de visibilidade usam o operador
     * `array-contains-any` do Firestore, limitado a 30 valores por consulta.
     * Reservando duas posições para os marcadores `public` e `u:<uid>`,
     * sobram 28; fixou-se 25 para deixar margem.
     */
    const val MAX_FRIENDS = 25

    /** Número máximo de pedidos de amizade pendentes na caixa de entrada. */
    const val MAX_PENDING_REQUESTS = 20

    /** Número máximo de atletas que um utilizador pode subscrever. */
    const val MAX_ATHLETE_SUBSCRIPTIONS = 30
}
