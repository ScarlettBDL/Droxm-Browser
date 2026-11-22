package com.droxm.browser.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** Favoritos: se guardan en el dispositivo, el más nuevo primero. */
class Bookmarks(context: Context) {

    class Item(val url: String, val title: String, val added: Long)

    val items = mutableStateListOf<Item>()

    private val prefs = context.getSharedPreferences("droxm_bookmarks", Context.MODE_PRIVATE)

    init {
        load()
    }

    fun contains(url: String): Boolean = items.any { it.url == url }

    /** Agrega el favorito, o lo quita si ya estaba. */
    fun toggle(url: String, title: String) {
        val index = items.indexOfFirst { it.url == url }
        if (index >= 0) {
            items.removeAt(index)
        } else {
            items.add(0, Item(url, title.ifBlank { url }, System.currentTimeMillis()))
        }
        save()
    }

    fun remove(item: Item) {
        items.remove(item)
        save()
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("u", it.url).put("n", it.title).put("t", it.added)) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString("items", null) ?: return
        try {
            val array = JSONArray(raw)
            items.addAll((0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Item(o.getString("u"), o.optString("n"), o.optLong("t"))
            })
        } catch (e: Exception) {
            // Datos dañados: se empieza de cero.
        }
    }
}
