package com.droxm.browser.engine

import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Bloqueo de anuncios por dominio. Las peticiones a estos dominios se contestan al instante con una
 * respuesta vacía, así nunca llegan a la red y el WebView no muestra pantallas de error.
 *
 * Con el motor nativo (Rust) y las listas descargadas se filtra con EasyList/EasyPrivacy; mientras no estén
 * disponibles, se usa esta lista corta de dominios. No tapa los anuncios propios de un sitio (por ejemplo los de YouTube).
 */
object AdBlocker {

    @Volatile
    var enabled: Boolean = true

    /** Sitios donde el usuario apagó el bloqueador (dominio sin "www."). Se reemplaza entero, nunca se modifica. */
    @Volatile
    var allowedHosts: Set<String> = emptySet()

    /** true si el bloqueador está encendido y no se apagó para el sitio de [pageUrl]. */
    fun activeFor(pageUrl: String?): Boolean {
        if (!enabled) return false
        val allowed = allowedHosts
        if (allowed.isEmpty() || pageUrl == null) return true
        var h = hostOf(pageUrl) ?: return true
        // Apagado para example.com también cubre m.example.com.
        while (true) {
            if (h in allowed) return false
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return true
            h = h.substring(dot + 1)
        }
    }

    fun hostOf(url: String): String? {
        val start = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return null
        var end = url.length
        for (c in "/?#") url.indexOf(c, start).let { if (it in 0 until end) end = it }
        var h = url.substring(start, end).substringAfterLast('@').substringBefore(':').lowercase()
        if (h.startsWith("www.")) h = h.substring(4)
        return h.ifEmpty { null }
    }

    /** true si [url] (destino de una ventana emergente) es de un anunciante conocido. */
    fun blocksPopup(url: String, pageUrl: String?): Boolean {
        if (!activeFor(pageUrl)) return false
        if (NativeAdblock.ready) return (NativeAdblock.check(url, pageUrl, null, "iframe") and 1) != 0
        return shouldBlock(hostOf(url))
    }

    private val HOSTS = setOf(
        // Google
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "2mdn.net", "google-analytics.com", "googletagservices.com", "admob.com",
        // Redes de anuncios / subastas
        "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com",
        "outbrain.com", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "advertising.com", "adform.net", "smartadserver.com", "moatads.com", "scorecardresearch.com",
        "quantserve.com", "quantcount.com", "media.net", "adroll.com", "yieldmo.com", "teads.tv",
        "sharethrough.com", "33across.com", "bidswitch.net", "contextweb.com", "lijit.com",
        "sovrn.com", "indexww.com", "adtechus.com", "serving-sys.com", "flashtalking.com",
        "adition.com", "bluekai.com", "krxd.net", "demdex.net", "everesttech.net", "mathtag.com",
        "tapad.com", "rlcdn.com", "agkn.com", "bidr.io", "adsymptotic.com", "adscale.de",
        "revcontent.com", "mgid.com", "zedo.com", "undertone.com", "triplelift.com", "gumgum.com",
        "spotxchange.com", "spotx.tv", "springserve.com", "tremorhub.com", "adsafeprotected.com",
        "doubleverify.com", "imrworldwide.com", "chartbeat.net", "ml314.com", "adgrx.com",
        "adhigh.net", "richaudience.com", "improvedigital.com", "emxdgt.com", "adkernel.com",
        // Móvil
        "adcolony.com", "applovin.com", "mopub.com", "inmobi.com", "chartboost.com", "vungle.com",
        "ironsrc.com", "supersonicads.com", "startappservice.com", "unityads.unity3d.com",
        // Pop-ups / tráfico
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "adsterra.com", "hilltopads.net", "clickadu.com", "trafficstars.com",
        "adspyglass.com", "onclickads.net", "ad-maven.com", "admaven.com", "pushwoosh.com",
        // Redes sociales (anuncios y seguimiento)
        "ads.twitter.com", "analytics.twitter.com", "ads.linkedin.com", "ads.pinterest.com",
        "ads.yahoo.com", "ads.tiktok.com", "business-api.tiktok.com", "an.facebook.com",
    )

    // GIF transparente de 1x1 para reemplazar imágenes de anuncios sin dejar el ícono de imagen rota.
    private val PIXEL: ByteArray by lazy {
        Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
    }

    fun shouldBlock(host: String?): Boolean {
        if (!enabled || host == null) return false
        var h = host.lowercase()
        // Se prueba el dominio completo y después cada dominio padre (a.b.example.com → b.example.com → example.com).
        while (h.contains('.')) {
            if (h in HOSTS) return true
            h = h.substringAfter('.')
        }
        return false
    }

    /**
     * Decide qué hacer con un pedido de un subrecurso. Devuelve la respuesta vacía si hay que bloquearlo, o null.
     * Con el motor nativo cargado se evalúa todo en una sola llamada; si no, se usa la lista corta de dominios.
     */
    fun intercept(url: String, host: String?, path: String, source: String?, accept: String?, dest: String?): WebResourceResponse? {
        if (!activeFor(source)) return null
        if (NativeAdblock.ready) {
            val v = NativeAdblock.check(url, source, accept, dest)
            return if ((v and 1) != 0) emptyResponseFor(v shr 1) else null
        }
        return if (shouldBlock(host)) emptyResponse(path) else null
    }

    private fun empty(mime: String, status: Int = 200): WebResourceResponse =
        if (status == 200) {
            WebResourceResponse(mime, "utf-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            WebResourceResponse(mime, "utf-8", status, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

    /** Respuesta vacía adecuada al tipo de recurso (códigos de `ResourceType` en Rust). */
    private fun emptyResponseFor(type: Int): WebResourceResponse = when (type) {
        2 -> empty("text/html")
        3 -> empty("application/javascript")
        4 -> empty("text/css")
        5 -> WebResourceResponse("image/gif", null, ByteArrayInputStream(PIXEL))
        6, 9 -> empty("text/plain", 204)
        else -> empty("text/plain")
    }

    fun emptyResponse(path: String): WebResourceResponse {
        val p = path.lowercase()
        return when {
            p.endsWith(".js") -> empty("application/javascript")
            p.endsWith(".css") -> empty("text/css")
            p.endsWith(".gif") || p.endsWith(".png") || p.endsWith(".jpg") ||
                p.endsWith(".jpeg") || p.endsWith(".webp") ->
                WebResourceResponse("image/gif", null, ByteArrayInputStream(PIXEL))
            else -> empty("text/plain")
        }
    }

    /** CSS que esconde los huecos que dejan los anuncios bloqueados. Se inyecta al terminar de cargar la página. */
    const val HIDE_SCRIPT = "(function(){if(document.getElementById('__droxm_ab'))return;" +
        "var s=document.createElement('style');s.id='__droxm_ab';" +
        "s.textContent='ins.adsbygoogle,iframe[id^=\"google_ads_iframe\"],iframe[src*=\"doubleclick.net\"]," +
        "iframe[src*=\"googlesyndication.com\"],[id^=\"div-gpt-ad\"],[id^=\"google_ads\"]" +
        "{display:none!important}';" +
        "(document.head||document.documentElement).appendChild(s);})();"
}
