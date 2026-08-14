package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageManager

object InstallLog {
    private fun prefs(context: Context) = context.getSharedPreferences("installed", 0)
    private fun pending(context: Context) = context.getSharedPreferences("pending", 0)

    fun versionOf(context: Context, app: StoreApp): Long {
        val pi = Catalog.installedPackage(context, app) ?: return -1L
        return if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun markPending(context: Context, appId: String, tag: String, prevVersion: Long) {
        pending(context).edit().putString(appId, "$tag|$prevVersion").apply()
    }

    fun resolvePending(context: Context, apps: List<StoreApp>) {
        val p = pending(context)
        val byId = apps.associateBy { it.id }
        for ((appId, raw) in p.all) {
            val parts = (raw as? String)?.split("|") ?: continue
            if (parts.size != 2) { p.edit().remove(appId).apply(); continue }
            val tag = parts[0]
            val prev = parts[1].toLongOrNull() ?: -1L
            val app = byId[appId] ?: continue
            val cur = versionOf(context, app)
            when {
                cur == -1L -> {
                    p.edit().remove(appId).apply()
                    forget(context, appId)
                }
                cur != prev -> {
                    record(context, appId, tag)
                    p.edit().remove(appId).apply()
                }
            }
        }
    }

    fun record(context: Context, appId: String, tag: String) {
        prefs(context).edit().putString(appId, tag).apply()
    }

    /** 記録が無い（ストア外で入れた）アプリは、今の最新タグを基準として記録する */
    fun baseline(context: Context, app: StoreApp, latestTag: String?) {
        if (latestTag == null) return
        if (Catalog.installedPackage(context, app) == null) return
        if (tagOf(context, app.id) != null) return
        record(context, app.id, latestTag)
    }

    fun tagOf(context: Context, appId: String): String? =
        prefs(context).getString(appId, null)

    fun forget(context: Context, appId: String) {
        prefs(context).edit().remove(appId).apply()
    }

    fun prune(context: Context, apps: List<StoreApp>) {
        for (a in apps) {
            if (a.packageName.isBlank()) continue
            if (Catalog.installedPackage(context, a) == null) forget(context, a.id)
        }
    }
}
