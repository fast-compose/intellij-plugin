package ma7moud3ly.fast.compose.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowType

/**
 * Moves the docked preview to a separate OS window that can be dragged to another display. Hidden in that window,
 * which has the IDE's own button to dock it again. The IDE remembers the mode and the window's bounds.
 */
class ToggleWindowModeAction(private val toolWindow: ToolWindow) : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.text = "Open Preview in Window"
        e.presentation.icon = AllIcons.Actions.MoveToWindow
        e.presentation.isVisible = toolWindow.type != ToolWindowType.WINDOWED
    }

    override fun actionPerformed(e: AnActionEvent) {
        toolWindow.setType(ToolWindowType.WINDOWED, null)
        toolWindow.activate(null)
    }
}
