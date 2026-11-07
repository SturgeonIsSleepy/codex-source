package cp.player.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*

internal object AppMotion {
    const val Fast = 120
    const val Page = 180
    const val Artwork = 220
    const val Scroll = 240
    val Ease = CubicBezierEasing(.2f, 0f, 0f, 1f)
    fun <T> page() = tween<T>(Page, easing = Ease)
    fun <T> fast() = tween<T>(Fast, easing = Ease)
    fun <T> artwork() = tween<T>(Artwork, easing = Ease)
}

/** First window entry has one owner; nested destinations only receive a subtle content transition. */
@Composable
internal fun rememberSceneEntrance(identity: Any): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val first = remember { identity }
    val alpha = remember(identity) { Animatable(if (identity == first) 1f else .92f) }
    LaunchedEffect(identity) { alpha.animateTo(1f, AppMotion.fast()) }
    return alpha
}
