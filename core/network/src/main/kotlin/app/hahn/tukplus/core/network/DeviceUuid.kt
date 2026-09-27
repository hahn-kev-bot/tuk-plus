package app.hahn.tukplus.core.network

import java.security.SecureRandom
import java.util.UUID

/**
 * Makes a version 1 (time-based) UUID, like the web app's `newUUID()`. The node id is
 * random (with the multicast bit set, as RFC 4122 says for random node ids).
 * The app makes this value one time and keeps it (api-reference §2).
 */
object DeviceUuid {
    /** 100-ns intervals between 1582-10-15 (UUID epoch) and 1970-01-01. */
    private const val EPOCH_OFFSET = 0x01B21DD213814000L
    private val random = SecureRandom()

    fun newV1(nowMillis: Long = System.currentTimeMillis()): UUID {
        val timestamp = nowMillis * 10_000 + EPOCH_OFFSET + random.nextInt(10_000)
        val timeLow = timestamp and 0xFFFFFFFFL
        val timeMid = (timestamp ushr 32) and 0xFFFFL
        val timeHigh = (timestamp ushr 48) and 0x0FFFL
        val msb = (timeLow shl 32) or (timeMid shl 16) or (0x1000L or timeHigh)
        val clockSeq = (random.nextInt(0x4000).toLong() or 0x8000L) // variant 10xx
        val node = (random.nextLong() and 0xFFFFFFFFFFFFL) or 0x010000000000L
        val lsb = (clockSeq shl 48) or node
        return UUID(msb, lsb)
    }
}
