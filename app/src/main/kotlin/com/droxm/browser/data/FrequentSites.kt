package com.droxm.browser.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cuenta cuántas veces se entra a cada sitio (solo pestañas normales, nunca incógnito)
 * para mostrar los más visitados en la pantalla de inicio. Todo queda en el dispositivo.
 */
class FrequentSites(context: Context) {

    class Site(val host: String, val url: String, val count: Int, val last: Long)

    private val prefs = context.getSharedPreferences("droxm_sites", Context.MODE_PRIVATE)

    /** Ordenados de más a menos visitado. */
    val sites = mutableStateListOf<Site>()

    private var lastUrl = ""

    init {
        load()
    }

    fun record(url: String) {
        if (url == lastUrl) return // recargas o redirecciones de la misma página no cuentan doble
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        val host = uri.host?.removePrefix("www.") ?: return
        if (isSearchResults(host, uri)) return
        lastUrl = url

        val now = System.currentTimeMillis()
        val current = sites.toMutableList()
        val index = current.indexOfFirst { it.host == host }
        if (index >= 0) {
            val old = current[index]
            current[index] = Site(host, old.url, old.count + 1, now)
        } else {
            current.add(Site(host, "$scheme://${uri.host}/", 1, now))
        }
        replaceAll(current)
    }

    fun remove(host: String) {
        replaceAll(sites.filter { it.host != host })
    }

    fun clear() {
        lastUrl = ""
        replaceAll(emptyList())
    }

    private fun replaceAll(list: List<Site>) {
        val sorted = list
            .sortedWith(compareByDescending<Site> { it.count }.thenByDescending { it.last })
            .take(Limits.MAX_SITES)
        sites.clear()
        sites.addAll(sorted)
        save()
    }

    // Las búsquedas (duckduckgo.com/?q=...) no son "un sitio que visitás", no se cuentan.
    private fun isSearchResults(host: String, uri: Uri): Boolean =
        host in Limits.SEARCH_HOSTS && uri.getQueryParameter("q") != null

    private fun save() {
        val array = JSONArray()
        sites.forEach { s ->
            array.put(
                JSONObject().put("h", s.host).put("u", s.url).put("c", s.count).put("t", s.last),
            )
        }
        prefs.edit().putString("sites", array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString("sites", null) ?: return
        try {
            val array = JSONArray(raw)
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Site(o.getString("h"), o.getString("u"), o.getInt("c"), o.optLong("t", 0L))
            }
            sites.addAll(list)
        } catch (e: Exception) {
            // Datos dañados: se empieza de cero.
        }
    }

    companion object {
        /** Visitas mínimas para que un sitio cuente como "frecuente" y aparezca en el inicio. */
        const val MIN_VISITS = 3
    }

    private object Limits {
        const val MAX_SITES = 100
        val SEARCH_HOSTS = setOf("duckduckgo.com", "google.com", "bing.com", "search.brave.com")
    }
}
