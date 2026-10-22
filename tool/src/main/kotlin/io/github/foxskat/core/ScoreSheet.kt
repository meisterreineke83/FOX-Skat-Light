package io.github.foxskat.core

/** One line in the Skat list. */
data class ScoreEntry(
    val number: Int,
    /** -1 if all three passed ("eingepasst"). */
    val declarer: Int,
    val game: String,
    val bid: Int,
    val won: Boolean,
    val score: Int,
    val declarerPoints: Int,
) {
    val passedIn: Boolean get() = declarer < 0

    fun encode(): String = listOf(number, declarer, game.replace("|", "/"), bid, won, score, declarerPoints).joinToString("|")

    companion object {
        fun decode(line: String): ScoreEntry? {
            val p = line.split('|')
            if (p.size != 7) return null
            return ScoreEntry(
                number = p[0].toIntOrNull() ?: return null,
                declarer = p[1].toIntOrNull() ?: return null,
                game = p[2],
                bid = p[3].toIntOrNull() ?: return null,
                won = p[4].toBooleanStrictOrNull() ?: return null,
                score = p[5].toIntOrNull() ?: return null,
                declarerPoints = p[6].toIntOrNull() ?: return null,
            )
        }
    }
}

/** The running list: only the declarer scores, as in the plain German list. */
data class ScoreSheet(val entries: List<ScoreEntry> = emptyList()) {

    fun total(seat: Int): Int = entries.filter { it.declarer == seat }.sumOf { it.score }

    /** Next dealer: the deal moves on clockwise after every round, passed-in or not. */
    val nextDealer: Int get() = entries.size % 3

    fun add(round: Round): ScoreSheet {
        val number = entries.size + 1
        val entry = if (round.phase == Phase.PASSED_IN) {
            ScoreEntry(number, -1, "Eingepasst", 0, false, 0, 0)
        } else {
            val s = checkNotNull(round.settlement) { "Runde ist nicht fertig" }
            ScoreEntry(number, round.declarer, round.contract!!.name, round.bid, s.won, s.score, s.declarerPoints)
        }
        return ScoreSheet(entries + entry)
    }

    fun encode(): String = entries.joinToString("\n") { it.encode() }

    companion object {
        fun decode(text: String): ScoreSheet =
            ScoreSheet(text.lines().filter { it.isNotBlank() }.mapNotNull(ScoreEntry::decode))
    }
}

object CardOrder {
    private val DEFAULT_SUITS = listOf(Suit.KREUZ, Suit.PIK, Suit.HERZ, Suit.KARO)

    /** Hand order for display: jacks, then trump suit, then the other suits, high to low. */
    fun sort(cards: Collection<Card>, contract: Contract? = null): List<Card> {
        if (contract?.kind == GameKind.NULL) {
            return cards.sortedWith(
                compareBy<Card> { DEFAULT_SUITS.indexOf(it.suit) }.thenByDescending { contract.strength(it) }
            )
        }
        val suits = contract?.trump?.let { listOf(it) + (DEFAULT_SUITS - it) } ?: DEFAULT_SUITS
        return cards.sortedWith(
            compareBy<Card> { if (it.isJack) 0 else 1 }
                .thenBy { if (it.isJack) it.suit.ordinal else suits.indexOf(it.suit) }
                .thenBy { Contract.SUIT_ORDER.indexOf(it.rank) }
        )
    }
}
