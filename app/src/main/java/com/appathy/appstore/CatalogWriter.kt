package com.appathy.appstore

import android.util.Base64
import org.json.JSONObject

object CatalogWriter {

    /** 並び順とステータスを catalog.json に書き戻す */
    fun save(token: String, changes: Map<String, Pair<Int, String>>): String {
        val path = "/repos/${Catalog.CATALOG_REPO}/contents/catalog.json"
        val metaText = Github.getText("$path?ref=main", token)
        val sha = JSONObject(metaText).getString("sha")

        val raw = Github.getText("$path?ref=main", token, raw = true)
        val root = JSONObject(raw)
        val apps = root.getJSONArray("apps")
        var touched = 0
        for (i in 0 until apps.length()) {
            val o = apps.getJSONObject(i)
            val c = changes[o.getString("id")] ?: continue
            o.put("order", c.first)
            if (c.second.isBlank()) o.remove("status") else o.put("status", c.second)
            touched++
        }
        val text = root.toString(2)
        val body = JSONObject()
            .put("message", "store: 並び順とステータスを更新")
            .put("content", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
            .put("sha", sha)
            .put("branch", "main")
        Github.put(path, token, body.toString())
        return "$touched 件を保存しました"
    }
}
