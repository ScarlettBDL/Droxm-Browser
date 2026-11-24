package com.droxm.browser.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.engine.AdBlocker

/**
 * Ajustes que se guardan por sitio: bloqueador de anuncios, ventanas emergentes, versión de escritorio y
 * tamaño del texto. El sitio se identifica por su dominio sin "www." (así `www.example.com` y `example.com` comparten).
 * Nunca se guardan desde incógnito: la interfaz no ofrece el diálogo en esas pestañas.
 */
class SiteSettings(context: Context) {

    private val prefs = context.getSharedPreferences("droxm_site_settings", Context.MODE_PRIVATE)

    /** Cantidad de sitios con algún ajuste propio (para mostrar en Ajustes). */
    var count by mutableIntStateOf(0)
        private set

    init {
        refreshCount()
        publishAllowlist()
    }

    /** Sitios con el bloqueador de anuncios apagado, para que el bloqueador los consulte sin tocar disco. */
    private fun publishAllowlist() {
        val off = HashSet<String>()
        for (k in prefs.all.keys) {
            if (k.endsWith(ADBLOCK_OFF)) off += k.removeSuffix(ADBLOCK_OFF)
        }
        AdBlocker.allowedHosts = off
    }

    private fun refreshCount() {
        count = prefs.all.keys.map { it.substringBefore('#') }.toSet().size
    }

    private fun key(site: String, field: String) = "$site#$field"

    // ---- bloqueador de anuncios

    fun adBlockOff(site: String?): Boolean = site != null && prefs.getBoolean(key(site, "noads"), false)

    fun setAdBlockOff(site: String, off: Boolean) {
        edit { if (off) putBoolean(key(site, "noads"), true) else remove(key(site, "noads")) }
        publishAllowlist()
    }

    // ---- ventanas emergentes

    fun popupsAllowed(site: String?): Boolean = site != null && prefs.getBoolean(key(site, "popups"), false)

    fun setPopupsAllowed(site: String, allowed: Boolean) =
        edit { if (allowed) putBoolean(key(site, "popups"), true) else remove(key(site, "popups")) }

    // ---- escritorio

    /** true = siempre escritorio, false = siempre móvil, null = lo normal. */
    fun desktop(site: String?): Boolean? {
        if (site == null) return null
        val k = key(site, "desktop")
        return if (prefs.contains(k)) prefs.getBoolean(k, false) else null
    }

    fun setDesktop(site: String, value: Boolean?) =
        edit { if (value == null) remove(key(site, "desktop")) else putBoolean(key(site, "desktop"), value) }

    // ---- tamaño del texto

    /** Porcentaje propio del sitio, o null si usa el general. */
    fun textZoom(site: String?): Int? {
        if (site == null) return null
        val k = key(site, "zoom")
        return if (prefs.contains(k)) prefs.getInt(k, 100) else null
    }

    fun setTextZoom(site: String, percent: Int?) =
        edit { if (percent == null) remove(key(site, "zoom")) else putInt(key(site, "zoom"), percent) }

    // ---- general

    fun hasAny(site: String?): Boolean = site != null && prefs.all.keys.any { it.startsWith("$site#") }

    /** Borra todos los ajustes de un sitio. */
    fun reset(site: String) {
        edit { prefs.all.keys.filter { it.startsWith("$site#") }.forEach { remove(it) } }
        publishAllowlist()
    }

    fun clear() {
        prefs.edit().clear().apply()
        count = 0
        publishAllowlist()
    }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        refreshCount()
    }

    companion object {
        private const val ADBLOCK_OFF = "#noads"

        /** Sitio (dominio sin "www.") de una dirección, o null si no tiene. */
        fun siteOf(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val host = try {
                Uri.parse(url).host
            } catch (e: Exception) {
                null
            } ?: return null
            return host.lowercase().removePrefix("www.").ifEmpty { null }
        }
    }
}
