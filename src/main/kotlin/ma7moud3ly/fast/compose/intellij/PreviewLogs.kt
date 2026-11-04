package ma7moud3ly.fast.compose.intellij

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.ide.CopyPasteManager
import kotlinx.coroutines.delay
import java.awt.datatransfer.StringSelection
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.ui.ComposeCode
import ma7moud3ly.fast.compose.ui.LogLine

/**
 * All [lines] as plain text, e.g. to paste into an issue. Lines about other files start with the file's name, as
 * runtime errors already do (`Theme.kt:12: …`).
 */
private fun logText(lines: List<LogLine>): String = lines.joinToString("\n") { line ->
    val name = line.file?.substringAfterLast('/')?.substringAfterLast('\\')
    if (name == null || line.text.startsWith(name)) line.text else "$name: ${line.text}"
}

/** True when [code] has parse errors, runtime errors or output to show in [PreviewLogs]. */
internal fun hasLogs(code: ComposeCode) = code.logs.isNotEmpty() || code.diagnostics.isNotEmpty()

/**
 * Problems and output of the previewed file: its parse errors, then runtime errors and `println` output, newest last.
 * Lines about other files the preview uses (e.g. `Theme.kt:12: …`) are hidden behind an "Other files" toggle, so
 * they don't mix with this file's. Clicking a line with a location calls [onOpen] with its file (null for the
 * previewed file) and 1-based line.
 */
@Composable
internal fun PreviewLogs(code: ComposeCode, modifier: Modifier, onOpen: (file: String?, line: Int) -> Unit) {
    val diagnostics = code.diagnostics.map {
        val isError = it.severity == Diagnostic.Severity.ERROR
        LogLine("Line ${it.span.line}:${it.span.column}: ${it.message}", isError, it.span.line, isWarning = !isError)
    }
    val all = diagnostics + code.logs
    val others = all.count { it.file != null }
    var showOthers by remember(code) { mutableStateOf(false) }
    val entries = if (showOthers) all else all.filter { it.file == null }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Logs", color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            if (others > 0) {
                TextButton(onClick = { showOthers = !showOthers }) {
                    Text(if (showOthers) "Hide other files" else "Other files ($others)", fontSize = 12.sp)
                }
            }
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(copied) {
                if (copied) {
                    delay(1_500)
                    copied = false
                }
            }
            TextButton(onClick = {
                CopyPasteManager.getInstance().setContents(StringSelection(logText(all)))
                copied = true
            }) {
                Text(if (copied) "Copied" else "Copy all", fontSize = 12.sp)
            }
            TextButton(onClick = { code.clearLogs() }) { Text("Clear", fontSize = 12.sp) }
        }
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(entries) { entry ->
                    val line = entry.line
                    Text(
                        text = entry.text,
                        color = when {
                            entry.isError -> colors.error
                            entry.isWarning -> colors.tertiary
                            else -> colors.onSurface
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = if (line != null) Modifier.clickable { onOpen(entry.file, line) } else Modifier,
                    )
                }
            }
        }
    }
}
