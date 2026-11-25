package com.droxm.browser.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Interruptores para descartar causas cuando algo falla (por ejemplo la memoria en pantalla completa).
 * Se cambian desde Ajustes → Pruebas de diagnóstico. Los de "pestañas nuevas" se leen al crear la pestaña.
 */
object DebugFlags {
    private var prefs: SharedPreferences? = null

    /** No interceptar pedidos de red (sin bloqueo de anuncios). Pestañas nuevas. */
    var noIntercept by mutableStateOf(false)
        private set

    /** No instalar el puente ni el script de filtros cosméticos. Pestañas nuevas. */
    var noCosmetic by mutableStateOf(false)
        private set

    /** Usar el user-agent original del WebView (con "; wv"). Pestañas nuevas. */
    var defaultUa by mutableStateOf(false)
        private set

    /** En pantalla completa no esconder la interfaz ni la página. */
    var keepVisible by mutableStateOf(false)
        private set

    /** Pantalla completa de videos: 0 = la del sistema, 1 = la del sistema sin girar la pantalla, 2 = modo cine (propio, sin pantalla completa del WebView). */
    var fsMode by mutableIntStateOf(0)
        private set

    fun chooseFsMode(value: Int) {
        fsMode = value
        prefs?.edit()?.putInt("fsMode", value)?.apply()
    }

    fun init(context: Context) {
        val p = context.getSharedPreferences("droxm_debug", Context.MODE_PRIVATE)
        prefs = p
        noIntercept = p.getBoolean("noIntercept", false)
        noCosmetic = p.getBoolean("noCosmetic", false)
        defaultUa = p.getBoolean("defaultUa", false)
        keepVisible = p.getBoolean("keepVisible", false)
        fsMode = p.getInt("fsMode", 1).let { if (it == 1) 1 else 0 } // el modo cine ya no existe
    }

    fun set(name: String, value: Boolean) {
        when (name) {
            "noIntercept" -> noIntercept = value
            "noCosmetic" -> noCosmetic = value
            "defaultUa" -> defaultUa = value
            "keepVisible" -> keepVisible = value
        }
        prefs?.edit()?.putBoolean(name, value)?.apply()
    }

    fun summary(): String = "pruebas: sinIntercept=$noIntercept sinCosmético=$noCosmetic uaOriginal=$defaultUa noEsconder=$keepVisible modoFS=$fsMode"
}
