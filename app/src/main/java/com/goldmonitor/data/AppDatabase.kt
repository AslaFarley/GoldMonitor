package com.goldmonitor.data

import android.content.Context
import androidx.room.*
import com.goldmonitor.model.*
import java.util.Date
import androidx.sqlite.db.SupportSQLiteDatabase



/**
 * 窗口期数据访问对象
 */
@Dao
interface WindowPeriodDao {
    @Query("SELECT * FROM window_periods ORDER BY startMonth, startDay")
    suspend fun getAll(): List<WindowPeriod>
    
    @Query("SELECT * FROM window_periods WHERE id = :id")
    suspend fun getById(id: Long): WindowPeriod?
    
    @Query("SELECT * FROM window_periods WHERE isTriggered = 0 ORDER BY startMonth, startDay")
    suspend fun getNotTriggered(): List<WindowPeriod>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(windowPeriod: WindowPeriod): Long
    
    @Update
    suspend fun update(windowPeriod: WindowPeriod)
    
    @Delete
    suspend fun delete(windowPeriod: WindowPeriod)
    
    @Query("DELETE FROM window_periods WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/**
 * 全局配置数据访问对象
 */
@Dao
interface GlobalConfigDao {
    @Query("SELECT * FROM global_config WHERE id = 1")
    suspend fun getConfig(): GlobalConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: GlobalConfig)
    
    @Update
    suspend fun update(config: GlobalConfig)
    
    @Query("UPDATE global_config SET poolBalance = :balance, updatedAt = :time WHERE id = 1")
    suspend fun updatePoolBalance(balance: Int, time: Long)
    
    @Query("UPDATE global_config SET lastPrice = :price, lastRunDate = :date, updatedAt = :time WHERE id = 1")
    suspend fun updateLastPrice(price: Double, date: Long, time: Long)
    
    @Query("UPDATE global_config SET isContinuousDrop = :isDrop, continuousDropDays = :days, updatedAt = :time WHERE id = 1")
    suspend fun updateContinuousDropStatus(isDrop: Boolean, days: Int, time: Long)
    
    @Query("UPDATE global_config SET apiCallCount = apiCallCount + 1, lastCallDate = :today WHERE id = 1")
    suspend fun incrementApiCallCount(today: Long)
    
    @Query("UPDATE global_config SET apiCallCount = 0 WHERE id = 1")
    suspend fun resetApiCallCount()
}

/**
 * 金价记录数据访问对象
 */
@Dao
interface GoldPriceRecordDao {
    @Query("SELECT * FROM gold_price_records ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<GoldPriceRecord>
    
    @Query("SELECT * FROM gold_price_records WHERE source = '上海黄金交易所' ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentFromSge(limit: Int): List<GoldPriceRecord>
    
    @Query("SELECT * FROM gold_price_records WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getBetween(start: Long, end: Long): List<GoldPriceRecord>
    
    @Query("SELECT * FROM gold_price_records WHERE date = :date")
    suspend fun getByDate(date: Long): GoldPriceRecord?
    
    @Query("SELECT AVG(price) FROM gold_price_records WHERE date >= :fromDate AND date < :toDate")
    suspend fun getAveragePrice(fromDate: Long, toDate: Long): Double?
    
    @Query("SELECT price FROM gold_price_records WHERE date = (SELECT MAX(date) FROM gold_price_records WHERE date < :date)")
    suspend fun getPreviousPrice(date: Long): Double?
    
    @Query("SELECT AVG(price) FROM gold_price_records WHERE date >= :fromDate AND date < :toDate AND price > 0")
    suspend fun getMA20(fromDate: Long, toDate: Long): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: GoldPriceRecord)
    
    @Query("DELETE FROM gold_price_records WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: Long)
}

/**
 * 买入日志数据访问对象
 */
@Dao
interface BuyLogDao {
    @Query("SELECT * FROM buy_logs ORDER BY buyDate DESC")
    suspend fun getAll(): List<BuyLog>
    
    @Query("SELECT * FROM buy_logs WHERE id = :id")
    suspend fun getById(id: Long): BuyLog?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BuyLog): Long
    
    @Query("SELECT SUM(buyAmount) FROM buy_logs")
    suspend fun getTotalBuyAmount(): Double?
}

/**
 * 应用数据库
 */
@Database(
    entities = [
        WindowPeriod::class,
        GlobalConfig::class,
        GoldPriceRecord::class,
        BuyLog::class
    ],
    version = 4, // 版本 4：poolBalance 改为 Int
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun windowPeriodDao(): WindowPeriodDao
    abstract fun globalConfigDao(): GlobalConfigDao
    abstract fun goldPriceRecordDao(): GoldPriceRecordDao
    abstract fun buyLogDao(): BuyLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gold_monitor_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
