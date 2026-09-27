package app.hahn.tukplus.ui.debug

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.BuildConfig
import app.hahn.tukplus.R
import app.hahn.tukplus.logging.AppLogging
import app.hahn.tukplus.logging.LogShare
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class LogDay(val day: LocalDate, val bytes: Long)

data class DebugState(val sessionId: String, val days: List<LogDay> = emptyList(), val error: String? = null)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val logging: AppLogging,
    private val share: LogShare,
) : ViewModel() {
    private val _state = MutableStateFlow(DebugState(logging.log.sessionId))
    val state: StateFlow<DebugState> = _state
    private val _intents = Channel<Intent>(Channel.BUFFERED)
    val intents = _intents.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val days = withContext(Dispatchers.IO) {
                logging.store.days().map { day -> LogDay(day, logging.store.filesFor(day).sumOf { it.length() }) }
            }
            _state.value = _state.value.copy(days = days)
        }
    }

    fun share(what: LogShare.What) {
        viewModelScope.launch {
            try {
                _intents.send(share.prepare(what))
                _state.value = _state.value.copy(error = null)
            } catch (e: Exception) {
                logging.log.e("debug", "logs_export_failed", "error" to e)
                _state.value = _state.value.copy(error = e.message ?: e::class.java.simpleName)
            }
        }
    }

    fun shareToday() = share(LogShare.What.Days(listOf(LocalDate.now(logging.clock))))
    fun shareWeek() = share(LogShare.What.Days((0L..6L).map { LocalDate.now(logging.clock).minusDays(it) }))
    fun shareSession() = share(LogShare.What.ThisSession)
    fun shareDay(day: LocalDate) = share(LogShare.What.Days(listOf(day)))

    fun testCrash(): Nothing {
        logging.log.w("debug", "test_crash")
        throw IllegalStateException("Test crash from the Debug screen")
    }
}

@Composable
fun DebugScreen(onBack: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.intents.collect { context.startActivity(it) } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.debug_back)) }
            Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.debug_session, state.sessionId))
        Text(
            pluralStringResource(
                R.plurals.debug_logs_size,
                state.days.size,
                state.days.size,
                Formatter.formatShortFileSize(context, state.days.sumOf { it.bytes }),
            ),
        )
        Button(onClick = viewModel::shareSession, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_share_session)) }
        Button(onClick = viewModel::shareToday, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_share_today)) }
        Button(onClick = viewModel::shareWeek, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_share_week)) }
        state.error?.let { Text(stringResource(R.string.debug_share_failed, it), color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        Text(stringResource(R.string.debug_days), style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(state.days, key = { it.day.toString() }) { day ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.day}  ·  ${Formatter.formatShortFileSize(context, day.bytes)}", Modifier.weight(1f))
                    TextButton(onClick = { viewModel.shareDay(day.day) }) { Text(stringResource(R.string.debug_share_day)) }
                }
            }
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(onClick = { viewModel.testCrash() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.debug_test_crash))
            }
        }
    }
}
