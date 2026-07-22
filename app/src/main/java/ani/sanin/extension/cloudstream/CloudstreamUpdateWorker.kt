package ani.sanin.extension.cloudstream

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ani.sanin.util.Logger
import java.time.Duration

class CloudstreamUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        return try {
            kotlinx.coroutines.runBlocking {
                CloudstreamManager.refreshRepos()
            }
            Logger.log("Cloudstream auto-update completed")
            Result.success()
        } catch (e: Exception) {
            Logger.log("Cloudstream auto-update failed")
            Logger.log(e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cloudstream_auto_update"
        private const val PREF_NAME = "cloudstream_update_prefs"
        private const val KEY_LAST_CHECK = "last_check_ms"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequest.Builder<CloudstreamUpdateWorker>(
                Duration.ofHours(24)
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun isScheduled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            return System.currentTimeMillis() - lastCheck < 24 * 60 * 60 * 1000L
        }

        fun recordCheck(context: Context) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply()
        }

        fun triggerManualUpdate(context: Context) {
            kotlinx.coroutines.runBlocking {
                CloudstreamManager.refreshRepos()
            }
            recordCheck(context)
        }
    }
}
