package app.hahn.tukplus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.R
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.ApiResult
import app.hahn.tukplus.core.network.TukApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackendState {
    data object Checking : BackendState
    data class Ok(val version: String, val ms: Long) : BackendState
    data class Error(val message: String) : BackendState
}

/** Phase 0 home: checks that the Tuk API answers. Phase 1 replaces this screen. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: TukApi,
    private val log: TukLog,
) : ViewModel() {
    private val _backend = MutableStateFlow<BackendState>(BackendState.Checking)
    val backend: StateFlow<BackendState> = _backend

    init {
        check()
    }

    fun check() {
        _backend.value = BackendState.Checking
        viewModelScope.launch {
            _backend.value = when (val result = api.version()) {
                is ApiResult.Success -> {
                    log.i("app", "backend_version", "version" to result.value.trim())
                    BackendState.Ok(result.value.trim(), result.durationMs)
                }
                is ApiResult.Failure -> BackendState.Error(result.message)
            }
        }
    }
}

@Composable
fun HomeScreen(onOpenDebug: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val backend by viewModel.backend.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.home_phase), style = MaterialTheme.typography.bodyMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_backend), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val state = backend) {
                        BackendState.Checking -> stringResource(R.string.home_backend_checking)
                        is BackendState.Ok -> stringResource(R.string.home_backend_version, state.version, state.ms)
                        is BackendState.Error -> stringResource(R.string.home_backend_error, state.message)
                    },
                )
                OutlinedButton(onClick = viewModel::check, enabled = backend !is BackendState.Checking) {
                    Text(stringResource(R.string.home_check_again))
                }
            }
        }
        Button(onClick = onOpenDebug) { Text(stringResource(R.string.home_open_debug)) }
    }
}
