package com.goldmonitor.data

import android.content.Context
import android.util.Log
import com.goldmonitor.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * 日志标签
 */
private const val TAG = "GoldPriceFetcher"

/**
 * 金价抓取结果
 */
data class GoldPriceData(
    val price: Double,           // 最新价
    val yesterdayPrice: Double?, // 昨结算价
    val change: Double?,         // 涨跌额
    val updateTime: String?,     // 更新时间
    val error: ApiError? = null  // 错误信息
)

/**
 * API 错误信息
 */
data class ApiError(
    val code: Int,      // 错误码
    val message: String // 错误描述
)

/**
 * 金价抓取器（仅支持上金所）
 */
class SgeGoldFetcher(
    private val context: Context,
    private val db: AppDatabase? = null
) {
    
    companion object {
        private const val API_URL = "https://web.juhe.cn/finance/gold/shgold"
        private const val PREF_NAME = "gold_price_cache"
        private const val KEY_API_KEY = "juhe_api_key"
        private const val KEY_LAST_PRICE = "last_price"
        private const val KEY_LAST_YESTERDAY_PRICE = "last_yesterday_price"
        private const val KEY_LAST_FETCH_TIME = "last_fetch_time"
    }
    
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 获取当前的 API Key (仅从 SharedPreferences 获取，不再使用内置默认 Key)
     */
    private fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }
    
    /**
     * 获取今日金价（带缓存）
     * @param forceRefresh 是否强制刷新（手动运行时使用）
     */
    suspend fun fetchTodayPrice(forceRefresh: Boolean = false): GoldPriceData? = withContext(Dispatchers.IO) {
        // 如果不是强制刷新，检查是否有缓存（24 小时内）
        if (!forceRefresh) {
            val lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0)
            val now = System.currentTimeMillis()
            if (now - lastFetchTime < 24 * 60 * 60 * 1000) {
                val cachedPrice = prefs.getFloat(KEY_LAST_PRICE, 0f)
                val cachedYesterdayPrice = prefs.getFloat(KEY_LAST_YESTERDAY_PRICE, 0f)
                if (cachedPrice > 0) {
                    Log.d(TAG, "上金所：使用缓存价格：$cachedPrice, 昨结算：$cachedYesterdayPrice")
                    // 返回缓存的昨结算（如果有）
                    val yesterdayPrice = if (cachedYesterdayPrice > 0) cachedYesterdayPrice.toDouble() else null
                    return@withContext GoldPriceData(cachedPrice.toDouble(), yesterdayPrice, null, null)
                }
            }
        } else {
            Log.d(TAG, "上金所：强制刷新，跳过缓存")
        }
        
        // 检查 API Key 是否已设置
        val apiKey = getApiKey()
        if (apiKey == null) {
            Log.e(TAG, "上金所：未设置 API Key")
            return@withContext GoldPriceData(0.0, null, null, null, ApiError(10001, "请先在设置中配置 API Key"))
        }

        return@withContext try {
            val url = "$API_URL?key=$apiKey&v=1"
            Log.d(TAG, "上金所：请求 URL: $url")
            
            val response = URL(url)
                .openConnection()
                .apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                .inputStream
                .bufferedReader()
                .use { it.readText() }
            
            Log.d(TAG, "上金所：响应：$response")
            parseResponse(response)?.also { data ->
                // 缓存成功获取的价格
                if (data.price > 0) {
                    prefs.edit().apply {
                        putFloat(KEY_LAST_PRICE, data.price.toFloat())
                        if (data.yesterdayPrice != null) {
                            putFloat(KEY_LAST_YESTERDAY_PRICE, data.yesterdayPrice.toFloat())
                        }
                        putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                        apply()
                    }
                    Log.d(TAG, "上金所：缓存价格：${data.price}, 昨结算：${data.yesterdayPrice}")
                    
                    // 增加 API 调用计数
                    db?.let { database ->
                        val today = DateUtils.getTodayTimestamp()
                        database.globalConfigDao().incrementApiCallCount(today)
                        Log.d(TAG, "上金所：API 调用次数 +1")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "上金所抓取失败", e)
            // 失败时尝试返回缓存
            val cachedPrice = prefs.getFloat(KEY_LAST_PRICE, 0f)
            if (cachedPrice > 0) {
                Log.d(TAG, "上金所：返回缓存价格（请求失败）：$cachedPrice")
                return@withContext GoldPriceData(cachedPrice.toDouble(), null, null, null)
            }
            null
        }
    }
    
    
    private fun parseResponse(response: String): GoldPriceData {
        try {
            val json = JSONObject(response)
            val resultCode = json.optInt("resultcode", 0)
            
            // 检查错误码
            if (resultCode != 200) {
                val reason = json.optString("reason", "未知错误")
                val errorCode = json.optInt("error_code", resultCode)
                return GoldPriceData(
                    price = 0.0,
                    yesterdayPrice = null,
                    change = null,
                    updateTime = null,
                    error = ApiError(errorCode, formatErrorMessage(errorCode, reason))
                )
            }
            
            val result = json.optJSONArray("result")
            if (result != null && result.length() > 0) {
                val varieties = result.optJSONObject(0)
                if (varieties != null) {
                    // 查找 Au99.99 或 黄金 9999
                    val keys = varieties.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val varietyObj = varieties.optJSONObject(key)
                        if (varietyObj != null) {
                            // 使用 variety 字段而不是 name 字段
                            val varietyName = varietyObj.optString("variety", key)
                            Log.d(TAG, "上金所：找到合约：$varietyName")
                            
                            // 匹配现货黄金（Au99.99）
                            if (varietyName == "Au99.99") {
                                val latestPri = varietyObj.optString("latestpri", "").replace(",", "").toDoubleOrNull()
                                val lastClear = varietyObj.optString("yespri", "").replace(",", "").toDoubleOrNull()
                                val change = varietyObj.optString("limit", "").replace("%", "").replace("NaN", "").toDoubleOrNull()
                                val time = varietyObj.optString("time", null)
                                
                                Log.d(TAG, "上金所：Au99.99 价格：$latestPri, 昨结算：$lastClear, 涨跌：$change")
                                
                                if (latestPri != null && latestPri != 0.0 && latestPri in 100.0..2000.0) {
                                    return GoldPriceData(latestPri, lastClear, change, time, null)
                                }
                            }
                        }
                    }
                    
                    // 如果没有找到 Au99.99，尝试返回第一个有价格的合约
                    val keys2 = varieties.keys()
                    while (keys2.hasNext()) {
                        val key = keys2.next()
                        val varietyObj = varieties.optJSONObject(key)
                        val price = varietyObj?.optString("latestpri")?.replace(",", "")?.toDoubleOrNull()
                        val lastClear = varietyObj?.optString("yespri")?.replace(",", "")?.toDoubleOrNull()
                        if (price != null && price != 0.0 && price in 100.0..2000.0) {
                            Log.d(TAG, "上金所：返回合约 $key 价格：$price, 昨结算：$lastClear")
                            return GoldPriceData(price, lastClear, null, null, null)
                        }
                    }
                }
            }
            
            return GoldPriceData(0.0, null, null, null, ApiError(202901, "查询不到结果（202901）"))
        } catch (e: Exception) {
            Log.e(TAG, "上金所：解析失败", e)
            return GoldPriceData(0.0, null, null, null, ApiError(10014, "系统内部异常（10014）"))
        }
    }
    
    /**
     * 格式化错误信息
     */
    private fun formatErrorMessage(errorCode: Int, reason: String): String {
        return when (errorCode) {
            10001 -> "错误的 API KEY（10001）"
            10002 -> "该 KEY 无请求权限（10002）"
            10003 -> "API KEY 已过期（10003）"
            10004 -> "错误的 OPENID（10004）"
            10005 -> "应用未审核超时，请提交认证（10005）"
            10007 -> "未知的请求源（10007）"
            10008 -> "被禁止的 IP（10008）"
            10009 -> "被禁止的 KEY（10009）"
            10011 -> "当前 IP 请求超过限制（10011）"
            10012 -> "请求次数超过限制（10012）"
            10013 -> "测试 KEY 超过请求限制（10013）"
            10014 -> "系统内部异常（10014）"
            10020 -> "接口维护中（10020）"
            10021 -> "接口已停用（10021）"
            202901 -> "查询不到结果（202901）"
            202902 -> "参数错误（202902）"
            else -> "请求失败：$reason（$errorCode）"
        }
    }
    
    /**
     * 获取数据源名称
     */
    fun getSourceName(): String = "上海黄金交易所"
}
