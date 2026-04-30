package com.goldmonitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 买入记录日志
 */
@Entity(tableName = "buy_logs")
data class BuyLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 买入日期 */
    val buyDate: Long,
    
    /** 买入克数（整数） */
    val buyAmount: Int,
    
    /** 买入时金价 */
    val price: Double,
    
    /** 买入原因 */
    val reason: BuyReason,
    
    /** 关联的窗口期 ID（如果是窗口期触发） */
    val windowPeriodId: Long? = null,
    
    /** 备注 */
    val note: String? = null,
    
    /** 记录时间 */
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 买入原因
 */
enum class BuyReason(val description: String) {
    WINDOW_TRIGGER("窗口期触发：低于均线"),
    WINDOW_EXPIRE("窗口期到期：强制买入"),
    CRASH_REBOUND("暴跌反弹：连续下跌后反弹"),
    MANUAL("手动买入")
}
