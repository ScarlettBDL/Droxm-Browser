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
import com.droxm.browser.data.SiteSettings
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
        com.droxm.browser.util.Diagnostics.install(this)
        com.droxm.browser.engine.DebugFlags.init(this)
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
        manager = TabManager(WebViewEngine(this), settings, TabStore(this), frequentSites, history, downloader, SitePermissions(this), SiteSettings(this), bridge)

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

    private val sampler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sampleTask = object : Runnable {
        override fun run() {
            val graphicsMb = com.droxm.browser.util.Diagnostics.sample(this@MainActivity, manager.debugState())
            // Freno de emergencia: si la memoria gráfica se dispara en pantalla completa (fuga del WebView en
            // algunos teléfonos), se sale antes de que Android cierre toda la app y se pierdan las pestañas.
            if (manager.fullscreen != null && graphicsMb > 700) {
                manager.exitFullscreen()
                // La memoria gráfica queda ocupada aunque se salga: se recrea el WebView para devolverla.
                sampler.postDelayed({ manager.recycleActive() }, 500)
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Se salió de pantalla completa y se recargó la página: usaba demasiada memoria",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
            sampler.postDelayed(this, if (manager.fullscreen != null) 1500 else 4000)
        }
    }

    override fun onPause() {
        sampler.removeCallbacks(sampleTask)
        manager.onHostPause()
        history.flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        manager.onHostResume()
        sampler.postDelayed(sampleTask, 1000)
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
