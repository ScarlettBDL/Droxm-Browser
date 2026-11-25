package com.droxm.browser.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import android.content.Intent
import android.provider.Settings
import com.droxm.browser.data.FrequentSites
import com.droxm.browser.data.HomeBackgrounds
import com.droxm.browser.data.SitePermissions
import com.droxm.browser.data.SiteSettings
import com.droxm.browser.engine.FilterUpdater
import com.droxm.browser.settings.AppSettings
import com.droxm.browser.settings.AccentColor
import com.droxm.browser.settings.BarPosition
import com.droxm.browser.settings.BarShape
import com.droxm.browser.settings.HomeSource
import com.droxm.browser.settings.BarSize
import com.droxm.browser.settings.SearchEngine
import com.droxm.browser.settings.SitesPosition
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
    sitePermissions: SitePermissions,
    siteSettings: SiteSettings,
    backgrounds: HomeBackgrounds,
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

                SwitchSetting(
                    title = "Botón de inicio",
                    description = "Vuelve la pestaña a la pantalla de inicio (se pierde su historial de atrás/adelante).",
                    checked = settings.showHomeButton,
                    onChange = settings::updateShowHomeButton,
                )

                SwitchSetting(
                    title = "Botón de nueva pestaña (+)",
                    description = "Un acceso directo en la barra, al lado del contador de pestañas.",
                    checked = settings.showNewTabButton,
                    onChange = settings::updateShowNewTabButton,
                )

                SwitchSetting(
                    title = "Botón de recargar",
                    description = "Dentro de la barra de direcciones. Mientras carga, siempre aparece el de detener.",
                    checked = settings.showReloadButton,
                    onChange = settings::updateShowReloadButton,
                )

                SwitchSetting(
                    title = "Esconder la barra al bajar",
                    description = "La barra desaparece al deslizar hacia abajo y vuelve al subir.",
                    checked = settings.hideBarOnScroll,
                    onChange = settings::updateHideBarOnScroll,
                )

                SwitchSetting(
                    title = "Deslizar la barra para cambiar de pestaña",
                    description = "Izquierda = pestaña siguiente, derecha = anterior.",
                    checked = settings.swipeTabs,
                    onChange = settings::updateSwipeTabs,
                )

                ChoiceSetting(
                    title = "Columnas del selector de pestañas",
                    options = listOf(1, 2, 3),
                    selected = settings.tabColumns,
                    label = { it.toString() },
                    onSelect = settings::updateTabColumns,
                )

                SectionTitle("Pantalla de inicio")

                BackgroundsSettings(backgrounds)

                DarknessSetting(settings)

                SwitchSetting(
                    title = "Mostrar el texto \"Droxm\"",
                    description = "El título grande de la pantalla de inicio.",
                    checked = settings.showHomeTitle,
                    onChange = settings::updateShowHomeTitle,
                )

                SwitchSetting(
                    title = "Mostrar hora y fecha",
                    description = "Un reloj arriba del título.",
                    checked = settings.showHomeClock,
                    onChange = settings::updateShowHomeClock,
                )

                SwitchSetting(
                    title = "Mostrar accesos directos",
                    description = "Sitios en la pantalla de inicio. Mantené apretado uno para quitarlo.",
                    checked = settings.showFrequentSites,
                    onChange = settings::updateShowFrequentSites,
                )

                if (settings.showFrequentSites) {
                    ChoiceSetting(
                        title = "Qué mostrar",
                        options = HomeSource.entries,
                        selected = settings.homeSource,
                        label = { it.label },
                        onSelect = settings::updateHomeSource,
                    )

                    ChoiceSetting(
                        title = "Cantidad de sitios",
                        description = if (settings.homeSource == HomeSource.FREQUENT) "Solo aparecen sitios que visitaste 3 veces o más." else null,
                        options = listOf(3, 4, 6, 8, 12),
                        selected = settings.homeSiteCount,
                        label = { it.toString() },
                        onSelect = settings::updateHomeSiteCount,
                    )

                    ChoiceSetting(
                        title = "Posición de los sitios",
                        options = SitesPosition.entries,
                        selected = settings.homeSitesPosition,
                        label = { it.label },
                        onSelect = settings::updateHomeSitesPosition,
                    )
                }

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

                if (settings.searchEngine == SearchEngine.CUSTOM) {
                    var url by remember { mutableStateOf(settings.customSearchUrl) }
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            settings.updateCustomSearchUrl(it)
                        },
                        singleLine = true,
                        label = { Text("Dirección de búsqueda") },
                        placeholder = { Text("https://ejemplo.com/search?q=%s") },
                        supportingText = { Text("Usá %s donde va lo que se busca. Si falta, se agrega al final.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AccentSetting(settings)

                if (settings.themeMode != ThemeMode.LIGHT) {
                    SwitchSetting(
                        title = "Negro puro (AMOLED)",
                        description = "En el tema oscuro, fondo totalmente negro. Ahorra batería en pantallas OLED.",
                        checked = settings.amoled,
                        onChange = settings::updateAmoled,
                    )
                }

                ChoiceSetting(
                    title = "Forma de la barra de direcciones",
                    options = BarShape.entries,
                    selected = settings.barShape,
                    label = { it.label },
                    onSelect = settings::updateBarShape,
                )

                ChoiceSetting(
                    title = "Tamaño del texto de las páginas",
                    description = "Se aplica a todos los sitios.",
                    options = listOf(80, 90, 100, 110, 125, 150, 175, 200),
                    selected = settings.textZoom,
                    label = { "$it %" },
                    onSelect = settings::updateTextZoom,
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

                SwitchSetting(
                    title = "Bloquear ventanas emergentes",
                    description = "Frena las ventanas que se abren solas o pegadas una a otra, y las que van a anunciantes. Los enlaces que tocás siguen abriendo pestañas nuevas. Si un sitio las necesita, se permiten desde \"Ajustes del sitio\".",
                    checked = settings.blockPopups,
                    onChange = settings::updateBlockPopups,
                )

                Column {
                    Text("Ajustes por sitio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Bloqueador, ventanas emergentes, versión de escritorio y tamaño del texto que cambiaste para un sitio puntual (menú ⋮ → Ajustes del sitio).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { siteSettings.clear() }, enabled = siteSettings.count > 0) {
                        Text("Borrar ajustes de sitios (${siteSettings.count})")
                    }
                }

                Column {
                    Text("Permisos de sitios", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Cámara, micrófono y ubicación que permitiste o bloqueaste con \"Recordar\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { sitePermissions.clear() }, enabled = sitePermissions.count > 0) {
                        Text("Borrar permisos guardados (${sitePermissions.count})")
                    }
                }

                ChoiceSetting(
                    title = "Pantalla completa de videos",
                    description = "En pantalla completa la ventana queda fija en vertical (girarla de verdad hacía que la memoria creciera hasta cerrar la app). Con \"Girar con el video\" el video se acomoda solo cuando girás el celular; con \"Vertical fijo\" no gira.",
                    options = listOf(0, 1),
                    selected = com.droxm.browser.engine.DebugFlags.fsMode,
                    label = { if (it == 0) "Girar con el video" else "Vertical fijo" },
                    onSelect = { com.droxm.browser.engine.DebugFlags.chooseFsMode(it) },
                )

                Column {
                    Text("Pruebas de diagnóstico", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Para descartar causas de un problema. Normalmente van todas apagadas. Las marcadas con * se aplican a pestañas nuevas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SwitchSetting(
                    title = "Sin interceptar pedidos *",
                    description = "Desactiva el bloqueo de anuncios por pedidos en pestañas nuevas.",
                    checked = com.droxm.browser.engine.DebugFlags.noIntercept,
                    onChange = { com.droxm.browser.engine.DebugFlags.set("noIntercept", it) },
                )
                SwitchSetting(
                    title = "Sin filtros cosméticos *",
                    description = "No instala el puente de JavaScript ni el script de ocultamiento.",
                    checked = com.droxm.browser.engine.DebugFlags.noCosmetic,
                    onChange = { com.droxm.browser.engine.DebugFlags.set("noCosmetic", it) },
                )
                SwitchSetting(
                    title = "Identificación original del WebView *",
                    description = "No se hace pasar por un Chrome común.",
                    checked = com.droxm.browser.engine.DebugFlags.defaultUa,
                    onChange = { com.droxm.browser.engine.DebugFlags.set("defaultUa", it) },
                )
                SwitchSetting(
                    title = "No esconder nada en pantalla completa",
                    description = "Deja la interfaz y la página visibles detrás del video.",
                    checked = com.droxm.browser.engine.DebugFlags.keepVisible,
                    onChange = { com.droxm.browser.engine.DebugFlags.set("keepVisible", it) },
                )

                Column {
                    val ctx = LocalContext.current
                    Text("Diagnóstico", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Si la app se cierra sola, copiá el informe y mandalo: dice si fue por falta de memoria o por un error.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Droxm", com.droxm.browser.util.Diagnostics.report(ctx)))
                        android.widget.Toast.makeText(ctx, "Informe copiado", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar informe") }
                }

                Column {
                    val ctx = LocalContext.current
                    Text("Contraseñas y autocompletar", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Droxm usa el autocompletar de Android (Google, Bitwarden, etc.). Se elige en los ajustes del sistema, en \"Autocompletar\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (e: Exception) {
                            // Sin pantalla de ajustes disponible.
                        }
                    }) { Text("Abrir ajustes del sistema") }
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
private fun AccentSetting(settings: AppSettings) {
    val options = AccentColor.entries.filter { it != AccentColor.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color principal", style = MaterialTheme.typography.bodyLarge)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                text = "\"Dinámico\" toma los colores de tu fondo de pantalla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                val selected = option == settings.accent || (option == AccentColor.VIOLET && settings.accent == AccentColor.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (option == AccentColor.DYNAMIC) {
                                Brush.sweepGradient(listOf(Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFF30A46C), Color(0xFF0091FF), Color(0xFFD6409F), Color(0xFFE5484D)))
                            } else {
                                SolidColor(Color(option.argb))
                            },
                        )
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .clickable { settings.updateAccent(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = option.label, tint = Color.White)
                }
            }
        }
    }
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
