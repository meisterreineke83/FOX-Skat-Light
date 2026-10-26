package io.github.foxskat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays the same deals twice: once with the current computer player in one seat against two
 * old ones, once with old players everywhere. The difference in that seat's list total shows
 * how much stronger the new player is, with the cards' luck taken out.
 */
class StrengthTest {

    private fun act(round: Round, seat: Int, newSeat: Int?) {
        if (round.kontraDecider == seat) {
            round.kontra(seat, if (seat == newSeat) Ai.decideKontra(round, seat) else Ai.decideKontra(round, seat, OLD_KONTRA))
            return
        }
        if (seat == newSeat) Ai.act(round, seat) else AiOld.act(round, seat)
    }

    /** List total of [seat] minus the average of the others, over [deals] deals per dealer. */
    private fun play(deals: Int, seat: Int, newSeat: Int?): Double {
        var sheet = ScoreSheet()
        repeat(deals) { i ->
            for (dealer in 0..2) {
                val r = Round(dealer, Card.DECK.shuffled(Random(77_000 + i)))
                while (r.activeSeat != null) act(r, r.activeSeat!!, newSeat)
                sheet = sheet.add(r)
            }
        }
        val others = (0..2).filter { it != seat }
        return sheet.total(seat) - others.sumOf { sheet.total(it) } / 2.0
    }

    private companion object {
        /** The Kontra threshold the old players keep. */
        const val OLD_KONTRA = 5.0
    }

    @Test
    fun newComputerBeatsOldOne() {
        System.getenv("BOLD")?.let { Ai.boldness = it.toDouble() }
        System.getenv("KT")?.let { Ai.kontraThreshold = it.toDouble() }
        val deals = 1500
        var gain = 0.0
        for (seat in 0..2) gain += play(deals, seat, newSeat = seat) - play(deals, seat, newSeat = null)
        val perGame = gain / (3 * 3 * deals)
        println("Neuer Computer gegen alten: %+.2f Punkte je Spiel".format(perGame))
        assertTrue(perGame > -0.5, "Neuer Computer spielt schwächer als der alte: $perGame")
    }
}
