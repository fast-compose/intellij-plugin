package ma7moud3ly.fast.compose.intellij.externals

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import ma7moud3ly.fast.compose.interpreter.ExternalResolver
import ma7moud3ly.fast.compose.interpreter.jvm.GradleJars
import ma7moud3ly.fast.compose.interpreter.jvm.JarResolver
import ma7moud3ly.fast.compose.project.GradleLayout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Real library code for previews: the engine's [JarResolver] per module over its library jars, as Gradle resolved them.
 * Jars come from Gradle's caches, which never change, so the class loader holding them open doesn't block builds;
 * libraries built inside the project (`build/` folders) are left out. Loaders are dropped and closed when modules or
 * their dependencies change, and when the project closes.
 */
@Service(Service.Level.PROJECT)
class ProjectLibraries(private val project: Project) : Disposable {

    private val entries = ConcurrentHashMap<Module, JarResolver>()

    init {
        project.messageBus.connect(this).subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) = clear()
            },
        )
    }

    /** The resolver for [file]'s module and its library dependencies; null when the file has no module. */
    fun resolver(file: VirtualFile): ExternalResolver? {
        val application = ApplicationManager.getApplication()
        val module = application.runReadAction(
            Computable { ProjectFileIndex.getInstance(project).getModuleForFile(file) },
        ) ?: return null
        entries[module]?.let { return it }
        // Without library jars, the resolver still serves the JDK, Kotlin and coroutines, e.g. `java.io.File`.
        val jars = application.runReadAction(Computable { libraryJars(module) })
        val resolver = JarResolver(jars, host = javaClass.classLoader)
        return entries.putIfAbsent(module, resolver)?.also { resolver.close() } ?: resolver
    }

    /**
     * Library jars visible from [module], in classpath order, with JVM classes ([GradleJars.jvmJars]). KMP common
     * source sets see only metadata artifacts; the JVM jars also come from the JVM source set of the same Gradle
     * module.
     */
    private fun libraryJars(module: Module): List<File> {
        val jvm = jvmSourceSet(module)?.let(::jarsOf).orEmpty()
        return (GradleJars.jvmJars(jarsOf(module)) + GradleJars.jvmJars(jvm)).distinct()
    }

    private fun jarsOf(module: Module): List<File> {
        val roots = OrderEnumerator.orderEntries(module).recursively().withoutSdk().librariesOnly().classes().roots
        return roots.mapNotNull { root -> PathUtil.getLocalPath(root)?.let(::File) }.distinct()
    }

    /** The JVM source set module (`jvmMain`, `desktopMain`) next to a KMP source set module such as `shared.commonMain`. */
    private fun jvmSourceSet(module: Module): Module? {
        val sourceSet = module.name.substringAfterLast('.')
        if (sourceSet in GradleLayout.JVM_SOURCE_SETS) return null
        val prefix = module.name.substringBeforeLast('.', "").ifEmpty { return null }
        val modules = ModuleManager.getInstance(project)
        return GradleLayout.JVM_SOURCE_SETS.firstNotNullOfOrNull { modules.findModuleByName("$prefix.$it") }
    }

    private fun clear() {
        val old = entries.values.toList()
        entries.clear()
        old.forEach { runCatching { it.close() } }
    }

    override fun dispose() = clear()
}
