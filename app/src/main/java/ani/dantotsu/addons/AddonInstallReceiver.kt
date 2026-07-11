package ani.dantotsu.addons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.extension.installer.Installer
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller

class AddonInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extensionInstaller = ExtensionInstaller.getInstaller(context)
        if (extensionInstaller != null) {
            val downloadId = intent.getLongExtra(ExtensionInstaller.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                val uri = intent.data
                extensionInstaller.addToQueue(null, downloadId, uri!!)
            }
        }
    }
}