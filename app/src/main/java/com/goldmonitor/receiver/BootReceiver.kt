package com.goldmonitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
                        // 1. 重新调度定时任务
                        MonitorScheduler.scheduleDaily(context, config.runHour, config.runMinute)
                        
                        // 2. 补课逻辑：如果当前时间已过设定时间，且今日未运行，立即触发一次
                        val calendar = java.util.Calendar.getInstance()
                        val nowHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        val nowMinute = calendar.get(java.util.Calendar.MINUTE)
                        
                        val isPastRunTime = if (nowHour > config.runHour) {
                            true
                        } else if (nowHour == config.runHour) {
                            nowMinute >= config.runMinute
                        } else {
                            false
                        }
                        
                        if (isPastRunTime) {
                            // 检查今日是否运行过
                            val today = getTodayTimestamp()
                            if (config.lastRunDate == null || config.lastRunDate < today) {
                                Log.d("BootReceiver", "开机补课：当前已过设定时间且今日未运行，启动监控")
                                MonitorScheduler.runNow(context)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getTodayTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
