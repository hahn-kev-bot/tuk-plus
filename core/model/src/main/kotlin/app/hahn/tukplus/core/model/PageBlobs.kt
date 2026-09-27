package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One tile in a Home or Eat row (`blob_type` "list"). */
data class PageTile(
    val id: String,
    val name: String,
    /** Action text, for example "@murka?a=…", "/eat?s=buy1get1", "*for-you". */
    val tag: String,
    val rank: Int,
    val hidden: Boolean,
    val showName: Boolean,
    /** Default picture. */
    val pic: String?,
    /** Pictures per language code, from the `pic_<lang>` fields. */
    val localizedPics: Map<String, String>,
) {
    fun picFor(language: String): String? = localizedPics[language] ?: pic
}

/** The title and settings of a row (`blob_type` "list_title"). */
@Serializable
data class PageRowTitle(
    val page: String? = null,
    val title: Map<String, String>? = null,
    val settings: PageRowSettings? = null,
) {
    fun titleFor(language: String): String? = title?.get(language) ?: title?.get("en")
}

@Serializable
data class PageRowSettings(
    @Serializable(LenientBooleanSerializer::class) val randomise: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val shopOpen: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val hidden: Boolean? = null,
    val timeSlot: TimeSlot? = null,
)

@Serializable
data class TimeSlot(
    @Serializable(LenientStringSerializer::class) val start: String? = null,
    @Serializable(LenientStringSerializer::class) val end: String? = null,
)

/** A Home or Eat row: its title and tiles, in the order that the server gives. */
data class PageRow(
    val page: String,
    val title: PageRowTitle?,
    val tiles: List<PageTile>,
)

object PageBlobs {
    /**
     * Makes a row from the blobs of one page. Tiles keep all flags; the caller decides
     * what to hide. The first title blob is used, like the web app.
     */
    fun toRow(page: String, blobs: List<Blob>): PageRow {
        val title = blobs.firstOrNull { it.blobType == Blob.TYPE_LIST_TITLE }?.let { blob ->
            (blob.data as? JsonObject)?.let { TukJson.decodeFromJsonElement(PageRowTitle.serializer(), it) }
        }
        val tiles = blobs.filter { it.blobType == Blob.TYPE_LIST }.mapNotNull { toTile(it) }
            .sortedBy { it.rank }
        return PageRow(page, title, tiles)
    }

    fun toTile(blob: Blob): PageTile? {
        val data = blob.data as? JsonObject ?: return null
        fun text(key: String): String? = (data[key] as? JsonPrimitive)?.contentOrNull
        fun flag(key: String): Boolean = text(key)?.lowercase() == "true"
        val pics = data.keys.filter { it.startsWith("pic_") }
            .mapNotNull { key -> text(key)?.takeIf { it.isNotBlank() }?.let { key.removePrefix("pic_") to it } }
            .toMap()
        return PageTile(
            id = blob.id,
            name = text("name").orEmpty(),
            tag = text("tag").orEmpty(),
            rank = text("rank")?.let(::parseIntLikeJavaScript) ?: Int.MAX_VALUE,
            hidden = flag("hidden"),
            showName = flag("show_name"),
            pic = text("pic")?.takeIf { it.isNotBlank() },
            localizedPics = pics,
        )
    }
}
