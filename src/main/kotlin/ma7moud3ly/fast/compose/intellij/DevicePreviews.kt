package ma7moud3ly.fast.compose.intellij

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ma7moud3ly.fast.compose.bindings.foundation.LocalPreviewDarkTheme
import ma7moud3ly.fast.compose.bindings.foundation.LocalPreviewWindowSize
import ma7moud3ly.fast.compose.bindings.resources.LocalResourceEnvironment
import ma7moud3ly.fast.compose.bindings.resources.ResourceEnvironment
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.ui.ComposeCode
import ma7moud3ly.fast.compose.ui.FastComposeContent
import ma7moud3ly.fast.compose.ui.PreviewConfig
import ma7moud3ly.fast.compose.ui.previewConfig
import ma7moud3ly.fast.compose.ui.rememberFullRegistry

/**
 * Lists every `@Preview` of [code], each in its own frame at [PreviewState.device]'s size, scaled by
 * [PreviewState.zoom] or, with Fit, to the largest scale at which one device fits the panel. A preview without
 * `widthDp`/`heightDp` wraps its content up to the device size, so full screens get the whole device. Scaling changes
 * the density, not a picture: previews lay out in device dp, text stays sharp and clicks land where they should.
 * Names keep the IDE's size. The list is centered and scrolls both ways; Ctrl + mouse wheel zooms.
 */
@Composable
fun DevicePreviews(state: PreviewState, code: ComposeCode, modifier: Modifier) {
    val program = code.program
    if (program == null) {
        Message(if (code.isLoading) "Loading…" else "Fix the errors to see the preview", modifier)
        return
    }
    val registry = rememberFullRegistry()
    val dependencies = code.dependencies
    val previews = remember(program, dependencies, registry) {
        val reader = Interpreter(program, registry, dependencies = dependencies)
        program.previews.map { (function, annotation) ->
            val config = runCatching { reader.previewConfig(function, annotation) }.getOrNull()
            function.name to (config ?: PreviewConfig(name = function.name))
        }
    }
    BoxWithConstraints(modifier.clipToBounds().zoomOnCtrlWheel(state)) {
        val device = state.device
        val fit = minOf(
            (maxWidth - PADDING * 2) / device.widthDp.dp,
            (maxHeight - PADDING * 2 - LABEL_SPACE) / device.heightDp.dp,
        ).coerceIn(MIN_ZOOM, 1f)
        LaunchedEffect(fit) { state.fitZoom = fit }
        val zoom = state.zoom ?: fit
        val panelWidth = maxWidth
        val panelHeight = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Column(
                Modifier.widthIn(min = panelWidth).heightIn(min = panelHeight).padding(PADDING),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                previews.forEachIndexed { index, (entry, config) ->
                    key(entry, index) {
                        DevicePreview(state, code, registry, entry, config, device, zoom)
                    }
                }
            }
        }
    }
}

/** One preview: its name, then [entry] in a frame of [device]'s size (or the `@Preview` size), scaled by [zoom]. */
@Composable
private fun DevicePreview(
    state: PreviewState,
    code: ComposeCode,
    registry: Registry,
    entry: String,
    config: PreviewConfig,
    device: PreviewDevice,
    zoom: Float,
) {
    Column {
        Text(
            config.title(),
            Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val width = (config.widthDp ?: device.widthDp).dp
        val height = (config.heightDp ?: device.heightDp).dp
        val size = Modifier
            .then(if (config.widthDp != null) Modifier.width(width * zoom) else Modifier.widthIn(max = width * zoom))
            .then(if (config.heightDp != null) Modifier.height(height * zoom) else Modifier.heightIn(max = height * zoom))
        val border = MaterialTheme.colorScheme.outlineVariant
        val base = LocalDensity.current
        MaterialTheme(colorScheme = if (config.darkTheme) darkColorScheme() else lightColorScheme()) {
            val background = when {
                config.showBackground -> config.backgroundColor ?: MaterialTheme.colorScheme.background
                config.darkTheme -> MaterialTheme.colorScheme.background
                else -> Color.Transparent
            }
            Surface(size.border(Dp.Hairline, border), color = background) {
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * zoom, config.fontScale),
                    LocalResourceEnvironment provides ResourceEnvironment(config.locale, config.darkTheme),
                    LocalPreviewDarkTheme provides config.darkTheme,
                    LocalInspectionMode provides true,
                    LocalPreviewWindowSize provides DpSize(width, height),
                ) {
                    FastComposeContent(code, entry = entry, registry = registry, resources = state.resources)
                }
            }
        }
    }
}

/** The preview's name and group, then its size, theme, font scale and locale when set. */
private fun PreviewConfig.title(): String {
    val details = listOfNotNull(
        if (widthDp != null || heightDp != null) "${widthDp ?: "wrap"} × ${heightDp ?: "wrap"} dp" else null,
        if (darkTheme) "dark" else null,
        if (fontScale != 1f) "font ×$fontScale" else null,
        locale,
    )
    val title = listOfNotNull(group, name).joinToString(" / ")
    return if (details.isEmpty()) title else "$title  ·  ${details.joinToString("  ·  ")}"
}

/** Zooms [state] with Ctrl (or Cmd) + mouse wheel, before the previews can scroll with it. */
private fun Modifier.zoomOnCtrlWheel(state: PreviewState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val modifiers = event.keyboardModifiers
            if (!modifiers.isCtrlPressed && !modifiers.isMetaPressed) continue
            val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
            if (delta != 0.0) state.zoomBy(if (delta < 0) 1 else -1)
            event.changes.forEach { it.consume() }
        }
    }
}

/** Bar below the previews: the device menu on the left; zoom out, the zoom menu and zoom in on the right. */
@Composable
fun DeviceBar(state: PreviewState, modifier: Modifier) {
    Row(modifier.height(32.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        val device = state.device
        MenuButton("${device.title}  ${device.widthDp} × ${device.heightDp}") { close ->
            PreviewDevice.entries.forEach {
                MenuItem("${it.title}  (${it.widthDp} × ${it.heightDp} dp)", selected = it == device) {
                    state.device = it
                    close()
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BarButton("−") { state.zoomBy(-1) }
        val zoom = state.zoom
        MenuButton(if (zoom == null) "Fit  ${percent(state.fitZoom)}" else percent(zoom)) { close ->
            MenuItem("Fit", selected = zoom == null) {
                state.zoom = null
                close()
            }
            ZOOM_LEVELS.forEach {
                MenuItem(percent(it), selected = zoom == it) {
                    state.zoom = it
                    close()
                }
            }
        }
        BarButton("+") { state.zoomBy(1) }
    }
}

/** A [BarButton] that opens a menu of [items]; each item calls the given `close` when picked. */
@Composable
private fun MenuButton(text: String, items: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton("$text  ▾") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items { open = false }
        }
    }
}

@Composable
private fun MenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodySmall) },
        onClick = onClick,
        leadingIcon = { Text(if (selected) "✓" else "", Modifier.width(12.dp)) },
    )
}

@Composable
private fun BarButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

private val PADDING = 16.dp

/** Height of a preview's name above its frame. */
private val LABEL_SPACE = 28.dp
