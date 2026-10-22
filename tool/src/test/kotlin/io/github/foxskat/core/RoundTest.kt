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
    fun allPassIsRamsch() {
        val r = Round(dealer = 2, deck = deck(2))
        r.pass(r.mittelhand)
        r.pass(r.hinterhand)
        assertTrue(r.vorhandAlone)
        r.pass(r.vorhand)
        assertTrue(r.isRamsch)
        assertEquals(Phase.PLAYING, r.phase)
        assertEquals(r.vorhand, r.activeSeat)
        while (r.phase == Phase.PLAYING) Ai.act(r, r.activeSeat!!)
        val result = assertNotNull(r.ramschResult)
        assertEquals(120, result.points.sum(), "Skat geht an den letzten Stich")
        val sheet = ScoreSheet().add(r)
        assertTrue(sheet.entries.single().game.startsWith("Ramsch"))
    }

    @Test
    fun ramschScoring() {
        val plain = RamschResult.of(points = listOf(50, 40, 30), tricks = listOf(4, 3, 3))
        assertEquals(listOf(-50, 0, 0), plain.scores)
        val jungfrau = RamschResult.of(points = listOf(70, 50, 0), tricks = listOf(6, 4, 0))
        assertEquals(listOf(-140, 0, 0), jungfrau.scores)
        val tie = RamschResult.of(points = listOf(45, 45, 30), tricks = listOf(4, 3, 3))
        assertEquals(listOf(0, 1), tie.losers)
        val durchmarsch = RamschResult.of(points = listOf(120, 0, 0), tricks = listOf(10, 0, 0))
        assertEquals(listOf(120, 0, 0), durchmarsch.scores)
    }

    @Test
    fun tiedRamschLosersBothScore() {
        val entry = ScoreEntry(4, 0, "Ramsch", 0, false, -45, 45, alsoSeats = listOf(1))
        val sheet = ScoreSheet(listOf(entry))
        assertEquals(-45, sheet.total(0))
        assertEquals(-45, sheet.total(1))
        assertEquals(sheet, ScoreSheet.decode(sheet.encode()))
        // Lists saved by version 0.1 have seven fields.
        assertEquals(ScoreEntry(1, 2, "Herz", 18, true, 20, 70), ScoreEntry.decode("1|2|Herz|18|true|20|70"))
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
        var ramsch = 0
        repeat(1000) { i ->
            val r = Round(dealer = sheet.nextDealer, deck = deck(1000 + i))
            var steps = 0
            while (r.activeSeat != null) {
                assertTrue(Ai.act(r, r.activeSeat!!))
                assertTrue(++steps < 200, "Runde endet nicht")
            }
            if (r.isRamsch) {
                ramsch++
                assertEquals(120, (0..2).sumOf { r.pointsOf(it) })
            } else if (r.phase == Phase.FINISHED) {
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
        println("Spiele: $played von 1000, gewonnen: $won, Arten: $kinds, Ramsch: $ramsch")
        assertEquals(1000, played + ramsch)
        assertTrue(played > 600, "Computer sollte meistens spielen, nicht einpassen: $played")
        assertTrue(won > played * 0.5, "Alleinspieler sollte meist gewinnen: $won/$played")
    }
}

class TextsTest {
    @Test
    fun trickCountUsesSingular() {
        assertEquals("0 Stiche", Texts.tricks(0))
        assertEquals("1 Stich", Texts.tricks(1))
        assertEquals("7 Stiche", Texts.tricks(7))
    }

    @Test
    fun statusAndResultReadNaturally() {
        val r = Round(dealer = 1, deck = Card.DECK.shuffled(Random(9)))
        // Dealer 1: Vorhand 2, Mittelhand 0 (you) says first.
        assertEquals("Du bist Mittelhand und sagst zuerst.", Texts.status(r))
        r.say(0, 18)
        assertEquals("Martin überlegt …", Texts.status(r))
        r.answer(2, hold = false)
        r.pass(1)
        r.pickUpSkat(0)
        r.discard(0, r.hand(0).take(2))
        r.declare(0, Contract(GameKind.GRAND))
        while (r.phase == Phase.PLAYING) Ai.act(r, r.activeSeat!!)
        assertTrue(Texts.result(r).startsWith("Du hast Grand mit "))
    }
}
