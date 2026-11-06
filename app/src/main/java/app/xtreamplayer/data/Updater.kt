package app.xtreamplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Una versión nueva publicada como Release en GitHub. */
data class UpdateInfo(
    val version: Int,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Busca versiones nuevas en los Releases de GitHub, las descarga y abre el instalador de Android.
 * Android no permite instalar sin que el usuario confirme: siempre pide permiso y un toque en "Instalar".
 */
class Updater(private val ctx: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val prefs = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)

    fun currentVersion(): Long {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun currentName(): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Ya pasaron los días configurados desde la última comprobación. */
    fun isCheckDue(): Boolean {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= CHECK_EVERY_MS
    }

    fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** Devuelve la versión nueva, o null si ya tienes la última. Lanza excepción si no hay conexión. */
    fun fetchLatest(): UpdateInfo? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { resp ->
            // 404 = todavía no hay ningún Release publicado.
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: return null)
            val version = json.optString("tag_name").removePrefix("v").toIntOrNull() ?: return null
            if (version <= currentVersion()) return null

            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return UpdateInfo(
                        version = version,
                        name = json.optString("name").ifBlank { "Versión $version" },
                        notes = json.optString("body").trim(),
                        apkUrl = a.getString("browser_download_url"),
                        sizeBytes = a.optLong("size", 0L),
                    )
                }
            }
            return null
        }
    }

    /** Descarga el APK a la carpeta de caché de la app. onProgress recibe de 0 a 1. */
    fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "XtreamPlayer.apk")

        val req = Request.Builder().url(info.apkUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Respuesta vacía")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (file.length() == 0L) throw IOException("Descarga vacía")
        return file
    }

    /** ¿Android ya permite que esta app instale APKs? (permiso "Instalar apps desconocidas"). */
    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Abre la pantalla de ajustes donde el usuario da el permiso a esta app. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private companion object {
        const val REPO = "emasanta66-beep/XtreamPlayer"
        const val KEY_LAST_CHECK = "last_check"
        /** Cada cuánto se busca una actualización al abrir la app: 3 días. */
        val CHECK_EVERY_MS = TimeUnit.DAYS.toMillis(3)
    }
}
