package app.hahn.tukplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Yellow = Color(0xFFFFBD20)
private val Ink = Color(0xFF1C1C1C)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color.White,
    primaryContainer = Yellow,
    onPrimaryContainer = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Yellow,
    onPrimary = Ink,
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFE08F),
)

@Composable
fun TukPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
