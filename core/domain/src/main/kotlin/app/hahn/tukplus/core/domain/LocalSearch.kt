package app.hahn.tukplus.core.domain

/** Shop search in the cached eatery list, with no network call. */
object LocalSearch {
    /**
     * Finds shops for [text]. Case is ignored. The best matches come first:
     *
     * 0. The name starts with the text.
     * 1. A word in the name starts with the text.
     * 2. The name contains the text.
     * 3. The categories or the search terms contain the text.
     *
     * In each group, open shops come first, then the sort is by name.
     * Empty or blank text gives an empty list.
     */
    fun shops(items: List<ShopListItem>, text: String, limit: Int = 30): List<ShopListItem> {
        val needle = text.trim().lowercase()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        return items.mapNotNull { item -> rank(item, needle)?.let { it to item } }
            .sortedWith(
                compareBy<Pair<Int, ShopListItem>> { it.first }
                    .thenBy { if (it.second.openState.isOpen) 0 else 1 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.business.name },
            )
            .take(limit)
            .map { it.second }
    }

    private fun rank(item: ShopListItem, needle: String): Int? {
        val name = item.business.name.lowercase()
        if (name.startsWith(needle)) return 0
        var index = name.indexOf(needle)
        var found = index >= 0
        while (index >= 0) {
            if (index == 0 || !name[index - 1].isLetterOrDigit()) return 1
            index = name.indexOf(needle, index + 1)
        }
        if (found) return 2
        val data = item.business.data
        found = data?.categories.orEmpty().lowercase().contains(needle) ||
            data?.searchTerms.orEmpty().lowercase().contains(needle)
        return if (found) 3 else null
    }
}
