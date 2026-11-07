package app.xtreamplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.xtreamplayer.data.DlStatus
import app.xtreamplayer.data.DownloadCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Mantiene viva la app (con una notificación) mientras se descargan videos. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val first = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }
        loop?.cancel()
        loop = scope.launch {
            while (true) {
                delay(1000)
                if (!DownloadCenter.running) break
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification())
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val items = DownloadCenter.items
        val active = items.firstOrNull { it.status == DlStatus.DOWNLOADING }
        val pending = items.count { it.status == DlStatus.QUEUED || it.status == DlStatus.DOWNLOADING }
        val title = active?.let {
            if (it.subtitle.isEmpty()) it.title else "${it.title} · ${it.subtitle}"
        } ?: "Descargas"
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (pending > 1) "$pending descargas en cola" else "Descargando…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (active != null && active.total > 0) {
            b.setProgress(100, (active.bytes * 100 / active.total).toInt().coerceIn(0, 100), false)
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIF_ID = 7
    }
}
