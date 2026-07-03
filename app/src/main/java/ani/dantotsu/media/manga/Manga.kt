package ani.dantotsu.media.manga

import ani.dantotsu.media.Author
import java.io.Serializable

data class Manga(
    val totalChapters: Int? = null,
    val author: Author? = null,
) : Serializable
