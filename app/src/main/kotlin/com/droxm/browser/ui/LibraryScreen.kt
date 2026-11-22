package com.droxm.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droxm.browser.data.Bookmarks
import com.droxm.browser.data.History
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MAX_SHOWN = 500

/** Favoritos e historial en una sola pantalla, con dos pestañas. */
@Composable
fun LibraryScreen(
    initialTab: Int,
    history: History,
    bookmarks: Bookmarks,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("Favoritos (${bookmarks.items.size})") },
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("Historial") },
                )
                Row(Modifier.weight(1f)) {}
                if (tab == 1) {
                    TextButton(onClick = { confirmClear = true }, enabled = history.entries.isNotEmpty()) {
                        Text("Borrar todo")
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(if (tab == 0) "Buscar en favoritos" else "Buscar en el historial") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            if (tab == 0) {
                val list = bookmarks.items.filter { matches(query, it.title, it.url) }
                if (list.isEmpty()) {
                    EmptyText(if (bookmarks.items.isEmpty()) "Todavía no guardaste favoritos.\nUsá el menú ⋮ → Agregar a favoritos." else "Sin resultados.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(list, key = { it.url }) { item ->
                            PageRow(item.title, item.url, onClick = { onOpen(item.url) }, onDelete = { bookmarks.remove(item) })
                        }
                    }
                }
            } else {
                val list = history.entries.filter { matches(query, it.title, it.url) }.take(MAX_SHOWN)
                if (list.isEmpty()) {
                    EmptyText(if (history.entries.isEmpty()) "El historial está vacío." else "Sin resultados.")
                } else {
                    // Se intercalan encabezados por día (Hoy, Ayer, fecha).
                    val rows = ArrayList<Any>(list.size + 8)
                    var lastDay = ""
                    list.forEach { entry ->
                        val day = dayLabel(entry.time)
                        if (day != lastDay) {
                            rows.add(day)
                            lastDay = day
                        }
                        rows.add(entry)
                    }
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(rows.size) { i ->
                            when (val row = rows[i]) {
                                is String -> Text(
                                    text = row,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                                is History.Entry -> PageRow(
                                    row.title, row.url,
                                    onClick = { onOpen(row.url) },
                                    onDelete = { history.remove(row) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    history.clear()
                    confirmClear = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

private fun matches(query: String, title: String, url: String): Boolean =
    query.isBlank() || title.contains(query.trim(), ignoreCase = true) || url.contains(query.trim(), ignoreCase = true)

private fun dayLabel(time: Long): String {
    fun dayStart(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val diffDays = ((dayStart(System.currentTimeMillis()) - dayStart(time)) / 86_400_000L).toInt()
    return when (diffDays) {
        0 -> "Hoy"
        1 -> "Ayer"
        else -> SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "AR")).format(Date(time))
            .replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun PageRow(title: String, url: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = title.ifBlank { url },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Quitar", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
