package com.droxm.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droxm.browser.data.SiteSettings
import com.droxm.browser.tabs.BrowserTab
import com.droxm.browser.tabs.TabManager

/** "Ajustes del sitio": bloqueador, ventanas emergentes, escritorio y tamaño del texto solo para el sitio abierto. */
@Composable
fun SiteSettingsDialog(manager: TabManager, tab: BrowserTab, onClose: () -> Unit) {
    val site = SiteSettings.siteOf(tab.url)
    if (site == null) {
        onClose()
        return
    }
    val store = manager.siteSettings

    var adBlockOff by remember(site) { mutableStateOf(store.adBlockOff(site)) }
    var popups by remember(site) { mutableStateOf(store.popupsAllowed(site)) }
    var desktop by remember(site) { mutableStateOf(store.desktop(site)) }
    var zoom by remember(site) { mutableStateOf(store.textZoom(site)) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(site) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ToggleRow(
                    title = "Bloquear anuncios",
                    description = "Apagalo si este sitio no anda bien con el bloqueador. Recarga la página.",
                    checked = !adBlockOff,
                ) {
                    adBlockOff = !it
                    manager.setSiteAdBlockOff(tab, adBlockOff)
                }

                ToggleRow(
                    title = "Permitir ventanas emergentes",
                    description = "Para sitios que las necesitan (por ejemplo, inicios de sesión).",
                    checked = popups,
                ) {
                    popups = it
                    manager.setSitePopups(tab, it)
                }

                Column {
                    Text("Versión del sitio", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Pair<String, Boolean?>>("Normal" to null, "Escritorio" to true, "Móvil" to false).forEach { (label, value) ->
                            FilterChip(
                                selected = desktop == value,
                                onClick = {
                                    desktop = value
                                    manager.setSiteDesktop(tab, value)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                Column {
                    Text("Tamaño del texto", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf<Int?>(null, 80, 100, 125, 150, 200).forEach { value ->
                            FilterChip(
                                selected = zoom == value,
                                onClick = {
                                    zoom = value
                                    manager.setSiteZoom(tab, value)
                                },
                                label = { Text(if (value == null) "General" else "$value %") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Listo") } },
        dismissButton = {
            TextButton(
                onClick = {
                    manager.resetSite(tab)
                    onClose()
                },
                enabled = store.hasAny(site),
            ) { Text("Restablecer") }
        },
    )
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
