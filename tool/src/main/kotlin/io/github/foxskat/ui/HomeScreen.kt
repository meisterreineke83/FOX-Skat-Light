package io.github.foxskat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import io.github.foxskat.core.Phase
import io.github.foxskat.core.ScoreSheet
import io.github.foxskat.core.Texts
import kotlinx.coroutines.launch

class HomeViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {

    val prefs = Prefs(dataStore)
    val sheet = Table.sheet
    val version = Table.version

    init {
        viewModelScope.launch {
            prefs.applyTheme()
            Table.ensureLoaded(prefs)
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() = HomeViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val sheet by viewModel.sheet.collectAsState()
        val version by viewModel.version.collectAsState()
        val running = Table.round?.let { it.phase != Phase.FINISHED && it.phase != Phase.PASSED_IN } == true

        // Names and the running game live outside Compose state; rebuild when they change.
        key(version) {
            Menu(running, sheet)
        }
    }

    @Composable
    private fun Menu(running: Boolean, sheet: ScoreSheet) {
        ToolScaffold(title = "Skat", onBack = null) {
            MenuItem(if (running) "WEITERSPIELEN" else "SPIELEN") {
                navigateTo(::GameScreen)
            }
            MenuItem("LISTE", value = if (sheet.entries.isEmpty()) null else "${sheet.total(Table.HUMAN)}") {
                navigateTo(::ScoreScreen)
            }
            MenuItem("ANZEIGE") {
                navigateTo(::SettingsScreen)
            }
            HintText(
                "Du spielst gegen ${Texts.seatName(1)} (links) und ${Texts.seatName(2)} (rechts). " +
                    "Tippe eine Karte an und tippe noch einmal, um sie zu spielen."
            )
        }
    }
}
