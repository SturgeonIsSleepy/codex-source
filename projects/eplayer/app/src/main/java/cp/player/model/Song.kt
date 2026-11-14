package cp.player.model

data class SongArtist(val id: String, val name: String)

data class Song(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String? = null,
    val album: String,
    val albumArtUrl: String? = null,
    val durationMs: Long = 0L,
    val albumId: String? = null,
    val artists: List<SongArtist> = emptyList(),
    val artistAliases: List<String> = emptyList(),
    val titleAliases: List<String> = emptyList()
)
