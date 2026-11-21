package com.droxm.browser.util

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Acciones sueltas del sistema que usan varias pantallas. */
object Actions {

    fun copy(context: Context, text: String, message: String = "Enlace copiado") {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Droxm", text))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (e: Exception) {
            // No hay con qué compartir.
        }
    }

    /** Abre la lista de descargas del sistema. */
    fun openDownloads(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir las descargas", Toast.LENGTH_SHORT).show()
        }
    }

    fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
