package com.droxm.browser.util

import android.net.Uri
import java.net.URLEncoder

object UrlUtils {

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** Convierte lo que se escribe en la barra en una URL cargable, o en una búsqueda. */
    fun resolve(input: String, searchUrl: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return text

        if (looksLikeAddress(text)) {
            val host = hostOf(text)
            val local = host.equals("localhost", true) || IPV4.matches(host)
            return (if (local) "http://" else "https://") + text
        }
        return searchUrl + URLEncoder.encode(text, "UTF-8")
    }

    /** Lo que se muestra en la barra cuando no se está editando: solo el dominio. */
    fun displayHost(url: String): String {
        if (url.isBlank()) return ""
        val host = try {
            Uri.parse(url).host
        } catch (e: Exception) {
            null
        }
        return (host ?: url).removePrefix("www.")
    }

    fun isSecure(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    private fun hostOf(text: String): String =
        text.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')

    private fun looksLikeAddress(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        val host = hostOf(text)
        if (host.isEmpty()) return false
        return host.equals("localhost", true) ||
            (host.contains('.') && !host.startsWith('.') && !host.endsWith('.'))
    }
}
