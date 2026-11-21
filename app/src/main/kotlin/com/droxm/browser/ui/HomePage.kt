package com.droxm.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Página de inicio de una pestaña vacía.
 * En el paso de personalización acá van el fondo (foto/animado) y los accesos rápidos.
 */
@Composable
fun HomePage(
    incognito: Boolean,
    incognitoIsolated: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(colors.primaryContainer, colors.background)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Droxm",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            if (incognito) {
                Text(
                    text = "Modo incógnito",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No se guarda historial, cookies ni datos de sitios al cerrar las pestañas incógnito.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!incognitoIsolated) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Este dispositivo no permite separar por completo las cookies del modo normal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = "Escribí una dirección o buscá desde la barra",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
