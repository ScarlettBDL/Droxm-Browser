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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
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
    columns: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var incognitoMode by remember { mutableStateOf(manager.activeTab?.incognito == true) }
    var tabMenu by remember { mutableStateOf<BrowserTab?>(null) }
    var renaming by remember { mutableStateOf<TabGroup?>(null) }
    var openGroupId by remember { mutableStateOf<Long?>(null) }

    // La foto de la pestaña que se estaba viendo se saca al abrir el selector.
    LaunchedEffect(Unit) { manager.activeTab?.let { manager.captureThumbnail(it) } }

    val normalCount = manager.tabs.count { !it.incognito }
    val incognitoCount = manager.tabs.count { it.incognito }
    val shown = manager.tabs.filter { it.incognito == incognitoMode }
    val groups = manager.groups.filter { it.incognito == incognitoMode }
    val ungrouped = shown.filter { tab -> groups.none { it.id == tab.groupId } }

    // Si el grupo abierto se quedó sin pestañas, desaparece y se vuelve a la vista general.
    val openGroup = openGroupId?.let { id -> manager.groups.firstOrNull { it.id == id } }
    BackHandler(enabled = openGroup != null) { openGroupId = null }

    fun select(tab: BrowserTab) {
        manager.select(tab.id)
        onClose()
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        if (openGroup != null) {
            val members = manager.tabs.filter { it.groupId == openGroup.id }
            GroupView(
                manager = manager,
                columns = columns,
                group = openGroup,
                members = members,
                onBack = { openGroupId = null },
                onSelect = ::select,
                onMenu = { tabMenu = it },
                onRename = { renaming = openGroup },
                onNewTab = {
                    manager.newTab(incognito = openGroup.incognito, groupId = openGroup.id)
                    onClose()
                },
                onCloseGroup = {
                    manager.closeGroup(openGroup.id)
                    openGroupId = null
                    if (manager.tabs.none { it.incognito == incognitoMode }) onClose()
                },
            )
        } else {
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
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(groups, key = { "g${it.id}" }) { group ->
                        val members = shown.filter { it.groupId == group.id }
                        GroupCard(
                            group = group,
                            members = members,
                            onOpen = { openGroupId = group.id },
                            onRename = { renaming = group },
                            onColor = { manager.nextColor(group) },
                            onUngroup = { manager.ungroup(group.id) },
                            onCloseGroup = {
                                manager.closeGroup(group.id)
                                if (manager.tabs.none { it.incognito == incognitoMode }) onClose()
                            },
                        )
                    }
                    items(ungrouped, key = { "t${it.id}" }) { tab ->
                        TabCard(
                            tab = tab,
                            active = tab.id == manager.activeId,
                            onSelect = { select(tab) },
                            onMenu = { tabMenu = tab },
                            onClose = { manager.close(tab.id) },
                        )
                    }
                }

                if (shown.isNotEmpty()) {
                    Text(
                        text = "Mantené apretada una pestaña para agruparla.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
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

private fun tabTitle(tab: BrowserTab): String =
    tab.title.ifBlank { if (tab.isHome) "Nueva pestaña" else UrlUtils.displayHost(tab.url) }

/** Miniatura de la pestaña; si no hay, un recuadro de color con la inicial del sitio. */
@Composable
private fun TabPreview(tab: BrowserTab, modifier: Modifier = Modifier) {
    val thumb = tab.thumbnail
    if (thumb != null && !tab.isHome) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier,
        )
    } else {
        val host = if (tab.isHome) "droxm" else UrlUtils.displayHost(tab.url).ifBlank { "?" }
        val hue = (abs(host.hashCode()) % 360).toFloat()
        Box(
            modifier = modifier.background(Color.hsv(hue, 0.35f, 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = host.first().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    active: Boolean,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Card(
        shape = MaterialTheme.shapes.large,
        border = if (active) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onSelect, onLongClick = onMenu),
    ) {
        Column {
            TabPreview(
                tab = tab,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f),
            )
            Row(
                modifier = Modifier.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tabTitle(tab),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar pestaña",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Un grupo se ve como una sola tarjeta con un resumen de sus pestañas; al tocarla se abre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: TabGroup,
    members: List<BrowserTab>,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onUngroup: () -> Unit,
    onCloseGroup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = groupColor(group)

    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, color),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Column {
            // Mosaico de 2x2 con las primeras pestañas del grupo.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (col in 0..1) {
                            val tab = members.getOrNull(row * 2 + col)
                            val cell = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(MaterialTheme.shapes.small)
                            if (tab != null) {
                                TabPreview(tab, cell)
                            } else {
                                Box(cell.background(MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${group.name} (${members.size})",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones del grupo",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    GroupMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onRename = onRename,
                        onColor = onColor,
                        onUngroup = onUngroup,
                        onCloseGroup = onCloseGroup,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onColor: () -> Unit,
    onUngroup: () -> Unit,
    onCloseGroup: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            "Cambiar nombre" to onRename,
            "Cambiar color" to onColor,
            "Desagrupar" to onUngroup,
            "Cerrar grupo" to onCloseGroup,
        ).forEach { (label, action) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

/** Contenido de un grupo: todas sus pestañas en una cuadrícula, con su propio encabezado. */
@Composable
private fun GroupView(
    manager: TabManager,
    columns: Int,
    group: TabGroup,
    members: List<BrowserTab>,
    onBack: () -> Unit,
    onSelect: (BrowserTab) -> Unit,
    onMenu: (BrowserTab) -> Unit,
    onRename: () -> Unit,
    onNewTab: () -> Unit,
    onCloseGroup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(groupColor(group)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${group.name} (${members.size})",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRename),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones del grupo")
                }
                GroupMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onRename = onRename,
                    onColor = { manager.nextColor(group) },
                    onUngroup = {
                        manager.ungroup(group.id)
                        onBack()
                    },
                    onCloseGroup = onCloseGroup,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(members, key = { "t${it.id}" }) { tab ->
                TabCard(
                    tab = tab,
                    active = tab.id == manager.activeId,
                    onSelect = { onSelect(tab) },
                    onMenu = { onMenu(tab) },
                    onClose = { manager.close(tab.id) },
                )
            }
        }

        Button(
            onClick = onNewTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Nueva pestaña en el grupo")
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
