package app.hahn.tukplus.core.data

import java.time.Duration

/** Refresh ages from PLAN.md §5.3. */
object Policies {
    val HOME_PAGE = CachePolicy(maxAge = Duration.ofMinutes(30), emptyMaxAge = Duration.ofDays(1))
    val EAT_CHIPS = CachePolicy(maxAge = Duration.ofHours(6))
    val EATERIES = CachePolicy(maxAge = Duration.ofMinutes(15))
    val NEW_SHOPS = CachePolicy(maxAge = Duration.ofHours(6))
    val FOR_YOU = CachePolicy(maxAge = Duration.ofHours(6))
    val MENU = CachePolicy(maxAge = Duration.ofHours(1))
    val FLEET = CachePolicy(maxAge = Duration.ofMinutes(30))
    val BUSINESS = CachePolicy(maxAge = Duration.ofHours(1))
    val SHORT_LINK = CachePolicy(maxAge = Duration.ofDays(7))
    val SEARCH = CachePolicy(maxAge = Duration.ofMinutes(10))
}
