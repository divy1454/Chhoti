package com.divy.chhoti

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Self-updater. Every push to GitHub builds a release tagged v<number> with the APK attached.
 * The app compares that number with its own versionCode and, if newer, downloads the APK and
 * hands it to Android's installer: you just tap "Update". No re-downloading from a browser.
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

    /** Streams the APK straight into an install session; Android then asks you to confirm. */
    suspend fun install(context: Context, r: Release, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            Claude.http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
                val body = resp.body ?: throw IOException("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: r.size
                session.openWrite("chhoti.apk", 0, total).use { out ->
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
                        session.fsync(out)
                    }
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallReceiver::class.java), flags
            )
            session.commit(pi.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app restarts on the new version
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                Toast.makeText(context, "Update failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
