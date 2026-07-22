package ani.sanin.extension.cloudstream

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import ani.sanin.media.MediaType
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object CloudstreamManager {

    private const val CLOUDSTREAM_FEATURE = "cloudstream.extension"

    private val _installedFlow = MutableStateFlow(emptyList<CloudstreamInstalledExtension>())
    val installedFlow = _installedFlow.asStateFlow()

    private val _reposFlow = MutableStateFlow(emptyList<CloudstreamRepo>())
    val reposFlow = _reposFlow.asStateFlow()

    private var installer: ExtensionInstaller? = null

    fun init(context: Context) {
        installer = ExtensionInstaller(context)
        loadInstalledExtensions(context)
        CloudstreamUpdateWorker.schedule(context)
    }

    fun loadInstalledExtensions(context: Context) {
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_META_DATA or
                PackageManager.GET_SIGNATURES
            val installedPkgs = pm.getInstalledPackages(flags)
            val cloudstreamPkgs = installedPkgs.filter { isCloudstreamExtension(it) }

            val installed = cloudstreamPkgs.map { pkg ->
                val appInfo = try { pm.getApplicationInfo(pkg.packageName, PackageManager.GET_META_DATA) }
                    catch (_: Exception) { null }
                val name = appInfo?.let { pm.getApplicationLabel(it).toString() }
                    ?: pkg.packageName
                val versionName = pkg.versionName ?: "1.0.0"
                val versionCode = PackageInfoCompat.getLongVersionCode(pkg)
                val icon = try { pm.getApplicationIcon(pkg.packageName) } catch (_: Exception) { null }

                CloudstreamInstalledExtension(
                    name = name,
                    pkgName = pkg.packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    icon = icon,
                    repository = extractRepoUrl(pkg) ?: "",
                )
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

    fun installExtension(extension: CloudstreamAvailableExtension): Observable<InstallStep> {
        val inst = installer ?: return Observable.empty()
        return inst.downloadAndInstall(extension.apkUrl, extension.pkgName, extension.name, MediaType.ANIME)
    }

    fun updateExtension(extension: CloudstreamInstalledExtension): Observable<InstallStep> {
        val repos = _reposFlow.value
        val available = repos.flatMap { it.extensions }
            .find { it.pkgName == extension.pkgName } ?: return Observable.empty()
        return installExtension(available)
    }

    fun uninstallExtension(pkgName: String) {
        installer?.uninstallApk(pkgName)
    }

    fun onExtensionInstalled(pkgName: String) {
        val installed = _installedFlow.value.toMutableList()
        val repos = _reposFlow.value
        val available = repos.flatMap { it.extensions }.find { it.pkgName == pkgName }
        if (available != null) {
            installed.add(CloudstreamInstalledExtension(
                name = available.name,
                pkgName = available.pkgName,
                versionName = available.versionName,
                versionCode = available.versionCode,
                lang = available.lang,
                isNsfw = available.isNsfw,
                repository = available.repository,
            ))
            _installedFlow.value = installed
        }
    }

    fun onExtensionRemoved(pkgName: String) {
        _installedFlow.value = _installedFlow.value.filter { it.pkgName != pkgName }
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
