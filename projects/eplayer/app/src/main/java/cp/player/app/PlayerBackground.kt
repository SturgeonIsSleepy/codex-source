package cp.player.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal object PlayerBackgroundStore {
    suspend fun import(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "player-backgrounds").apply { mkdirs() }
        val input = File.createTempFile("import-", ".tmp", directory)
        val output = File(directory, "background-${System.nanoTime()}.webp")
        try {
            context.contentResolver.openInputStream(uri)?.use { source -> input.outputStream().use { target ->
                val buffer = ByteArray(32768); var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer); if (count < 0) break
                    size += count; check(size <= 25L * 1024 * 1024) { "图片不能超过 25 MB" }
                    target.write(buffer, 0, count)
                }
            } } ?: error("无法读取图片")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.path, options)
            check(options.outWidth > 0 && options.outHeight > 0) { "无法识别这张图片" }
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(input.path, options) ?: error("无法读取图片")
            try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.WEBP, 84, it)) { "图片保存失败" } } }
            finally { bitmap.recycle() }
            output.absolutePath
        } catch (error: Throwable) { output.delete(); throw error }
        finally { input.delete() }
    }
    fun clean(context: Context, keep: String?) {
        File(context.filesDir, "player-backgrounds").listFiles()?.filter { it.name.startsWith("background-") && it.extension == "webp" && it.path != keep }?.forEach { it.delete() }
    }
}

@Composable
internal fun PlayerBackdrop(path: String?, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    Box(modifier.background(base)) {
        if (path != null) {
            AsyncImage(ImageRequest.Builder(context).data(File(path)).size(1200).build(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(base.copy(alpha = if (base.luminance() < .3f) .74f else .93f)))
        }
    }
}
