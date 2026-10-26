package io.github.foxskat.core

import kotlin.random.Random

/**
 * Card play by thinking ahead: the computer deals the cards it cannot see many times over, in
 * ways that fit everything the table has shown (who could not follow which suit), plays each
 * deal to the end with quick rules and picks the card that does best on average.
 *
 * It still only uses what the seat may know; the guessed deals stand in for the hidden cards.
 */
object Lookahead {

    /** Guessed deals per decision; more is stronger and slower. */
    var samples = 50

    var random: Random = Random.Default

    /** The cards the others hold in one guessed deal, plus the Skat. */
    class World(val hands: List<MutableList<Card>>, val skat: List<Card>)

    fun chooseCard(round: Round, seat: Int, legal: List<Card>): Card {
        val worlds = List(samples) { guessWorld(round, seat) }
        var best = legal.first()
        var bestScore = Double.NEGATIVE_INFINITY
        for (card in legal) {
            val score = worlds.sumOf { playOut(round, seat, it, card) }
            if (score > bestScore) {
                bestScore = score
                best = card
            }
        }
        return best
    }

    // --- Guessing the hidden cards ---------------------------------------------------------

    /** Suits (null = trump) each seat has shown it cannot follow. */
    fun voids(round: Round): List<Set<Suit?>> {
        val c = round.contract!!
        val result = List(3) { mutableSetOf<Suit?>() }
        val tricks = round.tricks.map { it.leader to it.cards } + (round.trickLeader to round.currentTrick)
        for ((leader, cards) in tricks) {
            if (cards.isEmpty()) continue
            val led = c.followSuit(cards[0])
            for (i in 1 until cards.size) if (c.followSuit(cards[i]) != led) result[(leader + i) % 3] += led
        }
        return result
    }

    fun guessWorld(round: Round, seat: Int): World {
        val c = round.contract!!
        val declarer = round.declarer
        val hands = List(3) { mutableListOf<Card>() }
        hands[seat] += round.hand(seat)
        val openDeclarer = c.ouvert && seat != declarer
        if (openDeclarer) hands[declarer] += round.hand(declarer)
        val knowsSkat = seat == declarer
        val pool = Card.DECK.toMutableList().apply {
            removeAll(round.playedCards.toSet())
            removeAll(hands.flatten().toSet())
            if (knowsSkat) removeAll(round.skat.toSet())
        }
        val others = (0..2).filter { it != seat && !(openDeclarer && it == declarer) }
        val need = IntArray(3) { round.hand(it).size - hands[it].size }
        val voids = voids(round)
        repeat(20) {
            val tryHands = others.associateWith { mutableListOf<Card>() }
            val skat = mutableListOf<Card>()
            val skatRoom = if (knowsSkat) 0 else 2
            var ok = true
            for (card in pool.shuffled(random)) {
                val key = c.followSuit(card)
                val fits = others.filter { tryHands[it]!!.size < need[it] && key !in voids[it] }
                val roomInSkat = skat.size < skatRoom
                val choices = fits.size + if (roomInSkat) 1 else 0
                if (choices == 0) {
                    ok = false
                    break
                }
                val pick = random.nextInt(choices)
                if (pick < fits.size) tryHands[fits[pick]]!! += card else skat += card
            }
            if (ok) {
                for ((s, cards) in tryHands) hands[s] += cards
                return World(hands, if (knowsSkat) round.skat else skat)
            }
        }
        // The voids could not be honoured (very rare): deal without them.
        val rest = pool.shuffled(random).toMutableList()
        for (s in others) repeat(need[s]) { hands[s] += rest.removeAt(rest.lastIndex) }
        return World(hands, if (knowsSkat) round.skat else rest)
    }

    // --- Playing a guessed deal to the end -------------------------------------------------

    /** How good the end of the game is for [seat] after it plays [first] in [world]. */
    private fun playOut(round: Round, seat: Int, world: World, first: Card): Double {
        val c = round.contract!!
        val declarer = round.declarer
        val hands = world.hands.map { it.toMutableList() }
        val trick = round.currentTrick.toMutableList()
        var leader = round.trickLeader
        var declarerPoints = round.tricksOf(declarer).sumOf { it.cards.points() }
        var declarerTricks = round.tricksOf(declarer).size
        var tricksLeft = 10 - round.tricks.size
        var toPlay = seat
        var card = first
        while (true) {
            hands[toPlay] -= card
            trick += card
            if (trick.size == 3) {
                val winner = (leader + c.trickWinnerIndex(trick)) % 3
                if (winner == declarer) {
                    declarerPoints += trick.points()
                    declarerTricks++
                    if (c.kind == GameKind.NULL) break
                }
                trick.clear()
                leader = winner
                if (--tricksLeft == 0) break
            }
            toPlay = (leader + trick.size) % 3
            card = quickCard(c, declarer, hands, trick, leader, toPlay)
        }
        val forDeclarer = if (c.kind == GameKind.NULL) {
            if (declarerTricks == 0) 100.0 else 0.0
        } else {
            val p = declarerPoints + world.skat.points()
            var u = p.toDouble()
            if (p >= 61) u += 40
            if (p >= 90) u += 15
            if (p <= 30) u -= 15
            if (c.schwarzAngesagt && declarerTricks < 10) u -= 60
            if (c.schneiderAngesagt && p < 90) u -= 60
            u
        }
        return if (seat == declarer) forDeclarer else -forDeclarer
    }

    /** Quick rules for the play-out, where every hand is visible. */
    private fun quickCard(
        c: Contract,
        declarer: Int,
        hands: List<List<Card>>,
        trick: List<Card>,
        leader: Int,
        seat: Int,
    ): Card {
        val legal = c.legalCards(hands[seat], trick)
        if (legal.size == 1) return legal[0]
        return if (c.kind == GameKind.NULL) quickNull(c, declarer, hands, trick, leader, seat, legal)
        else quickTrump(c, declarer, hands, trick, leader, seat, legal)
    }

    private fun sameSide(a: Int, b: Int, declarer: Int) = (a == declarer) == (b == declarer)

    /** Seats still to play after [seat] in this trick. */
    private fun laterSeats(trick: List<Card>, seat: Int): List<Int> = (1..(2 - trick.size)).map { (seat + it) % 3 }

    /** True if one of the opponents still to play can beat [card] once it lies in the trick. */
    private fun opponentCanBeat(
        c: Contract, declarer: Int, hands: List<List<Card>>, trick: List<Card>, seat: Int, card: Card, winning: Card,
    ): Boolean {
        val after = trick + card
        val best = if (c.beats(card, winning)) card else winning
        return laterSeats(trick, seat).any { s ->
            !sameSide(s, seat, declarer) && c.legalCards(hands[s], after).any { c.beats(it, best) }
        }
    }

    private fun cheapest(c: Contract, cards: List<Card>): Card =
        cards.minWith(compareBy<Card> { c.isTrump(it) }.thenBy { it.points }.thenBy { c.strength(it) })

    private fun quickTrump(
        c: Contract, declarer: Int, hands: List<List<Card>>, trick: List<Card>, leader: Int, seat: Int, legal: List<Card>,
    ): Card {
        val othersCards = (0..2).filter { it != seat }.flatMap { hands[it] }
        fun isTop(card: Card): Boolean {
            val key = c.followSuit(card)
            return othersCards.none { c.followSuit(it) == key && c.strength(it) > c.strength(card) }
        }
        if (trick.isEmpty()) {
            val opponents = (0..2).filter { !sameSide(it, seat, declarer) }
            val opponentTrumps = opponents.sumOf { s -> hands[s].count { c.isTrump(it) } }
            if (seat == declarer && opponentTrumps > 0) {
                val trumps = legal.filter { c.isTrump(it) }
                trumps.filter(::isTop).maxByOrNull { c.strength(it) }?.let { return it }
                if (trumps.size > opponentTrumps) return cheapest(c, trumps)
            }
            val safeTops = legal.filter { card ->
                !c.isTrump(card) && isTop(card) &&
                    opponents.none { s -> hands[s].none { c.followSuit(it) == c.followSuit(card) } && hands[s].any { c.isTrump(it) } }
            }
            safeTops.maxByOrNull { it.points }?.let { return it }
            return cheapest(c, legal)
        }
        val winIdx = c.trickWinnerIndex(trick)
        val winnerSeat = (leader + winIdx) % 3
        val winning = trick[winIdx]
        val ownSideWinning = sameSide(winnerSeat, seat, declarer)
        if (ownSideWinning &&
            laterSeats(trick, seat).none { s ->
                !sameSide(s, seat, declarer) && c.legalCards(hands[s], trick).any { c.beats(it, winning) }
            }
        ) {
            // Safe: give as many points as possible, keeping trumps.
            return legal.maxWith(compareBy<Card> { !c.isTrump(it) }.thenBy { it.points }.thenBy { -c.strength(it) })
        }
        val sureWinners = legal.filter { c.beats(it, winning) && !opponentCanBeat(c, declarer, hands, trick, seat, it, winning) }
        if (sureWinners.isNotEmpty()) {
            return sureWinners.minWith(compareBy<Card> { c.isTrump(it) }.thenBy { c.strength(it) })
        }
        return cheapest(c, legal)
    }

    private fun quickNull(
        c: Contract, declarer: Int, hands: List<List<Card>>, trick: List<Card>, leader: Int, seat: Int, legal: List<Card>,
    ): Card {
        if (trick.isEmpty()) return legal.minBy { c.strength(it) }
        val winIdx = c.trickWinnerIndex(trick)
        val winning = trick[winIdx]
        val winnerSeat = (leader + winIdx) % 3
        val under = legal.filter { !c.beats(it, winning) }
        if (seat == declarer) {
            return if (under.isNotEmpty()) under.maxBy { c.strength(it) } else legal.maxBy { c.strength(it) }
        }
        val declarerAfter = declarer in laterSeats(trick, seat)
        return when {
            winnerSeat == declarer -> if (under.isNotEmpty()) under.maxBy { c.strength(it) } else legal.minBy { c.strength(it) }
            declarerAfter -> legal.minBy { c.strength(it) }
            else -> legal.maxBy { c.strength(it) }
        }
    }
}
