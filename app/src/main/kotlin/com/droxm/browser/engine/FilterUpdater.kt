package com.droxm.browser.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Descarga las listas de filtros, las compila con el motor nativo y las carga. Todo en un hilo propio
 * de baja prioridad: nunca toca el hilo de la interfaz ni el de red del WebView.
 */
object FilterUpdater {

    private val SOURCES = listOf(
        "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
        "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
    )
    private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    private const val MAX_BYTES = 30 * 1024 * 1024

    /** Texto para mostrar en Ajustes. */
    var status by mutableStateOf("")
        private set

    var busy by mutableStateOf(false)
        private set

    private lateinit var appContext: Context
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "droxm-filters").apply { priority = Thread.MIN_PRIORITY }
    }

    private val dir get() = File(appContext.filesDir, "filters").apply { mkdirs() }
    private val compiled get() = File(dir, "filters.dxab")
    private val prefs get() = appContext.getSharedPreferences("filters", Context.MODE_PRIVATE)

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!NativeAdblock.available) {
            setStatus("Motor nativo no disponible: se usa la lista básica de dominios.")
            return
        }
        worker.execute {
            val n = NativeAdblock.load(compiled)
            if (n > 0) setStatus(summary(n))
            val stale = System.currentTimeMillis() - prefs.getLong("updated_at", 0) > MAX_AGE_MS
            // La actualización automática solo corre con una conexión sin límite de datos (Wi-Fi).
            if ((n <= 0 || stale) && isUnmetered()) update() else if (n <= 0) setStatus("Sin listas descargadas todavía.")
        }
    }

    fun updateNow() {
        if (!NativeAdblock.available || busy) return
        worker.execute { update() }
    }

    private fun update() {
        postBusy(true)
        try {
            setStatus("Descargando listas…")
            val files = mutableListOf<File>()
            var failed = 0
            for ((name, url) in SOURCES) {
                val target = File(dir, name)
                if (download(url, target) || target.exists()) files += target
                if (!target.exists()) failed++
            }
            if (files.isEmpty()) {
                setStatus("No se pudo descargar ninguna lista. Revisá la conexión.")
                return
            }
            setStatus("Procesando…")
            val tmp = File(dir, "filters.next")
            val result = NativeAdblock.compile(files, tmp)
            if (result.startsWith("ERR:")) {
                tmp.delete()
                setStatus("Error al procesar las listas: ${result.removePrefix("ERR:")}")
                return
            }
            // Se reemplaza el bloque anterior solo si el nuevo se pudo compilar.
            if (!tmp.renameTo(compiled)) {
                compiled.delete()
                tmp.renameTo(compiled)
            }
            val n = NativeAdblock.load(compiled)
            if (n <= 0) {
                setStatus("El bloque de reglas no se pudo cargar.")
                return
            }
            prefs.edit().putLong("updated_at", System.currentTimeMillis()).putInt("rules", n).apply()
            setStatus(summary(n) + if (failed > 0) " (alguna lista no se pudo actualizar)" else "")
        } catch (e: Throwable) {
            setStatus("Error al actualizar: ${e.message}")
        } finally {
            postBusy(false)
        }
    }

    private fun summary(rules: Int): String {
        val at = prefs.getLong("updated_at", 0)
        val age = if (at == 0L) "" else {
            val days = (System.currentTimeMillis() - at) / (24 * 3600 * 1000)
            if (days == 0L) " · actualizado hoy" else " · actualizado hace $days d"
        }
        return "%,d reglas cargadas$age".format(rules)
    }

    private fun download(url: String, target: File): Boolean {
        val tmp = File(target.parentFile, target.name + ".part")
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "DroxmBrowser")
            if (conn.responseCode != 200) return false
            var total = 0
            var head = ByteArray(0)
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (head.isEmpty()) head = buf.copyOf(minOf(n, 256))
                        total += n
                        if (total > MAX_BYTES) return false
                        out.write(buf, 0, n)
                    }
                }
            }
            // Que parezca una lista de filtros y no una página de error.
            val start = String(head, Charsets.UTF_8)
            val looksLikeList = start.startsWith("[Adblock") || start.startsWith("!")
            if (!looksLikeList || total < 1024) return false
            target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    private fun isUnmetered(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setStatus(s: String) = main.post { status = s }
    private fun postBusy(b: Boolean) = main.post { busy = b }
}
