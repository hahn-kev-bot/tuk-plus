package app.hahn.tukplus.platform

import android.content.Context
import app.hahn.tukplus.core.logging.TukLog
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Loads shop and menu pictures. They come from S3 with a 30-day cache header, so a
 * large disk cache keeps them (api-reference §11).
 */
object Images {
    fun loader(context: Context, log: TukLog): ImageLoader {
        val client = OkHttpClient.Builder().addInterceptor(WebpInterceptor(log)).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
}

/**
 * Asks S3 for the WebP version of a picture (same name, ".webp"), which is 20–60 % smaller.
 * If it does not exist, it gets the original. Like the web app's `v-webp` directive.
 */
class WebpInterceptor(private val log: TukLog) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val path = url.encodedPath
        val isTukS3 = url.host.startsWith("tukapp.s3")
        val ext = path.substringAfterLast('.', "").lowercase()
        if (!isTukS3 || ext !in setOf("png", "jpg", "jpeg")) return chain.proceed(request)
        val webpUrl = url.newBuilder().encodedPath(path.substringBeforeLast('.') + ".webp").build()
        val webp = chain.proceed(request.newBuilder().url(webpUrl).build())
        if (webp.isSuccessful) return webp
        webp.close()
        log.d("image", "webp_missing", "code" to webp.code)
        return chain.proceed(request)
    }
}
