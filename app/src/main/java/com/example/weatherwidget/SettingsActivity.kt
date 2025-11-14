package com.example.weatherwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "weather_widget_prefs"
        const val PREF_THEME = "pref_widget_theme"
        const val THEME_MALAYALAM = "malayalam"
        const val THEME_BW = "black_white"
        const val THEME_TRANSPARENT = "transparent"

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

        // --- Theme UI setup ---
        val currentTheme = prefs.getString(PREF_THEME, THEME_MALAYALAM) ?: THEME_MALAYALAM

        val rg = findViewById<RadioGroup>(R.id.rg_theme)
        val rbMalayalam = findViewById<RadioButton>(R.id.rb_malayalam)
        val rbBW = findViewById<RadioButton>(R.id.rb_bw)
        val rbTransparent = findViewById<RadioButton>(R.id.rb_transparent)
        val btnApply = findViewById<Button>(R.id.btn_apply)

        when (currentTheme) {
            THEME_MALAYALAM -> rbMalayalam.isChecked = true
            THEME_BW -> rbBW.isChecked = true
            THEME_TRANSPARENT -> rbTransparent.isChecked = true
            else -> rbMalayalam.isChecked = true
        }

        btnApply.setOnClickListener {
            val selectedId = rg.checkedRadioButtonId
            val chosenTheme = when (selectedId) {
                R.id.rb_bw -> THEME_BW
                R.id.rb_transparent -> THEME_TRANSPARENT
                else -> THEME_MALAYALAM
            }

            prefs.edit().putString(PREF_THEME, chosenTheme).apply()

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

            // Trigger one-shot worker to update widgets with new theme + location
            try {
                val work = OneTimeWorkRequestBuilder<WeatherWorker>().build()
                WorkManager.getInstance(this).enqueue(work)
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            Toast.makeText(this, "Applied: $chosenTheme", Toast.LENGTH_SHORT).show()
            finish()
        }

        // --- Location: try to update when settings screen is opened ---
        maybeUpdateLocationFromDevice()
    }

    // Check permission and either request or refresh location
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
            // Request FINE location (gives best result)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    // Called after user responds to location permission dialog
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted -> get location now
                refreshLocation()
            } else {
                // Permission denied -> keep last saved coords (fallback behavior)
                Toast.makeText(
                    this,
                    "Location permission denied. Using last known location (if any).",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Actually fetches last known location and saves lat/lon in prefs
    private fun refreshLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            // Check again for safety
            val hasFine = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                return
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude

                        prefs.edit()
                            .putFloat("lat", lat.toFloat())
                            .putFloat("lon", lon.toFloat())
                            .apply()

                        Toast.makeText(
                            this,
                            "Location updated: $lat, $lon",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Trigger WeatherWorker to use new coords immediately
                        try {
                            val work = OneTimeWorkRequestBuilder<WeatherWorker>().build()
                            WorkManager.getInstance(this).enqueue(work)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }

                    } else {
                        // No last location available from system; keep old prefs
                        Toast.makeText(
                            this,
                            "No last known location from device. Using previous saved location.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(
                        this,
                        "Failed to get device location. Using previous saved location.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
