package com.goldmonitor.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.goldmonitor.GoldMonitorApp
import com.goldmonitor.R
import com.goldmonitor.databinding.ActivityApiCallLogBinding
import com.goldmonitor.databinding.ItemApiCallLogBinding
import com.goldmonitor.model.ApiCallLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ApiCallLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApiCallLogBinding
    private val db = GoldMonitorApp.database
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApiCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadLogs()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvLogs.layoutManager = LinearLayoutManager(this)
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val todayStart = getTodayTimestamp()
            val logs = db.apiCallLogDao().getTodayLogs(todayStart)

            binding.tvTotalCalls.text = "今日调用总次数：${logs.size}"
            binding.rvLogs.adapter = LogAdapter(logs)
        }
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    inner class LogAdapter(private val logs: List<ApiCallLog>) :
        RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemApiCallLogBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemApiCallLogBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            with(holder.binding) {
                tvType.text = if (log.isManual) "手动" else "自动"
                tvType.setBackgroundResource(
                    if (log.isManual) R.drawable.bg_tag_manual else R.drawable.bg_tag_light
                )
                tvTime.text = dateFormat.format(Date(log.callTime))
                tvResult.text = log.result
                tvPrice.text = log.price?.let { String.format("%.2f", it) } ?: "--"
            }
        }

        override fun getItemCount() = logs.size
    }
}
