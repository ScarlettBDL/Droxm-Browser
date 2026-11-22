package com.droxm.browser.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Historial de navegación (solo pestañas normales, nunca incógnito). Queda en un archivo del dispositivo.
 * Se guarda en un hilo aparte y agrupando cambios, así navegar no se frena por escribir a disco.
 */
class History(context: Context) {

    class Entry(val url: String, val title: String, val time: Long)

    /** Más reciente primero. */
    val entries = mutableStateListOf<Entry>()

    private val file = File(context.filesDir, "history.json")
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "droxm-history").apply { priority = Thread.MIN_PRIORITY } }
    private val handler = Handler(Looper.getMainLooper())
    private var saveQueued = false

    init {
        load()
    }

    fun record(url: String, title: String) {
        if (!isWeb(url)) return
        val latest = entries.firstOrNull()
        if (latest != null && latest.url == url) {
            // Recarga o la misma página: solo se actualiza el título y la hora.
            entries[0] = Entry(url, title.ifBlank { latest.title }, System.currentTimeMillis())
        } else {
            entries.add(0, Entry(url, title, System.currentTimeMillis()))
            while (entries.size > MAX_ENTRIES) entries.removeAt(entries.lastIndex)
        }
        scheduleSave()
    }

    /** El título suele llegar un instante después de la URL: se corrige la última entrada. */
    fun updateTitle(url: String, title: String) {
        val latest = entries.firstOrNull() ?: return
        if (latest.url != url || title.isBlank() || latest.title == title) return
        entries[0] = Entry(url, title, latest.time)
        scheduleSave()
    }

    fun remove(entry: Entry) {
        entries.remove(entry)
        scheduleSave()
    }

    fun clear() {
        entries.clear()
        scheduleSave()
    }

    /** Escribe ya (por ejemplo cuando la app pasa a segundo plano). */
    fun flush() {
        handler.removeCallbacksAndMessages(null)
        saveQueued = false
        write(entries.toList())
    }

    private fun scheduleSave() {
        if (saveQueued) return
        saveQueued = true
        handler.postDelayed({
            saveQueued = false
            write(entries.toList())
        }, 2000)
    }

    private fun write(snapshot: List<Entry>) {
        io.execute {
            try {
                val array = JSONArray()
                snapshot.forEach {
                    array.put(JSONObject().put("u", it.url).put("n", it.title).put("t", it.time))
                }
                val tmp = File(file.parentFile, "history.json.tmp")
                tmp.writeText(array.toString())
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            } catch (e: Exception) {
                // Si falla un guardado, el próximo lo reintenta con todo el contenido.
            }
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            val list = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Entry(o.getString("u"), o.optString("n"), o.optLong("t"))
            }
            entries.addAll(list)
        } catch (e: Exception) {
            // Archivo dañado: se empieza de cero.
        }
    }

    private fun isWeb(url: String): Boolean {
        val scheme = try {
            Uri.parse(url).scheme?.lowercase()
        } catch (e: Exception) {
            null
        }
        return scheme == "http" || scheme == "https"
    }

    private companion object {
        const val MAX_ENTRIES = 3000
    }
}
