package com.goldmonitor

import android.app.Application
import com.goldmonitor.data.AppDatabase
import com.goldmonitor.model.GlobalConfig
import com.goldmonitor.model.WindowPeriod
import com.goldmonitor.service.MonitorScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用入口
 */
class GoldMonitorApp : Application() {
    
    companion object {
        lateinit var database: AppDatabase
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        
        // 初始化默认配置（如果不存在）
        CoroutineScope(Dispatchers.IO).launch {
            val config = database.globalConfigDao().getConfig()
            if (config == null) {
                database.globalConfigDao().insert(
                    GlobalConfig(
                        poolBalance = 3,  // 监控池 3 克
                        crashThreshold = 3.0,
                        runHour = 14,     // 14:30 运行
                        runMinute = 30
                    )
                )
                
                // 添加默认窗口期
                insertDefaultWindowPeriods()
                
                // 设置闹钟
                MonitorScheduler.scheduleDaily(applicationContext, 14, 30)
            } else {
                // 如果配置已存在，重新设置闹钟
                MonitorScheduler.scheduleDaily(applicationContext, config.runHour, config.runMinute)
            }
        }
    }
    
    /**
     * 插入默认窗口期配置
     */
    private suspend fun insertDefaultWindowPeriods() {
        val defaultPeriods = listOf(
            // 窗口期 1:1 月 15 日 -1 月 25 日
            WindowPeriod(
                startMonth = 1, startDay = 15,
                endMonth = 1, endDay = 25,
                maPeriod = 5, triggerThreshold = 2.0, buyAmount = 2
            ),
            // 窗口期 2:3 月 1 日 -3 月 15 日
            WindowPeriod(
                startMonth = 3, startDay = 1,
                endMonth = 3, endDay = 15,
                maPeriod = 5, triggerThreshold = 2.0, buyAmount = 2
            ),
            // 窗口期 3:6 月 1 日 -6 月 15 日
            WindowPeriod(
                startMonth = 6, startDay = 1,
                endMonth = 6, endDay = 15,
                maPeriod = 5, triggerThreshold = 2.0, buyAmount = 2
            ),
            // 窗口期 4:7 月 20 日 -7 月 31 日
            WindowPeriod(
                startMonth = 7, startDay = 20,
                endMonth = 7, endDay = 31,
                maPeriod = 5, triggerThreshold = 2.0, buyAmount = 2
            ),
            // 窗口期 5:9 月 1 日 -9 月 20 日
            WindowPeriod(
                startMonth = 9, startDay = 1,
                endMonth = 9, endDay = 20,
                maPeriod = 5, triggerThreshold = 2.0, buyAmount = 2
            )
        )
        
        for (period in defaultPeriods) {
            database.windowPeriodDao().insert(period)
        }
    }
}
