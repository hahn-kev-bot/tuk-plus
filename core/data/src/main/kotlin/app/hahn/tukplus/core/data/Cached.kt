package app.hahn.tukplus.core.data

import java.time.Duration
import java.time.Instant

/**
 * Data from the cache or the network, with its age (PLAN.md §5.2).
 *
 * The UI shows [data] when it is not null, also while [status] is [Status.Refreshing]
 * or [Status.Error]. So a screen is never empty when old data exists.
 */
data class Cached<out T>(
    val data: T?,
    /** When the server gave us [data]. Null when there is no data yet. */
    val fetchedAt: Instant?,
    val status: Status,
) {
    sealed interface Status {
        /** No data yet, and no request running. */
        data object Empty : Status
        /** The data is younger than its refresh age. */
        data object Fresh : Status
        /** The data is older than its refresh age. A refresh will start. */
        data object Stale : Status
        /** A request is running. */
        data object Refreshing : Status
        /** The last request failed. [offline] is true when there was no answer at all. */
        data class Error(val message: String, val offline: Boolean) : Status
    }

    fun age(now: Instant): Duration? = fetchedAt?.let { Duration.between(it, now) }

    fun <R> map(transform: (T) -> R): Cached<R> = Cached(data?.let(transform), fetchedAt, status)

    companion object {
        fun <T> empty(): Cached<T> = Cached(null, null, Status.Empty)

        /**
         * One status for a screen that shows many resources:
         * - the age is the age of the oldest part that has data;
         * - Refreshing if any part is refreshing, else Error if any part failed,
         *   else Stale if any part is stale, else Fresh (or Empty when no part has data).
         */
        fun combineStatus(parts: List<Cached<*>>): Cached<Unit> {
            val withData = parts.filter { it.data != null }
            val oldest = withData.mapNotNull { it.fetchedAt }.minOrNull()
            val statuses = parts.map { it.status }
            val status = when {
                statuses.any { it is Status.Refreshing } -> Status.Refreshing
                statuses.any { it is Status.Error } -> statuses.filterIsInstance<Status.Error>().let { errors ->
                    Status.Error(errors.first().message, offline = errors.all { it.offline })
                }
                withData.isEmpty() -> Status.Empty
                statuses.any { it is Status.Stale } -> Status.Stale
                else -> Status.Fresh
            }
            return Cached(if (withData.isEmpty()) null else Unit, oldest, status)
        }
    }
}
