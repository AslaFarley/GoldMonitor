package com.goldmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.R
import com.goldmonitor.databinding.ActivityWindowPeriodListBinding
import com.goldmonitor.model.WindowPeriod
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 窗口期列表界面
 */
class WindowPeriodListActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "WindowPeriodList"
    }
    
    private lateinit var binding: ActivityWindowPeriodListBinding
    private lateinit var adapter: WindowPeriodAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityWindowPeriodListBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupUI()
            loadWindowPeriods()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 失败", e)
            Toast.makeText(this, "初始化失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun setupUI() {
        try {
            binding.toolbar.setNavigationOnClickListener {
                finish()
            }
            
            binding.fabAdd.setOnClickListener {
                try {
                    val intent = Intent(this, WindowPeriodEditActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "打开编辑页面失败", e)
                    Toast.makeText(this, "无法打开编辑页面", Toast.LENGTH_SHORT).show()
                }
            }
            
            adapter = WindowPeriodAdapter(
                onItemClick = { period ->
                    try {
                        val intent = Intent(this, WindowPeriodEditActivity::class.java)
                        intent.putExtra("window_period_id", period.id)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "打开编辑页面失败", e)
                        Toast.makeText(this, "无法打开编辑页面", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteClick = { period ->
                    showDeleteDialog(period)
                }
            )
            
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e(TAG, "setupUI 失败", e)
            throw e
        }
    }
    
    private fun loadWindowPeriods() {
        try {
            lifecycleScope.launch {
                try {
                    val db = GoldMonitorApp.database
                    val periods = db.windowPeriodDao().getNotTriggered()
                    adapter.submitList(periods)
                    Log.d(TAG, "加载了 ${periods.size} 个窗口期")
                } catch (e: Exception) {
                    Log.e(TAG, "加载窗口期失败", e)
                    Toast.makeText(this@WindowPeriodListActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadWindowPeriods 失败", e)
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteDialog(period: WindowPeriod) {
        try {
            AlertDialog.Builder(this)
                .setTitle("删除窗口期")
                .setMessage("确定要删除这个窗口期吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val db = GoldMonitorApp.database
                            db.windowPeriodDao().delete(period)
                            loadWindowPeriods()
                            Toast.makeText(this@WindowPeriodListActivity, "已删除", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "删除失败", e)
                            Toast.makeText(this@WindowPeriodListActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "showDeleteDialog 失败", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadWindowPeriods()
    }
}

/**
 * 窗口期列表适配器
 */
class WindowPeriodAdapter(
    private val onItemClick: (WindowPeriod) -> Unit,
    private val onDeleteClick: (WindowPeriod) -> Unit
) : RecyclerView.Adapter<WindowPeriodAdapter.ViewHolder>() {
    
    private var periods: List<WindowPeriod> = emptyList()
    
    fun submitList(newPeriods: List<WindowPeriod>) {
        periods = newPeriods
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_window_period, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(periods[position])
    }
    
    override fun getItemCount() = periods.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateRange: TextView = itemView.findViewById(R.id.tvDateRange)
        private val tvConfig: TextView = itemView.findViewById(R.id.tvConfig)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        
        fun bind(period: WindowPeriod) {
            try {
                tvDateRange.text = period.getDateRangeString()
                
                tvConfig.text = "${period.maPeriod}日均线 | 低于${period.triggerThreshold}%买${period.buyAmount}克"
                
                tvStatus.text = if (period.isTriggered) {
                    "✅ 已触发"
                } else if (period.isInWindow()) {
                    "🟡 进行中"
                } else if (period.isExpired()) {
                    "⚪ 已过期"
                } else {
                    "⚪ 未开始"
                }
                
                itemView.setOnClickListener { onItemClick(period) }
                btnDelete.setOnClickListener { onDeleteClick(period) }
            } catch (e: Exception) {
                Log.e("WindowPeriodAdapter", "bind 失败", e)
            }
        }
    }
}
