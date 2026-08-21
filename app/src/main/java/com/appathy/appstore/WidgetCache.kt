package com.appathy.appstore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetItem(
    val id: String,
    val name: String,
    val packageName: String,
    val state: String,
    val memo: String,
    val status: String,
)

object WidgetCache {
    private fun prefs(context: Context) = context.getSharedPreferences("widget", 0)

    fun save(context: Context, apps: List<StoreApp>, states: Map<String, InstallState>) {
        val arr = JSONArray()
        for (a in apps) {
            val st = states[a.id] ?: InstallState.UNKNOWN
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("pkg", a.packageName)
                    .put("state", st.name)
                    .put("memo", a.memo)
                    .put("status", a.status)
            )
        }
        prefs(context).edit().putString("items", arr.toString()).apply()
    }

    fun load(context: Context): List<WidgetItem> {
        val raw = prefs(context).getString("items", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<WidgetItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                WidgetItem(
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("pkg"),
                    o.optString("state"),
                    o.optString("memo"),
                    o.optString("status")
                )
            )
        }
        return out
    }
}
