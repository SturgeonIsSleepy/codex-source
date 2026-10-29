package cp.player.app

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import cp.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Notification-material treatment, computed once per cover/theme; no live GPU blur. */
internal object LyricFrost {
    private val cache = LruCache<String, Bitmap>(4)
    suspend fun load(context: android.content.Context, artwork: String, night: Boolean): Bitmap? {
        val key = "$night:$artwork"
        synchronized(cache) { cache.get(key)?.let { return it } }
        val request = ImageRequest.Builder(context).data(artwork).size(96).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val bitmap = withContext(Dispatchers.Default) { create(result.image.toBitmap(), night) }
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }
    fun create(source: Bitmap, night: Boolean): Bitmap {
        val width = 72; val height = 72
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        var pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()
        repeat(3) { pixels = pass(pass(pixels, width, height, true), width, height, false) }
        val base = if (night) intArrayOf(67, 61, 81) else intArrayOf(250, 245, 253)
        val mix = if (night) .42f else .23f
        for (i in pixels.indices) {
            val color = pixels[i]
            val rgb = floatArrayOf(AndroidColor.red(color) * mix + base[0] * (1 - mix),
                AndroidColor.green(color) * mix + base[1] * (1 - mix), AndroidColor.blue(color) * mix + base[2] * (1 - mix))
            if (night) {
                val ceiling = rgb.maxOrNull()!!.coerceAtLeast(112f)
                for (channel in rgb.indices) rgb[channel] *= 112f / ceiling
            }
            pixels[i] = AndroidColor.rgb(rgb[0].toInt(), rgb[1].toInt(), rgb[2].toInt())
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    private fun pass(source: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val target = IntArray(source.size)
        val length = if (horizontal) width else height
        val count = if (horizontal) height else width
        val radius = 8
        fun index(line: Int, step: Int) = if (horizontal) line * width + step.coerceIn(0, width - 1) else step.coerceIn(0, height - 1) * width + line
        for (line in 0 until count) {
            var red = 0; var green = 0; var blue = 0
            for (step in -radius..radius) {
                val value = source[index(line, step)]
                red += AndroidColor.red(value); green += AndroidColor.green(value); blue += AndroidColor.blue(value)
            }
            for (step in 0 until length) {
                target[index(line, step)] = AndroidColor.rgb(red / 17, green / 17, blue / 17)
                val old = source[index(line, step - radius)]; val next = source[index(line, step + radius + 1)]
                red += AndroidColor.red(next) - AndroidColor.red(old)
                green += AndroidColor.green(next) - AndroidColor.green(old)
                blue += AndroidColor.blue(next) - AndroidColor.blue(old)
            }
        }
        return target
    }
}

@Composable
internal fun LyricFrostBackground(song: Song?, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val base = MaterialTheme.colorScheme.background
    val night = base.luminance() < .3f
    val artwork = songArtwork(song)
    val bitmap by produceState<Bitmap?>(null, artwork, night) {
        value = null
        if (artwork != null) value = try { LyricFrost.load(context, artwork, night) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { null }
    }
    Box(modifier.background(base).testTag("lyric-frost")) {
        Crossfade(bitmap, animationSpec = tween(320), label = "cover-material") { image ->
            if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(base.copy(alpha = .12f), Color.Transparent, base.copy(alpha = .18f)))))
    }
}
