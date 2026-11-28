package org.schabi.newpipe

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

object YmnUpdateManager {
    private const val MANIFEST_URL = "https://ymnyeamin-sys.github.io/ymntubeapk/update.json"
    private val executor = Executors.newSingleThreadExecutor()
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

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
                    val notes = json.optString("releaseNotes").trim()
                    if (!url.startsWith("https://") ||
                        !hash.matches(Regex("[a-fA-F0-9]{64}"))) return@use

                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        showUpdatePrompt(activity, version, notes, force, url, hash)
                    }
                }
            } catch (_: Exception) {
                // Keep the app usable when offline or when the update service is unavailable.
            }
        }
    }

    private fun showUpdatePrompt(
        activity: MainActivity,
        version: String,
        notes: String,
        force: Boolean,
        url: String,
        hash: String
    ) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 8))
            background = rounded(Color.WHITE, 24, Color.TRANSPARENT)
        }

        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 18), dp(activity, 22), dp(activity, 18), dp(activity, 22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 128, 105), Color.rgb(13, 82, 92))
            ).apply { cornerRadius = dp(activity, 18).toFloat() }
        }
        hero.addView(label(activity, "✦  Y M N T U B E", 12, Color.rgb(204, 255, 237), true))
        hero.addView(label(activity, "A fresh update is here", 21, Color.WHITE, true).apply {
            setPadding(0, dp(activity, 9), 0, dp(activity, 3))
        })
        hero.addView(label(activity, "Version $version", 14, Color.rgb(221, 255, 246), false))
        content.addView(hero)

        content.addView(label(activity, "What's new", 16, Color.rgb(30, 42, 48), true).apply {
            setPadding(0, dp(activity, 18), 0, dp(activity, 6))
        })
        content.addView(label(
            activity,
            notes.ifBlank { "Performance improvements and bug fixes." },
            14,
            Color.rgb(91, 103, 110),
            false
        ))
        content.addView(label(
            activity,
            if (force) "This update is required to continue using YmnTube."
            else "Update now to enjoy the latest improvements.",
            12,
            Color.rgb(18, 128, 105),
            true
        ).apply { setPadding(0, dp(activity, 14), 0, 0) })

        val builder = AlertDialog.Builder(activity)
            .setView(content)
            .setPositiveButton("Update now") { _, _ -> download(activity, url, hash) }
            .setCancelable(!force)
        if (!force) builder.setNegativeButton("Not now", null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(Color.rgb(18, 128, 105))
                isAllCaps = false
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(Color.rgb(110, 120, 125))
                isAllCaps = false
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.rgb(18, 128, 105))
            isAllCaps = false
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.rgb(110, 120, 125))
            isAllCaps = false
        }
    }

    private fun download(activity: MainActivity, url: String, expectedHash: String) {
        val progressLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), dp(activity, 24), dp(activity, 24), dp(activity, 22))
            background = rounded(Color.WHITE, 22, Color.TRANSPARENT)
        }
        progressLayout.addView(label(activity, "Updating YmnTube", 19, Color.rgb(30, 42, 48), true))
        progressLayout.addView(label(
            activity, "Downloading the latest version securely…", 13,
            Color.rgb(105, 116, 122), false
        ).apply { setPadding(0, dp(activity, 7), 0, dp(activity, 18)) })

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(18, 128, 105))
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.rgb(225, 238, 234))
        }
        progressLayout.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 8)
        ))

        val stats = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 12), 0, 0)
        }
        val downloadedText = label(activity, "0.00 MB / …", 13, Color.rgb(65, 78, 84), true)
        val percentText = label(activity, "0%", 14, Color.rgb(18, 128, 105), true).apply {
            gravity = Gravity.END
        }
        stats.addView(downloadedText, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(percentText, LinearLayout.LayoutParams(0, -2, 1f))
        progressLayout.addView(stats)

        val progressDialog = AlertDialog.Builder(activity)
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()
        progressDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        executor.execute {
            try {
                val file = File(activity.cacheDir, "updates/ymntube-update.apk")
                file.parentFile?.mkdirs()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed (" + response.code + ")")
                    val body = response.body ?: error("Empty download")
                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    var lastUiUpdate = 0L
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdate >= 100 || (totalBytes > 0 && downloaded >= totalBytes)) {
                                    lastUiUpdate = now
                                    val current = downloaded
                                    mainHandler.post {
                                        if (!activity.isFinishing && !activity.isDestroyed) {
                                            val currentMb = current / (1024.0 * 1024.0)
                                            val totalMb = if (totalBytes > 0) {
                                                String.format(Locale.US, "%.2f MB", totalBytes / (1024.0 * 1024.0))
                                            } else "Unknown size"
                                            downloadedText.text = String.format(
                                                Locale.US, "%.2f MB / %s", currentMb, totalMb
                                            )
                                            if (totalBytes > 0) {
                                                val percent = ((current * 100) / totalBytes)
                                                    .toInt().coerceIn(0, 100)
                                                progressBar.isIndeterminate = false
                                                progressBar.progress = percent
                                                percentText.text = "$percent%"
                                            } else {
                                                progressBar.isIndeterminate = true
                                                percentText.text = "Downloading"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val actualHash = MessageDigest.getInstance("SHA-256")
                    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                if (!actualHash.equals(expectedHash, true)) {
                    file.delete()
                    error("APK verification failed")
                }
                mainHandler.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        progressDialog.dismiss()
                        install(activity, file)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        progressDialog.dismiss()
                        AlertDialog.Builder(activity)
                            .setTitle("Update failed")
                            .setMessage(e.message ?: "Please try again.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun label(
        activity: MainActivity,
        value: String,
        size: Int,
        color: Int,
        bold: Boolean
    ): TextView = TextView(activity).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.START
    }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.toFloat() * 3
            if (stroke != Color.TRANSPARENT) setStroke(1, stroke)
        }

    private fun dp(activity: MainActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

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
