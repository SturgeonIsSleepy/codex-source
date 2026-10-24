package cp.player.app

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.manager.WitchPlaylist
import cp.player.model.Song
import cp.player.util.toMediaItem
import cp.player.util.toSong
import java.io.File
import java.util.UUID

data class Track(val song: Song, val uri: String? = null, val source: String? = null, val remoteId: String? = null) {
    fun mediaItem(quality: String = "standard") = song.toMediaItem(uri?.let(Uri::parse), quality).let { item ->
        if (source == null) item else {
            val builder = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
                    putString("source", source); putString("remoteId", remoteId ?: song.id)
                }).build())
            if (uri == null) builder.setUri(item.localConfiguration!!.uri.buildUpon().appendQueryParameter("source", source).build())
            builder.build()
        }
    }
}

class LibraryStore(private val context: Context) {
    private val gson = Gson()
    private val index = File(context.filesDir, "eplayer-library.json")
    private val type = object : TypeToken<List<Track>>() {}.type
    private val imports = if (index.exists()) runCatching { gson.fromJson<List<Track>>(index.readText(), type) }.getOrNull().orEmpty().toMutableList() else mutableListOf()

    @Synchronized fun imported(): List<Track> = imports.toList()
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

/** Persists at queue/transport changes; never writes progress every frame. */
object QueueStore {
    private val gson = Gson()
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { work -> Thread(work, "ePlayer-state").apply { isDaemon = true } }
    fun save(context: Context, player: Player, queueChanged: Boolean = true) {
        val prefs = context.getSharedPreferences("eplayer_queue", Context.MODE_PRIVATE)
        prefs.edit().putInt("index", player.currentMediaItemIndex).putLong("position", player.currentPosition).apply()
        if (!queueChanged) return
        val items = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            Track(item.toSong(), item.localConfiguration?.uri?.toString(), item.mediaMetadata.extras?.getString("source"), item.mediaMetadata.extras?.getString("remoteId"))
        }
        writer.execute { prefs.edit().putString("items", gson.toJson(items)).apply() }
    }
    fun restore(context: Context, player: Player) {
        val prefs = context.getSharedPreferences("eplayer_queue", Context.MODE_PRIVATE)
        val json = prefs.getString("items", null) ?: return
        val items = runCatching { gson.fromJson<List<Track>>(json, object : TypeToken<List<Track>>() {}.type) }.getOrNull().orEmpty()
        val valid = items.filter { it.uri != null && (!it.song.id.startsWith("witch_") || WitchPlaylist.uri(context, it.song.id) != null) }
        if (valid.isEmpty()) return
        player.setMediaItems(valid.map { it.mediaItem() }, prefs.getInt("index", 0).coerceIn(valid.indices), prefs.getLong("position", 0).coerceAtLeast(0))
        // Opening the app must not silently start playback or networking.
    }
}

object RecentStore {
    private val gson = Gson()
    private val type = object : TypeToken<List<Track>>() {}.type
    fun read(context: Context): List<Track> = runCatching {
        gson.fromJson<List<Track>>(context.getSharedPreferences("eplayer_recent", 0).getString("items", "[]"), type)
    }.getOrNull().orEmpty()
    fun add(context: Context, player: Player) {
        val item = player.currentMediaItem ?: return
        val track = Track(item.toSong(), item.localConfiguration?.uri?.toString(), item.mediaMetadata.extras?.getString("source"), item.mediaMetadata.extras?.getString("remoteId"))
        val next = (listOf(track) + read(context)).distinctBy { it.source to it.song.id }.take(100)
        context.getSharedPreferences("eplayer_recent", 0).edit().putString("items", gson.toJson(next)).apply()
    }
    fun remove(context: Context, id: String) {
        context.getSharedPreferences("eplayer_recent", 0).edit().putString("items", gson.toJson(read(context).filterNot { it.song.id == id })).apply()
    }
}
