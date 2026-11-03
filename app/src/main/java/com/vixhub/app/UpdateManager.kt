package com.vixhub.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class RemoteRelease(
    val tag: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

object UpdateManager {
    private const val OWNER = "VegsPro"
    private const val REPO = "VixHub"
    private const val CHANNEL = "vixhub_update"
    private const val NOTIF_ID = 4301

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun checkLatest(): Result<RemoteRelease?> = withContext(Dispatchers.IO) {
        try {
            // 1) GitHub Releases
            val req = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@withContext Result.success(null)
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body?.string() ?: return@withContext Result.success(null)
                val obj = JSONObject(body)
                val tag = obj.optString("tag_name", "")
                val assets = obj.optJSONArray("assets") ?: return@withContext Result.success(null)
                var apkUrl = ""
                var name = ""
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name", "")
                    if (n.endsWith(".apk", true)) {
                        apkUrl = a.optString("browser_download_url", "")
                        name = n
                        break
                    }
                }
                if (apkUrl.isBlank()) return@withContext Result.success(null)
                // parse versionCode from tag like v12 or 1.2.0-12
                val code = Regex("""(\d+)${'$'}""").find(tag.replace(".", ""))?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)""").findAll(tag).lastOrNull()?.value?.toIntOrNull()
                    ?: 0
                val vName = tag.removePrefix("v")
                Result.success(RemoteRelease(tag, code, vName, apkUrl))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cachedApk(context: Context): File? {
        val prefs = AppPrefs(context)
        val path = prefs.pendingApkPath() ?: return null
        val f = File(path)
        return if (f.exists() && f.length() > 1000) f else null
    }

    fun clearCache(context: Context) {
        AppPrefs(context).setPendingApkPath(null)
        AppPrefs(context).setPendingApkVersion(0)
        try {
            File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        versionCode: Int,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "VixHub-$versionCode.apk")
            if (out.exists()) out.delete()

            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body ?: return@withContext Result.failure(Exception("Пустой ответ"))
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            sum += read
                            onProgress(sum, total)
                        }
                    }
                }
            }
            AppPrefs(context).setPendingApkPath(out.absolutePath)
            AppPrefs(context).setPendingApkVersion(versionCode)
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun createUpdateChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Обновления", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun notifyProgress(context: Context, title: String, text: String, progress: Int, max: Int, ongoing: Boolean) {
        createUpdateChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (max > 0) b.setProgress(max, progress, false)
        else if (ongoing) b.setProgress(0, 0, true)
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build())
    }

    fun notifyDone(context: Context, text: String) {
        createUpdateChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle("VixHub · обновление готово")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID)
        nm.notify(NOTIF_ID + 1, n)
    }

    fun cancelNotif(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
