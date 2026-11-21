package com.droxm.browser.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.droxm.browser.settings.ThemeMode

private val LightColors = lightColorScheme(primary = Color(0xFF6C4DFF))
private val DarkColors = darkColorScheme(primary = Color(0xFFCBB8FF))

// Esquema fijo para el modo incógnito: oscuro y distinto al resto, para que se note a simple vista.
private val IncognitoColors = darkColorScheme(
    primary = Color(0xFFCBB8FF),
    background = Color(0xFF121016),
    surface = Color(0xFF1B1820),
    surfaceVariant = Color(0xFF2A2630),
    primaryContainer = Color(0xFF2B2250),
)

@Composable
fun DroxmTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val colors = when {
        forceDark -> IncognitoColors
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    // Iconos de la barra de estado/navegación claros u oscuros según el tema.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
