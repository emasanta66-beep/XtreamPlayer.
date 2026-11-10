package app.xtreamplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
        ensureChannel()
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

    /**
     * Android 15 avisa por aquí antes de matar un servicio dataSync que superó su límite (~6 h).
     * Se pausan las descargas activas (se reanudan luego con Range) y el servicio se cierra solo.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        pauseActiveDownloads(startId)
    }

    /** Variante de Android 14 (servicios cortos): mismo cierre limpio. */
    override fun onTimeout(startId: Int) {
        pauseActiveDownloads(startId)
    }

    private fun pauseActiveDownloads(startId: Int) {
        DownloadCenter.items
            .filter { it.status == DlStatus.DOWNLOADING || it.status == DlStatus.QUEUED }
            .map { it.key }
            .forEach { DownloadCenter.pause(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        // stopSelf(startId) detiene este arranque concreto, como recomienda Android para onTimeout.
        stopSelf(startId)
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // crearNotificationChannel es idempotente; lo llamamos una vez por arranque del servicio
        // en lugar de repetirlo cada segundo dentro del bucle de la notificación.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val items = DownloadCenter.items
        val active = items.firstOrNull { it.status == DlStatus.DOWNLOADING }
        val pending = items.count { it.status == DlStatus.QUEUED || it.status == DlStatus.DOWNLOADING }
        val title = active?.let {
            if (it.subtitle.isEmpty()) it.title else "${it.title} · ${it.subtitle}"
        } ?: getString(R.string.downloads_channel)
        // Tocar la notificación lleva a la pantalla principal (la app ya está viva descargando).
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(this, CHANNEL)
        b.setSmallIcon(R.drawable.ic_stat_download)
            .setContentIntent(contentIntent)
            .setContentTitle(title)
            .setContentText(
                if (pending > 1) getString(R.string.downloads_notification_queued, pending)
                else getString(R.string.downloads_notification_downloading)
            )
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
