package ma7moud3ly.fast.compose.intellij

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import com.intellij.openapi.application.PathManager
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Sets up Coil's singleton `ImageLoader`, used by `AsyncImage` in previews: network images through OkHttp directly,
 * and a disk cache in the IDE's system folder.
 *
 * Coil's service loader is off, so it never picks the Ktor fetcher: Ktor is built against a newer kotlinx-coroutines
 * than the IDE's, which the plugin must use, and `ServiceLoader` through the plugin's class loader also finds the IDE's
 * own Ktor engines.
 */
internal object ImageLoading {

    private val installed by lazy {
        val client = OkHttpClient()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .serviceLoaderEnabled(false)
                .components { add(OkHttpNetworkFetcherFactory(client)) }
                .diskCache {
                    DiskCache.Builder()
                        .directory(PathManager.getSystemDir().resolve("fast-compose/images").toOkioPath())
                        .maxSizeBytes(DISK_CACHE_BYTES)
                        .build()
                }
                .build()
        }
    }

    /** Installs the image loader once; call before showing any preview. */
    fun ensureInstalled() {
        installed
    }

    private const val DISK_CACHE_BYTES = 100L * 1024 * 1024
}
