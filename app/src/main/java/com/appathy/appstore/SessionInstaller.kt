package com.appathy.appstore

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

object SessionInstaller {
    const val ACTION_RESULT = "appathy.store.INSTALL_RESULT"

    fun install(context: Context, apk: File, label: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra("label", label)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    fun statusText(status: Int, message: String?, label: String): String = when (status) {
        PackageInstaller.STATUS_SUCCESS -> "$label: インストール完了"
        PackageInstaller.STATUS_FAILURE_ABORTED -> "$label: キャンセルされました"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "$label: ブロックされました (${message ?: ""})"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "$label: 既存アプリと衝突 (${message ?: ""})"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "$label: 端末と非互換 (${message ?: ""})"
        PackageInstaller.STATUS_FAILURE_INVALID -> "$label: APKが不正 (${message ?: ""})"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "$label: 容量不足 (${message ?: ""})"
        else -> "$label: 失敗 (${message ?: "status=$status"})"
    }
}
