package com.droxm.browser.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.util.BackgroundFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.random.Random

enum class HomeBgMode(val label: String) {
    NATURE("Naturaleza"),
    MINE("Mis fondos"),
    GRADIENT("Degradado"),
}

/**
 * Fondo de la pantalla de inicio. Tres orígenes:
 * - Naturaleza: fotos destacadas de Wikimedia Commons, descargadas a la app y elegidas al azar.
 * - Mis fondos: fotos y videos que subís vos; podés fijar uno o rotar entre todos cada vez que abrís el navegador.
 * - Degradado: sin imagen.
 * El fondo "actual" se elige una vez al crear esta clase (al abrir el navegador) y no cambia mientras se usa.
 */
class HomeBackgrounds(private val context: Context) {

    class Item(val file: String, val isVideo: Boolean)

    class Current(val file: File, val isVideo: Boolean, val credit: String?)

    private val prefs = context.getSharedPreferences("droxm_home_bg", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "droxm-nature").apply { priority = Thread.MIN_PRIORITY } }
    private val natureDir get() = File(context.filesDir, "nature").apply { mkdirs() }

    /** Lo que subió el usuario. */
    val items = mutableStateListOf<Item>()

    var mode by mutableStateOf(HomeBgMode.NATURE)
        private set

    /** Elegir uno de "mis fondos" al azar cada vez que se abre el navegador. */
    var rotate by mutableStateOf(false)
        private set

    /** El fondo fijo de "mis fondos" cuando no se rota. */
    var selected by mutableStateOf<String?>(null)
        private set

    var current by mutableStateOf<Current?>(null)
        private set

    var natureCount by mutableIntStateOf(0)
        private set

    var natureStatus by mutableStateOf("")
        private set

    var natureBusy by mutableStateOf(false)
        private set

    init {
        load()
        natureCount = natureFiles().size
        pick()
        // Si hace falta, se bajan fotos nuevas en segundo plano (solo con una conexión sin límite de datos).
        if (usesNature() && (natureCount < MIN_NATURE || stale()) && isUnmetered()) {
            worker.execute { fetchNature() }
        }
    }

    // ------------------------------------------------------------------ mis fondos

    /** Copia un archivo elegido. Devuelve un mensaje de error o null si salió bien. Va fuera del hilo principal. */
    fun importFile(uri: Uri): String? {
        val result = BackgroundFiles.import(context, uri)
        val name = result.fileName ?: return result.error
        main.post {
            items.add(Item(name, result.isVideo))
            // Lo último que se agregó se ve enseguida (salvo que se esté rotando).
            mode = HomeBgMode.MINE
            if (!rotate) selected = name
            save()
            pick()
        }
        return null
    }

    fun remove(item: Item) {
        items.removeAll { it.file == item.file }
        Thread { BackgroundFiles.delete(context, item.file) }.start()
        if (selected == item.file) selected = items.firstOrNull()?.file
        if (items.isEmpty() && mode == HomeBgMode.MINE) mode = HomeBgMode.NATURE
        save()
        pick()
    }

    fun select(item: Item) {
        selected = item.file
        rotate = false
        mode = HomeBgMode.MINE
        save()
        pick()
    }

    fun updateRotate(value: Boolean) {
        rotate = value
        if (value) mode = HomeBgMode.MINE
        save()
        // Al activarlo se sigue viendo el fondo actual; al apagarlo vuelve el fijo.
        if (!value) pick()
    }

    fun updateMode(value: HomeBgMode) {
        mode = value
        save()
        pick()
        if (usesNature() && (natureCount < MIN_NATURE || stale()) && isUnmetered()) {
            worker.execute { fetchNature() }
        }
    }

    // ------------------------------------------------------------------ naturaleza

    /** Baja fotos nuevas ahora (aunque haya datos móviles). */
    fun refreshNature() {
        if (natureBusy) return
        natureBusy = true
        natureStatus = "Buscando fotos…"
        worker.execute { fetchNature() }
    }

    private fun usesNature() = mode == HomeBgMode.NATURE || (mode == HomeBgMode.MINE && items.isEmpty())

    private fun natureFiles(): List<File> =
        natureDir.listFiles { f -> f.extension == "jpg" && !f.name.startsWith("tmp_") }?.toList() ?: emptyList()

    private fun credit(name: String): String? = try {
        File(natureDir, "index.json").takeIf { it.exists() }?.let { JSONObject(it.readText()).optString(name).ifBlank { null } }
    } catch (e: Exception) {
        null
    }

    private fun stale() = System.currentTimeMillis() - prefs.getLong("nature_at", 0) > MAX_AGE_MS

    private fun isUnmetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchNature() {
        try {
            val candidates = NatureSource.candidates()
            if (candidates.isEmpty()) {
                post("No se pudo obtener la lista de fotos. Revisá la conexión.")
                return
            }
            val dir = natureDir
            val stamp = System.currentTimeMillis()
            val downloaded = ArrayList<Pair<File, String>>()
            for ((i, c) in candidates.shuffled().take(MAX_TRIES).withIndex()) {
                if (downloaded.size >= TARGET_COUNT) break
                val tmp = File(dir, "tmp_$i.jpg")
                if (NatureSource.download(c.url, tmp)) downloaded.add(tmp to c.credit) else tmp.delete()
            }
            if (downloaded.size < MIN_NATURE) {
                downloaded.forEach { it.first.delete() }
                post("No se pudieron descargar fotos suficientes.")
                return
            }
            // Recién con el lote completo se reemplazan las fotos anteriores.
            natureFiles().forEach { it.delete() }
            val index = JSONObject()
            downloaded.forEachIndexed { i, (tmp, credit) ->
                val name = "n${stamp}_$i.jpg"
                if (tmp.renameTo(File(dir, name))) index.put(name, credit)
            }
            File(dir, "index.json").writeText(index.toString())
            prefs.edit().putLong("nature_at", System.currentTimeMillis()).apply()
            main.post {
                natureCount = natureFiles().size
                pick()
            }
            post("${downloaded.size} fotos descargadas.")
        } catch (e: Exception) {
            post("Error al descargar fotos: ${e.message ?: "desconocido"}")
        } finally {
            main.post { natureBusy = false }
        }
    }

    private fun post(status: String) = main.post { natureStatus = status }

    // ------------------------------------------------------------------ elección y persistencia

    private fun natureCurrent(): Current? =
        natureFiles().randomOrNull()?.let { Current(it, false, credit(it.name)) }

    /** Elige el fondo que se va a ver. */
    private fun pick() {
        current = when (mode) {
            HomeBgMode.GRADIENT -> null
            HomeBgMode.NATURE -> natureCurrent()
            HomeBgMode.MINE -> {
                val existing = items.filter { BackgroundFiles.file(context, it.file).exists() }
                val item = if (rotate) existing.randomOrNull() else existing.firstOrNull { it.file == selected } ?: existing.firstOrNull()
                item?.let { Current(BackgroundFiles.file(context, it.file), it.isVideo, null) } ?: natureCurrent()
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("f", it.file).put("v", it.isVideo)) }
        prefs.edit()
            .putString("items", array.toString())
            .putString("mode", mode.name)
            .putBoolean("rotate", rotate)
            .putString("selected", selected)
            .putBoolean("migrated", true)
            .apply()
    }

    private fun load() {
        try {
            val raw = prefs.getString("items", null)
            if (raw != null) {
                val array = JSONArray(raw)
                (0 until array.length()).forEach { i ->
                    val o = array.getJSONObject(i)
                    items.add(Item(o.getString("f"), o.optBoolean("v")))
                }
            }
        } catch (e: Exception) {
            // Lista dañada: se empieza de cero.
        }
        rotate = prefs.getBoolean("rotate", false)
        selected = prefs.getString("selected", null)
        mode = HomeBgMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: HomeBgMode.NATURE

        // Versiones anteriores guardaban un solo fondo en los ajustes: pasa a ser el primero de "mis fondos".
        if (!prefs.getBoolean("migrated", false)) {
            val old = context.getSharedPreferences("droxm_settings", Context.MODE_PRIVATE)
            val name = old.getString("home_bg_file", null)
            if (name != null && BackgroundFiles.file(context, name).exists()) {
                items.add(Item(name, old.getBoolean("home_bg_video", false)))
                selected = name
                mode = HomeBgMode.MINE
            }
            save()
        }
    }

    private companion object {
        const val MIN_NATURE = 3
        const val TARGET_COUNT = 8
        const val MAX_TRIES = 14
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }
}

/**
 * Fotos de naturaleza de Wikimedia Commons ("Featured pictures": imágenes destacadas por la comunidad, con licencia libre).
 * Se piden miniaturas de 1600 px de ancho, que pesan poco.
 */
private object NatureSource {

    class Candidate(val url: String, val credit: String)

    private const val API = "https://commons.wikimedia.org/w/api.php"
    private const val UA = "DroxmBrowser/0.1 (https://github.com/ScarlettBDL/Droxm-Browser)"
    private const val MAX_BYTES = 4 * 1024 * 1024

    private val CATEGORIES = listOf(
        "Featured pictures of landscapes",
        "Featured pictures of mountains",
        "Featured pictures of nature",
        "Featured pictures of forests",
        "Featured pictures of lakes",
        "Featured pictures of beaches",
    )

    fun candidates(): List<Candidate> {
        val cats = CATEGORIES.shuffled().take(3)
        val found = ArrayList<Candidate>()
        // 1) Búsqueda en la categoría y sus subcategorías, desde un punto al azar (así cada lote es distinto).
        for (cat in cats) {
            val search = "deepcategory:\"$cat\" filetype:bitmap"
            var list = query("generator=search&gsrnamespace=6&gsrlimit=50&gsroffset=${Random.nextInt(0, 120)}&gsrsearch=${enc(search)}")
            if (list.isEmpty()) list = query("generator=search&gsrnamespace=6&gsrlimit=50&gsrsearch=${enc(search)}")
            found.addAll(list)
        }
        // 2) Si no alcanzó, los archivos que están directo en las categorías.
        if (found.size < 6) {
            for (cat in cats) {
                found.addAll(query("generator=categorymembers&gcmtitle=${enc("Category:" + cat.replace(' ', '_'))}&gcmtype=file&gcmlimit=50"))
            }
        }
        return found.distinctBy { it.url }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun query(generator: String): List<Candidate> = try {
        val url = "$API?action=query&format=json&formatversion=2&$generator" +
            "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=1600"
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONArray("pages")
        val out = ArrayList<Candidate>()
        if (pages != null) {
            for (i in 0 until pages.length()) {
                val page = pages.getJSONObject(i)
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val thumb = info.optString("thumburl")
                val w = info.optInt("width")
                val h = info.optInt("height")
                if (thumb.isBlank() || info.optString("mime") != "image/jpeg" || w < 1600 || h <= 0) continue
                val ratio = w.toFloat() / h
                if (ratio < 0.7f || ratio > 2.2f) continue // nada de panorámicas enormes
                val title = page.optString("title").removePrefix("File:").substringBeforeLast('.').replace('_', ' ')
                out.add(Candidate(thumb, title.take(90)))
            }
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, dest: File): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", UA)
        try {
            if (conn.responseCode != 200) {
                false
            } else {
                var total = 0
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) error("demasiado grande")
                            out.write(buf, 0, n)
                        }
                    }
                }
                total > 20 * 1024
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }
}
