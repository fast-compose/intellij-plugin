package ma7moud3ly.fast.compose.intellij

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import ma7moud3ly.fast.compose.interpreter.ExternalResolver
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.ui.ComposeCode
import ma7moud3ly.fast.compose.ui.FastComposeContent
import ma7moud3ly.fast.compose.ui.PreviewConfig
import ma7moud3ly.fast.compose.ui.PreviewEntry
import ma7moud3ly.fast.compose.ui.previews

/**
 * Every preview of [code]'s file, from the engine's `previews`: one per `@Preview`, one per `@Preview` of a
 * multi-preview annotation (e.g. `@PreviewLightDark` or the project's own), and one per value of a `@PreviewParameter`
 * provider. Null while the file has no parsed program, e.g. while loading or with parse errors. Read with an
 * interpreter of its own, using [externals] for library code in providers; a failing provider is skipped.
 */
@Composable
fun rememberPreviews(code: ComposeCode, registry: Registry, externals: ExternalResolver?): List<PreviewEntry>? {
    val program = code.program ?: return null
    val dependencies = code.dependencies
    return remember(program, dependencies, registry, externals) {
        val reader = Interpreter(program, registry, dependencies = dependencies, externals = externals)
        runCatching { reader.previews(program) }.getOrDefault(emptyList())
    }
}

/**
 * Lists [previews] of [code], each in its own frame at [PreviewState.device]'s size, scaled by
 * [PreviewState.zoom] or, with Fit, to the largest scale at which one device fits the panel. A preview without
 * `widthDp`/`heightDp` wraps its content up to the device size, so full screens get the whole device. Scaling changes
 * the density, not a picture: previews lay out in device dp, text stays sharp and clicks land where they should.
 * Names keep the panel's size. Each preview is centered in the panel's width, and one wider than the panel starts at
 * its left edge; the list scrolls both ways, and Ctrl + mouse wheel zooms.
 */
@Composable
fun DevicePreviews(
    state: PreviewState,
    code: ComposeCode,
    registry: Registry,
    previews: List<PreviewEntry>,
    modifier: Modifier,
) {
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
            ) {
                previews.forEachIndexed { index, preview ->
                    key(preview.function.name, index) {
                        // Centered in the panel's visible width, so a wider preview doesn't push the others aside;
                        // a preview wider than the panel starts at its left edge.
                        Box(Modifier.widthIn(min = panelWidth - PADDING * 2), contentAlignment = Alignment.TopCenter) {
                            DevicePreview(state, code, registry, preview, device, zoom)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One preview: its name, then its function, called with its arguments, in a frame of [device]'s size (or the
 * `@Preview` size), scaled by [zoom].
 */
@Composable
private fun DevicePreview(
    state: PreviewState,
    code: ComposeCode,
    registry: Registry,
    preview: PreviewEntry,
    device: PreviewDevice,
    zoom: Float,
) {
    val config = preview.config
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
            .then(
                if (config.heightDp != null) Modifier.height(height * zoom) else Modifier.heightIn(max = height * zoom),
            )
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
                    FastComposeContent(
                        code,
                        entry = preview.function.name,
                        registry = registry,
                        resources = state.resources,
                        arguments = preview.arguments,
                    )
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

/**
 * Bar below the previews: the device menu, the Logs button and the Refresh and Report Issue icons on the left; zoom
 * out, the zoom menu and zoom in on the right. Without [showZoom], e.g. after a crash, the device menu and zoom are
 * hidden. The Logs button shows or hides the logs, and counts the lines of [code]'s logs, in the error color when
 * there are errors. Refresh calls [onRefresh]; Report Issue opens a menu of [ReportIssueTarget]s and calls
 * [onReportIssue] with the picked one. Below [COMPACT_BAR_WIDTH], the device menu shows the device's name without its
 * size.
 */
@Composable
fun DeviceBar(
    state: PreviewState,
    code: ComposeCode,
    showZoom: Boolean,
    onRefresh: () -> Unit,
    onReportIssue: (ReportIssueTarget) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        DeviceBarRow(state, code, showZoom, compact = maxWidth < COMPACT_BAR_WIDTH, onRefresh, onReportIssue)
    }
}

@Composable
private fun DeviceBarRow(
    state: PreviewState,
    code: ComposeCode,
    showZoom: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    onReportIssue: (ReportIssueTarget) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val device = state.device
        if (showZoom) {
            val dimensions = if (compact) "" else "  ${device.widthDp} × ${device.heightDp}"
            MenuButton("${device.title}$dimensions") { close ->
                PreviewDevice.entries.forEach {
                    MenuItem("${it.title}  (${it.widthDp} × ${it.heightDp} dp)", selected = it == device) {
                        state.device = it
                        close()
                    }
                }
            }
        }
        val count = logCount(code)
        BarButton(
            text = if (count == 0) "Logs" else "Logs ($count)",
            color = if (hasErrors(code)) MaterialTheme.colorScheme.error else Color.Unspecified,
            selected = state.showLogs,
        ) {
            state.showLogs = !state.showLogs
        }
        IconBarButton(BarIcons.Refresh, "Refresh", onClick = onRefresh)
        var reporting by remember { mutableStateOf(false) }
        Box {
            IconBarButton(BarIcons.Feedback, "Report Issue") { reporting = true }
            DropdownMenu(expanded = reporting, onDismissRequest = { reporting = false }) {
                ReportIssueTarget.entries.forEach {
                    DropdownMenuItem(
                        text = { Text(it.title, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            reporting = false
                            onReportIssue(it)
                        },
                    )
                }
            }
        }
        if (showZoom) {
            Spacer(Modifier.weight(1f))
            ZoomButtons(state)
        }
    }
}

/** Zoom out, the zoom menu (Fit and [ZOOM_LEVELS]) and zoom in. */
@Composable
private fun ZoomButtons(state: PreviewState) {
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

/** A text button of the bar, in [color] when given, with a background while [selected]. */
@Composable
private fun BarButton(
    text: String,
    color: Color = Color.Unspecified,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false)
    }
}

/** An [icon] button of the bar, with [label] as its tooltip on hover. */
@Composable
private fun IconBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(6.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        }
    }
}

/** Bar width below which the device menu leaves out the device's size. */
private val COMPACT_BAR_WIDTH = 420.dp

private val PADDING = 16.dp

/** Height of a preview's name above its frame. */
private val LABEL_SPACE = 28.dp
