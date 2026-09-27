package app.hahn.tukplus.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.ZonedDateTime
import app.hahn.tukplus.core.domain.ChiangMaiTime

/** Chiang Mai time now, and again every [periodMs]. Open states change with time, so lists use this. */
fun chiangMaiTicker(clock: Clock, periodMs: Long = 60_000): Flow<ZonedDateTime> = flow {
    while (true) {
        emit(ZonedDateTime.now(clock).withZoneSameInstant(ChiangMaiTime.ZONE))
        delay(periodMs)
    }
}
