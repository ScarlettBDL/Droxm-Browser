package com.droxm.browser.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Informe de por qué se cerró la app, sin necesidad de herramientas de programador:
 * guarda el error si fue una excepción de Kotlin/Java y, en Android 11+, pregunta al sistema cómo terminó
 * la última vez (memoria baja, error nativo, etc.). Se copia desde Ajustes → Diagnóstico.
 */
object Diagnostics {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        // El registro de memoria de la sesión anterior se guarda aparte: es lo que sirve si esa sesión terminó mal.
        try {
            val cur = File(app.filesDir, "mem_log.txt")
            if (cur.exists() && cur.length() > 0) cur.copyTo(File(app.filesDir, "mem_prev.txt"), overwrite = true)
            cur.delete()
        } catch (_: Throwable) {
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val rt = Runtime.getRuntime()
                val text = "Hilo: ${thread.name}\nMemoria Java: ${(rt.totalMemory() - rt.freeMemory()) shr 20} MB de ${rt.maxMemory() shr 20} MB\n$sw"
                File(app.filesDir, FILE).writeText(text.take(20_000))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private val samples = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** Anota el uso de memoria (se llama cada pocos segundos). Se guardan las últimas 40 muestras para el informe. */
    fun sample(context: Context, extra: String) {
        try {
            val mi = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(mi)
            val s = mi.memoryStats
            fun mb(key: String) = (s[key]?.toLongOrNull() ?: 0L) / 1024
            val line = "${timeFmt.format(Date())} total=${mb("summary.total-pss")}MB java=${mb("summary.java-heap")} " +
                "nativo=${mb("summary.native-heap")} gráficos=${mb("summary.graphics")} | $extra"
            synchronized(samples) {
                samples.addLast(line)
                while (samples.size > 40) samples.removeFirst()
                File(context.filesDir, "mem_log.txt").writeText(samples.joinToString("\n"))
            }
        } catch (_: Throwable) {
        }
    }

    fun report(context: Context): String {
        val out = StringBuilder()
        out.append("Droxm Browser — ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            out.append("RAM total: ${mi.totalMem shr 20} MB, libre: ${mi.availMem shr 20} MB\n")
        }
        val prev = File(context.filesDir, "mem_prev.txt")
        if (prev.exists()) out.append("\n== Memoria de la sesión anterior (cada ~4 s) ==\n").append(prev.readText()).append('\n')
        val log = File(context.filesDir, "mem_log.txt")
        if (log.exists()) out.append("\n== Memoria de esta sesión ==\n").append(log.readText()).append('\n')
        val crash = File(context.filesDir, FILE)
        if (crash.exists()) out.append("\n== Último error de la app ==\n").append(crash.readText()).append('\n')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && am != null) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                out.append("\n== Cómo terminó la app las últimas veces ==\n")
                for (e in exits) {
                    out.append("${fmt.format(Date(e.timestamp))} · ${reasonName(e.reason)} · ${e.description ?: "-"} · RAM usada ${e.rss shr 10} MB\n")
                }
            } catch (_: Throwable) {
            }
        }
        return out.toString()
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun reasonName(code: Int) = when (code) {
        1 -> "cierre normal"
        2 -> "matada por señal"
        3 -> "MEMORIA BAJA (la mató Android)"
        4 -> "ERROR de la app"
        5 -> "ERROR NATIVO"
        6 -> "no responde (ANR)"
        7 -> "falló al iniciar"
        9 -> "uso excesivo de recursos"
        10 -> "la cerró el usuario"
        11 -> "detenida por el usuario"
        13 -> "otro"
        else -> "código $code"
    }
}
