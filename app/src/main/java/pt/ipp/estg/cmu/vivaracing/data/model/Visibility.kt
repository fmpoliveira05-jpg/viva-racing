package pt.ipp.estg.cmu.vivaracing.data.model

/**
 * Visibilidade de uma prova e do conteúdo que dela depende.
 *
 * A visibilidade é materializada no Firestore através do campo `visibleTo`,
 * uma lista de marcadores textuais. Esta representação permite exprimir os
 * três níveis com uma única consulta `array-contains-any`, sem índices
 * compostos e sem múltiplos ouvintes em paralelo:
 *
 *  - PUBLIC  -> ["public"]
 *  - PRIVATE -> ["u:<autor>"]
 *  - FRIENDS -> ["u:<autor>", "f:<autor>"]
 *
 * O cliente consulta com os marcadores a que tem direito: `public`, o seu
 * próprio `u:<uid>` e um `f:<amigo>` por cada amizade estabelecida.
 */
enum class RaceVisibility {
    PUBLIC, FRIENDS, PRIVATE;

    companion object {
        fun fromName(value: String?): RaceVisibility =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PUBLIC
    }
}

object VisibilityTokens {

    const val PUBLIC = "public"

    private const val OWNER_PREFIX = "u:"
    private const val FRIENDS_PREFIX = "f:"

    fun owner(uid: String): String = "$OWNER_PREFIX$uid"

    fun friendsOf(uid: String): String = "$FRIENDS_PREFIX$uid"

    /** Marcadores gravados num registo, em função da visibilidade escolhida. */
    fun forRecord(visibility: RaceVisibility, authorUid: String): List<String> =
        when (visibility) {
            RaceVisibility.PUBLIC -> listOf(PUBLIC)
            RaceVisibility.PRIVATE -> listOf(owner(authorUid))
            RaceVisibility.FRIENDS -> listOf(owner(authorUid), friendsOf(authorUid))
        }

    /**
     * Marcadores que um utilizador pode usar na consulta.
     *
     * O convidado não tem identificador próprio nem amigos, pelo que só vê
     * conteúdo público.
     */
    fun forViewer(uid: String?, friendUids: Collection<String>, isGuest: Boolean): List<String> {
        if (isGuest || uid.isNullOrBlank()) return listOf(PUBLIC)
        val tokens = mutableListOf(PUBLIC, owner(uid))
        friendUids.take(SocialLimits.MAX_FRIENDS).forEach { tokens += friendsOf(it) }
        return tokens
    }
}
