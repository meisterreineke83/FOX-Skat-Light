package io.github.foxskat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RoundTest {

    private fun deck(seed: Int) = Card.DECK.shuffled(Random(seed))

    @Test
    fun biddingOrderMittelhandToVorhandThenHinterhand() {
        val r = Round(dealer = 0, deck = deck(1))
        assertEquals(1, r.vorhand)
        assertEquals(2, r.mittelhand)
        assertEquals(0, r.hinterhand)
        assertEquals(2, r.activeSeat, "Mittelhand sagt zuerst")
        r.say(2, 18)
        assertEquals(1, r.activeSeat, "Vorhand hört")
        r.answer(1, hold = true)
        r.say(2, 20)
        r.answer(1, hold = false)
        assertEquals(0, r.activeSeat, "jetzt sagt Hinterhand")
        r.say(0, 22)
        assertEquals(2, r.activeSeat, "Mittelhand hört jetzt")
        r.pass(2)
        assertEquals(Phase.SKAT_DECISION, r.phase)
        assertEquals(0, r.declarer)
        assertEquals(22, r.bid)
    }

    @Test
    fun allPassIsPassedIn() {
        val r = Round(dealer = 2, deck = deck(2))
        r.pass(r.mittelhand)
        r.pass(r.hinterhand)
        assertTrue(r.vorhandAlone)
        r.pass(r.vorhand)
        assertEquals(Phase.PASSED_IN, r.phase)
    }

    @Test
    fun vorhandCanPlayFor18() {
        val r = Round(dealer = 2, deck = deck(3))
        r.pass(r.mittelhand)
        r.pass(r.hinterhand)
        r.playFor18(r.vorhand)
        assertEquals(r.vorhand, r.declarer)
        assertEquals(18, r.bid)
    }

    @Test
    fun cannotBidBelowCurrentValue() {
        val r = Round(dealer = 0, deck = deck(4))
        r.say(2, 20)
        r.answer(1, hold = true)
        assertFailsWith<IllegalArgumentException> { r.say(2, 18) }
        assertFailsWith<IllegalArgumentException> { r.say(2, 21) }
    }

    @Test
    fun pickUpDiscardAndPlayKeepsAllCards() {
        val r = Round(dealer = 0, deck = deck(5))
        r.say(2, 18); r.answer(1, false); r.pass(0)
        r.pickUpSkat(2)
        assertEquals(12, r.hand(2).size)
        val push = r.hand(2).take(2)
        r.discard(2, push)
        assertEquals(push, r.skat)
        assertFailsWith<IllegalArgumentException> { r.declare(2, Contract(GameKind.GRAND, hand = true)) }
        r.declare(2, Contract(GameKind.GRAND))
        assertEquals(1, r.activeSeat, "Vorhand spielt aus")
        while (r.phase == Phase.PLAYING) {
            val seat = r.activeSeat!!
            r.play(seat, r.legalCards(seat).first())
        }
        assertEquals(10, r.tricks.size)
        assertEquals(120, r.pointsOf(2) + r.defenderPoints)
        assertNotNull(r.settlement)
    }

    @Test
    fun mustFollowSuit() {
        val r = Round(dealer = 0, deck = deck(6))
        r.say(2, 18); r.answer(1, false); r.pass(0)
        r.playHand(2)
        r.declare(2, Contract(GameKind.GRAND, hand = true))
        val lead = r.hand(1).first()
        r.play(1, lead)
        val c = r.contract!!
        val following = r.hand(2).filter { c.followSuit(it) == c.followSuit(lead) }
        val wrong = r.hand(2).firstOrNull { c.followSuit(it) != c.followSuit(lead) }
        if (following.isNotEmpty() && wrong != null) {
            assertFailsWith<IllegalArgumentException> { r.play(2, wrong) }
        }
    }

    @Test
    fun computerPlaysThousandRoundsWithoutBreakingRules() {
        var sheet = ScoreSheet()
        val kinds = mutableMapOf<GameKind, Int>()
        var won = 0
        repeat(1000) { i ->
            val r = Round(dealer = sheet.nextDealer, deck = deck(1000 + i))
            var steps = 0
            while (r.activeSeat != null) {
                assertTrue(Ai.act(r, r.activeSeat!!))
                assertTrue(++steps < 200, "Runde endet nicht")
            }
            if (r.phase == Phase.FINISHED) {
                val c = r.contract!!
                kinds.merge(c.kind, 1, Int::plus)
                if (r.settlement!!.won) won++
                if (c.kind != GameKind.NULL || r.tricks.size == 10) {
                    assertEquals(120, r.pointsOf(r.declarer) + r.defenderPoints)
                }
                assertEquals(32, r.playedCards.size + r.skat.size + (0..2).sumOf { r.hand(it).size })
            }
            sheet = sheet.add(r)
        }
        val played = kinds.values.sum()
        println("Spiele: $played von 1000, gewonnen: $won, Arten: $kinds")
        assertTrue(played > 600, "Computer sollte meistens spielen, nicht einpassen: $played")
        assertTrue(won > played * 0.5, "Alleinspieler sollte meist gewinnen: $won/$played")
    }
}

class TextsTest {
    @Test
    fun statusAndResultReadNaturally() {
        val r = Round(dealer = 1, deck = Card.DECK.shuffled(Random(9)))
        // Dealer 1: Vorhand 2, Mittelhand 0 (you) says first.
        assertEquals("Du sagst zuerst.", Texts.status(r))
        r.say(0, 18)
        assertEquals("Rechts überlegt …", Texts.status(r))
        r.answer(2, hold = false)
        r.pass(1)
        r.pickUpSkat(0)
        r.discard(0, r.hand(0).take(2))
        r.declare(0, Contract(GameKind.GRAND))
        while (r.phase == Phase.PLAYING) Ai.act(r, r.activeSeat!!)
        assertTrue(Texts.result(r).startsWith("Du hast Grand mit "))
    }
}
