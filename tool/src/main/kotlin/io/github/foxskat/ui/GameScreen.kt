package io.github.foxskat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
            title = "Skat",
            subtitle = round?.let { Texts.gameLine(it) },
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
            Opponents(round)
            if (myTurnToDeclare) {
                DeclareChoices(round, announcement)
            } else {
                TrickArea(round)
                LightText(
                    text = Texts.status(round),
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                )
                if (round.phase == Phase.FINISHED || round.phase == Phase.PASSED_IN) {
                    HintText("Skat: ${round.initialSkat.joinToString(" ")}" +
                        if (round.pickedUpSkat) " · gedrückt: ${round.skat.joinToString(" ")}" else "")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            val hand = CardOrder.sort(round.hand(Table.HUMAN), round.contract)
            val legal = round.legalCards(Table.HUMAN).toSet()
            HandView(
                cards = hand,
                selected = selected,
                playable = { round.phase != Phase.PLAYING || it in legal },
                onTap = viewModel::tapCard,
            )
        }
    }

    private fun bottomBar(round: Round, selectedCount: Int): List<LightBottomBarItem?>? {
        val mine = round.activeSeat == Table.HUMAN
        return when (round.phase) {
            Phase.BIDDING -> if (!mine) null else when {
                round.vorhandAlone -> listOf(
                    LightBarButton.Text("PASSE", onClick = viewModel::pass),
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
            Phase.DECLARE, Phase.PLAYING -> null
        }
    }

    /** One line per computer player: position, last bid word or tricks, open cards when ouvert. */
    @Composable
    private fun Opponents(round: Round) {
        for (seat in listOf(1, 2)) {
            val detail = when (round.phase) {
                Phase.BIDDING, Phase.PASSED_IN -> Texts.lastWord(round, seat) ?: ""
                else -> when (seat) {
                    round.declarer -> "spielt · ${round.tricksOf(seat).size} Stiche"
                    else -> "${round.tricksOf(seat).size} Stiche"
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                LightText("${Texts.seatName(seat).uppercase()} · ${Texts.position(round, seat)}", LightTextVariant.Detail)
                Spacer(modifier = Modifier.weight(1f))
                LightText(detail, LightTextVariant.Detail, lighten = true)
            }
            val c = round.contract
            if (c != null && c.ouvert && round.declarer == seat && round.phase == Phase.PLAYING) {
                LightText(
                    CardOrder.sort(round.hand(seat), c).joinToString(" "),
                    LightTextVariant.Detail,
                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                )
            }
        }
        val me = when (round.phase) {
            Phase.BIDDING, Phase.PASSED_IN -> Texts.lastWord(round, Table.HUMAN) ?: ""
            else -> "${round.tricksOf(Table.HUMAN).size} Stiche"
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
            LightText("DU · ${Texts.position(round, Table.HUMAN)}", LightTextVariant.Detail)
            Spacer(modifier = Modifier.weight(1f))
            LightText(me, LightTextVariant.Detail, lighten = true)
        }
    }

    /** The current trick, or the last finished one while the next has not started. */
    @Composable
    private fun TrickArea(round: Round) {
        if (round.phase != Phase.PLAYING && round.phase != Phase.FINISHED) return
        val current = round.currentTrick
        val last = round.tricks.lastOrNull()
        val showLast = current.isEmpty() && last != null
        val leader = if (showLast) last!!.leader else round.trickLeader
        val cards = if (showLast) last!!.cards else current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (i in 0 until 3) {
                val seat = (leader + i) % 3
                TrickSlot(
                    label = Texts.seatName(seat).uppercase(),
                    card = cards.getOrNull(i),
                    highlight = showLast && last!!.winner == seat,
                )
            }
        }
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
