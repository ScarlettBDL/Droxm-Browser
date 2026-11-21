package com.droxm.browser.util

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.droxm.browser.engine.DownloadRequest

/**
 * Descargas con el DownloadManager del sistema: aparecen en la barra de notificaciones, se guardan en la
 * carpeta Descargas y se pueden abrir desde la notificación. Tiene que crearse dentro de onCreate de la actividad.
 */
class Downloader(private val activity: ComponentActivity) {

    private var pending: DownloadRequest? = null

    // Hasta Android 9 hace falta pedir permiso para escribir en Descargas; desde Android 10 no.
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pending
        pending = null
        if (request == null) return@registerForActivityResult
        if (granted) enqueue(request) else toast("Sin permiso para guardar archivos")
    }

    fun start(request: DownloadRequest) {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = request
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            enqueue(request)
        }
    }

    private fun enqueue(request: DownloadRequest) {
        val scheme = Uri.parse(request.url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            toast("Este tipo de enlace no se puede descargar")
            return
        }

        val name = fileName(request)
        try {
            val manager = activity.getSystemService(DownloadManager::class.java)
            val dm = DownloadManager.Request(Uri.parse(request.url)).apply {
                request.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                request.cookie?.let { addRequestHeader("Cookie", it) }
                request.userAgent?.let { addRequestHeader("User-Agent", it) }
                request.referer?.let { addRequestHeader("Referer", it) }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            manager.enqueue(dm)
            toast("Descargando $name")
        } catch (e: Exception) {
            toast("No se pudo iniciar la descarga")
        }
    }

    private fun fileName(request: DownloadRequest): String {
        val guessed = URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType)
        if (!request.isImage) return guessed
        // Las imágenes sueltas suelen venir sin extensión ("imagen?id=3"): se les pone una para que la galería las reconozca.
        val ext = guessed.substringAfterLast('.', "").lowercase()
        if (ext in IMAGE_EXTENSIONS) return guessed
        return guessed.removeSuffix(".$ext").removeSuffix(".") + ".jpg"
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "svg")
    }
}
