package com.appathy.appstore

import android.content.Context
import android.content.Intent
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

    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun downloadAndInstall(context: Context, app: StoreApp, latest: LatestRelease, token: String) {
        val apk = download(context, app, latest, token)
        SessionInstaller.install(context, apk, app.name, app.id, latest.tag)
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
