package ani.dantotsu.connections.trakt

import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TraktAPI {
    private const val BASE_URL = "https://api.trakt.tv"
    private const val HARDCODED_CLIENT_ID = "w5-QcQAhtlujinZFPFddAnY5B_huGoWModzSoMM6VjM"
    private val json = Json { ignoreUnknownKeys = true }

    private val clientId: String
        get() = PrefManager.getNullableVal(PrefName.TraktClientId, null)
            ?.takeIf { it.isNotBlank() }
            ?: HARDCODED_CLIENT_ID

    private val client get() = Injekt.get<NetworkHelper>().client

    private fun headers(): Map<String, String> = mapOf(
        "trakt-api-key" to clientId,
        "trakt-api-version" to "2",
        "Content-Type" to "application/json"
    )

    private fun isEnabled(): Boolean =
        PrefManager.getVal<Int>(PrefName.TraktCommentsEnabled) == 1

    suspend fun searchByImdb(imdbId: String): TraktSearchResult? = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null
        try {
            val request = okhttp3.Request.Builder()
                .url("$BASE_URL/search/imdb/$imdbId?type=show,movie")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val results = json.decodeFromString<List<TraktSearchResult>>(body)
            results.firstOrNull()
        } catch (e: Exception) {
            Logger.log("Trakt search error: ${e.message}")
            null
        }
    }

    suspend fun getComments(
        type: String,
        traktId: Int,
        page: Int = 1,
        sort: String = "newest"
    ): List<TraktComment> = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext emptyList()
        try {
            val request = okhttp3.Request.Builder()
                .url("$BASE_URL/$type/$traktId/comments?sort=$sort&page=$page&limit=25")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<TraktComment>>(body)
        } catch (e: Exception) {
            Logger.log("Trakt comments error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getReplies(commentId: Int): List<TraktComment> = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext emptyList()
        try {
            val request = okhttp3.Request.Builder()
                .url("$BASE_URL/comments/$commentId/replies?limit=25")
                .apply { headers().forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<TraktComment>>(body)
        } catch (e: Exception) {
            Logger.log("Trakt replies error: ${e.message}")
            emptyList()
        }
    }
}

@Serializable
data class TraktSearchResult(
    @SerialName("type") val type: String,
    @SerialName("score") val score: Double? = null,
    @SerialName("show") val show: TraktShow? = null,
    @SerialName("movie") val movie: TraktMovie? = null
) {
    val traktId: Int?
        get() = show?.ids?.trakt ?: movie?.ids?.trakt
    val mediaType: String?
        get() = if (show != null) "shows" else if (movie != null) "movies" else null
    val title: String?
        get() = show?.title ?: movie?.title
}

@Serializable
data class TraktShow(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("ids") val ids: TraktIds
)

@Serializable
data class TraktMovie(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("ids") val ids: TraktIds
)

@Serializable
data class TraktIds(
    @SerialName("trakt") val trakt: Int,
    @SerialName("slug") val slug: String? = null,
    @SerialName("imdb") val imdb: String? = null,
    @SerialName("tmdb") val tmdb: Int? = null
)

@Serializable
data class TraktComment(
    @SerialName("id") val id: Int,
    @SerialName("parent_id") val parentId: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("comment") val comment: String,
    @SerialName("spoiler") val spoiler: Boolean = false,
    @SerialName("review") val review: Boolean = false,
    @SerialName("replies") val replies: Int = 0,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("user_stats") val userStats: TraktUserStats? = null,
    @SerialName("user") val user: TraktUser
)

@Serializable
data class TraktUserStats(
    @SerialName("rating") val rating: Int? = null,
    @SerialName("play_count") val playCount: Int? = null,
    @SerialName("completed_count") val completedCount: Int? = null
)

@Serializable
data class TraktUser(
    @SerialName("username") val username: String,
    @SerialName("name") val name: String? = null,
    @SerialName("vip") val vip: Boolean = false,
    @SerialName("vip_ep") val vipEp: Boolean = false,
    @SerialName("images") val images: TraktUserImages? = null
)

@Serializable
data class TraktUserImages(
    @SerialName("avatar") val avatar: TraktAvatar? = null
)

@Serializable
data class TraktAvatar(
    @SerialName("full") val full: String? = null
)
