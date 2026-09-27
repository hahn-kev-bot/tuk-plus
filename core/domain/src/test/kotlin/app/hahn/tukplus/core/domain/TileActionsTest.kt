package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.EatPresetKind.FOR_YOU
import app.hahn.tukplus.core.domain.EatPresetKind.FREE_DELIVERY
import app.hahn.tukplus.core.domain.EatPresetKind.OPEN_NOW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileActionsTest {
    private fun check(tag: String, expected: TileAction) = assertEquals(expected, TileActions.parse(tag), tag)

    @Test
    fun `shop handles`() {
        check("@murka?a=hp_tile_murka_whatshot", TileAction.ShopHandle("@murka"))
        check("/@7senses?a=hp_tile_vday", TileAction.ShopHandle("@7senses"))
        check("@airconCM", TileAction.ShopHandle("@airconCM"))
        check("@", TileAction.None)
    }

    @Test
    fun `shop ids`() {
        check("/shop/8096e441-19c0-11ec-9067-964bb4500c70", TileAction.ShopId("8096e441-19c0-11ec-9067-964bb4500c70"))
        check("/shop/abc/?a=x", TileAction.ShopId("abc"))
        check("/shop/", TileAction.None)
    }

    @Test
    fun `eat searches and presets`() {
        check("/eat?s=buy1get1&a=hp_tile_deals_buy1get1", TileAction.EatSearch("buy1get1"))
        check("/eat?a=x&s=middle%20eastern", TileAction.EatSearch("middle eastern"))
        check("/eat?s=_15&a=hp_tile_deals_upto30", TileAction.EatSearch("_15"))
        check("/eat?s=15%", TileAction.EatSearch("15%"))
        check("/eat", TileAction.EatSearch(""))
        check("/eat?a=x", TileAction.EatSearch(""))
        check("/eat?s=*open-now", TileAction.EatPreset(OPEN_NOW))
        check("/eat?s=*nothing", TileAction.None)
        check("*for-you", TileAction.EatPreset(FOR_YOU))
        check("*free-delivery?a=x", TileAction.EatPreset(FREE_DELIVERY))
        check("*Open-Now", TileAction.EatPreset(OPEN_NOW))
        check("*unknown", TileAction.None)
    }

    @Test
    fun `plain text is an eat search`() {
        check("American", TileAction.EatSearch("American"))
        check("15%", TileAction.EatSearch("15%"))
        check(" Middle Eastern ", TileAction.EatSearch("Middle Eastern"))
        check("Pizza?a=x", TileAction.EatSearch("Pizza"))
        check("Why not?", TileAction.EatSearch("Why not?"))
    }

    @Test
    fun `search, external and unknown`() {
        check("/search/Breakfast%20Sand?a=hp_tile_breakfastsandwich", TileAction.Search("Breakfast Sand"))
        check("/search/Duke", TileAction.Search("Duke"))
        check("https://m.me/tukcommerce", TileAction.External("https://m.me/tukcommerce"))
        check(" https://tukapp.typeform.com/to/XsD3OzAc?a=1 ", TileAction.External("https://tukapp.typeform.com/to/XsD3OzAc?a=1"))
        check("/orders", TileAction.None)
        check("", TileAction.None)
        check("   ", TileAction.None)
    }

    @Test
    fun `every recorded tile tag has an action`() {
        val tiles = Fixtures.pageRows.flatMap { it.tiles }
        assertTrue(tiles.size > 50)
        val none = tiles.filter { TileActions.parse(it.tag) == TileAction.None }.map { it.tag }
        assertEquals(emptyList(), none, "tags with no action")
        // Eat chips are plain text and one preset.
        val eat = Fixtures.pageRows.single { it.page == "Eat" }.tiles.map { TileActions.parse(it.tag) }
        assertTrue(TileAction.EatPreset(FOR_YOU) in eat)
        assertTrue(TileAction.EatSearch("American") in eat)
    }
}
