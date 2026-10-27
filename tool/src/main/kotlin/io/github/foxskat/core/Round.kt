package io.github.foxskat.core

import kotlin.random.Random

enum class Phase { BIDDING, SKAT_DECISION, DISCARD, DECLARE, PLAYING, FINISHED, PASSED_IN }

/** A finished trick: the seat that led, the three cards in play order and who took it. */
data class Trick(val leader: Int, val cards: List<Card>, val winner: Int) {
    fun seatOf(index: Int): Int = (leader + index) % 3
}

/**
 * One deal of Skat for three seats (0, 1, 2), played clockwise 0 → 1 → 2.
 *
 * The dealer sits in Hinterhand; Vorhand is the next seat and leads the first trick.
 * Every action checks that it is legal and that the right seat is acting.
 */
class Round(val dealer: Int, deck: List<Card>) {

    init {
        require(deck.size == 32 && deck.toSet().size == 32) { "Ein Skatblatt hat 32 verschiedene Karten" }
    }

    private val deck: List<Card> = deck.toList()

    /** Every action so far in a short text form, so the round can be saved and replayed. */
    private val log = mutableListOf<String>()

    val vorhand = (dealer + 1) % 3
    val mittelhand = (dealer + 2) % 3
    val hinterhand = dealer

    // Dealt 3 – Skat – 4 – 3, as at the table; the order does not matter for the game.
    private val hands: List<MutableList<Card>> = List(3) { seat ->
        val offset = seat * 10
        deck.subList(offset, offset + 10).toMutableList()
    }
    private val dealtHands: List<List<Card>> = hands.map { it.toList() }
    private val originalSkat: List<Card> = deck.subList(30, 32).toList()
    private val skatCards: MutableList<Card> = originalSkat.toMutableList()

    var phase: Phase = Phase.BIDDING
        private set

    // --- Bidding -------------------------------------------------------------------------

    /** Highest bid so far, 0 while nobody has bid. */
    var bid: Int = 0
        private set

    /** The seat that names values. */
    var bidder: Int = mittelhand
        private set

    /** The seat that answers with "ja" or "passe". */
    var listener: Int = vorhand
        private set

    /** True while [listener] has to answer [bid]. */
    var awaitingAnswer: Boolean = false
        private set

    private var biddingStage = 1

    /** In the last stage Vorhand alone decides whether to play for 18. */
    val vorhandAlone: Boolean get() = phase == Phase.BIDDING && biddingStage == 3

    /** Seat whose turn it is in bidding, skat handling, declaring or playing. */
    val activeSeat: Int?
        get() = when (phase) {
            Phase.BIDDING -> when {
                biddingStage == 3 -> vorhand
                awaitingAnswer -> listener
                else -> bidder
            }
            Phase.SKAT_DECISION, Phase.DISCARD, Phase.DECLARE -> declarer
            Phase.PLAYING -> kontraDecider ?: ((trickLeader + currentTrick.size) % 3)
            Phase.FINISHED, Phase.PASSED_IN -> null
        }

    /** Log of what each seat said, for display. */
    val biddingLog: MutableList<Pair<Int, String>> = mutableListOf()

    fun say(seat: Int, value: Int) {
        check(phase == Phase.BIDDING && !awaitingAnswer && biddingStage < 3 && seat == bidder) { "Nicht am Reizen" }
        require(value > bid && value in Contract.BID_VALUES) { "Ungültiger Reizwert $value" }
        bid = value
        awaitingAnswer = true
        biddingLog += seat to value.toString()
        log += "say $seat $value"
    }

    fun answer(seat: Int, hold: Boolean) {
        check(phase == Phase.BIDDING && awaitingAnswer && seat == listener) { "Nicht am Antworten" }
        awaitingAnswer = false
        biddingLog += seat to if (hold) "ja" else "passe"
        log += "ans $seat ${if (hold) 1 else 0}"
        if (!hold) endStage(winner = bidder)
    }

    fun pass(seat: Int) {
        check(phase == Phase.BIDDING && seat == activeSeat) { "Nicht am Zug" }
        if (biddingStage < 3 && awaitingAnswer) {
            answer(seat, hold = false)
            return
        }
        log += "pass $seat"
        biddingLog += seat to "passe"
        if (biddingStage == 3) {
            startRamsch()
            return
        }
        endStage(winner = listener)
    }

    /** Vorhand plays for 18 after both others passed without a bid. */
    fun playFor18(seat: Int) {
        check(phase == Phase.BIDDING && biddingStage == 3 && seat == vorhand) { "Nicht am Zug" }
        bid = 18
        biddingLog += seat to "18"
        log += "p18 $seat"
        startDeclaring(vorhand)
    }

    private fun endStage(winner: Int) {
        if (biddingStage == 1) {
            biddingStage = 2
            bidder = hinterhand
            listener = winner
        } else if (bid == 0) {
            biddingStage = 3
        } else {
            startDeclaring(winner)
        }
    }

    // --- Ramsch -----------------------------------------------------------------------------

    /** True if all three passed: everyone plays alone, jacks are trump, fewest points wins. */
    var isRamsch: Boolean = false
        private set

    private fun startRamsch() {
        isRamsch = true
        contract = Contract(GameKind.GRAND)
        phase = Phase.PLAYING
        trickLeader = vorhand
    }

    var ramschResult: RamschResult? = null
        private set

    // --- Skat and declaring ----------------------------------------------------------------

    var declarer: Int = -1
        private set

    var pickedUpSkat: Boolean = false
        private set

    var contract: Contract? = null
        private set

    private fun startDeclaring(seat: Int) {
        declarer = seat
        phase = Phase.SKAT_DECISION
    }

    fun playHand(seat: Int) {
        check(phase == Phase.SKAT_DECISION && seat == declarer)
        log += "hand $seat"
        phase = Phase.DECLARE
    }

    fun pickUpSkat(seat: Int) {
        check(phase == Phase.SKAT_DECISION && seat == declarer)
        log += "skat $seat"
        pickedUpSkat = true
        hands[seat] += skatCards
        skatCards.clear()
        phase = Phase.DISCARD
    }

    fun discard(seat: Int, cards: List<Card>) {
        check(phase == Phase.DISCARD && seat == declarer)
        require(cards.size == 2 && cards.toSet().size == 2 && hands[seat].containsAll(cards)) { "Zwei eigene Karten drücken" }
        log += "drk $seat ${cards.joinToString(",")}"
        hands[seat] -= cards.toSet()
        skatCards += cards
        phase = Phase.DECLARE
    }

    /** Cards the declarer owns for Spitzen: the ten dealt cards plus the original Skat. */
    val declarerCards: List<Card>
        get() = if (declarer < 0) emptyList() else dealtHands[declarer] + originalSkat

    /** Why a contract is not allowed right now, or null if it is. */
    fun declareProblem(contract: Contract): String? = when {
        contract.hand != !pickedUpSkat ->
            if (pickedUpSkat) "Nach dem Skataufnehmen gibt es kein Handspiel" else "Ohne Skat ist es ein Handspiel"
        contract.kind == GameKind.NULL && contract.baseValue < bid -> "${contract.name} ist nur ${contract.baseValue} wert"
        else -> null
    }

    fun declare(seat: Int, contract: Contract) {
        check(phase == Phase.DECLARE && seat == declarer)
        declareProblem(contract)?.let { throw IllegalArgumentException(it) }
        log += "dcl $seat ${encodeContract(contract)}"
        this.contract = contract
        phase = Phase.PLAYING
        trickLeader = vorhand
    }

    // --- Kontra and Re --------------------------------------------------------------------

    /** The defender who said Kontra, or -1. */
    var kontraBy: Int = -1
        private set

    /** True if the declarer answered the Kontra with Re. */
    var re: Boolean = false
        private set

    private val kontraDecided = mutableSetOf<Int>()

    /** Doubling of the game: 1 normally, 2 with Kontra, 4 with Re. */
    val kontraFactor: Int get() = if (re) 4 else if (kontraBy >= 0) 2 else 1

    /** Only a defender who said or held at least 18 in bidding may give Kontra. */
    fun mayKontra(seat: Int): Boolean =
        seat != declarer && biddingLog.any { (s, word) -> s == seat && word != "passe" }

    /**
     * Before the first card each defender who may give Kontra decides in turn, starting with
     * Vorhand; after a Kontra the declarer decides on Re. Null when nobody has to decide.
     */
    val kontraDecider: Int?
        get() {
            if (phase != Phase.PLAYING || isRamsch || tricks.isNotEmpty() || trickCards.isNotEmpty()) return null
            if (kontraBy < 0) return (0..2).map { (vorhand + it) % 3 }.firstOrNull { it !in kontraDecided && mayKontra(it) }
            return declarer.takeIf { it !in kontraDecided }
        }

    /** "Kontra" or "Kontra, Re" for display, null without Kontra. */
    val kontraLabel: String?
        get() = when {
            re -> "Kontra, Re"
            kontraBy >= 0 -> "Kontra"
            else -> null
        }

    /** Kontra (from a defender) or Re (from the declarer), or declining it with [say] = false. */
    fun kontra(seat: Int, say: Boolean) {
        check(seat == kontraDecider) { "Nicht am Kontra" }
        kontraDecided += seat
        log += "kon $seat ${if (say) 1 else 0}"
        if (say) {
            if (seat == declarer) re = true else kontraBy = seat
        }
    }

    // --- Playing ---------------------------------------------------------------------------

    var trickLeader: Int = vorhand
        private set

    private val trickCards = mutableListOf<Card>()
    val currentTrick: List<Card> get() = trickCards

    val tricks: MutableList<Trick> = mutableListOf()

    /** Every card played so far, including the current trick. */
    val playedCards: List<Card> get() = tricks.flatMap { it.cards } + trickCards

    fun hand(seat: Int): List<Card> = hands[seat]

    /** The Skat: the original two cards, or the pushed ones after discarding. */
    val skat: List<Card> get() = skatCards

    val initialSkat: List<Card> get() = originalSkat

    fun legalCards(seat: Int): List<Card> {
        if (phase != Phase.PLAYING || seat != activeSeat || kontraDecider != null) return emptyList()
        return contract!!.legalCards(hands[seat], trickCards)
    }

    fun play(seat: Int, card: Card) {
        check(phase == Phase.PLAYING && seat == activeSeat) { "Nicht am Zug" }
        require(card in legalCards(seat)) { "$card darf jetzt nicht gespielt werden" }
        log += "play $seat $card"
        hands[seat] -= card
        trickCards += card
        if (trickCards.size == 3) finishTrick()
    }

    private fun finishTrick() {
        val c = contract!!
        val winner = (trickLeader + c.trickWinnerIndex(trickCards)) % 3
        tricks += Trick(trickLeader, trickCards.toList(), winner)
        trickCards.clear()
        trickLeader = winner
        val nullLost = c.kind == GameKind.NULL && winner == declarer
        if (nullLost || hands.all { it.isEmpty() }) finish()
    }

    fun tricksOf(seat: Int): List<Trick> = tricks.filter { it.winner == seat }

    fun pointsOf(seat: Int): Int {
        val trickPoints = tricksOf(seat).sumOf { it.cards.points() }
        val getsSkat = if (isRamsch) tricks.size == 10 && tricks.last().winner == seat else seat == declarer
        return if (getsSkat) trickPoints + skatCards.points() else trickPoints
    }

    /** Card points of both opponents together. */
    val defenderPoints: Int get() = (0..2).filter { it != declarer }.sumOf { pointsOf(it) }

    var settlement: Settlement? = null
        private set

    private fun finish() {
        phase = Phase.FINISHED
        if (isRamsch) {
            ramschResult = RamschResult.of(points = (0..2).map(::pointsOf), tricks = (0..2).map { tricksOf(it).size })
            return
        }
        settlement = Scoring.settle(
            contract = contract!!,
            bid = bid,
            declarerCards = declarerCards,
            declarerPoints = pointsOf(declarer),
            declarerTricks = tricksOf(declarer).size,
            kontraFactor = kontraFactor,
        )
    }

    /** The deal and every action as text; [decode] turns it back into the same round. */
    fun encode(): String = listOf(FORMAT, dealer.toString(), deck.joinToString(" "), log.joinToString(";")).joinToString("\n")

    companion object {
        private const val FORMAT = "runde1"

        private fun encodeContract(c: Contract): String =
            listOf(c.kind.name, c.trump?.name ?: "-", c.hand, c.schneiderAngesagt, c.schwarzAngesagt, c.ouvert)
                .joinToString(",") { if (it is Boolean) (if (it) "1" else "0") else it.toString() }

        private fun decodeContract(text: String): Contract {
            val p = text.split(',')
            return Contract(
                kind = GameKind.valueOf(p[0]),
                trump = if (p[1] == "-") null else Suit.valueOf(p[1]),
                hand = p[2] == "1",
                schneiderAngesagt = p[3] == "1",
                schwarzAngesagt = p[4] == "1",
                ouvert = p[5] == "1",
            )
        }

        /** Replays a saved round; null if the text is damaged or from an unknown version. */
        fun decode(text: String): Round? = runCatching {
            val lines = text.split('\n')
            require(lines.size == 4 && lines[0] == FORMAT)
            val round = Round(lines[1].toInt(), Card.parseList(lines[2]))
            for (action in lines[3].split(';').filter { it.isNotBlank() }) {
                val p = action.split(' ')
                val seat = p[1].toInt()
                when (p[0]) {
                    "say" -> round.say(seat, p[2].toInt())
                    "ans" -> round.answer(seat, p[2] == "1")
                    "pass" -> round.pass(seat)
                    "p18" -> round.playFor18(seat)
                    "hand" -> round.playHand(seat)
                    "skat" -> round.pickUpSkat(seat)
                    "drk" -> round.discard(seat, p[2].split(',').map(Card::parse))
                    "dcl" -> round.declare(seat, decodeContract(p[2]))
                    "kon" -> round.kontra(seat, p[2] == "1")
                    "play" -> round.play(seat, Card.parse(p[2]))
                    else -> error("Unbekannter Zug $action")
                }
            }
            round
        }.getOrNull()

        fun shuffled(dealer: Int, random: Random = Random.Default): Round = Round(dealer, Card.DECK.shuffled(random))
    }
}
