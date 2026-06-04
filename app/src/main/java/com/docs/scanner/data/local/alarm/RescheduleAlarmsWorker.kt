package com.docs.scanner.data.local.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import timber.log.Timber

class RescheduleAlarmsWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val ep = EntryPointAccessors.fromApplication(
            applicationContext,
            TermAlarmReceiver.ReceiverEntryPoint::class.java
        )
        val active = ep.appDatabase().termDao().observeActive().first()
        active.forEach { ep.alarmScheduler().scheduleTerm(it) }
        Timber.i("✅ Rescheduled ${active.size} term alarms")
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Reschedule worker failed")
        Result.retry()
    }
}