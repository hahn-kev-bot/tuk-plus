package app.hahn.tukplus.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageBlobsTest {
    private val json = """
        [
          {"id":"2","parent_id":"Home_2","blob_type":"list","data":{"name":"B","tag":"@b","rank":2,"pic":"p2","hidden":true}},
          {"id":"1","parent_id":"Home_2","blob_type":"list","data":{"name":"A","tag":"/eat?s=x","rank":"1","pic":"p1","pic_th":"p1-th","pic_en":"","show_name":true}},
          {"id":"t","parent_id":"Home_2","blob_type":"list_title","data":{"page":"Home_2","title":{"en":"Hot","th":"ฮอต"},
            "settings":{"shop_open":true,"time_slot":{"start":"0700","end":2300}}}}
        ]
    """.trimIndent()

    @Test
    fun `row has sorted tiles and the title`() {
        val row = PageBlobs.toRow("Home_2", TukJson.decodeFromString<List<Blob>>(json))
        assertEquals(listOf("A", "B"), row.tiles.map { it.name })
        assertEquals("Hot", row.title?.titleFor("en"))
        assertEquals("Hot", row.title?.titleFor("ja"))
        assertEquals(true, row.title?.settings?.shopOpen)
        assertEquals("2300", row.title?.settings?.timeSlot?.end)
    }

    @Test
    fun `tile flags and localized pictures`() {
        val row = PageBlobs.toRow("Home_2", TukJson.decodeFromString<List<Blob>>(json))
        val a = row.tiles.first()
        assertTrue(a.showName)
        assertEquals("p1-th", a.picFor("th"))
        assertEquals("p1", a.picFor("en")) // An empty pic_en is ignored.
        assertTrue(row.tiles.last().hidden)
    }
}
