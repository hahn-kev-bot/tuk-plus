package app.hahn.tukplus

import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.LogShare
import app.hahn.tukplus.ui.debug.DebugScreen
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.ui.eat.EatScreen
import app.hahn.tukplus.ui.home.HomeScreen
import app.hahn.tukplus.ui.search.SearchScreen
import app.hahn.tukplus.ui.shop.ShopScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.hahn.tukplus.ui.theme.TukPlusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var logging: AppLogging
    @Inject lateinit var logShare: LogShare

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TukPlusTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val nav = rememberNavController()
                    // Log each screen change in one place (PLAN.md §9.2).
                    DisposableEffect(nav) {
                        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                            logging.log.i("ui", "screen", "name" to destination.route)
                        }
                        nav.addOnDestinationChangedListener(listener)
                        onDispose { nav.removeOnDestinationChangedListener(listener) }
                    }
                    NavHost(navController = nav, startDestination = Routes.HOME) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                onTileAction = { action -> openTileAction(nav, action) },
                                onOpenShop = { nav.navigate(Routes.shop(it)) },
                                onOpenEat = { nav.navigate(Routes.eat()) },
                                onOpenSearch = { nav.navigate(Routes.search()) },
                                onOpenDebug = { nav.navigate(Routes.DEBUG) },
                            )
                        }
                        composable(
                            Routes.EAT,
                            arguments = listOf(optionalArg("q"), optionalArg("preset")),
                        ) {
                            EatScreen(onBack = { nav.popBackStack() }, onOpenShop = { nav.navigate(Routes.shop(it)) })
                        }
                        composable(Routes.SEARCH, arguments = listOf(optionalArg("q"))) {
                            SearchScreen(
                                onBack = { nav.popBackStack() },
                                onOpenShop = { id, menuSearch -> nav.navigate(Routes.shop(id, menuSearch)) },
                            )
                        }
                        composable(Routes.SHOP, arguments = listOf(navArgument("id") { type = NavType.StringType }, optionalArg("q"))) {
                            ShopScreen(onBack = { nav.popBackStack() })
                        }
                        composable(Routes.SHOP_HANDLE, arguments = listOf(navArgument("handle") { type = NavType.StringType })) {
                            ShopScreen(onBack = { nav.popBackStack() })
                        }
                        composable(Routes.DEBUG) { DebugScreen(onBack = { nav.popBackStack() }) }
                    }
                    CrashPrompt()
                }
            }
        }
    }

    /** What a Home tile does (api-reference §4). */
    private fun openTileAction(nav: NavController, action: TileAction) {
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
    @androidx.compose.runtime.Composable
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
