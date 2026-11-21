package com.droxm.browser.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.util.BackgroundFiles
import kotlin.math.abs

private const val COLUMNS = 4
private const val MAX_SITES = 8

/** Pantalla de una pestaña vacía. En incógnito se ve distinta: sin fondo propio ni sitios frecuentes. */
@Composable
fun HomePage(
    incognito: Boolean,
    incognitoIsolated: Boolean,
    settings: AppSettings,
    frequentSites: FrequentSites,
    onOpenSite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (incognito) {
        IncognitoHome(incognitoIsolated, modifier)
    } else {
        NormalHome(settings, frequentSites, onOpenSite, modifier)
    }
}

@Composable
private fun NormalHome(
    settings: AppSettings,
    frequentSites: FrequentSites,
    onOpenSite: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    val bgName = settings.homeBgFile
    val bgFile = remember(bgName) {
        bgName?.let { BackgroundFiles.file(context, it) }?.takeIf { it.exists() }
    }
    val hasBackground = bgFile != null
    val textColor = if (hasBackground) Color.White else colors.onBackground
    val softTextColor = if (hasBackground) Color.White.copy(alpha = 0.8f) else colors.onSurfaceVariant
    val shadow = if (hasBackground) Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 10f) else null

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(colors.primaryContainer, colors.background)),
        ),
    ) {
        if (bgFile != null) {
            if (settings.homeBgIsVideo) {
                VideoBackdrop(bgFile, Modifier.fillMaxSize())
            } else {
                ImageBackdrop(bgFile, Modifier.fillMaxSize())
            }
            // Capa negra encima: es lo que controla el slider de "Oscuridad".
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.homeDarkness)),
            )
        }

        val sites = if (settings.showFrequentSites) frequentSites.sites.take(MAX_SITES) else emptyList()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings.showHomeTitle) {
                Text(
                    text = "Droxm",
                    style = MaterialTheme.typography.displayMedium.copy(shadow = shadow),
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                if (sites.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Escribí una dirección o buscá desde la barra",
                        style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                        color = softTextColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (sites.isNotEmpty()) {
                if (settings.showHomeTitle) Spacer(Modifier.height(32.dp))
                SiteGrid(
                    sites = sites,
                    textColor = textColor,
                    onOpen = onOpenSite,
                    onRemove = { frequentSites.remove(it) },
                )
            }
        }
    }
}

@Composable
private fun SiteGrid(
    sites: List<FrequentSites.Site>,
    textColor: Color,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        sites.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { site ->
                    SiteTile(
                        site = site,
                        textColor = textColor,
                        onOpen = { onOpen(site.url) },
                        onRemove = { onRemove(site.host) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Completa la última fila para que las columnas queden alineadas.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(
    site: FrequentSites.Site,
    textColor: Color,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hue = (abs(site.host.hashCode()) % 360).toFloat()

    Column(
        modifier = modifier
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.hsv(hue, 0.45f, 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = site.host.first().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = site.host,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Quitar de la lista") },
                onClick = {
                    menuOpen = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun IncognitoHome(incognitoIsolated: Boolean, modifier: Modifier) {
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
        }
    }
}
