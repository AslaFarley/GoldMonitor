package com.goldmonitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 金价记录（用于计算均线和历史对比）
 */
@Entity(tableName = "gold_price_records")
data class GoldPriceRecord(
    @PrimaryKey
    val date: Long, // 日期 timestamp（交易日）
    
    /** 金价（元/克） */
    val price: Double,
    
    /** 数据来源 */
    val source: String,
    
    /** 是否交易日 */
    val isTradingDay: Boolean = true,
    
    /** 记录时间 */
    val createdAt: Long = System.currentTimeMillis()
)
