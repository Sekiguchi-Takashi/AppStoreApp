package com.appathy.appstore

import android.content.Context
import org.json.JSONArray

object Diagnostics {

    fun report(context: Context, apps: List<StoreApp>, token: String): String {
        val sb = StringBuilder()
        sb.append("Appathy Store 診断レポート\n")
        sb.append("ストア: ").append(BuildInfo.version(context)).append("\n")
        sb.append("対象: ").append(apps.size).append(" 件\n\n")

        val problems = mutableListOf<String>()

        for (app in apps) {
            val spec = app.channels[app.defaultChannel]
            sb.append("[").append(app.id).append("] ").append(app.name).append("\n")
            sb.append("  repo=").append(app.repo).append("\n")
            sb.append("  packageName=").append(if (app.packageName.isBlank()) "(空)" else app.packageName).append("\n")
            sb.append("  assetPattern=").append(spec?.assetPattern ?: "(なし)").append("\n")

            if (app.packageName.isBlank()) {
                problems.add("${app.id}: packageName が空。setpkg.sh で設定が必要")
            }

            val res = runCatching {
                Github.getText("/repos/${app.repo}/releases?per_page=1", token)
            }
            if (res.isFailure) {
                val msg = res.exceptionOrNull()?.message ?: "不明なエラー"
                sb.append("  Release: 取得失敗 (").append(msg).append(")\n\n")
                problems.add("${app.id}: Release 取得失敗 " + msg)
                continue
            }
            val raw = res.getOrNull() ?: ""

            val arr = runCatching { JSONArray(raw) }.getOrNull()
            if (arr == null || arr.length() == 0) {
                sb.append("  Release: なし\n\n")
                problems.add("${app.id}: Release が1件もない。タグを打つ必要がある")
                continue
            }

            val rel = arr.getJSONObject(0)
            val tag = rel.optString("tag_name")
            val body = rel.optString("body")
            val assets = rel.optJSONArray("assets") ?: JSONArray()
            val names = (0 until assets.length()).map { assets.getJSONObject(it).optString("name") }

            sb.append("  最新タグ=").append(tag).append("\n")
            sb.append("  アセット=").append(if (names.isEmpty()) "(なし)" else names.joinToString(", ")).append("\n")

            val tagRe = spec?.tagPattern?.let { runCatching { Regex(it) }.getOrNull() }
            if (tagRe != null && !tagRe.containsMatchIn(tag)) {
                sb.append("  ！タグがパターンに不一致\n")
                problems.add("${app.id}: タグ $tag が tagPattern ${spec.tagPattern} に不一致")
            }

            val assetRe = spec?.assetPattern?.let { runCatching { Regex(it) }.getOrNull() }
            val matched = names.filter { it.endsWith(".apk") && (assetRe?.containsMatchIn(it) ?: true) }
            if (names.none { it.endsWith(".apk") }) {
                sb.append("  ！APK が添付されていない\n")
                problems.add("${app.id}: Release に APK がない。ビルド失敗の可能性")
            } else if (matched.isEmpty()) {
                sb.append("  ！assetPattern に合う APK がない\n")
                problems.add("${app.id}: assetPattern ${spec?.assetPattern} が実ファイル名 ${names.first()} に不一致")
            }

            val sha = matched.firstOrNull()?.let { name ->
                Regex("sha256 " + Regex.escape(name) + " ([0-9a-fA-F]{64})").find(body)?.groupValues?.get(1)
            }
            sb.append("  sha256=").append(if (sha == null) "なし (指紋判定できない)" else sha.take(12) + "...").append("\n")
            if (sha == null && matched.isNotEmpty()) {
                problems.add("${app.id}: リリース本文に sha256 がない。stamp.sh の適用が必要")
            }

            val installed = Catalog.installedPackage(context, app)
            if (installed == null) {
                sb.append("  端末: 未インストール\n")
            } else {
                val h = ApkHash.of(context, installed)
                val state = when {
                    sha == null -> "指紋なしのため判定不可"
                    h == null -> "端末APKを読めない"
                    h.equals(sha, ignoreCase = true) -> "一致 (最新)"
                    else -> "不一致 (更新あり)"
                }
                sb.append("  端末: v").append(installed.versionName).append(" / ").append(state).append("\n")
                sb.append("  記録タグ=").append(InstallLog.tagOf(context, app.id) ?: "なし").append("\n")
            }
            sb.append("\n")
        }

        sb.append("=== 要対応 ").append(problems.size).append(" 件 ===\n")
        if (problems.isEmpty()) {
            sb.append("問題は見つかりませんでした\n")
        } else {
            problems.forEach { sb.append("- ").append(it).append("\n") }
        }
        return sb.toString()
    }
}
