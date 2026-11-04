package ma7moud3ly.fast.compose.intellij.sources

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import ma7moud3ly.fast.compose.interpreter.SourceFile
import ma7moud3ly.fast.compose.interpreter.SourceProvider
import ma7moud3ly.fast.compose.intellij.VfsFiles
import ma7moud3ly.fast.compose.project.GradleLayout
import ma7moud3ly.fast.compose.project.KotlinSources

/**
 * Project Kotlin files for multi-file previews: [provider] finds the file that declares a top-level name in a package
 * and tells project packages from library ones, and [onChange] reports edits to files previews may use.
 *
 * Lookups read a package's folder under the source roots of the previewed file's module and the modules it depends on
 * (e.g. `androidMain` → `commonMain` → library modules), in that order, so they work while the IDE is indexing. The
 * engine's [KotlinSources] does the lookups over the IDE's file tree ([VfsFiles]), with unsaved editor text, and
 * replaces `expect` declarations by their `actual`.
 */
@Service(Service.Level.PROJECT)
class ProjectSources(private val project: Project, scope: CoroutineScope) : Disposable {

    private val uiScope = scope + Dispatchers.EDT

    /** Parsed project files and the lookups over them, shared with other hosts (see [KotlinSources]). */
    private val sources = KotlinSources(VfsFiles)

    /** Changes when modules or source roots change, so providers recompute their module scope. */
    private val rootsVersion = AtomicLong()

    /** Answers of [isProjectPackage] by package; cleared when source roots or folders change. */
    private val projectPackages = ConcurrentHashMap<String, Boolean>()

    /** Called on the UI thread with changed Kotlin file paths, after a short pause, while [isActive]. */
    var onChange: ((paths: Set<String>, structureChanged: Boolean) -> Unit)? = null

    /** True while a preview is visible; changes are ignored otherwise, since previews reload when shown again. */
    var isActive: Boolean = false

    private val changedPaths = LinkedHashSet<String>()
    private var structureChanged = false
    private var changeJob: Job? = null

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // A folder may have been added, removed or moved: package answers may change.
                    if (events.any { it !is VFileContentChangeEvent }) projectPackages.clear()
                    val kotlin = events.filter { it.path.endsWith(".kt") }
                    if (kotlin.isEmpty()) return
                    val paths = kotlin.map { it.path }
                    val structure = kotlin.any { it !is VFileContentChangeEvent }
                    if (structure) paths.forEach(sources::forget)
                    uiScope.launch { onFilesChanged(paths, structure) }
                }
            },
        )
        connection.subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    rootsVersion.incrementAndGet()
                    projectPackages.clear()
                }
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (file.extension == "kt") onFilesChanged(listOf(file.path), structure = false)
                }
            },
            this,
        )
    }

    /** Drops every cached file and module list, so the next lookups read the project again. */
    fun invalidate() {
        sources.clear()
        projectPackages.clear()
        rootsVersion.incrementAndGet()
    }

    /** True when [path] is a file previews have looked into, so its changes may affect them. */
    fun isKnown(path: String): Boolean = sources.isKnown(path)

    /** Finds declarations for a preview of [file] in its module and the modules it depends on. */
    fun provider(file: VirtualFile): SourceProvider = FileSources(file)

    private inner class FileSources(private val from: VirtualFile) : SourceProvider {

        /** Source roots of the modules visible from [from], its own first, with the [rootsVersion] they were computed for. */
        @Volatile
        private var roots: Pair<Long, List<String>>? = null

        /** Source roots of the libraries visible to [from], with the [rootsVersion] they were computed for. */
        @Volatile
        private var libraryRoots: Pair<Long, List<String>>? = null

        override suspend fun find(packageName: String, name: String): SourceFile? =
            lookup("lookup of $packageName.$name", null) { sources.find(packageName, name, visibleRoots(), from.path) }

        /**
         * True when [packageName] has a folder under a source root of any project module (generated sources under
         * `build/` don't count), or next to [from] without a project model. Other packages are libraries, which the
         * engine never looks up or reports. Answers are cached until source roots or folders change.
         */
        override suspend fun isProjectPackage(packageName: String): Boolean {
            if (packageName.isEmpty()) return true
            projectPackages[packageName]?.let { return it }
            val isProject = lookup("package check of $packageName", true) {
                val moduleRoots = ModuleManager.getInstance(project).modules.flatMap { module ->
                    ModuleRootManager.getInstance(module).sourceRoots.map { it.path }.filter(GradleLayout::isPreviewRoot)
                }
                sources.hasPackage(packageName, moduleRoots) ||
                    sources.hasPackage(packageName, listOfNotNull(sources.sourceRootOf(from.path)))
            }
            projectPackages[packageName] = isProject
            return isProject
        }

        override suspend fun findExpect(packageName: String, name: String): SourceFile? =
            lookup("lookup of expect $packageName.$name", null) {
                sources.findExpect(packageName, name, visibleRoots(), from.path)
            }

        /**
         * The file declaring [name] in library package [packageName], from the sources jars of the libraries visible
         * to [from] (downloaded by the IDE); see [KotlinSources.findLibrary].
         */
        override suspend fun findLibrary(packageName: String, name: String): SourceFile? =
            lookup("lookup of library $packageName.$name", null) {
                sources.findLibrary(packageName, name, librarySourceRoots())
            }

        /** Runs [block] in a read action off the UI thread; a failure is logged as [what] and gives [fallback]. */
        private suspend fun <T> lookup(what: String, fallback: T, block: () -> T): T = withContext(Dispatchers.Default) {
            try {
                readAction { if (from.isValid) block() else fallback }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("Fast Compose: $what failed", e)
                fallback
            }
        }

        private fun librarySourceRoots(): List<String> {
            val version = rootsVersion.get()
            libraryRoots?.takeIf { it.first == version }?.let { return it.second }
            val own = ProjectFileIndex.getInstance(project).getModuleForFile(from)
            val list = own?.let { module ->
                OrderEnumerator.orderEntries(module).recursively().withoutSdk().librariesOnly().sources().roots.map { it.path }
            }.orEmpty()
            libraryRoots = version to list
            return list
        }

        /**
         * Source roots previews read ([GradleLayout.isPreviewRoot]) of [from]'s module and the modules it depends on,
         * in order; empty without a project model, so lookups search next to [from].
         */
        private fun visibleRoots(): List<String> {
            val version = rootsVersion.get()
            roots?.takeIf { it.first == version }?.let { return it.second }
            val own = ProjectFileIndex.getInstance(project).getModuleForFile(from)
            val modules = buildList {
                if (own != null) {
                    OrderEnumerator.orderEntries(own).recursively().withoutSdk().withoutLibraries().forEachModule {
                        add(it)
                        true
                    }
                }
            }.let { if (own != null && own !in it) listOf(own) + it else it }.distinct()
            val list = modules.flatMap { module ->
                ModuleRootManager.getInstance(module).sourceRoots.map { it.path }.filter(GradleLayout::isPreviewRoot)
            }.distinct()
            roots = version to list
            return list
        }
    }

    private fun onFilesChanged(paths: List<String>, structure: Boolean) {
        if (!isActive || onChange == null) return
        changedPaths += paths
        structureChanged = structureChanged || structure
        changeJob?.cancel()
        changeJob = uiScope.launch {
            delay(CHANGE_DELAY_MS)
            val changed = changedPaths.toSet()
            val structural = structureChanged
            changedPaths.clear()
            structureChanged = false
            onChange?.invoke(changed, structural)
        }
    }

    override fun dispose() {
        onChange = null
        sources.clear()
    }

    private companion object {
        const val CHANGE_DELAY_MS = 300L
    }
}
