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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

class AppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        AppWidgetProvider(context).onUpdate(appWidgetManager, appWidgetIds)
    }

    data class Data(val consumption: UnitValue<Float>, val date: Instant)

    inner class AppWidgetProvider(private val context: Context) {

        private val cacheKey = "widget"
        private val kv = KV(context)

        private val dateTimeFormatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

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

        private fun latestData(): Data? {
            val maybeConsumption = runBlocking { loadData(context, CoopClient::getConsumption) }
            val consumptions = maybeConsumption.right() ?: return null
            val consumption = consumptions[when {
                consumptions.size >= 2 -> 1
                consumptions.size >= 1 -> 0
                else -> return null
            }]
            return Data(consumption, Instant.now())
        }

        private fun cachedData() = kv.get<Data>(cacheKey, TypeToken.get(Data::class.java).type)

        private fun views(data: Data): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.setTextViewText(R.id.description, data.consumption.description)
            views.setTextViewText(R.id.amount, data.consumption.amount.toString())
            views.setTextViewText(R.id.unit, data.consumption.unit)
            views.setTextViewText(R.id.last_updated, dateTimeFormatter.format(data.date))
            return views
        }

    }

}
