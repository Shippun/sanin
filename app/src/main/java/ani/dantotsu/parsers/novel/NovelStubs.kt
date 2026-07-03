package ani.dantotsu.parsers.novel

open class NovelExtension {
    data class Available(
        val name: String = "",
        val pkgName: String = "",
        val apkName: String = "",
        val versionCode: Long = 0,
        val repository: String = "",
        val sources: List<AvailableNovelSources> = emptyList(),
        val iconUrl: String = "",
    )
}

data class AvailableNovelSources(
    val id: Long = 0,
    val lang: String = "",
    val name: String = "",
    val baseUrl: String = "",
)

open class NovelExtensionManager
