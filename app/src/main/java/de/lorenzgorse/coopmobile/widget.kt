package de.lorenzgorse.coopmobile

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.coopclient.UnitValue
import de.lorenzgorse.coopmobile.data.loadData
import kotlinx.coroutines.runBlocking

class AppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AppWidgetProvider(context).onUpdate(appWidgetManager, appWidgetIds)
    }

    inner class AppWidgetProvider(private val context: Context) {

        private val cacheKey = "widget"
        private val kv = KV(context)

        fun onUpdate(
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val data = latestData() ?: cachedData() ?: return
            kv.set(cacheKey, data)
            val newViews = views(data)
            for (appWidgetId in appWidgetIds) {
                appWidgetManager.updateAppWidget(appWidgetId, newViews)
            }
        }

        private fun latestData(): UnitValue<Float>? {
            val maybeConsumption = runBlocking { loadData(context, CoopClient::getConsumption) }
            val consumption = maybeConsumption.right() ?: return null
            return consumption[when {
                consumption.size >= 2 -> 1
                consumption.size >= 1 -> 0
                else -> return null
            }]
        }

        private fun cachedData() = kv.get<UnitValue<Float>>(
            cacheKey,
            TypeToken.getParameterized(UnitValue::class.java, java.lang.Float::class.java).type
        )

        private fun views(widgetData: UnitValue<Float>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.amount, widgetData.amount.toString())
            views.setTextViewText(R.id.unit, widgetData.unit)
            return views
        }

    }

}
