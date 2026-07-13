package ani.sanin.connections

import android.content.Context
import ani.sanin.Mapper
import ani.sanin.client
import ani.sanin.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

object LogoApi {
    private const val ANIZIP_API = "https://api.ani.zip/mappings?anilist_id="
    private var cacheDir: File? = null
    private const val CACHE_FILE = "logos.json"

    private val memoryCache = HashMap<Int, String>()
    private var diskLoaded = false

    fun init(context: Context) {
        if (cacheDir != null) return
        cacheDir = File(context.cacheDir, "logo_cache").also { it.mkdirs() }
    }

    private fun getCacheFile(): File? {
        return cacheDir?.let { File(it, CACHE_FILE) }
    }

    private fun loadFromDisk() {
        if (diskLoaded) return
        diskLoaded = true
        try {
            val file = getCacheFile() ?: return
            if (!file.exists()) return
            val raw = file.readText()
            val map = Mapper.json.decodeFromString<Map<String, String>>(raw)
            map.forEach { (k, v) -> memoryCache[k.toInt()] = v }
            Logger.log("LogoApi: loaded ${map.size} cached logos")
        } catch (e: Exception) {
            Logger.log("LogoApi: disk load error — ${e.message}")
        }
    }

    private fun saveToDisk() {
        try {
            val file = getCacheFile() ?: return
            val json = Mapper.json.encodeToString(memoryCache.mapKeys { it.key.toString() })
            file.writeText(json)
        } catch (e: Exception) {
            Logger.log("LogoApi: disk save error — ${e.message}")
        }
    }

    suspend fun getLogoUrl(anilistId: Int): String? {
        if (!diskLoaded) withContext(Dispatchers.IO) { loadFromDisk() }
        memoryCache[anilistId]?.let { return it.ifEmpty { null } }

        return withContext(Dispatchers.IO) {
            try {
                val response = client.get("$ANIZIP_API$anilistId")
                val json = Mapper.json.parseToJsonElement(response.text)
                val url = extractLogoUrl(json)
                memoryCache[anilistId] = url ?: ""
                saveToDisk()
                Logger.log("LogoApi: $anilistId → ${url ?: "no logo"}")
                url
            } catch (e: Exception) {
                Logger.log("LogoApi: fetch error for $anilistId — ${e.message}")
                null
            }
        }
    }

    private fun extractLogoUrl(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> {
                val logoKeys = listOf("clearlogo", "clearLogo", "logo", "logoImage")
                val direct = logoKeys.firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)?.contentOrNull
                        ?.takeIf { it.startsWith("http") }
                }
                if (direct != null) return direct

                val imagesArray =
                    (element["mappings"] as? JsonObject)?.get("images") as? JsonArray
                        ?: element["images"] as? JsonArray

                imagesArray?.filterIsInstance<JsonObject>()
                    ?.firstOrNull { img ->
                        (img["coverType"] as? JsonPrimitive)?.contentOrNull
                            ?.equals("Clearlogo", ignoreCase = true) == true
                    }
                    ?.let { (it["url"] as? JsonPrimitive)?.contentOrNull?.takeIf { u -> u.startsWith("http") } }
            }
            is JsonArray -> {
                element.filterIsInstance<JsonObject>().firstNotNullOfOrNull { extractLogoUrl(it) }
            }
            else -> null
        }
    }

    fun clearCache() {
        memoryCache.clear()
        diskLoaded = false
        getCacheFile()?.delete()
    }
}
