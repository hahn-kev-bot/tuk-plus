package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A content record. `data` has a different shape for each [blobType], so it stays raw
 * here. Use [PageBlobs] and [MenuParser] to read it.
 */
@Serializable
data class Blob(
    val id: String,
    val parentId: String? = null,
    val parentType: String? = null,
    val blobType: String? = null,
    val data: JsonElement = JsonNull,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isDeleted: Boolean = false,
) {
    companion object {
        const val TYPE_LIST = "list"
        const val TYPE_LIST_TITLE = "list_title"
        const val TYPE_MENU_CATEGORIES = "menu_categories"
        const val TYPE_DIGITAL_MENU = "digital_menu"
        const val TYPE_OPTIONS_MENU = "options_menu"
        const val TYPE_MENU_PREFACE = "menu_preface"
    }
}
