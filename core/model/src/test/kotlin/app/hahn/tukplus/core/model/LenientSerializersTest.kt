package app.hahn.tukplus.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LenientSerializersTest {

    @Test
    fun `price is read from a string, a number or null`() {
        val items = TukJson.decodeFromString<List<OptionItem>>(
            """[{"id":"a","price":"50"},{"id":"b","price":0},{"id":"c","price":null},{"id":"d"},{"id":"e","price":12.5}]""",
        )
        assertEquals(listOf("50", "0", null, null, "12.5"), items.map { it.price })
    }

    @Test
    fun `numbers are read from numeric strings`() {
        val data = TukJson.decodeFromString<WorkflowData>("""{"vat":"0.07","min_order":"150","paused":"true"}""")
        assertEquals(0.07, data.vat)
        assertEquals(150, data.minOrder)
        assertEquals(true, data.paused)
    }

    @Test
    fun `wrong types become null instead of failing`() {
        val data = TukJson.decodeFromString<WorkflowData>("""{"vat":{"x":1},"min_order":"abc","paused":[1]}""")
        assertNull(data.vat)
        assertNull(data.minOrder)
        assertNull(data.paused)
    }

    @Test
    fun `parseInt works like JavaScript`() {
        assertEquals(380, parseIntLikeJavaScript("380"))
        assertEquals(12, parseIntLikeJavaScript("12.9"))
        assertEquals(5, parseIntLikeJavaScript("  5 baht"))
        assertEquals(-3, parseIntLikeJavaScript("-3"))
        assertNull(parseIntLikeJavaScript("baht 5"))
        assertNull(parseIntLikeJavaScript(""))
    }
}
