package com.droxm.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droxm.browser.tabs.BrowserTab
import com.droxm.browser.tabs.TabGroup
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.util.UrlUtils

/** Colores que se pueden asignar a un grupo (la cantidad tiene que coincidir con [TabGroup.COLOR_COUNT]). */
private val GroupColors = listOf(
    Color(0xFF6C4DFF),
    Color(0xFF00A3A3),
    Color(0xFFE5484D),
    Color(0xFFF5A524),
    Color(0xFF30A46C),
    Color(0xFF0091FF),
    Color(0xFFD6409F),
    Color(0xFF8E8C99),
)

private fun groupColor(group: TabGroup): Color = GroupColors[group.colorIndex.mod(GroupColors.size)]

@Composable
fun TabSwitcher(
    manager: TabManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var incognitoMode by remember { mutableStateOf(manager.activeTab?.incognito == true) }
    var tabMenu by remember { mutableStateOf<BrowserTab?>(null) }
    var renaming by remember { mutableStateOf<TabGroup?>(null) }

    val normalCount = manager.tabs.count { !it.incognito }
    val incognitoCount = manager.tabs.count { it.incognito }
    val shown = manager.tabs.filter { it.incognito == incognitoMode }
    val groups = manager.groups.filter { it.incognito == incognitoMode }
    val ungrouped = shown.filter { tab -> groups.none { it.id == tab.groupId } }

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

            if (shown.isNotEmpty()) {
                Text(
                    text = "Mantené apretada una pestaña para agruparla o moverla de grupo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                groups.forEach { group ->
                    val members = shown.filter { it.groupId == group.id }

                    item(key = "g${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader(
                            group = group,
                            count = members.size,
                            onToggle = { manager.toggleCollapsed(group) },
                            onRename = { renaming = group },
                            onColor = { manager.nextColor(group) },
                            onUngroup = { manager.ungroup(group.id) },
                            onCloseGroup = {
                                manager.closeGroup(group.id)
                                if (manager.tabs.none { it.incognito == incognitoMode }) onClose()
                            },
                        )
                    }

                    if (!group.collapsed) {
                        items(members, key = { "t${it.id}" }) { tab ->
                            TabCard(
                                tab = tab,
                                stripe = groupColor(group),
                                active = tab.id == manager.activeId,
                                onSelect = {
                                    manager.select(tab.id)
                                    onClose()
                                },
                                onMenu = { tabMenu = tab },
                                onClose = { manager.close(tab.id) },
                            )
                        }
                    }
                }

                items(ungrouped, key = { "t${it.id}" }) { tab ->
                    TabCard(
                        tab = tab,
                        stripe = null,
                        active = tab.id == manager.activeId,
                        onSelect = {
                            manager.select(tab.id)
                            onClose()
                        },
                        onMenu = { tabMenu = tab },
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

    tabMenu?.let { tab ->
        TabMenuDialog(
            manager = manager,
            tab = tab,
            onDismiss = { tabMenu = null },
        )
    }

    renaming?.let { group ->
        RenameGroupDialog(
            group = group,
            onSave = { name ->
                manager.renameGroup(group, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    stripe: Color?,
    active: Boolean,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val title = tab.title.ifBlank {
        if (tab.isHome) "Nueva pestaña" else UrlUtils.displayHost(tab.url)
    }

    Card(
        border = if (active) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onSelect, onLongClick = onMenu),
    ) {
        Row(Modifier.fillMaxSize()) {
            // Franja del color del grupo al que pertenece la pestaña.
            if (stripe != null) {
                Box(
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(stripe),
                )
            }
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar pestaña",
                            modifier = Modifier.size(18.dp),
                        )
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
}

@Composable
private fun GroupHeader(
    group: TabGroup,
    count: Int,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onUngroup: () -> Unit,
    onCloseGroup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(groupColor(group)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${group.name} ($count)",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (group.collapsed) "▸" else "▾",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones del grupo")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Cambiar nombre") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Cambiar color") },
                    onClick = {
                        menuOpen = false
                        onColor()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Desagrupar") },
                    onClick = {
                        menuOpen = false
                        onUngroup()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Cerrar grupo") },
                    onClick = {
                        menuOpen = false
                        onCloseGroup()
                    },
                )
            }
        }
    }
}

/** Menú de una pestaña (al mantenerla apretada): agrupar, mover o cerrar. */
@Composable
private fun TabMenuDialog(
    manager: TabManager,
    tab: BrowserTab,
    onDismiss: () -> Unit,
) {
    val otherGroups = manager.groups.filter { it.incognito == tab.incognito && it.id != tab.groupId }
    val title = tab.title.ifBlank { if (tab.isHome) "Nueva pestaña" else UrlUtils.displayHost(tab.url) }

    fun act(action: () -> Unit) {
        onDismiss()
        action()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            Column {
                DialogRow("Agregar a un grupo nuevo") { act { manager.createGroup(tab) } }
                otherGroups.forEach { group ->
                    DialogRow("Mover a \"${group.name}\"") { act { manager.moveToGroup(tab, group.id) } }
                }
                if (tab.groupId != BrowserTab.NO_GROUP) {
                    DialogRow("Sacar del grupo") { act { manager.moveToGroup(tab, BrowserTab.NO_GROUP) } }
                }
                DialogRow("Cerrar pestaña") { act { manager.close(tab.id) } }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun RenameGroupDialog(
    group: TabGroup,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(group) { mutableStateOf(group.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del grupo") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DialogRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}
