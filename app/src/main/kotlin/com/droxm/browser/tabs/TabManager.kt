package com.droxm.browser.tabs

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.engine.BrowserEngine
import com.droxm.browser.engine.EngineDelegate
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.util.UrlUtils

/**
 * Maneja la lista de pestañas, cuál está activa y cuántas viven a la vez.
 * Las pestañas inactivas se "duermen": se guarda su estado y se destruye el WebView.
 * Las pestañas normales se guardan en disco y se restauran al abrir la app.
 */
class TabManager(
    private val engine: BrowserEngine,
    private val settings: AppSettings,
    private val store: TabStore,
) {

    val tabs = mutableStateListOf<BrowserTab>()

    var activeId by mutableStateOf(-1L)
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId }

    val incognitoIsolated: Boolean
        get() = engine.incognitoIsolated

    private var nextId = 1L

    /** Última pestaña normal que estuvo activa (para guardar cuál reabrir aunque se cierre desde incógnito). */
    private var lastNormalId = -1L

    init {
        engine.cleanupLeftovers()
    }

    /** Recupera las pestañas guardadas. Devuelve false si no había ninguna. */
    fun restore(): Boolean {
        val snapshot = store.load() ?: return false
        if (snapshot.tabs.isEmpty()) return false

        snapshot.tabs.forEach { saved ->
            val tab = BrowserTab(nextId++, incognito = false, initialUrl = saved.url)
            tab.title = saved.title
            tabs.add(tab)
        }
        // Solo la activa se carga; las demás quedan dormidas hasta que se abran.
        select(tabs[snapshot.activeIndex.coerceIn(0, tabs.lastIndex)].id)
        return true
    }

    fun newTab(incognito: Boolean, url: String = "", select: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++, incognito, url)
        tabs.add(tab)
        if (select) select(tab.id)
        persist()
        return tab
    }

    fun select(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        val previous = activeTab
        if (previous != null && previous !== tab) previous.session?.onPause()

        activeId = id
        if (!tab.incognito) lastNormalId = id
        tab.lastActive = SystemClock.elapsedRealtime()
        ensureSession(tab)
        tab.session?.onResume()
        enforceLimit()
        persist()
    }

    /** Escribió algo en la barra: carga la URL o hace una búsqueda. */
    fun navigate(tab: BrowserTab, input: String) {
        val url = UrlUtils.resolve(input, settings.searchEngine.url) ?: return
        tab.url = url
        tab.title = ""
        val session = tab.session
        if (session != null) {
            session.loadUrl(url)
        } else {
            tab.savedState = null
            ensureSession(tab)
        }
        persist()
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
        persist()
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
        persist()
    }

    /** Duerme todas las pestañas menos la activa (se usa cuando el sistema pide memoria). */
    fun discardInactive() {
        tabs.filter { it.session != null && it.id != activeId }.forEach { discard(it) }
    }

    fun onHostPause() {
        activeTab?.session?.onPause()
        persist()
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

    private fun persist() {
        if (tabs.isEmpty()) return
        store.save(tabs, lastNormalId)
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
        val keep = (settings.maxLiveTabs - 1).coerceAtLeast(0)
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
            // Al terminar de cargar ya están la URL y el título finales: se guardan.
            if (!loading && !tab.incognito) persist()
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
