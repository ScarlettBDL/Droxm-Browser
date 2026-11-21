package com.droxm.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droxm.browser.tabs.BrowserTab
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.util.UrlUtils

@Composable
fun TabSwitcher(
    manager: TabManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var incognitoMode by remember { mutableStateOf(manager.activeTab?.incognito == true) }
    val normalCount = manager.tabs.count { !it.incognito }
    val incognitoCount = manager.tabs.count { it.incognito }
    val shown = manager.tabs.filter { it.incognito == incognitoMode }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !incognitoMode,
                    onClick = { incognitoMode = false },
                    label = { Text("Pestañas ($normalCount)") },
                )
                FilterChip(
                    selected = incognitoMode,
                    onClick = { incognitoMode = true },
                    label = { Text("Incógnito ($incognitoCount)") },
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = shown.isNotEmpty(),
                    onClick = {
                        manager.closeAll(incognitoMode)
                        onClose()
                    },
                ) {
                    Text("Cerrar todas")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(shown, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        active = tab.id == manager.activeId,
                        onSelect = {
                            manager.select(tab.id)
                            onClose()
                        },
                        onClose = { manager.close(tab.id) },
                    )
                }
            }

            Button(
                onClick = {
                    manager.newTab(incognito = incognitoMode)
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (incognitoMode) "Nueva pestaña incógnito" else "Nueva pestaña")
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = tab.title.ifBlank {
        if (tab.isHome) "Nueva pestaña" else UrlUtils.displayHost(tab.url)
    }

    Card(
        onClick = onSelect,
        border = if (active) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar pestaña", modifier = Modifier.size(18.dp))
                }
            }
            if (!tab.isHome) {
                Text(
                    text = UrlUtils.displayHost(tab.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
