package ani.sanin.connections.trakt

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Base64
import ani.sanin.openLinkInBrowser
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object TraktAuth {
    const val CLIENT_ID = "w5-QcQAhtlujinZFPFddAnY5B_huGoWModzSoMM6VjM"
    private const val CLIENT_SECRET = "8ozV_Z-5SFz2_tChyEBd9w1BmMkECMSw9my5DPjtKOI"
    private const val REDIRECT_URI = "psycho-trakt://oauth"
    private val json = Json { ignoreUnknownKeys = true }

    var accessToken: String? = null
        private set
    var username: String? = null
        private set

    fun loadSavedToken(): Boolean {
        accessToken = PrefManager.getNullableVal(PrefName.TraktAccessToken, null)
        username = PrefManager.getNullableVal(PrefName.TraktUsername, null)
        return !accessToken.isNullOrBlank()
    }

    fun saveToken(token: String, user: String) {
        accessToken = token
        username = user
        PrefManager.setVal(PrefName.TraktAccessToken, token)
        PrefManager.setVal(PrefName.TraktUsername, user)
    }

    fun logout() {
        accessToken = null
        username = null
        PrefManager.setVal(PrefName.TraktAccessToken, null as String?)
        PrefManager.setVal(PrefName.TraktUsername, null as String?)
    }

    fun isLoggedIn(): Boolean = !accessToken.isNullOrBlank()

    fun loginIntent(context: Context) {
        val authUrl = "https://trakt.tv/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$CLIENT_ID" +
                "&redirect_uri=$REDIRECT_URI"
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
        } catch (_: ActivityNotFoundException) {
            openLinkInBrowser(authUrl)
        }
    }

    suspend fun exchangeCode(authorizationCode: String): Boolean {
        return try {
            val requestBody = json.encodeToString(
                TokenExchangeRequest(
                    code = authorizationCode,
                    clientId = CLIENT_ID,
                    clientSecret = CLIENT_SECRET,
                    redirectUri = REDIRECT_URI,
                    grantType = "authorization_code"
                )
            )
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/token")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody(mediaType))
                .build()
            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return false
            if (response.code != 200) {
                Logger.log("Trakt token exchange failed: $response.code $body")
                return false
            }
            val result = json.decodeFromString<TokenExchangeResponse>(body)
            saveToken(result.accessToken, result.user?.username ?: "Trakt User")
            true
        } catch (e: Exception) {
            Logger.log("Trakt token exchange error: ${e.message}")
            false
        }
    }

    suspend fun fetchUsername(): String? {
        val token = accessToken ?: return null
        return try {
            val request = Request.Builder()
                .url("https://api.trakt.tv/users/me")
                .addHeader("Content-Type", "application/json")
                .addHeader("trakt-api-key", CLIENT_ID)
                .addHeader("trakt-api-version", "2")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val response = OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (response.code != 200) return null
            val user = json.decodeFromString<TraktUserResponse>(body)
            user.username
        } catch (e: Exception) {
            Logger.log("Trakt fetch user error: ${e.message}")
            null
        }
    }
}

@Serializable
data class TokenExchangeRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("grant_type") val grantType: String
)

@Serializable
data class TokenExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    val user: TraktUserResponse? = null
)

@Serializable
data class TraktUserResponse(
    val username: String,
    val name: String? = null,
    @SerialName("ids") val ids: TraktUserIds? = null
)

@Serializable
data class TraktUserIds(
    val slug: String? = null
)
