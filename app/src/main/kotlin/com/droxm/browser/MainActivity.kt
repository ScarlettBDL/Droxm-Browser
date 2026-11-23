package com.droxm.browser

import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.droxm.browser.engine.FilterUpdater
import com.droxm.browser.engine.WebViewEngine
import com.droxm.browser.data.Bookmarks
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.data.HomeBackgrounds
import com.droxm.browser.data.History
import com.droxm.browser.data.SitePermissions
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.tabs.TabStore
import com.droxm.browser.ui.BrowserScreen
import com.droxm.browser.ui.DroxmTheme
import com.droxm.browser.util.Downloader
import com.droxm.browser.util.SystemBridge

class MainActivity : ComponentActivity() {

    private lateinit var manager: TabManager
    private lateinit var settings: AppSettings
    private lateinit var frequentSites: FrequentSites
    private lateinit var history: History
    private lateinit var bookmarks: Bookmarks
    private lateinit var backgrounds: HomeBackgrounds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = AppSettings(this)
        FilterUpdater.init(this)
        frequentSites = FrequentSites(this)
        history = History(this)
        bookmarks = Bookmarks(this)
        // Se elige el fondo de inicio de esta apertura del navegador.
        backgrounds = HomeBackgrounds(this)
        // Tiene que crearse acá (antes de que la actividad arranque) porque registra el pedido de permiso de descargas.
        val downloader = Downloader(this)
        val bridge = SystemBridge(this)
        manager = TabManager(WebViewEngine(this), settings, TabStore(this), frequentSites, history, downloader, SitePermissions(this), bridge)

        // Se recuperan las pestañas de la sesión anterior; si hay un link de otra app, va en una pestaña nueva.
        val restored = manager.restore()
        val link = viewUrl(intent)
        when {
            link != null -> manager.newTab(incognito = false, url = link)
            !restored -> manager.newTab(incognito = false)
        }

        setContent {
            DroxmTheme(
                mode = settings.themeMode,
                accent = settings.accent,
                amoled = settings.amoled,
                forceDark = manager.activeTab?.incognito == true,
            ) {
                BrowserScreen(manager, settings, frequentSites, history, bookmarks, backgrounds)
            }
        }
    }

    // Otra app abre un link con Droxm: va a una pestaña nueva.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewUrl(intent)?.let { manager.newTab(incognito = false, url = it) }
    }

    override fun onPause() {
        manager.onHostPause()
        history.flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        manager.onHostResume()
    }

    // Poca memoria o app en segundo plano: se duermen las pestañas que no se están viendo.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::manager.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            manager.discardInactive()
        }
    }

    override fun onDestroy() {
        manager.destroy()
        super.onDestroy()
    }

    private fun viewUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        return if (data.startsWith("http://", true) || data.startsWith("https://", true)) data else null
    }
}
