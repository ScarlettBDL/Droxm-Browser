package com.droxm.browser.data

import android.Manifest
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Permisos que una página puede pedir. */
enum class SitePerm(val label: String, val androidPermissions: List<String>) {
    CAMERA("la cámara", listOf(Manifest.permission.CAMERA)),
    MICROPHONE("el micrófono", listOf(Manifest.permission.RECORD_AUDIO)),
    LOCATION("tu ubicación", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
}

/** Decisiones guardadas por sitio ("Permitir" / "Bloquear" con "Recordar"). Nunca se guardan desde incógnito. */
class SitePermissions(context: Context) {

    private val prefs = context.getSharedPreferences("droxm_site_permissions", Context.MODE_PRIVATE)

    var count by mutableIntStateOf(prefs.all.size)
        private set

    /** true = permitido, false = bloqueado, null = sin decisión. */
    fun get(site: String, perm: SitePerm): Boolean? {
        val key = key(site, perm)
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun set(site: String, perm: SitePerm, allowed: Boolean) {
        prefs.edit().putBoolean(key(site, perm), allowed).apply()
        count = prefs.all.size
    }

    fun clear() {
        prefs.edit().clear().apply()
        count = 0
    }

    private fun key(site: String, perm: SitePerm) = "${site.lowercase()}#${perm.name}"
}
