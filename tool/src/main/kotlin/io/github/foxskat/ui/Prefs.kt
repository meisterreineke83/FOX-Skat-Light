package io.github.foxskat.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thelightphone.sdk.ui.LightThemeController
import io.github.foxskat.core.ScoreSheet
import io.github.foxskat.core.Texts
import kotlinx.coroutines.flow.first

/** Small settings and the score list, stored in the tool's DataStore. */
class Prefs(private val store: DataStore<Preferences>) {

    /** Applies the saved light/dark choice to the shared theme controller. */
    suspend fun applyTheme() {
        val light = store.data.first()[LIGHT_THEME] ?: false
        if (light) LightThemeController.setLightTheme() else LightThemeController.setDarkTheme()
    }

    suspend fun toggleTheme() {
        LightThemeController.toggle()
        val light = !LightThemeController.isDarkTheme
        store.edit { it[LIGHT_THEME] = light }
    }

    /** Names of the computer players, left and right. */
    suspend fun loadNames(): Pair<String, String> {
        val p = store.data.first()
        return (p[LEFT_NAME] ?: Texts.DEFAULT_LEFT) to (p[RIGHT_NAME] ?: Texts.DEFAULT_RIGHT)
    }

    suspend fun saveName(seat: Int, name: String) {
        store.edit { it[if (seat == 1) LEFT_NAME else RIGHT_NAME] = name }
    }

    suspend fun loadSheet(): ScoreSheet = ScoreSheet.decode(store.data.first()[SHEET] ?: "")

    suspend fun saveSheet(sheet: ScoreSheet) {
        store.edit { it[SHEET] = sheet.encode() }
    }

    companion object {
        private val LIGHT_THEME = booleanPreferencesKey("lightTheme")
        private val SHEET = stringPreferencesKey("scoreSheet")
        private val LEFT_NAME = stringPreferencesKey("leftName")
        private val RIGHT_NAME = stringPreferencesKey("rightName")
    }
}
