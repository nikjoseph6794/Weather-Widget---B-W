package com.WeatherWidget.weatherwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.PREF_THEME
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_BW
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_BW_TRANSPARENT
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_MALAYALAM
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_TRANSPARENT
import com.weatherwidget.app.R

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private const val TAG = "WeatherWorker"

class WeatherWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient()

    private val prefs = applicationContext.getSharedPreferences(
        MyWeatherWidgetProvider.PREFS_NAME,
        Context.MODE_PRIVATE
    )

//    private val defaultLat = prefs.getFloat("lat", 10.0159f).toDouble()
//    private val defaultLon = prefs.getFloat("lon", 76.3419f).toDouble()

    override suspend fun doWork(): Result {
        return try {

            val weather = fetchCurrentWeather()
            if (weather == null) {

                return Result.retry()
            }
            val condition = normalizeCondition(weather.condition)
            val tempC = weather.temperatureC


            val lastCondition = prefs.getString(MyWeatherWidgetProvider.PREF_LAST_CONDITION, null)
            val lastTemp = prefs.getFloat("last_temp", Float.NaN).toDouble()

            val tempChanged = if (lastTemp.isNaN()) true else abs(lastTemp - tempC) >= 1.0
            val conditionChanged = lastCondition != condition

            if (conditionChanged || tempChanged) {
                prefs.edit().putString(MyWeatherWidgetProvider.PREF_LAST_CONDITION, condition)
                    .putFloat("last_temp", if (tempC.isNaN()) Float.NaN else tempC.toFloat())
                    .apply()
                updateWidgets(condition)

            }
            Result.success()
        } catch (t: Throwable) {

            Result.retry()
        }
    }

    private data class WeatherResult(val condition: String, val temperatureC: Double)
    private fun fetchCurrentWeather(): WeatherResult? {
        // read the latest coordinates directly from prefs each time so location is dynamic
        val lat = prefs.getFloat("lat", 10.0159f).toDouble()
        val lon = prefs.getFloat("lon", 76.3419f).toDouble()

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {

                return null
            }
            val body = resp.body?.string() ?: return null

            val json = JSONObject(body)
            val current = json.optJSONObject("current_weather") ?: return null
            val code = current.optInt("weathercode", -1)
            val temp = current.optDouble("temperature", Double.NaN)
            val cond = mapWeatherCodeToText(code)
            return WeatherResult(cond, if (temp.isNaN()) Double.NaN else temp)
        }
    }

    private fun formatTemperature(tempC: Double): String {
        if (tempC.isNaN()) return ""
        val rounded = round(tempC).toInt()
        return "$rounded°C"
    }

    private fun normalizeCondition(raw: String): String {
        return raw.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun mapWeatherCodeToText(code: Int): String {
        return when (code) {
            0, 1 -> "Clear"
            2, 3 -> "Clouds"
            45 -> "Fog"
            48 -> "Mist"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63 -> "Rain"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71, 73 -> "Snow"
            75 -> "Heavy Snow"
            77 -> "Snow Grains"
            80, 81 -> "Rain Showers"
            82 -> "Violent Rain"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm with Hail"
            else -> "Unknown"
        }
    }


    private fun updateWidgets(condition: String) {
        val mgr = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(applicationContext, MyWeatherWidgetProvider::class.java)
        val ids = mgr.getAppWidgetIds(provider)

        // Threshold for wide layout — tune if needed
        val WIDE_THRESHOLD_DP = 300

        for (id in ids) {
            val options = mgr.getAppWidgetOptions(id)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val useWide = minWidthDp >= WIDE_THRESHOLD_DP

            val layout = if (useWide) R.layout.widget_5x1 else R.layout.widget_2x1
            val views = RemoteViews(applicationContext.packageName, layout)

            val iconRes = pickDrawableForConditionAndTheme(condition, prefs, applicationContext)


            views.setImageViewResource(R.id.weather_icon, iconRes)

            if (useWide) {
                // only set condition text (no temperature)
                views.setTextViewText(R.id.weather_title, condition)
            } else {
                // compact layout: image only (if you have hidden text for accessibility)
                // views.setTextViewText(R.id.weather_text, condition) // optional
            }

            mgr.updateAppWidget(id, views)
        }
    }

     fun pickDrawableForConditionAndTheme(
        condition: String,
        prefs: SharedPreferences,
        context: Context
    ): Int {
        val theme = prefs.getString(PREF_THEME, THEME_MALAYALAM) ?: THEME_MALAYALAM
        val key = condition.lowercase(Locale.ROOT)

         val suffix = when (theme) {
             THEME_BW -> "_bw"
             THEME_TRANSPARENT -> "_tr"
             THEME_BW_TRANSPARENT -> "_bw_tr"
             else -> "_ml"
         }


         val baseName = when (key) {

             // ☀️ Clear & clouds
             "clear" -> "weather_clear"
             "clouds" -> "weather_clouds"

             // 🌫 Visibility
             "fog" -> "weather_fog"
             "mist" -> "weather_mist"

             // 🌦 Drizzle
             "drizzle" -> "weather_drizzle"
             "freezing drizzle" -> "weather_freezing_drizzle"

             // 🌧 Rain
             "rain" -> "weather_rain"
             "heavy rain" -> "weather_rain_heavy"
             "rain showers" -> "weather_rain_showers"
             "violent rain" -> "weather_violent_rain"
             "freezing rain" -> "weather_freezing_rain"

             // ❄️ Snow
             "snow" -> "weather_snow"
             "heavy snow" -> "weather_snow_heavy"
             "snow grains" -> "weather_snow_grains"
             "snow showers" -> "weather_snow_showers"

             // ⛈ Thunder
             "thunderstorm" -> "weather_thunder"
             "thunderstorm with hail" -> "weather_thunder_hail"

             else -> "weather_unknown"
         }


         val fullName = baseName + suffix
        val resId = context.resources.getIdentifier(fullName, "drawable", context.packageName)
        return if (resId != 0) resId else context.resources.getIdentifier(baseName + "_ml", "drawable", context.packageName)
    }


}
