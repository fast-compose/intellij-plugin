package ma7moud3ly.fast.compose.intellij.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import ma7moud3ly.fast.compose.bindings.resources.ChainResources
import ma7moud3ly.fast.compose.bindings.resources.ResourceProvider
import ma7moud3ly.fast.compose.bindings.resources.StorageResources
import ma7moud3ly.fast.compose.project.GradleLayout

/**
 * Resources for the previewed file of a project: one [StorageResources] per resource folder of the file's module and
 * its dependencies, chained most specific first. Folders are kept while the previewed file moves within the same
 * modules, so cached files are reused.
 *
 * Changed files (on disk or unsaved in an editor) refresh only their folder, after a short pause. While the preview is
 * not [isActive], changes are only noted and applied when it becomes active again.
 */
@Service(Service.Level.PROJECT)
class ProjectResources(private val project: Project, scope: CoroutineScope) : Disposable {

    private val uiScope = scope + Dispatchers.EDT

    /** Providers of the current file's resource folders by folder path, most specific first. */
    private var folders = LinkedHashMap<String, StorageResources>()
    private var file: VirtualFile? = null
    private var onChange: ((ResourceProvider) -> Unit)? = null
    private var resolveJob: Job? = null

    /** Folders with changed files, waiting for a refresh. */
    private val changedFolders = LinkedHashSet<String>()
    private var refreshJob: Job? = null

    /** True while a preview is visible; refreshes wait otherwise. */
    var isActive: Boolean = false
        set(value) {
            field = value
            if (value && changedFolders.isNotEmpty()) scheduleRefresh()
        }

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val paths = events.map { it.path }
                    // Files or folders were added, removed, moved or renamed, not only edited.
                    val structureChanged = events.any { it !is VFileContentChangeEvent }
                    uiScope.launch { onFilesChanged(paths, structureChanged) }
                }
            },
        )
        connection.subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = resolve()
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    onFilesChanged(listOf(changed.path), structureChanged = false)
                }
            },
            this,
        )
    }

    /** Provides resources for [newFile] to [listener], now and whenever its resource folders change. */
    fun watch(newFile: VirtualFile, listener: (ResourceProvider) -> Unit) {
        file = newFile
        onChange = listener
        resolve()
    }

    /** Finds the current file's resource folders again and lists every folder again. */
    fun refresh() {
        changedFolders += folders.keys
        scheduleRefresh()
        resolve()
    }

    /** Stops providing resources, e.g. when the preview closes. */
    fun unwatch() {
        resolveJob?.cancel()
        file = null
        onChange = null
    }

    /** Finds the current file's resource folders in the background and sends a provider chaining them. */
    private fun resolve() {
        val target = file ?: return
        resolveJob?.cancel()
        resolveJob = uiScope.launch {
            val roots = withContext(Dispatchers.Default) {
                readAction { if (target.isValid) ResourceFolders.find(project, target) else emptyList() }
            }
            val previous = folders
            folders = LinkedHashMap<String, StorageResources>().apply {
                roots.forEach { root ->
                    put(root.path, previous[root.path] ?: StorageResources(VfsResourceStorage(root), uiScope, CACHE_BYTES))
                }
            }
            onChange?.invoke(if (folders.isEmpty()) ResourceProvider.Empty else ChainResources(folders.values.toList()))
        }
    }

    private fun onFilesChanged(paths: List<String>, structureChanged: Boolean) {
        if (file == null) return
        // A resource folder itself was added or removed: the chain changes.
        if (structureChanged && paths.any { it.substringAfterLast('/') in GradleLayout.RESOURCE_FOLDERS }) resolve()
        val hit = folders.keys.filter { folder -> paths.any { it.startsWith("$folder/") } }
        if (hit.isEmpty()) return
        changedFolders += hit
        if (isActive) scheduleRefresh()
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = uiScope.launch {
            delay(REFRESH_DELAY_MS)
            val paths = changedFolders.toList()
            changedFolders.clear()
            paths.forEach { folders[it]?.refresh() }
        }
    }

    override fun dispose() {
        unwatch()
        folders.clear()
    }

    private companion object {
        const val CACHE_BYTES = 16L * 1024 * 1024
        const val REFRESH_DELAY_MS = 200L
    }
}
