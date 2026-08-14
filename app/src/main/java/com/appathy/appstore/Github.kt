package com.appathy.appstore

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Github {
    private const val API = "https://api.github.com"

    private fun open(url: String, token: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("Authorization", "token $token")
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "appathy-store")
        return c
    }

    fun getText(path: String, token: String, raw: Boolean = false): String {
        val accept = if (raw) "application/vnd.github.raw" else "application/vnd.github+json"
        val c = open(if (path.startsWith("http")) path else API + path, token, accept)
        c.inputStream.use { return it.readBytes().decodeToString() }
    }

    fun downloadAsset(assetUrl: String, token: String, dest: File) {
        var c = open(assetUrl, token, "application/octet-stream")
        c.instanceFollowRedirects = false
        val code = c.responseCode
        if (code in 300..399) {
            val loc = c.headerFields["Location"]?.firstOrNull()
                ?: throw IllegalStateException("リダイレクト先がありません")
            c.disconnect()
            val s3 = URL(loc).openConnection() as HttpURLConnection
            s3.connectTimeout = 15000
            s3.readTimeout = 120000
            c = s3
        }
        dest.parentFile?.mkdirs()
        c.inputStream.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
