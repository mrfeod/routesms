package club.ithueti.routesms

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleHealthCheck()
    }

    private fun scheduleHealthCheck() {
        val work = PeriodicWorkRequestBuilder<HealthCheckWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "health_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
