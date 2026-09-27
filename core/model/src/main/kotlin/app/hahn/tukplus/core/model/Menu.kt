package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class LocalizedText(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class MenuCategory(
    val name: String = "",
    val en: String? = null,
    val th: String? = null,
)

/** One item of the `digital_menu` blob. Prices are text in the API. */
@Serializable
data class MenuItem(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    @Serializable(LenientStringSerializer::class) val price: String? = null,
    val pic: String? = null,
    val category: String? = null,
    /** Ids of `options_menu` blobs. */
    val options: List<String>? = null,
    val en: LocalizedText? = null,
    val th: LocalizedText? = null,
    @Serializable(LenientStringSerializer::class) val discount: String? = null,
    /** "percent" (default) or "number". */
    val discountType: String? = null,
    @Serializable(LenientBooleanSerializer::class) val outOfStock: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val hidden: Boolean? = null,
    val tags: List<String>? = null,
    @Serializable(LenientBooleanSerializer::class) val special: Boolean? = null,
    @Serializable(LenientBooleanSerializer::class) val flashDeal: Boolean? = null,
    @Serializable(LenientStringSerializer::class) val priceFrom: String? = null,
    @Serializable(LenientIntSerializer::class) val maxCount: Int? = null,
    @Serializable(LenientBooleanSerializer::class) val vatable: Boolean? = null,
    @Serializable(LenientIntSerializer::class) val prepTime: Int? = null,
    /** Days when the item is available. Not seen with a value yet, so it stays raw. */
    val schedule: JsonElement? = null,
    val freeGift: JsonElement? = null,
) {
    /** Name in English if there is one, else the default name. */
    val displayName: String get() = en?.name?.takeIf { it.isNotBlank() } ?: name.orEmpty()
    val displayDescription: String? get() = en?.description?.takeIf { it.isNotBlank() } ?: description
}

/** One option group (`options_menu` blob `data`). */
@Serializable
data class OptionGroup(
    val name: String = "",
    val en: String? = null,
    val th: String? = null,
    @Serializable(LenientBooleanSerializer::class) val required: Boolean? = null,
    /** "single" or "multiple". */
    val select: String? = null,
    @Serializable(LenientBooleanSerializer::class) val allowMultiple: Boolean? = null,
    /** "none", "exactly" or "up_to". */
    val multipleConstraint: String? = null,
    @Serializable(LenientIntSerializer::class) val multipleN: Int? = null,
    /** Show the group only when a selected option name contains this text. */
    val condition: String? = null,
    @Serializable(LenientBooleanSerializer::class) val showZero: Boolean? = null,
    val zerosAs: String? = null,
    val items: List<OptionItem> = emptyList(),
) {
    val displayName: String get() = en?.takeIf { it.isNotBlank() } ?: name
}

@Serializable
data class OptionItem(
    val id: String,
    val name: String = "",
    val description: String? = null,
    @Serializable(LenientStringSerializer::class) val price: String? = null,
    @Serializable(LenientBooleanSerializer::class) val outOfStock: Boolean? = null,
    val en: LocalizedText? = null,
    val th: LocalizedText? = null,
) {
    val displayName: String get() = en?.name?.takeIf { it.isNotBlank() } ?: name
}

/** A menu item with its raw JSON. The web checkout fallback needs the raw item. */
data class MenuEntry(val item: MenuItem, val raw: JsonObject)

/** An option group with its blob id (the id that menu items refer to) and raw JSON. */
data class MenuOptionGroup(val blobId: String, val group: OptionGroup, val raw: JsonObject)

/** The full menu of one Commerce workflow. */
data class Menu(
    val workflowId: String,
    /** `workflow.updated_at`. Use it as the menu version. */
    val version: String?,
    val categories: List<MenuCategory>,
    val entries: List<MenuEntry>,
    val optionGroups: Map<String, MenuOptionGroup>,
    val preface: JsonObject?,
    /** Problems found while reading the blobs. Empty when all is well. */
    val problems: List<String>,
) {
    fun groupsFor(item: MenuItem): List<MenuOptionGroup> =
        item.options.orEmpty().mapNotNull { optionGroups[it] }
}

object MenuParser {
    /** Reads the menu blobs of a Commerce workflow. Bad records are skipped and reported in [Menu.problems]. */
    fun parse(workflow: Workflow): Menu {
        val problems = mutableListOf<String>()
        val blobs = workflow.blobs.orEmpty().filter { !it.isDeleted }
        var categories = emptyList<MenuCategory>()
        val entries = mutableListOf<MenuEntry>()
        val groups = linkedMapOf<String, MenuOptionGroup>()
        var preface: JsonObject? = null

        for (blob in blobs) {
            when (blob.blobType) {
                Blob.TYPE_MENU_CATEGORIES -> {
                    val array = blob.data as? JsonArray
                    if (array == null) problems += "menu_categories ${blob.id}: data is not a list"
                    categories = array.orEmpty().mapNotNull { element ->
                        decodeOrNull(MenuCategory.serializer(), element, "menu_categories ${blob.id}", problems)
                    }
                }
                Blob.TYPE_DIGITAL_MENU -> {
                    val array = blob.data as? JsonArray
                    if (array == null) problems += "digital_menu ${blob.id}: data is not a list"
                    array.orEmpty().forEachIndexed { index, element ->
                        val raw = element as? JsonObject
                        if (raw == null) {
                            problems += "digital_menu ${blob.id}[$index]: not an object"
                            return@forEachIndexed
                        }
                        decodeOrNull(MenuItem.serializer(), raw, "digital_menu ${blob.id}[$index]", problems)
                            ?.let { entries += MenuEntry(it, raw) }
                    }
                }
                Blob.TYPE_OPTIONS_MENU -> {
                    val raw = blob.data as? JsonObject
                    if (raw == null) {
                        problems += "options_menu ${blob.id}: data is not an object"
                        continue
                    }
                    decodeOrNull(OptionGroup.serializer(), raw, "options_menu ${blob.id}", problems)
                        ?.let { groups[blob.id] = MenuOptionGroup(blob.id, it, raw) }
                }
                Blob.TYPE_MENU_PREFACE -> preface = blob.data as? JsonObject
            }
        }
        for (entry in entries) {
            for (optionId in entry.item.options.orEmpty()) {
                if (optionId !in groups) problems += "item ${entry.item.id}: unknown option group $optionId"
            }
        }
        return Menu(workflow.id, workflow.updatedAt, categories, entries, groups, preface, problems)
    }

    private fun <T> decodeOrNull(
        serializer: kotlinx.serialization.KSerializer<T>,
        element: JsonElement,
        where: String,
        problems: MutableList<String>,
    ): T? = try {
        TukJson.decodeFromJsonElement(serializer, element)
    } catch (e: kotlinx.serialization.SerializationException) {
        problems += "$where: ${e.message?.lineSequence()?.firstOrNull()}"
        null
    } catch (e: IllegalArgumentException) {
        problems += "$where: ${e.message?.lineSequence()?.firstOrNull()}"
        null
    }
}
