package ani.sanin.extension.cloudstream

import ani.sanin.asyncMap
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

object CloudstreamApi {
    private val networkService: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()

    suspend fun fetchAllRepos(): List<CloudstreamRepo> {
        return withIOContext {
            val urls = PrefManager.getVal<Set<String>>(PrefName.CloudstreamExtensionRepos).toList()
            if (urls.isEmpty()) return@withIOContext emptyList()

            urls.asyncMap { url ->
                try {
                    fetchRepo(url)
                } catch (e: Exception) {
                    Logger.log("Failed to fetch Cloudstream repo: $url")
                    Logger.log(e)
                    null
                }
            }.filterNotNull()
        }
    }

    suspend fun fetchRepo(url: String): CloudstreamRepo? {
        return try {
            val repoUrl = if (url.contains("repo.json")) url
                else "${url.trimEnd('/')}/repo.json"
            val response = networkService.client.newCall(GET(repoUrl)).awaitSuccess()
            val text = response.body.string()
            val jsonObj = json.parseToJsonElement(text).jsonObject

            if (jsonObj["pluginLists"]?.jsonArray != null && jsonObj["manifestVersion"]?.jsonPrimitive?.content == "1") {
                val extensions = CloudstreamRepoParser.parsePluginLists(url, jsonObj)
                val name = jsonObj["name"]?.jsonPrimitive?.content ?: url.substringAfterLast("/").substringBeforeLast(".")
                val description = jsonObj["description"]?.jsonPrimitive?.content ?: ""
                return CloudstreamRepo(
                    url = url,
                    name = name,
                    description = description,
                    extensions = extensions,
                )
            }

            parseRepoJson(url, jsonObj)
        } catch (e: Exception) {
            Logger.log("Failed to fetch Cloudstream repo: $url")
            Logger.log(e)
            null
        }
    }

    private fun parseRepoJson(url: String, obj: JsonObject): CloudstreamRepo {
        val name = obj["name"]?.jsonPrimitive?.content ?: url.substringAfterLast("/").substringBeforeLast(".")
        val description = obj["description"]?.jsonPrimitive?.content ?: ""
        val website = obj["website"]?.jsonPrimitive?.content ?: ""
        val icon = obj["icon"]?.jsonPrimitive?.content
        val version = (obj["version"]?.jsonPrimitive?.content ?: "0").toIntOrNull() ?: 0

        val packs = mutableListOf<CloudstreamAvailableExtension>()
        val packsArray = obj["packs"]?.jsonArray
        if (packsArray != null) {
            for (element in packsArray) {
                try {
                    val pack = element.jsonObject
                    val pkgName = pack["pkg"]?.jsonPrimitive?.content ?: continue
                    val apkFile = pack["file"]?.jsonPrimitive?.content
                        ?: pack["apk"]?.jsonPrimitive?.content ?: continue
                    val apkUrl = if (apkFile.startsWith("http")) apkFile
                        else "${url.trimEnd('/').removeSuffix("/repo.json")}/$apkFile"

                    packs.add(CloudstreamAvailableExtension(
                        name = pack["name"]?.jsonPrimitive?.content ?: pkgName,
                        pkgName = pkgName,
                        versionName = pack["versionName"]?.jsonPrimitive?.content
                            ?: "1.0.0",
                        versionCode = (pack["version"]?.jsonPrimitive?.content ?: "1").toLongOrNull() ?: 1L,
                        lang = pack["lang"]?.jsonPrimitive?.content ?: "en",
                        isNsfw = (pack["nsfw"]?.jsonPrimitive?.content ?: "false").toBooleanStrictOrNull() ?: false,
                        description = pack["description"]?.jsonPrimitive?.content ?: "",
                        iconUrl = pack["icon"]?.jsonPrimitive?.content?.let { iconPath ->
                            if (iconPath.startsWith("http")) iconPath
                            else "${url.trimEnd('/').removeSuffix("/repo.json")}/$iconPath"
                        },
                        apkUrl = apkUrl,
                        repository = url,
                        category = pack["category"]?.jsonPrimitive?.content ?: "",
                    ))
                } catch (e: Exception) {
                    Logger.log("Failed to parse Cloudstream pack")
                    Logger.log(e)
                }
            }
        }

        return CloudstreamRepo(
            url = url,
            name = name,
            description = description,
            website = website,
            icon = icon,
            version = version,
            extensions = packs,
        )
    }

    suspend fun fetchRepoCategories(url: String): List<CloudstreamRepoCategory> {
        val repo = fetchRepo(url) ?: return emptyList()
        return repo.extensions
            .groupBy { it.category.ifEmpty { "Uncategorized" } }
            .map { (category, exts) -> CloudstreamRepoCategory(category, exts) }
    }
}

@Serializable
data class CloudstreamRepoResponse(
    val name: String = "",
    val description: String = "",
    val website: String = "",
    val icon: String? = null,
    val packs: List<CloudstreamPack> = emptyList(),
)

@Serializable
data class CloudstreamPack(
    val name: String = "",
    val pkg: String = "",
    val version: Int = 0,
    @SerialName("version name")
    val versionName: String = "",
    val lang: String = "",
    val nsfw: Boolean = false,
    val description: String = "",
    val icon: String? = null,
    val apk: String = "",
    val category: String = "",
)
