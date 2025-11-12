package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import java.util.concurrent.TimeUnit

class MyWeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "weather_widget_prefs"
        const val PREF_LAST_CONDITION = "last_condition"
        const val PREF_THEME = "pref_widget_theme"

        // possible values:
        const val THEME_MALAYALAM = "malayalam"
        const val THEME_BW = "black_white"
        const val THEME_TRANSPARENT = "transparent"

        /**
         * Helper to update one widget from current prefs (safe and idempotent)
         * Marked @JvmStatic so it can be called from other places like Activities easily.
         */
        @JvmStatic
        fun updateSingleWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCondition = prefs.getString(PREF_LAST_CONDITION, "Unknown") ?: "Unknown"

                // choose layout based on options if needed (use 5x1 by default)
                val layout = R.layout.widget_5x1
                val views = RemoteViews(context.packageName, layout)

                val iconRes = pickDrawableForConditionAndTheme(lastCondition, prefs, context)
                views.setImageViewResource(R.id.weather_icon, iconRes)

                // set text only if layout has weather_title (5x1)
                try {
                    views.setTextViewText(R.id.weather_title, lastCondition)
                } catch (_: Throwable) {
                    // ignore if the layout used doesn't have that id
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        /**
         * Choose themed drawable name and return resource id.
         * @param condition plain condition like "Clear", "Clouds", etc.
         */
        @JvmStatic
        fun pickDrawableForConditionAndTheme(condition: String, prefs: SharedPreferences, context: Context): Int {
            val theme = prefs.getString(PREF_THEME, THEME_MALAYALAM) ?: THEME_MALAYALAM
            val key = condition.lowercase(Locale.ROOT)

            // choose suffix based on theme
            val suffix = when (theme) {
                THEME_BW -> "_bw"
                THEME_TRANSPARENT -> "_tr"
                else -> "_ml"
            }

            // map condition -> base name
            val baseName = when (key) {
                "clear" -> "weather_clear"
                "clouds" -> "weather_clouds"
                "rain" -> "weather_rain"
                "snow" -> "weather_snow"
                "thunderstorm" -> "weather_thunder"
                "drizzle" -> "weather_drizzle"
                "fog" -> "weather_fog"
                "mist" -> "weather_mist"
                else -> "weather_unknown"
            }

            val fullName = baseName + suffix
            // get resource id by name
            val resId = context.resources.getIdentifier(fullName, "drawable", context.packageName)
            // fallback to Malayalam base if not found
            return if (resId != 0) resId else context.resources.getIdentifier(baseName + "_ml", "drawable", context.packageName)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // enqueue immediate one-shot worker to refresh data
        try {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
        } catch (t: Throwable) {
            // ignore WorkManager errors for robustness
            t.printStackTrace()
        }

        // schedule periodic worker once (keep if already scheduled)
        try {
            val periodic = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "weather_update_work",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // update UI from last known prefs (in case worker hasn't run yet)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCondition = prefs.getString(PREF_LAST_CONDITION, "Unknown") ?: "Unknown"

        for (appWidgetId in appWidgetIds) {
            try {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val useWide = minWidth >= 300
                val layoutId = if (useWide) R.layout.widget_5x1 else R.layout.widget_2x1
                val views = RemoteViews(context.packageName, layoutId)

                val iconRes = pickDrawableForConditionAndTheme(lastCondition, prefs, context)
                views.setImageViewResource(R.id.weather_icon, iconRes)

                if (useWide) {
                    // set condition text if present
                    try {
                        views.setTextViewText(R.id.weather_title, lastCondition)
                    } catch (_: Throwable) { /* ignore */ }
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCondition = prefs.getString(PREF_LAST_CONDITION, "Unknown") ?: "Unknown"

        try {
            val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val useWide = minWidth >= 300
            val layoutId = if (useWide) R.layout.widget_5x1 else R.layout.widget_2x1
            val views = RemoteViews(context.packageName, layoutId)

            val iconRes = pickDrawableForConditionAndTheme(lastCondition, prefs, context)
            views.setImageViewResource(R.id.weather_icon, iconRes)

            if (useWide) {
                try {
                    views.setTextViewText(R.id.weather_title, lastCondition)
                } catch (_: Throwable) { /* ignore */ }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
