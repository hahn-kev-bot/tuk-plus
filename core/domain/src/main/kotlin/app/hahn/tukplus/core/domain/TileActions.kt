package app.hahn.tukplus.core.domain

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Eat list presets that a tile can open. */
enum class EatPresetKind(val tag: String) {
    FOR_YOU("*for-you"),
    FREE_DELIVERY("*free-delivery"),
    OPEN_NOW("*open-now");

    companion object {
        /** Finds the preset for a tag such as "*for-you". Case is ignored. */
        fun fromTag(tag: String): EatPresetKind? = entries.firstOrNull { it.tag.equals(tag.trim(), ignoreCase = true) }
    }
}

/** What to do when the user taps a Home or Eat tile. */
sealed interface TileAction {
    /** Open the shop with this handle. [handle] starts with "@". */
    data class ShopHandle(val handle: String) : TileAction

    /** Open the shop with this business id. */
    data class ShopId(val businessId: String) : TileAction

    /** Open the Eat list with a text filter. An empty [text] shows all shops. */
    data class EatSearch(val text: String) : TileAction

    /** Open the Eat list with a preset. */
    data class EatPreset(val preset: EatPresetKind) : TileAction

    /** Open the search screen with this text. */
    data class Search(val text: String) : TileAction

    /** Open an external link. */
    data class External(val url: String) : TileAction

    /** The tile does nothing. */
    data object None : TileAction
}

/** Reads the `tag` of a tile (api-reference §4). */
object TileActions {
    /**
     * Gives the action for [tag]. The ad tracking parameter "a=…" is always ignored.
     *
     * - Text that contains "http" is an external link.
     * - "@handle" and "/@handle" open a shop. "/shop/<id>" opens a shop.
     * - "/eat?s=<text>" opens the Eat list with a filter. "/eat" alone shows all shops.
     *   When the text starts with "*", it must be a preset.
     * - "*for-you", "*free-delivery" and "*open-now" are presets. Other "*" tags do nothing.
     * - "/search/<text>" opens the search screen.
     * - Plain text such as "American" opens the Eat list with that filter.
     * - All other tags do nothing.
     */
    fun parse(tag: String): TileAction {
        val text = tag.trim()
        if (text.isEmpty()) return TileAction.None
        if (text.contains("http", ignoreCase = true)) return TileAction.External(text)

        val path = text.substringBefore('?')
        val query = text.substringAfter('?', missingDelimiterValue = "")
        val allParams = query.split('&').filter { it.isNotBlank() }
            .map { it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "") }
        val params = allParams.filter { it.first != "a" }

        return when {
            path.startsWith("@") || path.startsWith("/@") -> {
                val name = path.removePrefix("/").removePrefix("@").trim()
                if (name.isEmpty()) TileAction.None else TileAction.ShopHandle("@$name")
            }
            path.startsWith("/shop/") -> {
                val id = path.removePrefix("/shop/").trim('/').substringBefore('/')
                if (id.isEmpty()) TileAction.None else TileAction.ShopId(id)
            }
            path == "/eat" || path == "/eat/" -> {
                val search = params.firstOrNull { it.first == "s" }?.second?.let(::decode)?.trim()
                when {
                    search == null -> TileAction.EatSearch("")
                    search.startsWith("*") -> preset(search)
                    else -> TileAction.EatSearch(search)
                }
            }
            path.startsWith("*") -> preset(path)
            path.startsWith("/search/") -> {
                val search = decode(path.removePrefix("/search/")).trim()
                if (search.isEmpty()) TileAction.None else TileAction.Search(search)
            }
            path.startsWith("/") -> TileAction.None
            // Plain text. A "?" can be part of the text, so remove only a query of ad parameters.
            else -> TileAction.EatSearch(if (allParams.isNotEmpty() && params.isEmpty()) path.trim() else text)
        }
    }

    private fun preset(tag: String): TileAction =
        EatPresetKind.fromTag(tag)?.let { TileAction.EatPreset(it) } ?: TileAction.None

    /** URL decoding. Text with a bad "%" sequence (for example "15%") stays as it is. */
    private fun decode(text: String): String = try {
        URLDecoder.decode(text, StandardCharsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        text
    }
}
