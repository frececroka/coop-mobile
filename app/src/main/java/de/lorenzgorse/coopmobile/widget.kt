package de.lorenzgorse.coopmobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.reflect.TypeToken
import de.lorenzgorse.coopmobile.coopclient.UnitValue
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
        val provider = AppWidgetProvider(context)
        provider.onUpdate(appWidgetManager, appWidgetIds)
        provider.close()
    }

    data class Data(val consumption: UnitValue<Float>, val date: Instant)

    inner class AppWidgetProvider(private val context: Context) : AutoCloseable {

        private val cacheKey = "widget"
        private val kv = KV(context)

        private val client = createClient(context)

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
            val maybeConsumption = runBlocking { client.getConsumption() }
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

            // update the widget when the "amount" text view is clicked
            val intent = Intent(context, de.lorenzgorse.coopmobile.AppWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val componentName = ComponentName(context, de.lorenzgorse.coopmobile.AppWidgetProvider::class.java)
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(componentName)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.amount, pendingIntent)

            return views
        }

        override fun close() {
            kv.close()
        }

    }

}
