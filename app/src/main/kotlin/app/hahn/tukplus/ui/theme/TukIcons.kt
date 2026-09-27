package app.hahn.tukplus.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Line icons of the design (24 × 24, round stroke). Made from SVG path data, so the
 * app needs no icon library. Tint them with `Icon(tint = …)`.
 */
object TukIcons {
    private fun icon(name: String, vararg paths: String, strokeWidth: Float = 2f, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = if (filled) SolidColor(androidx.compose.ui.graphics.Color.Black) else null,
                    stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val CIRCLE_SEARCH = "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z"

    val Search = icon("search", CIRCLE_SEARCH, "M16.5 16.5L21 21")
    val Pin = icon("pin", "M12 21s-6-5.3-6-11a6 6 0 1 1 12 0c0 5.7-6 11-6 11z", "M14 10a2 2 0 1 1-4 0a2 2 0 1 1 4 0z")
    val ChevronDown = icon("chevron_down", "M6 9l6 6 6-6")
    val Refresh = icon("refresh", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5", strokeWidth = 2.4f)
    val Person = icon("person", "M16 8a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6")
    val Home = icon("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val HomeFilled = icon("home_filled", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", filled = true, strokeWidth = 1.6f)
    val Grid = icon("grid", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val List = icon("list", "M9 6h11M9 12h11M9 18h11", "M4 6h.01M4 12h.01M4 18h.01", strokeWidth = 2.2f)
    val Cart = icon("cart", "M5 8h14l-1.2 11a2 2 0 0 1-2 1.8H8.2a2 2 0 0 1-2-1.8z", "M9 8V6a3 3 0 0 1 6 0v2")
    val Trash = icon("trash", "M4 7h16", "M10 11v6M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3")
    val Receipt = icon("receipt", "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h6")
    val Back = icon("back", "M19 12H5", "M11 18l-6-6 6-6", strokeWidth = 2.2f)
    val Close = icon("close", "M6 6l12 12M18 6L6 18", strokeWidth = 2.2f)
    val Plus = icon("plus", "M12 5v14M5 12h14", strokeWidth = 2.6f)
    val Minus = icon("minus", "M5 12h14", strokeWidth = 2.4f)
    val Check = icon("check", "M5 12l5 5 9-10", strokeWidth = 2.4f)
    val Clock = icon("clock", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 7v5l3 2", strokeWidth = 2.4f)
}
