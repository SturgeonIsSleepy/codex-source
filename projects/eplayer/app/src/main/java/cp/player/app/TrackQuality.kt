package cp.player.app

import androidx.media3.common.MediaItem

/** Change only remote quality; keep the original source, ID, metadata and URI arguments. */
internal fun MediaItem.withQuality(quality: String): MediaItem {
    val uri = localConfiguration?.uri ?: return this
    if (uri.scheme != "cp" || uri.getQueryParameter("quality") == quality) return this
    val builder = uri.buildUpon().clearQuery()
    uri.queryParameterNames.forEach { name ->
        if (name == "quality") builder.appendQueryParameter(name, quality)
        else uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
    }
    if ("quality" !in uri.queryParameterNames) builder.appendQueryParameter("quality", quality)
    return buildUpon().setUri(builder.build()).build()
}
