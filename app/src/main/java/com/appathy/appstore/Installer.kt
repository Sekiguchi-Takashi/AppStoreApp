package com.appathy.appstore

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Installer {
    fun download(context: Context, app: StoreApp, latest: LatestRelease, token: String): File {
        val dir = File(context.filesDir, "apks")
        val apk = File(dir, "${app.id}-${latest.tag}.apk")
        if (!apk.exists() || apk.length() == 0L) {
            Github.downloadAsset(latest.assetUrl, token, apk)
        }
        return apk
    }

    fun downloadAndInstall(context: Context, app: StoreApp, latest: LatestRelease, token: String) {
        val dir = File(context.filesDir, "apks")
        val apk = File(dir, "${app.id}-${latest.tag}.apk")
        if (!apk.exists() || apk.length() == 0L) {
            Github.downloadAsset(latest.assetUrl, token, apk)
        }
        InstallLog.markPending(context, app.id, latest.tag, InstallLog.versionOf(context, app))
        val uri = FileProvider.getUriForFile(context, "com.appathy.appstore.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun clearCache(context: Context) {
        File(context.filesDir, "apks").deleteRecursively()
    }

    fun pruneCache(context: Context, keep: Map<String, String>) {
        val dir = File(context.filesDir, "apks")
        val files = dir.listFiles() ?: return
        for (f in files) {
            val ok = keep.any { (id, tag) -> f.name == "$id-$tag.apk" }
            if (!ok) f.delete()
        }
    }
}
