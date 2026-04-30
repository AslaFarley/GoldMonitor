# 黄金监控助手 (Gold Monitor)

一个基于聚合数据 API 的黄金价格监控应用，支持窗口期策略和暴跌监控。

## 功能特性

- 📊 **实时金价** - 从上金所获取实时黄金价格
- 📈 **均线系统** - 5 日/10 日/20 日均线计算
- 📅 **窗口期策略** - 在设定的窗口期内，金价低于均线 X% 时自动买入
- 📉 **暴跌监控** - 连续下跌 N 天后反弹时买入
- ⏰ **定时运行** - 每日指定时间自动执行监控任务
- 📱 **通知推送** - 买入时发送通知提醒

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM
- **数据库**: Room
- **后台任务**: AlarmManager + Foreground Service
- **API**: 聚合数据（上金所黄金价格）

## 构建说明

### 前提条件

- Android Studio Arctic Fox 或更高版本
- JDK 17
- Android SDK 34

### 配置 API Key

1. 在 [聚合数据](https://www.juhe.cn/) 注册账号并申请黄金 API
2. 复制 API Key
3. 修改 `app/build.gradle.kts`：

```kotlin
android {
    defaultConfig {
        buildConfigField("String", "JUHE_API_KEY", "\"你的 API_KEY\"")
    }
}
```

### 编译项目

```bash
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 使用说明

### 首次安装

应用首次启动会自动初始化：
- 监控池余额：3 克
- 运行时间：14:30
- 5 个默认窗口期

### 监控策略

#### 窗口期监控

在设定的窗口期内：
- 如果某天金价 < 5 日均线 × (1 - 阈值%)，买入 n 克（不进入监控池）
- 如果窗口期结束仍未触发，最后一天买入 1 克（剩余 n-1 克进入监控池）

#### 暴跌监控

- 连续下跌 N 天后，第 N+1 天反弹时，从监控池买入 1 克

### 手动运行

首页点击"立即运行一次"可手动触发监控任务。

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 获取金价数据 |
| POST_NOTIFICATIONS | 发送买入通知 |
| FOREGROUND_SERVICE | 后台执行监控任务 |
| WAKE_LOCK | 唤醒设备执行任务 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |
| SCHEDULE_EXACT_ALARM | 精确时间触发 |

## 项目结构

```
app/src/main/java/com/goldmonitor/
├── data/                  # 数据层
│   ├── AppDatabase.kt    # Room 数据库
│   └── GoldPriceFetcher.kt # API 抓取
├── model/                 # 数据模型
│   ├── GlobalConfig.kt
│   ├── WindowPeriod.kt
│   ├── GoldPriceRecord.kt
│   └── BuyLog.kt
├── service/               # 服务层
│   ├── MonitorService.kt          # 监控逻辑
│   ├── MonitorForegroundService.kt # 前台服务
│   └── MonitorWorker.kt           # WorkManager（备用）
├── ui/                    # UI 层
│   ├── MainActivity.kt
│   ├── WindowPeriodListActivity.kt
│   └── WindowPeriodEditActivity.kt
├── receiver/              # 广播接收器
│   ├── AlarmReceiver.kt   # 闹钟触发
│   └── BootReceiver.kt    # 开机自启
├── util/                  # 工具类
│   ├── AlarmScheduler.kt      # 闹钟调度
│   ├── NotificationHelper.kt  # 通知助手
│   └── ...
└── GoldMonitorApp.kt      # Application
```

## 注意事项

1. **API 限制**: 聚合数据免费版有每日调用次数限制
2. **后台存活**: 建议将应用加入电池优化白名单
3. **数据备份**: 卸载应用会清空所有数据，请定期备份

## 许可证

MIT License

## 免责声明

本应用仅供学习参考，不构成投资建议。黄金投资有风险，入市需谨慎。
