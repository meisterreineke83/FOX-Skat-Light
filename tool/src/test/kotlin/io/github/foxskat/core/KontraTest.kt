package io.github.foxskat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KontraTest {

    private fun deck(seed: Int) = Card.DECK.shuffled(Random(seed))

    /** Dealer 0: Vorhand 1, Mittelhand 2. Mittelhand says 18, Vorhand holds, then passes at 20; Hinterhand passes. */
    private fun grandBy2(seed: Int): Round {
        val r = Round(dealer = 0, deck = deck(seed))
        r.say(2, 18); r.answer(1, hold = true)
        r.say(2, 20); r.answer(1, hold = false)
        r.pass(0)
        r.playHand(2)
        r.declare(2, Contract(GameKind.GRAND, hand = true))
        return r
    }

    @Test
    fun onlyDefendersWhoBidMayGiveKontra() {
        val r = grandBy2(1)
        assertTrue(r.mayKontra(1), "Vorhand hat 18 gehalten")
        assertFalse(r.mayKontra(0), "Hinterhand hat nur gepasst")
        assertEquals(1, r.kontraDecider)
        assertEquals(1, r.activeSeat)
        assertTrue(r.legalCards(1).isEmpty(), "Erst Kontra entscheiden, dann ausspielen")
        r.kontra(1, say = false)
        assertNull(r.kontraDecider, "Hinterhand darf nicht, also keine weitere Frage")
        assertEquals(1, r.activeSeat)
        assertEquals(1, r.kontraFactor)
    }

    @Test
    fun kontraAndReDoubleTwice() {
        val r = grandBy2(2)
        assertFailsWith<IllegalStateException> { r.kontra(0, say = true) }
        r.kontra(1, say = true)
        assertEquals(2, r.kontraDecider, "Alleinspieler darf Re sagen")
        r.kontra(2, say = true)
        assertNull(r.kontraDecider)
        assertEquals(4, r.kontraFactor)
        assertEquals("Kontra, Re", r.kontraLabel)
        while (r.phase == Phase.PLAYING) Ai.act(r, r.activeSeat!!)
        val s = r.settlement!!
        assertEquals(4 * if (s.won) s.value else -2 * s.value, s.score)
        assertTrue(ScoreSheet().add(r).entries.single().game.endsWith(", Kontra, Re"))
    }

    @Test
    fun scoringMultipliesWonAndLost() {
        val c = Contract(GameKind.GRAND)
        val cards = Card.JACKS + Card.parseList("♣A ♣10 ♣K ♠A ♠10 ♠K ♥A ♥10")
        val won = Scoring.settle(c, 18, cards, declarerPoints = 70, declarerTricks = 7, kontraFactor = 2)
        assertEquals(2 * won.value, won.score)
        val lost = Scoring.settle(c, 18, cards, declarerPoints = 50, declarerTricks = 4, kontraFactor = 2)
        assertEquals(-4 * lost.value, lost.score)
    }

    @Test
    fun noKontraInRamschOrAfterFirstCard() {
        val ramsch = Round(dealer = 2, deck = deck(3))
        ramsch.pass(ramsch.mittelhand); ramsch.pass(ramsch.hinterhand); ramsch.pass(ramsch.vorhand)
        assertNull(ramsch.kontraDecider)

        val r = grandBy2(4)
        r.kontra(1, say = false)
        r.play(1, r.legalCards(1).first())
        assertNull(r.kontraDecider)
        assertFailsWith<IllegalStateException> { r.kontra(1, say = true) }
    }
}
