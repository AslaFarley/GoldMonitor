package com.goldmonitor.service

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.util.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * 监控任务 Worker（由 WorkManager 调度）
 */
class MonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    
    private val db = GoldMonitorApp.database
    private val notificationHelper = NotificationHelper(appContext)
    private val monitorService = MonitorService(
        context = appContext,
        db = db,
        notificationHelper = notificationHelper
    )
    
    override suspend fun doWork(): Result {
         return try {
            val result = monitorService.runMonitor()
            
            when (result) {
                is MonitorResult.Success -> {
                    Result.success()
                }
                is MonitorResult.Skipped -> {
                    Result.success()
                }
                is MonitorResult.Error -> {
                    // 如果是网络相关错误，则允许重试
                    if (result.message.contains("网络") || result.message.contains("API")) {
                        Result.retry()
                    } else {
                        notificationHelper.sendErrorNotification(result.message)
                        Result.success() // 其他非网络错误不再重试
                    }
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * 监控任务调度器（使用 AlarmManager + WorkManager）
 */
object MonitorScheduler {
    
    /**
     * 设置每日定时闹钟
     */
    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        com.goldmonitor.util.AlarmScheduler.scheduleDaily(context, hour, minute)
    }
    
    /**
     * 立即运行一次（通过前台服务）
     */
    fun runNow(context: Context) {
        // 直接启动前台服务执行
        val serviceIntent = Intent(context, MonitorForegroundService::class.java)
        serviceIntent.action = "ACTION_RUN_MONITOR"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /**
     * 调度 WorkManager 任务（带网络约束，用于补课或重试）
     */
    fun scheduleWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "monitor_retry_work",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
    
    /**
     * 取消闹钟
     */
    fun cancel(context: Context) {
        com.goldmonitor.util.AlarmScheduler.cancel(context)
    }
}
