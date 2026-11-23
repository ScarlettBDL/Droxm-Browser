package com.droxm.browser.engine

import android.content.Intent
import android.os.Bundle
import android.view.View

/** Lo que hay debajo del dedo cuando se mantiene apretado en una página: un enlace, una imagen o las dos cosas. */
class HitInfo(val linkUrl: String?, val imageUrl: String?)

/** Todo lo necesario para descargar un archivo en nombre de la página (con sus cookies y su sesión). */
class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val cookie: String?,
    val referer: String?,
    val isImage: Boolean = false,
)

/**
 * Todo lo que la interfaz necesita saber del motor. Hoy lo implementa WebView (Chromium);
 * si algún día se migra a GeckoView, solo hay que escribir otra implementación de estas interfaces.
 */
interface EngineDelegate {
    fun onUrlChanged(url: String)
    fun onTitleChanged(title: String)
    fun onProgressChanged(progress: Int)
    fun onLoadingChanged(loading: Boolean)
    fun onNavigationChanged(canGoBack: Boolean, canGoForward: Boolean)
    fun onOpenInNewTab(url: String)

    /** El proceso de render del WebView murió (por ejemplo por falta de memoria): hay que volver a crear la vista. */
    fun onRendererGone()

    /** Se mantuvo apretado un enlace o una imagen. */
    fun onLongPress(info: HitInfo)

    /** La página inició la descarga de un archivo. */
    fun onDownload(request: DownloadRequest)

    /** La página pide la cámara y/o el micrófono. [respond] se llama una sola vez con la decisión. */
    fun onPermissionRequest(origin: String, camera: Boolean, microphone: Boolean, respond: (Boolean) -> Unit)

    /** La página pide la ubicación. */
    fun onGeolocationRequest(origin: String, respond: (Boolean) -> Unit)

    /** Un video (u otro contenido) pide pantalla completa. [onExit] le avisa a la página que se salió. */
    fun onShowFullscreen(view: View, onExit: () -> Unit)

    fun onHideFullscreen()

    /** La página quiere que se elija un archivo. Hay que abrir [intent] y devolver el resultado. */
    fun onFileChooser(intent: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit)
}

/** Una pestaña "viva" (con su vista de motor). */
interface EngineSession {
    val view: View
    fun loadUrl(url: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()
    fun saveState(): Bundle
    fun onPause()
    fun onResume()
    fun release()

    /** Pide la versión de escritorio (o vuelve a la móvil) y recarga la página. */
    fun setDesktopMode(enabled: Boolean)

    /** Resultado de "buscar en la página": (coincidencia actual, total). Lo llama el motor en el hilo de la interfaz. */
    var onFindResult: ((Int, Int) -> Unit)?

    fun findAll(query: String)
    fun findNext(forward: Boolean)
    fun clearFind()

    /** Arma una descarga para [url] con las cookies y el referer de esta pestaña. */
    fun downloadRequest(url: String, isImage: Boolean): DownloadRequest
}

interface BrowserEngine {
    /** true si el incógnito tiene cookies/almacenamiento realmente separados del modo normal. */
    val incognitoIsolated: Boolean

    fun createSession(
        incognito: Boolean,
        desktop: Boolean,
        delegate: EngineDelegate,
        restoreFrom: Bundle?,
        initialUrl: String?,
    ): EngineSession

    /** Se llama cuando ya no queda ninguna pestaña incógnito: hay que borrar sus datos. */
    fun onIncognitoClosed()

    /** Se llama al iniciar: borra restos de una sesión incógnito anterior (por ejemplo si se mató la app). */
    fun cleanupLeftovers()
}
