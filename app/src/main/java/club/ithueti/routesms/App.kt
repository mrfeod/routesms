package club.ithueti.routesms

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleHealthCheck(this)
    }

    companion object {
        fun scheduleHealthCheck(context: android.content.Context) {
            val intervalHours = HealthSettings.intervalHours(context).toLong()
            if (intervalHours == 0L) {
                WorkManager.getInstance(context).cancelUniqueWork("health_check")
                return
            }
            val work = PeriodicWorkRequestBuilder<HealthCheckWorker>(intervalHours, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "health_check",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }
    }
}
