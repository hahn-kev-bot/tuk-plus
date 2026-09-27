package app.hahn.tukplus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.core.data.CartQuotes
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.LogShare
import app.hahn.tukplus.ui.account.AccountScreen
import app.hahn.tukplus.ui.account.OrdersScreen
import app.hahn.tukplus.ui.cart.CartBar
import app.hahn.tukplus.ui.cart.CartScreen
import app.hahn.tukplus.ui.debug.DebugScreen
import app.hahn.tukplus.ui.eat.EatScreen
import app.hahn.tukplus.ui.home.HomeScreen
import app.hahn.tukplus.ui.search.SearchScreen
import app.hahn.tukplus.ui.shop.ShopScreen
import app.hahn.tukplus.ui.theme.TukIcons
import app.hahn.tukplus.ui.theme.TukPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private data class NavTab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector, val go: () -> String)

private val TABS = listOf(
    NavTab(Routes.HOME, R.string.nav_home, TukIcons.Home, TukIcons.HomeFilled) { Routes.HOME },
    NavTab(Routes.EAT, R.string.nav_shops, TukIcons.Grid, TukIcons.Grid) { Routes.eat() },
    NavTab(Routes.ORDERS, R.string.nav_orders, TukIcons.Receipt, TukIcons.Receipt) { Routes.ORDERS },
    NavTab(Routes.ACCOUNT, R.string.nav_account, TukIcons.Person, TukIcons.Person) { Routes.ACCOUNT },
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var logging: AppLogging
    @Inject lateinit var logShare: LogShare
    @Inject lateinit var cartQuotes: CartQuotes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TukPlusTheme {
                val nav = rememberNavController()
                // Log each screen change in one place (PLAN.md §9.2).
                DisposableEffect(nav) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        logging.log.i("ui", "screen", "name" to destination.route)
                    }
                    nav.addOnDestinationChangedListener(listener)
                    onDispose { nav.removeOnDestinationChangedListener(listener) }
                }
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val cart by cartQuotes.state.collectAsStateWithLifecycle()
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    bottomBar = {
                        if (route in Routes.TOP_LEVEL) {
                            Column {
                                cart?.let { CartBar(it, showShop = true, onClick = { nav.navigate(Routes.CART) }) }
                                BottomBar(nav, route)
                            }
                        }
                    },
                ) { padding ->
                    NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                onTileAction = { action -> openTileAction(nav, action) },
                                onOpenShop = { nav.navigate(Routes.shop(it)) },
                                onOpenSearch = { nav.navigate(Routes.search()) },
                                onOpenAccount = { goToTab(nav, Routes.ACCOUNT) },
                            )
                        }
                        composable(Routes.EAT, arguments = listOf(optionalArg("q"), optionalArg("preset"))) {
                            EatScreen(onOpenShop = { nav.navigate(Routes.shop(it)) }, onOpenSearch = { nav.navigate(Routes.search()) })
                        }
                        composable(Routes.SEARCH, arguments = listOf(optionalArg("q"))) {
                            SearchScreen(
                                onBack = { nav.popBackStack() },
                                onOpenShop = { id, menuSearch -> nav.navigate(Routes.shop(id, menuSearch)) },
                            )
                        }
                        composable(Routes.SHOP, arguments = listOf(navArgument("id") { type = NavType.StringType }, optionalArg("q"))) {
                            ShopScreen(onBack = { nav.popBackStack() }, onOpenCart = { nav.navigate(Routes.CART) })
                        }
                        composable(Routes.SHOP_HANDLE, arguments = listOf(navArgument("handle") { type = NavType.StringType })) {
                            ShopScreen(onBack = { nav.popBackStack() }, onOpenCart = { nav.navigate(Routes.CART) })
                        }
                        composable(Routes.CART) {
                            CartScreen(onBack = { nav.popBackStack() }, onOpenShop = { id -> openShopFromCart(nav, id) })
                        }
                        composable(Routes.ORDERS) { OrdersScreen() }
                        composable(Routes.ACCOUNT) { AccountScreen(onOpenDebug = { nav.navigate(Routes.DEBUG) }) }
                        composable(Routes.DEBUG) { DebugScreen(onBack = { nav.popBackStack() }) }
                    }
                }
                CrashPrompt()
            }
        }
    }

    @Composable
    private fun BottomBar(nav: NavHostController, route: String?) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            for (tab in TABS) {
                val selected = route == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) goToTab(nav, tab.go()) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        }
    }

    /**
     * Opens a top-level tab and keeps one copy of each tab on the back stack.
     *
     * Home is the start screen. Screens that were opened on top of Home (for example a
     * shop list from a Home filter) are saved under Home's id when the user leaves, so
     * "restore state" for Home would show them again. So Home only goes back to itself.
     */
    private fun goToTab(nav: NavHostController, target: String) {
        if (target == Routes.HOME) {
            if (!nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)) nav.navigate(Routes.HOME)
            return
        }
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** "Add more" in the cart: back to the shop if the cart was opened from it, else open the shop. */
    private fun openShopFromCart(nav: NavHostController, businessId: String) {
        val previous = nav.previousBackStackEntry
        val fromShop = previous?.destination?.route in setOf(Routes.SHOP, Routes.SHOP_HANDLE)
        if (fromShop) nav.popBackStack() else nav.navigate(Routes.shop(businessId)) { popUpTo(Routes.CART) { inclusive = true } }
    }

    /** What a Home tile or quick filter does (api-reference §4). */
    private fun openTileAction(nav: NavHostController, action: TileAction) {
        logging.log.i("ui", "tile", "action" to action::class.java.simpleName)
        when (action) {
            is TileAction.ShopHandle -> nav.navigate(Routes.shopHandle(action.handle))
            is TileAction.ShopId -> nav.navigate(Routes.shop(action.businessId))
            is TileAction.EatSearch -> nav.navigate(Routes.eat(q = action.text))
            is TileAction.EatPreset -> nav.navigate(Routes.eat(preset = action.preset.name))
            is TileAction.Search -> nav.navigate(Routes.search(action.text))
            is TileAction.External -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, action.url.toUri())) }
            TileAction.None -> Unit
        }
    }

    /** After a crash, ask the user to share the logs of yesterday and today (PLAN.md §9.1). */
    @Composable
    private fun CrashPrompt() {
        var show by remember { mutableStateOf(logging.crashedLastTime) }
        val scope = rememberCoroutineScope()
        if (!show) return
        val close = {
            show = false
            logging.clearCrashMarker()
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.crash_title)) },
            text = { Text(stringResource(R.string.crash_text)) },
            confirmButton = {
                TextButton(onClick = {
                    close()
                    scope.launch {
                        val today = LocalDate.now(logging.clock)
                        startActivity(logShare.prepare(LogShare.What.Days(listOf(today, today.minusDays(1)), reason = "after a crash")))
                    }
                }) { Text(stringResource(R.string.crash_share)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.crash_not_now)) } },
        )
    }
}
