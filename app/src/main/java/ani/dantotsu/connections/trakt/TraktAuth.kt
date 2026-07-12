package ani.dantotsu.connections.trakt

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import ani.dantotsu.openLinkInBrowser
import kotlinx.serialization.SerialName
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.Serializable

object TraktAuth {
    const val CLIENT_ID = "w5-QcQAhtlujinZFPFddAnY5B_huGoWModzSoMM6VjM"
    private const val CLIENT_SECRET = "8ozV_Z-5SFz2_tChyEBd9w1BmMkECMSw9my5DPjtKOI"
    private const val REDIRECT_URI = "psycho-trakt://oauth"

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
            val requestBody = kotlinx.serialization.json.Json.encodeToString(
                TokenExchangeRequest(
                    code = authorizationCode,
                    clientId = CLIENT_ID,
                    clientSecret = CLIENT_SECRET,
                    redirectUri = REDIRECT_URI,
                    grantType = "authorization_code"
                )
            )
            val request = okhttp3.Request.Builder()
                .url("https://api.trakt.tv/oauth/token")
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), requestBody))
                .build()
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return false
            if (response.code != 200) {
                Logger.log("Trakt token exchange failed: $response.code $body")
                return false
            }
            val result = kotlinx.serialization.json.Json
                { ignoreUnknownKeys = true }
                .decodeFromString<TokenExchangeResponse>(body)
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
            val request = okhttp3.Request.Builder()
                .url("https://api.trakt.tv/users/me")
                .addHeader("Content-Type", "application/json")
                .addHeader("trakt-api-key", CLIENT_ID)
                .addHeader("trakt-api-version", "2")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (response.code != 200) return null
            val user = kotlinx.serialization.json.Json
                { ignoreUnknownKeys = true }
                .decodeFromString<TraktUserResponse>(body)
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
