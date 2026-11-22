package com.droxm.browser.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.engine.AdBlocker

enum class BarPosition(val label: String) {
    TOP("Arriba"),
    BOTTOM("Abajo"),
}

enum class SitesPosition(val label: String) {
    TOP("Arriba"),
    CENTER("Centro"),
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

    /** Nombre del archivo de fondo (guardado en la carpeta de la app) o null si no hay. */
    var homeBgFile by mutableStateOf(prefs.getString("home_bg_file", null))
        private set

    var homeBgIsVideo by mutableStateOf(prefs.getBoolean("home_bg_video", false))
        private set

    /** Cuánto se oscurece el fondo de inicio: 0 = tal cual, 0.9 = casi negro. */
    var homeDarkness by mutableStateOf(prefs.getFloat("home_darkness", 0.35f).coerceIn(0f, MAX_DARKNESS))
        private set

    var showHomeTitle by mutableStateOf(prefs.getBoolean("show_home_title", true))
        private set

    var showFrequentSites by mutableStateOf(prefs.getBoolean("show_frequent_sites", true))
        private set

    var adBlock by mutableStateOf(prefs.getBoolean("ad_block", true))
        private set

    /** Cuántos sitios frecuentes se muestran en el inicio. */
    var homeSiteCount by mutableStateOf(prefs.getInt("home_site_count", 8).coerceIn(1, 12))
        private set

    var homeSitesPosition by mutableStateOf(loadEnum("home_sites_position", SitesPosition.CENTER))
        private set

    init {
        AdBlocker.enabled = adBlock
    }

    fun updateHomeBackground(fileName: String?, isVideo: Boolean) {
        homeBgFile = fileName
        homeBgIsVideo = isVideo
        prefs.edit().putString("home_bg_file", fileName).putBoolean("home_bg_video", isVideo).apply()
    }

    fun updateHomeDarkness(value: Float) {
        homeDarkness = value.coerceIn(0f, MAX_DARKNESS)
        prefs.edit().putFloat("home_darkness", homeDarkness).apply()
    }

    fun updateShowHomeTitle(value: Boolean) {
        showHomeTitle = value
        prefs.edit().putBoolean("show_home_title", value).apply()
    }

    fun updateHomeSiteCount(value: Int) {
        homeSiteCount = value.coerceIn(1, 12)
        prefs.edit().putInt("home_site_count", homeSiteCount).apply()
    }

    fun updateHomeSitesPosition(value: SitesPosition) {
        homeSitesPosition = value
        prefs.edit().putString("home_sites_position", value.name).apply()
    }

    fun updateShowFrequentSites(value: Boolean) {
        showFrequentSites = value
        prefs.edit().putBoolean("show_frequent_sites", value).apply()
    }

    fun updateAdBlock(value: Boolean) {
        adBlock = value
        AdBlocker.enabled = value
        prefs.edit().putBoolean("ad_block", value).apply()
    }

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

    companion object {
        const val MAX_DARKNESS = 0.9f
    }
}
