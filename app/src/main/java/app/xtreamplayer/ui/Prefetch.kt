package app.xtreamplayer.ui

import android.content.Context
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Baja de antemano las imágenes (a disco) para que al llegar a ellas ya estén listas.
 * No usa memoria RAM; Coil omite las que ya están guardadas.
 */
fun prefetchImages(context: Context, urls: List<String?>) {
    val loader = context.imageLoader
    urls.asSequence()
        .filter { !it.isNullOrBlank() }
        .distinct()
        .forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
}
