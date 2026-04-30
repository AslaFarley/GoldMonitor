package com.goldmonitor.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.util.Calendar
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.R
import com.goldmonitor.data.AppDatabase
import com.goldmonitor.data.SgeGoldFetcher
import com.goldmonitor.databinding.ActivityMainBinding
import com.goldmonitor.model.GlobalConfig
import com.goldmonitor.model.WindowPeriod
import kotlinx.coroutines.withTimeoutOrNull
import com.goldmonitor.service.MonitorScheduler
import com.goldmonitor.service.MonitorService
import com.goldmonitor.service.MonitorResult
import com.goldmonitor.util.NotificationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主界面
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val db = GoldMonitorApp.database
    private val notificationHelper by lazy { NotificationHelper(applicationContext) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadDashboard()
    }
    
    private fun setupUI() {
        // 立即运行按钮
        binding.btnRunNow.setOnClickListener {
            runMonitorNow()
        }
        
        // 窗口期管理按钮
        binding.btnWindowPeriods.setOnClickListener {
            val intent = android.content.Intent(this, WindowPeriodListActivity::class.java)
            startActivity(intent)
        }
        
        // 买入记录按钮
        binding.btnBuyLogs.setOnClickListener {
            showBuyLogs()
        }
        
        // 调整监控池余额
        binding.btnAdjustPool.setOnClickListener {
            showAdjustPoolDialog()
        }
        
        // 设置暴跌阈值
        binding.btnSetCrashThreshold.setOnClickListener {
            showCrashThresholdDialog()
        }
        
        // 设置运行时间
        binding.btnSetRunTime.setOnClickListener {
            showRunTimeDialog()
        }
        
        // 长按"立即运行"按钮 3 秒进入测试模式
        binding.btnRunNow.setOnLongClickListener {
            showTestMenu()
            true
        }
    }
    
    private fun loadDashboard() {
        lifecycleScope.launch {
            try {
                // 一次性获取所有配置数据
                val config = db.globalConfigDao().getConfig()
                val recentPrices = db.goldPriceRecordDao().getRecentFromSge(20)
                
                // 预加载窗口期数据（避免后续查询阻塞）
                val windowPeriods = db.windowPeriodDao().getAll()
                Log.d("MainActivity", "加载了 ${windowPeriods.size} 个窗口期")
                
                // 显示配置
                if (config != null) {
                    binding.tvPoolBalance.text = "${config.poolBalance.toInt()} 克"
                    binding.tvCrashThreshold.text = "${String.format("%.1f", config.crashThreshold)}%"
                    binding.tvNextRunTime.text = String.format("%02d:%02d", config.runHour, config.runMinute)
                    
                    // API 调用次数
                    val callCount = config.apiCallCount
                    val lastCallDate = config.lastCallDate
                    val todayTimestamp = getTodayTimestamp()
                    
                    if (lastCallDate == null || lastCallDate < todayTimestamp) {
                        db.globalConfigDao().resetApiCallCount()
                        binding.tvApiCallCount.text = "📊 今日 API 调用：0 次（已清零）"
                    } else {
                        binding.tvApiCallCount.text = "📊 今日 API 调用：$callCount 次"
                    }
                } else {
                    binding.tvPoolBalance.text = "未设置"
                    binding.tvCrashThreshold.text = "未设置"
                    binding.tvNextRunTime.text = "未设置"
                    binding.tvApiCallCount.text = "📊 今日 API 调用：0 次"
                }
                
                // 检查 API 错误
                if (recentPrices.isEmpty()) {
                    binding.tvApiError.text = "⚠️ 暂无历史数据，请稍后重试"
                    binding.tvApiError.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvApiError.visibility = android.view.View.GONE
                }
                
                if (recentPrices.isNotEmpty()) {
                    val today = recentPrices[0]
                    val yesterday = if (recentPrices.size > 1) recentPrices[1] else null
                    
                    // 当前金价
                    binding.tvCurrentPrice.text = "${String.format("%.2f", today.price)} 元/克"
                    
                    // 更新时间（使用 createdAt 字段，显示实际抓取时间）
                    val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    binding.tvLastUpdateTime.text = "更新于 ${timeFormat.format(Date(today.createdAt))}"
                    
                    // 昨日金价
                    if (yesterday != null) {
                        binding.tvYesterdayPrice.text = "${String.format("%.2f", yesterday.price)} 元/克"
                        
                        val change = today.price - yesterday.price
                        val changePercent = (change / yesterday.price) * 100
                        val changeText = if (change >= 0) {
                            "↑ +${String.format("%.2f", change)} (${String.format("%.2f", changePercent)}%)"
                        } else {
                            "↓ ${String.format("%.2f", change)} (${String.format("%.2f", changePercent)}%)"
                        }
                        binding.tvPriceChange.text = changeText
                    } else {
                        binding.tvYesterdayPrice.text = "--"
                        binding.tvPriceChange.text = "--"
                    }
                    
                    // 均线计算
                    val priceList = recentPrices.map { it.price }
                    
                    // 5 日均线
                    val ma5 = calculateMA(priceList, 5)
                    if (ma5 != null) {
                        binding.tvMA5Price.text = "${String.format("%.2f", ma5)}"
                        val ma5Diff = today.price - ma5
                        val ma5DiffPercent = (ma5Diff / ma5) * 100
                        binding.tvMA5Diff.text = formatMaDiff(ma5Diff, ma5DiffPercent)
                    } else {
                        binding.tvMA5Price.text = "--"
                        binding.tvMA5Diff.text = "--"
                    }
                    
                    // 10 日均线
                    val ma10 = calculateMA(priceList, 10)
                    if (ma10 != null) {
                        binding.tvMA10Price.text = "${String.format("%.2f", ma10)}"
                        val ma10Diff = today.price - ma10
                        val ma10DiffPercent = (ma10Diff / ma10) * 100
                        binding.tvMA10Diff.text = formatMaDiff(ma10Diff, ma10DiffPercent)
                    } else {
                        binding.tvMA10Price.text = "--"
                        binding.tvMA10Diff.text = "--"
                    }
                    
                    // 20 日均线
                    val ma20 = calculateMA(priceList, 20)
                    if (ma20 != null) {
                        binding.tvMA20Price.text = "${String.format("%.2f", ma20)}"
                        val ma20Diff = today.price - ma20
                        val ma20DiffPercent = (ma20Diff / ma20) * 100
                        binding.tvMA20Diff.text = formatMaDiff(ma20Diff, ma20DiffPercent)
                    } else {
                        binding.tvMA20Price.text = "--"
                        binding.tvMA20Diff.text = "--"
                    }
                    
                } else {
                    binding.tvCurrentPrice.text = "暂无数据"
                    binding.tvLastUpdateTime.text = ""
                    binding.tvYesterdayPrice.text = "--"
                    binding.tvPriceChange.text = "--"
                    binding.tvMA5Price.text = "--"
                    binding.tvMA5Diff.text = "--"
                    binding.tvMA10Price.text = "--"
                    binding.tvMA10Diff.text = "--"
                    binding.tvMA20Price.text = "--"
                    binding.tvMA20Diff.text = "--"
                }
            } catch (e: Exception) {
                // 静默失败，不影响其他功能
                Log.e("MainActivity", "loadDashboard 失败", e)
            }
        }
    }
    
    private fun calculateMA(prices: List<Double>, days: Int): Double? {
        if (prices.isEmpty()) return null
        val take = minOf(days, prices.size)
        val sum = prices.take(take).sum()
        return sum / take
    }
    
    private fun formatMaDiff(diff: Double, diffPercent: Double): String {
        return if (diff >= 0) {
            "高于 +${String.format("%.2f", diff)} (${String.format("%.2f", diffPercent)}%)"
        } else {
            "低于 ${String.format("%.2f", diff)} (${String.format("%.2f", diffPercent)}%)"
        }
    }
    
    private fun getTodayTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun runMonitorNow() {
        Log.d("MainActivity", "=== 开始 runMonitorNow ===")
        Log.d("MainActivity", "按钮当前状态：isEnabled=${binding.btnRunNow.isEnabled}")
        
        // 防止重复点击
        if (!binding.btnRunNow.isEnabled) {
            Log.w("MainActivity", "按钮正在运行中，忽略重复点击")
            return
        }
        
        Log.d("MainActivity", "设置按钮为运行中...")
        binding.btnRunNow.isEnabled = false
        binding.btnRunNow.text = "运行中..."
        
        lifecycleScope.launch {
            Log.d("MainActivity", "协程启动，开始执行监控任务")
            var completed = false
            try {
                Log.d("MainActivity", "创建 MonitorService...")
                val monitorService = MonitorService(
                    context = applicationContext,
                    db = db,
                    notificationHelper = notificationHelper
                )
                
                Log.d("MainActivity", "调用 runMonitor()...")
                val result = withTimeoutOrNull(30000) {  // 30 秒超时
                    monitorService.runMonitor()
                }
                
                if (result == null) {
                    Log.e("MainActivity", "runMonitor() 超时")
                    completed = false
                    Toast.makeText(this@MainActivity, "运行超时", Toast.LENGTH_LONG).show()
                } else {
                    Log.d("MainActivity", "runMonitor() 返回结果：${result::class.simpleName}")
                    completed = true
                    
                    when (result) {
                        is MonitorResult.Success -> {
                            Log.d("MainActivity", "监控成功：${result.message}")
                            Toast.makeText(this@MainActivity, "监控完成", Toast.LENGTH_SHORT).show()
                        }
                        is MonitorResult.Skipped -> {
                            Log.d("MainActivity", "监控跳过：${result.reason}")
                            Toast.makeText(this@MainActivity, "已跳过：${result.reason}", Toast.LENGTH_SHORT).show()
                        }
                        is MonitorResult.Error -> {
                            Log.e("MainActivity", "监控失败：${result.message}")
                            Toast.makeText(this@MainActivity, "失败：${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                
                    Log.d("MainActivity", "准备刷新 UI...")
                    loadDashboard()
                    Log.d("MainActivity", "UI 刷新完成")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.w("MainActivity", "运行被取消：${e.message}", e)
                Toast.makeText(this@MainActivity, "运行被取消（切换了页面）", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "运行失败：${e.message}", e)
                Toast.makeText(this@MainActivity, "运行失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Log.d("MainActivity", "进入 finally 块，completed=$completed")
                if (!completed) {
                    Log.w("MainActivity", "运行未完成，恢复按钮状态")
                }
                binding.btnRunNow.isEnabled = true
                binding.btnRunNow.text = "▶️ 立即运行一次"
                Log.d("MainActivity", "按钮状态已恢复")
                Log.d("MainActivity", "=== runMonitorNow 结束 ===")
            }
        }
    }
    
    private fun showBuyLogs() {
        lifecycleScope.launch {
            val logs = db.buyLogDao().getAll()
            
            if (logs.isEmpty()) {
                Toast.makeText(this@MainActivity, "暂无买入记录", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val logText = logs.joinToString("\n\n") { log ->
                buildString {
                    appendLine("📅 ${dateFormat.format(Date(log.buyDate))}")
                    appendLine("   买入：${String.format("%.1f", log.buyAmount)} 克")
                    appendLine("   金价：${String.format("%.2f", log.price)} 元/克")
                    appendLine("   原因：${log.reason.description}")
                    if (log.note != null) {
                        appendLine("   备注：${log.note}")
                    }
                }
            }
            
            AlertDialog.Builder(this@MainActivity)
                .setTitle("买入记录")
                .setMessage(logText)
                .setPositiveButton("关闭", null)
                .show()
        }
    }
    
    private fun showAdjustPoolDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("调整监控池余额")
        
        val input = android.widget.EditText(this)
        input.hint = "输入新的池子克数（整数）"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        
        lifecycleScope.launch {
            val config = db.globalConfigDao().getConfig()
            if (config != null) {
                // 显示为整数（去掉小数部分）
                input.setText(config.poolBalance.toInt().toString())
            }
        }
        
        builder.setView(input)
        
        builder.setPositiveButton("确认") { _, _ ->
            val newBalance = input.text.toString().toIntOrNull()
            if (newBalance != null && newBalance >= 0) {
                lifecycleScope.launch {
                    db.globalConfigDao().updatePoolBalance(newBalance, System.currentTimeMillis())
                    Toast.makeText(this@MainActivity, "池子余额已更新为 $newBalance 克", Toast.LENGTH_SHORT).show()
                    loadDashboard()
                }
            } else {
                Toast.makeText(this@MainActivity, "请输入有效整数", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }
    
    private fun showCrashThresholdDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("设置暴跌监控阈值")
        
        val input = android.widget.EditText(this)
        input.hint = "输入跌幅百分比（如 3.0）"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        lifecycleScope.launch {
            val config = db.globalConfigDao().getConfig()
            if (config != null) {
                input.setText(String.format("%.1f", config.crashThreshold))
            }
        }
        
        builder.setView(input)
        
        builder.setMessage("当单日金价跌幅超过此百分比时，触发暴跌监控")
        
        builder.setPositiveButton("确认") { _, _ ->
            val threshold = input.text.toString().toDoubleOrNull()
            if (threshold != null && threshold > 0 && threshold <= 100) {
                lifecycleScope.launch {
                    val config = db.globalConfigDao().getConfig()
                    if (config != null) {
                        db.globalConfigDao().update(
                            config.copy(
                                crashThreshold = threshold,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        Toast.makeText(this@MainActivity, "暴跌阈值已更新为 ${String.format("%.1f", threshold)}%", Toast.LENGTH_SHORT).show()
                        loadDashboard()
                    }
                }
            } else {
                Toast.makeText(this@MainActivity, "请输入有效百分比（0-100）", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }
    
    private fun showRunTimeDialog() {
        lifecycleScope.launch {
            val config = db.globalConfigDao().getConfig()
            val hour = config?.runHour ?: 19
            val minute = config?.runMinute ?: 0
            
            TimePickerDialog(
                this@MainActivity,
                { _, selectedHour, selectedMinute ->
                    lifecycleScope.launch {
                        val updatedConfig = config?.copy(
                            runHour = selectedHour,
                            runMinute = selectedMinute,
                            updatedAt = System.currentTimeMillis()
                        )
                        if (updatedConfig != null) {
                            db.globalConfigDao().update(updatedConfig)
                            
                            // 重新调度定时任务
                            MonitorScheduler.scheduleDaily(this@MainActivity, selectedHour, selectedMinute)
                            
                            Toast.makeText(this@MainActivity, "运行时间已更新为 ${String.format("%02d:%02d", selectedHour, selectedMinute)}", Toast.LENGTH_SHORT).show()
                            loadDashboard()
                        }
                    }
                },
                hour,
                minute,
                true
            ).show()
        }
    }
    
    private fun showTestMenu() {
        val items = arrayOf(
            "测试暴跌监控（插入测试数据）",
            "测试窗口期（插入测试窗口期）",
            "重置所有数据",
            "取消"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🧪 测试模式")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> setupCrashTest()
                    1 -> setupWindowPeriodTest()
                    2 -> resetAllData()
                }
            }
            .show()
    }
    
    private fun setupCrashTest() {
        lifecycleScope.launch {
            // 设置昨日金价为 1030（今日约 1021，跌幅约 0.9%）
            db.globalConfigDao().updateLastPrice(1030.0, getTodayTimestamp() - 86400000, System.currentTimeMillis())
            
            // 设置暴跌阈值为 0.5%（很容易触发）
            val config = db.globalConfigDao().getConfig()
            if (config != null) {
                db.globalConfigDao().update(config.copy(crashThreshold = 0.5))
            }
            
            // 设置监控池余额
            db.globalConfigDao().updatePoolBalance(10, System.currentTimeMillis())
            
            Toast.makeText(this@MainActivity, "✅ 暴跌监控测试数据已准备：\n昨日金价：1030 元\n今日金价：约 1021 元\n跌幅阈值：0.5%\n监控池：10 克", Toast.LENGTH_LONG).show()
            loadDashboard()
        }
    }
    
    private fun setupWindowPeriodTest() {
        lifecycleScope.launch {
            // 插入一个测试窗口期（今天就在窗口期内）
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
            
            val testPeriod = WindowPeriod(
                startMonth = currentMonth,
                startDay = currentDay,
                endMonth = currentMonth,
                endDay = (currentDay + 7).coerceAtMost(31),
                maPeriod = 5,
                triggerThreshold = 0.1, // 0.1% 很容易触发
                buyAmount = 3
            )
            
            db.windowPeriodDao().insert(testPeriod)
            
            // 设置监控池
            db.globalConfigDao().updatePoolBalance(10, System.currentTimeMillis())
            
            Toast.makeText(this@MainActivity, "✅ 窗口期测试数据已准备：\n窗口期：${currentMonth}月${currentDay}日 ~ ${currentMonth}月${currentDay + 7}日\n均线：5 日\n触发阈值：0.1%\n买入：3 克", Toast.LENGTH_LONG).show()
            loadDashboard()
        }
    }
    
    private fun resetAllData() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 确认重置")
            .setMessage("这将清空所有配置和记录，但保留金价数据。确定吗？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    db.globalConfigDao().insert(
                        GlobalConfig(
                            poolBalance = 0,
                            crashThreshold = 3.0,
                            runHour = 19,
                            runMinute = 0
                        )
                    )
                    Toast.makeText(this@MainActivity, "已重置配置", Toast.LENGTH_SHORT).show()
                    loadDashboard()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onResume() {
        super.onResume()
        loadDashboard()
    }
}
