package ma7moud3ly.fast.compose.intellij

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.window.WindowExceptionHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Frame
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.intellij.resources.ProjectResources
import ma7moud3ly.fast.compose.intellij.externals.ProjectLibraries
import ma7moud3ly.fast.compose.intellij.sources.ProjectSources
import ma7moud3ly.fast.compose.parser.KotlinParser

/**
 * Tool window content: follows the selected Kotlin editor and previews its live, unsaved text.
 * Selecting a non-Kotlin file, or closing all editors, clears the preview.
 *
 * The preview runs only while it can be seen. When the tool window is hidden or its window is minimized, the Compose
 * panel is removed, which stops composing, parsing and drawing; editor changes are only noted and applied on return.
 *
 * Nothing is prepared for a file until it is known to have a `@Preview` annotation: the check runs in the background
 * first, and only then are its resources listed, other files looked up and the preview rendered.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val state = PreviewState(isDark = !JBColor.isBright())
    private var compose: ComposePanel = run {
        BundledSkiko.ensureLoaded()
        ImageLoading.ensureInstalled()
        createCompose()
    }

    /** Plain text shown instead of [compose] when the preview crashes again while showing a crash. */
    private var fallback: JComponent? = null

    /** True while replacing [compose] after a crash is scheduled. */
    private var recovering = false
    private val resources = project.service<ProjectResources>()
    private val sources = project.service<ProjectSources>()
    private val libraries = project.service<ProjectLibraries>()
    private var file: VirtualFile? = null
    private var document: Document? = null

    /** Runs the `@Preview` checks; cancelled on dispose. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT)
    private var detectJob: Job? = null

    /** Path of the file whose resources and other files are prepared, after it was found to have a `@Preview`. */
    private var preparedPath: String? = null

    /** True while the preview is shown and its window is not minimized. */
    private var isActive = false

    /** True when the followed file or its text changed while the preview was not active. */
    private var isStale = false

    /** True when modules or their roots changed while the preview was not active, e.g. a Gradle sync finished. */
    private var modelChanged = false

    /** Waits for module and index changes to settle before refreshing; see [projectModelChanged]. */
    private var modelJob: Job? = null

    /** The window holding the preview: the IDE frame when docked, or its own window. */
    private var window: Window? = null
    private var isMinimized = false
    private val windowListener = object : WindowAdapter() {
        override fun windowIconified(e: WindowEvent) {
            isMinimized = true
            updateActive()
        }

        override fun windowDeiconified(e: WindowEvent) {
            isMinimized = false
            updateActive()
        }
    }

    init {
        addHierarchyListener { event ->
            val flags = HierarchyEvent.SHOWING_CHANGED.toLong() or HierarchyEvent.PARENT_CHANGED.toLong()
            if ((event.changeFlags and flags) != 0L) SwingUtilities.invokeLater { updateActive() }
        }
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = follow(event.newFile)
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (event.document != document) return
                    if (!isActive) {
                        isStale = true
                        return
                    }
                    clearCrash()
                    state.text = event.document.text
                    detect(TYPING_DELAY_MS)
                }
            },
            this,
        )
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener { state.isDark = !JBColor.isBright() },
        )
        // A Gradle sync or indexing changes which modules, files and resources the preview can see.
        state.isIndexing = DumbService.isDumb(project)
        project.messageBus.connect(this).subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun enteredDumbMode() {
                    state.isIndexing = true
                }

                override fun exitDumbMode() {
                    state.isIndexing = false
                    projectModelChanged()
                }
            },
        )
        project.messageBus.connect(this).subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = projectModelChanged()
            },
        )
        // Another file the preview may use changed, or Kotlin files were added, removed or moved: rebuild the preview,
        // so classes and values declared outside composables (e.g. a data class) are read again too.
        restoreDeviceAndZoom()
        sources.onChange = { paths, structureChanged ->
            val main = file?.path
            val affected = structureChanged || paths.any { it != main && sources.isKnown(it) }
            if (affected && state.hasPreviews == true) state.rebuild()
        }
        follow(FileEditorManager.getInstance(project).selectedFiles.firstOrNull())
    }

    /** A Compose panel showing the preview; exceptions while composing, measuring or drawing go to [onCrash]. */
    private fun createCompose(): ComposePanel = ComposePanel().apply {
        exceptionHandler = WindowExceptionHandler { onCrash(it) }
        setContent { PreviewContent(state, onOpen = ::navigateTo) }
    }

    /**
     * Handles an exception Compose threw while composing, measuring or drawing a preview. The failed composition can't
     * be trusted, so the panel is replaced by a new one that shows the crash in the logs (through
     * `ComposeCode.reportCrash`) without rendering previews, until the file is edited or refreshed. If the new panel
     * crashes too, plain text replaces it.
     */
    private fun onCrash(error: Throwable) {
        thisLogger().info("Fast Compose: preview crashed", error)
        val again = state.crash != null
        state.crash = error
        if (recovering) return
        recovering = true
        SwingUtilities.invokeLater {
            recovering = false
            if (again) showFallback(error) else replaceCompose()
        }
    }

    /** Replaces [compose] (or the [fallback]) by a new Compose panel. */
    private fun replaceCompose() {
        removeContent()
        compose = createCompose()
        showContent()
    }

    private fun showFallback(error: Throwable) {
        removeContent()
        val message = "Preview crashed: ${error::class.simpleName}: ${error.message.orEmpty()}\n\n" +
            "Edit the file or press Refresh to try again."
        fallback = JBScrollPane(
            JBTextArea(message).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
            },
        )
        showContent()
    }

    /** Leaves crash mode, e.g. after an edit or refresh; previews render again, in a new panel if text was shown. */
    private fun clearCrash() {
        if (state.crash == null) return
        state.crash = null
        if (fallback != null) replaceCompose()
    }

    private fun removeContent() {
        remove(compose)
        fallback?.let(::remove)
        fallback = null
        revalidate()
        repaint()
    }

    private fun showContent() {
        if (isActive) add(fallback ?: compose, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /**
     * Follows [newFile]: a Kotlin file is previewed, read now when active or on return otherwise; any other file, or
     * no file, clears the preview so it never shows a previous file's previews.
     */
    private fun follow(newFile: VirtualFile?) {
        val newDocument = newFile?.takeIf { it.extension == "kt" }?.let { FileDocumentManager.getInstance().getDocument(it) }
        clearCrash()
        if (newFile == null || newDocument == null) {
            file = null
            document = null
            isStale = false
            detectJob?.cancel()
            unprepare()
            state.clear()
            return
        }
        file = newFile
        document = newDocument
        if (isActive) show(newFile, newDocument.text) else isStale = true
    }

    /** Shows [text] of [target], then checks it for `@Preview` before anything else is done. */
    private fun show(target: VirtualFile, text: String) {
        if (target.path != preparedPath) unprepare()
        state.show(target.path, text)
        detect(delayMs = 0)
    }

    /**
     * Checks the followed file's text for a `@Preview` annotation in the background, after [delayMs] (a pause in
     * typing). With one, the file's resources and other files are prepared and the preview is shown; without, nothing
     * is prepared and only a message is shown.
     */
    private fun detect(delayMs: Long) {
        val target = file ?: return
        val text = state.text
        detectJob?.cancel()
        detectJob = scope.launch {
            delay(delayMs)
            val hasPreviews = withContext(Dispatchers.Default) { KotlinParser.hasPreviews(text) }
            if (file != target) return@launch
            if (hasPreviews) prepare(target) else unprepare()
            state.hasPreviews = hasPreviews
        }
    }

    /** Prepares [target]'s other files and resources, once per file. */
    private fun prepare(target: VirtualFile) {
        if (target.path == preparedPath) return
        preparedPath = target.path
        state.sources = sources.provider(target)
        state.externals = libraries.resolver(target)
        resources.watch(target) { state.resources = it }
    }

    /** Drops what [prepare] set up, e.g. when the file loses its last `@Preview` or another file is selected. */
    private fun unprepare() {
        if (preparedPath == null) return
        preparedPath = null
        resources.unwatch()
        state.sources = null
        state.resources = ResourceProvider.Empty
        state.externals = null
    }

    /** Adds the Compose panel when the preview becomes visible and removes it when it is hidden or minimized. */
    private fun updateActive() {
        watchWindow(SwingUtilities.getWindowAncestor(this))
        val active = isShowing && !isMinimized
        if (active == isActive) return
        isActive = active
        resources.isActive = active
        sources.isActive = active
        if (active) {
            if (modelChanged) {
                modelChanged = false
                projectModelChanged()
            }
            if (isStale) {
                isStale = false
                val current = file
                val text = document?.text
                if (current != null && text != null) show(current, text)
            }
            add(fallback ?: compose, BorderLayout.CENTER)
        } else {
            remove(compose)
            fallback?.let(::remove)
        }
        revalidate()
        repaint()
    }

    /** Listens for minimizing [newWindow], e.g. after the preview moves between the IDE frame and its own window. */
    private fun watchWindow(newWindow: Window?) {
        if (newWindow === window) return
        window?.removeWindowListener(windowListener)
        window = newWindow
        newWindow?.addWindowListener(windowListener)
        isMinimized = newWindow is Frame && (newWindow.extendedState and Frame.ICONIFIED) != 0
    }

    /** Rebuilds the preview from scratch: the file's text, the files it uses and its resources are read again. */
    fun refresh() {
        clearCrash()
        val current = file ?: return
        if (state.hasPreviews != true) return
        sources.invalidate()
        resources.refresh()
        document?.text?.let { state.text = it }
        state.sources = sources.provider(current)
        state.externals = libraries.resolver(current)
        state.rebuild()
    }

    /**
     * Refreshes the preview after modules, source roots or indexes change, e.g. when a Gradle sync finishes, so files
     * and resources of other modules that were missing are found. Waits until changes pause and indexing ends.
     */
    private fun projectModelChanged() {
        if (!isActive) {
            modelChanged = true
            return
        }
        modelJob?.cancel()
        modelJob = scope.launch {
            delay(MODEL_CHANGE_DELAY_MS)
            if (!DumbService.isDumb(project)) refresh()
        }
    }

    /** Restores the device and zoom saved for this project, and saves them whenever they change. */
    private fun restoreDeviceAndZoom() {
        val settings = PropertiesComponent.getInstance(project)
        state.device = PreviewDevice.entries.find { it.name == settings.getValue(DEVICE_KEY) } ?: PreviewDevice.Phone
        state.zoom = settings.getValue(ZOOM_KEY)?.toFloatOrNull()
        scope.launch {
            snapshotFlow { state.device to state.zoom }.drop(1).collect { (device, zoom) ->
                settings.setValue(DEVICE_KEY, device.name, PreviewDevice.Phone.name)
                settings.setValue(ZOOM_KEY, zoom?.toString())
            }
        }
    }

    /** Opens [path] (the previewed file when null), e.g. a file the preview uses, at 1-based [line]. */
    private fun navigateTo(path: String?, line: Int) {
        val target = path?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: file ?: return
        OpenFileDescriptor(project, target, (line - 1).coerceAtLeast(0), 0).navigate(true)
    }

    override fun dispose() {
        scope.cancel()
        resources.isActive = false
        resources.unwatch()
        sources.isActive = false
        sources.onChange = null
        watchWindow(null)
        file = null
        document = null
    }

    private companion object {
        const val TYPING_DELAY_MS = 150L
        const val MODEL_CHANGE_DELAY_MS = 1_000L
        const val DEVICE_KEY = "fastCompose.preview.device"
        const val ZOOM_KEY = "fastCompose.preview.zoom"
    }
}
