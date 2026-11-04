package ma7moud3ly.fast.compose.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** Rebuilds the preview from scratch, re-reading the file, the files it uses and its resources. */
class RefreshPreviewAction(private val panel: PreviewPanel) : DumbAwareAction(
    "Refresh Preview",
    "Read the file, the files it uses and its resources again",
    AllIcons.Actions.Refresh,
) {
    override fun actionPerformed(e: AnActionEvent) = panel.refresh()
}
