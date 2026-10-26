package io.github.foxskat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SaveTest {

    /** Everything the table shows, to compare a saved and a restored round. */
    private fun snapshot(r: Round): List<Any?> = listOf(
        r.phase, r.activeSeat, r.bid, r.declarer, r.contract, r.biddingLog.toList(), r.kontraBy, r.re,
        (0..2).map { r.hand(it) }, r.skat, r.tricks.toList(), r.currentTrick.toList(), r.settlement, r.ramschResult,
    )

    @Test
    fun restoredRoundMatchesAfterEveryStep() {
        repeat(200) { i ->
            val r = Round.shuffled(dealer = i % 3, random = Random(500 + i))
            while (true) {
                val restored = assertNotNull(Round.decode(r.encode()), "Runde $i lässt sich nicht laden")
                assertEquals(snapshot(r), snapshot(restored))
                val seat = r.activeSeat ?: break
                Ai.act(r, seat)
            }
        }
    }

    @Test
    fun restoredRoundPlaysOn() {
        val r = Round.shuffled(dealer = 0, random = Random(7))
        repeat(12) { Ai.act(r, r.activeSeat!!) }
        val restored = Round.decode(r.encode())!!
        while (restored.activeSeat != null) Ai.act(restored, restored.activeSeat!!)
        while (r.activeSeat != null) Ai.act(r, r.activeSeat!!)
        assertEquals(snapshot(r), snapshot(restored))
    }

    @Test
    fun damagedTextGivesNoRound() {
        assertNull(Round.decode(""))
        assertNull(Round.decode("runde1\n0\n♣B ♠B\n"))
        val r = Round.shuffled(dealer = 1, random = Random(3))
        Ai.act(r, r.activeSeat!!)
        assertNull(Round.decode(r.encode() + ";sag 0 18"))
        assertNull(Round.decode(r.encode() + ";play 0 ♣B ♠B"), "Zug, der nicht erlaubt ist")
    }
}
