package com.droxm.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

private const val INCOGNITO_PROFILE = "droxm_incognito"

class WebViewEngine(private val context: Context) : BrowserEngine {

    private val profilesSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    override val incognitoIsolated: Boolean
        get() = profilesSupported

    override fun createSession(
        incognito: Boolean,
        delegate: EngineDelegate,
        restoreFrom: Bundle?,
        initialUrl: String?,
    ): EngineSession {
        val webView = WebView(context)
        val isolated = incognito && profilesSupported
        if (isolated) {
            // Un perfil aparte = cookies, caché y almacenamiento separados del modo normal.
            ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
            WebViewCompat.setProfile(webView, INCOGNITO_PROFILE)
        }
        // Sin soporte de perfiles: incógnito "liviano" (sin caché ni DOM storage), pero comparte cookies.
        val lite = incognito && !profilesSupported

        val session = WebViewSession(webView, delegate, lite = lite, isolated = isolated)

        val restored = restoreFrom != null && webView.restoreState(restoreFrom) != null
        if (!restored && initialUrl != null) webView.loadUrl(initialUrl)
        return session
    }

    override fun onIncognitoClosed() = deleteIncognitoProfile()

    override fun cleanupLeftovers() = deleteIncognitoProfile()

    private fun deleteIncognitoProfile() {
        if (!profilesSupported) return
        try {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE)
        } catch (e: Exception) {
            // Si todavía está en uso no se puede borrar; se reintenta al próximo inicio.
        }
    }
}

private class WebViewSession(
    private val webView: WebView,
    private val delegate: EngineDelegate,
    private val lite: Boolean,
    private val isolated: Boolean,
) : EngineSession {

    override val view: View
        get() = webView

    private val client = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            delegate.onLoadingChanged(true)
            if (url != null) delegate.onUrlChanged(url)
            publishNavigation(view)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            delegate.onLoadingChanged(false)
            if (url != null) delegate.onUrlChanged(url)
            publishNavigation(view)
            if (AdBlocker.enabled) view?.evaluateJavascript(AdBlocker.HIDE_SCRIPT, null)
        }

        // Anuncios de dominios conocidos: respuesta vacía al instante, sin pasar por la red (y sin pantalla de error).
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (request == null || request.isForMainFrame) return null
            val uri = request.url
            return if (AdBlocker.shouldBlock(uri.host)) AdBlocker.emptyResponse(uri.path ?: "") else null
        }

        // Si algo que NO es la página principal falla (un anuncio bloqueado por DNS, por ejemplo), el WebView dibuja
        // su pantalla de error dentro del iframe. Se esconde ese iframe para que no quede un cartel de error en medio.
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (view == null || request == null || request.isForMainFrame) return
            val quoted = JSONObject.quote(request.url.toString())
            view.evaluateJavascript(
                "(function(u){var f=document.querySelectorAll('iframe');" +
                    "for(var i=0;i<f.length;i++){if(f[i].src===u){f[i].style.display='none';}}})($quoted)",
                null,
            )
        }

        // Si el proceso de render muere (poca memoria), sin esto la app entera se cierra.
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            delegate.onRendererGone()
            return true
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (url != null) delegate.onUrlChanged(url)
            publishNavigation(view)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return shouldHandleExternally(webView.context, uri)
        }
    }

    private val chrome = object : WebChromeClient() {
        private var lastProgress = -1

        // El WebView avisa el progreso decenas de veces por página; cada aviso redibuja la interfaz.
        // Alcanza con avisar cada 5 puntos.
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (newProgress == 100 || newProgress < lastProgress || newProgress - lastProgress >= 5) {
                lastProgress = newProgress
                delegate.onProgressChanged(newProgress)
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) delegate.onTitleChanged(title)
        }

        // Links con target="_blank" y window.open(): se abren en una pestaña nueva.
        // Solo si hubo un gesto del usuario (así se frenan los popups automáticos).
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean {
            if (view == null || resultMsg == null || !isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

            val popup = WebView(view.context)
            if (isolated) WebViewCompat.setProfile(popup, INCOGNITO_PROFILE)
            popup.settings.javaScriptEnabled = true

            var done = false
            fun finish(url: String) {
                if (done) return
                done = true
                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    delegate.onOpenInNewTab(url)
                } else {
                    shouldHandleExternally(view.context, Uri.parse(url))
                }
                popup.post {
                    popup.stopLoading()
                    popup.destroy()
                }
            }

            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url == "about:blank") return false
                    finish(url)
                    return true
                }

                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                    if (url != null && url != "about:blank") finish(url)
                }
            }
            // Si el popup nunca navega a ningún lado, se limpia solo.
            popup.postDelayed({
                if (!done) {
                    done = true
                    popup.destroy()
                }
            }, 10_000)

            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    init {
        configure()
        webView.webViewClient = client
        webView.webChromeClient = chrome
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = !lite
        s.cacheMode = if (lite) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = false
        // Sin el "; wv" muchos sitios sirven la versión móvil normal en vez de rechazar el WebView.
        s.userAgentString = s.userAgentString.replace("; wv", "")
    }

    private fun publishNavigation(view: WebView?) {
        if (view != null) delegate.onNavigationChanged(view.canGoBack(), view.canGoForward())
    }

    override fun loadUrl(url: String) = webView.loadUrl(url)

    override fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    override fun reload() = webView.reload()

    override fun stopLoading() = webView.stopLoading()

    override fun saveState(): Bundle = Bundle().also { webView.saveState(it) }

    override fun onPause() = webView.onPause()

    override fun onResume() = webView.onResume()

    override fun release() {
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

/** mailto:, tel:, intent:, etc. se mandan a la app que corresponda. Devuelve true si NO hay que cargarlo en el WebView. */
private fun shouldHandleExternally(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme in setOf("http", "https", "about", "data", "blob", "javascript")) return false
    try {
        val intent = if (scheme == "intent") {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        // No hay app que lo maneje: se ignora.
    }
    return true
}
