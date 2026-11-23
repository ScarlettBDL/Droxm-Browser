package com.droxm.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droxm.browser.data.HomeBgMode
import com.droxm.browser.data.HomeBackgrounds
import com.droxm.browser.util.BackgroundFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes del fondo de inicio: origen (naturaleza / mis fondos / degradado), galería propia y rotación. */
@Composable
fun BackgroundsSettings(backgrounds: HomeBackgrounds) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            error = null
            val errors = withContext(Dispatchers.IO) { uris.mapNotNull { backgrounds.importFile(it) } }
            if (errors.isNotEmpty()) error = errors.first()
            importing = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fondo", style = MaterialTheme.typography.bodyLarge)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeBgMode.entries.forEach { m ->
                FilterChip(
                    selected = backgrounds.mode == m,
                    onClick = { backgrounds.updateMode(m) },
                    label = { Text(m.label) },
                )
            }
        }

        if (backgrounds.mode == HomeBgMode.NATURE || (backgrounds.mode == HomeBgMode.MINE && backgrounds.items.isEmpty())) {
            Text(
                text = "Fotos de paisajes de Wikimedia Commons (imágenes destacadas, licencia libre). Se bajan solas con Wi-Fi y cambian cada vez que abrís el navegador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = backgrounds.current
            Text(
                text = buildString {
                    append("${backgrounds.natureCount} fotos guardadas")
                    current?.credit?.let { append(" · Ahora: $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (backgrounds.natureStatus.isNotBlank()) {
                Text(backgrounds.natureStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { backgrounds.refreshNature() }, enabled = !backgrounds.natureBusy) {
                Text(if (backgrounds.natureBusy) "Descargando…" else "Descargar fotos nuevas")
            }
        }

        Text("Mis fondos", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Fotos, GIF animados o videos (hasta 150 MB cada uno). Tocá uno para fijarlo; la X lo quita.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            backgrounds.items.forEach { item ->
                val active = backgrounds.mode == HomeBgMode.MINE && !backgrounds.rotate && backgrounds.selected == item.file
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(if (active) 3.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable { backgrounds.select(item) },
                ) {
                    val thumb by produceState<ImageBitmap?>(null, item.file) {
                        value = withContext(Dispatchers.IO) { thumbnail(BackgroundFiles.file(context, item.file), item.isVideo) }
                    }
                    thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp)) }
                    if (item.isVideo) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { backgrounds.remove(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !importing) { picker.launch(arrayOf("image/*", "video/*")) },
                contentAlignment = Alignment.Center,
            ) {
                if (importing) Text("…") else Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Rotar mis fondos", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (backgrounds.items.size < 2) "Agregá al menos 2 para rotar. Cada vez que abras el navegador se elige uno al azar."
                    else "Cada vez que abrís el navegador se elige uno al azar entre los de arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = backgrounds.rotate,
                onCheckedChange = { backgrounds.updateRotate(it) },
                enabled = backgrounds.items.size >= 2,
            )
        }
    }
}

/** Miniatura chica de una foto o del primer cuadro de un video. */
private fun thumbnail(file: File, isVideo: Boolean): ImageBitmap? {
    if (!file.exists()) return null
    return try {
        val bitmap: Bitmap? = if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0)
            } finally {
                retriever.release()
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1)
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bitmap?.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}
