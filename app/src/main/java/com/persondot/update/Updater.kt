package com.persondot.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.persondot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização do app pelo GitHub Releases.
 * Cada release (tag vX.Y.Z) traz um APK; o app compara com a versão instalada, baixa e abre o instalador do Android.
 */
object Updater {

    data class Release(val tag: String, val versionCode: Int, val notes: String, val apkUrl: String, val sizeBytes: Long)

    enum class InstallResult { STARTED, NEEDS_PERMISSION }

    /** v1.2.3 -> 10203 (mesma conta que o CI usa para o versionCode). */
    fun versionCodeOf(tag: String): Int {
        val p = tag.removePrefix("v").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        return p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
    }

    fun isNewer(r: Release): Boolean = r.versionCode > BuildConfig.VERSION_CODE

    /** Última release publicada, ou null se ainda não existe nenhuma. Lança IOException em falha de rede. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "PersonDot-App")
            conn.connectTimeout = 10_000; conn.readTimeout = 15_000
            when (val code = conn.responseCode) {
                404 -> return@withContext null
                !in 200..299 -> throw IOException("GitHub respondeu $code")
            }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = o.optJSONArray("assets") ?: return@withContext null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            val tag = o.getString("tag_name")
            Release(tag, versionCodeOf(tag), o.optString("body", ""), apk.getString("browser_download_url"), apk.optLong("size", 0L))
        } finally { conn.disconnect() }
    }

    /** Baixa o APK para a pasta de cache do app, reportando o progresso de 0 a 1. */
    suspend fun download(ctx: Context, rel: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "PersonDot-${rel.tag}.apk")
        val conn = URL(rel.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", "PersonDot-App")
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) throw IOException("Download falhou (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.sizeBytes
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { conn.disconnect() }
        out
    }

    /** Abre o instalador do Android. Na primeira vez o usuário precisa permitir "instalar apps desta fonte". */
    fun install(ctx: Context, apk: File): InstallResult {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return InstallResult.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return InstallResult.STARTED
    }
}
