package ani.sanin.extension.cloudstream

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object CloudstreamManager {

    private const val CLOUDSTREAM_FEATURE = "cloudstream.extension"
    private const val EXTENSIONS_DIR = "cloudstream-extensions"

    private val _installedFlow = MutableStateFlow(emptyList<CloudstreamInstalledExtension>())
    val installedFlow = _installedFlow.asStateFlow()

    private val _reposFlow = MutableStateFlow(emptyList<CloudstreamRepo>())
    val reposFlow = _reposFlow.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context
        loadInstalledExtensions(context)
        CloudstreamUpdateWorker.schedule(context)
    }

    fun loadInstalledExtensions(context: Context) {
        try {
            val installed = mutableListOf<CloudstreamInstalledExtension>()
            val seenPkgs = mutableSetOf<String>()

            // Scan system-installed packages with cloudstream feature
            val pm = context.packageManager
            val flags = PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_META_DATA or
                PackageManager.GET_SIGNATURES
            val installedPkgs = pm.getInstalledPackages(flags)
            val cloudstreamPkgs = installedPkgs.filter { isCloudstreamExtension(it) }

            for (pkg in cloudstreamPkgs) {
                val appInfo = try { pm.getApplicationInfo(pkg.packageName, PackageManager.GET_META_DATA) }
                    catch (_: Exception) { null }
                val name = appInfo?.let { pm.getApplicationLabel(it).toString() }
                    ?: pkg.packageName
                val versionName = pkg.versionName ?: "1.0.0"
                val versionCode = PackageInfoCompat.getLongVersionCode(pkg)
                val icon = try { pm.getApplicationIcon(pkg.packageName) } catch (_: Exception) { null }

                installed.add(CloudstreamInstalledExtension(
                    name = name,
                    pkgName = pkg.packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    icon = icon,
                    repository = extractRepoUrl(pkg) ?: "",
                ))
                seenPkgs.add(pkg.packageName)
            }

            // Scan locally-downloaded .cs3 files
            val extDir = getExtensionsDir(context)
            if (extDir.exists()) {
                val pkgNames = PrefManager.getVal<Set<String>>(PrefName.CloudstreamInstalledExtensions)
                for (pkgName in pkgNames) {
                    if (pkgName in seenPkgs) continue
                    val cs3File = File(extDir, "$pkgName.cs3")
                    if (!cs3File.exists()) continue

                    val repo = PrefManager.getVal<Set<String>>(PrefName.CloudstreamExtensionRepos)
                        .firstNotNullOfOrNull { repoUrl ->
                            reposFlow.value.find { it.url == repoUrl }
                                ?.extensions?.find { it.pkgName == pkgName }
                        }

                    installed.add(CloudstreamInstalledExtension(
                        name = repo?.name ?: pkgName,
                        pkgName = pkgName,
                        versionName = repo?.versionName ?: "1.0.0",
                        versionCode = repo?.versionCode ?: 0L,
                        lang = repo?.lang ?: "",
                        isNsfw = repo?.isNsfw ?: false,
                        repository = repo?.repository ?: "",
                        filePath = cs3File.absolutePath,
                    ))
                    seenPkgs.add(pkgName)
                }
            }

            _installedFlow.value = installed
        } catch (e: Exception) {
            Logger.log("Failed to load Cloudstream extensions")
            Logger.log(e)
        }
    }

    private fun isCloudstreamExtension(pkg: android.content.pm.PackageInfo): Boolean {
        return pkg.reqFeatures.orEmpty().any { feature ->
            feature.name.equals(CLOUDSTREAM_FEATURE, ignoreCase = true) ||
            pkg.packageName.startsWith("com.cloudstream.")
        }
    }

    private fun extractRepoUrl(pkg: android.content.pm.PackageInfo): String? {
        return pkg.applicationInfo?.metaData?.getString("cloudstream.repo")
    }

    suspend fun refreshRepos() {
        val repos = CloudstreamApi.fetchAllRepos()
        _reposFlow.value = repos
        updateInstalledStatuses()
    }

    private fun updateInstalledStatuses() {
        val installed = _installedFlow.value.toMutableList()
        val repos = _reposFlow.value
        var changed = false

        for ((i, ext) in installed.withIndex()) {
            val available = repos.flatMap { it.extensions }
                .find { it.pkgName == ext.pkgName }
            if (available != null && available.versionCode > ext.versionCode) {
                installed[i] = ext.copy(hasUpdate = true)
                changed = true
            }
        }
        if (changed) _installedFlow.value = installed
    }

    suspend fun installExtension(extension: CloudstreamAvailableExtension) {
        val context = appContext ?: return
        withContext(Dispatchers.IO) {
            try {
                val networkService: NetworkHelper by Injekt.injectLazy()
                val response = networkService.client.newCall(GET(extension.apkUrl)).execute()
                if (!response.isSuccessful) {
                    Logger.log("Cloudstream install: failed to download ${extension.name}: HTTP ${response.code}")
                    return@withContext
                }
                val bytes = response.body.bytes()

                val extDir = getExtensionsDir(context)
                extDir.mkdirs()
                val outputFile = File(extDir, "${extension.pkgName}.cs3")
                outputFile.writeBytes(bytes)

                val installed = _installedFlow.value.toMutableList()
                installed.add(CloudstreamInstalledExtension(
                    name = extension.name,
                    pkgName = extension.pkgName,
                    versionName = extension.versionName,
                    versionCode = extension.versionCode,
                    lang = extension.lang,
                    isNsfw = extension.isNsfw,
                    repository = extension.repository,
                    filePath = outputFile.absolutePath,
                ))
                _installedFlow.value = installed

                val pkgNames = PrefManager.getVal<Set<String>>(PrefName.CloudstreamInstalledExtensions)
                    .plus(extension.pkgName)
                PrefManager.setVal(PrefName.CloudstreamInstalledExtensions, pkgNames)

                Logger.log("Cloudstream: installed ${extension.name} to ${outputFile.absolutePath}")
            } catch (e: Exception) {
                Logger.log("Cloudstream install failed for ${extension.name}: ${e.message}")
                Logger.log(e)
            }
        }
    }

    suspend fun updateExtension(extension: CloudstreamInstalledExtension) {
        val repos = _reposFlow.value
        val available = repos.flatMap { it.extensions }
            .find { it.pkgName == extension.pkgName } ?: return
        installExtension(available)
    }

    fun uninstallExtension(pkgName: String) {
        val context = appContext ?: return
        val extDir = getExtensionsDir(context)
        val cs3File = File(extDir, "$pkgName.cs3")
        if (cs3File.exists()) cs3File.delete()

        _installedFlow.value = _installedFlow.value.filter { it.pkgName != pkgName }
        val pkgNames = PrefManager.getVal<Set<String>>(PrefName.CloudstreamInstalledExtensions)
            .minus(pkgName)
        PrefManager.setVal(PrefName.CloudstreamInstalledExtensions, pkgNames)
    }

    private fun getExtensionsDir(context: Context): File {
        return File(context.filesDir, EXTENSIONS_DIR)
    }

    fun getCategoriesForRepo(url: String): List<CloudstreamRepoCategory> {
        val repo = _reposFlow.value.find { it.url == url } ?: return emptyList()
        return repo.extensions
            .groupBy { it.category.ifEmpty { "Uncategorized" } }
            .map { (category, exts) -> CloudstreamRepoCategory(category, exts) }
    }

    fun getExtensionsForRepo(url: String): List<CloudstreamAvailableExtension> {
        return _reposFlow.value.find { it.url == url }?.extensions ?: emptyList()
    }

    fun addRepo(url: String) {
        val repos = PrefManager.getVal<Set<String>>(PrefName.CloudstreamExtensionRepos).plus(url)
        PrefManager.setVal(PrefName.CloudstreamExtensionRepos, repos)
    }

    fun removeRepo(url: String) {
        val repos = PrefManager.getVal<Set<String>>(PrefName.CloudstreamExtensionRepos).minus(url)
        PrefManager.setVal(PrefName.CloudstreamExtensionRepos, repos)
    }

    fun getRepos(): List<String> {
        return PrefManager.getVal<Set<String>>(PrefName.CloudstreamExtensionRepos).toList()
    }
}
