package io.github.foxskat.core

/**
 * Computer player: rule-of-thumb bidding, skat handling and card play.
 *
 * It only looks at what the seat may know: its own hand, played cards, the Skat when it
 * is the declarer and the declarer's hand in an ouvert game.
 */
object Ai {

    // --- Hand evaluation -------------------------------------------------------------------

    /** How a candidate game looks for a hand of ten cards; margin ≥ 0 means playable. */
    data class Option(val contract: Contract, val margin: Double)

    private const val SUIT_THRESHOLD = 7.0
    private const val GRAND_THRESHOLD = 6.5
    private const val HAND_EXTRA = 3.0

    fun suitScore(hand: Collection<Card>, trump: Suit): Double {
        val c = Contract(GameKind.FARBE, trump)
        val trumps = hand.count { c.isTrump(it) }
        val jacks = hand.count { it.isJack }
        var score = trumps + jacks * 0.5
        if (Card(Suit.KREUZ, Rank.BUBE) in hand) score += 0.5
        for (s in Suit.entries) {
            if (s == trump) continue
            val side = hand.filter { it.suit == s && !it.isJack }.map { it.rank }
            if (side.isEmpty()) {
                if (trumps >= 5) score += 0.5
                continue
            }
            if (Rank.ASS in side) score += 1.0
            if (Rank.ZEHN in side) score += if (Rank.ASS in side) 0.75 else if (side.size == 1) -0.5 else 0.2
        }
        return score
    }

    fun grandScore(hand: Collection<Card>): Double {
        val jacks = hand.count { it.isJack }
        if (jacks < 2) return 0.0
        var score = jacks * 1.5
        if (Card(Suit.KREUZ, Rank.BUBE) in hand) score += 0.5
        for (s in Suit.entries) {
            val side = hand.filter { it.suit == s && !it.isJack }.map { it.rank }
            if (Rank.ASS in side) score += 1.2
            if (Rank.ZEHN in side) score += if (Rank.ASS in side) 0.8 else if (side.size == 1) -0.6 else 0.1
            // A long suit headed by Ass and Zehn brings extra tricks.
            if (Rank.ASS in side && Rank.ZEHN in side && side.size >= 4) score += 0.6
        }
        return score
    }

    /** Number of suits that can catch the declarer in a null game. */
    fun nullDangers(hand: Collection<Card>): List<Suit> = Suit.entries.filter { s ->
        val ranks = hand.filter { it.suit == s }.map { Contract.NULL_ORDER.indexOf(it.rank) }.sorted()
        ranks.withIndex().any { (i, r) -> r > 2 * i }
    }

    /** Candidate games for a ten-card hand, best first. */
    fun options(hand: Collection<Card>, vorhand: Boolean, hand10IsFinal: Boolean = false): List<Option> {
        val bonus = if (vorhand) 0.5 else 0.0
        val result = mutableListOf<Option>()
        for (s in Suit.entries) {
            result += Option(Contract(GameKind.FARBE, s), suitScore(hand, s) + bonus - SUIT_THRESHOLD)
        }
        result += Option(Contract(GameKind.GRAND), grandScore(hand) + bonus - GRAND_THRESHOLD)
        val dangers = nullDangers(hand)
        val nullMargin = when {
            dangers.isEmpty() -> 1.0
            hand10IsFinal -> -2.0
            dangers.size == 1 && hand.count { it.suit == dangers[0] } <= 2 -> 0.2
            else -> -2.0
        }
        result += Option(Contract(GameKind.NULL), nullMargin)
        return result.sortedByDescending { it.margin }
    }

    /** Added to every margin in bidding: above 0 the computer bids more boldly. */
    var boldness = 0.0

    /** Highest value this seat is willing to bid with its ten cards; 0 means pass. */
    fun maxBid(hand: Collection<Card>, vorhand: Boolean): Int {
        var best = 0
        for (o in options(hand, vorhand)) {
            if (o.margin + boldness < 0) continue
            val c = o.contract
            val value = if (c.kind == GameKind.NULL) {
                if (o.margin >= 1.0) 35 else 23
            } else {
                // The Skat can change "ohne" counts, so play one Spitze safer there.
                val spitzen = c.spitzen(hand)
                val safe = if (c.isMit(hand)) spitzen else maxOf(1, spitzen - 1)
                val handBonus = if (o.margin >= HAND_EXTRA) 1 else 0
                (safe + 1 + handBonus) * c.baseValue
            }
            best = maxOf(best, value)
        }
        return best
    }

    // --- Bidding ---------------------------------------------------------------------------

    fun actInBidding(round: Round, seat: Int) {
        val limit = maxBid(round.hand(seat), seat == round.vorhand)
        when {
            round.vorhandAlone -> if (limit >= 18) round.playFor18(seat) else round.pass(seat)
            round.awaitingAnswer -> round.answer(seat, hold = round.bid <= limit)
            else -> {
                val next = Contract.nextBid(round.bid)
                if (next != null && next <= limit) round.say(seat, next) else round.pass(seat)
            }
        }
    }

    // --- Skat and declaring ----------------------------------------------------------------

    fun decideSkat(round: Round, seat: Int) {
        val hand = round.hand(seat)
        val best = options(hand, seat == round.vorhand, hand10IsFinal = true).first()
        val value = valueOf(best.contract.copy(hand = true), hand)
        if (best.margin >= HAND_EXTRA && value >= round.bid) round.playHand(seat) else round.pickUpSkat(seat)
    }

    /** Chooses two cards to push and returns the contract to play with the rest. */
    fun discardAndChoose(hand12: List<Card>, bid: Int, vorhand: Boolean): Pair<List<Card>, Contract> {
        var best: Triple<List<Card>, Contract, Double>? = null
        val candidates = Suit.entries.map { Contract(GameKind.FARBE, it) } + Contract(GameKind.GRAND) + Contract(GameKind.NULL)
        for (c in candidates) {
            val reachable = valueOf(c, hand12) >= bid
            for (i in hand12.indices) for (j in i + 1 until hand12.size) {
                val pushed = listOf(hand12[i], hand12[j])
                val rest = hand12 - pushed.toSet()
                var score = when (c.kind) {
                    GameKind.FARBE -> suitScore(rest, c.trump!!) - SUIT_THRESHOLD + pushed.points() * 0.03
                    GameKind.GRAND -> grandScore(rest) - GRAND_THRESHOLD + pushed.points() * 0.03
                    GameKind.NULL -> if (nullDangers(rest).isEmpty()) 0.8 else -3.0
                }
                if (c.kind != GameKind.NULL && pushed.any { c.isTrump(it) }) score -= 0.5
                if (vorhand) score += 0.5
                if (!reachable) score -= 10.0
                // Between playable games prefer the one worth more.
                if (score >= 0) score += valueOf(c, hand12) * 0.03
                if (best == null || score > best.third) best = Triple(pushed, c, score)
            }
        }
        return best!!.first to best.second
    }

    private fun valueOf(c: Contract, cards: Collection<Card>): Int =
        if (c.kind == GameKind.NULL) c.baseValue else c.minimumValue(cards)

    fun handleDiscardAndDeclare(round: Round, seat: Int) {
        if (round.phase == Phase.DISCARD) {
            val (pushed, contract) = discardAndChoose(round.hand(seat), round.bid, seat == round.vorhand)
            round.discard(seat, pushed)
            round.declare(seat, contract)
        } else {
            val best = options(round.hand(seat), seat == round.vorhand, hand10IsFinal = true)
                .map { it.contract.copy(hand = true) }
                .firstOrNull { round.declareProblem(it) == null && valueOf(it, round.hand(seat)) >= round.bid }
                ?: Contract(GameKind.GRAND, hand = true)
            round.declare(seat, best)
        }
    }

    // --- Kontra and Re ----------------------------------------------------------------------

    /** Defender hand strength at or above which the computer gives Kontra. */
    var kontraThreshold = 5.0

    /** Margin of the declarer's game at or above which the computer answers with Re. */
    var reMargin = 2.5

    /** Roughly the tricks a defender's ten cards take against [c]; 0 for null. */
    fun defenceScore(hand: Collection<Card>, c: Contract): Double {
        if (c.kind == GameKind.NULL) return 0.0
        val trumps = hand.filter { c.isTrump(it) }
        var score = trumps.size * 0.8
        // High trumps catch the declarer's trumps; a long trump suit wears his down.
        score += trumps.count { it.isJack && it.suit.ordinal <= 1 } * 0.5
        if (trumps.size >= 4) score += 0.5
        for (s in Suit.entries) {
            val side = hand.filter { it.suit == s && !c.isTrump(it) }.map { it.rank }
            if (side.isEmpty()) continue
            if (Rank.ASS in side) score += 1.0
            if (Rank.ZEHN in side) score += if (Rank.ASS in side) 0.7 else if (side.size >= 3) 0.3 else 0.0
        }
        return score
    }

    /** Kontra for a defender, Re for the declarer. */
    fun decideKontra(round: Round, seat: Int, threshold: Double = kontraThreshold): Boolean {
        val c = round.contract!!
        val hand = round.hand(seat)
        if (seat != round.declarer) {
            val handGame = if (c.hand) 0.5 else 0.0
            return c.kind != GameKind.NULL && defenceScore(hand, c) >= threshold + handGame
        }
        return when (c.kind) {
            GameKind.NULL -> nullDangers(hand).isEmpty()
            GameKind.FARBE -> suitScore(hand, c.trump!!) - SUIT_THRESHOLD >= reMargin
            GameKind.GRAND -> grandScore(hand) - GRAND_THRESHOLD >= reMargin
        }
    }

    // --- Card play -------------------------------------------------------------------------

    fun chooseCard(round: Round, seat: Int): Card {
        val legal = round.legalCards(seat)
        if (legal.size == 1) return legal.single()
        val k = Knowledge(round, seat)
        return when {
            round.isRamsch -> playRamsch(round.contract!!, round.currentTrick, legal)
            round.contract!!.kind == GameKind.NULL -> playNull(k, legal)
            else -> playTrumpGame(k, legal)
        }
    }

    /** What [seat] can know about the cards it does not see. */
    private class Knowledge(val round: Round, val seat: Int) {
        val c: Contract = round.contract!!
        val declarer = round.declarer
        val isDeclarer = seat == declarer
        val trick = round.currentTrick
        val hand = round.hand(seat)

        /** Cards that may still be in another player's hand. */
        val unknown: Set<Card> = buildSet {
            addAll(Card.DECK)
            removeAll(hand.toSet())
            removeAll(round.playedCards.toSet())
            if (isDeclarer) removeAll(round.skat.toSet())
        }

        /** Suits (null = trump) a seat has shown it cannot follow. */
        fun voids(of: Int): Set<Suit?> = buildSet {
            for (t in round.tricks) {
                val led = c.followSuit(t.cards[0])
                for (i in 1..2) if (t.seatOf(i) == of && c.followSuit(t.cards[i]) != led) add(led)
            }
            val led = trick.firstOrNull()?.let { c.followSuit(it) }
            for (i in 1 until trick.size) {
                if ((round.trickLeader + i) % 3 == of && c.followSuit(trick[i]) != led) add(led)
            }
        }

        val openDeclarerHand: List<Card>? =
            if (c.ouvert && !isDeclarer) round.hand(declarer) else null

        fun isTop(card: Card): Boolean {
            val key = c.followSuit(card)
            return unknown.none { c.followSuit(it) == key && c.strength(it) > c.strength(card) }
        }

        fun seatAfter(offset: Int) = (seat + offset) % 3
    }

    private fun playTrumpGame(k: Knowledge, legal: List<Card>): Card {
        val c = k.c
        if (k.trick.isEmpty()) return if (k.isDeclarer) leadAsDeclarer(k, legal) else leadAsDefender(k, legal)

        val winIdx = c.trickWinnerIndex(k.trick)
        val winnerSeat = (k.round.trickLeader + winIdx) % 3
        val winning = k.trick[winIdx]
        val trickPoints = k.trick.points()
        val laterSeats = (1..(2 - k.trick.size)).map { k.seatAfter(it) }
        val partnerWinning = !k.isDeclarer && winnerSeat != k.declarer
        val winners = legal.filter { c.beats(it, winning) }

        if (partnerWinning) {
            // Safe if the declarer has already played or cannot beat the partner's card.
            val key = c.followSuit(winning)
            val declarerMayTrump = key != null && key in k.voids(k.declarer) && k.unknown.any { c.isTrump(it) }
            val safe = k.declarer !in laterSeats || (k.isTop(winning) && !declarerMayTrump)
            return if (safe) smear(c, legal) else cheapest(c, legal)
        }

        if (winners.isNotEmpty()) {
            val cheapestWinner = winners.minWith(compareBy<Card> { c.isTrump(it) }.thenBy { c.strength(it) }.thenBy { it.points })
            if (laterSeats.isEmpty()) {
                return if (trickPoints == 0 && cheapestWinner.isJack) cheapest(c, legal) else cheapestWinner
            }
            // The declarer has played already: whatever we take stays with our side.
            val declarerDone = !k.isDeclarer && k.declarer !in laterSeats
            if (declarerDone && (trickPoints >= 3 || !c.isTrump(cheapestWinner))) return cheapestWinner
            val sureWinners = winners.filter { k.isTop(it) }
            if (sureWinners.isNotEmpty()) return sureWinners.minBy { c.strength(it) }
            val partnerAfter = !k.isDeclarer && laterSeats.any { it != k.declarer }
            if (partnerAfter) return cheapest(c, legal)
            if (trickPoints >= 10) return winners.maxBy { c.strength(it) }
        }
        return cheapest(c, legal)
    }

    private fun leadAsDeclarer(k: Knowledge, legal: List<Card>): Card {
        val c = k.c
        val myTrumps = legal.filter { c.isTrump(it) }
        val trumpsOut = k.unknown.count { c.isTrump(it) }
        if (myTrumps.isNotEmpty() && trumpsOut > 0) {
            val top = myTrumps.maxBy { c.strength(it) }
            if (k.isTop(top)) return top
            if (myTrumps.size > trumpsOut) return myTrumps.minWith(compareBy<Card> { it.points }.thenBy { c.strength(it) })
        }
        val side = legal.filter { !c.isTrump(it) }
        if (side.isEmpty()) return legal.minBy { c.strength(it) }
        val defenderVoids = (0..2).filter { it != k.seat }.flatMap { k.voids(it) }.toSet()
        val safeTops = side.filter { k.isTop(it) && (trumpsOut == 0 || it.suit !in defenderVoids) }
        if (safeTops.isNotEmpty()) return safeTops.maxBy { it.points }
        // Make a suit short so it can be trumped later.
        val bySuit = side.groupBy { it.suit }
        val shortest = bySuit.values.minWith(compareBy<List<Card>> { it.size }.thenBy { it.sumOf { card -> card.points } })
        return shortest.minWith(compareBy<Card> { it.points }.thenBy { c.strength(it) })
    }

    private fun leadAsDefender(k: Knowledge, legal: List<Card>): Card {
        val c = k.c
        val declarerVoids = k.voids(k.declarer)
        val side = legal.filter { !c.isTrump(it) }
        if (side.isEmpty()) return cheapest(c, legal)
        val aces = side.filter { k.isTop(it) && it.suit !in declarerVoids && it.points >= 10 }
        if (aces.isNotEmpty()) return aces.maxBy { it.points }
        val notIntoVoid = side.filter { it.suit !in declarerVoids }.ifEmpty { side }
        val bySuit = notIntoVoid.groupBy { it.suit }
        // "Kurzer Weg, lange Farbe; langer Weg, kurze Farbe."
        val declarerNext = k.seatAfter(1) == k.declarer
        val suitCards = if (declarerNext) bySuit.values.maxBy { it.size } else bySuit.values.minBy { it.size }
        return suitCards.minWith(compareBy<Card> { it.points >= 10 }.thenBy { c.strength(it) })
    }

    /** Lowest-value card, keeping trumps and high cards. */
    private fun cheapest(c: Contract, legal: List<Card>): Card =
        legal.minWith(compareBy<Card> { c.isTrump(it) }.thenBy { it.points }.thenBy { c.strength(it) })

    /** Most points to a trick the own side wins, without spending trumps if possible. */
    private fun smear(c: Contract, legal: List<Card>): Card =
        legal.maxWith(compareBy<Card> { !c.isTrump(it) }.thenBy { it.points }.thenBy { -c.strength(it) })

    private fun playNull(k: Knowledge, legal: List<Card>): Card {
        val c = k.c
        if (k.isDeclarer) {
            if (k.trick.isEmpty()) {
                return legal.minBy { c.strength(it) }
            }
            val winning = k.trick[c.trickWinnerIndex(k.trick)]
            val under = legal.filter { !c.beats(it, winning) }
            if (under.isEmpty()) return legal.maxBy { c.strength(it) }
            val followed = under.filter { it.suit == k.trick[0].suit }
            // Following: play just under the winner. Void: drop the most dangerous card.
            return if (followed.isNotEmpty()) followed.maxBy { c.strength(it) } else under.maxBy { c.strength(it) }
        }

        val declarerVoids = k.voids(k.declarer)
        val open = k.openDeclarerHand
        if (k.trick.isEmpty()) {
            val target = legal.filter { it.suit !in declarerVoids && (open == null || open.any { o -> o.suit == it.suit }) }
                .ifEmpty { legal }
            return target.minBy { c.strength(it) }
        }
        val winIdx = c.trickWinnerIndex(k.trick)
        val winnerSeat = (k.round.trickLeader + winIdx) % 3
        val winning = k.trick[winIdx]
        val declarerStillToPlay = k.trick.size == 1 && k.seatAfter(1) == k.declarer
        return when {
            winnerSeat == k.declarer -> {
                val under = legal.filter { !c.beats(it, winning) }
                if (under.isNotEmpty()) under.maxBy { c.strength(it) } else legal.minBy { c.strength(it) }
            }
            declarerStillToPlay -> legal.minBy { c.strength(it) }
            else -> legal.maxBy { c.strength(it) }
        }
    }

    /**
     * Ramsch: take as few points as possible. Stay under the winning card and drop high
     * cards there; lead low cards; when a trick cannot be avoided, take it cheaply.
     */
    private fun playRamsch(c: Contract, trick: List<Card>, legal: List<Card>): Card {
        if (trick.isEmpty()) {
            return legal.minWith(compareBy<Card> { c.isTrump(it) }.thenBy { c.strength(it) }.thenBy { it.points })
        }
        val winning = trick[c.trickWinnerIndex(trick)]
        val under = legal.filter { !c.beats(it, winning) }
        if (under.isNotEmpty()) return under.maxWith(compareBy<Card> { it.points }.thenBy { c.strength(it) })
        return if (trick.size == 2) {
            legal.minWith(compareBy<Card> { it.points }.thenBy { c.strength(it) })
        } else {
            legal.minWith(compareBy<Card> { c.strength(it) }.thenBy { it.points })
        }
    }

    /** Lets the computer act for [seat] once; returns false if nothing was to do. */
    fun act(round: Round, seat: Int): Boolean {
        if (round.activeSeat != seat) return false
        when (round.phase) {
            Phase.BIDDING -> actInBidding(round, seat)
            Phase.SKAT_DECISION -> decideSkat(round, seat)
            Phase.DISCARD, Phase.DECLARE -> handleDiscardAndDeclare(round, seat)
            Phase.PLAYING -> if (round.kontraDecider == seat) {
                round.kontra(seat, decideKontra(round, seat))
            } else {
                round.play(seat, chooseCard(round, seat))
            }
            Phase.FINISHED, Phase.PASSED_IN -> return false
        }
        return true
    }
}
