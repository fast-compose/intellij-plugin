package ma7moud3ly.fast.compose.intellij

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.interpreter.ExternalResolver
import ma7moud3ly.fast.compose.interpreter.LocalExternalResolver
import ma7moud3ly.fast.compose.interpreter.SourceProvider
import ma7moud3ly.fast.compose.ui.FastComposePreview
import ma7moud3ly.fast.compose.ui.rememberComposeCode

/** The previewed file and its current text, written by [PreviewPanel] on the UI thread. */
@Stable
class PreviewState(isDark: Boolean) {
    var path: String? by mutableStateOf(null)
        private set
    var text: String by mutableStateOf("")
    var isDark: Boolean by mutableStateOf(isDark)

    /** Images and strings for `R` and `Res`, from the file's module and its dependencies. */
    var resources: ResourceProvider by mutableStateOf(ResourceProvider.Empty)

    /** Runs library code the engine has no bindings for, through reflection on the module's library jars. */
    var externals: ExternalResolver? by mutableStateOf(null)

    /** Finds declarations from other project files for the next file shown; set before [show]. */
    var sources: SourceProvider? = null

    /** Changes on [rebuild], which recreates the preview from the current text and files. */
    var generation: Int by mutableIntStateOf(0)
        private set

    /** Recreates the preview: parses the file again, loads the files it uses again and restarts its previews. */
    fun rebuild() {
        generation++
    }

    /** Height of the logs below the previews, changed by dragging the divider above them; kept across files. */
    var logsHeight: Dp by mutableStateOf(160.dp)

    /**
     * True while the IDE indexes the project, e.g. after a Gradle sync; other modules' files and resources may be
     * missing until then, and the preview refreshes when it ends.
     */
    var isIndexing: Boolean by mutableStateOf(false)

    /** Whether the file has a `@Preview` annotation; null until checked. Only with true is anything rendered. */
    var hasPreviews: Boolean? by mutableStateOf(null)

    /**
     * The exception Compose last threw while composing, measuring or drawing a preview; while set, previews are not
     * rendered and the crash shows in the logs. Cleared by [PreviewPanel] on an edit, a refresh or another file.
     */
    var crash: Throwable? by mutableStateOf(null)

    fun show(path: String, text: String) {
        if (path != this.path) hasPreviews = null
        this.text = text
        this.path = path
    }

    /** Shows no file, e.g. when a non-Kotlin file is selected. */
    fun clear() {
        path = null
        text = ""
        hasPreviews = null
        crash = null
        sources = null
        resources = ResourceProvider.Empty
        externals = null
    }
}

/**
 * Previews every `@Preview` of [state]'s file, with its logs below when there is something to show. A file without a
 * `@Preview` annotation ([PreviewState.hasPreviews] false) is not parsed, loaded or rendered at all; only a message is
 * shown, and nothing is shown until the check is done.
 */
@Composable
fun PreviewContent(state: PreviewState, onOpen: (file: String?, line: Int) -> Unit) {
    MaterialTheme(colorScheme = if (state.isDark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val path = state.path
            if (path == null) {
                Message("Open a Kotlin file with @Preview functions", Modifier.fillMaxSize())
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (state.isIndexing && state.hasPreviews == true) {
                        Text(
                            "Indexing the project; files and resources of other modules may be missing until it ends. " +
                                "The preview refreshes then.",
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        key(path, state.generation) {
                            when (state.hasPreviews) {
                                true -> FilePreview(state, path, onOpen)
                                false -> Message("No @Preview functions in ${path.substringAfterLast('/')}", Modifier.fillMaxSize())
                                null -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePreview(state: PreviewState, path: String, onOpen: (file: String?, line: Int) -> Unit) {
    val code = rememberComposeCode(state.text, path, state.sources)
    LaunchedEffect(code) {
        snapshotFlow { state.text }.collect { code.update(it) }
    }
    // After a crash the panel is new, and so is this code: report the crash in its logs once.
    val crash = state.crash
    LaunchedEffect(code, crash) {
        if (crash != null) code.reportCrash(crash)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The logs can grow until the previews keep only a small strip.
        val maxLogs = (maxHeight - MIN_PREVIEW_HEIGHT).coerceAtLeast(MIN_LOGS_HEIGHT)
        Column(Modifier.fillMaxSize()) {
            val program = code.program
            when {
                crash != null -> Message(
                    "The preview crashed; see the logs. Edit the file or press Refresh to try again.",
                    Modifier.weight(1f).fillMaxWidth(),
                )

                program != null && program.previews.isEmpty() -> Message(
                    "No @Preview functions in ${path.substringAfterLast('/')}",
                    Modifier.weight(1f).fillMaxWidth(),
                )

                else -> CompositionLocalProvider(LocalExternalResolver provides state.externals) {
                    FastComposePreview(code, Modifier.weight(1f).fillMaxWidth(), resources = state.resources)
                }
            }
            if (hasLogs(code)) {
                LogsDivider { delta ->
                    state.logsHeight = (state.logsHeight - delta).coerceIn(MIN_LOGS_HEIGHT, maxLogs)
                }
                val logsHeight = state.logsHeight.coerceIn(MIN_LOGS_HEIGHT, maxLogs)
                PreviewLogs(code, Modifier.fillMaxWidth().height(logsHeight), onOpen)
            }
        }
    }
}

/** A thin bar above the logs; dragging it up or down by a distance calls [onDrag] with it, to resize the logs. */
@Composable
private fun LogsDivider(onDrag: (Dp) -> Unit) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> currentOnDrag(with(density) { delta.toDp() }) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val MIN_LOGS_HEIGHT = 48.dp
private val MIN_PREVIEW_HEIGHT = 80.dp

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
