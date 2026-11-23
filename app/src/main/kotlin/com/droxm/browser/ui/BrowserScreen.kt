package com.droxm.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.droxm.browser.data.Bookmarks
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.data.History
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.settings.BarPosition
import com.droxm.browser.tabs.BrowserTab
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.util.Actions
import com.droxm.browser.util.UrlUtils

@Composable
fun BrowserScreen(
    manager: TabManager,
    settings: AppSettings,
    frequentSites: FrequentSites,
    history: History,
    bookmarks: Bookmarks,
    modifier: Modifier = Modifier,
) {
    val tab = manager.activeTab
    val focusManager = LocalFocusManager.current
    var showSwitcher by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // 0 = favoritos, 1 = historial, null = cerrado.
    var library by remember { mutableStateOf<Int?>(null) }
    // Id de la pestaña en la que está abierta la búsqueda en página.
    var findTabId by remember { mutableStateOf<Long?>(null) }
    val finding = tab != null && findTabId == tab.id
    // Lo que se está escribiendo en la barra (vacío si no tiene foco): de acá salen las sugerencias.
    var typing by remember { mutableStateOf("") }

    LaunchedEffect(settings.textZoom) { manager.applyTextZoom() }

    BackHandler(enabled = manager.fullscreen != null || manager.pdf != null || library != null || showSettings || showSwitcher || finding || tab?.canGoBack == true) {
        when {
            manager.fullscreen != null -> manager.exitFullscreen()
            manager.pdf != null -> manager.closePdf()
            library != null -> library = null
            showSettings -> showSettings = false
            showSwitcher -> showSwitcher = false
            finding -> findTabId = null
            else -> tab?.session?.goBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        if (tab != null) {
            Column(Modifier.fillMaxSize()) {
                val bar: @Composable () -> Unit = {
                    key(tab.id) {
                        BrowserBar(
                            manager = manager,
                            tab = tab,
                            settings = settings,
                            bookmarks = bookmarks,
                            onTyping = { typing = it },
                            onFind = { findTabId = tab.id },
                            onOpenLibrary = {
                                focusManager.clearFocus()
                                library = it
                            },
                            onShowTabs = {
                                focusManager.clearFocus()
                                showSwitcher = true
                            },
                            onOpenSettings = {
                                focusManager.clearFocus()
                                showSettings = true
                            },
                        )
                    }
                }

                val onTop = settings.barPosition == BarPosition.TOP
                // Con "ocultar al deslizar", la barra se esconde sola mientras se baja por la página.
                val barVisible = !(settings.hideBarOnScroll && manager.barHidden) || tab.isHome
                val animatedBar: @Composable () -> Unit = {
                    AnimatedVisibility(
                        visible = barVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) { bar() }
                }
                if (onTop) animatedBar()
                if (onTop && finding) key(tab.id) { FindBar(tab.session, onClose = { findTabId = null }) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PageContent(
                        tab = tab,
                        incognitoIsolated = manager.incognitoIsolated,
                        settings = settings,
                        frequentSites = frequentSites,
                        bookmarks = bookmarks,
                        onOpenSite = { url -> manager.navigate(tab, url) },
                    )
                    // Se ignora lo escrito si es la URL actual entera (es lo que aparece seleccionado al tocar la barra).
                    if (typing.isNotBlank() && typing != tab.url) {
                        val suggestions = suggest(typing, bookmarks, history, useHistory = !tab.incognito)
                        SuggestionList(
                            query = typing.trim(),
                            suggestions = suggestions,
                            onPick = { value ->
                                typing = ""
                                focusManager.clearFocus()
                                manager.navigate(tab, value)
                            },
                        )
                    }
                }
                if (!onTop && finding) key(tab.id) { FindBar(tab.session, onClose = { findTabId = null }) }
                if (!onTop) animatedBar()
            }
        }

        if (showSwitcher) {
            TabSwitcher(
                manager = manager,
                columns = settings.tabColumns,
                onClose = { showSwitcher = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        library?.let { initial ->
            LibraryScreen(
                initialTab = initial,
                history = history,
                bookmarks = bookmarks,
                onOpen = { url ->
                    library = null
                    if (tab != null) manager.navigate(tab, url)
                },
                onClose = { library = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                frequentSites = frequentSites,
                sitePermissions = manager.sitePermissions,
                onClose = { showSettings = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Menú de enlace/imagen (al mantener apretado algo en la página).
        manager.hit?.let { LinkMenu(manager, it) }

        manager.pdf?.let { request ->
            PdfViewerScreen(
                request = request,
                onClose = { manager.closePdf() },
                onSave = { manager.downloadFile(request) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        manager.permissionPrompt?.let { PermissionDialog(it, manager::answerPermission) }

        manager.fullscreen?.let { FullscreenOverlay(it.view, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun PageContent(
    tab: BrowserTab,
    incognitoIsolated: Boolean,
    settings: AppSettings,
    frequentSites: FrequentSites,
    bookmarks: Bookmarks,
    onOpenSite: (String) -> Unit,
) {
    if (tab.isHome) {
        HomePage(
            incognito = tab.incognito,
            incognitoIsolated = incognitoIsolated,
            settings = settings,
            frequentSites = frequentSites,
            bookmarks = bookmarks,
            onOpenSite = onOpenSite,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val session = tab.session
    Box(Modifier.fillMaxSize()) {
        if (session != null) {
            key(session) {
                AndroidView(
                    factory = { context -> FrameLayout(context) },
                    update = { container ->
                        val webView = session.view
                        if (webView.parent !== container) {
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            container.removeAllViews()
                            container.addView(
                                webView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    },
                    onRelease = { container -> container.removeAllViews() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (tab.isLoading && tab.progress < 100) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun BrowserBar(
    manager: TabManager,
    tab: BrowserTab,
    settings: AppSettings,
    bookmarks: Bookmarks,
    onTyping: (String) -> Unit,
    onFind: () -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onShowTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var addressFocused by remember { mutableStateOf(false) }
    val tabCount = manager.tabs.count { it.incognito == tab.incognito }
    val barHeight = settings.barSize.dp.dp

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(settings.swipeTabs) {
                if (!settings.swipeTabs) return@pointerInput
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragCancel = { total = 0f },
                    onDragEnd = {
                        // Deslizar hacia la izquierda va a la pestaña siguiente; hacia la derecha, a la anterior.
                        if (total < -120f) manager.switchTab(+1) else if (total > 120f) manager.switchTab(-1)
                    },
                ) { _, delta -> total += delta }
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (addressFocused) {
                // Escribiendo en la barra: se esconden los botones para que la dirección use todo el ancho.
                Spacer(Modifier.width(8.dp))
            } else if (settings.showNavButtons) {
                IconButton(
                    onClick = { tab.session?.goBack() },
                    enabled = tab.canGoBack,
                    modifier = Modifier.size(barHeight),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
                IconButton(
                    onClick = { tab.session?.goForward() },
                    enabled = tab.canGoForward,
                    modifier = Modifier.size(barHeight),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Adelante")
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            if (!addressFocused && settings.showHomeButton) {
                IconButton(onClick = { manager.goHome(tab) }, modifier = Modifier.size(barHeight)) {
                    Icon(Icons.Default.Home, contentDescription = "Inicio")
                }
            }

            AddressField(
                tab = tab,
                height = barHeight,
                corner = settings.barShape.dp.dp,
                showReload = settings.showReloadButton,
                onSubmit = { manager.navigate(tab, it) },
                onFocusChange = { addressFocused = it },
                onTyping = onTyping,
                modifier = Modifier.weight(1f),
            )

            if (!addressFocused && settings.showNewTabButton) {
                IconButton(
                    onClick = { manager.newTab(incognito = tab.incognito) },
                    modifier = Modifier.size(barHeight),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva pestaña")
                }
            }

            if (!addressFocused) IconButton(onClick = onShowTabs, modifier = Modifier.size(barHeight)) {
                val outline = MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, outline, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (tabCount > 99) "99+" else tabCount.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = outline,
                    )
                }
            }

            if (!addressFocused) Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(barHeight)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nueva pestaña") },
                        onClick = {
                            menuOpen = false
                            manager.newTab(incognito = false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Nueva pestaña incógnito") },
                        onClick = {
                            menuOpen = false
                            manager.newTab(incognito = true)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Recargar") },
                        enabled = !tab.isHome,
                        onClick = {
                            menuOpen = false
                            tab.session?.reload()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (bookmarks.contains(tab.url)) "Quitar de favoritos ★" else "Agregar a favoritos") },
                        enabled = !tab.isHome,
                        onClick = {
                            menuOpen = false
                            bookmarks.toggle(tab.url, tab.title)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Buscar en la página") },
                        enabled = !tab.isHome && tab.session != null,
                        onClick = {
                            menuOpen = false
                            onFind()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar enlace") },
                        enabled = !tab.isHome,
                        onClick = {
                            menuOpen = false
                            Actions.copy(context, tab.url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        enabled = !tab.isHome,
                        onClick = {
                            menuOpen = false
                            Actions.share(context, tab.url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (tab.desktop) "Sitio de escritorio ✓" else "Sitio de escritorio") },
                        onClick = {
                            menuOpen = false
                            manager.toggleDesktop(tab)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Favoritos") },
                        onClick = {
                            menuOpen = false
                            onOpenLibrary(0)
                        },
                    )
                    if (!tab.incognito) DropdownMenuItem(
                        text = { Text("Historial") },
                        onClick = {
                            menuOpen = false
                            onOpenLibrary(1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Descargas") },
                        onClick = {
                            menuOpen = false
                            Actions.openDownloads(context)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Cerrar pestaña") },
                        onClick = {
                            menuOpen = false
                            manager.close(tab.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Ajustes") },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressField(
    tab: BrowserTab,
    height: Dp,
    corner: Dp,
    showReload: Boolean,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onTyping: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }

    // Avisa lo escrito para mostrar sugerencias; sin foco se limpia.
    LaunchedEffect(focused, typed.text) { onTyping(if (focused) typed.text else "") }

    // Sin foco se ve solo el dominio; con foco, la URL completa seleccionada para reemplazarla.
    val shown = if (focused) typed else TextFieldValue(UrlUtils.displayHost(tab.url))

    Surface(
        shape = RoundedCornerShape(corner),
        color = colors.surfaceVariant,
        // Altura mínima: al escribir, la barra crece para mostrar la dirección completa (varias líneas).
        modifier = modifier.heightIn(min = height),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!focused && UrlUtils.isSecure(tab.url)) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Conexión segura",
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }

            BasicTextField(
                value = shown,
                onValueChange = { value ->
                    if (focused) {
                        if (value.text.contains('\n')) {
                            // Algunos teclados mandan un salto de línea en vez de "Ir": se toma como enviar.
                            onSubmit(value.text.replace("\n", ""))
                            focusManager.clearFocus()
                        } else {
                            typed = value
                        }
                    }
                },
                // Sin foco: una línea con el dominio. Con foco: la URL completa, en hasta 5 líneas.
                singleLine = !focused,
                maxLines = if (focused) 5 else 1,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        onSubmit(typed.text)
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused && !focused) {
                            typed = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                        }
                        if (state.isFocused != focused) onFocusChange(state.isFocused)
                        focused = state.isFocused
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (shown.text.isEmpty()) {
                            Text(
                                text = "Buscar o escribir dirección",
                                color = colors.onSurfaceVariant,
                                fontSize = 15.sp,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )

            if (focused && typed.text.isNotEmpty()) {
                IconButton(onClick = { typed = TextFieldValue("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                }
            } else if (!tab.isHome && (showReload || tab.isLoading)) {
                IconButton(
                    onClick = { if (tab.isLoading) tab.session?.stopLoading() else tab.session?.reload() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (tab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (tab.isLoading) "Detener" else "Recargar",
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}
