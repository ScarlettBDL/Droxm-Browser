package com.droxm.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.URLUtil
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import com.droxm.browser.engine.DownloadRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_PDF_BYTES = 200L * 1024 * 1024
private const val MAX_RENDER_WIDTH = 3000
private val ZOOM_STEPS = listOf(1f, 1.5f, 2f)

/** Documento abierto con el PdfRenderer del sistema. El renderer solo admite una página a la vez: todo pasa por un candado. */
private class PdfDoc(private val file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Alto / ancho de la primera página (sirve para reservar lugar mientras las demás se dibujan). */
    val ratio: Float = if (pageCount > 0) {
        renderer.openPage(0).let { page ->
            try {
                page.height.toFloat() / page.width.coerceAtLeast(1)
            } finally {
                page.close()
            }
        }
    } else {
        1.414f
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.Default) {
            check(!closed) { "documento cerrado" }
            val page = renderer.openPage(index)
            try {
                val height = (widthPx * page.height.toFloat() / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    /** Espera a que termine el dibujo en curso y recién ahí libera el archivo. */
    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                try {
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    // Ya estaba cerrado.
                }
                file.delete()
            }
        }
    }
}

private sealed interface PdfState {
    data object Loading : PdfState
    class Ready(val doc: PdfDoc) : PdfState
    class Failed(val message: String) : PdfState
}

/** Baja el PDF a la caché con las cookies y el referer de la pestaña (si no, muchos sitios lo rechazan). */
private fun fetchPdf(context: Context, request: DownloadRequest): File {
    val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "doc-${System.currentTimeMillis()}.pdf")

    val conn = URL(request.url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    request.userAgent?.let { conn.setRequestProperty("User-Agent", it) }
    request.cookie?.let { conn.setRequestProperty("Cookie", it) }
    request.referer?.let { conn.setRequestProperty("Referer", it) }
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        var total = 0L
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_PDF_BYTES) error("el archivo es demasiado grande")
                    output.write(buffer, 0, n)
                }
            }
        }
    } finally {
        conn.disconnect()
    }

    val head = ByteArray(5)
    val read = out.inputStream().use { it.read(head) }
    if (read < 5 || String(head, Charsets.ISO_8859_1) != "%PDF-") {
        out.delete()
        error("el archivo no es un PDF")
    }
    return out
}

/** Visor de PDF: páginas dibujadas bajo demanda (liviano en memoria), zoom y botón para guardar. */
@Composable
fun PdfViewerScreen(
    request: DownloadRequest,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember(request) { mutableStateOf<PdfState>(PdfState.Loading) }
    var zoomIndex by remember { mutableFloatStateOf(0f) }
    val zoom = ZOOM_STEPS[zoomIndex.toInt()]

    LaunchedEffect(request) {
        state = withContext(Dispatchers.IO) {
            try {
                PdfState.Ready(PdfDoc(fetchPdf(context, request)))
            } catch (e: SecurityException) {
                PdfState.Failed("Este PDF tiene contraseña. Descargalo y abrilo con otra app.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PdfState.Failed("No se pudo abrir el PDF (${e.message ?: "error"}).")
            }
        }
    }
    DisposableEffect(request) {
        onDispose { (state as? PdfState.Ready)?.doc?.close() }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar PDF")
                }
                Text(
                    text = URLUtil.guessFileName(request.url, request.contentDisposition, "application/pdf"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { if (zoomIndex > 0f) zoomIndex -= 1f }, enabled = zoomIndex > 0f) { Text("−") }
                TextButton(
                    onClick = { if (zoomIndex < ZOOM_STEPS.lastIndex) zoomIndex += 1f },
                    enabled = zoomIndex < ZOOM_STEPS.lastIndex,
                ) { Text("+") }
                TextButton(onClick = onSave) { Text("Guardar") }
            }

            when (val s = state) {
                PdfState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is PdfState.Failed -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(s.message)
                    Button(onClick = onSave) { Text("Descargar") }
                }
                is PdfState.Ready -> PdfPages(s.doc, zoom, onToggleZoom = {
                    zoomIndex = if (zoomIndex == 0f) 2f else 0f
                })
            }
        }
    }
}

@Composable
private fun PdfPages(doc: PdfDoc, zoom: Float, onToggleZoom: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF3A3A3A)),
    ) {
        val pageWidth = maxWidth * zoom
        val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }.coerceIn(1, MAX_RENDER_WIDTH)

        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleZoom() }) },
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(pageWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(doc.pageCount) { index -> PdfPage(doc, index, widthPx) }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int) {
    var bitmap by remember(doc, index, widthPx) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(doc, index, widthPx) {
        bitmap = try {
            doc.render(index, widthPx).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    val shown = bitmap
    val base = Modifier
        .fillMaxWidth()
        .background(Color.White)
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = "Página ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = base.aspectRatio(shown.width.toFloat() / shown.height),
        )
    } else {
        Box(base.aspectRatio(1f / doc.ratio), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}
