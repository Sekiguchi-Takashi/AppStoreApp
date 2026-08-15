package com.appathy.appstore

import android.util.Base64
import org.json.JSONObject

object CatalogWriter {
    private const val PATH = "/repos/${Catalog.CATALOG_REPO}/contents/catalog.json"

    private fun commit(token: String, message: String, edit: (JSONObject) -> Int): String {
        val sha = JSONObject(Github.getText("$PATH?ref=main", token)).getString("sha")
        val root = JSONObject(Github.getText("$PATH?ref=main", token, raw = true))
        val n = edit(root)
        val body = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(root.toString(2).toByteArray(), Base64.NO_WRAP))
            .put("sha", sha)
            .put("branch", "main")
        Github.put(PATH, token, body.toString())
        return "$n 件を保存しました"
    }

    /** 並び順のみ保存。渡されなかったアプリは最後尾 (9999) */
    fun saveOrder(token: String, order: Map<String, Int>): String =
        commit(token, "store: 並び順を更新") { root ->
            val apps = root.getJSONArray("apps")
            var n = 0
            for (i in 0 until apps.length()) {
                val o = apps.getJSONObject(i)
                val v = order[o.getString("id")] ?: 9999
                o.put("order", v)
                if (v < 9999) n++
            }
            n
        }

    /** ステータスとメモのみ保存 */
    fun saveStatus(token: String, data: Map<String, Pair<String, String>>): String =
        commit(token, "store: ステータスを更新") { root ->
            val apps = root.getJSONArray("apps")
            var n = 0
            for (i in 0 until apps.length()) {
                val o = apps.getJSONObject(i)
                val c = data[o.getString("id")] ?: continue
                if (c.first.isBlank()) o.remove("status") else o.put("status", c.first)
                if (c.second.isBlank()) o.remove("memo") else o.put("memo", c.second.take(20))
                if (c.first.isNotBlank() || c.second.isNotBlank()) n++
            }
            n
        }
}
