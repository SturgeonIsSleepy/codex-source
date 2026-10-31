package cp.player.core.download

import cp.player.core.library.Track
import cp.player.core.library.LibraryStore

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class DownloadRecord(val key: String, val id: Long, val track: Track, val error: String? = null)
data class DownloadProgress(val record: DownloadRecord, val bytes: Long = 0, val total: Long = 0, val status: Int = DownloadManager.STATUS_PENDING)

/** Android owns the transfer, so downloads survive closing the app without a custom polling service. */
class Downloads(private val context: Context, private val library: LibraryStore) {
    private val manager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("eplayer_downloads", 0)
    private val gson = Gson()
    private val type = object : TypeToken<List<DownloadRecord>>() {}.type
    private fun read(): List<DownloadRecord> = runCatching { gson.fromJson<List<DownloadRecord>>(prefs.getString("jobs", "[]"), type) }.getOrNull().orEmpty()
    private fun save(items: List<DownloadRecord>) { prefs.edit().putString("jobs", gson.toJson(items)).apply() }
    @Synchronized fun enqueue(track: Track, url: String) {
        val key = "${track.source}:${track.remoteId ?: track.song.id}"
        if (read().any { it.key == key && it.id > 0 && it.error == null }) return
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.song.name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("Referer", "https://music.163.com")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "${UUID.randomUUID()}.audio")
        read().filter { it.key == key && it.id > 0 }.forEach { manager.remove(it.id) }
        val id = manager.enqueue(request)
        save(read().filterNot { it.key == key } + DownloadRecord(key, id, track))
    }
    @Synchronized fun cancel(key: String) {
        val jobs = read()
        jobs.filter { it.key == key && it.id > 0 }.forEach { manager.remove(it.id) }
        save(jobs.filterNot { it.key == key })
    }
    @Synchronized fun reconcile(): Pair<List<DownloadProgress>, Boolean> {
        var changedLibrary = false
        val original = read()
        val jobs = original.toMutableList()
        val result = mutableListOf<DownloadProgress>()
        val iterator = jobs.listIterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            if (job.error != null || job.id < 0) { result += DownloadProgress(job, status = DownloadManager.STATUS_FAILED); continue }
            val row = manager.query(DownloadManager.Query().setFilterById(job.id))
            row.use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    val failed = job.copy(id = -1, error = "下载已失效")
                    iterator.set(failed); result += DownloadProgress(failed, status = DownloadManager.STATUS_FAILED)
                    return@use
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    try {
                        val uri = manager.getUriForDownloadedFile(job.id) ?: error("下载文件不可读")
                        library.importAudio(uri, job.track.song.copy(id = job.track.remoteId ?: job.track.song.id), job.track.source)
                        manager.remove(job.id); iterator.remove(); changedLibrary = true
                    } catch (error: Exception) {
                        val failed = job.copy(error = error.message ?: "保存失败")
                        iterator.set(failed); result += DownloadProgress(failed, status = DownloadManager.STATUS_FAILED)
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    val failed = job.copy(error = "下载失败（${cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}）")
                    iterator.set(failed); result += DownloadProgress(failed, bytes, total, status)
                } else result += DownloadProgress(job, bytes, total, status)
            }
        }
        if (jobs != original) save(jobs)
        return result to changedLibrary
    }
}
