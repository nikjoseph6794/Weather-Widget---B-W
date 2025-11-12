package com.example.weatherwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "weather_widget_prefs"
        const val PREF_THEME = "pref_widget_theme"
        const val THEME_MALAYALAM = "malayalam"
        const val THEME_BW = "black_white"
        const val THEME_TRANSPARENT = "transparent"

        fun open(context: Context) {
            val i = Intent(context, SettingsActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(i)
        }
    }

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)
        } catch (t: Throwable) {
            // If layout inflation fails, show a toast and finish gracefully
            Toast.makeText(this, "Failed to open settings UI", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(PREF_THEME, THEME_MALAYALAM) ?: THEME_MALAYALAM

        val rg = findViewById<RadioGroup>(R.id.rg_theme)
        val rbMalayalam = findViewById<RadioButton>(R.id.rb_malayalam)
        val rbBW = findViewById<RadioButton>(R.id.rb_bw)
        val rbTransparent = findViewById<RadioButton>(R.id.rb_transparent)
        val btnApply = findViewById<Button>(R.id.btn_apply)

        // Restore selection safely
        when (current) {
            THEME_MALAYALAM -> rbMalayalam.isChecked = true
            THEME_BW -> rbBW.isChecked = true
            THEME_TRANSPARENT -> rbTransparent.isChecked = true
            else -> rbMalayalam.isChecked = true
        }

        btnApply.setOnClickListener {
            // Get selected radio id and map to value
            val selectedId = rg.checkedRadioButtonId
            val chosen = when (selectedId) {
                R.id.rb_bw -> THEME_BW
                R.id.rb_transparent -> THEME_TRANSPARENT
                else -> THEME_MALAYALAM
            }

            // Save preference atomically
            prefs.edit().putString(PREF_THEME, chosen).apply()

            // Trigger immediate widget refresh:
            try {
                // enqueue a one-shot worker to update widgets
                val work = OneTimeWorkRequestBuilder<WeatherWorker>().build()
                WorkManager.getInstance(this).enqueue(work)
            } catch (t: Throwable) {
                // ignore workmanager failure but notify user
                t.printStackTrace()
            }

            // Also trigger provider update using AppWidgetManager so UI refreshes quickly
            try {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val thisWidget = android.content.ComponentName(this, MyWeatherWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(thisWidget)
                // Force provider's onUpdate to run by calling updateAppWidget with current RemoteViews
                for (id in ids) {
                    // Let the provider handle reading prefs and rendering appropriately
                    MyWeatherWidgetProvider.updateSingleWidget(this, appWidgetManager, id)
                }
            } catch (t: Throwable) {
                // fallback: send broadcast (older providers may rely on this)
                try {
                    val updateIntent = Intent(this, MyWeatherWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    }
                    sendBroadcast(updateIntent)
                } catch (_: Throwable) { /* ignore */ }
            }

            Toast.makeText(this, "Applied: $chosen", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
