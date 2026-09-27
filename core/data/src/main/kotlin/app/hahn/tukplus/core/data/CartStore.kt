package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.domain.CartStoreJson
import app.hahn.tukplus.core.domain.LineChange
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Menu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The one cart of the device (PLAN.md §5.7). It is kept in one JSON file, not in a
 * database: there is only one small cart, and a file needs no schema migrations.
 *
 * Each change writes a temporary file and then renames it, so a crash cannot leave
 * half a file. A file that cannot be read is moved to `cart.bad.json` and logged.
 */
class CartStore(private val file: File, private val log: TukLog) {
    private val _cart = MutableStateFlow(read())
    val cart: StateFlow<Cart?> = _cart.asStateFlow()

    private val _changes = MutableStateFlow<List<LineChange>>(emptyList())

    /** The changes of the last menu refresh that the user did not close yet. Kept in memory only. */
    val changes: StateFlow<List<LineChange>> = _changes.asStateFlow()

    fun dismissChanges() {
        _changes.value = emptyList()
    }

    /** Changes the cart with [change] and saves it. A null or empty result removes the cart. */
    @Synchronized
    fun update(change: (Cart?) -> Cart?) {
        val next = change(_cart.value)?.takeUnless { it.isEmpty }
        if (next == _cart.value) return
        _cart.value = next
        write(next)
    }

    fun clear() {
        update { null }
        _changes.value = emptyList()
    }

    /**
     * Brings the cart up to date with a newer [menu] of its shop (PLAN.md §5.7) and gives the
     * changes. Nothing happens when the cart is for another menu or already has this version.
     */
    @Synchronized
    fun reconcile(menu: Menu): List<LineChange> {
        val cart = _cart.value ?: return emptyList()
        if (cart.workflowId != menu.workflowId || cart.lines.all { it.menuVersion == menu.version && menu.version != null }) return emptyList()
        val result = CartRules.reconcile(cart, menu)
        update { result.cart }
        if (result.changes.isNotEmpty()) _changes.value = result.changes
        for (change in result.changes) {
            log.i("cart", "line_changed", "kind" to change::class.java.simpleName, "item_id" to (cart.lines.firstOrNull { it.lineId == change.lineId }?.itemId))
        }
        return result.changes
    }

    private fun read(): Cart? {
        if (!file.exists()) return null
        return try {
            CartStoreJson.decodeFromString(Cart.serializer(), file.readText())
        } catch (e: Exception) {
            log.e("cart", "read_failed", "error" to e.toString())
            file.renameTo(File(file.parentFile, "cart.bad.json"))
            null
        }
    }

    private fun write(cart: Cart?) {
        try {
            if (cart == null) {
                file.delete()
                return
            }
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(CartStoreJson.encodeToString(Cart.serializer(), cart))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            log.e("cart", "write_failed", "error" to e.toString())
        }
    }
}
