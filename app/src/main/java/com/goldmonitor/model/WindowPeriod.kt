package com.goldmonitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 窗口期配置（月/日格式，每年重复）
 * 
 * 规则：窗口期内只触发 1 次
 * - 要么某天低于均线 X% 买入 n 克
 * - 要么到期买入 1 克（n-1 克进池子）
 */
@Entity(tableName = "window_periods")
data class WindowPeriod(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 窗口期开始月份（1-12） */
    val startMonth: Int,
    
    /** 窗口期开始日期（1-31） */
    val startDay: Int,
    
    /** 窗口期结束月份（1-12） */
    val endMonth: Int,
    
    /** 窗口期结束日期（1-31） */
    val endDay: Int,
    
    /** 均线周期：5/10/20 日 */
    val maPeriod: Int, // 5, 10, or 20
    
    /** 触发阈值 X%（低于均线 X% 触发） */
    val triggerThreshold: Double, // e.g., 2.0 means 2%
    
    /** 触发时买入克数（整数） */
    val buyAmount: Int, // e.g., 3
    
    /** 是否已触发 */
    val isTriggered: Boolean = false,
    
    /** 触发日期（如果已触发） */
    val triggeredDate: Long? = null,
    
    /** 实际买入克数（如果已触发，整数） */
    val actualBuyAmount: Int? = null,
    
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 获取今年的开始日期 */
    fun getStartDateThisYear(): java.util.Date {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, startMonth - 1, startDay, 0, 0, 0)
        return calendar.time
    }
    
    /** 获取今年的结束日期 */
    fun getEndDateThisYear(): java.util.Date {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, endMonth - 1, endDay, 0, 0, 0)
        return calendar.time
    }
    
    /** 获取开始日期字符串 */
    fun getStartDateString(): String {
        return String.format("%02d-%02d", startMonth, startDay)
    }
    
    /** 获取结束日期字符串 */
    fun getEndDateString(): String {
        return String.format("%02d-%02d", endMonth, endDay)
    }
    
    /** 获取日期范围字符串 */
    fun getDateRangeString(): String {
        return "${getStartDateString()} ~ ${getEndDateString()}"
    }
    
    /** 检查是否在窗口期内（今年） */
    fun isInWindow(): Boolean {
        val today = java.util.Calendar.getInstance()
        val todayMonth = today.get(java.util.Calendar.MONTH) + 1 // 1-12
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH) // 1-31
        
        // 转换为数字比较：MMDD
        val nowValue = todayMonth * 100 + todayDay
        val startValue = startMonth * 100 + startDay
        val endValue = endMonth * 100 + endDay
        
        return nowValue >= startValue && nowValue <= endValue
    }
    
    /** 检查是否已过期（今年） */
    fun isExpired(): Boolean {
        val today = java.util.Calendar.getInstance()
        val todayMonth = today.get(java.util.Calendar.MONTH) + 1
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
        
        val nowValue = todayMonth * 100 + todayDay
        val endValue = endMonth * 100 + endDay
        
        return nowValue > endValue
    }
    
    /** 获取剩余天数（估算） */
    fun getRemainingDays(): Long {
        if (isExpired()) return 0
        
        val today = java.util.Calendar.getInstance()
        val todayMonth = today.get(java.util.Calendar.MONTH) + 1
        val todayDay = today.get(java.util.Calendar.DAY_OF_MONTH)
        
        val nowValue = todayMonth * 100 + todayDay
        val endValue = endMonth * 100 + endDay
        
        // 简单估算
        return (endValue - nowValue).toLong()
    }
}
