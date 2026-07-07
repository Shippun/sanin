package ani.dantotsu.connections.anizip

import ani.dantotsu.Mapper
import ani.dantotsu.client
import kotlinx.serialization.Serializable

@Serializable
data class AniZipImage(
    val coverType: String? = null,
    val url: String? = null,
)

@Serializable
data class AniZipMappings(
    val images: List<AniZipImage>? = null,
)

object AniZip {
    private const val BASE_URL = "https://api.ani.zip"

    suspend fun getBackdropUrl(anilistId: Int): String? {
        return try {
            val response = client.get("$BASE_URL/mappings?anilist_id=$anilistId")
            val mappings = Mapper.json.decodeFromString<AniZipMappings>(response.text)
            mappings.images
                ?.firstOrNull { it.coverType == "Fanart" }
                ?.url
                ?: mappings.images
                    ?.firstOrNull { it.coverType == "Banner" }
                    ?.url
        } catch (_: Exception) {
            null
        }
    }
}
