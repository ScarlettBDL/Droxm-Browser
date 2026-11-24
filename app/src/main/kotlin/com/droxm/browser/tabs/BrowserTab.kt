package com.droxm.browser.tabs

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.droxm.browser.engine.EngineSession

/**
 * Una pestaña. Los campos con estado de Compose actualizan la interfaz solos.
 * [session] es null cuando la pestaña está "dormida" (sin WebView, casi sin consumo de RAM)
 * o cuando todavía es una pestaña de inicio vacía.
 */
class BrowserTab(
    val id: Long,
    val incognito: Boolean,
    initialUrl: String = "",
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)

    /** Pide la versión de escritorio de los sitios. */
    var desktop by mutableStateOf(false)

    /** El modo escritorio de esta pestaña lo impuso un ajuste del sitio (se deshace al salir de él). Solo en memoria. */
    var desktopBySite: Boolean = false

    /** Grupo al que pertenece (0 = ninguno). */
    var groupId by mutableLongStateOf(NO_GROUP)

    var session by mutableStateOf<EngineSession?>(null)

    /** Miniatura de la última vez que se vio la pestaña (solo en memoria). */
    var thumbnail by mutableStateOf<ImageBitmap?>(null)

    /** Estado guardado (historial de navegación, scroll) mientras la pestaña está dormida. Solo en memoria. */
    var savedState: Bundle? = null

    var lastActive: Long = SystemClock.elapsedRealtime()

    /** Pestaña vacía: se muestra la página de inicio en vez de un WebView. */
    val isHome: Boolean
        get() = url.isBlank()

    companion object {
        const val NO_GROUP = 0L
    }
}
