package app.hahn.tukplus.core.network

import app.hahn.tukplus.core.logging.LogLevel
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/** Adds `Authorization: Bearer helloworld|<uuid>` and the User-Agent (api-reference §2). */
class AuthInterceptor(
    private val deviceUuid: () -> String,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer helloworld|${deviceUuid()}")
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Tries a GET again when there is no answer or the gateway fails (502, 503, 504).
 * It does not try again on 500: the API uses 500 for normal errors (for example
 * "sql: no rows in result set"), so a second try gives the same answer.
 * Other methods are never tried again, so an order is never sent two times.
 */
class RetryInterceptor(
    private val delaysMs: List<Long> = listOf(1_000, 3_000),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.code !in RETRY_CODES || attempt >= delaysMs.size) {
                    return response.newBuilder().header(RETRY_HEADER, attempt.toString()).build()
                }
                response.close()
            } catch (e: IOException) {
                if (attempt >= delaysMs.size || chain.call().isCanceled()) throw e
            }
            sleep(delaysMs[attempt])
            attempt++
        }
    }

    companion object {
        val RETRY_CODES = setOf(502, 503, 504)
        /** Response header (local only) with the number of retries. */
        const val RETRY_HEADER = "X-TukPlus-Retries"
    }
}

/**
 * Writes one log event per request (PLAN.md §9.2): method, redacted path, status,
 * time, size and retries. On errors it adds the start of the body. For calls that
 * change data (not GET) it adds the full redacted request and response bodies.
 */
class HttpLogInterceptor(
    private val log: TukLog,
    private val redactor: Redactor,
    private val baseUrl: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // The "ts" parameter is only a cache buster. Leave it out of the log.
        val url = request.url.newBuilder().removeAllQueryParameters("ts").build().toString()
        val path = redactor.redactUrl(if (url.startsWith(baseUrl)) url.removePrefix(baseUrl) else url)
        val isWrite = request.method != "GET"
        val requestBody = if (isWrite) request.body?.let { body ->
            Buffer().also { body.writeTo(it) }.readUtf8()
        } else null
        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log.log(
                LogLevel.WARN, "net", "http_fail",
                buildMap {
                    put("method", request.method)
                    put("path", path)
                    put("ms", (System.nanoTime() - start) / 1_000_000)
                    put("error", "${e::class.java.simpleName}: ${e.message}")
                    if (requestBody != null) put("req", redactor.redactBody(requestBody, MAX_WRITE_BODY))
                },
            )
            throw e
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        val failed = !response.isSuccessful
        val fields = buildMap<String, Any?> {
            put("method", request.method)
            put("path", path)
            put("status", response.code)
            put("ms", ms)
            response.header(RetryInterceptor.RETRY_HEADER)?.toIntOrNull()?.takeIf { it > 0 }?.let { put("retries", it) }
            response.body.contentLength().takeIf { it >= 0 }?.let { put("bytes", it) }
            if (isWrite) {
                put("req", requestBody?.let { redactor.redactBody(it, MAX_WRITE_BODY) })
                put("resp", redactor.redactBody(response.peekBody(MAX_WRITE_BODY.toLong()).string(), MAX_WRITE_BODY))
            } else if (failed) {
                put("resp", redactor.redactBody(response.peekBody(ERROR_BODY.toLong()).string(), ERROR_BODY))
            }
        }
        log.log(if (failed) LogLevel.WARN else LogLevel.INFO, "net", "http", fields)
        return response
    }

    private companion object {
        const val ERROR_BODY = 4 * 1024
        const val MAX_WRITE_BODY = 256 * 1024
    }
}
