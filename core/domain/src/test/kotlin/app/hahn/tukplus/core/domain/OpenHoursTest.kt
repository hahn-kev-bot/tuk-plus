package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.Fixtures.monday
import app.hahn.tukplus.core.domain.Fixtures.shop
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.model.WorkflowData
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenHoursTest {
    private fun ranges(vararg pairs: Pair<Int, Int>) = DayHours.Ranges(pairs.map { TimeRange(it.first, it.second) })
    private fun m(hhmm: Int) = hhmm / 100 * 60 + hhmm % 100

    @Test
    fun `empty text is closed`() {
        assertEquals(DayHours.Closed, OpenHours.parseDay(null))
        assertEquals(DayHours.Closed, OpenHours.parseDay(""))
        assertEquals(DayHours.Closed, OpenHours.parseDay("   "))
        assertEquals(DayHours.Closed, OpenHours.parseDay(" , "))
    }

    @Test
    fun `all seen shapes parse`() {
        val expected = ranges(m(800) to m(1600))
        for (text in listOf("0800-1600", "08:00-16:00", "08.00-16.00", "0800-16.00", "0800-16:00", "08:00-16.00",
            "08:00-1600", "08.00-16:00", "08.00-1600", "08:00 -16:00", "08:00- 16:00", "08.00 -16.00",
            "08.00-16.00 ", "0800-1600 ", "800-1600")) {
            assertEquals(expected, OpenHours.parseDay(text), text)
        }
        val two = ranges(m(1100) to m(1400), m(1700) to m(2100))
        for (text in listOf("1100-1400, 1700-2100", "1100-1400,1700-2100", "1100-1400 , 1700-2100",
            "11.00-1400,17.00-21.00", "1100-1400, 17.00-21.00", "11:00-14:00, 17:00-21.00")) {
            assertEquals(two, OpenHours.parseDay(text), text)
        }
        assertEquals(ranges(m(930) to m(1400)), OpenHours.parseDay("930-1400"))
    }

    @Test
    fun `an end of 0 or 2400 is midnight`() {
        assertEquals(ranges(m(1000) to 1440), OpenHours.parseDay("1000-0000"))
        assertEquals(ranges(m(1000) to 1440), OpenHours.parseDay("1000-0"))
        assertEquals(ranges(m(1000) to 1440), OpenHours.parseDay("10:00-24:00"))
        assertEquals(ranges(0 to 1440), OpenHours.parseDay("0000-0000"))
    }

    @Test
    fun `overnight range keeps end before start`() {
        val day = OpenHours.parseDay("0800-0400") as DayHours.Ranges
        assertTrue(day.ranges.single().isOvernight)
    }

    @Test
    fun `bad text is unreadable and bad parts are ignored`() {
        assertEquals(DayHours.Unreadable("closed"), OpenHours.parseDay("closed"))
        assertEquals(DayHours.Unreadable("2500-2600"), OpenHours.parseDay("2500-2600"))
        assertEquals(DayHours.Unreadable("0860-1000"), OpenHours.parseDay("0860-1000"))
        assertEquals(ranges(m(800) to m(1000)), OpenHours.parseDay("0800-1000, later"))
    }

    @Test
    fun `every hours text in the recorded eatery list can be read`() {
        val values = Fixtures.eateries.flatMap { it.data?.hours?.values.orEmpty() }
        assertTrue(values.size >= 1700, "only ${values.size} hours values")
        val bad = values.filter { OpenHours.parseDay(it) is DayHours.Unreadable }
        assertEquals(emptyList(), bad, "unreadable hours")
        // Each state can be computed for each shop, at each hour of a week.
        for (shop in Fixtures.eateries) {
            for (hour in 0 until 24 * 7 step 5) OpenHours.state(shop, monday(0).plusHours(hour.toLong()))
            OpenHours.todayText(shop, monday(12))
        }
    }

    private val weekdays = mapOf(
        "mon" to "1100-1400, 1700-2100", "tue" to "1200-1920", "wed" to "", "thu" to null,
        "fri" to "1800-0200", "sat" to "0000-0000", "sun" to "closed",
    )
    private val normal = shop(hours = weekdays)

    @Test
    fun `open inside a range, with the closing time`() {
        assertEquals(OpenState.Open(LocalTime.of(14, 0)), OpenHours.state(normal, monday(11, 0)))
        assertEquals(OpenState.Open(LocalTime.of(14, 0)), OpenHours.state(normal, monday(13, 59)))
        assertEquals(OpenState.Open(LocalTime.of(21, 0)), OpenHours.state(normal, monday(17, 30)))
    }

    @Test
    fun `closed now between ranges gives the next opening time`() {
        assertEquals(OpenState.ClosedNow(LocalTime.of(11, 0)), OpenHours.state(normal, monday(10, 59)))
        assertEquals(OpenState.ClosedNow(LocalTime.of(17, 0)), OpenHours.state(normal, monday(14, 0)))
        assertEquals(OpenState.ClosedNow(null), OpenHours.state(normal, monday(21, 0)))
        assertFalse(OpenHours.state(normal, monday(21, 0)).isOpen)
    }

    @Test
    fun `empty or missing day is closed today`() {
        assertEquals(OpenState.ClosedToday, OpenHours.state(normal, monday(12).plusDays(2))) // Wednesday ""
        assertEquals(OpenState.ClosedToday, OpenHours.state(normal, monday(12).plusDays(3))) // Thursday null
        assertEquals(OpenState.ClosedToday, OpenHours.state(shop(hours = mapOf("tue" to "0800-1600")), monday(12)))
    }

    @Test
    fun `overnight range is open until midnight and after midnight on the next day`() {
        val friday = monday(0).plusDays(4)
        assertEquals(OpenState.ClosedNow(LocalTime.of(18, 0)), OpenHours.state(normal, friday.withHour(17)))
        assertEquals(OpenState.Open(LocalTime.of(2, 0)), OpenHours.state(normal, friday.withHour(23)))
        // Saturday 01:00 is still in Friday's range.
        assertEquals(OpenState.Open(LocalTime.of(2, 0)), OpenHours.state(normal, friday.plusDays(1).withHour(1)))
    }

    @Test
    fun `0000-0000 is open all day`() {
        val saturday = monday(0).plusDays(5)
        assertEquals(OpenState.Open(null), OpenHours.state(normal, saturday.withHour(3)))
        assertEquals("Open 24 hours", OpenHours.todayText(normal, saturday.withHour(3)))
    }

    @Test
    fun `unreadable day fails open`() {
        val sunday = monday(12).plusDays(6)
        assertEquals(OpenState.Open(null), OpenHours.state(normal, sunday))
        assertEquals("closed", OpenHours.todayText(normal, sunday))
    }

    @Test
    fun `always open and no hours are open`() {
        assertEquals(OpenState.Open(null), OpenHours.state(shop(hours = weekdays, hoursType = "always-open"), monday(3)))
        assertEquals(OpenState.Open(null), OpenHours.state(shop(hours = null), monday(3)))
        assertEquals(OpenState.Open(null), OpenHours.state(shop(hours = emptyMap()), monday(3)))
        assertEquals("Open 24 hours", OpenHours.todayText(shop(hours = null), monday(3)))
    }

    @Test
    fun `no commerce workflow is not orderable`() {
        assertEquals(OpenState.NotOrderable, OpenHours.state(shop(commerce = null), monday(12)))
        val deliveryOnly = shop(commerce = null).copy(
            workflows = listOf(Workflow(id = "d", workflowTypeName = Workflow.TYPE_DELIVERY)),
        )
        assertEquals(OpenState.NotOrderable, OpenHours.state(deliveryOnly, monday(12)))
        assertEquals("Not taking orders now", OpenHours.todayText(deliveryOnly, monday(12)))
    }

    @Test
    fun `paused comes before holiday and hours`() {
        val paused = shop(hours = weekdays, commerce = WorkflowData(paused = true, closedUntil = "2099-01-01T00:00:00Z"))
        assertEquals(OpenState.Paused, OpenHours.state(paused, monday(12)))
        assertEquals("Not taking orders now", OpenHours.todayText(paused, monday(12)))
    }

    @Test
    fun `holiday in the future closes the shop, in the past it does not`() {
        val holiday = shop(hours = weekdays, commerce = WorkflowData(closedUntil = "2026-09-29T17:00:00.000Z"))
        assertEquals(OpenState.OnHoliday(Instant.parse("2026-09-29T17:00:00Z")), OpenHours.state(holiday, monday(12)))
        assertEquals("On holiday until 30 Sep", OpenHours.todayText(holiday, monday(12)))
        val over = shop(hours = weekdays, commerce = WorkflowData(closedUntil = "2026-08-12T17:00:00.000Z"))
        assertEquals(OpenState.Open(LocalTime.of(14, 0)), OpenHours.state(over, monday(12)))
        val bad = shop(hours = weekdays, commerce = WorkflowData(closedUntil = "soon"))
        assertEquals(OpenState.Open(LocalTime.of(14, 0)), OpenHours.state(bad, monday(12)))
    }

    @Test
    fun `time in another zone is changed to Chiang Mai time`() {
        // 05:00 UTC is 12:00 in Chiang Mai.
        val utc = monday(12).withZoneSameInstant(ZoneOffset.UTC)
        assertEquals(OpenState.Open(LocalTime.of(14, 0)), OpenHours.state(normal, utc))
    }

    @Test
    fun `today text lists the ranges`() {
        assertEquals("11:00–14:00, 17:00–21:00", OpenHours.todayText(normal, monday(12)))
        assertEquals("12:00–19:20", OpenHours.todayText(normal, monday(12).plusDays(1)))
        assertEquals("Closed today", OpenHours.todayText(normal, monday(12).plusDays(2)))
        assertEquals("18:00–02:00", OpenHours.todayText(normal, monday(12).plusDays(4)))
        assertEquals("10:00–24:00", OpenHours.todayText(shop(hours = mapOf("mon" to "1000-0000")), monday(12)))
    }
}
