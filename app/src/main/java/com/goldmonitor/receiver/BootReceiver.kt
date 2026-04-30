package com.goldmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goldmonitor.data.AppDatabase
import com.goldmonitor.service.MonitorScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机启动广播接收器
 * 手机重启后自动恢复定时任务
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 手机启动完成，恢复定时任务
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val config = db.globalConfigDao().getConfig()
                    
                    if (config != null) {
                        // 重新调度每日任务
                        MonitorScheduler.scheduleDaily(context, config.runHour, config.runMinute)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
