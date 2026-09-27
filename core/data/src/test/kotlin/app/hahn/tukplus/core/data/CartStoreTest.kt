package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.logging.TukLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CartStoreTest {
    private val dir: File = Files.createTempDirectory("cart").toFile()
    private val file = File(dir, "cart.json")
    private val line = CartLine("L0", "water", 2, item = JsonObject(mapOf("id" to JsonPrimitive("water"))))
    private val cart = Cart("b1", "wf1", "Shop", createdAt = 1L, lines = listOf(line))

    @Test
    fun `cart is kept after a restart`() {
        CartStore(file, TukLog.NONE).update { cart }
        assertEquals(cart, CartStore(file, TukLog.NONE).cart.value)
    }

    @Test
    fun `an empty cart removes the file`() {
        val store = CartStore(file, TukLog.NONE)
        store.update { cart }
        store.update { it?.copy(lines = emptyList()) }
        assertNull(store.cart.value)
        assertTrue(!file.exists())
    }

    @Test
    fun `a bad file is moved away`() {
        file.writeText("{not json")
        assertNull(CartStore(file, TukLog.NONE).cart.value)
        assertTrue(File(dir, "cart.bad.json").exists())
    }
}
