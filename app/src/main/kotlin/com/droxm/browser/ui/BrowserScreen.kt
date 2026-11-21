package com.droxm.browser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
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
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.settings.BarPosition
import com.droxm.browser.tabs.BrowserTab
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.util.UrlUtils

@Composable
fun BrowserScreen(
    manager: TabManager,
    settings: AppSettings,
    frequentSites: FrequentSites,
    modifier: Modifier = Modifier,
) {
    val tab = manager.activeTab
    val focusManager = LocalFocusManager.current
    var showSwitcher by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettings || showSwitcher || tab?.canGoBack == true) {
        when {
            showSettings -> showSettings = false
            showSwitcher -> showSwitcher = false
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
                if (onTop) bar()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PageContent(
                        tab = tab,
                        incognitoIsolated = manager.incognitoIsolated,
                        settings = settings,
                        frequentSites = frequentSites,
                        onOpenSite = { url -> manager.navigate(tab, url) },
                    )
                }
                if (!onTop) bar()
            }
        }

        if (showSwitcher) {
            TabSwitcher(
                manager = manager,
                onClose = { showSwitcher = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                frequentSites = frequentSites,
                onClose = { showSettings = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PageContent(
    tab: BrowserTab,
    incognitoIsolated: Boolean,
    settings: AppSettings,
    frequentSites: FrequentSites,
    onOpenSite: (String) -> Unit,
) {
    if (tab.isHome) {
        HomePage(
            incognito = tab.incognito,
            incognitoIsolated = incognitoIsolated,
            settings = settings,
            frequentSites = frequentSites,
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
    onShowTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tabCount = manager.tabs.count { it.incognito == tab.incognito }
    val barHeight = settings.barSize.dp.dp

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.showNavButtons) {
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

            AddressField(
                tab = tab,
                height = barHeight,
                onSubmit = { manager.navigate(tab, it) },
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onShowTabs, modifier = Modifier.size(barHeight)) {
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

            Box {
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
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf(TextFieldValue("")) }

    // Sin foco se ve solo el dominio; con foco, la URL completa seleccionada para reemplazarla.
    val shown = if (focused) typed else TextFieldValue(UrlUtils.displayHost(tab.url))

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceVariant,
        modifier = modifier.height(height),
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
                onValueChange = { if (focused) typed = it },
                singleLine = true,
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
                    .onFocusChanged { state ->
                        if (state.isFocused && !focused) {
                            typed = TextFieldValue(tab.url, TextRange(0, tab.url.length))
                        }
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
            } else if (!tab.isHome) {
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
