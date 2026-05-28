package com.goldmonitor.service

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.goldmonitor.data.*
import com.goldmonitor.model.*
import com.goldmonitor.util.NotificationHelper
import com.goldmonitor.util.PriceCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 黄金监控服务
 * 
 * 核心逻辑：
 * 1. 窗口期策略：低于均线 X% 买 n 克，或到期买 1 克
 * 2. 全年监控池：连续下跌后反弹买 1 克
 * 
 * 数据源：固定使用上海黄金交易所（上金所）现货黄金价格
 * 
 * 价格更新策略：
 * - 今天获取的金价是盘中价（临时值）
 * - 明天获取 API 数据时，会返回今天的官方结算价
 * - 用结算价更新昨天的记录，确保均线计算基于准确的官方数据
 */
class MonitorService(
    private val context: Context,
    private val db: AppDatabase,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "MonitorService"
        private val monitorMutex = Mutex()
    }
    
    private val windowPeriodDao = db.windowPeriodDao()
    private val globalConfigDao = db.globalConfigDao()
    private val priceRecordDao = db.goldPriceRecordDao()
    private val buyLogDao = db.buyLogDao()
    private val apiCallLogDao = db.apiCallLogDao()
    private val sgeFetcher = SgeGoldFetcher(context, db)
    
    /**
     * 执行监控任务（每日运行）
     */
    suspend fun runMonitor(isManual: Boolean = false): MonitorResult = monitorMutex.withLock {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "=== runMonitor 开始 (isManual=$isManual) ===")
            try {
                // 0. 检查今日是否已运行成功（非手动模式下）
                val config = globalConfigDao.getConfig()
                val today = getTodayTimestamp()
                
                if (!isManual && config?.lastRunDate != null && config.lastRunDate >= today) {
                    Log.d(TAG, "今日已运行成功，跳过自动执行")
                    return@withContext MonitorResult.Skipped("今日已运行")
                }

                Log.d(TAG, "1. 检查是否是工作日...")
                
                // 1. 检查是否是工作日
                if (!isTradingDay()) {
                    Log.d(TAG, "今日非交易日，跳过")
                    return@withContext MonitorResult.Skipped("非交易日")
                }
                
                // 2. 获取或初始化全局配置
                var currentConfig = config ?: GlobalConfig(
                    poolBalance = 0,
                    crashThreshold = 3.0,
                    runHour = 19,
                    runMinute = 0
                )
                if (config == null) {
                    globalConfigDao.insert(currentConfig)
                    Log.d(TAG, "初始化全局配置")
                }
                
                // 3. 获取今日金价（上金所，强制刷新）
                val priceData = sgeFetcher.fetchTodayPrice(forceRefresh = true)
                
                // 记录 API 调用日志
                val logResult = when {
                    priceData?.error != null -> "错误: ${priceData.error.message}"
                    priceData == null -> "失败: 无法获取数据"
                    else -> "成功"
                }
                apiCallLogDao.insert(
                    ApiCallLog(
                        isManual = isManual,
                        result = logResult,
                        price = priceData?.price
                    )
                )
                
                // 调试日志
                Log.d(TAG, "API 返回：price=${priceData?.price}, yesterdayPrice=${priceData?.yesterdayPrice}, error=${priceData?.error}")
                
                // 检查 API 错误
                if (priceData?.error != null) {
                    Log.e(TAG, "API 错误：${priceData.error.code} - ${priceData.error.message}")
                    return@withContext MonitorResult.Error("API 错误：${priceData.error.message}")
                }
                
                if (priceData == null || priceData.price <= 0) {
                    return@withContext MonitorResult.Error("获取金价失败")
                }
                
                val todayPrice = priceData.price
                // today 已在上方定义
                val yesterday = today - 24 * 60 * 60 * 1000 // 昨天 0 点
                
                Log.d(TAG, "今日金价：$todayPrice (上金所)")
                
                // 4. 更新昨日金价
                Log.d(TAG, "4. 开始更新昨日金价...")
                if (priceData.yesterdayPrice != null && priceData.yesterdayPrice > 0) {
                    Log.d(TAG, "4.1 更新 global_config.lastPrice...")
                    globalConfigDao.updateLastPrice(priceData.yesterdayPrice, yesterday, System.currentTimeMillis())
                    Log.d(TAG, "4.1 完成")
                    
                    Log.d(TAG, "4.2 插入昨天金价记录...")
                    priceRecordDao.insert(
                        GoldPriceRecord(
                            date = yesterday,
                            price = priceData.yesterdayPrice,
                            source = "上海黄金交易所",
                            isTradingDay = true
                        )
                    )
                    Log.d(TAG, "4.2 完成")
                    Log.d(TAG, "更新昨日结算价：${priceData.yesterdayPrice}")
                }
                Log.d(TAG, "4. 完成")
                
                // 5. 保存今日金价记录
                Log.d(TAG, "5. 插入今日金价记录...")
                priceRecordDao.insert(
                    GoldPriceRecord(
                        date = today,
                        price = todayPrice,
                        source = "上海黄金交易所",
                        isTradingDay = true
                    )
                )
                Log.d(TAG, "5. 完成")
                
                // 6. 执行窗口期策略
                Log.d(TAG, "6. 开始执行窗口期策略...")
                val windowResult = executeWindowPeriodStrategy(todayPrice, today)
                Log.d(TAG, "6. 完成 - 窗口期结果：${windowResult.size} 条")
                
                // 7. 执行暴跌监控策略（优先使用 API 返回的昨结算价）
                val yesterdayPrice = priceData.yesterdayPrice ?: currentConfig.lastPrice
                val crashResult = executeCrashMonitorStrategy(todayPrice, yesterdayPrice, today, currentConfig)
                
                // 8. 更新最后运行时间和昨日金价
                globalConfigDao.updateLastPrice(todayPrice, today, System.currentTimeMillis())
                
                // 9. 汇总结果
                val messages = mutableListOf<String>()
                messages.add("今日金价：${String.format("%.2f", todayPrice)} 元/克 (上金所)")
                if (windowResult.isNotEmpty()) messages.addAll(windowResult)
                if (crashResult.isNotEmpty()) messages.addAll(crashResult)
                
                // 9. 发送通知
                notificationHelper.sendPriceUpdateNotification(
                    price = todayPrice,
                    source = "上海黄金交易所",
                    poolBalance = currentConfig.poolBalance,
                    messages = messages
                )
                
                Log.d(TAG, "监控任务完成")
                MonitorResult.Success(messages.joinToString("\n"))
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 协程被取消（应用切换后台/页面关闭）
                Log.w(TAG, "监控任务被取消：${e.message}")
                MonitorResult.Skipped("任务被取消")
            } catch (e: Exception) {
                Log.e(TAG, "监控任务失败", e)
                MonitorResult.Error(e.message ?: "未知错误")
            }
        }
    }
    
    /**
     * 执行窗口期策略
     */
    private suspend fun executeWindowPeriodStrategy(
        todayPrice: Double,
        today: Long
    ): List<String> {
        val results = mutableListOf<String>()
        val activePeriods = windowPeriodDao.getNotTriggered()
        
        for (period in activePeriods) {
            // 跳过已触发的窗口期
            if (period.isTriggered) continue
            
            // 检查是否在窗口期内（核心修复：必须在开始日期之后）
            if (!period.isInWindow()) {
                Log.d(TAG, "窗口期 ${period.getDateRangeString()} 尚未开始或已结束，跳过")
                continue
            }
            
            // 检查是否到期（今年）- 最后一天触发
            val calendar = java.util.Calendar.getInstance()
            val todayMonth = calendar.get(java.util.Calendar.MONTH) + 1
            val todayDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val todayValue = todayMonth * 100 + todayDay
            val endValue = period.endMonth * 100 + period.endDay
            
            // 如果是窗口期最后一天且未触发，强制买入
            if (todayValue == endValue && !period.isTriggered) {
                // 窗口期到期，强制买入 1 克
                val remainingAmount = period.buyAmount - 1
                handleWindowExpire(period, todayPrice, calendar.timeInMillis, remainingAmount)
                results.add("窗口期到期：买入 1 克 ($remainingAmount 克进入监控池)")
                continue
            }
            
            // 计算均线
            Log.d(TAG, "窗口期 ${period.startMonth}-${period.startDay} 到 ${period.endMonth}-${period.endDay}：开始计算均线...")
            val maPeriod = period.maPeriod
            val previousPrices = getPreviousPrices(today, maPeriod)
            val avgPrice = PriceCalculator.calculateMA(previousPrices, maPeriod)
            
            Log.d(TAG, "均线计算完成：$maPeriod 日均线 = $avgPrice (基于过去 ${previousPrices.size} 个交易日)")
            if (avgPrice == null) {
                Log.w(TAG, "无法计算${maPeriod}日均线，数据不足")
                continue
            }
            
            // 检查是否触发条件：低于均线 X%
            val threshold = avgPrice * (1 - period.triggerThreshold / 100)
            if (todayPrice <= threshold) {
                // 触发买入
                handleWindowTrigger(period, todayPrice, today)
                results.add("窗口期触发：低于${maPeriod}日均线${period.triggerThreshold}%，买入${period.buyAmount}克")
            }
        }
        
        return results
    }
    
    /**
     * 执行暴跌监控策略
     */
    private suspend fun executeCrashMonitorStrategy(
        todayPrice: Double,
        yesterdayPrice: Double?,
        today: Long,
        config: GlobalConfig
    ): List<String> {
        val results = mutableListOf<String>()
        
        if (yesterdayPrice == null || yesterdayPrice <= 0) {
            // 没有昨日数据，无法判断跌幅
            Log.d(TAG, "暴跌监控：缺少昨日价格数据")
            return results
        }
        
        // 计算今日跌幅
        val dropPercent = (yesterdayPrice - todayPrice) / yesterdayPrice * 100
        
        Log.d(TAG, "今日跌幅：${String.format("%.2f", dropPercent)}% (阈值：${config.crashThreshold}%)")
        
        if (dropPercent >= config.crashThreshold) {
            // 今日暴跌，进入连续下跌状态
            val newDropDays = config.continuousDropDays + 1
            globalConfigDao.updateContinuousDropStatus(true, newDropDays, System.currentTimeMillis())
            Log.d(TAG, "连续下跌第 $newDropDays 天")
        } else {
            // 今日未下跌（持平或反弹）
            if (config.isContinuousDrop && config.continuousDropDays > 0) {
                // 之前处于连续下跌状态，今日反弹 -> 触发买入
                if (config.poolBalance > 0) {
                    handleCrashRebound(todayPrice, today, config)
                    results.add("暴跌反弹：连续下跌${config.continuousDropDays}天后反弹，买入 1 克")
                }
            }
            // 重置连续下跌状态
            globalConfigDao.updateContinuousDropStatus(false, 0, System.currentTimeMillis())
        }
        
        return results
    }
    
    /**
     * 处理窗口期触发买入
     */
    private suspend fun handleWindowTrigger(period: WindowPeriod, price: Double, date: Long) {
        db.withTransaction {
            // 重新检查状态，防止并发触发
            val currentPeriod = windowPeriodDao.getById(period.id)
            if (currentPeriod == null || currentPeriod.isTriggered) return@withTransaction

            // 更新窗口期状态
            windowPeriodDao.update(
                currentPeriod.copy(
                    isTriggered = true,
                    triggeredDate = date,
                    actualBuyAmount = currentPeriod.buyAmount
                )
            )
            
            // 记录买入日志
            buyLogDao.insert(
                BuyLog(
                    buyDate = date,
                    buyAmount = currentPeriod.buyAmount,
                    price = price,
                    reason = BuyReason.WINDOW_TRIGGER,
                    windowPeriodId = currentPeriod.id,
                    note = "窗口期触发：低于${currentPeriod.maPeriod}日均线${currentPeriod.triggerThreshold}%"
                )
            )
        }
        
        // 发送买入通知 (在事务外发送，避免事务失败导致通知错误)
        notificationHelper.sendBuyNotification(
            amount = period.buyAmount,
            price = price,
            reason = "窗口期触发",
            note = "低于${period.maPeriod}日均线${period.triggerThreshold}%"
        )
    }

    /**
     * 处理窗口期到期
     */
    private suspend fun handleWindowExpire(
        period: WindowPeriod,
        price: Double,
        date: Long,
        remainingAmount: Int
    ) {
        db.withTransaction {
            // 重新检查状态
            val currentPeriod = windowPeriodDao.getById(period.id)
            if (currentPeriod == null || currentPeriod.isTriggered) return@withTransaction

            // 更新窗口期状态
            windowPeriodDao.update(
                currentPeriod.copy(
                    isTriggered = true,
                    triggeredDate = date,
                    actualBuyAmount = 1
                )
            )
            
            // 更新监控池余额
            val config = globalConfigDao.getConfig() ?: return@withTransaction
            val newBalance = config.poolBalance + remainingAmount
            globalConfigDao.updatePoolBalance(newBalance, System.currentTimeMillis())
            
            Log.d(TAG, "窗口期到期：买入 1 克，${remainingAmount}克进入监控池，新余额：$newBalance")
            
            // 记录买入日志
            buyLogDao.insert(
                BuyLog(
                    buyDate = date,
                    buyAmount = 1,
                    price = price,
                    reason = BuyReason.WINDOW_EXPIRE,
                    windowPeriodId = currentPeriod.id,
                    note = "窗口期到期强制买入，${remainingAmount}克进入监控池"
                )
            )
        }
        
        // 发送买入通知
        notificationHelper.sendBuyNotification(
            amount = 1,
            price = price,
            reason = "窗口期到期",
            note = "${remainingAmount}克进入监控池"
        )
    }

    /**
     * 处理暴跌反弹买入
     */
    private suspend fun handleCrashRebound(price: Double, date: Long, config: GlobalConfig) {
        var actualContinuousDropDays = 0
        db.withTransaction {
            // 重新获取配置以获取最新余额 and 状态
            val currentConfig = globalConfigDao.getConfig() ?: return@withTransaction
            if (!currentConfig.isContinuousDrop || currentConfig.poolBalance <= 0) return@withTransaction
            
            actualContinuousDropDays = currentConfig.continuousDropDays
            
            // 更新监控池余额
            val newBalance = currentConfig.poolBalance - 1
            globalConfigDao.updatePoolBalance(newBalance, System.currentTimeMillis())
            
            // 记录买入日志
            buyLogDao.insert(
                BuyLog(
                    buyDate = date,
                    buyAmount = 1,
                    price = price,
                    reason = BuyReason.CRASH_REBOUND,
                    note = "连续下跌${actualContinuousDropDays}天后反弹"
                )
            )
        }
        
        // 发送买入通知
        notificationHelper.sendBuyNotification(
            amount = 1,
            price = price,
            reason = "暴跌反弹",
            note = "连续下跌${actualContinuousDropDays}天后反弹"
        )
    }
    
    /**
     * 获取指定日期之前的历史金价（不含该日期）
     * 
     * @param today 当前日期时间戳
     * @param count 需要获取的天数
     * @return 价格列表，按时间倒序排列（索引 0 是昨天）
     */
    private suspend fun getPreviousPrices(today: Long, count: Int): List<Double> {
        val prices = mutableListOf<Double>()
        val calendar = Calendar.getInstance()
        calendar.time = Date(today)
        
        var daysBack = 0
        val maxDaysBack = count * 3 // 最多往前找 3 倍天数，防止死循环
        
        while (prices.size < count && daysBack < maxDaysBack) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            daysBack++
            
            val date = calendar.timeInMillis
            val record = priceRecordDao.getByDate(date)
            if (record != null) {
                prices.add(record.price)
            }
        }
        return prices
    }
    
    /**
     * 检查是否是交易日（工作日）
     */
    private fun isTradingDay(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
    }
    
    /**
     * 获取今日 0 点时间戳
     */
    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/**
 * 监控任务结果
 */
sealed class MonitorResult {
    data class Success(val message: String) : MonitorResult()
    data class Skipped(val reason: String) : MonitorResult()
    data class Error(val message: String) : MonitorResult()
}
