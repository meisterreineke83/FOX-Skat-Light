package io.github.foxskat.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import io.github.foxskat.core.Ai
import io.github.foxskat.core.Card
import io.github.foxskat.core.Contract
import io.github.foxskat.core.GameKind
import io.github.foxskat.core.Phase
import io.github.foxskat.core.Round
import io.github.foxskat.core.ScoreSheet
import io.github.foxskat.core.Suit
import io.github.foxskat.core.Texts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The table outlives single screens: going back to the menu and returning keeps the
 * running game. Only the score list is saved; a game is lost if the tool is closed.
 */
object Table {
    const val HUMAN = 0
    private const val MAX_NAME_LENGTH = 12

    private val _version = MutableStateFlow(0)

    /** Bumped after every change to [round] so the UI recomposes. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _sheet = MutableStateFlow(ScoreSheet())
    val sheet: StateFlow<ScoreSheet> = _sheet.asStateFlow()

    var round: Round? = null
        private set

    private var recorded = false
    private var loaded = false
    private val loadLock = Mutex()

    suspend fun ensureLoaded(prefs: Prefs) = loadLock.withLock {
        if (!loaded) {
            _sheet.value = prefs.loadSheet()
            Texts.names = prefs.loadNames()
            loaded = true
        }
    }

    fun newRound() {
        round = Round.shuffled(dealer = _sheet.value.nextDealer)
        recorded = false
        changed()
    }

    /** Writes a finished round to the list once. */
    suspend fun recordIfDone(prefs: Prefs) {
        val r = round ?: return
        if (recorded || r.activeSeat != null) return
        recorded = true
        _sheet.value = _sheet.value.add(r)
        prefs.saveSheet(_sheet.value)
        changed()
    }

    /** Renames a computer player; empty input keeps the old name. */
    suspend fun rename(prefs: Prefs, seat: Int, name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        if (clean.isEmpty()) return
        Texts.names = if (seat == 1) clean to Texts.names.second else Texts.names.first to clean
        prefs.saveName(seat, clean)
        changed()
    }

    suspend fun clearSheet(prefs: Prefs) {
        _sheet.value = ScoreSheet()
        prefs.saveSheet(_sheet.value)
        round = null
        changed()
    }

    fun changed() {
        _version.value++
    }
}

/** Announcement for hand games, cycled through on the declare screen. */
enum class Announcement(val label: String) { KEINE("KEINE"), SCHNEIDER("SCHNEIDER"), SCHWARZ("SCHWARZ"), OUVERT("OUVERT") }

class GameViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {

    private val prefs = Prefs(dataStore)
    val version = Table.version

    private val _selected = MutableStateFlow<Set<Card>>(emptySet())
    val selected: StateFlow<Set<Card>> = _selected.asStateFlow()

    private val _announcement = MutableStateFlow(Announcement.KEINE)
    val announcement: StateFlow<Announcement> = _announcement.asStateFlow()

    private var driver: Job? = null

    val round: Round? get() = Table.round

    init {
        viewModelScope.launch {
            prefs.applyTheme()
            Table.ensureLoaded(prefs)
            if (Table.round == null) Table.newRound()
            drive()
        }
    }

    /** Lets the computer players act, one step at a time, until it is the person's turn. */
    private fun drive() {
        if (driver?.isActive == true) return
        driver = viewModelScope.launch {
            while (true) {
                val r = Table.round ?: break
                val seat = r.activeSeat
                if (seat == null) {
                    Table.recordIfDone(prefs)
                    break
                }
                if (seat == Table.HUMAN) break
                val trickJustDone = r.phase == Phase.PLAYING && r.currentTrick.isEmpty() && r.tricks.isNotEmpty()
                delay(if (trickJustDone) TRICK_PAUSE_MS else STEP_DELAY_MS)
                if (Table.round !== r) continue
                Ai.act(r, seat)
                Table.changed()
            }
        }
    }

    private fun humanAction(action: (Round) -> Unit) {
        val r = Table.round ?: return
        runCatching { action(r) }
        _selected.value = emptySet()
        Table.changed()
        drive()
    }

    fun say() = humanAction { r -> Contract.nextBid(r.bid)?.let { r.say(Table.HUMAN, it) } }

    fun hold() = humanAction { it.answer(Table.HUMAN, hold = true) }

    fun pass() = humanAction { it.pass(Table.HUMAN) }

    fun playFor18() = humanAction { it.playFor18(Table.HUMAN) }

    fun playHand() = humanAction { it.playHand(Table.HUMAN) }

    fun pickUpSkat() = humanAction { it.pickUpSkat(Table.HUMAN) }

    fun discardSelected() {
        val cards = _selected.value.toList()
        if (cards.size == 2) humanAction { it.discard(Table.HUMAN, cards) }
    }

    fun declare(contract: Contract) = humanAction { it.declare(Table.HUMAN, contract) }

    fun nextRound() {
        Table.newRound()
        _selected.value = emptySet()
        _announcement.value = Announcement.KEINE
        drive()
    }

    fun cycleAnnouncement() {
        _announcement.value = Announcement.entries[(_announcement.value.ordinal + 1) % Announcement.entries.size]
    }

    /**
     * Tap on a card in the hand. While discarding it toggles the selection; while playing
     * the first tap marks the card and the second tap plays it.
     */
    fun tapCard(card: Card) {
        val r = Table.round ?: return
        when (r.phase) {
            Phase.DISCARD -> {
                if (r.declarer != Table.HUMAN) return
                val sel = _selected.value
                _selected.value = when {
                    card in sel -> sel - card
                    sel.size < 2 -> sel + card
                    else -> sel
                }
            }
            Phase.PLAYING -> {
                if (r.activeSeat != Table.HUMAN || card !in r.legalCards(Table.HUMAN)) return
                if (card in _selected.value) humanAction { it.play(Table.HUMAN, card) } else _selected.value = setOf(card)
            }
            else -> Unit
        }
    }

    /** Games the person can choose from, with the value they would have. */
    fun declareChoices(): List<Pair<Contract, Int>> {
        val r = Table.round ?: return emptyList()
        val hand = !r.pickedUpSkat
        val known = if (hand) r.hand(Table.HUMAN) else r.declarerCards
        val a = _announcement.value
        val trumpGames = (Suit.entries.map { Contract(GameKind.FARBE, it, hand = hand) } + Contract(GameKind.GRAND, hand = hand))
            .map {
                if (!hand) it else it.copy(
                    schneiderAngesagt = a >= Announcement.SCHNEIDER,
                    schwarzAngesagt = a >= Announcement.SCHWARZ,
                    ouvert = a == Announcement.OUVERT,
                )
            }
        val nullGames = listOf(Contract(GameKind.NULL, hand = hand), Contract(GameKind.NULL, hand = hand, ouvert = true))
        return (trumpGames + nullGames).map { c ->
            c to if (c.kind == GameKind.NULL) c.baseValue else c.minimumValue(known)
        }
    }

    companion object {
        const val STEP_DELAY_MS = 700L
        const val TRICK_PAUSE_MS = 1500L
    }
}
