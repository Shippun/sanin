package ani.dantotsu.media

interface Type {
    fun asText(): String
}

enum class MediaType : Type {
    ANIME;

    override fun asText(): String {
        return when (this) {
            ANIME -> "Anime"
        }
    }

    companion object {
        fun fromText(string: String): MediaType? {
            return when (string) {
                "Anime" -> ANIME
                else -> {
                    null
                }
            }
        }
    }
}

enum class AddonType : Type {
    TORRENT,
    DOWNLOAD;

    override fun asText(): String {
        return when (this) {
            TORRENT -> "Torrent"
            DOWNLOAD -> "Download"
        }
    }

    companion object {
        fun fromText(string: String): AddonType? {
            return when (string) {
                "Torrent" -> TORRENT
                "Download" -> DOWNLOAD
                else -> {
                    null
                }
            }
        }
    }
}