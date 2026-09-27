package app.hahn.tukplus

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.LogShare
import app.hahn.tukplus.ui.debug.DebugScreen
import app.hahn.tukplus.ui.home.HomeScreen
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
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(onOpenDebug = {
                                logging.log.i("ui", "screen", "name" to "debug")
                                nav.navigate("debug")
                            })
                        }
                        composable("debug") { DebugScreen(onBack = { nav.popBackStack() }) }
                    }
                    CrashPrompt()
                }
            }
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
                        startActivity(logShare.prepare(LogShare.What.Days(listOf(today, today.minusDays(1)))))
                    }
                }) { Text(stringResource(R.string.crash_share)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.crash_not_now)) } },
        )
    }
}
