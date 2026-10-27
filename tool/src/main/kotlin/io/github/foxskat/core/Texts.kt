package io.github.foxskat.core

/** German texts for the table; seat 0 is the person holding the phone. */
object Texts {

    const val DEFAULT_LEFT = "Marc"
    const val DEFAULT_RIGHT = "Martin"

    /** Names of the computer players to the left (seat 1) and right (seat 2). */
    var names: Pair<String, String> = DEFAULT_LEFT to DEFAULT_RIGHT

    fun seatName(seat: Int): String = when (seat) {
        0 -> "Du"
        1 -> names.first
        else -> names.second
    }

    fun position(round: Round, seat: Int): String = when (seat) {
        round.vorhand -> "Vorhand"
        round.mittelhand -> "Mittelhand"
        else -> "Hinterhand"
    }

    /** "1 Stich", "3 Stiche". */
    fun tricks(count: Int): String = if (count == 1) "1 Stich" else "$count Stiche"

    /** What a seat said last in bidding, e.g. "20", "ja" or "passe"; null if nothing yet. */
    fun lastWord(round: Round, seat: Int): String? = round.biddingLog.lastOrNull { it.first == seat }?.second

    /** One line telling the person what is going on and what to do. */
    fun status(round: Round, human: Int = 0): String {
        val active = round.activeSeat
        return when (round.phase) {
            Phase.BIDDING -> when {
                active != human -> "${seatName(active!!)} ${if (round.awaitingAnswer) "überlegt" else "reizt"} …"
                round.vorhandAlone -> "Beide haben gepasst. Spielst du für 18? Sonst wird Ramsch gespielt."
                round.awaitingAnswer -> "${seatName(round.bidder)} sagt ${round.bid}. Hältst du?"
                else -> if (round.bid == 0) {
                    "Du bist ${position(round, human)} und sagst zuerst."
                } else {
                    "Du sagst weiter: ${Contract.nextBid(round.bid)}?"
                }
            }
            Phase.SKAT_DECISION -> if (round.declarer == human) {
                "Du spielst für ${round.bid}. Skat aufnehmen oder Hand?"
            } else {
                "${seatName(round.declarer)} spielt für ${round.bid} und überlegt …"
            }
            Phase.DISCARD -> if (round.declarer == human) "Wähle zwei Karten zum Drücken." else "${seatName(round.declarer)} drückt …"
            Phase.DECLARE -> if (round.declarer == human) "Was spielst du?" else "${seatName(round.declarer)} sagt an …"
            Phase.PLAYING -> when {
                active == round.kontraDecider -> kontraStatus(round, active!!, human)
                active == human && round.currentTrick.isEmpty() -> "Du spielst aus."
                active == human -> "Du bist dran."
                else -> "${seatName(active!!)} ist dran …"
            }
            Phase.FINISHED -> result(round, human)
            Phase.PASSED_IN -> "Alle haben gepasst. Eingepasst."
        }
    }

    fun result(round: Round, human: Int = 0): String {
        round.ramschResult?.let { return ramschResult(it) }
        val s = round.settlement ?: return ""
        val who = if (round.declarer == human) "Du hast" else "${seatName(round.declarer)} hat"
        val verdict = if (s.won) "gewonnen" else "verloren"
        val extra = buildList {
            round.kontraLabel?.let { add(it) }
            if (s.overbid) add("überreizt")
            if (s.schwarz) add("schwarz") else if (s.schneider) add("Schneider")
        }.joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }
        val points = if (round.contract!!.kind == GameKind.NULL) "" else " mit ${s.declarerPoints} Augen"
        val sign = if (s.score > 0) "+" else ""
        return "$who ${round.contract!!.name}$points $verdict$extra: $sign${s.score}."
    }

    private fun kontraStatus(round: Round, seat: Int, human: Int): String {
        val kontraFrom = round.kontraBy.takeIf { it >= 0 }?.let { if (it == human) "Du sagst" else "${seatName(it)} sagt" }
        return when {
            seat != human -> "${seatName(seat)} überlegt …"
            kontraFrom != null -> "$kontraFrom Kontra. Sagst du Re?"
            else -> "${seatName(round.declarer)} spielt ${round.contract!!.name}. Gibst du Kontra?"
        }
    }

    private fun ramschResult(r: RamschResult): String {
        val d = r.durchmarsch
        if (d != null) return "Ramsch: ${if (d == 0) "Du hast" else "${seatName(d)} hat"} alle Stiche, Durchmarsch: +120."
        val names = r.losers.map(::seatName)
        val who = when {
            names.size == 1 && r.losers[0] == 0 -> "Du verlierst"
            names.size == 1 -> "${names[0]} verliert"
            else -> names.joinToString(" und ") + " verlieren"
        }
        val jungfrau = if (r.jungfrauen.isEmpty()) "" else " (Jungfrau: ${r.jungfrauen.joinToString(", ") { seatName(it) }})"
        return "Ramsch: $who mit ${r.points[r.losers[0]]} Augen$jungfrau: ${r.scores[r.losers[0]]}."
    }

    /** Game in large letters for the top bar, e.g. "♥ HERZ HAND", "REIZEN" or "RAMSCH". */
    fun gameTitle(round: Round): String {
        val c = round.contract
        return when {
            round.isRamsch -> "RAMSCH"
            c != null && c.kind == GameKind.FARBE -> "${c.trump!!.symbol} ${c.name.uppercase()}"
            c != null -> c.name.uppercase()
            round.phase == Phase.BIDDING -> "REIZEN"
            else -> "SKAT"
        }
    }

    /** Who plays and how far was bid, e.g. "Marc spielt · gereizt bis 20". */
    fun gameLine(round: Round): String? = when {
        round.isRamsch -> "Alle gepasst · wer die meisten Augen hat, verliert"
        round.declarer >= 0 -> {
            val who = if (round.declarer == 0) "Du spielst" else "${seatName(round.declarer)} spielt"
            "$who · gereizt bis ${round.bid}" + (round.kontraLabel?.let { " · $it" } ?: "")
        }
        round.phase == Phase.BIDDING && round.bid > 0 -> "Gereizt: ${round.bid}"
        round.phase == Phase.BIDDING -> "Noch nichts gesagt"
        else -> null
    }
}
