package com.divy.chhoti

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Self-updater. Every push to GitHub builds a release tagged v<number> with the APKs attached.
 * The app compares that number with its own versionCode and, if newer, downloads the APK and
 * opens Android's standard install screen, the same one a file manager uses, so you just tap Update.
 */
object Updater {
    data class Release(val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        Claude.http.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null // no releases yet
            if (!resp.isSuccessful) throw IOException("GitHub returned ${resp.code}")
            val j = JSONObject(resp.body!!.string())
            val code = j.optString("tag_name").removePrefix("v").toIntOrNull() ?: return@withContext null
            val assets = j.optJSONArray("assets") ?: return@withContext null
            // Stay on the same edition: chhoti-lite-v5.apk / chhoti-full-v5.apk
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk") && name.contains("-${BuildConfig.FLAVOR}-")) {
                    return@withContext Release(
                        code, j.optString("name", "v$code"), j.optString("body"),
                        a.getString("browser_download_url"), a.optLong("size", -1)
                    )
                }
            }
            null
        }
    }

    fun isNewer(r: Release?) = r != null && r.versionCode > BuildConfig.VERSION_CODE

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Downloads the APK (reporting %), checks it really is a newer Chhoti, and returns the file. */
    suspend fun download(context: Context, r: Release, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "chhoti-v${r.versionCode}.apk")
        Claude.http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: r.size
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct) }
                        }
                    }
                }
            }
        }
        // Sanity check before handing it to Android.
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IOException("The downloaded file isn't a valid APK.")
        if (info.packageName != context.packageName) throw IOException("The downloaded APK is not Chhoti.")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (code <= BuildConfig.VERSION_CODE) throw IOException("The downloaded APK isn't newer than this one.")
        file
    }

    /** Opens Android's standard "Do you want to update this app?" screen. */
    fun openInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
