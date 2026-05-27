package com.goldmonitor.util

import android.util.Log
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备份数据模型
 */
data class AppBackup(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val globalConfig: GlobalConfig?,
    val windowPeriods: List<WindowPeriod>,
    val goldPriceRecords: List<GoldPriceRecord>,
    val buyLogs: List<BuyLog>,
    val apiCallLogs: List<ApiCallLog>
)

/**
 * 数据备份与恢复管理器
 */
object BackupManager {
    private const val TAG = "BackupManager"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 将所有数据导出为 JSON 字符串
     */
    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val db = GoldMonitorApp.database
        val backup = AppBackup(
            globalConfig = db.globalConfigDao().getConfig(),
            windowPeriods = db.windowPeriodDao().getAll(),
            goldPriceRecords = db.goldPriceRecordDao().getRecent(10000), // 导出更多记录
            buyLogs = db.buyLogDao().getAll(),
            apiCallLogs = db.apiCallLogDao().getTodayLogs(0) // 获取全部
        )
        return@withContext gson.toJson(backup)
    }

    /**
     * 从 JSON 字符串恢复数据
     */
    suspend fun importData(json: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, AppBackup::class.java)
                ?: return@withContext Result.failure(Exception("解析备份文件失败"))

            val db = GoldMonitorApp.database
            
            try {
                db.runInTransaction {
                    // 1. 清空旧数据
                    db.globalConfigDao().clearAllSync()
                    db.windowPeriodDao().clearAllSync()
                    db.goldPriceRecordDao().clearAllSync()
                    db.buyLogDao().clearAllSync()
                    db.apiCallLogDao().clearAllSync()

                    // 2. 恢复配置
                    backup.globalConfig?.let {
                        db.globalConfigDao().insertSync(it)
                    }

                    // 3. 恢复窗口期
                    backup.windowPeriods.forEach {
                        db.windowPeriodDao().insertSync(it)
                    }

                    // 4. 恢复金价记录
                    backup.goldPriceRecords.forEach {
                        db.goldPriceRecordDao().insertSync(it)
                    }

                    // 5. 恢复买入日志
                    backup.buyLogs.forEach {
                        db.buyLogDao().insertSync(it)
                    }

                    // 6. 恢复 API 日志
                    backup.apiCallLogs.forEach {
                        db.apiCallLogDao().insertSync(it)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "恢复数据库出错", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "导入解析失败", e)
            Result.failure(e)
        }
    }
}
