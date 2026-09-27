package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Business
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * One opening range of a day, in minutes from midnight.
 *
 * An end of 0 in the text becomes 24 * 60. When [endMinute] is less than
 * [startMinute], the range goes past midnight (for example "1800-0200").
 */
data class TimeRange(val startMinute: Int, val endMinute: Int) {
    /** True when the range goes past midnight into the next day. */
    val isOvernight: Boolean get() = endMinute < startMinute

    /** True when [minute] of this day is in the range. The part after midnight is not included. */
    fun containsToday(minute: Int): Boolean =
        if (isOvernight) minute >= startMinute else minute in startMinute until endMinute

    /** Text such as "09:30–14:00". */
    fun format(): String = "${formatMinute(startMinute)}–${formatMinute(endMinute)}"

    internal companion object {
        fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    }
}

/** The opening hours of one day. */
sealed interface DayHours {
    /** The shop does not open on this day. */
    data object Closed : DayHours

    /** The shop is open in these ranges. */
    data class Ranges(val ranges: List<TimeRange>) : DayHours

    /** We cannot read the text. [text] is the original text. */
    data class Unreadable(val text: String) : DayHours
}

/** Whether a shop takes orders now, and why not. */
sealed interface OpenState {
    val isOpen: Boolean

    /** The shop is open. [closesAt] is null when the shop is always open or the time is not known. */
    data class Open(val closesAt: LocalTime?) : OpenState {
        override val isOpen: Boolean get() = true
    }

    /** The shop is closed now. [opensAt] is the next opening time later today, if there is one. */
    data class ClosedNow(val opensAt: LocalTime?) : OpenState {
        override val isOpen: Boolean get() = false
    }

    /** The shop does not open today. */
    data object ClosedToday : OpenState {
        override val isOpen: Boolean get() = false
    }

    /** The shop is on holiday until [until]. */
    data class OnHoliday(val until: Instant) : OpenState {
        override val isOpen: Boolean get() = false
    }

    /** The shop owner stopped orders for a time. */
    data object Paused : OpenState {
        override val isOpen: Boolean get() = false
    }

    /** The shop has no Commerce workflow. It cannot take orders. */
    data object NotOrderable : OpenState {
        override val isOpen: Boolean get() = false
    }
}

/**
 * Opening hours rules of the web app (api-reference §5.2).
 *
 * All times are read in [ChiangMaiTime.ZONE]. A `now` in another zone is changed to that zone first.
 */
object OpenHours {
    private const val MINUTES_PER_DAY = 24 * 60
    private val PART = Regex("""(\d{1,4})-(\d{1,4})""")
    private val HOLIDAY_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** Day key of the `hours` map: "mon", "tue", …, "sun". */
    fun dayKey(day: DayOfWeek): String = day.name.take(3).lowercase()

    /**
     * Reads the hours text of one day.
     *
     * Null, empty or blank text means closed. Spaces, ":" and "." are removed. The text is
     * split on ",". Each part is "HHMM-HHMM". A 3-digit time such as "930" means 09:30.
     * Parts that we cannot read are ignored. When no part can be read, the result is
     * [DayHours.Unreadable].
     */
    fun parseDay(text: String?): DayHours {
        if (text.isNullOrBlank()) return DayHours.Closed
        val cleaned = text.filterNot { it.isWhitespace() || it == ':' || it == '.' }
        val parts = cleaned.split(',').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return DayHours.Closed
        val ranges = parts.mapNotNull(::parsePart)
        return if (ranges.isEmpty()) DayHours.Unreadable(text) else DayHours.Ranges(ranges)
    }

    private fun parsePart(part: String): TimeRange? {
        val match = PART.matchEntire(part) ?: return null
        val start = parseTime(match.groupValues[1], isEnd = false) ?: return null
        val end = parseTime(match.groupValues[2], isEnd = true) ?: return null
        return TimeRange(start, end)
    }

    /** Reads "HHMM", "HMM", "HH" or "H". Gives minutes from midnight. For an end, 0 and 2400 give 24 * 60. */
    private fun parseTime(text: String, isEnd: Boolean): Int? {
        val (hour, minute) = when (text.length) {
            1, 2 -> text.toInt() to 0
            3 -> text.substring(0, 1).toInt() to text.substring(1).toInt()
            4 -> text.substring(0, 2).toInt() to text.substring(2).toInt()
            else -> return null
        }
        if (minute > 59) return null
        val value = hour * 60 + minute
        return when {
            isEnd && (value == 0 || value == MINUTES_PER_DAY) -> MINUTES_PER_DAY
            value < MINUTES_PER_DAY -> value
            else -> null
        }
    }

    /**
     * Tells if [business] is open at [now]. The checks are done in this order:
     * no Commerce workflow, paused, on holiday, always open, today's hours.
     *
     * A range that goes past midnight is open from its start to midnight. Yesterday's range
     * of this type is also open today, until its end. The web app does not do this.
     *
     * When we cannot read today's hours, the shop is [OpenState.Open] with no closing time.
     * The web app also shows these shops as open. The server check before checkout
     * (`helpers/shop_open`) still stops an order when the shop is closed.
     */
    fun state(business: Business, now: ZonedDateTime): OpenState {
        val workflow = business.commerceWorkflow ?: return OpenState.NotOrderable
        val settings = workflow.data
        if (settings?.paused == true) return OpenState.Paused
        val local = now.withZoneSameInstant(ChiangMaiTime.ZONE)
        parseInstant(settings?.closedUntil)?.let { until ->
            if (until.isAfter(local.toInstant())) return OpenState.OnHoliday(until)
        }
        val data = business.data
        val hours = data?.hours
        if (data?.hoursType == HOURS_ALWAYS_OPEN || hours.isNullOrEmpty()) return OpenState.Open(null)

        val minute = local.hour * 60 + local.minute
        val yesterday = parseDay(hours[dayKey(local.dayOfWeek.minus(1))])
        if (yesterday is DayHours.Ranges) {
            val spill = yesterday.ranges.filter { it.isOvernight && minute < it.endMinute }
            if (spill.isNotEmpty()) return OpenState.Open(toLocalTime(spill.maxOf { it.endMinute }))
        }
        return when (val today = parseDay(hours[dayKey(local.dayOfWeek)])) {
            DayHours.Closed -> OpenState.ClosedToday
            is DayHours.Unreadable -> OpenState.Open(null)
            is DayHours.Ranges -> {
                val current = today.ranges.firstOrNull { it.containsToday(minute) }
                if (current != null) {
                    val allDay = current.startMinute == 0 && current.endMinute == MINUTES_PER_DAY
                    OpenState.Open(if (allDay) null else toLocalTime(current.endMinute))
                } else {
                    val next = today.ranges.map { it.startMinute }.filter { it > minute }.minOrNull()
                    OpenState.ClosedNow(next?.let(::toLocalTime))
                }
            }
        }
    }

    /**
     * Short text about today's hours for the shop list and the shop page. Examples:
     * "12:00–19:20", "11:00–14:00, 17:00–21:00", "Closed today", "Open 24 hours",
     * "On holiday until 30 Sep", "Not taking orders now".
     */
    fun todayText(business: Business, now: ZonedDateTime): String {
        val local = now.withZoneSameInstant(ChiangMaiTime.ZONE)
        when (val state = state(business, now)) {
            OpenState.NotOrderable, OpenState.Paused -> return "Not taking orders now"
            is OpenState.OnHoliday ->
                return "On holiday until ${state.until.atZone(ChiangMaiTime.ZONE).format(HOLIDAY_DATE)}"
            else -> Unit
        }
        val data = business.data
        val hours = data?.hours
        if (data?.hoursType == HOURS_ALWAYS_OPEN || hours.isNullOrEmpty()) return "Open 24 hours"
        return when (val today = parseDay(hours[dayKey(local.dayOfWeek)])) {
            DayHours.Closed -> "Closed today"
            is DayHours.Unreadable -> today.text.trim()
            is DayHours.Ranges ->
                if (today.ranges.any { it.startMinute == 0 && it.endMinute == MINUTES_PER_DAY }) "Open 24 hours"
                else today.ranges.joinToString(", ") { it.format() }
        }
    }

    private const val HOURS_ALWAYS_OPEN = "always-open"

    /** 24 * 60 becomes midnight (00:00). */
    private fun toLocalTime(minute: Int): LocalTime = LocalTime.of((minute / 60) % 24, minute % 60)

    /** Reads an ISO time such as "2026-08-12T17:00:00.000Z". Gives null for empty or bad text. */
    internal fun parseInstant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try {
            Instant.parse(text.trim())
        } catch (e: DateTimeParseException) {
            try {
                OffsetDateTime.parse(text.trim()).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
        }
    }
}
