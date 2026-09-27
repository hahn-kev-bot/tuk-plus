package app.hahn.tukplus.core.domain

/**
 * The parts of the `data.categories` text of a shop.
 *
 * @property cuisines Entries to show. The first one is the main cuisine.
 * @property badges Entries that end with "*", without "*" and "_". For example "TOP RATED".
 * @property keywords Hidden search keywords (entries that start with "_"), without "_".
 */
data class ShopCategories(
    val cuisines: List<String>,
    val badges: List<String>,
    val keywords: List<String>,
) {
    /** The first cuisine, or null when there is none. */
    val mainCuisine: String? get() = cuisines.firstOrNull()

    companion object {
        val EMPTY = ShopCategories(emptyList(), emptyList(), emptyList())
    }
}

/** Reads the `data.categories` text of a shop (api-reference §5.1). */
object Categories {
    /**
     * Splits [text] on ",". Entries are trimmed and empty entries are removed.
     * An entry that ends with "*" is a badge. Else an entry that starts with "_" is a keyword.
     * All other entries are cuisines.
     */
    fun parse(text: String?): ShopCategories {
        if (text.isNullOrBlank()) return ShopCategories.EMPTY
        val cuisines = mutableListOf<String>()
        val badges = mutableListOf<String>()
        val keywords = mutableListOf<String>()
        for (entry in text.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                entry.endsWith("*") -> {
                    val badge = entry.trim('*', '_').trim()
                    if (badge.isNotEmpty()) badges += badge.replaceFirstChar { it.uppercaseChar() }
                }
                entry.startsWith("_") -> {
                    val keyword = entry.trimStart('_').trim()
                    if (keyword.isNotEmpty()) keywords += keyword
                }
                else -> cuisines += entry
            }
        }
        return ShopCategories(cuisines, badges, keywords)
    }
}
