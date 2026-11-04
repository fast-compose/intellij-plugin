package ma7moud3ly.fast.compose.intellij

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** Creates the "Fast Compose Preview" tool window on the right side bar, next to Running Devices. */
class PreviewToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PreviewPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        toolWindow.setTitleActions(
            listOf(
                RefreshPreviewAction(panel),
                ToggleWindowModeAction(toolWindow),
                ReportIssueGroup(),
            ),
        )
    }
}
