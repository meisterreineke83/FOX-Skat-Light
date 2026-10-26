package io.github.foxskat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import io.github.foxskat.core.Texts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {

    private val prefs = Prefs(dataStore)
    val version = Table.version

    init {
        viewModelScope.launch { Table.ensureLoaded(prefs) }
    }

    fun toggleTheme() {
        viewModelScope.launch { prefs.toggleTheme() }
    }

    fun rename(seat: Int, name: String) {
        viewModelScope.launch { Table.rename(prefs, seat, name) }
    }

    private val _confirmForget = MutableStateFlow(false)
    val confirmForget: StateFlow<Boolean> = _confirmForget.asStateFlow()

    /** First tap asks, second tap makes the computer players forget what they learned. */
    fun forgetExperience() {
        if (!_confirmForget.value) {
            _confirmForget.value = true
            return
        }
        _confirmForget.value = false
        viewModelScope.launch { Table.forgetExperience(prefs) }
    }
}

/** ANZEIGE: light/dark, the names of the two computer players and what they have learned. */
class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        // Names live in Texts; the version changes after every rename.
        val version by viewModel.version.collectAsState()
        val colors by LightThemeController.colors.collectAsState()
        val isDark = colors == LightThemeColors.Dark
        val confirmForget by viewModel.confirmForget.collectAsState()

        key(version) {
            Settings(isDark, confirmForget)
        }
    }

    @Composable
    private fun Settings(isDark: Boolean, confirmForget: Boolean) {
        ToolScaffold(title = "Anzeige", onBack = { goBack() }) {
            MenuItem("HELL / DUNKEL", value = if (isDark) "DUNKEL" else "HELL") {
                viewModel.toggleTheme()
            }
            for (seat in listOf(1, 2)) {
                MenuItem(if (seat == 1) "SPIELER LINKS" else "SPIELER RECHTS", value = Texts.seatName(seat)) {
                    val title = if (seat == 1) "Spieler links" else "Spieler rechts"
                    navigateTo(
                        screenFactory = { NameEditorScreen(it, title, Texts.seatName(seat)) },
                        resultCallback = { name -> viewModel.rename(seat, name) },
                    )
                }
            }
            val games = Table.experience.games
            MenuItem("ERFAHRUNG", value = if (confirmForget) "LÖSCHEN?" else if (games == 1) "1 SPIEL" else "$games SPIELE") {
                viewModel.forgetExperience()
            }
            HintText(
                "Links spielt nach dir, rechts vor dir. Tippe auf einen Namen, um ihn zu ändern. " +
                    "Die Computergegner lernen aus euren Spielen; zweimal auf ERFAHRUNG tippen lässt sie neu anfangen."
            )
        }
    }
}

/** Keyboard screen for one name; returns the typed text. */
class NameEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val keyboardOptions = rememberKeyboardOptions()
        val textState = rememberTextFieldState(initial)
        val themeColors by LightThemeController.colors.collectAsState()
        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack(null) },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "OK",
                singleLine = true,
                initialCaps = true,
            )
        }
    }
}
