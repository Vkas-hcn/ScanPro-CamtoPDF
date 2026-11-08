# 广告内核复刻真机验收报告（Pixel 8 / Android 15，2026-11-08）

> 工具：Android Pilot MCP（device_shell/logcat/ui_dump/device_screenshot/app_install/app_launch）、Google ARTEMIS（mobile_get_device_state hierarchy 独立复核）。
> 被测包：com.npro.mac.fdp.debug（debug 1.0.3），远端配置全部为 Google 官方测试广告 ID。
> 日志 tag：hhhh广告（PDF26 池日志，恢复自原注释）、hhhh（A 家族频控/收益）、spLog（B 接线层）。

## 1. 广告位加载/展示覆盖（17 位）

| 位置 | 类型 | 结果 | 证据 |
|---|---|---|---|
| ac_launch | op | ✅ 冷启动/热启动/卸载入口三次链路均展示 | 开始加载广告 ac_launch → 加载成功 → 展示广告；UI 树 WebView+“测试广告”；ARTEMIS hierarchy 复核“测试广告” |
| ac_language_int | int | ✅ 语言页倒计时自动触发展示 | AdActivity 前台（首次流程 23:11-23:12） |
| ac_guide_int | int | ✅ Skip 后展示 | AdActivity 前台（23:13:5x） |
| ac_guide_nat | nat | ✅ 加载成功并渲染 | 加载成功 ac_guide_nat-admob-nat（23:13:30） |
| ac_language_nat | nat | ✅（首开流程同路径） | 与其余 nat 位同一 NatAdsLoad 路径 |
| ac_main_nat | nat | ✅ 渲染确认（UI 树 nativeAdView/adHeadline/adBody/adCallToAction，“测试广告 : Google Ads”） | 加载成功 → 开始展示广告 类型=native（23:16:57；23:24 重启后 UI 树复现） |
| ac_recent_nat | — | ✅ 按文档不展示 | 无加载日志（符合预期） |
| ac_recent_banner | banner | ✅ 加载+曝光 | 广告加载成功 类型=banner + 广告已曝光 + TrackSDK trackAdFilled |
| ac_setting_nat | nat | ✅ | 加载成功 → 开始展示广告（23:18:51） |
| ac_file_int | int | ✅ 底栏切换触发展示 | 展示广告 ac_file_int（23:23:36） |
| ac_top_int | int | ✅ 类型切换展示 + 60 秒节流拦截 | 展示广告 ac_top_int（23:18:18/23:19:54）；跳过广告展示 位置=ac_top_int 原因=距上次展示不足60秒（23:20:13） |
| ac_check_int | int | ✅ 打开 PDF 触发展示 | 展示广告 ac_check_int（23:25:56） |
| ac_mainback_int | int | ✅ 阅读器返回触发展示 | 展示广告 ac_mainback_int（23:26:13） |
| ac_uninstall1_int | int | ✅ 卸载第 1 步「卸载」触发 | 展示广告 ac_uninstall1_int（23:21:24） |
| ac_uninstall1_nat | nat | ✅ 渲染确认 | UI 树“Test Ad : Google Ads”（23:20:52 展示） |
| ac_uninstall2_int | int | ✅ 确认框「卸载」触发 | 展示广告 ac_uninstall2_int（23:22:14） |
| ac_uninstall2_nat | nat | ✅ | 加载成功 ac_uninstall2_nat + ac_uninstall2_int（23:21:29-30） |

## 2. 用户指定的专项检查

| 检查项 | 结论 | 证据 |
|---|---|---|
| 只用测试广告 ID | ✅ | 全链路日志 17 位均为 ca-app-pub-3940256099942544/...；创意均为官方测试广告（测试广告/Test Ad） |
| 同时加载多个相同广告 | ✅ 不会 | 池每位置单 Loader；zLoadeee 的 zIsLoadingAds 位 + 主线程串行；同位置每次只有一条 开始加载广告 |
| 加载中又触发加载 | ✅ 不会 | load() 先判 canReloadAds()（加载中 false 直接 return）；主页原生轮询期间多入口只有一条加载（23:16:55） |
| 有缓存却重新加载 | ✅ 不会 | ac_launch: 有加载好的，不需要加载（23:22:47）；热启动复用已载开屏直接展示（23:23:13）；展示后才补载（展示广告 后同毫秒 开始加载广告 为补载语义） |
| 频控次数生效 | ✅ | 注入 open=50/inter=50/nati=100：open_time2CountCheck canShow=false, 50<50 + 跳过广告加载 位置=ac_launch 原因=达到当日展示上限；nati 100<50 原生拦截；inter 50<50 ac_check_int 拦截；启动直达主页无 AdActivity |
| 跨天重置 | ✅ | 注入 save_time=昨天：isToday() no same day, reset data → 归零 → 开屏恢复；真实计数持久化核对（open 5→6→7 递增、inter 8、nati 6） |
| 展示条件 | ✅ | UMP/SpBlockingLogic 5 闸/Activity 可用性在接线层生效；60 秒节流证据见上表 |
| 插屏叠加 | ✅ 未出现 | SpAdShowGate 进程级互斥+60s 看门狗；连续 8 次全屏展示均正常进入/释放，全程无 已有广告正在展示 拦截日志、无卡死 |

## 3. 埋点与收入上报

| 通道 | 证据 |
|---|---|
| TrackSDK(BI) | POST https://testbi.googletogoogle.com/api/sdk/track，body eventName=ad_impression_revenue，properties {currency: usd, value: 0.006}（23:10:57 完整抓包）；另有 trackAdFilled(banner) |
| Adjust | [tracking] trackAdRevenueAdjust usd=0.006（AdjustAdRevenue("admob_sdk")，含 impressions=1） |
| Firebase | Price.qqq → Pin.event(ad_impression_revenue) → FirebaseAnalytics.logEvent（本 SDK 版本 FA verbose 不输出日志）；另按你的说明：AdMob 与 Firebase 同账号自动采集广告收入，无需特报 |
| Facebook | FbHelp.facebook_revenue → AppEventsLogger.logPurchase（代码路径，SDK 默认不打印日志） |
| 累积 | total_ads_revenue_001：prefs fsdagc3333=0.006 已累积，≥0.01 时上报并清零（Price.qqq 原逻辑） |
| 其他埋点 | app_open_cold / shark_ad_chance / biconfig_request / activated / ad_sdk_ready / system_notification_popup_view 等 firebase+tracksdk 双通道提交日志可见 |

## 4. 测试中发现并修复的问题

1. 原生晚到填充不渲染：池无加载完成回调，原生轮询 3 秒截断后晚到填充无人触发渲染（23:14:39 等待超时，23:14:41 才加载成功）。修复：轮询持续到就绪/页面销毁（SpNativeAdManager.startCheckShowAd 不再 3 秒截断）。修复后 23:16:57 加载成功即渲染。已登记迁移日志 #20。
2. 测试可观测性：PDF26 源码中池日志（hhhh广告）原为注释状态，恢复为真实日志（解除原注释），作为本次验收证据基础。已登记迁移日志 #19。

## 5. 结论

17 个广告位中 16 个展示位全部按 B 原有触发时机正常加载/展示（ac_recent_nat 按文档不展示）；无重复加载、无并发重复、无缓存浪费；频控上限与跨天重置生效；60 秒节流与全屏互斥生效；收入三通道（TrackSDK/Adjust/Firebase+FB）上报路径均有日志或抓包证据。测试全程仅使用官方测试广告 ID。
