package com.droxm.browser.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class BarPosition(val label: String) {
    TOP("Arriba"),
    BOTTOM("Abajo"),
}

enum class BarSize(val label: String, val dp: Int) {
    COMPACT("Compacta", 34),
    NORMAL("Normal", 40),
    LARGE("Grande", 48),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

enum class SearchEngine(val label: String, val url: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
}

/**
 * Ajustes de la app. Cada uno es estado de Compose (la interfaz se actualiza sola al cambiarlo)
 * y se guarda al instante en SharedPreferences.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("droxm_settings", Context.MODE_PRIVATE)

    var barPosition by mutableStateOf(loadEnum("bar_position", BarPosition.BOTTOM))
        private set

    var barSize by mutableStateOf(loadEnum("bar_size", BarSize.NORMAL))
        private set

    var showNavButtons by mutableStateOf(prefs.getBoolean("show_nav_buttons", true))
        private set

    var themeMode by mutableStateOf(loadEnum("theme_mode", ThemeMode.SYSTEM))
        private set

    var searchEngine by mutableStateOf(loadEnum("search_engine", SearchEngine.DUCKDUCKGO))
        private set

    /** Cuántas pestañas pueden tener su WebView vivo a la vez (menos = menos RAM). */
    var maxLiveTabs by mutableStateOf(prefs.getInt("max_live_tabs", 3).coerceIn(1, 6))
        private set

    fun updateBarPosition(value: BarPosition) {
        barPosition = value
        prefs.edit().putString("bar_position", value.name).apply()
    }

    fun updateBarSize(value: BarSize) {
        barSize = value
        prefs.edit().putString("bar_size", value.name).apply()
    }

    fun updateShowNavButtons(value: Boolean) {
        showNavButtons = value
        prefs.edit().putBoolean("show_nav_buttons", value).apply()
    }

    fun updateThemeMode(value: ThemeMode) {
        themeMode = value
        prefs.edit().putString("theme_mode", value.name).apply()
    }

    fun updateSearchEngine(value: SearchEngine) {
        searchEngine = value
        prefs.edit().putString("search_engine", value.name).apply()
    }

    fun updateMaxLiveTabs(value: Int) {
        maxLiveTabs = value.coerceIn(1, 6)
        prefs.edit().putInt("max_live_tabs", maxLiveTabs).apply()
    }

    private inline fun <reified E : Enum<E>> loadEnum(key: String, default: E): E {
        val name = prefs.getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
