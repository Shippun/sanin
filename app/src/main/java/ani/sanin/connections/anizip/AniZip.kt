package ani.sanin.connections.anizip

import ani.sanin.Mapper
import ani.sanin.client
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class AniZipImages(
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val posterUrl: String? = null,
)

object AniZip {
    private const val BASE_URL = "https://api.ani.zip"

    suspend fun getImages(anilistId: Int): AniZipImages {
        return try {
            val response = client.get("$BASE_URL/mappings?anilist_id=$anilistId")
            val mappings = Mapper.json.decodeFromString<AniZipMappings>(response.text)
            val images = mappings.images.orEmpty()
            AniZipImages(
                backdropUrl = images.firstOrNull { it.coverType == "Fanart" }?.url
                    ?: images.firstOrNull { it.coverType == "Banner" }?.url,
                logoUrl = images.firstOrNull { it.coverType == "Clearlogo" }?.url,
                posterUrl = images.firstOrNull { it.coverType == "Poster" }?.url,
            )
        } catch (_: Exception) {
            AniZipImages()
        }
    }

    suspend fun getImagesBatch(ids: List<Int>): Map<Int, AniZipImages> = coroutineScope {
        ids.map { id -> async { id to getImages(id) } }.associate { it.await() }
    }

    suspend fun getBackdropUrl(anilistId: Int): String? {
        return getImages(anilistId).backdropUrl
    }

    suspend fun getPosterUrl(anilistId: Int): String? {
        return getImages(anilistId).posterUrl
    }
}
