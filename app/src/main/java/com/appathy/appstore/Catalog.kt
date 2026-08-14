package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

data class ChannelSpec(val tagPattern: String, val assetPattern: String)

data class Profile(val id: String, val name: String, val appIds: List<String>)

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

    fun fetch(token: String): Pair<List<StoreApp>, List<Profile>> {
        val text = Github.getText("/repos/$CATALOG_REPO/contents/catalog.json?ref=main", token, raw = true)
        val root = JSONObject(text)
        val appsJson = root.getJSONArray("apps")
        val apps = (0 until appsJson.length()).map { parseApp(appsJson.getJSONObject(it)) }
        val profsJson = root.optJSONArray("profiles")
        val profiles = mutableListOf<Profile>()
        if (profsJson != null) {
            for (i in 0 until profsJson.length()) {
                val o = profsJson.getJSONObject(i)
                val idsJson = o.getJSONArray("appIds")
                val ids = (0 until idsJson.length()).map { idsJson.getString(it) }
                val expanded = if (ids.contains("*")) apps.map { it.id } else ids
                profiles.add(Profile(o.getString("id"), o.optString("name", o.getString("id")), expanded))
            }
        }
        return apps to profiles
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
        val installed = try {
            context.packageManager.getPackageInfo(app.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (installed == null) {
            return if (latest == null) InstallState.NO_RELEASE else InstallState.NOT_INSTALLED
        }
        if (latest == null) return InstallState.UP_TO_DATE
        val recorded = InstallLog.tagOf(context, app.id)
        if (recorded != null) {
            return if (recorded == latest.tag) InstallState.UP_TO_DATE else InstallState.UPDATE_AVAILABLE
        }
        val installedVersion = installed.versionName ?: ""
        val latestVersion = latest.tag.removePrefix("v")
        return if (installedVersion == latestVersion) InstallState.UP_TO_DATE
        else InstallState.UPDATE_AVAILABLE
    }
}
