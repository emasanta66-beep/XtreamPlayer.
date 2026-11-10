package app.xtreamplayer

import android.app.Application
import app.xtreamplayer.data.Http
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Dispatcher

/**
 * Cargador de imágenes global. Los servidores IPTV casi nunca mandan cabeceras de caché,
 * así que sin esto los pósters se volvían a bajar cada vez; aquí se guardan en disco y en memoria.
 */
class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            // Cliente propio para imágenes: por defecto OkHttp baja solo 5 a la vez por servidor, y los carteles
            // casi siempre vienen del mismo. Con más conexiones en paralelo las filas se llenan mucho más rápido.
            .okHttpClient {
                Http.client.newBuilder()
                    .dispatcher(Dispatcher().apply {
                        maxRequests = 64
                        maxRequestsPerHost = 16
                    })
                    .build()
            }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("img_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(120)
            .build()
}
