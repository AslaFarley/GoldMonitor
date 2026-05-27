package com.goldmonitor.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * API 调用日志
 */
@Entity(tableName = "api_call_logs")
data class ApiCallLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 调用时间戳 */
    val callTime: Long = System.currentTimeMillis(),
    
    /** 是否是手动调用 */
    val isManual: Boolean = false,
    
    /** 调用结果描述 */
    val result: String,
    
    /** 抓取到的价格（如果有） */
    val price: Double? = null
)
