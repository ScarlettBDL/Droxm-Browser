package com.droxm.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droxm.browser.data.Bookmarks
import com.droxm.browser.data.History
import com.droxm.browser.util.UrlUtils

class Suggestion(val title: String, val url: String, val bookmark: Boolean)

private const val MAX_SUGGESTIONS = 6

/**
 * Sugerencias mientras se escribe: primero los favoritos, después el historial (lo más reciente primero).
 * Los sitios cuyo dominio empieza con lo escrito van antes. En incógnito no se usa el historial.
 */
fun suggest(query: String, bookmarks: Bookmarks, history: History, useHistory: Boolean): List<Suggestion> {
    val q = query.trim()
    if (q.length < 2) return emptyList()

    fun hits(title: String, url: String) =
        title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)

    fun hostStarts(url: String) = UrlUtils.displayHost(url).startsWith(q, ignoreCase = true)

    val fromBookmarks = bookmarks.items.filter { hits(it.title, it.url) }.map { Suggestion(it.title, it.url, true) }
    val fromHistory = if (useHistory) {
        history.entries.asSequence()
            .filter { hits(it.title, it.url) }
            .map { Suggestion(it.title, it.url, false) }
            .take(200)
            .toList()
    } else {
        emptyList()
    }

    return (fromBookmarks + fromHistory)
        .distinctBy { it.url }
        .sortedWith(compareByDescending<Suggestion> { it.bookmark }.thenByDescending { hostStarts(it.url) })
        .take(MAX_SUGGESTIONS)
}

@Composable
fun SuggestionList(
    query: String,
    suggestions: List<Suggestion>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn {
            // La primera fila siempre es buscar/ir a lo que se escribió (es lo mismo que apretar "Ir").
            item {
                Column(Modifier.clickable { onPick(query) }.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Buscar o ir a", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(query, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider()
            }
            items(suggestions, key = { it.url }) { s ->
                Column(Modifier.clickable { onPick(s.url) }.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = (if (s.bookmark) "★ " else "") + s.title.ifBlank { s.url },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = s.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
