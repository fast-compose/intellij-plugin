package ma7moud3ly.fast.compose.intellij

import kotlin.math.roundToInt

/** A screen size that previews are laid out at, in dp. */
enum class PreviewDevice(val title: String, val widthDp: Int, val heightDp: Int) {
    Phone("Phone", 411, 891),
    Foldable("Foldable", 673, 841),
    Tablet("Tablet", 1280, 800),
    Desktop("Desktop", 1920, 1080),
}

/** Scales offered in the zoom menu, besides Fit. */
internal val ZOOM_LEVELS = listOf(0.5f, 0.75f, 1f, 1.5f)
internal const val MIN_ZOOM = 0.1f
internal const val MAX_ZOOM = 4f

/** [zoom] as a percentage, e.g. "75 %". */
internal fun percent(zoom: Float) = "${(zoom * 100).roundToInt()} %"
