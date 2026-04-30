package com.goldmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.goldmonitor.service.MonitorForegroundService

/**
 * 闹钟触发接收器
 */
class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "闹钟触发，启动监控服务")
        
        // 启动前台服务执行监控
        val serviceIntent = Intent(context, MonitorForegroundService::class.java)
        serviceIntent.action = "ACTION_RUN_MONITOR"
        
        // Android 8.0+ 必须使用 startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
