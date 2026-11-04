package ma7moud3ly.fast.compose.intellij

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import ma7moud3ly.fast.compose.project.FileEntry
import ma7moud3ly.fast.compose.project.ProjectFiles
import java.io.IOException

/**
 * The engine's [ProjectFiles] over the IDE's virtual file system, so lookups use its in-memory file tree, also inside
 * sources jars (`…-sources.jar!/…`). Open files with unsaved edits are read from the editor. Call in a read action.
 */
internal object VfsFiles : ProjectFiles {

    fun find(path: String): VirtualFile? =
        if ("!/" in path) JarFileSystem.getInstance().findFileByPath(path)
        else LocalFileSystem.getInstance().findFileByPath(path)

    override fun list(path: String): List<FileEntry> {
        val folder = find(path)?.takeIf { it.isValid && it.isDirectory } ?: return emptyList()
        return folder.children.map { FileEntry(it.name, it.isDirectory, if (it.isDirectory) 0L else it.length) }
    }

    override fun isFolder(path: String): Boolean = find(path)?.let { it.isValid && it.isDirectory } == true

    override fun read(path: String): String? {
        val file = find(path)?.takeIf { it.isValid && !it.isDirectory } ?: return null
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.text }
        return try {
            VfsUtilCore.loadText(file)
        } catch (_: IOException) {
            null
        }
    }

    /** Changes when the file is saved or, for an open file, on every unsaved edit. */
    override fun version(path: String): Long {
        val file = find(path) ?: return -1L
        val documents = FileDocumentManager.getInstance()
        val document = documents.getCachedDocument(file)
        return if (document != null && documents.isDocumentUnsaved(document)) document.modificationStamp
        else file.modificationStamp
    }
}
