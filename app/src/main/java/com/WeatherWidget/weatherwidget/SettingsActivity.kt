package com.WeatherWidget.weatherwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.weatherwidget.app.R

// ✅ IMPORT ALL CONSTANTS FROM ONE PLACE
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.PREFS_NAME
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.PREF_THEME
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_MALAYALAM
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_BW
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_TRANSPARENT
import com.WeatherWidget.weatherwidget.MyWeatherWidgetProvider.Companion.THEME_BW_TRANSPARENT

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001

        fun open(context: Context) {
            val i = Intent(context, SettingsActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(i)
        }
    }

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ---- UI references ----
        val rg = findViewById<RadioGroup>(R.id.rg_theme)
        val rbMalayalam = findViewById<RadioButton>(R.id.rb_malayalam)
        val rbBW = findViewById<RadioButton>(R.id.rb_bw)
        val rbTransparent = findViewById<RadioButton>(R.id.rb_transparent)
        val rbBWTransparent = findViewById<RadioButton>(R.id.rb_bw_transparent)
        val btnApply = findViewById<Button>(R.id.btn_apply)

        // ---- Restore previously selected theme ----
        val currentTheme = prefs.getString(PREF_THEME, THEME_MALAYALAM)

        when (currentTheme) {
            THEME_MALAYALAM -> rbMalayalam.isChecked = true
            THEME_BW -> rbBW.isChecked = true
            THEME_TRANSPARENT -> rbTransparent.isChecked = true
            THEME_BW_TRANSPARENT -> rbBWTransparent.isChecked = true
            else -> rbMalayalam.isChecked = true
        }

        // ---- Apply button ----
        btnApply.setOnClickListener {

            val chosenTheme = when (rg.checkedRadioButtonId) {
                R.id.rb_bw -> THEME_BW
                R.id.rb_transparent -> THEME_TRANSPARENT
                R.id.rb_bw_transparent -> THEME_BW_TRANSPARENT
                else -> THEME_MALAYALAM
            }

            // Save theme
            prefs.edit().putString(PREF_THEME, chosenTheme).apply()

            // 🔄 Immediately refresh all widgets
            try {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val component = ComponentName(this, MyWeatherWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(component)

                for (id in ids) {
                    MyWeatherWidgetProvider.updateSingleWidget(this, appWidgetManager, id)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            // Trigger weather worker (optional but good)
            try {
                val work = OneTimeWorkRequestBuilder<WeatherWorker>().build()
                WorkManager.getInstance(this).enqueue(work)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            Toast.makeText(this, "Applied: $chosenTheme", Toast.LENGTH_SHORT).show()
            finish()
        }

        // ---- Update location when app opens ----
        maybeUpdateLocationFromDevice()
    }

    // ---- Location handling ----

    private fun maybeUpdateLocationFromDevice() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            refreshLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            refreshLocation()
        }
    }

    private fun refreshLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            val hasFine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) return

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    prefs.edit()
                        .putFloat("lat", location.latitude.toFloat())
                        .putFloat("lon", location.longitude.toFloat())
                        .apply()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
