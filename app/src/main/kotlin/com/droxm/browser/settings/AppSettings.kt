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
    CUSTOM("Personalizado", ""),
}

/** Color principal de la app. DYNAMIC toma los colores del fondo de pantalla (Android 12+). */
enum class AccentColor(val label: String, val argb: Long) {
    DYNAMIC("Dinámico", 0),
    VIOLET("Violeta", 0xFF6C4DFF),
    BLUE("Azul", 0xFF1E6FFF),
    TEAL("Turquesa", 0xFF00A3A3),
    GREEN("Verde", 0xFF1F9D55),
    ORANGE("Naranja", 0xFFF08A24),
    RED("Rojo", 0xFFE5484D),
    PINK("Rosa", 0xFFD6409F),
}

/** Qué tan redondeada es la barra de direcciones. */
enum class BarShape(val label: String, val dp: Int) {
    PILL("Píldora", 24),
    ROUNDED("Redondeada", 12),
    SQUARE("Recta", 4),
}

/** Qué accesos se muestran en la pantalla de inicio. */
enum class HomeSource(val label: String) {
    FREQUENT("Más visitados"),
    BOOKMARKS("Favoritos"),
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

    var blockPopups by mutableStateOf(prefs.getBoolean("block_popups", true))
        private set

    var accent by mutableStateOf(loadEnum("accent", AccentColor.DYNAMIC))
        private set

    /** Tema oscuro con negro puro (ahorra batería en pantallas OLED). */
    var amoled by mutableStateOf(prefs.getBoolean("amoled", false))
        private set

    var barShape by mutableStateOf(loadEnum("bar_shape", BarShape.PILL))
        private set

    var showHomeButton by mutableStateOf(prefs.getBoolean("show_home_button", false))
        private set

    var showNewTabButton by mutableStateOf(prefs.getBoolean("show_new_tab_button", false))
        private set

    var showReloadButton by mutableStateOf(prefs.getBoolean("show_reload_button", true))
        private set

    /** Zoom del texto de las páginas, en porcentaje. */
    var textZoom by mutableStateOf(prefs.getInt("text_zoom", 100).coerceIn(50, 300))
        private set

    var hideBarOnScroll by mutableStateOf(prefs.getBoolean("hide_bar_on_scroll", false))
        private set

    /** Deslizar la barra de izquierda a derecha cambia de pestaña. */
    var swipeTabs by mutableStateOf(prefs.getBoolean("swipe_tabs", false))
        private set

    var homeSource by mutableStateOf(loadEnum("home_source", HomeSource.FREQUENT))
        private set

    var showHomeClock by mutableStateOf(prefs.getBoolean("show_home_clock", false))
        private set

    /** Columnas del selector de pestañas. */
    var tabColumns by mutableStateOf(prefs.getInt("tab_columns", 2).coerceIn(1, 3))
        private set

    /** Dirección de búsqueda propia; `%s` marca dónde va lo que se busca (si falta, se agrega al final). */
    var customSearchUrl by mutableStateOf(prefs.getString("custom_search_url", "") ?: "")
        private set

    /** Dirección de búsqueda efectiva según el buscador elegido. */
    val searchUrl: String
        get() = if (searchEngine == SearchEngine.CUSTOM && customSearchUrl.isNotBlank()) {
            customSearchUrl.trim()
        } else if (searchEngine == SearchEngine.CUSTOM) {
            SearchEngine.DUCKDUCKGO.url
        } else {
            searchEngine.url
        }

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

    fun updateBlockPopups(value: Boolean) {
        blockPopups = value
        prefs.edit().putBoolean("block_popups", value).apply()
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

    fun updateAccent(value: AccentColor) {
        accent = value
        prefs.edit().putString("accent", value.name).apply()
    }

    fun updateAmoled(value: Boolean) {
        amoled = value
        prefs.edit().putBoolean("amoled", value).apply()
    }

    fun updateBarShape(value: BarShape) {
        barShape = value
        prefs.edit().putString("bar_shape", value.name).apply()
    }

    fun updateShowHomeButton(value: Boolean) {
        showHomeButton = value
        prefs.edit().putBoolean("show_home_button", value).apply()
    }

    fun updateShowNewTabButton(value: Boolean) {
        showNewTabButton = value
        prefs.edit().putBoolean("show_new_tab_button", value).apply()
    }

    fun updateShowReloadButton(value: Boolean) {
        showReloadButton = value
        prefs.edit().putBoolean("show_reload_button", value).apply()
    }

    fun updateTextZoom(value: Int) {
        textZoom = value.coerceIn(50, 300)
        prefs.edit().putInt("text_zoom", textZoom).apply()
    }

    fun updateHideBarOnScroll(value: Boolean) {
        hideBarOnScroll = value
        prefs.edit().putBoolean("hide_bar_on_scroll", value).apply()
    }

    fun updateSwipeTabs(value: Boolean) {
        swipeTabs = value
        prefs.edit().putBoolean("swipe_tabs", value).apply()
    }

    fun updateHomeSource(value: HomeSource) {
        homeSource = value
        prefs.edit().putString("home_source", value.name).apply()
    }

    fun updateShowHomeClock(value: Boolean) {
        showHomeClock = value
        prefs.edit().putBoolean("show_home_clock", value).apply()
    }

    fun updateTabColumns(value: Int) {
        tabColumns = value.coerceIn(1, 3)
        prefs.edit().putInt("tab_columns", tabColumns).apply()
    }

    fun updateCustomSearchUrl(value: String) {
        customSearchUrl = value
        prefs.edit().putString("custom_search_url", value).apply()
    }

    private inline fun <reified E : Enum<E>> loadEnum(key: String, default: E): E {
        val name = prefs.getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    companion object {
        const val MAX_DARKNESS = 0.9f
    }
}
