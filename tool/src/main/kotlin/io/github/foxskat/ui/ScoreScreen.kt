package io.github.foxskat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import io.github.foxskat.core.Texts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScoreViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {

    private val prefs = Prefs(dataStore)
    val sheet = Table.sheet

    private val _confirmClear = MutableStateFlow(false)
    val confirmClear: StateFlow<Boolean> = _confirmClear.asStateFlow()

    init {
        viewModelScope.launch { Table.ensureLoaded(prefs) }
    }

    /** First tap asks, second tap clears the list and ends the running game. */
    fun clear() {
        if (!_confirmClear.value) {
            _confirmClear.value = true
            return
        }
        _confirmClear.value = false
        viewModelScope.launch { Table.clearSheet(prefs) }
    }
}

class ScoreScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ScoreViewModel>(sealedActivity) {

    override val viewModelClass: Class<ScoreViewModel>
        get() = ScoreViewModel::class.java

    override fun createViewModel() = ScoreViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val sheet by viewModel.sheet.collectAsState()
        val confirm by viewModel.confirmClear.collectAsState()

        ToolScaffold(
            title = "Liste",
            subtitle = "${sheet.entries.size} Spiele",
            onBack = { goBack() },
            rightButton = if (sheet.entries.isEmpty()) null else LightBarButton.Text(
                if (confirm) "WIRKLICH?" else "LÖSCHEN",
                onClick = viewModel::clear,
            ),
        ) {
            // Standings, set apart from the games below by a line.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
                for (seat in 0..2) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        LightText(Texts.seatName(seat).uppercase(), LightTextVariant.Detail, lighten = true, maxLines = 1)
                        LightText("${sheet.total(seat)}", LightTextVariant.Heading, maxLines = 1)
                    }
                }
            }
            Divider()
            if (sheet.entries.isEmpty()) {
                HintText("Noch keine Spiele. Gezählt wird wie auf dem Skatblock: gewonnen +Spielwert, verloren −2 × Spielwert.")
            }
            LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (e in sheet.entries.asReversed()) {
                    if (e.passedIn) {
                        TwoLineRow(title = "${e.number}. Eingepasst", detail = null)
                    } else {
                        val who = e.seats.joinToString(" und ") { Texts.seatName(it) }
                        val detail = if (e.game.startsWith("Ramsch")) {
                            "${e.declarerPoints} Augen"
                        } else {
                            "gereizt ${e.bid} · ${if (e.won) "gewonnen" else "verloren"} · ${e.declarerPoints} Augen"
                        }
                        TwoLineRow(
                            title = "${e.number}. $who · ${e.game}",
                            detail = detail,
                            trailing = if (e.score > 0) "+${e.score}" else "${e.score}",
                        )
                    }
                }
            }
        }
    }
}
