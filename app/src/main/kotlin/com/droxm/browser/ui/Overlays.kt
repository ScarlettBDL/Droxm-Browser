package com.droxm.browser.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.droxm.browser.tabs.TabManager

/** Pantalla completa (videos): oculta las barras del sistema, deja girar la pantalla y muestra la vista del motor. */
@Composable
fun FullscreenOverlay(view: View, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity

    // La vista de pantalla completa se pone directo en el contenido de la actividad (como hacen los navegadores),
    // fuera del árbol de Compose: así los cambios de tamaño o de recomposición no la tocan mientras el video corre.
    DisposableEffect(view) {
        val content = activity?.findViewById<ViewGroup>(android.R.id.content)
        val holder = FrameLayout(view.context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            isClickable = true
            keepScreenOn = true
        }
        // Como otros navegadores, se esconde toda la interfaz mientras dura el video: no se dibuja ni se recalcula
        // nada por detrás en cada cuadro, y al girar la pantalla no hay nada que volver a medir.
        val appRoot = if (com.droxm.browser.engine.DebugFlags.keepVisible) null else content?.getChildAt(0)
        val appRootVisibility = appRoot?.visibility ?: View.VISIBLE
        appRoot?.visibility = View.GONE
        if (content != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            holder.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            content.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            appRoot?.visibility = appRootVisibility
            if (content != null) {
                content.removeView(holder)
                holder.removeAllViews()
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

}

/** "Este sitio quiere usar la cámara…": Permitir o Bloquear, con la opción de recordar la decisión. */
@Composable
fun PermissionDialog(prompt: TabManager.PermissionPrompt, onAnswer: (allow: Boolean, remember: Boolean) -> Unit) {
    var keep by remember { mutableStateOf(!prompt.incognito) }
    val what = prompt.perms.joinToString(" y ") { it.label }

    AlertDialog(
        onDismissRequest = { onAnswer(false, false) },
        title = { Text("Permiso para ${prompt.site}") },
        text = {
            Column {
                Text("${prompt.site} quiere usar $what.")
                if (!prompt.incognito) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { keep = !keep },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = keep, onCheckedChange = { keep = it })
                        Text("Recordar mi decisión")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true, keep) }) { Text("Permitir") } },
        dismissButton = { TextButton(onClick = { onAnswer(false, keep) }) { Text("Bloquear") } },
    )
}
