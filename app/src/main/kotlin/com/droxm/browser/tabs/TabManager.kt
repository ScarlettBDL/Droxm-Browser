package com.droxm.browser.tabs

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.engine.BrowserEngine
import com.droxm.browser.engine.DownloadRequest
import com.droxm.browser.engine.EngineDelegate
import com.droxm.browser.engine.HitInfo
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.util.Downloader
import com.droxm.browser.util.UrlUtils

/**
 * Maneja la lista de pestañas y sus grupos, cuál está activa y cuántas viven a la vez.
 * Las pestañas inactivas se "duermen": se guarda su estado y se destruye el WebView.
 * Las pestañas normales (y sus grupos) se guardan en disco y se restauran al abrir la app.
 */
class TabManager(
    private val engine: BrowserEngine,
    private val settings: AppSettings,
    private val store: TabStore,
    private val frequentSites: FrequentSites,
    private val downloader: Downloader,
) {

    /** Lo que se mantuvo apretado en una pestaña (para mostrar el menú contextual). */
    class Hit(val tabId: Long, val info: HitInfo)

    val tabs = mutableStateListOf<BrowserTab>()

    val groups = mutableStateListOf<TabGroup>()

    var activeId by mutableStateOf(-1L)
        private set

    /** Enlace o imagen sobre el que se mantuvo apretado; la interfaz muestra un menú mientras no sea null. */
    var hit by mutableStateOf<Hit?>(null)
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId }

    val incognitoIsolated: Boolean
        get() = engine.incognitoIsolated

    private var nextId = 1L
    private var nextGroupId = 1L

    /** Última pestaña normal que estuvo activa (para guardar cuál reabrir aunque se cierre desde incógnito). */
    private var lastNormalId = -1L

    init {
        engine.cleanupLeftovers()
    }

    // ---------------------------------------------------------------- pestañas

    /** Recupera las pestañas y grupos guardados. Devuelve false si no había ninguna. */
    fun restore(): Boolean {
        val snapshot = store.load() ?: return false
        if (snapshot.tabs.isEmpty()) return false

        snapshot.groups.forEach { saved ->
            groups.add(TabGroup(saved.id, false, saved.name, saved.colorIndex, saved.collapsed))
            nextGroupId = maxOf(nextGroupId, saved.id + 1)
        }
        snapshot.tabs.forEach { saved ->
            val tab = BrowserTab(nextId++, incognito = false, initialUrl = saved.url)
            tab.title = saved.title
            if (groups.any { it.id == saved.groupId }) tab.groupId = saved.groupId
            tabs.add(tab)
        }
        pruneGroups()
        // Solo la activa se carga; las demás quedan dormidas hasta que se abran.
        select(tabs[snapshot.activeIndex.coerceIn(0, tabs.lastIndex)].id)
        return true
    }

    fun newTab(
        incognito: Boolean,
        url: String = "",
        select: Boolean = true,
        groupId: Long = BrowserTab.NO_GROUP,
    ): BrowserTab {
        val tab = BrowserTab(nextId++, incognito, url)
        if (groups.any { it.id == groupId && it.incognito == incognito }) tab.groupId = groupId
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
        pruneGroups()

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
        pruneGroups()
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

    fun toggleDesktop(tab: BrowserTab) {
        tab.desktop = !tab.desktop
        tab.session?.setDesktopMode(tab.desktop)
    }

    // ---------------------------------------------------------------- grupos

    /** Crea un grupo nuevo con esta pestaña adentro. */
    fun createGroup(tab: BrowserTab): TabGroup {
        val sameMode = groups.count { it.incognito == tab.incognito }
        val group = TabGroup(
            id = nextGroupId++,
            incognito = tab.incognito,
            name = "Grupo ${sameMode + 1}",
            colorIndex = groups.size % TabGroup.COLOR_COUNT,
        )
        groups.add(group)
        moveToGroup(tab, group.id)
        return group
    }

    /** Mueve la pestaña a un grupo (o la saca de su grupo con [BrowserTab.NO_GROUP]). */
    fun moveToGroup(tab: BrowserTab, groupId: Long) {
        if (groupId != BrowserTab.NO_GROUP) {
            val group = groups.firstOrNull { it.id == groupId } ?: return
            if (group.incognito != tab.incognito) return
        }
        tab.groupId = groupId
        pruneGroups()
        persist()
    }

    /** Deshace el grupo pero deja sus pestañas abiertas. */
    fun ungroup(groupId: Long) {
        tabs.filter { it.groupId == groupId }.forEach { it.groupId = BrowserTab.NO_GROUP }
        pruneGroups()
        persist()
    }

    /** Cierra el grupo y todas sus pestañas. */
    fun closeGroup(groupId: Long) {
        tabs.filter { it.groupId == groupId }.map { it.id }.forEach { close(it) }
    }

    fun toggleCollapsed(group: TabGroup) {
        group.collapsed = !group.collapsed
        persist()
    }

    fun renameGroup(group: TabGroup, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) group.name = clean
        persist()
    }

    fun nextColor(group: TabGroup) {
        group.colorIndex = (group.colorIndex + 1) % TabGroup.COLOR_COUNT
        persist()
    }

    /** Un grupo sin pestañas no tiene sentido: se borra solo. */
    private fun pruneGroups() {
        val empty = groups.filter { group -> tabs.none { it.groupId == group.id } }
        if (empty.isNotEmpty()) groups.removeAll(empty)
    }

    // ---------------------------------------------------------------- menú contextual y descargas

    fun dismissHit() {
        hit = null
    }

    /** Abre un enlace en otra pestaña (en el mismo grupo que la de origen). */
    fun openInNewTab(from: BrowserTab, url: String, select: Boolean = true) {
        newTab(incognito = from.incognito, url = url, select = select, groupId = from.groupId)
    }

    fun openInIncognito(url: String) {
        newTab(incognito = true, url = url)
    }

    fun downloadImage(from: BrowserTab, url: String) {
        val session = from.session ?: return
        downloader.start(session.downloadRequest(url, isImage = true))
    }

    // ---------------------------------------------------------------- ciclo de vida

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
        groups.clear()
        engine.onIncognitoClosed()
    }

    // ---------------------------------------------------------------- internos

    private fun persist() {
        if (tabs.isEmpty()) return
        store.save(tabs, groups, lastNormalId)
    }

    private fun ensureSession(tab: BrowserTab) {
        if (tab.session != null) return
        if (tab.url.isBlank() && tab.savedState == null) return // pestaña de inicio: no hace falta WebView
        tab.session = engine.createSession(
            incognito = tab.incognito,
            desktop = tab.desktop,
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
            if (!loading && !tab.incognito) {
                frequentSites.record(tab.url)
                persist()
            }
        }

        override fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean) {
            tab.canGoBack = canGoBack
            tab.canGoForward = canGoForward
        }

        override fun onOpenInNewTab(url: String) {
            newTab(incognito = tab.incognito, url = url, groupId = tab.groupId)
        }

        override fun onRendererGone() {
            // Se hace en el próximo ciclo: no se puede destruir el WebView desde dentro de su propio callback.
            Handler(Looper.getMainLooper()).post {
                if (tab.session == null) return@post
                tab.session?.release()
                tab.session = null
                tab.isLoading = false
                tab.progress = 0
                // Si es la pestaña que se está viendo, se vuelve a crear y recarga la página.
                if (tab.id == activeId && tabs.contains(tab)) ensureSession(tab)
            }
        }

        override fun onLongPress(info: HitInfo) {
            if (tab.id == activeId) hit = Hit(tab.id, info)
        }

        override fun onDownload(request: DownloadRequest) {
            downloader.start(request)
        }
    }
}
