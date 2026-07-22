package ani.sanin.extension.cloudstream

import ani.sanin.util.Logger
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

enum class RepoType {
    ANIYOMI, CLOUDSTREAM_PACKS, CLOUDSTREAM_PLUGINLISTS, UNKNOWN
}

data class RepoDetectionResult(
    val type: RepoType,
    val url: String,
)

object CloudstreamRepoParser {
    private val networkService: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    suspend fun detectRepoType(url: String): RepoDetectionResult? {
        return withIOContext {
            try {
                val repoUrl = normalizeUrl(url)
                val response = networkService.client.newCall(GET(repoUrl)).awaitSuccess()
                val text = response.body.string()
                val element = json.parseToJsonElement(text)

                val type = when (element) {
                    is JsonObject -> detectCloudstreamType(element)
                    is JsonArray -> RepoType.ANIYOMI
                    else -> RepoType.UNKNOWN
                }

                RepoDetectionResult(type, url)
            } catch (e: Exception) {
                Logger.log("Failed to detect repo type: $url")
                Logger.log(e)
                null
            }
        }
    }

    private fun detectCloudstreamType(obj: JsonObject): RepoType {
        if (obj["pluginLists"]?.jsonArray != null) {
            return RepoType.CLOUDSTREAM_PLUGINLISTS
        }
        if (obj["packs"]?.jsonArray != null) {
            return RepoType.CLOUDSTREAM_PACKS
        }
        return RepoType.UNKNOWN
    }

    suspend fun parsePluginLists(
        repoUrl: String,
        obj: JsonObject,
    ): List<CloudstreamAvailableExtension> {
        val pluginLists = obj["pluginLists"]?.jsonArray ?: return emptyList()
        val baseUrl = repoUrl.trimEnd('/').removeSuffix("/repo.json")

        val allPlugins = pluginLists.mapNotNull { element ->
            val relativePath = element.jsonPrimitive?.content ?: return@mapNotNull null
            val pluginUrl = if (relativePath.startsWith("http")) relativePath
                else "$baseUrl/$relativePath"
            try {
                fetchPluginList(pluginUrl)
            } catch (e: Exception) {
                Logger.log("Failed to fetch plugin list: $pluginUrl")
                Logger.log(e)
                emptyList()
            }
        }.flatten()

        return allPlugins.map { plugin ->
            val apkUrl = if (plugin.download.startsWith("http")) plugin.download
                else "$baseUrl/${plugin.download.trimStart('/')}"

            CloudstreamAvailableExtension(
                name = plugin.name,
                pkgName = plugin.packageName,
                versionName = plugin.version,
                versionCode = plugin.versionCode,
                lang = plugin.language,
                isNsfw = false,
                description = plugin.description,
                iconUrl = plugin.icon?.let { icon ->
                    if (icon.startsWith("http")) icon
                    else "$baseUrl/$icon"
                },
                apkUrl = apkUrl,
                repository = repoUrl,
                category = plugin.tvTypes?.firstOrNull() ?: "",
            )
        }
    }

    private suspend fun fetchPluginList(url: String): List<CloudstreamPlugin> {
        val response = networkService.client.newCall(GET(url)).awaitSuccess()
        val text = response.body.string()
        Logger.log("Cloudstream: fetched plugins from $url, length=${text.length}")
        return try {
            val plugins = json.decodeFromString<List<CloudstreamPlugin>>(text)
            Logger.log("Cloudstream: parsed ${plugins.size} plugins. First: name='${plugins.firstOrNull()?.name}', pkg='${plugins.firstOrNull()?.packageName}', download='${plugins.firstOrNull()?.download?.take(50)}'")
            plugins
        } catch (e: Exception) {
            Logger.log("Cloudstream: failed to parse plugin list from $url: ${e.message}")
            Logger.log(text.take(500))
            emptyList()
        }
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.contains("repo.json") || url.contains("index.min.json") -> url
            else -> "${url.trimEnd('/')}/repo.json"
        }
    }
}

@Serializable
data class CloudstreamPlugin(
    val name: String = "",
    @SerialName("internalName")
    val packageName: String = "",
    @SerialName("url")
    val download: String = "",
    @SerialName("iconUrl")
    val icon: String? = null,
    val language: String = "en",
    val tvTypes: List<String>? = null,
    val fileSize: Long = 0,
    val description: String = "",
    @SerialName("repositoryUrl")
    val website: String? = null,
    val status: Int = 0,
    @SerialName("version")
    val versionCode: Long = 0,
    @SerialName("authors")
    private val _authors: List<String>? = null,
) {
    val author: String? get() = _authors?.firstOrNull()
    val version: String get() = versionCode.toString()
}
