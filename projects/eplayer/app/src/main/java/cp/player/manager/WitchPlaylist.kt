package cp.player.manager

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Compatibility for explicitly imported audio in older installations; no preset songs are exposed. */
object WitchPlaylist {
    private val legacyIds = listOf("witch_0", "witch_1")
    private fun file(context: Context, index: Int) = File(context.filesDir, "witch/audio-$index")
    fun isImported(context: Context, index: Int) = file(context, index).isFile
    fun uri(context: Context, id: String): Uri? {
        val index = legacyIds.indexOf(id)
        if (index < 0 || !isImported(context, index)) return null
        val recording = file(context, index)
        val inode = android.system.Os.stat(recording.absolutePath).st_ino
        // A replacement must invalidate both Media3's URI cache and the artwork rhythm cache.
        val revision = "$inode-${recording.lastModified()}-${recording.length()}"
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", recording)
            .buildUpon().appendQueryParameter("recording", revision).build()
    }
    fun import(context: Context, index: Int, uri: Uri) {
        require(index in legacyIds.indices)
        val destination = file(context, index)
        destination.parentFile!!.mkdirs()
        val temporary = File.createTempFile("import-", ".audio", destination.parentFile)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取音频" }
                temporary.outputStream().use { output ->
                    val bytes = input.copyTo(output)
                    require(bytes > 0) { "音频为空" }
                }
            }
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(temporary.absolutePath)
                require((metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "请选择有效音频" }
            } finally { metadata.release() }
            check(temporary.renameTo(destination)) { "保存音频失败" }
        } finally { temporary.delete() }
    }
}
