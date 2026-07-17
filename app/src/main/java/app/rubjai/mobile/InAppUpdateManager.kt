package app.rubjai.mobile

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class AppUpdateInfo(val version: String, val releaseNotes: String, val apkUrl: String, val apkName: String)

class InAppUpdateManager {
    private val main = Handler(Looper.getMainLooper())

    fun checkForUpdate(currentVersion: String, result: (AppUpdateInfo?) -> Unit) = thread(name = "rubjai-update-check") {
        val update = runCatching {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.inputStream.bufferedReader().use { reader ->
                val release = JSONObject(reader.readText())
                val version = release.getString("tag_name").removePrefix("v")
                if (compareVersions(version, currentVersion) <= 0) return@use null

                val assets = release.getJSONArray("assets")
                val assetObjects = (0 until assets.length()).map { assets.getJSONObject(it) }
                val apk = assetObjects.firstOrNull { it.getString("name").endsWith(".apk", true) } ?: return@use null
                val thaiNotes = assetObjects
                    .firstOrNull { it.getString("name").equals(THAI_NOTES_ASSET, true) }
                    ?.getString("browser_download_url")
                    ?.let(::downloadText)
                    .orEmpty()

                AppUpdateInfo(
                    version = version,
                    releaseNotes = thaiNotes.ifBlank { "ปรับปรุง RubJai ให้เสถียรขึ้น" },
                    apkUrl = apk.getString("browser_download_url"),
                    apkName = apk.getString("name"),
                )
            }
        }.getOrNull()
        main.post { result(update) }
    }

    fun canRequestInstall(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }
    }

    fun download(context: Context, update: AppUpdateInfo, progress: (Float) -> Unit, complete: (Uri?) -> Unit) {
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(
            DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle("RubJai ${update.version}")
                .setDescription("กำลังดาวน์โหลดอัปเดต")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, update.apkName)
        )
        thread(name = "rubjai-update-download") {
            var success = false
            while (true) {
                val finished = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) return@use true
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) main.post { progress(done.toFloat() / total) }
                    success = status == DownloadManager.STATUS_SUCCESSFUL
                    success || status == DownloadManager.STATUS_FAILED
                }
                if (finished) break
                Thread.sleep(300)
            }
            main.post { complete(if (success) manager.getUriForDownloadedFile(id) else null) }
        }
    }

    fun launchInstaller(context: Context, uri: Uri) = context.startActivity(
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        return (0 until maxOf(left.size, right.size)).firstNotNullOfOrNull { index ->
            (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0).takeIf { it != 0 }
        } ?: 0
    }

    private fun downloadText(url: String): String = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.inputStream.bufferedReader().use { it.readText() }.trim()
    }.getOrDefault("")

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/phiraponsuk1131/RubJai/releases/latest"
        private const val THAI_NOTES_ASSET = "APP_UPDATE_NOTES_TH.md"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
