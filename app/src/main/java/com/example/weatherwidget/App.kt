package com.example.weatherwidget

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val work = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weather_poll",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
