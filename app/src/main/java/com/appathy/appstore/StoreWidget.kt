package com.appathy.appstore

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class StoreWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_root)

            val serviceIntent = Intent(context, StoreWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            val template = Intent(context, StoreWidget::class.java).apply {
                action = ACTION_ITEM
            }
            views.setPendingIntentTemplate(
                R.id.widget_list,
                PendingIntent.getBroadcast(
                    context, 0, template,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            val openStore = Intent(context, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(
                    context, 1, openStore,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val refresh = Intent(context, StoreWidget::class.java).apply { action = ACTION_REFRESH }
            views.setOnClickPendingIntent(
                R.id.widget_refresh,
                PendingIntent.getBroadcast(
                    context, 2, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                notifyChanged(context)
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_REFRESH, true)
                )
            }
            ACTION_ITEM -> {
                val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
                val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
                when (intent.getStringExtra(EXTRA_KIND)) {
                    "open" -> {
                        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launch)
                        }
                    }
                    "install" -> {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(EXTRA_AUTO_INSTALL, appId)
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_ITEM = "com.appathy.appstore.WIDGET_ITEM"
        const val ACTION_REFRESH = "com.appathy.appstore.WIDGET_REFRESH"
        const val EXTRA_APP_ID = "appId"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_KIND = "kind"
        const val EXTRA_AUTO_INSTALL = "autoInstall"
        const val EXTRA_REFRESH = "refresh"

        fun notifyChanged(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StoreWidget::class.java))
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }
    }
}
