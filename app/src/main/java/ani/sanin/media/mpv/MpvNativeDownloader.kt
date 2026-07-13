package ani.sanin.media.mpv

import android.content.Context
import android.os.Build
import ani.sanin.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

object MpvNativeDownloader {

    private const val MPV_LIB_VERSION = "0.1.12"

    private val DOWNLOAD_BASE = "https://github.com/n4237074-creator/sanin-mpv-libs/releases/download/v$MPV_LIB_VERSION"

    val LOAD_ORDER = listOf(
        "libc++_shared.so",
        "libavutil.so",
        "libswresample.so",
        "libswscale.so",
        "libxml2.so",
        "libavcodec.so",
        "libavformat.so",
        "libavfilter.so",
        "libavdevice.so",
        "libplayer.so",
        "libmpv.so",
    )

    private fun archDir(): String {
        return Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }

    private fun zipFileName(): String {
        return "mpv-native-${archDir()}.zip"
    }

    fun getLibDir(context: Context): File {
        return File(context.filesDir, "mpv").also { it.mkdirs() }
    }

    fun getLibFile(context: Context): File {
        return File(getLibDir(context), "libmpv.so")
    }

    fun isDownloaded(context: Context): Boolean {
        return File(getLibDir(context), "libmpv.so").exists()
    }

    fun deleteLib(context: Context) {
        val dir = getLibDir(context)
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun download(context: Context, onProgress: (Float) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val libDir = getLibDir(context)
                val zipFile = File(libDir, zipFileName())

                zipFile.delete()

                val url = "$DOWNLOAD_BASE/${zipFileName()}"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = zipFile.outputStream()

                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                    bytesRead += read
                    if (totalBytes > 0) {
                        onProgress(bytesRead.toFloat() / totalBytes.toFloat())
                    }
                }
                outputStream.close()
                inputStream.close()

                ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(libDir, entry.name)
                            outFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }

                zipFile.delete()
                Result.success(File(libDir, "libmpv.so"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun loadNativeLibs(context: Context): Boolean {
        val libDir = getLibDir(context)
        return try {
            for (libName in LOAD_ORDER) {
                val libFile = File(libDir, libName)
                if (libFile.exists()) {
                    System.load(libFile.absolutePath)
                }
            }
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
