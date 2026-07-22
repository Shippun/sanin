package ani.sanin.extension.common

import android.graphics.drawable.Drawable

enum class ExtensionEcosystem { ANIYOMI, CLOUDSTREAM, UNKNOWN }

data class CommonExtension(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val isNsfw: Boolean,
    val icon: Drawable?,
    val iconUrl: String?,
    val hasUpdate: Boolean = false,
    val isObsolete: Boolean = false,
    val ecosystem: ExtensionEcosystem,
    val source: Any?,
)
