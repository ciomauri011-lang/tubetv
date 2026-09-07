package com.tubetv.youtube

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-update desde GitHub releases (para installs sideloaded).
 * Compara tag "vX.Y" con BuildConfig.VERSION_NAME una vez al día.
 * Si hay nueva: descarga el APK y abre el instalador del sistema
 * (el usuario confirma; requiere "instalar apps desconocidas").
 */
class UpdateChecker(private val activity: MainActivity) {

    companion object {
        const val OWNER = "ciomauri011-lang"
        const val REPO = "tubetv"
        const val PREFS = "tubetv_prefs"
        const val KEY_LAST_CHECK = "last_update_check"
    }

    fun checkOnceDaily() {
        val prefs = activity.getSharedPreferences(PREFS, 0)
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        Thread({
            try {
                val json = httpGet("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: return@Thread
                val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").find(json)?.groupValues?.get(1) ?: return@Thread
                if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Nueva versión $tag, descargando…", Toast.LENGTH_LONG).show()
                    }
                    val file = download(apk) ?: return@Thread
                    activity.runOnUiThread { installApk(file) }
                }
            } catch (e: Exception) {
                // Sin red o sin releases: silencioso.
            }
        }, "tubetv-update").start()
    }

    private fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').split(".", "-").map { it.toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun httpGet(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return c.inputStream.bufferedReader().readText().also { c.disconnect() }
    }

    private fun download(url: String): File? {
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            val file = File(activity.cacheDir, "tubetv-update.apk")
            c.inputStream.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            c.disconnect()
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val pm = activity.packageManager
                if (!pm.canRequestPackageInstalls()) {
                    Toast.makeText(activity, "Activa 'instalar desconocidas' para actualizar", Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                    return
                }
            }
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".updates", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "No se pudo abrir el instalador", Toast.LENGTH_SHORT).show()
        }
    }
}
