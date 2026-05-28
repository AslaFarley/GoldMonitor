package com.goldmonitor.util

import android.util.Log

/**
 * 金价计算工具类
 * 确保 UI 和后台服务使用完全统一的计算逻辑
 */
object PriceCalculator {
    private const val TAG = "PriceCalculator"

    /**
     * 计算移动平均线 (MA)
     * 统一逻辑：排除今日金价，只计算过去 N 个交易日的平均值
     * 
     * @param previousPrices 历史价格列表，按时间倒序排列（索引 0 是昨天）
     * @param days 均线周期（如 5, 10, 20）
     * @return 均线价格，如果数据不足则返回 null
     */
    fun calculateMA(previousPrices: List<Double>, days: Int): Double? {
        if (previousPrices.isEmpty()) return null
        
        // 确保只取需要的周期天数，且不能超过列表长度
        val takeCount = minOf(days, previousPrices.size)
        val sum = previousPrices.take(takeCount).sum()
        val ma = sum / takeCount
        
        Log.d(TAG, "计算 MA$days: 取了 $takeCount 天数据, 平均值 = $ma")
        return ma
    }

    /**
     * 计算当前价格相对于目标的百分比差异
     * 公式: (当前价格 - 目标价格) / 目标价格 * 100
     * 
     * @param current 当前金价
     * @param target 目标价格（如 MA）
     * @return 百分比（如 -2.5 表示低于 2.5%）
     */
    fun calculatePercentDiff(current: Double, target: Double): Double {
        if (target == 0.0) return 0.0
        return (current - target) / target * 100
    }
}
