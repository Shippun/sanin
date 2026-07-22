package ani.sanin.parsers

import ani.sanin.Lazier
import ani.sanin.extension.cloudstream.CloudstreamInstalledExtension
import ani.sanin.extension.cloudstream.CloudstreamSourceAdapter
import ani.sanin.lazyList
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import android.content.Context
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

object AnimeSources : WatchSources() {
    override var list: List<Lazier<BaseParser>> = emptyList()
    var pinnedAnimeSources: List<String> = emptyList()
    var isInitialized = false
    private var appContext: Context? = null

    suspend fun init(
        fromExtensions: StateFlow<List<AnimeExtension.Installed>>,
        context: Context,
        fromCloudstream: StateFlow<List<CloudstreamInstalledExtension>>? = null,
    ) {
        appContext = context
        pinnedAnimeSources =
            PrefManager.getNullableVal<List<String>>(PrefName.AnimeSourcesOrder, null)
                ?: emptyList()

        val initialExtensions = fromExtensions.first()
        list = createParsersFromExtensions(initialExtensions) +
            createParsersFromCloudstream(fromCloudstream?.first() ?: emptyList(), context) +
            listOf(Lazier({ LocalAnimeParser() }, "Local"))
        isInitialized = true

        fromExtensions.collect { extensions ->
            val csExtensions = fromCloudstream?.value ?: emptyList()
            list = sortPinnedAnimeSources(
                createParsersFromExtensions(extensions) +
                    createParsersFromCloudstream(csExtensions, context),
                pinnedAnimeSources
            ) + listOf(Lazier({ LocalAnimeParser() }, "Local"))
        }
    }

    fun performReorderAnimeSources() {
        list = list.filter { it.name != "Local" }
        list = sortPinnedAnimeSources(list, pinnedAnimeSources) + listOf(
            Lazier({ LocalAnimeParser() }, "Local")
        )
    }

    private fun createParsersFromExtensions(
        extensions: List<AnimeExtension.Installed>
    ): List<Lazier<BaseParser>> {
        return extensions.map { extension ->
            Lazier({ DynamicAnimeParser(extension) }, extension.name)
        }
    }

    private fun createParsersFromCloudstream(
        extensions: List<CloudstreamInstalledExtension>,
        context: Context,
    ): List<Lazier<BaseParser>> {
        return extensions.map { csExt ->
            val adapter = CloudstreamSourceAdapter(csExt, context, csExt.filePath)
            val fakeExtension = AnimeExtension.Installed(
                name = csExt.name,
                pkgName = csExt.pkgName,
                versionName = csExt.versionName,
                versionCode = csExt.versionCode,
                libVersion = 0.0,
                lang = csExt.lang.ifEmpty { "en" },
                isNsfw = csExt.isNsfw,
                hasReadme = false,
                hasChangelog = false,
                sources = listOf(adapter),
                pkgFactory = null,
                icon = csExt.icon,
                hasUpdate = csExt.hasUpdate,
                isObsolete = false,
                isUnofficial = true,
            )
            Lazier({ DynamicAnimeParser(fakeExtension) }, csExt.name)
        }
    }

    private fun sortPinnedAnimeSources(
        sources: List<Lazier<BaseParser>>,
        pinnedAnimeSources: List<String>
    ): List<Lazier<BaseParser>> {
        val pinnedSourcesMap = sources.filter { pinnedAnimeSources.contains(it.name) }
            .associateBy { it.name }
        val orderedPinnedSources = pinnedAnimeSources.mapNotNull { name ->
            pinnedSourcesMap[name]
        }
        val unpinnedSources = sources.filterNot { pinnedAnimeSources.contains(it.name) }
        return orderedPinnedSources + unpinnedSources
    }
}


object HAnimeSources : WatchSources() {
    private val aList: List<Lazier<BaseParser>> = lazyList(
    )

    override val list = listOf(aList, AnimeSources.list).flatten()
}
