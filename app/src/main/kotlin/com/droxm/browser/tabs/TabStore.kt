package com.droxm.browser.tabs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda las pestañas normales (y sus grupos) en el almacenamiento de la app para restaurarlas al abrir.
 * Las pestañas y grupos de incógnito NUNCA se guardan.
 */
class TabStore(context: Context) {

    class SavedTab(val url: String, val title: String, val groupId: Long)

    class SavedGroup(val id: Long, val name: String, val colorIndex: Int, val collapsed: Boolean)

    class Snapshot(val tabs: List<SavedTab>, val groups: List<SavedGroup>, val activeIndex: Int)

    private val prefs = context.getSharedPreferences("droxm_tabs", Context.MODE_PRIVATE)

    fun save(tabs: List<BrowserTab>, groups: List<TabGroup>, activeNormalId: Long) {
        val normal = tabs.filter { !it.incognito }

        val tabArray = JSONArray()
        normal.forEach { tab ->
            tabArray.put(
                JSONObject()
                    .put("url", tab.url)
                    .put("title", tab.title)
                    .put("group", tab.groupId),
            )
        }

        val groupArray = JSONArray()
        groups.filter { !it.incognito }.forEach { group ->
            groupArray.put(
                JSONObject()
                    .put("id", group.id)
                    .put("name", group.name)
                    .put("color", group.colorIndex)
                    .put("collapsed", group.collapsed),
            )
        }

        val active = normal.indexOfFirst { it.id == activeNormalId }.coerceAtLeast(0)
        prefs.edit()
            .putString("tabs", tabArray.toString())
            .putString("groups", groupArray.toString())
            .putInt("active", active)
            .apply()
    }

    fun load(): Snapshot? {
        val rawTabs = prefs.getString("tabs", null) ?: return null
        return try {
            val tabArray = JSONArray(rawTabs)
            val tabs = (0 until tabArray.length()).map { i ->
                val item = tabArray.getJSONObject(i)
                SavedTab(
                    url = item.optString("url", ""),
                    title = item.optString("title", ""),
                    groupId = item.optLong("group", BrowserTab.NO_GROUP),
                )
            }

            // Versiones anteriores no guardaban grupos: simplemente no hay.
            val groups = prefs.getString("groups", null)?.let { raw ->
                val groupArray = JSONArray(raw)
                (0 until groupArray.length()).map { i ->
                    val item = groupArray.getJSONObject(i)
                    SavedGroup(
                        id = item.getLong("id"),
                        name = item.optString("name", "Grupo"),
                        colorIndex = item.optInt("color", 0),
                        collapsed = item.optBoolean("collapsed", false),
                    )
                }
            } ?: emptyList()

            Snapshot(tabs, groups, prefs.getInt("active", 0))
        } catch (e: Exception) {
            null
        }
    }
}
