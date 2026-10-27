package io.github.foxskat.core

/**
 * What the computer players have learned from the games at this table. After every game they
 * adjust how boldly they bid and how readily they give Kontra:
 *
 * - A game a computer player wins as declarer makes them a little bolder, a lost one clearly
 *   more careful. That settles where they win about four games out of five.
 * - A Kontra that the declarer still wins makes them more careful with Kontra; a Kontra that
 *   beats the declarer, or a game you lose without anybody giving Kontra, makes them readier.
 */
data class Experience(
    val games: Int = 0,
    val boldness: Double = Ai.DEFAULT_BOLDNESS,
    val kontraThreshold: Double = Ai.DEFAULT_KONTRA_THRESHOLD,
) {

    /** Applies the learned values to the computer players. */
    fun apply() {
        Ai.boldness = boldness
        Ai.kontraThreshold = kontraThreshold
    }

    /** Learns from one finished round in which [human] sat at the table. */
    fun learn(round: Round, human: Int): Experience {
        val s = round.settlement ?: return this
        var bold = boldness
        var kontra = kontraThreshold
        val computerDeclared = round.declarer != human
        if (computerDeclared) bold += if (s.won) BOLDER else -CAREFUL
        val computerKontra = round.kontraBy >= 0 && round.kontraBy != human
        when {
            computerKontra -> kontra += if (s.won) KONTRA_STEP else -KONTRA_STEP
            !computerDeclared && round.kontraBy < 0 && !s.won -> kontra -= KONTRA_STEP / 2
        }
        return Experience(
            games = games + 1,
            boldness = bold.coerceIn(MIN_BOLDNESS, MAX_BOLDNESS),
            kontraThreshold = kontra.coerceIn(MIN_KONTRA, MAX_KONTRA),
        )
    }

    fun encode(): String = "erfahrung1|$games|$boldness|$kontraThreshold"

    companion object {
        private const val BOLDER = 0.02
        private const val CAREFUL = 0.08
        private const val KONTRA_STEP = 0.1
        private const val MIN_BOLDNESS = -1.0
        private const val MAX_BOLDNESS = 1.5
        private const val MIN_KONTRA = 3.5
        private const val MAX_KONTRA = 7.0

        fun decode(text: String?): Experience {
            val p = text?.split('|') ?: return Experience()
            if (p.size != 4 || p[0] != "erfahrung1") return Experience()
            return Experience(
                games = p[1].toIntOrNull() ?: return Experience(),
                boldness = p[2].toDoubleOrNull() ?: return Experience(),
                kontraThreshold = p[3].toDoubleOrNull() ?: return Experience(),
            )
        }
    }
}
