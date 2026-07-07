package ani.dantotsu.connections.anizip

import ani.dantotsu.Mapper
import ani.dantotsu.client
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TmdbImage(
    val file_path: String? = null,
    val vote_average: Double? = null,
    val aspect_ratio: Double? = null,
    val iso_639_1: String? = null,
)

@Serializable
data class TmdbImagesResponse(
    val backdrops: List<TmdbImage>? = null,
    val posters: List<TmdbImage>? = null,
)

object AniZip {
    private const val BASE_URL = "https://hayase.ani.zip"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val imageBase = "https://image.tmdb.org/t/p/original"

    suspend fun getBackdropUrl(anilistId: Int): String? {
        return try {
            val response = client.get("$BASE_URL/v2/images/tmdb?anilist_id=$anilistId")
            val body = Mapper.json.decodeFromString<TmdbImagesResponse>(response.text) ?: return null
            body.backdrops
                ?.filter { it.iso_639_1 == null && it.aspect_ratio != null && it.aspect_ratio > 1.2 }
                ?.maxByOrNull { it.vote_average ?: 0.0 }
                ?.file_path
                ?.let { "$imageBase$it" }
        } catch (_: Exception) {
            null
        }
    }
}
