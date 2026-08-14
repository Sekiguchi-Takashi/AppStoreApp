package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageInfo
import java.io.File
import java.security.MessageDigest

object ApkHash {
    fun of(context: Context, info: PackageInfo): String? {
        val src = info.applicationInfo?.sourceDir ?: return null
        val f = File(src)
        if (!f.canRead()) return null
        val prefs = context.getSharedPreferences("apkhash", 0)
        val key = info.packageName
        val stamp = "${f.lastModified()}:${f.length()}"
        prefs.getString(key, null)?.let {
            val parts = it.split("|")
            if (parts.size == 2 && parts[0] == stamp) return parts[1]
        }
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        prefs.edit().putString(key, "$stamp|$hash").apply()
        return hash
    }
}
