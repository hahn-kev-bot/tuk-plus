package app.hahn.tukplus.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.logging.TukLog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Refreshes the shop list and the Home pages every few hours on Wi-Fi, so the app
 * opens with recent data (PLAN.md §5.4).
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun browse(): BrowseRepository
        fun log(): TukLog
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        deps.log().i("work", "refresh_start")
        val results = (listOf(deps.browse().eateries) + deps.browse().homePages).map { it.refresh() }.map { it.await() }
        val failed = results.count { it.status is app.hahn.tukplus.core.data.Cached.Status.Error }
        deps.log().i("work", "refresh_done", "failed" to failed)
        return if (failed == results.size) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(4, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
