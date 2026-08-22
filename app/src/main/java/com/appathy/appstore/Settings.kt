package com.appathy.appstore

import android.content.Context

object Settings {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", 0)

    fun showOpenButton(context: Context): Boolean =
        prefs(context).getBoolean("showOpen", false)

    fun setShowOpenButton(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("showOpen", value).apply()
    }
}
