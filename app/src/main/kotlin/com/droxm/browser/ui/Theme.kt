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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.droxm.browser.settings.AccentColor
import com.droxm.browser.settings.ThemeMode

/** Esquema armado a partir de un solo color. */
private fun accentScheme(color: Color, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = lerp(color, Color.White, 0.5f),
        primaryContainer = lerp(color, Color.Black, 0.55f),
        onPrimaryContainer = Color.White,
        secondary = lerp(color, Color.White, 0.6f),
    )
} else {
    lightColorScheme(
        primary = color,
        primaryContainer = lerp(color, Color.White, 0.8f),
        onPrimaryContainer = lerp(color, Color.Black, 0.6f),
        secondary = lerp(color, Color.Black, 0.2f),
    )
}

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
    accent: AccentColor = AccentColor.DYNAMIC,
    amoled: Boolean = false,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val dynamic = accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        forceDark -> IncognitoColors
        dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        accent != AccentColor.DYNAMIC -> accentScheme(Color(accent.argb), dark)
        dark -> DarkColors
        else -> LightColors
    }
    // Negro puro: el fondo y las superficies pasan a negro, sin el tinte del color principal.
    val colors = if (amoled && dark && !forceDark) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1E),
            surfaceTint = Color.Transparent,
            primaryContainer = lerp(base.primaryContainer, Color.Black, 0.5f),
        )
    } else {
        base
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
