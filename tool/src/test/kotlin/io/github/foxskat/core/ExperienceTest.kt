package io.github.foxskat.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperienceTest {

    @Test
    fun savesAndLoads() {
        val e = Experience(games = 12, boldness = 0.27, kontraThreshold = 4.6)
        assertEquals(e, Experience.decode(e.encode()))
        assertEquals(Experience(), Experience.decode(null))
        assertEquals(Experience(), Experience.decode("kaputt"))
    }

    /** Computer players at all three seats learn together; their values must settle in range. */
    @Test
    fun learningSettles() {
        var e = Experience()
        var sheet = ScoreSheet()
        Lookahead.random = Random(11)
        try {
            repeat(600) { i ->
                e.apply()
                val r = Round(sheet.nextDealer, Card.DECK.shuffled(Random(9000 + i)))
                while (r.activeSeat != null) Ai.act(r, r.activeSeat!!)
                // Seat 0 counts as the person here; it plays like the computer.
                e = e.learn(r, human = 0)
                sheet = sheet.add(r)
            }
            println("Gelernt nach ${e.games} Spielen: Mut %.2f, Kontra ab %.2f".format(e.boldness, e.kontraThreshold))
            assertTrue(e.boldness in -1.0..1.5)
            assertTrue(e.kontraThreshold in 3.5..7.0)
        } finally {
            Experience().apply()
        }
    }
}
