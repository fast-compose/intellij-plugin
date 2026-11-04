package ma7moud3ly.fast.compose.intellij

import org.jetbrains.skiko.Library

/**
 * Loads the plugin's own Skiko native library. The IDE sets `skiko.library.path` to its own Skiko build, which is a
 * different version, so the property is cleared while the plugin's Skiko extracts and loads its native library from
 * its jar (into `~/.skiko`), then restored for the IDE.
 */
internal object BundledSkiko {

    private const val LIBRARY_PATH = "skiko.library.path"

    private val loaded by lazy {
        synchronized(System.getProperties()) {
            val idePath = System.getProperty(LIBRARY_PATH)
            System.clearProperty(LIBRARY_PATH)
            try {
                Library.load()
            } finally {
                if (idePath != null) System.setProperty(LIBRARY_PATH, idePath)
            }
        }
    }

    /** Loads the native library once; call before creating any Compose UI. */
    fun ensureLoaded() {
        loaded
    }
}
