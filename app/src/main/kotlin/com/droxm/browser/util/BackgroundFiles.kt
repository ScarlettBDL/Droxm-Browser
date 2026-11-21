package com.droxm.browser.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File

/** Importa y carga el fondo de la pantalla de inicio. El archivo elegido se copia a la app, así no depende de permisos ni de que siga existiendo. */
object BackgroundFiles {

    const val MAX_BYTES = 150L * 1024 * 1024

    class ImportResult(val fileName: String?, val isVideo: Boolean, val error: String?)

    fun file(context: Context, name: String): File = File(context.filesDir, name)

    fun delete(context: Context, name: String?) {
        if (name != null) file(context, name).delete()
    }

    /** Copia el archivo. Tiene que llamarse fuera del hilo principal. */
    fun import(context: Context, uri: Uri): ImportResult {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: ""
        val isVideo = mime.startsWith("video/")
        if (!isVideo && !mime.startsWith("image/")) {
            return ImportResult(null, false, "Ese archivo no es una foto ni un video.")
        }

        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        if (size > MAX_BYTES) {
            return ImportResult(null, false, "El archivo es muy pesado (máximo 150 MB).")
        }

        val name = "home_bg_${System.currentTimeMillis()}.${if (isVideo) "vid" else "img"}"
        val out = file(context, name)
        return try {
            val input = resolver.openInputStream(uri)
                ?: return ImportResult(null, false, "No se pudo abrir el archivo.")
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            if (out.length() > MAX_BYTES) {
                out.delete()
                ImportResult(null, false, "El archivo es muy pesado (máximo 150 MB).")
            } else {
                ImportResult(name, isVideo, null)
            }
        } catch (e: Exception) {
            out.delete()
            ImportResult(null, false, "No se pudo copiar el archivo.")
        }
    }

    /**
     * Decodifica la imagen a un tamaño parecido al de la pantalla (no a su resolución original, para no gastar RAM).
     * En Android 9+ también anima GIF y WebP animados.
     */
    fun decodeImage(context: Context, file: File): Drawable? {
        val metrics = context.resources.displayMetrics
        val target = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    decoder.setTargetSampleSize((longest / target).coerceAtLeast(1))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = (longest / target).coerceAtLeast(1)
                }
                val bitmap: Bitmap? = BitmapFactory.decodeFile(file.path, options)
                bitmap?.let { BitmapDrawable(context.resources, it) }
            }
        } catch (e: Throwable) {
            null
        }
    }
}
