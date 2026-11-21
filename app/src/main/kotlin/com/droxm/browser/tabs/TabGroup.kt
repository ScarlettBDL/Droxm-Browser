package com.droxm.browser.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Un grupo de pestañas: tiene nombre y color, y se puede plegar en el selector de pestañas. */
class TabGroup(
    val id: Long,
    val incognito: Boolean,
    name: String,
    colorIndex: Int,
    collapsed: Boolean = false,
) {
    var name by mutableStateOf(name)
    var colorIndex by mutableIntStateOf(colorIndex)
    var collapsed by mutableStateOf(collapsed)

    companion object {
        /** Cantidad de colores disponibles (los define la interfaz). */
        const val COLOR_COUNT = 8
    }
}
