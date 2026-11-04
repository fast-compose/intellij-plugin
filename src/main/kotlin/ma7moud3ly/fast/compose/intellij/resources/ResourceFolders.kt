package ma7moud3ly.fast.compose.intellij.resources

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import ma7moud3ly.fast.compose.intellij.VfsFiles
import ma7moud3ly.fast.compose.project.GradleLayout

/**
 * Finds the resource folders a Kotlin file can use, by the engine's Gradle layout rules ([GradleLayout.resourceFolders]),
 * with the source sets of the modules it depends on from the IDE's project model.
 */
internal object ResourceFolders {

    /**
     * Resource folders for [file], most specific first: its own source set, then the modules it depends on in
     * classpath order (e.g. `androidMain`, `commonMain`, then library modules), then its module's shared source sets.
     * Call in a read action.
     */
    fun find(project: Project, file: VirtualFile): List<VirtualFile> {
        val sourceSets = LinkedHashSet<String>()
        ModuleUtilCore.findModuleForFile(file, project)?.let { module ->
            OrderEnumerator.orderEntries(module).recursively().withoutSdk().withoutLibraries().forEachModule { dependency ->
                val roots = ModuleRootManager.getInstance(dependency)
                roots.sourceRoots
                    .map { it.path }
                    .filter { "/build/" !in it }
                    .mapNotNullTo(sourceSets, GradleLayout::sourceSetOf)
                // A module with only resources, e.g. `common/resources/src/main/res`, has no source roots; its content
                // root is the source set (`src/main`) or the module folder (`common/resources`).
                for (root in roots.contentRoots) {
                    if ("/build/" in root.path) continue
                    GradleLayout.sourceSetOf(root.path)?.let(sourceSets::add)
                    root.findChild("src")?.let { src -> sourceSets += GradleLayout.sharedSourceSets(VfsFiles, src.path) }
                }
                true
            }
        }
        return GradleLayout.resourceFolders(VfsFiles, file.path, sourceSets.toList()).mapNotNull(VfsFiles::find)
    }
}
