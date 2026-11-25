package com.droxm.browser.tabs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.data.History
import com.droxm.browser.data.SitePerm
import com.droxm.browser.data.SitePermissions
import com.droxm.browser.data.SiteSettings
import com.droxm.browser.engine.AdBlocker
import com.droxm.browser.engine.BrowserEngine
import com.droxm.browser.engine.DownloadRequest
import com.droxm.browser.engine.EngineDelegate
import com.droxm.browser.engine.HitInfo
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.util.Downloader
import com.droxm.browser.util.SystemBridge
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
    private val history: History,
    private val downloader: Downloader,
    val sitePermissions: SitePermissions,
    val siteSettings: SiteSettings,
    private val bridge: SystemBridge,
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

    /** Un sitio pide un permiso: la interfaz muestra un diálogo mientras no sea null. */
    class PermissionPrompt(
        val site: String,
        val perms: List<SitePerm>,
        val incognito: Boolean,
        val resolve: (allow: Boolean, remember: Boolean) -> Unit,
    )

    var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
        private set

    /** Modo cine activo: la interfaz esconde las barras y gira la pantalla. */
    var cinema by mutableStateOf(false)

    /** Se bloqueó una ventana emergente: la interfaz muestra un aviso mientras no sea null. [site] es null en incógnito (no se puede recordar). */
    class PopupNotice(val site: String?, val url: String?)

    var popupNotice by mutableStateOf<PopupNotice?>(null)
        private set

    /** Resumen del estado para el informe de diagnóstico. */
    fun debugState(): String {
        val a = activeTab
        return "pestañas=${tabs.size} vivas=${tabs.count { it.session != null }} pantallaCompleta=${fullscreen != null} " +
            "popups(vivos/creados)=${com.droxm.browser.engine.EngineStats.popupsAlive}/${com.droxm.browser.engine.EngineStats.popupsCreated} " +
            "sitio=${SiteSettings.siteOf(a?.url) ?: "-"} cargando=${a?.isLoading} ${com.droxm.browser.engine.DebugFlags.summary()}" +
            (fullscreen?.view?.let { v ->
                " vistaFS=${v.javaClass.simpleName} ${v.width}x${v.height} aceleradaHW=${v.isHardwareAccelerated} " +
                    "adjunta=${v.isAttachedToWindow} capa=${v.layerType} visible=${v.visibility}"
            } ?: "") +
            (a?.session?.view?.let { v -> " webview(capa=${v.layerType} vis=${v.visibility} hw=${v.isHardwareAccelerated})" } ?: "")
    }

    fun dismissPopupNotice() {
        popupNotice = null
    }

    /** "Permitir en este sitio" desde el aviso: se recuerda y, si había un destino, se abre. */
    fun allowPopupsFromNotice() {
        val notice = popupNotice ?: return
        popupNotice = null
        val site = notice.site ?: return
        siteSettings.setPopupsAllowed(site, true)
        val url = notice.url ?: return
        val tab = activeTab ?: return
        newTab(incognito = tab.incognito, url = url, groupId = tab.groupId)
    }

    /** Contenido en pantalla completa (por ejemplo un video). */
    class Fullscreen(val tabId: Long, val view: View, val exit: () -> Unit)

    var fullscreen by mutableStateOf<Fullscreen?>(null)
        private set

    /** La barra está escondida porque se está deslizando hacia abajo (solo si el ajuste está activado). */
    var barHidden by mutableStateOf(false)
        private set

    private var scrollAccum = 0
    private var lastBarToggle = 0L

    /** PDF que se está viendo en el visor propio. */
    var pdf by mutableStateOf<DownloadRequest?>(null)
        private set

    val activeTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeId }

    val incognitoIsolated: Boolean
        get() = engine.incognitoIsolated

    private companion object {
        const val THUMB_WIDTH = 240
    }

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
        cinema = false
        if (fullscreen?.tabId != id) exitFullscreen()
        val previous = activeTab
        if (previous != null && previous !== tab) {
            captureThumbnail(previous)
            previous.session?.onPause()
        }

        activeId = id
        barHidden = false
        scrollAccum = 0
        if (!tab.incognito) lastNormalId = id
        tab.lastActive = SystemClock.elapsedRealtime()
        ensureSession(tab)
        tab.session?.onResume()
        enforceLimit()
        persist()
    }

    /** Escribió algo en la barra: carga la URL o hace una búsqueda. */
    fun navigate(tab: BrowserTab, input: String) {
        val url = UrlUtils.resolve(input, settings.searchUrl) ?: return
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
        if (fullscreen?.tabId == id) exitFullscreen()

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

    /** Pasa a la pestaña de al lado (del mismo modo, normal o incógnito). */
    fun switchTab(delta: Int) {
        val current = activeTab ?: return
        val sameMode = tabs.filter { it.incognito == current.incognito }
        val target = sameMode.getOrNull(sameMode.indexOf(current) + delta) ?: return
        select(target.id)
    }

    /** Vuelve la pestaña a la pantalla de inicio (se pierde su historial de navegación). */
    fun goHome(tab: BrowserTab) {
        tab.session?.release()
        tab.session = null
        tab.savedState = null
        tab.url = ""
        tab.title = ""
        tab.canGoBack = false
        tab.canGoForward = false
        tab.isLoading = false
        tab.progress = 0
        barHidden = false
        persist()
    }

    /** Aplica el tamaño de texto a todas las pestañas vivas. */
    fun applyTextZoom() {
        tabs.forEach { it.session?.setTextZoom(zoomFor(it.url)) }
    }

    fun exitFullscreen() {
        val fs = fullscreen ?: return
        fullscreen = null
        fs.exit()
    }

    fun closePdf() {
        pdf = null
    }

    fun downloadFile(request: DownloadRequest) = downloader.start(request)

    fun answerPermission(allow: Boolean, remember: Boolean) {
        val prompt = permissionPrompt ?: return
        permissionPrompt = null
        prompt.resolve(allow, remember)
    }

    /** Decide si una página puede usar cámara, micrófono o ubicación: usa lo guardado o pregunta. */
    private fun askSitePermissions(tab: BrowserTab, origin: String, perms: List<SitePerm>, respond: (Boolean) -> Unit) {
        val site = try {
            Uri.parse(origin).host ?: origin
        } catch (e: Exception) {
            origin
        }

        fun proceed() {
            // Falta además el permiso de Android: se pide acá y se responde a la página con el resultado.
            bridge.requestPermissions(perms.flatMap { it.androidPermissions }.distinct()) { result ->
                respond(perms.all { perm -> perm.androidPermissions.any { result[it] == true } })
            }
        }

        val saved = perms.map { sitePermissions.get(site, it) }
        if (saved.any { it == false }) {
            respond(false)
            return
        }
        if (saved.all { it == true }) {
            proceed()
            return
        }
        // Solo se pregunta si la pestaña se está viendo.
        if (tab.id != activeId) {
            respond(false)
            return
        }
        permissionPrompt?.resolve?.invoke(false, false)
        permissionPrompt = PermissionPrompt(site, perms, tab.incognito) { allow, remember ->
            if (remember && !tab.incognito) perms.forEach { sitePermissions.set(site, it, allow) }
            if (allow) proceed() else respond(false)
        }
    }

    private fun isPdf(request: DownloadRequest): Boolean {
        val scheme = Uri.parse(request.url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val path = request.url.substringBefore('?').substringBefore('#')
        return request.mimeType?.contains("pdf", ignoreCase = true) == true || path.endsWith(".pdf", ignoreCase = true)
    }

    /** Tamaño de texto de una página: el propio del sitio si lo tiene, si no el general. */
    private fun zoomFor(url: String?): Int = siteSettings.textZoom(SiteSettings.siteOf(url)) ?: settings.textZoom

    /** Aplica a la pestaña los ajustes del [site] (tamaño de texto y versión de escritorio). */
    private fun applySiteSettings(tab: BrowserTab, site: String?) {
        tab.session?.setTextZoom(siteSettings.textZoom(site) ?: settings.textZoom)
        val wanted = siteSettings.desktop(site)
        if (wanted != null) {
            tab.desktopBySite = true
            if (wanted != tab.desktop) {
                tab.desktop = wanted
                tab.session?.setDesktopMode(wanted)
            }
        } else if (tab.desktopBySite) {
            // Se salió de un sitio que forzaba el modo: se vuelve al normal.
            tab.desktopBySite = false
            if (tab.desktop) {
                tab.desktop = false
                tab.session?.setDesktopMode(false)
            }
        }
    }

    // ---- ajustes por sitio (los llama el diálogo de "Ajustes del sitio")

    fun setSiteAdBlockOff(tab: BrowserTab, off: Boolean) {
        val site = SiteSettings.siteOf(tab.url) ?: return
        siteSettings.setAdBlockOff(site, off)
        tab.session?.reload()
    }

    fun setSitePopups(tab: BrowserTab, allowed: Boolean) {
        siteSettings.setPopupsAllowed(SiteSettings.siteOf(tab.url) ?: return, allowed)
    }

    fun setSiteDesktop(tab: BrowserTab, value: Boolean?) {
        val site = SiteSettings.siteOf(tab.url) ?: return
        siteSettings.setDesktop(site, value)
        applySiteSettings(tab, site)
    }

    fun setSiteZoom(tab: BrowserTab, percent: Int?) {
        val site = SiteSettings.siteOf(tab.url) ?: return
        siteSettings.setTextZoom(site, percent)
        applySiteSettings(tab, site)
    }

    fun resetSite(tab: BrowserTab) {
        val site = SiteSettings.siteOf(tab.url) ?: return
        val hadAdBlockOff = siteSettings.adBlockOff(site)
        siteSettings.reset(site)
        applySiteSettings(tab, site)
        if (hadAdBlockOff) tab.session?.reload()
    }

    fun toggleDesktop(tab: BrowserTab) {
        tab.desktopBySite = false
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
        activeTab?.let { captureThumbnail(it) }
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
        val wantedDesktop = siteSettings.desktop(SiteSettings.siteOf(tab.url))
        if (wantedDesktop != null) {
            tab.desktop = wantedDesktop
            tab.desktopBySite = true
        }
        tab.session = engine.createSession(
            incognito = tab.incognito,
            desktop = tab.desktop,
            delegate = TabDelegate(tab),
            restoreFrom = tab.savedState,
            initialUrl = tab.url.ifBlank { null },
        )
        tab.savedState = null
        tab.session?.setTextZoom(zoomFor(tab.url))
    }

    private fun enforceLimit() {
        val others = tabs
            .filter { it.session != null && it.id != activeId }
            .sortedByDescending { it.lastActive }
        val keep = (settings.maxLiveTabs - 1).coerceAtLeast(0)
        others.drop(keep).forEach { discard(it) }
    }

    /** Saca una foto chica de la página para mostrarla en el selector de pestañas. */
    fun captureThumbnail(tab: BrowserTab) {
        val view = tab.session?.view ?: return
        if (view.width <= 0 || view.height <= 0) return
        try {
            val scale = THUMB_WIDTH / view.width.toFloat()
            val height = (view.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(THUMB_WIDTH, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.scale(scale, scale)
            view.draw(canvas)
            tab.thumbnail = bitmap.asImageBitmap()
        } catch (e: Throwable) {
            // Sin miniatura: el selector muestra la inicial del sitio.
        }
    }

    private fun discard(tab: BrowserTab) {
        if (fullscreen?.tabId == tab.id) exitFullscreen()
        captureThumbnail(tab)
        val session = tab.session ?: return
        tab.savedState = session.saveState()
        session.release()
        tab.session = null
        tab.isLoading = false
        tab.progress = 0
    }

    private inner class TabDelegate(private val tab: BrowserTab) : EngineDelegate {
        private var lastSite: String? = SiteSettings.siteOf(tab.url)
        private var lastPopupAt = 0L
        private var lastPopupViaLink = false

        override fun onUrlChanged(url: String) {
            if (url == "about:blank") return
            tab.url = url
            val site = SiteSettings.siteOf(url)
            if (site != lastSite) {
                lastSite = site
                applySiteSettings(tab, site)
            }
        }

        private fun popupsFree(): Boolean =
            !settings.blockPopups || siteSettings.popupsAllowed(SiteSettings.siteOf(tab.url))

        private fun notifyBlocked(url: String?) {
            if (tab.id != activeId) return
            popupNotice = PopupNotice(if (tab.incognito) null else SiteSettings.siteOf(tab.url), url)
        }

        override fun allowPopup(viaLink: Boolean): Boolean {
            lastPopupViaLink = viaLink
            if (fullscreen != null) return false // tocar la pantalla de un video no debe abrir nada
            if (viaLink || popupsFree()) return true
            // Una ventana que no viene de un enlace y aparece pegada a otra es casi siempre publicidad.
            val now = SystemClock.elapsedRealtime()
            val tooFast = now - lastPopupAt < 4000
            lastPopupAt = now
            if (tooFast) notifyBlocked(null)
            return !tooFast
        }

        override fun allowPopupTarget(url: String): Boolean {
            if (lastPopupViaLink || popupsFree()) return true
            if (!AdBlocker.blocksPopup(url, tab.url)) return true
            notifyBlocked(url)
            return false
        }

        override fun onTitleChanged(title: String) {
            tab.title = title
            if (!tab.incognito) history.updateTitle(tab.url, title)
        }

        override fun onProgressChanged(progress: Int) {
            tab.progress = progress
        }

        override fun onLoadingChanged(loading: Boolean) {
            tab.isLoading = loading
            // Al terminar de cargar ya están la URL y el título finales: se guardan.
            if (!loading && !tab.incognito) {
                frequentSites.record(tab.url)
                history.record(tab.url, tab.title)
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
            // Los PDF se abren en el visor propio (con botón para guardarlos); el resto se descarga.
            if (isPdf(request)) pdf = request else downloader.start(request)
        }

        override fun onPermissionRequest(origin: String, camera: Boolean, microphone: Boolean, respond: (Boolean) -> Unit) {
            val perms = buildList {
                if (camera) add(SitePerm.CAMERA)
                if (microphone) add(SitePerm.MICROPHONE)
            }
            if (perms.isEmpty()) respond(false) else askSitePermissions(tab, origin, perms, respond)
        }

        override fun onGeolocationRequest(origin: String, respond: (Boolean) -> Unit) {
            askSitePermissions(tab, origin, listOf(SitePerm.LOCATION), respond)
        }

        override fun onScrollChanged(y: Int, dy: Int) {
            if (tab.id != activeId || !settings.hideBarOnScroll || fullscreen != null) return
            val now = SystemClock.elapsedRealtime()
            // Al esconder o mostrar la barra cambia el alto de la página y el WebView dispara scrolls que no
            // hizo el dedo: se ignoran un rato para que no se realimente (la barra "temblaba").
            if (now - lastBarToggle < 600) {
                scrollAccum = 0
                return
            }
            if (y <= 0) {
                if (barHidden) {
                    barHidden = false
                    lastBarToggle = now
                }
                scrollAccum = 0
                return
            }
            // Se acumula el movimiento en una dirección; un cambio chico de dirección no lo reinicia,
            // uno grande sí. Mostrar la barra pide un gesto claro hacia arriba.
            if (dy != 0 && (dy > 0) != (scrollAccum > 0) && kotlin.math.abs(dy) > 12) scrollAccum = 0
            scrollAccum += dy
            if (scrollAccum > 120 && !barHidden) {
                barHidden = true
                lastBarToggle = now
                scrollAccum = 0
            } else if (scrollAccum < -160 && barHidden) {
                barHidden = false
                lastBarToggle = now
                scrollAccum = 0
            } else if (scrollAccum > 120 || scrollAccum < -160) {
                scrollAccum = 0
            }
        }

        override fun onShowFullscreen(view: View, onExit: () -> Unit) {
            if (tab.id != activeId) {
                onExit()
                return
            }
            fullscreen = Fullscreen(tab.id, view, onExit)
            // Con el video en pantalla completa se liberan las otras pestañas y la página de atrás sale del layout
            // (GONE): así, al girar el celular, no se vuelve a calcular ni dibujar la página al nuevo tamaño.
            if (!com.droxm.browser.engine.DebugFlags.keepVisible) {
                discardInactive()
                tab.session?.view?.visibility = View.GONE
            }
        }

        override fun onCinemaToggle() {
            if (tab.id == activeId) cinema = !cinema
        }

        override fun onHideFullscreen() {
            if (fullscreen?.tabId == tab.id) fullscreen = null
            tab.session?.view?.visibility = View.VISIBLE
        }

        override fun onFileChooser(intent: android.content.Intent, onResult: (Int, android.content.Intent?) -> Unit) {
            bridge.chooseFile(intent, onResult)
        }
    }
}
