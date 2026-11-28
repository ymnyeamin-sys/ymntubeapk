package org.schabi.newpipe

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

object YmnUpdateManager {
    private const val MANIFEST_URL = "https://ymnyeamin-sys.github.io/ymntubeapk/update.json"
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()

    @JvmStatic
    fun check(activity: MainActivity) {
        executor.execute {
            try {
                val request = Request.Builder().url(MANIFEST_URL)
                    .header("Cache-Control", "no-cache").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val json = JSONObject(response.body?.string() ?: return@use)
                    if (json.optString("packageName") != activity.packageName) return@use
                    val code = json.optInt("versionCode", 0)
                    if (code <= BuildConfig.VERSION_CODE) return@use
                    val version = json.optString("versionName")
                    val url = json.optString("downloadUrl")
                    val hash = json.optString("sha256")
                    val force = json.optString("mode") == "force"
                    val notes = json.optString("releaseNotes")
                    if (!url.startsWith("https://") || !hash.matches(Regex("[a-fA-F0-9]{64}"))) return@use
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        val message = "Version " + version + "\n\n" + notes + "\n\n" +
                            if (force) "This update is required." else "You can update now or later."
                        val dialog = AlertDialog.Builder(activity)
                            .setTitle("YmnTube update available")
                            .setMessage(message)
                            .setPositiveButton("Update") { _, _ -> download(activity, url, hash) }
                            .setCancelable(!force)
                        if (!force) dialog.setNegativeButton("Later", null)
                        dialog.show()
                    }
                }
            } catch (_: Exception) {
                // Keep the app usable when offline or when the update service is unavailable.
            }
        }
    }

    private fun download(activity: MainActivity, url: String, expectedHash: String) {
        val progress = AlertDialog.Builder(activity)
            .setTitle("Downloading update").setMessage("Please wait…")
            .setCancelable(false).create()
        progress.show()
        executor.execute {
            try {
                val file = File(activity.cacheDir, "updates/ymntube-update.apk")
                file.parentFile?.mkdirs()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed (" + response.code + ")")
                    val body = response.body ?: error("Empty download")
                    body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                val actualHash = MessageDigest.getInstance("SHA-256")
                    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, true)) {
                    file.delete()
                    error("APK verification failed")
                }
                activity.runOnUiThread {
                    progress.dismiss()
                    install(activity, file)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!activity.isFinishing) AlertDialog.Builder(activity)
                        .setTitle("Update failed").setMessage(e.message ?: "Please try again.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun install(activity: MainActivity, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.packageName)))
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".provider", apk)
        activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
