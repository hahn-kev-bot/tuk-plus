package app.hahn.tukplus.core.domain

import java.time.ZoneId

/** Time values for release 1. All shops are in Chiang Mai. */
object ChiangMaiTime {
    /**
     * The time zone of the shops. Use it for opening hours and time slots,
     * not the time zone of the device (api-reference §5.2).
     */
    val ZONE: ZoneId = ZoneId.of("Asia/Bangkok")
}
