package ma7moud3ly.fast.compose.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowType

/**
 * Moves the preview between the docked tool window and a separate OS window that can be dragged to another display.
 * The IDE remembers the mode and the window's bounds.
 */
class ToggleWindowModeAction(private val toolWindow: ToolWindow) : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val windowed = toolWindow.type == ToolWindowType.WINDOWED
        e.presentation.text = if (windowed) "Dock Preview" else "Open Preview in Window"
        e.presentation.icon = if (windowed) AllIcons.General.Pin_tab else AllIcons.Actions.MoveToWindow
    }

    override fun actionPerformed(e: AnActionEvent) {
        val windowed = toolWindow.type == ToolWindowType.WINDOWED
        toolWindow.setType(if (windowed) ToolWindowType.DOCKED else ToolWindowType.WINDOWED, null)
        toolWindow.activate(null)
    }
}
