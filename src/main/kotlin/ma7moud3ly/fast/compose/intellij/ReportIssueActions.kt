package ma7moud3ly.fast.compose.intellij

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbAwareAction
import java.net.URLEncoder
import java.util.Properties

/** Where an issue is reported: the Fast Compose engine or this plugin. */
enum class ReportIssueTarget(val title: String, val description: String, val repository: String) {
    Preview(
        "Preview Problem…",
        "Wrong rendering, unsupported Compose API, parse or runtime errors (Fast Compose engine)",
        "fast-compose",
    ),
    Plugin(
        "Plugin Problem…",
        "Tool window, resources, other files, IDE errors (Fast Compose Preview plugin)",
        "intellij-plugin",
    ),
}

/**
 * "Report Issue" menu: opens a new GitHub issue, prefilled with a template and the plugin, engine, IDE and OS
 * versions. No code or file contents are sent; the reporter adds what they choose.
 */
class ReportIssueGroup : DefaultActionGroup("Report Issue", true) {
    init {
        templatePresentation.icon = AllIcons.Actions.Report
        ReportIssueTarget.entries.forEach { add(ReportIssueAction(it)) }
    }
}

private class ReportIssueAction(private val target: ReportIssueTarget) :
    DumbAwareAction(target.title, target.description, null) {

    override fun actionPerformed(e: AnActionEvent) = reportIssue(target)
}

/** Opens a new GitHub issue in [target]'s repository, prefilled with a template and the versions. */
fun reportIssue(target: ReportIssueTarget) {
    BrowserUtil.browse("https://github.com/fast-compose/${target.repository}/issues/new?body=${encode(issueBody())}")
}

private fun issueBody(): String {
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

/** Plugin and engine versions, written into `fast-compose-versions.properties` by the build. */
private val versions: Properties by lazy {
    Properties().apply {
        ReportIssueTarget::class.java.getResourceAsStream("/fast-compose-versions.properties")?.use { load(it) }
    }
}
