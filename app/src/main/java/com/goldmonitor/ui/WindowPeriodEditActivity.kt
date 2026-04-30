package com.goldmonitor.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.R
import com.goldmonitor.databinding.ActivityWindowPeriodEditBinding
import com.goldmonitor.model.BuyReason
import com.goldmonitor.model.WindowPeriod
import com.goldmonitor.service.MonitorService
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 窗口期编辑界面
 */
class WindowPeriodEditActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWindowPeriodEditBinding
    private val windowPeriodDao = GoldMonitorApp.database.windowPeriodDao()
    
    private var startMonth = 1
    private var startDay = 1
    private var endMonth = 1
    private var endDay = 1
    private var maPeriod = 5
    private var threshold = 2.0
    private var buyAmount = 1
    
    private var editingPeriodId: Long = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWindowPeriodEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        editingPeriodId = intent.getLongExtra("period_id", -1)
        
        setupUI()
        
        if (editingPeriodId > 0) {
            loadPeriod()
        } else {
            // 新建模式，设置默认值
            updateDateDisplay()
        }
    }
    
    private fun setupUI() {
        // 返回按钮
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // 保存按钮
        binding.btnSave.setOnClickListener {
            validateAndSave()
        }
        
        // 均线周期选择
        setupMaPeriodSpinner()
    }
    
    private fun setupMaPeriodSpinner() {
        val maPeriods = arrayOf("5 日均线", "10 日均线", "20 日均线")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, maPeriods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMaPeriod.adapter = adapter
        
        binding.spinnerMaPeriod.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                maPeriod = when (position) {
                    0 -> 5
                    1 -> 10
                    2 -> 20
                    else -> 5
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateDateDisplay() {
        binding.etStartMonth.setText(startMonth.toString())
        binding.etStartDay.setText(startDay.toString())
        binding.etEndMonth.setText(endMonth.toString())
        binding.etEndDay.setText(endDay.toString())
    }
    
    private fun loadPeriod() {
        lifecycleScope.launch {
            val period = windowPeriodDao.getById(editingPeriodId)
            if (period == null) {
                Toast.makeText(this@WindowPeriodEditActivity, "窗口期不存在", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            
            startMonth = period.startMonth
            startDay = period.startDay
            endMonth = period.endMonth
            endDay = period.endDay
            updateDateDisplay()
            
            binding.spinnerMaPeriod.setSelection(when (period.maPeriod) {
                5 -> 0
                10 -> 1
                20 -> 2
                else -> 0
            })
            
            binding.etThreshold.setText(period.triggerThreshold.toString())
            binding.etBuyAmount.setText(period.buyAmount.toString())
            
            threshold = period.triggerThreshold
            buyAmount = period.buyAmount
        }
    }
    
    private fun validateAndSave() {
        // 获取并验证月份
        val startMonthStr = binding.etStartMonth.text.toString()
        val startDayStr = binding.etStartDay.text.toString()
        val endMonthStr = binding.etEndMonth.text.toString()
        val endDayStr = binding.etEndDay.text.toString()
        
        if (startMonthStr.isBlank() || startDayStr.isBlank() || 
            endMonthStr.isBlank() || endDayStr.isBlank()) {
            Toast.makeText(this, "请填写完整的日期", Toast.LENGTH_SHORT).show()
            return
        }
        
        val newStartMonth = startMonthStr.toIntOrNull()
        val newStartDay = startDayStr.toIntOrNull()
        val newEndMonth = endMonthStr.toIntOrNull()
        val newEndDay = endDayStr.toIntOrNull()
        
        if (newStartMonth == null || newStartDay == null || 
            newEndMonth == null || newEndDay == null) {
            Toast.makeText(this, "日期必须是有效的数字", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证月份范围
        if (newStartMonth < 1 || newStartMonth > 12 || newEndMonth < 1 || newEndMonth > 12) {
            Toast.makeText(this, "月份必须在 1-12 之间", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证日期范围
        val maxStartDay = getDaysInMonth(newStartMonth)
        val maxEndDay = getDaysInMonth(newEndMonth)
        
        if (newStartDay < 1 || newStartDay > maxStartDay) {
            Toast.makeText(this, "开始日期无效，${newStartMonth}月最多有${maxStartDay}天", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (newEndDay < 1 || newEndDay > maxEndDay) {
            Toast.makeText(this, "结束日期无效，${newEndMonth}月最多有${maxEndDay}天", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证结束日期不能早于开始日期
        val startValue = newStartMonth * 100 + newStartDay
        val endValue = newEndMonth * 100 + newEndDay
        
        if (endValue < startValue) {
            Toast.makeText(this, "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 获取阈值
        val thresholdStr = binding.etThreshold.text.toString()
        if (thresholdStr.isBlank()) {
            Toast.makeText(this, "请填写触发阈值", Toast.LENGTH_SHORT).show()
            return
        }
        
        val newThreshold = thresholdStr.toDoubleOrNull()
        if (newThreshold == null || newThreshold <= 0 || newThreshold > 100) {
            Toast.makeText(this, "触发阈值必须是 0-100 之间的数字", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 获取买入克数
        val buyAmountStr = binding.etBuyAmount.text.toString()
        if (buyAmountStr.isBlank()) {
            Toast.makeText(this, "请填写买入克数", Toast.LENGTH_SHORT).show()
            return
        }
        
        val newBuyAmount = buyAmountStr.toIntOrNull()
        if (newBuyAmount == null || newBuyAmount <= 0) {
            Toast.makeText(this, "买入克数必须是正整数", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 保存
        lifecycleScope.launch {
            if (editingPeriodId > 0) {
                // 更新
                val updatedPeriod = WindowPeriod(
                    id = editingPeriodId,
                    startMonth = newStartMonth,
                    startDay = newStartDay,
                    endMonth = newEndMonth,
                    endDay = newEndDay,
                    maPeriod = maPeriod,
                    triggerThreshold = newThreshold,
                    buyAmount = newBuyAmount
                )
                windowPeriodDao.update(updatedPeriod)
                Toast.makeText(this@WindowPeriodEditActivity, "窗口期已更新", Toast.LENGTH_SHORT).show()
            } else {
                // 新建
                val newPeriod = WindowPeriod(
                    startMonth = newStartMonth,
                    startDay = newStartDay,
                    endMonth = newEndMonth,
                    endDay = newEndDay,
                    maPeriod = maPeriod,
                    triggerThreshold = newThreshold,
                    buyAmount = newBuyAmount
                )
                windowPeriodDao.insert(newPeriod)
                Toast.makeText(this@WindowPeriodEditActivity, "窗口期已创建", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
    
    /**
     * 获取某个月份的天数
     */
    private fun getDaysInMonth(month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> 29  // 闰年按 29 天算
            else -> 30
        }
    }
}
