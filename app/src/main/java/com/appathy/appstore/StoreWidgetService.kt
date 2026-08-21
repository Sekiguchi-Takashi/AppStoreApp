package com.appathy.appstore

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class StoreWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StoreWidgetFactory(applicationContext)
}

class StoreWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = WidgetCache.load(context)
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        views.setTextViewText(R.id.item_name, item.name)

        val label = when (item.state) {
            "NOT_INSTALLED" -> "未インストール"
            "UPDATE_AVAILABLE" -> "更新あり"
            "UP_TO_DATE" -> "最新"
            "NO_RELEASE" -> "配布なし"
            else -> "不明"
        }
        val sub = buildString {
            append(label)
            if (item.status.isNotBlank()) append(" ・ ").append(item.status)
            if (item.memo.isNotBlank()) append(" ・ ").append(item.memo)
        }
        views.setTextViewText(R.id.item_sub, sub)

        val installed = item.state == "UPDATE_AVAILABLE" || item.state == "UP_TO_DATE"
        views.setViewVisibility(R.id.item_open, if (installed) android.view.View.VISIBLE else android.view.View.GONE)

        val actionLabel = when (item.state) {
            "NOT_INSTALLED" -> "インストール"
            "UPDATE_AVAILABLE" -> "更新"
            else -> ""
        }
        if (actionLabel.isBlank()) {
            views.setViewVisibility(R.id.item_action, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.item_action, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_action, actionLabel)
            views.setOnClickFillInIntent(
                R.id.item_action,
                Intent()
                    .putExtra(StoreWidget.EXTRA_APP_ID, item.id)
                    .putExtra(StoreWidget.EXTRA_PKG, item.packageName)
                    .putExtra(StoreWidget.EXTRA_KIND, "install")
            )
        }

        views.setOnClickFillInIntent(
            R.id.item_open,
            Intent()
                .putExtra(StoreWidget.EXTRA_APP_ID, item.id)
                .putExtra(StoreWidget.EXTRA_PKG, item.packageName)
                .putExtra(StoreWidget.EXTRA_KIND, "open")
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
