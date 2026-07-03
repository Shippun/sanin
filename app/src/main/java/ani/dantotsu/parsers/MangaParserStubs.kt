package ani.dantotsu.parsers

import java.io.Serializable
import java.util.Date

open class MangaParser {
    open val name: String = ""
    open val saveName: String = ""
    open val hostUrl: String = ""
    open val isNSFW: Boolean = false
    open val icon: ByteArray? = null
}

class EmptyMangaParser : MangaParser()

open class OfflineMangaParser : MangaParser()

open class NovelParser {
    open val name: String = ""
    open val saveName: String = ""
}

class EmptyNovelParser : NovelParser()

open class MangaChapter(
    open val name: String = "",
    open val url: String = "",
    open val number: String = "",
    open val date: String? = null,
    open val scanlator: String? = null,
    open val sChapter: Any? = null,
    open val dateUpload: Long? = null,
) : Serializable

data class MangaImage(
    val url: Any?,
    val isSpecial: Boolean = false,
    val page: Any? = null,
)

object MangaSources : BaseSources() {
    override val list = mutableListOf<SourceRef<MangaParser>>()
    override val listener = SourceListener()
    val isInitialized = false

    override operator fun get(i: Int): MangaParser? = null

    override fun getSource(name: String): MangaParser? = null
}

object HMangaSources : BaseSources() {
    override val list = mutableListOf<SourceRef<MangaParser>>()
    override val listener = SourceListener()
    val isInitialized = false

    override operator fun get(i: Int): MangaParser? = null

    override fun getSource(name: String): MangaParser? = null
}
