package com.droxm.browser.tabs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda las pestañas normales en el almacenamiento de la app para restaurarlas al abrir.
 * Las pestañas de incógnito NUNCA se guardan.
 */
class TabStore(context: Context) {

    class SavedTab(val url: String, val title: String)

    class Snapshot(val tabs: List<SavedTab>, val activeIndex: Int)

    private val prefs = context.getSharedPreferences("droxm_tabs", Context.MODE_PRIVATE)

    fun save(tabs: List<BrowserTab>, activeNormalId: Long) {
        val normal = tabs.filter { !it.incognito }
        val array = JSONArray()
        normal.forEach { tab ->
            array.put(JSONObject().put("url", tab.url).put("title", tab.title))
        }
        val active = normal.indexOfFirst { it.id == activeNormalId }.coerceAtLeast(0)
        prefs.edit()
            .putString("tabs", array.toString())
            .putInt("active", active)
            .apply()
    }

    fun load(): Snapshot? {
        val raw = prefs.getString("tabs", null) ?: return null
        return try {
            val array = JSONArray(raw)
            val list = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                SavedTab(item.optString("url", ""), item.optString("title", ""))
            }
            Snapshot(list, prefs.getInt("active", 0))
        } catch (e: Exception) {
            null
        }
    }
}
