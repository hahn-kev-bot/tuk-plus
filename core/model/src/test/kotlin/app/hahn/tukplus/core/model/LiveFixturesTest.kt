package app.hahn.tukplus.core.model

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parses responses that tools/api-probe recorded from the live API. When the API
 * changes, record again and run these tests:
 * `./gradlew :tools:api-probe:run --args="--record core/model/src/test/resources/fixtures/live"`
 */
class LiveFixturesTest {
    private val dir: File = File(javaClass.getResource("/fixtures/live/manifest.txt")?.toURI() ?: fail("No fixtures")).parentFile

    private fun read(name: String) = File(dir, name).readText()
    private fun files(folder: String) = File(dir, folder).listFiles().orEmpty().sortedBy { it.name }

    /** A strict parser, to find fields whose type is not what we think. */
    private val strict = Json(TukJson) { coerceInputValues = false; isLenient = false }

    @Test
    fun `all Chiang Mai eateries parse`() {
        val shops = strict.decodeFromString<List<Business>>(read("eateries.json"))
        assertTrue(shops.size > 200, "only ${shops.size} shops")
        assertTrue(shops.all { it.id.isNotBlank() && it.name.isNotBlank() })
        val withMenu = shops.count { it.commerceWorkflow != null }
        assertTrue(withMenu > shops.size * 0.9, "only $withMenu shops have a Commerce workflow")
        assertTrue(shops.count { it.data?.premiumLink?.startsWith("@") == true } > 150)
    }

    @Test
    fun `all recorded menus parse without problems`() {
        val menus = files("workflows")
        assertTrue(menus.size >= 25, "only ${menus.size} menus")
        var items = 0
        for (file in menus) {
            val workflows = strict.decodeFromString<List<Workflow>>(file.readText())
            val commerce = workflows.single { it.workflowTypeName == Workflow.TYPE_COMMERCE }
            val menu = MenuParser.parse(commerce)
            assertEquals(emptyList(), menu.problems, file.name)
            items += menu.entries.size
            for (entry in menu.entries) {
                // The raw item is kept for the web checkout fallback.
                assertEquals(entry.item.id, entry.raw["id"].toString().trim('"'))
                for (group in menu.groupsFor(entry.item)) assertTrue(group.group.items.isNotEmpty() || group.group.name.isNotEmpty())
            }
        }
        assertTrue(items > 500, "only $items menu items")
    }

    @Test
    fun `home and eat pages parse`() {
        val pages = files("pages")
        assertTrue(pages.any { it.name == "Home_1.json" })
        var tiles = 0
        for (file in pages) {
            val text = file.readText().trim()
            val blobs = if (text == "null") emptyList() else strict.decodeFromString<List<Blob>>(text)
            val row = PageBlobs.toRow(file.nameWithoutExtension, blobs)
            tiles += row.tiles.size
            assertTrue(row.tiles.all { it.tag.isNotBlank() }, "tile without tag in ${file.name}")
        }
        assertTrue(tiles > 50, "only $tiles tiles")
    }

    @Test
    fun `delivery fleets parse and have prices`() {
        val fleets = files("commerce_delivery").map { strict.decodeFromString<CommerceDelivery>(it.readText()) }
        assertTrue(fleets.size >= 25)
        for (fleet in fleets) {
            val prices = fleet.express?.data?.pricingArray.orEmpty()
            assertTrue(prices.size > 10, "short pricing_array: $prices")
            assertTrue(prices.zipWithNext().all { (a, b) -> b >= a }, "prices go down: $prices")
        }
    }

    @Test
    fun `shop open answers are understood`() {
        val answers = files("shop_open").map { ShopOpenStatus.fromText(it.readText()) }
        assertTrue(answers.any { it == ShopOpenStatus.Open } || answers.isNotEmpty())
        files("shop_open").filter { it.name.endsWith(".500.txt") }.forEach { file ->
            val status = ShopOpenStatus.fromText(file.readText())
            assertTrue(status is ShopOpenStatus.Closed && !status.reason.startsWith(ShopOpenStatus.CLOSED_PREFIX), file.name)
        }
    }

    @Test
    fun `search and other responses parse`() {
        strict.decodeFromString<List<NewShop>>(read("new_shops.json"))
        strict.decodeFromString<List<ForYouScore>>(read("foryou.json"))
        strict.decodeFromString<BusinessAutocomplete>(read("autocomplete_business/pizza.json"))
        strict.decodeFromString<List<MenuItemSearchHit>>(read("menu_items/pizza.json"))
        strict.decodeFromString<Directions>(read("directions.json"))
        strict.decodeFromString<PriceCheck>(read("price_check.json"))
        val link = files("short_link").first().let { strict.decodeFromString<ShortLink>(it.readText()) }
        assertTrue(link.businessId != null)
        // No match gives {"businesses":null}.
        val none = strict.decodeFromString<BusinessAutocomplete>(read("autocomplete_business/zzqqxx.json"))
        assertTrue(none.businesses.isNullOrEmpty())
    }
}
