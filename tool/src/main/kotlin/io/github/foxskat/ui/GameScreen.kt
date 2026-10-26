package io.github.foxskat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.foxskat.core.Card
import io.github.foxskat.core.CardOrder
import io.github.foxskat.core.Contract
import io.github.foxskat.core.GameKind
import io.github.foxskat.core.Phase
import io.github.foxskat.core.Round
import io.github.foxskat.core.Texts

class GameScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, GameViewModel>(sealedActivity) {

    override val viewModelClass: Class<GameViewModel>
        get() = GameViewModel::class.java

    override fun createViewModel() = GameViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val version by viewModel.version.collectAsState()
        val selected by viewModel.selected.collectAsState()
        val announcement by viewModel.announcement.collectAsState()
        val round = viewModel.round

        // The round is one mutable object, so Compose would skip every part that only gets
        // the same Round instance. Keying on the version rebuilds the table after each move.
        key(version) {
            GameTable(round, selected, announcement)
        }
    }

    @Composable
    private fun GameTable(round: Round?, selected: Set<Card>, announcement: Announcement) {
        ToolScaffold(
            title = round?.let { Texts.gameTitle(it) } ?: "SKAT",
            onBack = { goBack() },
            bottomBar = if (round == null) {
                listOf(LightBarButton.Text("NEUES SPIEL", onClick = viewModel::nextRound))
            } else {
                bottomBar(round, selected.size)
            },
        ) {
            if (round == null) {
                HintText("Die Liste ist leer. Ein neues Spiel beginnt.")
                return@ToolScaffold
            }
            val myTurnToDeclare = round.phase == Phase.DECLARE && round.declarer == Table.HUMAN
            val over = round.phase == Phase.FINISHED || round.phase == Phase.PASSED_IN
            // This part gives way first if space runs short, so the hand always stays visible.
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (myTurnToDeclare) {
                    DeclareChoices(round, announcement)
                } else {
                    // Who plays matters while playing; before that the table and the status say it.
                    if (round.phase == Phase.PLAYING) {
                        Texts.gameLine(round)?.let {
                            LightText(
                                text = it,
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                align = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
                            )
                        }
                    }
                    // What is going on comes before the table, so it is never the part that gets cut off.
                    LightText(
                        text = Texts.status(round),
                        variant = LightTextVariant.Paragraph,
                        align = TextAlign.Center,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
                    )
                    if (over) {
                        if (round.contract != null) {
                            HintText("Skat: ${round.initialSkat.joinToString(" ")}" +
                                if (round.pickedUpSkat) " · gedrückt: ${round.skat.joinToString(" ")}" else "")
                        }
                    } else {
                        TableView(round)
                        OpenHand(round)
                    }
                }
            }
            val hand = CardOrder.sort(round.hand(Table.HUMAN), round.contract)
            val legal = round.legalCards(Table.HUMAN).toSet()
            HandView(
                cards = hand,
                selected = selected,
                playable = { round.phase != Phase.PLAYING || it in legal },
                trump = { round.contract?.isTrump(it) == true },
                onTap = viewModel::tapCard,
            )
        }
    }

    private fun bottomBar(round: Round, selectedCount: Int): List<LightBottomBarItem?>? {
        val mine = round.activeSeat == Table.HUMAN
        return when (round.phase) {
            Phase.BIDDING -> if (!mine) null else when {
                round.vorhandAlone -> listOf(
                    LightBarButton.Text("RAMSCH", onClick = viewModel::pass),
                    LightBarButton.Text("18 SPIELEN", onClick = viewModel::playFor18),
                )
                round.awaitingAnswer -> listOf(
                    LightBarButton.Text("PASSE", onClick = viewModel::pass),
                    LightBarButton.Text("JA", onClick = viewModel::hold),
                )
                else -> listOf(
                    LightBarButton.Text("PASSE", onClick = viewModel::pass),
                    LightBarButton.Text("${Contract.nextBid(round.bid)}", onClick = viewModel::say),
                )
            }
            Phase.SKAT_DECISION -> if (!mine) null else listOf(
                LightBarButton.Text("HAND", onClick = viewModel::playHand),
                LightBarButton.Text("SKAT NEHMEN", onClick = viewModel::pickUpSkat),
            )
            Phase.DISCARD -> if (!mine) null else listOf(
                LightBarButton.Text(
                    if (selectedCount == 2) "DRÜCKEN" else "NOCH ${2 - selectedCount}",
                    onClick = if (selectedCount == 2) viewModel::discardSelected else null,
                ),
            )
            Phase.FINISHED, Phase.PASSED_IN -> listOf(
                LightBarButton.Text("LISTE", onClick = { navigateTo(::ScoreScreen) }),
                LightBarButton.Text("WEITER", onClick = viewModel::nextRound),
            )
            Phase.PLAYING -> if (round.kontraDecider != Table.HUMAN) null else {
                val word = if (round.declarer == Table.HUMAN) "RE" else "KONTRA"
                listOf(
                    LightBarButton.Text("WEITER", onClick = { viewModel.kontra(say = false) }),
                    LightBarButton.Text(word, onClick = { viewModel.kontra(say = true) }),
                )
            }
            Phase.DECLARE -> null
        }
    }

    /**
     * The table as seen from the chair: left neighbour, you, right neighbour. While playing each
     * place holds the card that player put into the current trick (or the last trick, with the
     * winner's card inverted); before that it holds the player's last word in bidding.
     */
    @Composable
    private fun TableView(round: Round) {
        val playing = round.phase == Phase.PLAYING
        val current = round.currentTrick
        val last = round.tricks.lastOrNull()
        val showLast = current.isEmpty() && last != null
        val leader = if (showLast) last!!.leader else round.trickLeader
        val cards = if (showLast) last!!.cards else current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (seat in listOf(1, Table.HUMAN, 2)) {
                val slot = if (playing) {
                    SeatSlot.Played(
                        card = cards.getOrNull((seat - leader + 3) % 3),
                        wonTrick = showLast && last!!.winner == seat,
                    )
                } else {
                    SeatSlot.Word(Texts.lastWord(round, seat) ?: "–")
                }
                SeatView(
                    name = Texts.seatName(seat),
                    declarer = seat == round.declarer,
                    active = seat == round.activeSeat,
                    slot = slot,
                    detail = if (playing) Texts.tricks(round.tricksOf(seat).size) else Texts.position(round, seat),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    /** The declarer's cards in an ouvert game, in one line below the table. */
    @Composable
    private fun OpenHand(round: Round) {
        val c = round.contract ?: return
        if (!c.ouvert || round.phase != Phase.PLAYING || round.declarer == Table.HUMAN) return
        LightText(
            "${Texts.seatName(round.declarer)}: " + CardOrder.sort(round.hand(round.declarer), c).joinToString(" "),
            LightTextVariant.Detail,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
        )
    }

    @Composable
    private fun ColumnScope.DeclareChoices(round: Round, announcement: Announcement) {
        LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column {
                HintText("Gereizt bis ${round.bid}. Was spielst du?")
                if (!round.pickedUpSkat) {
                    MenuItem("ANSAGE", value = announcement.label, onClick = viewModel::cycleAnnouncement)
                }
                for ((contract, value) in viewModel.declareChoices()) {
                    val enough = value >= round.bid
                    val trump = contract.trump
                    val label = when (contract.kind) {
                        GameKind.FARBE -> trump!!.symbol + " " + trump.germanName.uppercase()
                        GameKind.GRAND -> "GRAND"
                        GameKind.NULL -> if (contract.ouvert) "NULL OUVERT" else "NULL"
                    }
                    val allowed = round.declareProblem(contract) == null
                    MenuItem(
                        label = label,
                        value = if (enough) "$value" else "$value · zu wenig",
                        enabled = allowed,
                        onClick = { viewModel.declare(contract) },
                    )
                }
            }
        }
    }
}
