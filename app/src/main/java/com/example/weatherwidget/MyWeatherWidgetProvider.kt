package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MyWeatherWidgetProvider : AppWidgetProvider() {
    companion object {
        const val PREFS_NAME = "weather_widget_prefs"
        const val PREF_LAST_CONDITION = "last_condition"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
        // schedule periodic worker (keep existing if already scheduled)
        val periodic = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weather_update_work",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // also enqueue one-shot to update immediately
        val oneShot = OneTimeWorkRequestBuilder<WeatherWorker>().build()
        WorkManager.getInstance(context).enqueue(oneShot)

        // update UI from last known prefs (in case worker hasn't run yet)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCondition = prefs.getString(PREF_LAST_CONDITION, "Unknown") ?: "Unknown"
        val lastTemp = prefs.getFloat("last_temp", Float.NaN)
        val tempStr = if (lastTemp.isNaN()) "" else "${lastTemp.toInt()}\u00B0C"



        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val useWide = minWidth >= 300
            val layoutId = if (useWide) R.layout.widget_5x1 else R.layout.widget_2x1
            val views = RemoteViews(context.packageName, layoutId)

            // choose icon based on lastCondition (same logic as worker)
            val iconRes = when (lastCondition.lowercase()) {
                "clear" -> R.drawable.weather_clear
                "clouds" -> R.drawable.weather_clouds
                "rain" -> R.drawable.weather_rain
                "snow" -> R.drawable.weather_snow
                "thunderstorm" -> R.drawable.weather_thunder
                "drizzle" -> R.drawable.weather_drizzle
                "fog", "mist" -> R.drawable.weather_fog
                else -> R.drawable.weather_unknown
            }
            views.setImageViewResource(R.id.weather_icon, iconRes)
            views.setImageViewResource(R.id.weather_icon, iconRes)
            if (useWide) {
                views.setTextViewText(R.id.weather_title, lastCondition)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        // Re-render this widget using last saved prefs
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCondition = prefs.getString(PREF_LAST_CONDITION, "Unknown") ?: "Unknown"
        val lastTemp = prefs.getFloat("last_temp", Float.NaN)
        val tempStr = if (lastTemp.isNaN()) "" else "${'$'}{lastTemp.toInt()}\u00B0C"

        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val useWide = minWidth >= 300
        val layoutId = if (useWide) R.layout.widget_5x1 else R.layout.widget_2x1
        val views = RemoteViews(context.packageName, layoutId)

        val iconRes = when (lastCondition.lowercase()) {
            "clear" -> R.drawable.weather_clear
            "clouds" -> R.drawable.weather_clouds
            "rain" -> R.drawable.weather_rain
            "snow" -> R.drawable.weather_snow
            "thunderstorm" -> R.drawable.weather_thunder
            "drizzle" -> R.drawable.weather_drizzle
            "fog", "mist" -> R.drawable.weather_fog
            else -> R.drawable.weather_unknown
        }
        views.setImageViewResource(R.id.weather_icon, iconRes)
        if (useWide) {
            views.setTextViewText(R.id.weather_title, lastCondition)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
