package ma7moud3ly.fast.compose.intellij

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAwareAction
import java.net.URLEncoder
import java.util.Properties

/**
 * "Report Issue" menu: opens a new GitHub issue, prefilled with a template and the plugin, engine, IDE and OS
 * versions. No code or file contents are sent; the reporter adds what they choose.
 */
class ReportIssueGroup : DefaultActionGroup("Report Issue", true) {
    init {
        templatePresentation.icon = AllIcons.Actions.Report
        add(
            ReportIssueAction(
                "Preview Problem…",
                "Wrong rendering, unsupported Compose API, parse or runtime errors (Fast Compose engine)",
                repository = "fast-compose",
            ),
        )
        add(
            ReportIssueAction(
                "Plugin Problem…",
                "Tool window, resources, other files, IDE errors (Fast Compose Preview plugin)",
                repository = "intellij-plugin",
            ),
        )
    }
}

private class ReportIssueAction(text: String, description: String, private val repository: String) :
    DumbAwareAction(text, description, null) {

    override fun actionPerformed(e: AnActionEvent) {
        val url = "https://github.com/fast-compose/$repository/issues/new?body=${encode(body())}"
        BrowserUtil.browse(url)
    }

    private fun body(): String {
        val app = ApplicationInfo.getInstance()
        return """
            |**What happened**
            |
            |
            |**Expected**
            |
            |
            |**Code to reproduce**
            |```kotlin
            |
            |```
            |
            |**Environment**
            |- Fast Compose Preview: ${versions.getProperty("pluginVersion") ?: "unknown"}
            |- Fast Compose engine: ${versions.getProperty("engineVersion") ?: "unknown"}
            |- IDE: ${app.fullApplicationName} (${app.build.asString()})
            |- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})
            |- Java: ${System.getProperty("java.runtime.version")}
        """.trimMargin()
    }

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

    private companion object {
        /** Plugin and engine versions, written into `fast-compose-versions.properties` by the build. */
        val versions: Properties by lazy {
            Properties().apply {
                ReportIssueAction::class.java.getResourceAsStream("/fast-compose-versions.properties")?.use { load(it) }
            }
        }
    }
}
