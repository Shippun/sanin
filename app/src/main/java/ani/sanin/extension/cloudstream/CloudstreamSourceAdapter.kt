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
import kotlinx.coroutines.withContext
import rx.Observable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
    private var loadMethod: Method? = null
    private var loadLinksMethod: Method? = null
    private var initialized = false

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
                    providerInstance = providerClass.getDeclaredConstructor().newInstance()
                    searchMethod = tryFindMethod(providerClass, "search", String::class.java)
                    loadMethod = tryFindMethod(providerClass, "load", String::class.java)
                    loadLinksMethod = tryFindMethod(providerClass, "loadLinks", String::class.java)
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
        val apiClass = tryLoadApiClass(classLoader, "com.lagradost.cloudstream3.API")
        if (apiClass != null) {
            return scanDexForProviders(apkPath, classLoader, apiClass)
        }
        val baseClass = tryLoadApiClass(classLoader, "com.lagradost.cloudstream3.Provider")
        if (baseClass != null) {
            return scanDexForProviders(apkPath, classLoader, baseClass)
        }
        return null
    }

    private fun tryLoadApiClass(classLoader: ClassLoader, name: String): Class<*>? {
        return try {
            classLoader.loadClass(name)
        } catch (_: Exception) {
            null
        }
    }

    private fun scanDexForProviders(
        apkPath: String,
        classLoader: ClassLoader,
        baseClass: Class<*>,
    ): Class<*>? {
        try {
            val dexFile = DexFile(apkPath)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                try {
                    val clazz = classLoader.loadClass(className)
                    if (baseClass.isAssignableFrom(clazz) &&
                        !clazz.isInterface &&
                        !Modifier.isAbstract(clazz.modifiers)
                    ) {
                        dexFile.close()
                        return clazz
                    }
                } catch (_: Exception) {
                }
            }
            dexFile.close()
        } catch (_: Exception) {
        }
        return null
    }

    private fun tryFindMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method? {
        return try {
            clazz.getMethod(name, *paramTypes)
        } catch (_: Exception) {
            try {
                clazz.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
            } catch (_: Exception) {
                null
            }
        }
    }

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
                val rawResults = method.invoke(provider, query)
                val results = rawResults as? List<*> ?: return@withContext AnimesPage(emptyList(), false)

                val animes = results.mapNotNull { item ->
                    try {
                        val name = reflectString(item, "name") ?: reflectString(item, "title") ?: return@mapNotNull null
                        val url = reflectString(item, "url") ?: return@mapNotNull null
                        val image = reflectString(item, "image") ?: reflectString(item, "poster") ?: reflectString(item, "thumbnail_url")

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
                val rawResponse = method.invoke(provider, anime.url)
                if (rawResponse == null) return@withContext anime

                val name = reflectString(rawResponse, "name") ?: anime.title
                val description = reflectString(rawResponse, "plot") ?: reflectString(rawResponse, "description")
                val image = reflectString(rawResponse, "image") ?: reflectString(rawResponse, "poster")
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
                val rawResponse = method.invoke(provider, anime.url)
                if (rawResponse == null) return@withContext emptyList()

                val rawEpisodes = reflectField(rawResponse, "episodes") as? List<*> ?: return@withContext emptyList()

                rawEpisodes.mapNotNull { ep ->
                    try {
                        val epName = reflectString(ep, "name") ?: ""
                        val epUrl = reflectString(ep, "url") ?: return@mapNotNull null
                        val epNumber = reflectNumber(ep, "episode") ?: reflectNumber(ep, "episode_number") ?: -1f
                        val epImage = reflectString(ep, "image") ?: reflectString(ep, "poster")
                        val epDesc = reflectString(ep, "description") ?: reflectString(ep, "plot")
                        val epDate = reflectLong(ep, "date") ?: 0L

                        SEpisodeImpl().apply {
                            url = epUrl
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
        val method = loadLinksMethod ?: return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val rawResults = method.invoke(provider, episode.url)
                val results = rawResults as? List<*> ?: return@withContext emptyList()

                results.mapNotNull { link ->
                    try {
                        val videoUrl = reflectString(link, "url") ?: return@mapNotNull null
                        val quality = reflectString(link, "quality") ?: ""
                        val headersRaw = reflectField(link, "headers") as? Map<*, *>
                        val headersMap = headersRaw?.mapKeys { it.key.toString() }
                            ?.mapValues { it.value.toString() }

                        val headers = if (headersMap.isNullOrEmpty()) null
                        else {
                            val b = okhttp3.Headers.Builder()
                            headersMap.forEach { (k, v) -> b.add(k, v) }
                            b.build()
                        }

                        Video(
                            videoUrl = videoUrl,
                            videoTitle = quality,
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
            kotlinx.coroutines.runBlocking { getPopularAnime(page) }
        }
    }

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return Observable.fromCallable {
            kotlinx.coroutines.runBlocking { getSearchAnime(page, query, filters) }
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
                val popularMethod = tryFindMethod(provider.javaClass, "getPopular", Integer.TYPE)
                    ?: tryFindMethod(provider.javaClass, "getPopular", Int::class.java)
                    ?: tryFindMethod(provider.javaClass, "popular", Integer.TYPE)
                    ?: tryFindMethod(provider.javaClass, "popular", Int::class.java)
                val rawResults = popularMethod?.invoke(provider, page)
                val results = rawResults as? List<*> ?: return@withContext AnimesPage(emptyList(), false)

                val animes = results.mapNotNull { item ->
                    try {
                        val name = reflectString(item, "name") ?: reflectString(item, "title") ?: return@mapNotNull null
                        val url = reflectString(item, "url") ?: return@mapNotNull null
                        val image = reflectString(item, "image") ?: reflectString(item, "poster")

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
