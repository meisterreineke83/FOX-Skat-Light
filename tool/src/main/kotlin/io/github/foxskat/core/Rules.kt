package io.github.foxskat.core

enum class GameKind { FARBE, GRAND, NULL }

/** What the declarer plays, including Hand and the announcements. */
data class Contract(
    val kind: GameKind,
    val trump: Suit? = null,
    val hand: Boolean = false,
    val schneiderAngesagt: Boolean = false,
    val schwarzAngesagt: Boolean = false,
    val ouvert: Boolean = false,
) {
    init {
        require((kind == GameKind.FARBE) == (trump != null)) { "Nur Farbspiele haben eine Trumpffarbe" }
        if (kind == GameKind.NULL) {
            require(!schneiderAngesagt && !schwarzAngesagt) { "Null hat keine Ansagen" }
        } else {
            require(!schneiderAngesagt || hand) { "Schneider ansagen geht nur bei Hand" }
            require(!schwarzAngesagt || schneiderAngesagt) { "Schwarz angesagt schließt Schneider angesagt ein" }
            require(!ouvert || schwarzAngesagt) { "Ouvert schließt Schwarz angesagt ein" }
        }
    }

    val name: String
        get() = buildString {
            append(
                when (kind) {
                    GameKind.FARBE -> trump!!.germanName
                    GameKind.GRAND -> "Grand"
                    GameKind.NULL -> "Null"
                }
            )
            if (kind == GameKind.NULL) {
                if (ouvert) append(" Ouvert")
                if (hand) append(" Hand")
            } else {
                if (hand) append(" Hand")
                when {
                    ouvert -> append(" Ouvert")
                    schwarzAngesagt -> append(", Schwarz angesagt")
                    schneiderAngesagt -> append(", Schneider angesagt")
                }
            }
        }

    /** Grundwert: 9–12 for suits, 24 for grand. Null games have fixed values instead. */
    val baseValue: Int
        get() = when (kind) {
            GameKind.FARBE -> trump!!.baseValue
            GameKind.GRAND -> 24
            GameKind.NULL -> nullValue
        }

    private val nullValue: Int
        get() = when {
            ouvert && hand -> 59
            ouvert -> 46
            hand -> 35
            else -> 23
        }

    fun isTrump(card: Card): Boolean = when (kind) {
        GameKind.FARBE -> card.isJack || card.suit == trump
        GameKind.GRAND -> card.isJack
        GameKind.NULL -> false
    }

    /** The suit a card belongs to for following suit; null means trump. */
    fun followSuit(card: Card): Suit? = if (isTrump(card)) null else card.suit

    /** Higher is stronger, only comparable between cards of the same [followSuit]. */
    fun strength(card: Card): Int = when {
        kind == GameKind.NULL -> NULL_ORDER.indexOf(card.rank)
        card.isJack -> 100 - card.suit.ordinal
        else -> 10 - card.rank.ordinal
    }

    /** True if [challenger] beats [current], the card that is winning the trick so far. */
    fun beats(challenger: Card, current: Card): Boolean {
        val chTrump = isTrump(challenger)
        val curTrump = isTrump(current)
        return when {
            chTrump && !curTrump -> true
            !chTrump && curTrump -> false
            followSuit(challenger) != followSuit(current) -> false
            else -> strength(challenger) > strength(current)
        }
    }

    /** Index (0..2) of the card that wins a complete or partial trick. */
    fun trickWinnerIndex(trick: List<Card>): Int {
        var best = 0
        for (i in 1 until trick.size) if (beats(trick[i], trick[best])) best = i
        return best
    }

    fun legalCards(hand: List<Card>, trick: List<Card>): List<Card> {
        if (trick.isEmpty()) return hand
        val led = followSuit(trick.first())
        val following = hand.filter { followSuit(it) == led }
        return following.ifEmpty { hand }
    }

    /** All trumps from highest to lowest; empty for null. */
    val trumpOrder: List<Card>
        get() = when (kind) {
            GameKind.FARBE -> Card.JACKS + SUIT_ORDER.map { Card(trump!!, it) }
            GameKind.GRAND -> Card.JACKS
            GameKind.NULL -> emptyList()
        }

    /**
     * Spitzen ("mit"/"ohne"): how many of the top trumps the declarer holds (or lacks)
     * in an unbroken run, counted over the declarer's cards including the Skat.
     */
    fun spitzen(declarerCards: Collection<Card>): Int {
        val order = trumpOrder
        if (order.isEmpty()) return 0
        val mit = order.first() in declarerCards
        return order.takeWhile { (it in declarerCards) == mit }.size
    }

    fun isMit(declarerCards: Collection<Card>): Boolean = trumpOrder.firstOrNull() in declarerCards

    /** Game value assuming no Schneider or Schwarz is played beyond what is announced. */
    fun minimumValue(declarerCards: Collection<Card>): Int = valueFor(declarerCards, schneider = false, schwarz = false)

    fun valueFor(declarerCards: Collection<Card>, schneider: Boolean, schwarz: Boolean): Int {
        if (kind == GameKind.NULL) return nullValue
        var multiplier = spitzen(declarerCards) + 1
        if (hand) multiplier++
        if (schneider || schneiderAngesagt) multiplier++
        if (schneiderAngesagt) multiplier++
        if (schwarz || schwarzAngesagt) multiplier++
        if (schwarzAngesagt) multiplier++
        if (ouvert) multiplier++
        return multiplier * baseValue
    }

    companion object {
        /** Order of non-jack cards in suit and grand games, highest first. */
        val SUIT_ORDER = listOf(Rank.ASS, Rank.ZEHN, Rank.KOENIG, Rank.DAME, Rank.NEUN, Rank.ACHT, Rank.SIEBEN)

        /** Null order from lowest to highest (index = strength). */
        val NULL_ORDER = listOf(Rank.SIEBEN, Rank.ACHT, Rank.NEUN, Rank.ZEHN, Rank.BUBE, Rank.DAME, Rank.KOENIG, Rank.ASS)

        /** All values a player can bid, ascending: 18, 20, 22, 23, 24, 27, … */
        val BID_VALUES: List<Int> = buildSet {
            for (base in listOf(9, 10, 11, 12)) for (m in 2..18) add(base * m)
            for (m in 2..11) add(24 * m)
            addAll(listOf(23, 35, 46, 59))
        }.filter { it >= 18 }.sorted()

        fun nextBid(after: Int): Int? = BID_VALUES.firstOrNull { it > after }
    }
}

/** Outcome of one finished round. */
data class Settlement(
    val won: Boolean,
    val value: Int,
    /** Points for the declarer's score: value if won, minus twice the value if lost. */
    val score: Int,
    val declarerPoints: Int,
    val schneider: Boolean,
    val schwarz: Boolean,
    val overbid: Boolean,
    val spitzen: Int,
    val mit: Boolean,
)

object Scoring {

    /**
     * Settles a round after all tricks are played.
     *
     * @param declarerCards the declarer's ten cards plus the original Skat (for Spitzen)
     * @param declarerPoints card points won by the declarer including the pushed Skat
     * @param declarerTricks number of tricks the declarer won (0..10)
     */
    fun settle(
        contract: Contract,
        bid: Int,
        declarerCards: Collection<Card>,
        declarerPoints: Int,
        declarerTricks: Int,
    ): Settlement {
        val spitzen = contract.spitzen(declarerCards)
        val mit = contract.isMit(declarerCards)
        if (contract.kind == GameKind.NULL) {
            val value = contract.baseValue
            val overbid = value < bid
            val won = declarerTricks == 0 && !overbid
            return Settlement(won, value, if (won) value else -2 * value, declarerPoints, false, false, overbid, 0, false)
        }

        val schneider = declarerPoints >= 90 || declarerPoints <= 30
        val schwarz = declarerTricks == 10 || declarerTricks == 0
        var won = declarerPoints >= 61
        if (contract.schneiderAngesagt && declarerPoints < 90) won = false
        if (contract.schwarzAngesagt && declarerTricks < 10) won = false

        var value = contract.valueFor(declarerCards, schneider, schwarz)
        val overbid = value < bid
        if (overbid) {
            won = false
            value = generateSequence(contract.baseValue) { it + contract.baseValue }.first { it >= bid }
        }
        return Settlement(won, value, if (won) value else -2 * value, declarerPoints, schneider, schwarz, overbid, spitzen, mit)
    }
}
