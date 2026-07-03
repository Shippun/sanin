package ani.dantotsu.media.manga

open class MangaCache {
    fun put(url: String, data: ImageData) {}
    fun get(url: String): ImageData? = null
    fun clear() {}
}
