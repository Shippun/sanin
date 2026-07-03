package ani.dantotsu.parsers

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.io.Serializable
import java.util.Date

open class MangaParser {
    open val name: String = ""
    open val saveName: String = ""
    open val hostUrl: String = ""
    open val isNSFW: Boolean = false
    open val icon: ByteArray? = null

    open suspend fun search(query: String): List<ShowResponse>? = null
    open suspend fun loadChapters(
        mangaUrl: String,
        extra: Map<String, String>? = null,
        sManga: SManga? = null,
        sourceLanguage: Int = 0,
    ): List<MangaChapter>? = null
    open suspend fun loadImages(chapterLink: String, sChapter: SChapter): List<MangaImage> = emptyList()
}

class EmptyMangaParser : MangaParser()

open class OfflineMangaParser : MangaParser()

open class NovelParser : BaseParser() {
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
) : Serializable {
    constructor(chapter: MangaChapter) : this(
        chapter.name, chapter.url, chapter.number,
        chapter.date, chapter.scanlator, chapter.sChapter, chapter.dateUpload
    )
}

data class MangaImage(
    val url: Any?,
    val isSpecial: Boolean = false,
    val page: Any? = null,
)

object MangaSources {
    val list = mutableListOf<Any>()
    val isInitialized = false

    operator fun get(i: Int): MangaParser? = null
    fun getSource(name: String): MangaParser? = null
}

object HMangaSources {
    val list = mutableListOf<Any>()
    val isInitialized = false

    operator fun get(i: Int): MangaParser? = null
    fun getSource(name: String): MangaParser? = null
}
