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
                if (a.optString("name").equals(APK_ASSET_NAME, ignoreCase = true)) {
                    val apkUrl = a.optString("browser_download_url")
                    val size = a.optLong("size", 0L)
                    if (isTrustedReleaseUrl(apkUrl) && size in 1L..MAX_APK_BYTES) {
                        return UpdateInfo(
                            version = version,
                            name = json.optString("name").ifBlank { "Versión $version" },
                            notes = json.optString("body").trim(),
                            apkUrl = apkUrl,
                            sizeBytes = size,
                        )
                    }
                }
            }
            return null
        }
    }

    /** Descarga el APK a caché, limita su tamaño y solo acepta enlaces de Releases de este repo. */
    fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File {
        if (!isTrustedReleaseUrl(info.apkUrl)) throw IOException("Enlace de actualización no confiable")
        if (info.sizeBytes !in 1..MAX_APK_BYTES) throw IOException("Tamaño de actualización inválido")

        val dir = File(ctx.cacheDir, "updates")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("No se pudo preparar la descarga")
        val file = File(dir, APK_ASSET_NAME)
        val tempFile = File(dir, "$APK_ASSET_NAME.part")
        tempFile.delete()

        try {
            val req = Request.Builder().url(info.apkUrl).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Respuesta vacía")
                val contentLength = body.contentLength()
                if (contentLength > MAX_APK_BYTES) throw IOException("Actualización demasiado grande")
                if (contentLength > 0 && contentLength != info.sizeBytes) {
                    throw IOException("El tamaño de la actualización no coincide")
                }
                val total = contentLength.takeIf { it > 0 } ?: info.sizeBytes
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            done += n
                            if (done > MAX_APK_BYTES || done > info.sizeBytes) {
                                throw IOException("Actualización demasiado grande")
                            }
                            output.write(buf, 0, n)
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (tempFile.length() != info.sizeBytes) throw IOException("Descarga incompleta")
            if (file.exists() && !file.delete()) throw IOException("No se pudo reemplazar el APK anterior")
            if (!tempFile.renameTo(file)) throw IOException("No se pudo finalizar la descarga")
            return file
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun isTrustedReleaseUrl(raw: String): Boolean {
        val uri = Uri.parse(raw)
        val expectedPrefix = "/$REPO/releases/download/"
        return uri.scheme == "https" &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1 &&
            uri.path?.startsWith(expectedPrefix, ignoreCase = true) == true
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
        const val REPO = "emasanta66-beep/XtreamPlayer."
        const val APK_ASSET_NAME = "XtreamPlayer.apk"
        const val MAX_APK_BYTES = 150L * 1024 * 1024
        const val KEY_LAST_CHECK = "last_check"
        /** Cada cuánto se busca una actualización al abrir la app: 3 días. */
        val CHECK_EVERY_MS = TimeUnit.DAYS.toMillis(3)
    }
}
