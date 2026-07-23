package ani.sanin.connections.subtitles

import ani.sanin.Mapper
import ani.sanin.okHttpClient
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object OpenSubtitles {

    private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
    private const val USER_AGENT = "Sanin v3.2.2"

    private fun getApiKey(): String? {
        val key = PrefManager.getVal<String>(PrefName.OpenSubtitlesApiKey)
        return key.ifBlank { null }
    }

    suspend fun search(imdbId: String, season: Int, episode: Int): List<StremioSub> {
        val apiKey = getApiKey() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val languages = PrefManager.getVal<Set<String>>(PrefName.OnlineSubtitleLanguages)
                    .joinToString(",") { it.take(2).lowercase() }

                val url = buildString {
                    append("$BASE_URL/subtitles?imdb_id=$imdbId&languages=$languages")
                    append("&type=episode&season_number=$season&episode_number=$episode")
                }
                Logger.log("OpenSubtitles: Searching $url")

                val request = Request.Builder()
                    .url(url)
                    .header("Api-Key", apiKey)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Logger.log("OpenSubtitles: Search failed HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val searchResult = Mapper.json.decodeFromString<OpenSubtitlesSearchResponse>(body)

                searchResult.data.mapNotNull { item ->
                    try {
                        val fileId = item.attributes.files.firstOrNull()?.fileId ?: return@mapNotNull null
                        val downloadUrl = downloadSubtitle(fileId, apiKey) ?: return@mapNotNull null
                        Logger.log("OpenSubtitles: Got link for file $fileId → $downloadUrl")

                        StremioSub(
                            id = item.id,
                            url = downloadUrl,
                            lang = item.attributes.language,
                            source = "opensubtitles"
                        )
                    } catch (_: Exception) { null }
                }
            } catch (e: Exception) {
                Logger.log("OpenSubtitles: Error - ${e.message}")
                emptyList()
            }
        }
    }

    private fun downloadSubtitle(fileId: Int, apiKey: String): String? {
        return try {
            val json = Mapper.json.encodeToString(DownloadRequest(fileId))
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/download")
                .header("Api-Key", apiKey)
                .header("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Logger.log("OpenSubtitles: Download failed HTTP ${response.code} for file $fileId")
                return null
            }

            val body = response.body?.string() ?: return null
            val downloadResult = Mapper.json.decodeFromString<DownloadResponse>(body)
            downloadResult.link
        } catch (e: Exception) {
            Logger.log("OpenSubtitles: Download error - ${e.message}")
            null
        }
    }
}

@Serializable
data class OpenSubtitlesSearchResponse(
    val data: List<OpenSubtitlesItem> = emptyList()
)

@Serializable
data class OpenSubtitlesItem(
    val id: String,
    val attributes: OpenSubtitlesAttributes
)

@Serializable
data class OpenSubtitlesAttributes(
    val language: String,
    val files: List<OpenSubtitlesFile> = emptyList()
)

@Serializable
data class OpenSubtitlesFile(
    @SerialName("file_id") val fileId: Int,
    @SerialName("file_name") val fileName: String? = null
)

@Serializable
data class DownloadRequest(
    @SerialName("file_id") val fileId: Int
)

@Serializable
data class DownloadResponse(
    val link: String? = null,
    @SerialName("file_name") val fileName: String? = null
)
