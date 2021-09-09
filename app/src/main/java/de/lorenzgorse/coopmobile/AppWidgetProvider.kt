package de.lorenzgorse.coopmobile

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import de.lorenzgorse.coopmobile.coopclient.CoopClient
import de.lorenzgorse.coopmobile.data.Either
import de.lorenzgorse.coopmobile.data.loadData
import kotlinx.coroutines.runBlocking

class AppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        runBlocking {
            val coopClientFactory = CoopModule.coopClientFactory
            val client = coopClientFactory.get(context)
            if (client != null) {
                val maybeConsumption = loadData(context, CoopClient::getConsumption)
                val consumption = when (maybeConsumption) {
                    is Either.Left -> return@runBlocking
                    is Either.Right -> maybeConsumption.value
                }
                val measure = consumption[when {
                    consumption.size >= 2 -> 1
                    consumption.size >= 1 -> 0
                    else -> return@runBlocking
                }]
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget)
                    views.setTextViewText(R.id.amount, measure.amount.toString())
                    views.setTextViewText(R.id.unit, measure.unit)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}