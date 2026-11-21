package com.droxm.browser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.settings.BarPosition
import com.droxm.browser.settings.BarSize
import com.droxm.browser.settings.SearchEngine
import com.droxm.browser.settings.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text("Ajustes", style = MaterialTheme.typography.titleLarge)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SectionTitle("Distribución")

                ChoiceSetting(
                    title = "Posición de la barra",
                    options = BarPosition.entries,
                    selected = settings.barPosition,
                    label = { it.label },
                    onSelect = settings::updateBarPosition,
                )

                ChoiceSetting(
                    title = "Tamaño de la barra",
                    options = BarSize.entries,
                    selected = settings.barSize,
                    label = { it.label },
                    onSelect = settings::updateBarSize,
                )

                SwitchSetting(
                    title = "Botones atrás y adelante",
                    description = "Si los ocultás, se sigue pudiendo volver con el gesto del sistema.",
                    checked = settings.showNavButtons,
                    onChange = settings::updateShowNavButtons,
                )

                SectionTitle("Apariencia")

                ChoiceSetting(
                    title = "Tema",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.label },
                    onSelect = settings::updateThemeMode,
                )

                SectionTitle("Búsqueda")

                ChoiceSetting(
                    title = "Buscador",
                    options = SearchEngine.entries,
                    selected = settings.searchEngine,
                    label = { it.label },
                    onSelect = settings::updateSearchEngine,
                )

                SectionTitle("Rendimiento")

                ChoiceSetting(
                    title = "Pestañas activas a la vez",
                    description = "Las demás se duermen y se recargan al abrirlas. Menos pestañas activas = menos memoria.",
                    options = listOf(1, 2, 3, 4, 5, 6),
                    selected = settings.maxLiveTabs,
                    label = { it.toString() },
                    onSelect = settings::updateMaxLiveTabs,
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    description: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
