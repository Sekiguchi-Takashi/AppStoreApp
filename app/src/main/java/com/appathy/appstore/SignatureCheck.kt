package com.appathy.appstore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object SignatureCheck {

    private fun digest(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun certsOf(pm: PackageManager, packageName: String): Set<String> {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val s = info.signingInfo ?: return emptySet()
                val arr = if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
                arr.map { digest(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                (info.signatures ?: emptyArray()).map { digest(it.toByteArray()) }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun certsOfApk(context: Context, apk: File): Set<String> {
        val pm = context.packageManager
        return try {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return emptySet()
            if (Build.VERSION.SDK_INT >= 28) {
                val s = info.signingInfo ?: return emptySet()
                val arr = if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
                arr.map { digest(it.toByteArray()) }.toSet()
            } else {
                @Suppress("DEPRECATION")
                (info.signatures ?: emptyArray()).map { digest(it.toByteArray()) }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** APK が壊れていないか、署名されているかを確認する。問題があれば理由を返す */
    fun apkProblem(context: Context, apk: File): String? {
        if (!apk.exists() || apk.length() < 10000) return "APK のダウンロードが不完全です"
        val info = try {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        } catch (e: Exception) {
            null
        } ?: return "APK を読み取れません（破損の可能性）"
        if (certsOfApk(context, apk).isEmpty()) {
            return "APK が署名されていません（" + info.packageName + "）"
        }
        return null
    }

    /** 上書き可能なら null、不可能なら理由の説明を返す */
    fun blockingReason(context: Context, app: StoreApp, apk: File): String? {
        val installed = Catalog.installedPackage(context, app) ?: return null
        val a = certsOf(context.packageManager, installed.packageName)
        val b = certsOfApk(context, apk)
        if (a.isEmpty() || b.isEmpty()) return null
        if (a.intersect(b).isNotEmpty()) return null
        return "端末側 " + a.first().take(8) + " / 配布側 " + b.first().take(8)
    }
}
