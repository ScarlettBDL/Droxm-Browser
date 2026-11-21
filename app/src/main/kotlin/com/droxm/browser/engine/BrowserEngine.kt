package com.droxm.browser.engine

import android.os.Bundle
import android.view.View

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
}

interface BrowserEngine {
    /** true si el incógnito tiene cookies/almacenamiento realmente separados del modo normal. */
    val incognitoIsolated: Boolean

    fun createSession(
        incognito: Boolean,
        delegate: EngineDelegate,
        restoreFrom: Bundle?,
        initialUrl: String?,
    ): EngineSession

    /** Se llama cuando ya no queda ninguna pestaña incógnito: hay que borrar sus datos. */
    fun onIncognitoClosed()

    /** Se llama al iniciar: borra restos de una sesión incógnito anterior (por ejemplo si se mató la app). */
    fun cleanupLeftovers()
}
