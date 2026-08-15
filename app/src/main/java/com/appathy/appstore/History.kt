package com.appathy.appstore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val name: String, val action: String, val time: Long, val tag: String)

object History {
    private const val MAX = 100
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences("history", 0)

    fun add(context: Context, name: String, action: String, tag: String) {
        val arr = raw(context)
        arr.put(
            JSONObject()
                .put("name", name)
                .put("action", action)
                .put("time", System.currentTimeMillis())
                .put("tag", tag)
        )
        save(context, prune(arr))
    }

    fun list(context: Context): List<HistoryEntry> {
        val arr = prune(raw(context))
        val out = mutableListOf<HistoryEntry>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(
                HistoryEntry(
                    o.optString("name"),
                    o.optString("action"),
                    o.optLong("time"),
                    o.optString("tag")
                )
            )
        }
        return out
    }

    fun clear(context: Context) {
        prefs(context).edit().remove("data").apply()
    }

    private fun raw(context: Context): JSONArray =
        runCatching { JSONArray(prefs(context).getString("data", "[]")) }.getOrDefault(JSONArray())

    private fun prune(arr: JSONArray): JSONArray {
        val limit = System.currentTimeMillis() - KEEP_MS
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("time") >= limit) kept.put(o)
        }
        if (kept.length() <= MAX) return kept
        val trimmed = JSONArray()
        for (i in kept.length() - MAX until kept.length()) trimmed.put(kept.getJSONObject(i))
        return trimmed
    }

    private fun save(context: Context, arr: JSONArray) {
        prefs(context).edit().putString("data", arr.toString()).apply()
    }
}
