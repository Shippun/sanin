package ani.sanin.extension.cloudstream

import android.graphics.drawable.Drawable

data class CloudstreamRepo(
    val url: String,
    val name: String,
    val description: String = "",
    val website: String = "",
    val icon: String? = null,
    val version: Int = 0,
    val extensions: List<CloudstreamAvailableExtension> = emptyList(),
)

data class CloudstreamAvailableExtension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val isNsfw: Boolean = false,
    val description: String = "",
    val iconUrl: String? = null,
    val apkUrl: String,
    val repository: String,
    val category: String = "",
)

data class CloudstreamInstalledExtension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String = "",
    val isNsfw: Boolean = false,
    val icon: Drawable? = null,
    val hasUpdate: Boolean = false,
    val repository: String = "",
)

data class CloudstreamRepoCategory(
    val name: String,
    val extensions: List<CloudstreamAvailableExtension>,
)

data class CloudstreamRepoJson(
    val name: String = "",
    val description: String = "",
    val website: String = "",
    val icon: String? = null,
    val version: Int = 0,
    val packs: List<CloudstreamPackJson> = emptyList(),
)

data class CloudstreamPackJson(
    val name: String = "",
    val pkg: String = "",
    val version: Int = 0,
    val versionName: String = "",
    val lang: String = "",
    val nsfw: Boolean = false,
    val description: String = "",
    val icon: String? = null,
    val apk: String = "",
    val category: String = "",
    val author: String? = null,
    val file: String? = null,
)
