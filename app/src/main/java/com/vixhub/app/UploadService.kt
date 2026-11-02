package com.vixhub.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UploadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val folder = intent.getStringExtra(EXTRA_FOLDER) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val key = intent.getStringExtra(EXTRA_KEY)
        val uris = intent.getStringArrayListExtra(EXTRA_URIS) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(NOTIF_ID, buildNotification(0, uris.size, "Подготовка…", 0, uris.size))

        job?.cancel()
        job = scope.launch {
            // URL мог протухнуть / не загружен в этом процессе
            if (ApiClient.getBaseUrl().isEmpty()) {
                ApiClient.fetchBaseUrl()
            }

            var ok = 0
            var fail = 0
            val total = uris.size

            for ((index, uriStr) in uris.withIndex()) {
                val current = index + 1
                try {
                    val uri = Uri.parse(uriStr)
                    val name = FileUtils.displayName(this@UploadService, uri)
                    updateNotification(current, total, "Загрузка: $name", current - 1, total)

                    val bytes = FileUtils.readBytes(this@UploadService, uri)
                    val result = ApiClient.uploadFile(folder, name, bytes, key)
                    if (result.isSuccess) {
                        ok++
                        updateNotification(current, total, "Готово: $name", current, total)
                    } else {
                        fail++
                        updateNotification(current, total, "Ошибка: $name", current, total)
                    }
                } catch (e: Exception) {
                    fail++
                    updateNotification(current, total, "Ошибка: ${e.message}", current, total)
                }
            }

            val summary = when {
                fail == 0 -> "Загружено $ok из $total"
                ok == 0 -> "Не удалось загрузить ($fail ошибок)"
                else -> "Загружено $ok из $total, ошибок: $fail"
            }
            showDoneNotification(summary, total)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Загрузка файлов",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Прогресс массовой загрузки в VixHub"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(current: Int, total: Int, text: String, progress: Int, max: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VixHub · $current/$total")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(max, progress, max == 0)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun updateNotification(current: Int, total: Int, text: String, progress: Int, max: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(current, total, text, progress, max))
    }

    private fun showDoneNotification(text: String, total: Int) {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VixHub · загрузка завершена")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setProgress(0, 0, false)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_DONE_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "vixhub_upload"
        const val NOTIF_ID = 4201
        const val NOTIF_DONE_ID = 4202
        const val EXTRA_FOLDER = "folder"
        const val EXTRA_KEY = "key"
        const val EXTRA_URIS = "uris"

        fun start(context: Context, folder: String, key: String?, uris: List<Uri>) {
            val i = Intent(context, UploadService::class.java).apply {
                putExtra(EXTRA_FOLDER, folder)
                putExtra(EXTRA_KEY, key)
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
