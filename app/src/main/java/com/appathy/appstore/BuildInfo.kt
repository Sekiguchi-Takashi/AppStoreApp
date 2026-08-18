package com.appathy.appstore

import android.content.Context

object BuildInfo {
    fun version(context: Context): String = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        "v" + pi.versionName
    }.getOrDefault("不明")
}
