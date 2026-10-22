package io.github.foxskat.core

/** German texts for the table; seat 0 is the person holding the phone. */
object Texts {

    fun seatName(seat: Int): String = when (seat) {
        0 -> "Du"
        1 -> "Links"
        else -> "Rechts"
    }

    fun position(round: Round, seat: Int): String = when (seat) {
        round.vorhand -> "Vorhand"
        round.mittelhand -> "Mittelhand"
        else -> "Hinterhand"
    }

    /** What a seat said last in bidding, e.g. "20", "ja" or "passe"; null if nothing yet. */
    fun lastWord(round: Round, seat: Int): String? = round.biddingLog.lastOrNull { it.first == seat }?.second

    /** One line telling the person what is going on and what to do. */
    fun status(round: Round, human: Int = 0): String {
        val active = round.activeSeat
        return when (round.phase) {
            Phase.BIDDING -> when {
                active != human -> "${seatName(active!!)} ${if (round.awaitingAnswer) "überlegt" else "reizt"} …"
                round.vorhandAlone -> "Beide haben gepasst. Spielst du für 18?"
                round.awaitingAnswer -> "${seatName(round.bidder)} sagt ${round.bid}. Hältst du?"
                else -> if (round.bid == 0) "Du sagst zuerst." else "Du sagst weiter: ${Contract.nextBid(round.bid)}?"
            }
            Phase.SKAT_DECISION -> if (round.declarer == human) {
                "Du spielst für ${round.bid}. Skat aufnehmen oder Hand?"
            } else {
                "${seatName(round.declarer)} spielt für ${round.bid} und überlegt …"
            }
            Phase.DISCARD -> if (round.declarer == human) "Wähle zwei Karten zum Drücken." else "${seatName(round.declarer)} drückt …"
            Phase.DECLARE -> if (round.declarer == human) "Was spielst du?" else "${seatName(round.declarer)} sagt an …"
            Phase.PLAYING -> when {
                active == human && round.currentTrick.isEmpty() -> "Du spielst aus."
                active == human -> "Du bist dran."
                else -> "${seatName(active!!)} ist dran …"
            }
            Phase.FINISHED -> result(round, human)
            Phase.PASSED_IN -> "Alle haben gepasst. Eingepasst."
        }
    }

    fun result(round: Round, human: Int = 0): String {
        val s = round.settlement ?: return ""
        val who = if (round.declarer == human) "Du hast" else "${seatName(round.declarer)} hat"
        val verdict = if (s.won) "gewonnen" else "verloren"
        val extra = buildList {
            if (s.overbid) add("überreizt")
            if (s.schwarz) add("schwarz") else if (s.schneider) add("Schneider")
        }.joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }
        val points = if (round.contract!!.kind == GameKind.NULL) "" else " mit ${s.declarerPoints} Augen"
        val sign = if (s.score > 0) "+" else ""
        return "$who ${round.contract!!.name}$points $verdict$extra: $sign${s.score}."
    }

    /** Short description of the game for the top bar. */
    fun gameLine(round: Round): String? {
        val c = round.contract
        return when {
            c != null -> "${c.name} · ${seatName(round.declarer)} · ${round.bid}"
            round.phase == Phase.BIDDING && round.bid > 0 -> "Gereizt: ${round.bid}"
            round.declarer >= 0 -> "${seatName(round.declarer)} spielt · ${round.bid}"
            else -> null
        }
    }
}
