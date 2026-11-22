package com.droxm.browser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droxm.browser.engine.EngineSession

/** Barra de "Buscar en la página": resalta las coincidencias y permite saltar entre ellas. */
@Composable
fun FindBar(
    session: EngineSession?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var active by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    DisposableEffect(session) {
        session?.onFindResult = { index, count ->
            active = index
            total = count
        }
        onDispose {
            session?.onFindResult = null
            session?.clearFind()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceVariant, modifier = Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.isBlank()) {
                                session?.clearFind()
                                active = 0
                                total = 0
                            } else {
                                session?.findAll(it)
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { session?.findNext(true) }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text("Buscar en la página", color = colors.onSurfaceVariant, fontSize = 15.sp)
                                }
                                inner()
                            }
                        },
                    )
                    if (query.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (total == 0) "0/0" else "${active + 1}/$total",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            IconButton(onClick = { session?.findNext(false) }, enabled = total > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Anterior")
            }
            IconButton(onClick = { session?.findNext(true) }, enabled = total > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Siguiente")
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
            }
        }
    }
}
