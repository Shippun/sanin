package ani.dantotsu.media.manga

import ani.dantotsu.media.Author
import java.io.Serializable

data class Manga(
    var totalChapters: Int? = null,
    var author: Author? = null,
    var chapters: Int? = null,
) : Serializable
