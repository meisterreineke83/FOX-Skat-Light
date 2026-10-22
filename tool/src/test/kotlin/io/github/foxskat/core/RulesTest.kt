package io.github.foxskat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RulesTest {

    private fun cards(text: String) = Card.parseList(text)

    @Test
    fun deckHas120Points() {
        assertEquals(32, Card.DECK.size)
        assertEquals(120, Card.DECK.points())
    }

    @Test
    fun bidValuesStartLikeAtTheTable() {
        assertEquals(listOf(18, 20, 22, 23, 24, 27, 30, 33, 35, 36, 40, 44, 45, 46, 48, 50), Contract.BID_VALUES.take(16))
        assertEquals(264, Contract.BID_VALUES.last())
    }

    @Test
    fun jacksAreTrumpAndBeatTheTrumpAce() {
        val herz = Contract(GameKind.FARBE, Suit.HERZ)
        assertTrue(herz.beats(Card.parse("♦B"), Card.parse("♥A")))
        assertTrue(herz.beats(Card.parse("♣B"), Card.parse("♠B")))
        assertTrue(herz.beats(Card.parse("♥7"), Card.parse("♠A")))
        assertFalse(herz.beats(Card.parse("♠A"), Card.parse("♣7")), "Abwerfen gewinnt nicht")
        assertEquals(1, herz.trickWinnerIndex(cards("♠10 ♠A ♠K")))
    }

    @Test
    fun jackFollowsTrumpNotItsSuit() {
        val pik = Contract(GameKind.FARBE, Suit.PIK)
        val hand = cards("♥B ♥A ♣7")
        // Herz is led: the Herz jack is trump and does not count as Herz.
        assertEquals(cards("♥A"), pik.legalCards(hand, cards("♥10")))
        // Trump is led: the jack must follow.
        assertEquals(cards("♥B"), pik.legalCards(hand, cards("♠7")))
    }

    @Test
    fun nullOrderPutsTenBetweenNineAndJack() {
        val n = Contract(GameKind.NULL)
        assertTrue(n.beats(Card.parse("♥B"), Card.parse("♥10")))
        assertTrue(n.beats(Card.parse("♥10"), Card.parse("♥9")))
        assertFalse(n.isTrump(Card.parse("♣B")))
        assertEquals(cards("♣B ♣7"), n.legalCards(cards("♣B ♣7 ♥A"), cards("♣A")))
    }

    @Test
    fun spitzenMitAndOhne() {
        val kreuz = Contract(GameKind.FARBE, Suit.KREUZ)
        assertEquals(2, kreuz.spitzen(cards("♣B ♠B ♦B ♣A")))
        assertTrue(kreuz.isMit(cards("♣B")))
        assertEquals(3, kreuz.spitzen(cards("♦B ♣10")), "ohne drei")
        assertEquals(4, Contract(GameKind.GRAND).spitzen(cards("♥A")), "Grand ohne vier")
        // Mit 4 bis zur Trumpf-Sieben = mit 11.
        assertEquals(11, kreuz.spitzen(cards("♣B ♠B ♥B ♦B ♣A ♣10 ♣K ♣D ♣9 ♣8 ♣7")))
    }

    @Test
    fun simpleWinCountsSpitzenPlusOne() {
        val herz = Contract(GameKind.FARBE, Suit.HERZ)
        val s = Scoring.settle(herz, 18, cards("♣B ♥A ♥10 ♥K ♥D ♥9 ♠A ♠10 ♦A ♦7 ♦8 ♦9"), 75, 6)
        assertTrue(s.won)
        assertEquals(20, s.value, "mit 1, Spiel 2 × 10")
        assertEquals(20, s.score)
    }

    @Test
    fun schneiderAndHandAddMultipliers() {
        val grand = Contract(GameKind.GRAND, hand = true)
        val s = Scoring.settle(grand, 18, cards("♣B ♠B ♥A"), 95, 8)
        // mit 2, Spiel 3, Hand 4, Schneider 5 → 5 × 24.
        assertEquals(120, s.value)
        assertTrue(s.won && s.schneider && !s.schwarz)
    }

    @Test
    fun lostGameCountsDouble() {
        val karo = Contract(GameKind.FARBE, Suit.KARO)
        val s = Scoring.settle(karo, 18, cards("♣B ♦A"), 60, 4)
        assertFalse(s.won)
        assertEquals(18, s.value)
        assertEquals(-36, s.score)
    }

    @Test
    fun overbidLosesWithNextMultipleOfTheBase() {
        val pik = Contract(GameKind.FARBE, Suit.PIK)
        // ohne 1, Spiel 2 → 22, but 24 was bid → lost with 33 (3 × 11).
        val s = Scoring.settle(pik, 24, cards("♠B ♠A"), 80, 7)
        assertTrue(s.overbid)
        assertFalse(s.won)
        assertEquals(33, s.value)
        assertEquals(-66, s.score)
    }

    @Test
    fun schneiderAnnouncedButMissedLoses() {
        val c = Contract(GameKind.FARBE, Suit.KREUZ, hand = true, schneiderAngesagt = true)
        val s = Scoring.settle(c, 18, cards("♣B ♠B"), 85, 8)
        assertFalse(s.won)
        // mit 2, Spiel 3, Hand 4, Schneider 5, angesagt 6 → 72.
        assertEquals(72, s.value)
    }

    @Test
    fun nullValues() {
        assertEquals(23, Contract(GameKind.NULL).baseValue)
        assertEquals(35, Contract(GameKind.NULL, hand = true).baseValue)
        assertEquals(46, Contract(GameKind.NULL, ouvert = true).baseValue)
        assertEquals(59, Contract(GameKind.NULL, hand = true, ouvert = true).baseValue)
        val lost = Scoring.settle(Contract(GameKind.NULL), 18, emptyList(), 0, 1)
        assertEquals(-46, lost.score)
    }

    @Test
    fun scoreSheetRoundTrip() {
        val sheet = ScoreSheet(listOf(ScoreEntry(1, 0, "Herz Hand", 30, true, 30, 70), ScoreEntry(2, -1, "Eingepasst", 0, false, 0, 0)))
        assertEquals(sheet, ScoreSheet.decode(sheet.encode()))
        assertEquals(30, sheet.total(0))
        assertEquals(2, sheet.nextDealer)
    }
}
