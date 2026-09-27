package app.hahn.tukplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hahn.tukplus.R

/*
 * "Rainbow lorikeet" palette (docs/design.md): electric violet-blue, lime and orange.
 * The app always uses this palette; it does not use the Android wallpaper colors.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B2BE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF170065),
    secondary = Color(0xFF4A6700),
    onSecondary = Color(0xFFFFFFFF),
    // Lime: selected chips, the navigation indicator, the cart total.
    secondaryContainer = Color(0xFFB8F23A),
    onSecondaryContainer = Color(0xFF1B2600),
    tertiary = Color(0xFFA33A00),
    onTertiary = Color(0xFFFFFFFF),
    // Orange: badges such as "Top rated".
    tertiaryContainer = Color(0xFFFF6B2C),
    onTertiaryContainer = Color(0xFF2A0B00),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1A24),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1A24),
    surfaceVariant = Color(0xFFE4E1EE),
    onSurfaceVariant = Color(0xFF474554),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FC),
    surfaceContainer = Color(0xFFF0ECFA),
    surfaceContainerHigh = Color(0xFFEAE6F4),
    surfaceContainerHighest = Color(0xFFE4E1EE),
    outline = Color(0xFF787586),
    outlineVariant = Color(0xFFC9C4D6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BFFF),
    onPrimary = Color(0xFF2A0B9E),
    primaryContainer = Color(0xFF4B2BE8),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFB8F23A),
    onSecondary = Color(0xFF213600),
    secondaryContainer = Color(0xFFB8F23A),
    onSecondaryContainer = Color(0xFF1B2600),
    tertiary = Color(0xFFFFB596),
    onTertiary = Color(0xFF581D00),
    tertiaryContainer = Color(0xFFFF6B2C),
    onTertiaryContainer = Color(0xFF2A0B00),
    background = Color(0xFF13121B),
    onBackground = Color(0xFFE5E1EE),
    surface = Color(0xFF13121B),
    onSurface = Color(0xFFE5E1EE),
    surfaceVariant = Color(0xFF474554),
    onSurfaceVariant = Color(0xFFC9C4D6),
    surfaceContainerLowest = Color(0xFF0E0D16),
    surfaceContainerLow = Color(0xFF1B1A24),
    surfaceContainer = Color(0xFF1F1E28),
    surfaceContainerHigh = Color(0xFF2A2933),
    surfaceContainerHighest = Color(0xFF35343E),
    outline = Color(0xFF928F9F),
    outlineVariant = Color(0xFF474554),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private fun figtree(weight: Int) = Font(
    R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun bricolage(weight: Int) = Font(
    R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Text face (docs/design.md). */
val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))

/** Heading face (docs/design.md). */
val Bricolage = FontFamily(bricolage(500), bricolage(700))

private val base = Typography()

private fun TextStyle.text(weight: FontWeight? = null) = copy(fontFamily = Figtree, fontWeight = weight ?: fontWeight)
private fun TextStyle.heading() = copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold)

private val TukTypography = Typography(
    displayLarge = base.displayLarge.heading(),
    displayMedium = base.displayMedium.heading(),
    displaySmall = base.displaySmall.heading(),
    headlineLarge = base.headlineLarge.heading().copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.heading().copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.heading().copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = base.titleLarge.text(FontWeight.Bold),
    titleMedium = base.titleMedium.text(FontWeight.Bold).copy(fontSize = 17.sp),
    titleSmall = base.titleSmall.text(FontWeight.Bold).copy(fontSize = 15.sp),
    bodyLarge = base.bodyLarge.text(),
    bodyMedium = base.bodyMedium.text(),
    bodySmall = base.bodySmall.text().copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.text(FontWeight.Bold),
    labelMedium = base.labelMedium.text(FontWeight.SemiBold),
    labelSmall = base.labelSmall.text(FontWeight.SemiBold),
)

private val TukShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun TukPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = TukTypography,
        shapes = TukShapes,
        content = content,
    )
}
