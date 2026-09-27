package app.hahn.tukplus.core.network

/** Result of one API call. The API sends errors as HTTP 500 with a plain-text body. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val durationMs: Long) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {
        val message: String
    }

    /** The server answered with an error status. [text] is the body (plain text, cut to 4 KB). */
    data class HttpError(val code: Int, val text: String) : Failure {
        override val message: String get() = "HTTP $code: ${text.take(200)}"
    }

    /** No answer: no connection, time-out, or a cut connection. */
    data class NetworkError(val cause: Throwable) : Failure {
        override val message: String get() = "Network error: ${cause::class.java.simpleName}: ${cause.message}"
    }

    /** The answer could not be read. [snippet] is the start of the body. */
    data class ParseError(val cause: Throwable, val snippet: String) : Failure {
        override val message: String get() = "Parse error: ${cause.message?.lineSequence()?.firstOrNull()}"
    }
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value), durationMs)
    is ApiResult.Failure -> this
}
