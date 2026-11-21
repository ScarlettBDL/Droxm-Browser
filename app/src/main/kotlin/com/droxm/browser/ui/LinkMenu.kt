package com.droxm.browser.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droxm.browser.tabs.TabManager
import com.droxm.browser.util.Actions

/** Menú que aparece al mantener apretado un enlace o una imagen de la página. */
@Composable
fun LinkMenu(manager: TabManager, hit: TabManager.Hit) {
    val context = LocalContext.current
    val tab = manager.tabs.firstOrNull { it.id == hit.tabId }
    // La pestaña se cerró mientras el menú estaba pendiente: no hay nada que mostrar.
    if (tab == null) return

    val link = hit.info.linkUrl
    val image = hit.info.imageUrl
    val webLink = link != null && Actions.isWebUrl(link)
    val webImage = image != null && Actions.isWebUrl(image)

    val title = link ?: image ?: ""

    // Cada acción cierra el menú primero y después hace lo suyo.
    fun act(action: () -> Unit) {
        manager.dismissHit()
        action()
    }

    AlertDialog(
        onDismissRequest = { manager.dismissHit() },
        title = {
            Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            Column {
                if (link != null && webLink) {
                    MenuRow("Abrir en pestaña nueva") { act { manager.openInNewTab(tab, link) } }
                    MenuRow("Abrir en segundo plano") {
                        act {
                            manager.openInNewTab(tab, link, select = false)
                            Toast.makeText(context, "Abierta en segundo plano", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (!tab.incognito) {
                        MenuRow("Abrir en incógnito") { act { manager.openInIncognito(link) } }
                    }
                }
                if (link != null) {
                    MenuRow("Copiar enlace") { act { Actions.copy(context, link) } }
                    if (webLink) MenuRow("Compartir enlace") { act { Actions.share(context, link) } }
                }
                if (image != null) {
                    if (webImage) {
                        MenuRow("Descargar imagen") { act { manager.downloadImage(tab, image) } }
                        MenuRow("Abrir imagen en pestaña nueva") { act { manager.openInNewTab(tab, image) } }
                        MenuRow("Copiar dirección de la imagen") {
                            act { Actions.copy(context, image, "Dirección copiada") }
                        }
                    } else {
                        // data: y blob: no se pueden bajar con el gestor de descargas del sistema.
                        Text(
                            text = "Esta imagen está incrustada en la página y no se puede descargar directamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { manager.dismissHit() }) { Text("Cancelar") }
        },
    )
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
}
