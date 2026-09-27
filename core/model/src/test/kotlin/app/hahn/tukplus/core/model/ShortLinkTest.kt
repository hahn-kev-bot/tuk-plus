package app.hahn.tukplus.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortLinkTest {
    @Test
    fun `business id comes from the shop url`() {
        assertEquals("2d12", ShortLink(url = "/shop/2d12").businessId)
        assertEquals("2d12", ShortLink(url = "/shop/2d12?tab=menu").businessId)
        assertNull(ShortLink(url = "/eat?s=x").businessId)
        assertNull(ShortLink(url = null).businessId)
    }

    @Test
    fun `shop open text`() {
        assertEquals(ShopOpenStatus.Open, ShopOpenStatus.fromText("yes\n"))
        assertEquals(
            ShopOpenStatus.Closed("Shop is not currently open (Shop is closed today)"),
            ShopOpenStatus.fromText("checkCommerceAllowTransaction: Shop is not currently open (Shop is closed today)"),
        )
    }
}
