package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

data class ChannelSpec(val tagPattern: String, val assetPattern: String)

data class StoreApp(
    val id: String,
    val name: String,
    val packageName: String,
    val description: String,
    val category: String,
    val repo: String,
    val defaultChannel: String,
    val channels: Map<String, ChannelSpec>,
)

data class LatestRelease(val tag: String, val assetName: String, val assetUrl: String)

enum class InstallState { NOT_INSTALLED, UP_TO_DATE, UPDATE_AVAILABLE, NO_RELEASE, UNKNOWN }

object Catalog {
    const val CATALOG_REPO = "Sekiguchi-Takashi/CatalogApp"

    fun fetch(token: String): List<StoreApp> {
        val text = Github.getText("/repos/$CATALOG_REPO/contents/catalog.json?ref=main", token, raw = true)
        val root = JSONObject(text)
        val apps = root.getJSONArray("apps")
        return (0 until apps.length()).map { parseApp(apps.getJSONObject(it)) }
    }

    private fun parseApp(o: JSONObject): StoreApp {
        val ch = o.getJSONObject("channels")
        val channels = ch.keys().asSequence().associateWith { k ->
            val s = ch.getJSONObject(k)
            ChannelSpec(s.getString("tagPattern"), s.getString("assetPattern"))
        }
        return StoreApp(
            id = o.getString("id"),
            name = o.getString("name"),
            packageName = o.optString("packageName"),
            description = o.optString("description"),
            category = o.optString("category"),
            repo = o.getString("repo"),
            defaultChannel = o.optString("defaultChannel", "stable"),
            channels = channels,
        )
    }

    fun latestFor(app: StoreApp, channel: String, token: String): LatestRelease? {
        val spec = app.channels[channel] ?: return null
        val tagRe = runCatching { Regex(spec.tagPattern) }.getOrNull() ?: return null
        val assetRe = runCatching { Regex(spec.assetPattern) }.getOrNull() ?: return null
        val text = Github.getText("/repos/${app.repo}/releases?per_page=10", token)
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val rel = arr.getJSONObject(i)
            val tag = rel.optString("tag_name")
            if (!tagRe.containsMatchIn(tag)) continue
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (assetRe.containsMatchIn(name)) {
                    return LatestRelease(tag, name, a.getString("url"))
                }
            }
        }
        return null
    }

    fun installState(context: Context, app: StoreApp, latest: LatestRelease?): InstallState {
        if (app.packageName.isBlank()) return InstallState.UNKNOWN
        val installedVersion = try {
            context.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            return if (latest == null) InstallState.NO_RELEASE else InstallState.NOT_INSTALLED
        }
        if (latest == null) return InstallState.UP_TO_DATE
        val latestVersion = latest.tag.removePrefix("v")
        return if (installedVersion == latestVersion) InstallState.UP_TO_DATE
        else InstallState.UPDATE_AVAILABLE
    }
}
