package ani.dantotsu.media.manga

import ani.dantotsu.connections.anilist.api.Staff
import java.io.Serializable

data class Manga(
    val totalChapters: Int? = null,
    val author: Staff? = null,
) : Serializable
