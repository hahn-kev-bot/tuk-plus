package app.hahn.tukplus.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R

/** Orders tab. Orders come in phase 5. */
@Composable
fun OrdersScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_orders), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp))
        Notice(stringResource(R.string.orders_later))
    }
}

/** Account tab. Login comes in phase 3. The Debug screen (logs) is here. */
@Composable
fun AccountScreen(onOpenDebug: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.nav_account), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 12.dp))
        Notice(stringResource(R.string.account_later))
        FilledTonalButton(onClick = onOpenDebug, shape = RoundedCornerShape(24.dp)) { Text(stringResource(R.string.account_debug)) }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
    }
}
