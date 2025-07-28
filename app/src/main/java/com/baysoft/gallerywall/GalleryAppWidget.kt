package com.baysoft.gallerywall

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class GalleryAppWidget : AppWidgetProvider() {

    companion object {
        fun updateLoaded(context: Context) {
            val views = RemoteViews(context.packageName, R.layout.app_widget)
            views.setViewVisibility(R.id.v_btn_update, View.VISIBLE)
            views.setViewVisibility(android.R.id.progress, View.GONE)
            updateAppWidgets(context, views)
        }

        fun updateLoading(context: Context) {
            val views = RemoteViews(context.packageName, R.layout.app_widget)
            views.setViewVisibility(R.id.v_btn_update, View.GONE)
            views.setViewVisibility(android.R.id.progress, View.VISIBLE)
            updateAppWidgets(context, views)
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.app_widget)
            val activateIntent = Intent(context, GalleryWallReceiver::class.java)
            activateIntent.action = "update"

            val activatePending = PendingIntent.getBroadcast(
                    context, 1, activateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.v_btn_update, activatePending)

            val inActivity = Intent(context, MainActivity::class.java)
            inActivity.putExtra("key", "value1")
            val activityIntent = PendingIntent.getActivity(context, 0, inActivity, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.v_btn_settings, activityIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateAppWidgets(context: Context?, remoteViews: RemoteViews?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                    context?.let {
                        ComponentName(it, GalleryAppWidget::class.java)
                    }
            )
            appWidgetManager.updateAppWidget(ids, remoteViews)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (index in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, index)
        }
    }
}