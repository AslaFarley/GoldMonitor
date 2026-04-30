package com.goldmonitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 全局配置
 */
@Entity(tableName = "global_config")
data class GlobalConfig(
    @PrimaryKey
    val id: Int = 1, // 单例，固定 ID=1
    
    /** 监控池当前余额（克数，整数存储） */
    val poolBalance: Int = 0,
    
    /** 暴跌阈值 Y%（单日跌幅超过 Y%） */
    val crashThreshold: Double = 3.0,
    
    /** 每日运行时间（小时） */
    val runHour: Int = 19,
    
    /** 每日运行时间（分钟） */
    val runMinute: Int = 0,
    
    /** 数据源（固定使用上金所） */
    val dataSource: String = "上海黄金交易所",
    
    /** 上次运行日期 */
    val lastRunDate: Long? = null,
    
    /** 昨日金价（用于计算跌幅） */
    val lastPrice: Double? = null,
    
    /** 是否处于连续下跌状态 */
    val isContinuousDrop: Boolean = false,
    
    /** 连续下跌天数 */
    val continuousDropDays: Int = 0,
    
    /** 更新时间 */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** 今日 API 调用次数 */
    val apiCallCount: Int = 0,
    
    /** 最后调用日期（时间戳） */
    val lastCallDate: Long? = null
)



/**
 * 均线周期类型
 */
enum class MaPeriod(val days: Int) {
    MA_5(5),
    MA_10(10),
    MA_20(20)
}
