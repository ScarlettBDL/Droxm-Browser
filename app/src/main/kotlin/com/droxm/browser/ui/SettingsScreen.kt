package com.droxm.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.engine.FilterUpdater
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.settings.BarPosition
import com.droxm.browser.settings.BarSize
import com.droxm.browser.settings.SearchEngine
import com.droxm.browser.settings.ThemeMode
import com.droxm.browser.util.BackgroundFiles
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: AppSettings,
    frequentSites: FrequentSites,
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

                SectionTitle("Pantalla de inicio")

                HomeBackgroundSetting(settings)

                DarknessSetting(settings)

                SwitchSetting(
                    title = "Mostrar el texto \"Droxm\"",
                    description = "El título grande de la pantalla de inicio.",
                    checked = settings.showHomeTitle,
                    onChange = settings::updateShowHomeTitle,
                )

                SwitchSetting(
                    title = "Mostrar sitios más visitados",
                    description = "Accesos a las páginas a las que más entrás. Mantené apretado un sitio para quitarlo.",
                    checked = settings.showFrequentSites,
                    onChange = settings::updateShowFrequentSites,
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

                SectionTitle("Privacidad")

                SwitchSetting(
                    title = "Bloquear anuncios",
                    description = "Bloquea anuncios y rastreadores con EasyList y EasyPrivacy. No cubre los anuncios propios de cada sitio (por ejemplo los de YouTube).",
                    checked = settings.adBlock,
                    onChange = settings::updateAdBlock,
                )

                if (settings.adBlock) {
                    Column {
                        Text("Listas de filtros", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            FilterUpdater.status.ifEmpty { "EasyList + EasyPrivacy" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { FilterUpdater.updateNow() }, enabled = !FilterUpdater.busy) {
                            Text(if (FilterUpdater.busy) "Actualizando…" else "Actualizar ahora")
                        }
                    }
                }

                Column {
                    Text("Sitios más visitados", style = MaterialTheme.typography.bodyLarge)
                    TextButton(
                        onClick = { frequentSites.clear() },
                        enabled = frequentSites.sites.isNotEmpty(),
                    ) {
                        Text("Borrar lista (${frequentSites.sites.size})")
                    }
                }

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
private fun HomeBackgroundSetting(settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            error = null
            val result = withContext(Dispatchers.IO) { BackgroundFiles.import(context, uri) }
            if (result.fileName != null) {
                val old = settings.homeBgFile
                settings.updateHomeBackground(result.fileName, result.isVideo)
                withContext(Dispatchers.IO) { BackgroundFiles.delete(context, old) }
            } else {
                error = result.error
            }
            importing = false
        }
    }

    val current = when {
        settings.homeBgFile == null -> "Sin fondo (se usa un degradado)"
        settings.homeBgIsVideo -> "Fondo actual: video"
        else -> "Fondo actual: foto o animación"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fondo", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Foto, GIF animado o video (hasta 150 MB). Un video corto y liviano gasta menos batería.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (importing) "Importando..." else current,
            style = MaterialTheme.typography.bodyMedium,
        )
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !importing,
                onClick = { picker.launch(arrayOf("image/*", "video/*")) },
            ) {
                Text("Elegir archivo")
            }
            OutlinedButton(
                enabled = !importing && settings.homeBgFile != null,
                onClick = {
                    val old = settings.homeBgFile
                    settings.updateHomeBackground(null, false)
                    scope.launch { withContext(Dispatchers.IO) { BackgroundFiles.delete(context, old) } }
                },
            ) {
                Text("Quitar fondo")
            }
        }
    }
}

@Composable
private fun DarknessSetting(settings: AppSettings) {
    val percent = (settings.homeDarkness * 100).roundToInt()
    Column {
        Text("Oscuridad del fondo: $percent%", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Subila si el fondo es muy brillante y no se lee bien el texto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = settings.homeDarkness,
            onValueChange = settings::updateHomeDarkness,
            valueRange = 0f..AppSettings.MAX_DARKNESS,
            enabled = settings.homeBgFile != null,
        )
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
