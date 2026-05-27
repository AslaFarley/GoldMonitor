package com.goldmonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.R
import com.goldmonitor.ui.MainActivity
import com.goldmonitor.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 监控前台服务
 */
class MonitorForegroundService : Service() {
    
    companion object {
        private const val TAG = "MonitorForegroundService"
        private const val CHANNEL_ID = "monitor_service_channel"
        private const val NOTIFICATION_ID = 2001
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "前台服务创建")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "前台服务启动：${intent?.action}")
        
        // 创建通知
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // 执行监控任务
        if (intent?.action == "ACTION_RUN_MONITOR") {
            executeMonitorTask()
        }
        
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("黄金监控运行中")
            .setContentText("正在执行每日监控任务...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "监控服务通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "黄金监控后台服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun executeMonitorTask() {
        serviceScope.launch {
            try {
                Log.d(TAG, "开始执行监控任务")
                
                val db = GoldMonitorApp.database
                val notificationHelper = NotificationHelper(applicationContext)
                val monitorService = MonitorService(
                    context = applicationContext,
                    db = db,
                    notificationHelper = notificationHelper
                )
                
                val result = withTimeoutOrNull(30000) {
                    monitorService.runMonitor(isManual = false)
                }
                
                if (result == null) {
                    Log.e(TAG, "监控任务超时")
                } else {
                    when (result) {
                        is MonitorResult.Success -> {
                            Log.d(TAG, "监控完成：${result.message}")
                        }
                        is MonitorResult.Skipped -> {
                            Log.d(TAG, "监控跳过：${result.reason}")
                        }
                        is MonitorResult.Error -> {
                            Log.e(TAG, "监控失败：${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "监控任务异常", e)
            } finally {
                // 任务完成后停止服务
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "前台服务停止")
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "前台服务销毁")
    }
}
