package ma7moud3ly.fast.compose.intellij.resources

import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma7moud3ly.fast.compose.bindings.resources.ResourceStorage
import ma7moud3ly.fast.compose.bindings.resources.StoredFile
import ma7moud3ly.fast.compose.project.GradleLayout

/**
 * A `res/` or `composeResources/` folder read through the IDE's virtual file system, so listing uses the IDE's
 * in-memory file tree instead of the disk. Open files with unsaved edits, such as `strings.xml`, are read from the
 * editor. Only folders previews use are listed, one level deep; hidden files and files over 20 MB are skipped.
 */
internal class VfsResourceStorage(private val root: VirtualFile) : ResourceStorage {

    override suspend fun list(): List<StoredFile> = withContext(Dispatchers.Default) {
        readAction {
            if (!root.isValid) return@readAction emptyList()
            root.children.filter { it.isDirectory && GradleLayout.isPreviewResourceFolder(it.name) }.flatMap { folder ->
                folder.children
                    .filter { !it.isDirectory && !it.name.startsWith(".") && it.length <= MAX_FILE_BYTES }
                    .map { StoredFile("${folder.name}/${it.name}", version(it)) }
            }
        }
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        readAction {
            val file = root.findFileByRelativePath(path)?.takeIf { it.isValid && !it.isDirectory }
                ?: return@readAction null
            unsavedText(file)?.encodeToByteArray() ?: file.contentsToByteArray()
        }
    }

    /** Changes when the file is saved or, for an open file, on every unsaved edit. */
    private fun version(file: VirtualFile): Long {
        val documents = FileDocumentManager.getInstance()
        val document = documents.getCachedDocument(file)
        return if (document != null && documents.isDocumentUnsaved(document)) document.modificationStamp
        else file.modificationStamp
    }

    private fun unsavedText(file: VirtualFile): String? {
        val documents = FileDocumentManager.getInstance()
        val document = documents.getCachedDocument(file) ?: return null
        return if (documents.isDocumentUnsaved(document)) document.text else null
    }

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
    }
}
