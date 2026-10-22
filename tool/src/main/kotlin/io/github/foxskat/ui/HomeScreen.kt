package io.github.foxskat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import io.github.foxskat.core.Phase
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

    fun toggleTheme() {
        viewModelScope.launch { prefs.toggleTheme() }
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
        viewModel.version.collectAsState().value
        val colors by LightThemeController.colors.collectAsState()
        val isDark = colors == LightThemeColors.Dark
        val running = Table.round?.let { it.phase != Phase.FINISHED && it.phase != Phase.PASSED_IN } == true

        ToolScaffold(title = "Skat", onBack = null) {
            MenuItem(if (running) "WEITERSPIELEN" else "SPIELEN") {
                navigateTo(::GameScreen)
            }
            MenuItem("LISTE", value = if (sheet.entries.isEmpty()) null else "${sheet.total(Table.HUMAN)}") {
                navigateTo(::ScoreScreen)
            }
            MenuItem("ANZEIGE", value = if (isDark) "DUNKEL" else "HELL") {
                viewModel.toggleTheme()
            }
            HintText("Du spielst gegen zwei Computergegner, Links und Rechts. Tippe eine Karte an und tippe noch einmal, um sie zu spielen.")
        }
    }
}
