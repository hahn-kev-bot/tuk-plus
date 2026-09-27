package app.hahn.tukplus.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The mix of picture shapes (docs/design.md): rounded square, circle, "leaf"
 * (two large and two small corners) and "arch" (round top).
 */
object PictureShapes {
    val Squircle: Shape = RoundedCornerShape(28)
    val Circle: Shape = CircleShape
    val Leaf: Shape = RoundedCornerShape(topStartPercent = 36, topEndPercent = 10, bottomEndPercent = 36, bottomStartPercent = 10)
    val Arch: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 18, bottomStartPercent = 18)

    private val all = listOf(Squircle, Circle, Leaf, Arch)

    /** A shape for [key] (for example an item id). The same key always gets the same shape. */
    fun forKey(key: String): Shape = all[Math.floorMod(key.hashCode(), all.size)]

    /** A shape by position, so that neighbours differ. */
    fun forIndex(index: Int): Shape = all[Math.floorMod(index, all.size)]

    /** Tile shapes for text tiles (cuisines). */
    val tiles: List<Shape> = listOf(RoundedCornerShape(24.dp), RoundedCornerShape(36.dp), RoundedCornerShape(24.dp, 8.dp, 24.dp, 8.dp))
}
