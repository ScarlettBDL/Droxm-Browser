package com.droxm.browser.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.droxm.browser.util.BackgroundFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Foto (o GIF/WebP animado en Android 9+) a pantalla completa, recortada al centro. */
@Composable
fun ImageBackdrop(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) { BackgroundFiles.decodeImage(context, file) }
    }

    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { imageView ->
            imageView.setImageDrawable(drawable)
            val d = drawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d is AnimatedImageDrawable) d.start()
        },
        onRelease = { imageView ->
            val d = imageView.drawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d is AnimatedImageDrawable) d.stop()
            imageView.setImageDrawable(null)
        },
        modifier = modifier,
    )
}

/** Video en loop, sin sonido, a pantalla completa y recortado al centro. Se libera al salir de la pantalla de inicio. */
@Composable
fun VideoBackdrop(file: File, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> LoopingVideoView(ctx) },
        update = { view -> view.setSource(file.absolutePath) },
        onRelease = { view -> view.release() },
        modifier = modifier,
    )
}

private class LoopingVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var path: String? = null
    private var clipWidth = 0
    private var clipHeight = 0
    private var paused = false

    init {
        surfaceTextureListener = this
    }

    fun setSource(newPath: String) {
        if (newPath == path && player != null) return
        path = newPath
        if (surface != null) startPlayer()
    }

    fun release() {
        player?.release()
        player = null
    }

    private fun startPlayer() {
        release()
        val source = path ?: return
        val target = surface ?: return
        try {
            player = MediaPlayer().apply {
                setSurface(target)
                setDataSource(source)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h ->
                    clipWidth = w
                    clipHeight = h
                    applyCenterCrop()
                }
                setOnPreparedListener { mp -> if (!paused) mp.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (e: Exception) {
            release()
        }
    }

    // TextureView estira el video a todo el view; esta matriz lo devuelve a su proporción real y lo recorta al centro.
    private fun applyCenterCrop() {
        if (clipWidth == 0 || clipHeight == 0 || width == 0 || height == 0) return
        val viewRatio = width.toFloat() / height
        val videoRatio = clipWidth.toFloat() / clipHeight
        var scaleX = 1f
        var scaleY = 1f
        if (videoRatio > viewRatio) scaleX = videoRatio / viewRatio else scaleY = viewRatio / videoRatio
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
        setTransform(matrix)
    }

    // Con la app en segundo plano no hay motivo para seguir decodificando video.
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        paused = visibility != View.VISIBLE
        val p = player ?: return
        try {
            if (paused) {
                if (p.isPlaying) p.pause()
            } else {
                p.start()
            }
        } catch (e: IllegalStateException) {
            // El reproductor todavía se está preparando; arranca solo cuando termine.
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        if (path != null) startPlayer()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
