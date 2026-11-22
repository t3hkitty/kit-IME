package com.kitime.ime.data

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kitime.ime.pref.KitImePreferences
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for nightly corpus ingestion, gentle decay, and snapshotting.
 */
class NightlyCorpusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "KitImeNightlyCorpusWork"

        /**
         * Enqueue or schedule nightly 24-hour periodic work.
         */
        fun scheduleNightly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<NightlyCorpusWorker>(
                24, TimeUnit.HOURS,
                2, TimeUnit.HOURS // flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = PredictorDatabase.getInstance(applicationContext)
            val gatherer = LocalCorpusGatherer(applicationContext, db)
            val prefs = KitImePreferences(applicationContext)

            // 1. Run corpus decay and gather from SMS, SAF, and remote Google Docs
            val safUri = prefs.safFolderUri?.let { Uri.parse(it) }
            val docUrls = prefs.getGoogleDocUrlList()
            gatherer.runFullGather(safUri, docUrls)

            // 2. Perform daily database snapshot (retaining last 5)
            db.createDailySnapshot()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
