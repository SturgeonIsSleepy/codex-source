package cp.player.core.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.model.Song
import java.io.File
import java.util.UUID

class LibraryStore(private val context: Context) {
    private val gson = Gson()
    private val index = File(context.filesDir, "eplayer-library.json")
    private val type = object : TypeToken<List<Track>>() {}.type
    private val imports = if (index.exists()) runCatching { gson.fromJson<List<Track>>(index.readText(), type) }.getOrNull().orEmpty().toMutableList() else mutableListOf()

    @Synchronized fun imported(): List<Track> {
        // Retire preset rows, preserving audio the user explicitly imported in earlier versions.
        val migrated = context.getSharedPreferences("legacy_audio_migration", Context.MODE_PRIVATE)
        for (slot in 0..1) if (!migrated.getBoolean("slot_$slot", false)) {
            val uri = WitchPlaylist.uri(context, "witch_$slot")
            if (uri == null || runCatching { importAudio(uri) }.isSuccess)
                migrated.edit().putBoolean("slot_$slot", true).apply()
        }
        return imports.toList()
    }
    @Synchronized fun importAudio(uri: Uri, metadataOverride: Song? = null, source: String? = null): Track {
        val id = "import_${UUID.randomUUID()}"
        val directory = File(context.filesDir, "audio").apply { mkdirs() }
        val temporary = File(directory, "$id.part")
        val destination = File(directory, "$id.audio")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取音频" }
                temporary.outputStream().use { require(input.copyTo(it) > 0) { "文件为空" } }
            }
            val metadata = MediaMetadataRetriever()
            val song = try {
                metadata.setDataSource(temporary.absolutePath)
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                require(duration > 0) { "请选择有效音频" }
                val displayName = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                Song(id, metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
                    ?: displayName?.substringBeforeLast('.') ?: "未命名",
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeUnless { it == "<unknown>" }.orEmpty(),
                    album = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(), durationMs = duration)
            } finally { metadata.release() }
            check(temporary.renameTo(destination)) { "保存失败" }
            val finalSong = metadataOverride?.copy(id = id, durationMs = song.durationMs) ?: song
            val track = Track(finalSong, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination).toString(), source, metadataOverride?.id)
            imports.add(0, track)
            try { save() } catch (failure: Exception) { imports.remove(track); destination.delete(); throw failure }
            return track
        } finally { temporary.delete() }
    }

    @Synchronized fun removeImported(track: Track) {
        val index = imports.indexOfFirst { it.song.id == track.song.id }
        require(index >= 0) { "此文件不属于导入曲库" }
        val removed = imports.removeAt(index)
        try { save() } catch (error: Exception) { imports.add(index, removed); throw error }
        File(context.filesDir, "audio/${track.song.id}.audio").delete()
    }

    fun scan(): List<Track> {
        val result = mutableListOf<Track>()
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION),
            "${MediaStore.Audio.Media.DURATION} > 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                result += Track(Song("local_$id", cursor.getString(1).orEmpty(), cursor.getString(2)?.takeUnless { it == "<unknown>" }.orEmpty(),
                    album = cursor.getString(3).orEmpty(), durationMs = cursor.getLong(4)),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString())
            }
        }
        return result
    }
    private fun save() {
        val temporary = File(index.parentFile, "eplayer-library.tmp")
        temporary.writeText(gson.toJson(imports))
        check(temporary.renameTo(index)) { "无法保存曲库" }
    }
}
