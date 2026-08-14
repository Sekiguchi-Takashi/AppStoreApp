package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageManager

object InstallLog {
    private fun prefs(context: Context) = context.getSharedPreferences("installed", 0)

    fun record(context: Context, appId: String, tag: String) {
        prefs(context).edit().putString(appId, tag).apply()
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
