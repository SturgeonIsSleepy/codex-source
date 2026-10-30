package cp.player.core.library

import android.net.Uri
import android.os.Bundle
import cp.player.model.Song
import cp.player.util.toMediaItem

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
