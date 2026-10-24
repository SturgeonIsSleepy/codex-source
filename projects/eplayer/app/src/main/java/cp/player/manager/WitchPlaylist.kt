package cp.player.manager

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import cp.player.model.Song
import java.io.File

/** The original playlist remains available without a provider, alongside the full CPPlayer library. */
object WitchPlaylist {
    val songs = listOf(
        Song("witch_0", "灰色のサーガ", "ChouCho", album = "魔女の旅々", albumArtUrl = "file:///android_asset/player-reference.jpg"),
        Song("witch_1", "リテラチュア", "上田麗奈", album = "魔女の旅々", albumArtUrl = "file:///android_asset/player-reference.jpg")
    )
    private fun file(context: Context, index: Int) = File(context.filesDir, "witch/audio-$index")
    fun isImported(context: Context, index: Int) = file(context, index).isFile
    fun available(context: Context) = songs.filterIndexed { index, _ -> isImported(context, index) }
    fun uri(context: Context, id: String): Uri? {
        val index = songs.indexOfFirst { it.id == id }
        if (index < 0 || !isImported(context, index)) return null
        val recording = file(context, index)
        val inode = android.system.Os.stat(recording.absolutePath).st_ino
        // A replacement must invalidate both Media3's URI cache and the artwork rhythm cache.
        val revision = "$inode-${recording.lastModified()}-${recording.length()}"
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", recording)
            .buildUpon().appendQueryParameter("recording", revision).build()
    }
    fun import(context: Context, index: Int, uri: Uri) {
        require(index in songs.indices)
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
