package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.core.model.TimeSlot
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import java.time.ZonedDateTime
import java.util.Random

/** Rules that select the Home and Eat rows and tiles to show (api-reference §4). */
object HomeRules {
    /**
     * Gives the rows to show at [now], in the input order.
     *
     * - A row with `settings.hidden` is removed.
     * - A row is removed when the Chiang Mai time is outside `settings.time_slot`.
     * - Tiles with `hidden` are removed.
     * - When `settings.shop_open` is true, a shop tile is removed when [shopIsOpen] gives false.
     *   When it gives null (not known), the tile stays.
     * - When `settings.randomise` is true, the tiles are shuffled. The same [shuffleSeed]
     *   gives the same order, so the order does not change on each screen update.
     * - A row with no tiles is removed.
     */
    fun visibleRows(
        rows: List<PageRow>,
        now: ZonedDateTime,
        shopIsOpen: (handle: String) -> Boolean?,
        shuffleSeed: Long,
    ): List<PageRow> {
        val local = now.withZoneSameInstant(ChiangMaiTime.ZONE)
        val hhmm = local.hour * 100 + local.minute
        return rows.mapNotNull { row ->
            val settings = row.title?.settings
            if (settings?.hidden == true) return@mapNotNull null
            if (!inTimeSlot(settings?.timeSlot, hhmm)) return@mapNotNull null
            var tiles = row.tiles.filter { !it.hidden }
            if (settings?.shopOpen == true) {
                tiles = tiles.filter { tile ->
                    val action = TileActions.parse(tile.tag)
                    action !is TileAction.ShopHandle || shopIsOpen(action.handle) != false
                }
            }
            if (settings?.randomise == true) tiles = tiles.shuffled(Random(shuffleSeed + row.page.hashCode()))
            if (tiles.isEmpty()) null else row.copy(tiles = tiles)
        }
    }

    /**
     * Tells if the time [hhmm] (for example 1930 for 19:30) is in [slot].
     * The start and end are "HHMM" text or numbers, for example "0700", "700" or 2300.
     * An end of 0 means 2400. A missing start means 0000. A missing end means 2400.
     * When the end is before the start, the slot goes past midnight.
     */
    fun inTimeSlot(slot: TimeSlot?, hhmm: Int): Boolean {
        if (slot == null) return true
        val start = slot.start?.let(::parseIntLikeJavaScript)
        val rawEnd = slot.end?.let(::parseIntLikeJavaScript)
        if (start == null && rawEnd == null) return true
        val from = start ?: 0
        val to = if (rawEnd == null || rawEnd == 0) 2400 else rawEnd
        return if (to < from) hhmm >= from || hhmm < to else hhmm in from until to
    }
}
