package ani.sanin.extension.cloudstream

import android.content.Context
import ani.sanin.util.Logger
import dalvik.system.DexFile
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.animesource.model.SEpisodeImpl
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import rx.Observable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class CloudstreamSourceAdapter(
    private val extension: CloudstreamInstalledExtension,
    private val context: Context,
    private val overrideFilePath: String? = null,
) : AnimeCatalogueSource {

    override val id: Long = extension.pkgName.hashCode().toLong()
    override val name: String = extension.name
    override val lang: String = extension.lang.ifEmpty { "en" }
    override val supportsLatest = false

    private var providerInstance: Any? = null
    private var searchMethod: Method? = null
    private var searchHasPage = false
    private var searchIsSuspend = false
    private var loadMethod: Method? = null
    private var loadIsSuspend = false
    private var loadLinksMethod: Method? = null
    private var loadLinksIsSuspend = false
    private var loadLinksHasCallback = false
    private var initialized = false

    private data class LinksMethodInfo(
        val method: Method?,
        val isSuspend: Boolean = false,
        val isCallback: Boolean = false,
    )

    private suspend fun ensureInitialized() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            try {
                val apkPath = overrideFilePath ?: extension.filePath ?: run {
                    val ai = context.packageManager.getApplicationInfo(extension.pkgName, 0)
                    ai.sourceDir
                }
                if (!java.io.File(apkPath).exists()) {
                    Logger.log("Cloudstream: extension file not found at $apkPath")
                    initialized = true
                    return@withContext
                }

                val classLoader = PathClassLoader(apkPath, null, context.classLoader)

                val providerClass = findProviderClass(classLoader, apkPath)
                if (providerClass != null) {
                    Logger.log("Cloudstream: found provider class ${providerClass.name}")
                    providerInstance = providerClass.getDeclaredConstructor().newInstance()

                    val (search, sHasPage, sIsSus) = findMethodWithInfo(providerClass, listOf("search", "getSearch", "find"))
                    searchMethod = search
                    searchHasPage = sHasPage
                    searchIsSuspend = sIsSus

                    val (load, _, lIsSus) = findMethodWithInfo(providerClass, listOf("load", "getLoad"))
                    loadMethod = load
                    loadIsSuspend = lIsSus

                    val linksInfo = findLoadLinksMethod(providerClass)
                    loadLinksMethod = linksInfo.method
                    loadLinksIsSuspend = linksInfo.isSuspend
                    loadLinksHasCallback = linksInfo.isCallback

                    Logger.log("Cloudstream: search=${searchMethod != null} page=$searchHasPage suspend=$searchIsSuspend " +
                        "load=${loadMethod != null} suspend=$loadIsSuspend " +
                        "links=${loadLinksMethod != null} cb=$loadLinksHasCallback suspend=$loadLinksIsSuspend")
                } else {
                    Logger.log("Cloudstream: no provider class found in $apkPath")
                }
                initialized = true
            } catch (e: Exception) {
                Logger.log("Failed to initialize Cloudstream provider: ${extension.pkgName}")
                Logger.log(e)
                initialized = true
            }
        }
    }

    private fun findProviderClass(classLoader: ClassLoader, apkPath: String): Class<*>? {
        try {
            val dexFile = DexFile(apkPath)
            val entries = dexFile.entries()
            val candidates = mutableListOf<Class<*>>()
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                try {
                    val clazz = classLoader.loadClass(className)
                    if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) continue
                    if (clazz.name.startsWith("kotlin.") || clazz.name.startsWith("android.")) continue

                    val methodNames = clazz.methods.map { it.name }.toSet()
                    if (methodNames.any { it == "search" || it == "getSearch" || it == "load" }) {
                        candidates.add(clazz)
                    }
                } catch (_: Exception) { }
            }
            dexFile.close()

            if (candidates.isEmpty()) return null
            return candidates.maxByOrNull { it.methods.size }
        } catch (_: Exception) {
            return null
        }
    }

    private fun findMethodWithInfo(clazz: Class<*>, names: List<String>): Triple<Method?, Boolean, Boolean> {
        for (name in names) {
            val methods = clazz.methods.filter { it.name == name }
            for (m in methods) {
                val params = m.parameterTypes
                if (params.isEmpty()) continue

                val isSuspend = params.lastOrNull() == kotlin.coroutines.Continuation::class.java
                val realParams = if (isSuspend) params.dropLast(1) else params.toList()

                when (realParams.size) {
                    1 -> {
                        if (realParams[0] == String::class.java) {
                            m.isAccessible = true
                            return Triple(m, false, isSuspend)
                        }
                    }
                    2 -> {
                        if (realParams[0] == String::class.java &&
                            (realParams[1] == Int::class.java || realParams[1] == Integer.TYPE)
                        ) {
                            m.isAccessible = true
                            return Triple(m, true, isSuspend)
                        }
                    }
                }
            }
        }
        return Triple(null, false, false)
    }

    private fun findLoadLinksMethod(clazz: Class<*>): LinksMethodInfo {
        val names = listOf("loadLinks", "getLink", "getVideoList", "fetchLinks")
        for (name in names) {
            val methods = clazz.methods.filter { it.name == name }
            for (m in methods) {
                val params = m.parameterTypes
                if (params.isEmpty()) continue

                val isSuspend = params.lastOrNull() == kotlin.coroutines.Continuation::class.java
                val realParams = if (isSuspend) params.dropLast(1) else params.toList()

                // Standard CS3 callback-based: (String, boolean, Function1, Function1)
                if (realParams.size == 4 && realParams[0] == String::class.java) {
                    m.isAccessible = true
                    return LinksMethodInfo(m, isSuspend, true)
                }
                // Legacy List-returning: (String)
                if (realParams.size == 1 && realParams[0] == String::class.java) {
                    m.isAccessible = true
                    return LinksMethodInfo(m, isSuspend, false)
                }
                // Simple (String, boolean) returning List
                if (realParams.size == 2 && realParams[0] == String::class.java) {
                    m.isAccessible = true
                    return LinksMethodInfo(m, isSuspend, false)
                }
            }
        }
        return LinksMethodInfo(null, false, false)
    }

    private fun callSuspendMethod(provider: Any, method: Method, vararg args: Any?): Any? {
        return try {
            runBlocking {
                kotlin.coroutines.suspendCoroutine { cont ->
                    try {
                        val fullArgs = args.toMutableList()
                        // Continuation is always the last param
                        val contIdx = method.parameterTypes.indexOfFirst { it == kotlin.coroutines.Continuation::class.java }
                        if (contIdx >= 0) {
                            while (fullArgs.size < contIdx) fullArgs.add(null)
                            fullArgs.add(contIdx, cont)
                        } else {
                            fullArgs.add(cont)
                        }
                        val result = method.invoke(provider, *fullArgs.toTypedArray())
                        if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                            @Suppress("UNCHECKED_CAST")
                            (cont as kotlin.coroutines.Continuation<Any?>).resume(result)
                        }
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log("Cloudstream suspend call failed: ${e.message}")
            null
        }
    }

    private fun collectExtractorLinks(
        method: Method,
        provider: Any,
        data: String,
    ): List<Any> {
        val collected = mutableListOf<Any>()
        val proxyClassLoader = provider.javaClass.classLoader ?: context.classLoader
        val params = method.parameterTypes
        val isSuspend = params.lastOrNull() == kotlin.coroutines.Continuation::class.java
        val realParams = if (isSuspend) params.dropLast(1) else params.toList()

        val callArgs = arrayOfNulls<Any?>(realParams.size)
        var stringPassed = false
        var boolPassed = false
        var linkCbCreated = false
        var subCbCreated = false

        for (i in realParams.indices) {
            val p = realParams[i]
            callArgs[i] = when {
                !stringPassed && p == String::class.java -> {
                    stringPassed = true; data
                }
                !boolPassed && (p == Boolean::class.java || p == java.lang.Boolean.TYPE) -> {
                    boolPassed = true; false
                }
                !subCbCreated && p.name.contains("Function") && !linkCbCreated -> {
                    subCbCreated = true
                    Proxy.newProxyInstance(
                        proxyClassLoader,
                        arrayOf(p),
                        InvocationHandler { _, _, _ -> Unit }
                    )
                }
                !linkCbCreated && p.name.contains("Function") -> {
                    linkCbCreated = true
                    Proxy.newProxyInstance(
                        proxyClassLoader,
                        arrayOf(p),
                        InvocationHandler { _, _, a ->
                            if (a != null && a.isNotEmpty()) collected.add(a[0])
                            Unit
                        }
                    )
                }
                else -> null
            }
        }

        try {
            if (isSuspend) {
                runBlocking {
                    kotlin.coroutines.suspendCoroutine { cont ->
                        val fullArgs = callArgs.toMutableList()
                        val contIdx = method.parameterTypes.indexOfFirst {
                            it == kotlin.coroutines.Continuation::class.java
                        }
                        if (contIdx >= 0) {
                            while (fullArgs.size < contIdx) fullArgs.add(null)
                            fullArgs.add(contIdx, cont)
                        } else {
                            fullArgs.add(cont)
                        }
                        val result = method.invoke(provider, *fullArgs.toTypedArray())
                        if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                            @Suppress("UNCHECKED_CAST")
                            (cont as kotlin.coroutines.Continuation<Any?>).resume(result)
                        }
                    }
                }
            } else {
                method.invoke(provider, *callArgs)
            }
        } catch (e: Exception) {
            Logger.log("Cloudstream loadLinks invoke failed: ${e.message}")
        }
        return collected
    }

    private fun extractSearchResults(rawResults: Any?): Pair<List<*>, Boolean> {
        if (rawResults == null) return Pair(emptyList<Any>(), false)
        if (rawResults is List<*>) return Pair(rawResults, false)
        // SearchResponseList: has "items" and "hasNext" fields
        val items = reflectField(rawResults, "items") as? List<*>
        val hasNext = (reflectField(rawResults, "hasNext") as? Boolean) ?: false
        if (items != null) return Pair(items, hasNext)
        return Pair(emptyList<Any>(), false)
    }

    private fun extractEpisodes(rawResponse: Any?): List<*> {
        if (rawResponse == null) return emptyList<Any>()
        val rawEpisodes = reflectField(rawResponse, "episodes")
        return when (rawEpisodes) {
            is List<*> -> rawEpisodes
            is Map<*, *> -> rawEpisodes.values.flatMap { (it as? List<*>) ?: emptyList() }
            else -> {
                // Try dataUrl for movie/live responses
                val dataUrl = reflectString(rawResponse, "dataUrl")
                if (dataUrl != null) {
                    listOf(createVirtualEpisode(rawResponse, dataUrl))
                } else emptyList<Any>()
            }
        }
    }

    private fun createVirtualEpisode(rawResponse: Any, dataUrl: String): Any {
        val epName = reflectString(rawResponse, "name") ?: ""
        val epPoster = reflectString(rawResponse, "posterUrl") ?: reflectString(rawResponse, "poster") ?: reflectString(rawResponse, "image")
        // Create a simple Map-like object that our reflection can read
        return VirtualEpisode(
            data = dataUrl,
            name = epName,
            season = 1,
            episode = 1,
            posterUrl = epPoster,
        )
    }

    private class VirtualEpisode(
        val data: String,
        val name: String?,
        val season: Int?,
        val episode: Int?,
        val posterUrl: String?,
    )

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage {
        ensureInitialized()
        val provider = providerInstance ?: return AnimesPage(emptyList(), false)
        val method = searchMethod ?: return AnimesPage(emptyList(), false)

        return withContext(Dispatchers.IO) {
            try {
                val rawResults = if (searchIsSuspend) {
                    if (searchHasPage) callSuspendMethod(provider, method, query, page)
                    else callSuspendMethod(provider, method, query)
                } else {
                    if (searchHasPage) method.invoke(provider, query, page)
                    else method.invoke(provider, query)
                }

                Logger.log("Cloudstream search: rawResults=${rawResults?.javaClass?.name}")
                val (results, hasNextPage) = extractSearchResults(rawResults)
                Logger.log("Cloudstream search: extracted ${results.size} results, hasNext=$hasNextPage")
                if (results.isNotEmpty()) {
                    Logger.log("Cloudstream search: first result type=${results.first()?.javaClass?.name}")
                }

                val animes = results.mapNotNull { item ->
                    try {
                        val name = reflectString(item, "name") ?: reflectString(item, "title") ?: return@mapNotNull null
                        val url = reflectString(item, "url") ?: return@mapNotNull null
                        val image = reflectString(item, "posterUrl") ?: reflectString(item, "poster") ?: reflectString(item, "image") ?: reflectString(item, "thumbnail_url")

                        SAnimeImpl().apply {
                            this.url = url
                            this.title = name
                            this.thumbnail_url = image ?: ""
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                AnimesPage(animes, false)
            } catch (e: Exception) {
                Logger.log("Cloudstream search failed: ${e.message}")
                AnimesPage(emptyList(), false)
            }
        }
    }

    override suspend fun getAnimeDetails(anime: eu.kanade.tachiyomi.animesource.model.SAnime): eu.kanade.tachiyomi.animesource.model.SAnime {
        ensureInitialized()
        val provider = providerInstance ?: return anime
        val method = loadMethod ?: return anime

        return withContext(Dispatchers.IO) {
            try {
                val rawResponse = if (loadIsSuspend) callSuspendMethod(provider, method, anime.url)
                    else method.invoke(provider, anime.url)
                if (rawResponse == null) return@withContext anime

                val name = reflectString(rawResponse, "name") ?: anime.title
                val description = reflectString(rawResponse, "plot") ?: reflectString(rawResponse, "description")
                val image = reflectString(rawResponse, "posterUrl") ?: reflectString(rawResponse, "poster") ?: reflectString(rawResponse, "image")
                val tags = reflectList(rawResponse, "tags")?.mapNotNull { it?.toString() }?.joinToString(", ")

                SAnimeImpl().apply {
                    url = anime.url
                    title = name
                    this.description = description
                    thumbnail_url = image ?: anime.thumbnail_url
                    genre = tags
                    status = anime.status
                }
            } catch (e: Exception) {
                Logger.log("Cloudstream getAnimeDetails failed: ${e.message}")
                anime
            }
        }
    }

    override suspend fun getEpisodeList(anime: eu.kanade.tachiyomi.animesource.model.SAnime): List<eu.kanade.tachiyomi.animesource.model.SEpisode> {
        ensureInitialized()
        val provider = providerInstance ?: return emptyList()
        val method = loadMethod ?: return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val rawResponse = if (loadIsSuspend) callSuspendMethod(provider, method, anime.url)
                    else method.invoke(provider, anime.url)
                if (rawResponse == null) {
                    Logger.log("Cloudstream getEpisodeList: load returned null")
                    return@withContext emptyList()
                }

                Logger.log("Cloudstream getEpisodeList: load returned ${rawResponse.javaClass.name}")
                val rawEpisodes = extractEpisodes(rawResponse)
                Logger.log("Cloudstream getEpisodeList: extracted ${rawEpisodes.size} episodes")

                rawEpisodes.mapNotNull { ep ->
                    try {
                        val epName = reflectString(ep, "name") ?: ""
                        // Episode uses "data" not "url" as its identifier
                        val epData = reflectString(ep, "data") ?: reflectString(ep, "url") ?: return@mapNotNull null
                        val epNumber = reflectNumber(ep, "episode") ?: -1f
                        val epImage = reflectString(ep, "posterUrl") ?: reflectString(ep, "poster") ?: reflectString(ep, "image")
                        val epDesc = reflectString(ep, "description") ?: reflectString(ep, "plot")
                        val epDate = reflectLong(ep, "date") ?: 0L

                        SEpisodeImpl().apply {
                            url = epData
                            name = epName
                            episode_number = epNumber
                            preview_url = epImage
                            summary = epDesc
                            date_upload = epDate
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Logger.log("Cloudstream getEpisodeList failed: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun getVideoList(episode: eu.kanade.tachiyomi.animesource.model.SEpisode): List<Video> {
        ensureInitialized()
        val provider = providerInstance ?: return emptyList()
        val method = loadLinksMethod ?: run {
            Logger.log("Cloudstream getVideoList: no loadLinksMethod found")
            return emptyList()
        }

        Logger.log("Cloudstream getVideoList: method=${method.name} params=${method.parameterTypes.map { it.simpleName }} cb=$loadLinksHasCallback suspend=$loadLinksIsSuspend")

        return withContext(Dispatchers.IO) {
            try {
                val links = if (loadLinksHasCallback) {
                    Logger.log("Cloudstream getVideoList: using callback-based collectExtractorLinks")
                    collectExtractorLinks(method, provider, episode.url)
                } else {
                    val rawResults = if (loadLinksIsSuspend) callSuspendMethod(provider, method, episode.url)
                        else method.invoke(provider, episode.url)
                    (rawResults as? List<*>) ?: emptyList()
                }

                Logger.log("Cloudstream getVideoList: collected ${links.size} links")
                if (links.isNotEmpty()) {
                    Logger.log("Cloudstream getVideoList: first link type=${links.first()?.javaClass?.name}")
                }

                links.mapNotNull { link ->
                    try {
                        val videoUrl = reflectString(link, "url") ?: return@mapNotNull null
                        val qualityOrName = reflectString(link, "name") ?: reflectString(link, "quality") ?: ""
                        val referer = reflectString(link, "referer")
                        val headersRaw = reflectField(link, "headers") as? Map<*, *>
                        val headersMap = headersRaw?.mapKeys { it.key.toString() }
                            ?.mapValues { it.value.toString() }

                        val allHeaders = mutableMapOf<String, String>()
                        if (!referer.isNullOrEmpty()) {
                            allHeaders["referer"] = referer
                        }
                        if (!headersMap.isNullOrEmpty()) {
                            allHeaders.putAll(headersMap)
                        }

                        val headers = if (allHeaders.isEmpty()) null
                        else {
                            val b = okhttp3.Headers.Builder()
                            allHeaders.forEach { (k, v) -> b.add(k, v) }
                            b.build()
                        }

                        Video(
                            videoUrl = videoUrl,
                            videoTitle = qualityOrName,
                            headers = headers,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                Logger.log("Cloudstream getVideoList failed: ${e.message}")
                emptyList()
            }
        }
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(emptyList())

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return Observable.fromCallable {
            runBlocking { getPopularAnime(page) }
        }
    }

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return Observable.fromCallable {
            runBlocking { getSearchAnime(page, query, filters) }
        }
    }

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return Observable.just(AnimesPage(emptyList(), false))
    }

    override suspend fun getSeasonList(anime: eu.kanade.tachiyomi.animesource.model.SAnime): List<eu.kanade.tachiyomi.animesource.model.SAnime> {
        return emptyList()
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        ensureInitialized()
        val provider = providerInstance ?: return AnimesPage(emptyList(), false)

        return withContext(Dispatchers.IO) {
            try {
                val (popMethod, popHasPage, popIsSus) = findMethodWithInfo(provider.javaClass, listOf("getPopular", "popular"))
                val rawResults = if (popMethod != null) {
                    if (popIsSus) {
                        if (popHasPage) callSuspendMethod(provider, popMethod, page)
                        else callSuspendMethod(provider, popMethod, page.toString())
                    } else {
                        if (popHasPage) popMethod.invoke(provider, page)
                        else popMethod.invoke(provider, page.toString())
                    }
                } else null

                val (results, _) = extractSearchResults(rawResults)

                val animes = results.mapNotNull { item ->
                    try {
                        val name = reflectString(item, "name") ?: reflectString(item, "title") ?: return@mapNotNull null
                        val url = reflectString(item, "url") ?: return@mapNotNull null
                        val image = reflectString(item, "posterUrl") ?: reflectString(item, "poster") ?: reflectString(item, "image")

                        SAnimeImpl().apply {
                            this.url = url
                            this.title = name
                            thumbnail_url = image ?: ""
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                AnimesPage(animes, false)
            } catch (e: Exception) {
                Logger.log("Cloudstream getPopularAnime failed: ${e.message}")
                AnimesPage(emptyList(), false)
            }
        }
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        return AnimesPage(emptyList(), false)
    }

    private fun reflectString(obj: Any?, field: String): String? {
        if (obj == null) return null
        return try {
            val getter = obj.javaClass.getMethod("get${field.replaceFirstChar { it.uppercase() }}")
            getter.invoke(obj)?.toString()
        } catch (_: NoSuchMethodException) {
            try {
                val fieldObj = obj.javaClass.getDeclaredField(field).apply { isAccessible = true }
                fieldObj.get(obj)?.toString()
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reflectField(obj: Any?, field: String): Any? {
        if (obj == null) return null
        return try {
            val getter = obj.javaClass.getMethod("get${field.replaceFirstChar { it.uppercase() }}")
            getter.invoke(obj)
        } catch (_: NoSuchMethodException) {
            try {
                val fieldObj = obj.javaClass.getDeclaredField(field).apply { isAccessible = true }
                fieldObj.get(obj)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reflectList(obj: Any?, field: String): List<*>? {
        val raw = reflectField(obj, field)
        return raw as? List<*>
    }

    private fun reflectNumber(obj: Any?, field: String): Float? {
        if (obj == null) return null
        return try {
            val getter = obj.javaClass.getMethod("get${field.replaceFirstChar { it.uppercase() }}")
            (getter.invoke(obj) as? Number)?.toFloat()
        } catch (_: NoSuchMethodException) {
            try {
                val fieldObj = obj.javaClass.getDeclaredField(field).apply { isAccessible = true }
                (fieldObj.get(obj) as? Number)?.toFloat()
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reflectLong(obj: Any?, field: String): Long? {
        if (obj == null) return null
        return try {
            val getter = obj.javaClass.getMethod("get${field.replaceFirstChar { it.uppercase() }}")
            (getter.invoke(obj) as? Number)?.toLong()
        } catch (_: NoSuchMethodException) {
            try {
                val fieldObj = obj.javaClass.getDeclaredField(field).apply { isAccessible = true }
                (fieldObj.get(obj) as? Number)?.toLong()
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
