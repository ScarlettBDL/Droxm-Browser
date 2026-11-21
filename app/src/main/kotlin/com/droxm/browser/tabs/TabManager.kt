package com.droxm.browser.tabs

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.engine.BrowserEngine
import com.droxm.browser.engine.EngineDelegate
import com.droxm.browser.util.UrlUtils

/**
 * Maneja la lista de pestañas, cuál está activa y cuántas viven a la vez.
 * Las pestañas inactivas se "duermen": se guarda su estado y se destruye el WebView.
 */
class TabManager(private val engine: BrowserEngine) {

    val tabs = mutableStateListOf<BrowserTab>()

    var activeId by mutableStateOf(-1L)
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId }

    val incognitoIsolated: Boolean
        get() = engine.incognitoIsolated

    /** Máximo de pestañas con WebView vivo a la vez (la activa incluida). */
    var maxLiveTabs = 3

    private var nextId = 1L

    init {
        engine.cleanupLeftovers()
    }

    fun newTab(incognito: Boolean, url: String = "", select: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++, incognito, url)
        tabs.add(tab)
        if (select) select(tab.id)
        return tab
    }

    fun select(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val previous = activeTab
        if (previous != null && previous !== tab) previous.session?.onPause()

        activeId = id
        tab.lastActive = SystemClock.elapsedRealtime()
        ensureSession(tab)
        tab.session?.onResume()
        enforceLimit()
    }

    /** Escribió algo en la barra: carga la URL o hace una búsqueda. */
    fun navigate(tab: BrowserTab, input: String) {
        val url = UrlUtils.resolve(input) ?: return
        tab.url = url
        tab.title = ""
        val session = tab.session
        if (session != null) {
            session.loadUrl(url)
        } else {
            tab.savedState = null
            ensureSession(tab)
        }
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs[index]

        tab.session?.release()
        tab.session = null
        tab.savedState = null
        tabs.removeAt(index)

        if (tab.incognito && tabs.none { it.incognito }) engine.onIncognitoClosed()

        if (id == activeId) {
            val sameMode = tabs.filter { it.incognito == tab.incognito }
            // Vecina anterior del mismo modo; si no hay, la primera. Desde incógnito se vuelve a una normal;
            // desde una normal nunca se salta sola a incógnito.
            val target = sameMode.lastOrNull { tabs.indexOf(it) < index }
                ?: sameMode.firstOrNull()
                ?: (if (tab.incognito) tabs.lastOrNull() else null)
            if (target != null) select(target.id) else newTab(incognito = false)
        }
    }

    fun closeAll(incognito: Boolean) {
        val victims = tabs.filter { it.incognito == incognito }
        if (victims.isEmpty()) return
        val activeWasClosed = victims.any { it.id == activeId }

        victims.forEach {
            it.session?.release()
            it.session = null
            it.savedState = null
        }
        tabs.removeAll(victims)
        if (incognito) engine.onIncognitoClosed()

        if (activeWasClosed) {
            val target = if (incognito) tabs.lastOrNull() else null
            if (target != null) select(target.id) else newTab(incognito = false)
        }
    }

    /** Duerme todas las pestañas menos la activa (se usa cuando el sistema pide memoria). */
    fun discardInactive() {
        tabs.filter { it.session != null && it.id != activeId }.forEach { discard(it) }
    }

    fun onHostPause() {
        activeTab?.session?.onPause()
    }

    fun onHostResume() {
        activeTab?.session?.onResume()
    }

    fun destroy() {
        tabs.forEach {
            it.session?.release()
            it.session = null
        }
        tabs.clear()
        engine.onIncognitoClosed()
    }

    private fun ensureSession(tab: BrowserTab) {
        if (tab.session != null) return
        if (tab.url.isBlank() && tab.savedState == null) return // pestaña de inicio: no hace falta WebView
        tab.session = engine.createSession(
            incognito = tab.incognito,
            delegate = TabDelegate(tab),
            restoreFrom = tab.savedState,
            initialUrl = tab.url.ifBlank { null },
        )
        tab.savedState = null
    }

    private fun enforceLimit() {
        val others = tabs
            .filter { it.session != null && it.id != activeId }
            .sortedByDescending { it.lastActive }
        val keep = (maxLiveTabs - 1).coerceAtLeast(0)
        others.drop(keep).forEach { discard(it) }
    }

    private fun discard(tab: BrowserTab) {
        val session = tab.session ?: return
        tab.savedState = session.saveState()
        session.release()
        tab.session = null
        tab.isLoading = false
        tab.progress = 0
    }

    private inner class TabDelegate(private val tab: BrowserTab) : EngineDelegate {
        override fun onUrlChanged(url: String) {
            if (url != "about:blank") tab.url = url
        }

        override fun onTitleChanged(title: String) {
            tab.title = title
        }

        override fun onProgressChanged(progress: Int) {
            tab.progress = progress
        }

        override fun onLoadingChanged(loading: Boolean) {
            tab.isLoading = loading
        }

        override fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean) {
            tab.canGoBack = canGoBack
            tab.canGoForward = canGoForward
        }

        override fun onOpenInNewTab(url: String) {
            newTab(incognito = tab.incognito, url = url)
        }
    }
}
