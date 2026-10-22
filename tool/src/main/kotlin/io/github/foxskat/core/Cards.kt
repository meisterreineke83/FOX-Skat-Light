package io.github.foxskat.core

/** The four suits, ordered by their value in bidding (Kreuz highest). */
enum class Suit(val germanName: String, val symbol: String, val baseValue: Int) {
    KREUZ("Kreuz", "♣", 12),
    PIK("Pik", "♠", 11),
    HERZ("Herz", "♥", 10),
    KARO("Karo", "♦", 9),
}

/** Ranks in the order used for suit and grand games (Ass highest, jacks handled separately). */
enum class Rank(val label: String, val points: Int) {
    ASS("A", 11),
    ZEHN("10", 10),
    KOENIG("K", 4),
    DAME("D", 3),
    BUBE("B", 2),
    NEUN("9", 0),
    ACHT("8", 0),
    SIEBEN("7", 0),
}

data class Card(val suit: Suit, val rank: Rank) {
    val points: Int get() = rank.points
    val isJack: Boolean get() = rank == Rank.BUBE

    /** Short form like "♥10" or "♣B". */
    override fun toString(): String = suit.symbol + rank.label

    companion object {
        val DECK: List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(s, r) } }

        /** Jacks from highest to lowest. */
        val JACKS: List<Card> = Suit.entries.map { Card(it, Rank.BUBE) }

        private val bySymbol = DECK.associateBy { it.toString() }

        fun parse(text: String): Card = bySymbol[text.trim()] ?: error("Unbekannte Karte: $text")

        /** Parses a space separated list like "♣B ♥A ♥10". */
        fun parseList(text: String): List<Card> =
            text.split(' ').filter { it.isNotBlank() }.map(::parse)
    }
}

fun Iterable<Card>.points(): Int = sumOf { it.points }
