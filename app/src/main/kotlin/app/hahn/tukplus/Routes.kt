package app.hahn.tukplus

import android.net.Uri
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

/** Navigation routes. Arguments go to the view models through `SavedStateHandle`. */
object Routes {
    const val HOME = "home"
    const val EAT = "eat?q={q}&preset={preset}"
    const val SEARCH = "search?q={q}"
    const val SHOP = "shop/{id}?q={q}"
    const val SHOP_HANDLE = "handle/{handle}"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
    const val DEBUG = "debug"
    const val CART = "cart"
    const val WEB_CHECKOUT = "web_checkout"

    /** Routes with the bottom navigation bar. */
    val TOP_LEVEL = setOf(HOME, EAT, ORDERS, ACCOUNT)

    fun eat(q: String? = null, preset: String? = null) =
        "eat?q=${Uri.encode(q.orEmpty())}&preset=${Uri.encode(preset.orEmpty())}"

    fun search(q: String? = null) = "search?q=${Uri.encode(q.orEmpty())}"
    fun shop(id: String, menuSearch: String? = null) = "shop/${Uri.encode(id)}?q=${Uri.encode(menuSearch.orEmpty())}"
    fun shopHandle(handle: String) = "handle/${Uri.encode(handle.removePrefix("@"))}"
}

fun optionalArg(name: String): NamedNavArgument = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}
