# `pdfcam`（`com.scanpro.cam.pdf`）移植代码说明

> 对象：`project/pdfcam`（包名 `com.scanpro.cam.pdf`，线上包，**只写、不编译、不运行**）。
> 内容：把竞品 ApolloPDF 1.0.1 的「广告编排 / 通知拉活 / 进程保活 / 卸载挽留」四块逻辑移植进来，变现口径仍走我们自己的账号和配置。
> 出处纪律：每条实现都标了它在 ApolloPDF 反编译里的对应类。反编译产物在 `data/apks/apollo/`，分析报告在 [ApolloPDF 1.0.1 反编译分析报告.md](</Users/blackc/Developer/flux/document/调研与竞品/逆向拆解/ApolloPDF 1.0.1 反编译分析报告.md>)。
> 本文所有「现状」描述，都是按仓库里的代码静态读出来的，未在设备上运行验证过。

---

## 0. 铁则（先看这条，再往下）

1. **本仓库是线上包，本地不编译、不运行、不打包。** 不跑 `./gradlew` 的任何任务。需要「编一下才知道」的事，停下来提出来，由 Black 安排。
2. **不许自造广告 ID、token、域名、包名。** 代码里广告单元一律空、配置接口一律空串占位，等中台下发和接入文档。
3. **改动只加不移。** 已过审的页面、导航、包名、商店元数据不动。

---

## 1. 总览：移植了什么、没移植什么

| 模块 | 包 | 内容 | 状态 |
|---|---|---|---|
| 广告编排 | `ads/`、`ads/manager/` | 17 个广告位、位置→单元 fallback 链、缓存 TTL、500ms 遮罩、原生关闭键误触、UMP、收入上报 | 代码全在；16 个位已接到页面，`ac_recent_nat` 反编译无调用（见 §5.8） |
| 配置下发 | `config/` | `api/dispatch` 请求 / 缓存 / 解析（key 与 Apollo 远端一致） | 代码全在；debug 地址和 8 个混淆 Key 已填，**未联调**；release Base URL 仍空 |
| 通知拉活 | `notice/` | 7 个触发场景、3 类通知、两套频控、重发循环、文案与 ID 轮换、3 个广播接收器 | 代码全在；受「远端配置未到」影响，见 §11 |
| 进程保活 | `keepalive/` | 前台服务 + JobScheduler 兜底 + 空 binder 服务 + 开机接收器 | 代码全在 |
| 卸载挽留 | `leave/` | 桌面快捷方式 → 中转页 → 两步挽留页 + 插屏/原生 | 代码全在，入口**已开** |
| 埋点 | `analytics/` | Firebase Analytics，事件名照抄 Apollo | 已接 |

**明确不移植**（按 Black 要求，Apollo 有、我们不要）：

- 买量 / 用户 / 设备维度的判据：`ReferFilter`、`UserFilter`、`DeviceFilter`、`AdFilterKt`（`SpBlockingLogic` 里对应的中间层已掐掉，只留「总闸 + 兜底放行」的首尾，见 §5.2）。
- Firebase Remote Config 拉配置：换成我们自己的中台配置（`config/SpConfigStore`）。
- Apollo 用反射 + AES 解类名字符串的反分析写法：行为照抄，写法不抄。

---

## 2. 代码结构

路径根：`project/pdfcam/app/src/main/java/com/scanpro/cam/pdf/`

```
ads/                      广告编排
  SpAdPosition.kt           17 个位置的 key（常量）
  SpAdInfo.kt               单条位置配置（ad_unit_id / ad_paltfrom / ad_type / ad_timelimit）
  SpAdConfig.kt             位置 → 单元列表
  SpNatConfig.kt            原生关闭键行为（switch_open）
  SpAdBean.kt               缓存条目（ad 对象 + createTime + sessionId）
  SpAdCache.kt              三类缓存（各留 1 条 + TTL 校验）
  SpBlockingLogic.kt        广告/通知总闸 + 当日展示上限
  SpAdsHub.kt               总入口：初始化、配置装配、按 Activity 造 manager
  SpAdMobInit.kt            MobileAds 初始化
  SpAdSession.kt            sessionId、广告点击一次性标记、前后台
  SpAdIncomeReport.kt       展示收入上报
  SpAdDevFlags.kt           测试单元开关
  SpUmpGate.kt              EEA/UK 同意表单（UMP）
  SpWebWarmup.kt            WebView 预热（解 App Open 拿不到 JS 引擎的问题）
  SpFullScreenMask.kt       全屏广告前的 500ms 遮罩
  SpPageAdTriggers.kt       首页/文件/返回插屏的共用触发（60 秒节流）
  manager/
    SpFullScreenAdManager.kt  开屏 + 插屏
    SpNativeAdManager.kt      原生
    SpBannerAdManager.kt      Banner
    SpNativeAdStyle.kt        原生版式枚举（big / small）

config/                   中台配置下发（`POST {baseUrl}api/dispatch`，对齐 EfileScan）
  SpConfigWire.kt           从 `sp_dispatch_config.xml` 读地址和 8 个混淆 Key，校验不通过则跳过
  SpConfigRemoteSource.kt   POST + 公共头 + `{code,msg,data}` 解包
  SpConfigRepository.kt     3 次 × 20s 重试，远端 → 缓存 → Unavailable
  SpConfigStore.kt          data 按块解析（广告 / 通知 / 5 个总闸）
  SpConfigBootstrapper.kt   缓存优先、3 小时节流、后台刷新、cloak、`configApplied`
  SpConfigIdentity.kt       请求字段组装（version 去后缀、languageTag、本机 UUID、Referrer 缓存）
  SpPlayInstallReferrerProvider.kt  Play Install Referrer，5s 超时，取到后落盘

notice/                   通知拉活
  SpNotifyModels.kt         场景枚举（7 个）/ 跳转类型 / 通知文案项 / intent extras 名
  SpNotifyConfig.kt         5 个配置数据类（new_config / acpop_config / pop_refresh / media_config / win_pop_config）
  SpNotifyCatalog.kt        文案池（3×8 条）+ 通知 ID 池（每场景 3 个）
  SpNotifyCreator.kt        三类通知的构造与渠道
  SpSendManager.kt          发通知 + 重发循环 + media2 节流
  SpTriggerManager.kt       7 个入口 + 分流 + 闹钟排程
  SpNotifyFilter.kt         两套频控（老/新）
  SpNotifySupport.kt        配置读取、权限判定、亮屏、小工具
  SpNoticeReceivers.kt      SpPhoneLockReceiver / SpKeyReceiver / SpAlarmReceiver
  SpPhoneStateManager.kt    锁屏状态快照 + 解锁后 1.5s 发提醒
  SpNoticeLifecycle.kt      Activity 生命周期 → no_page_exist / ad_click

keepalive/                进程保活
  SpKeepAliveServices.kt    SpApoForegroundService / SpAssistanceLaunchJobService / SpYoutubeService
  SpForegroundServiceLauncher.kt  两条拉起路径
  SpPersistentNotifyManager.kt    常驻通知
  SpPhoneBootReceiver.kt    开机 / 覆盖安装

leave/                    卸载挽留
  SpLeaveEntry.kt           入口判定 + 总开关
  SpLeaveEntryActivity.kt   快捷方式中转页
  SpUnInstall01Activity.kt  第 1 步
  SpUnInstall02Activity.kt  第 2 步 + 卸载确认框

guide/                    首次语言页 + 引导页
  SpFirstLaunch.kt          `IS_FIRST_OPEN` 标记
  SpGuideLanguageActivity.kt  语言页（`ac_language_nat` / `ac_language_int`）
  SpGuideInfoActivity.kt    三页引导（`ac_guide_nat` / `ac_guide_int`）

analytics/                SpTracker.kt（事件名）/ SpSceneCode.kt（场景码）
boot/                     SpBootActivity.kt（冷启动，等价 Apollo `SplashActivity`）
```

---

## 3. 启动接线

`CamPdfApp.onCreate`（[CamPdfApp.kt](/Users/blackc/Developer/flux/project/pdfcam/app/src/main/java/com/scanpro/cam/pdf/CamPdfApp.kt)）按顺序做 5 件事：

| 顺序 | 动作 | 说明 |
|---|---|---|
| 1 | `SpLocale.resetCompatLocalesIfNeeded` | 语言兼容 |
| 2 | `ensureWorkManager()` | 手动初始化 WorkManager（去掉了 `androidx.startup` provider，避免和 GMA 撞崩溃） |
| 3 | `SpTracker.log(APP_COLD_BOOT)` | — |
| 4 | `SpAdsHub.get(this, scope).initialize()` | 起广告与配置 |
| 5 | `startKeepAliveAndNotice()` | 起通知与保活 |

第 4 步内部（`SpAdsHub.initialize`）：装 Activity 生命周期回调 → `MobileAds.initialize` → `SpConfigBootstrapper.start()`。**前两个用 `AtomicBoolean` 保证只跑一次。**

第 5 步逐项：`SpTriggerManager.startWhileTask` → `startAlarmTask` → `SpForegroundServiceLauncher.startAppCreateTask` → `SpKeyReceiver.register` → `SpNoticeLifecycle.register`。

### 3.1 冷启动 `SpBootActivity`

等价 Apollo `SplashActivity`，顺序是：

1. **`!isTaskRoot` 直接 `finish()`** —— 这是原逻辑，也是「后台点桌面快捷方式没反应」的根因，所以才有 `leave/SpLeaveEntryActivity` 中转层（§8）。
2. 通知入口处理（`handleNotificationEntry`）：按 `from_type` 报点击埋点、撤掉媒体式提醒、停掉重发循环，并把 `from_type` 映射成开屏场景码。
3. 报一次 `Shark_ad_chance`（带 `scene` / `type`）。
4. `checkNotifyPermission()` —— **这一步的真正作用是「开屏计时什么时候开始」**，不是进主界面前补授权。
5. 授权框答完（或无需询问）→ 计时开始 → 等 `minDelay` 后取 `ac_launch` 缓存展示；没缓存或超时走 `nextPage`。
6. `nextPage`（Apollo 同序）：卸载入口 → `SpUnInstall01Activity`；首次打开 → `SpGuideLanguageActivity`；否则把 `jump_type` 转交给 `CamShellActivity`。

`from_type` → 场景码映射（Apollo `SplashActivity.fromTypeToSceneCode`）：

| `from_type` | 场景码 | 点击埋点 |
|---|---|---|
| `sys_notify` | `ac_launch_notification` | `system_notification_click`（带 `type`） |
| `media_notify` | `ac_launch_media` | `media_notification_click`（带 `type`）+ 撤掉该通知 |
| `persistent_notify` | `ac_launch_bar` | `always_notification_click` |
| `FloatingWindow` | `ac_launch_popup` | （悬浮窗那套未移植，仅保留映射） |
| `delete_app` | `ac_launch_uninstall` | — |
| 其他 / 无 | `ac_launch_open` | — |

> `media_notify_2` 那一路（`channel_ado_pdf_media_two`）**既不上报点击、也不撤通知**。这是 Apollo 的原样，不是漏写。

---

## 4. 配置下发（中台 `api/dispatch`）

调度对齐 EfileScan《买量配置接口(api-dispatch)接入文档》：缓存优先、3 小时节流（失败也写 attempt 时间）、一轮 3 次 × 20s、`{code,msg,data}` 包装。`data` 里放本项目的 Apollo 格式 JSON（对象或 JSON 字符串都认）。

### 4.1 链路

```
SpAdsHub.initialize()
  └─ SpConfigBootstrapper.start()
       ├─ loadAndApplyConfig()          // Mutex：有缓存先 dispatch，满 3 小时再拉远端
       ├─ reportCloakIfNeeded()         // POST api/cloak，安装周期内成功一次即止
       └─ monitorRemoteConfig()         // configApplied 之后每 3 小时刷新
            └─ SpConfigRepository.load()
                 └─ SpConfigRemoteSource.fetch()  POST {baseUrl}api/dispatch
                      成功（HTTP 2xx 且 code∈{0,200} 且 data 非空）→ 写缓存 → dispatch(Available)
                      失败 → 回落缓存；没缓存 → dispatch(Unavailable)
  └─ dispatch
       Available   → applyPayload：7 组业务配置 + 5 个总闸 → 清广告缓存 → 预加载 ac_launch
                     remotePayloadReady = true
       Unavailable → adConfig 置空（关广告），remotePayloadReady 保持 false
       无论成败都把 configApplied 置 true
```

**远端请求前**（`buildDispatchRequest`，对齐 EfileScan）：Referrer / Distinct ID / GAID **并行**取齐后才 `POST`。Referrer 流程见下；Distinct ID 为空则不发 dispatch。有配置缓存时先立刻生效，不堵在 Referrer 上；只有真正打接口时才等。

**Install Referrer**（`SpPlayInstallReferrerProvider`，对齐 `PlayInstallReferrerProvider`）：

```
resolveInstallReferrer()
  ├─ SharedPreferences 已有非空值 → 直接用，不再连 Play
  └─ 否则 InstallReferrerClient.startConnection
       ├─ 5 秒超时（withTimeoutOrNull）→ null
       ├─ responseCode == OK → 读 installReferrer，去控制字符 / trim / 截断 2048
       │    非空 → 落盘，之后只读本地
       └─ 其他响应码 / 断连 / 异常 → null
  返回值（可能是空串）写入请求字段 tsd589，然后才 POST
```

EfileScan **没有** Referrer 重试循环：连一次、等最多 5 秒，失败就带空串继续请求。本工程相同。sideload / 没接 Play 商店时 `tsd589` 仍会是空串，这是 SDK 拿不到，不是没等。

**解析容错**：值既可以是嵌套 JSON 对象，也可以是 JSON 字符串。缺项回落本地默认；未列出 key 忽略；单个位置格式错只丢那个位置。

debug 的 Base URL 是 `https://testserver.googletogoogle.com/`；release 留空（prod 地址待后端分配）。8 个请求字段 Key 已写入两份 `sp_dispatch_config.xml`：`hne8oo` / `ke3har` / `1bfui7` / `pz9zqb` / `b3dgej` / `tsd589` / `0ifdut` / `y1ywr3`。校验不通过（地址不是 https 且以 `/` 结尾，或 Key 为空 / 重复 / `REPLACE_KEY`）就不发请求，不崩溃。

### 4.2 请求字段

请求 JSON **不使用语义化字段名**，8 个混淆 Key 始终全部发送，取不到的发空串。取值对齐 EfileScan §3.2：

| 语义 | 取值 | 现状 |
|---|---|---|
| 包名 | `application.packageName` | 已实现 |
| 版本号 | `VERSION_NAME.substringBefore('-')` | 已实现 |
| Distinct ID | 本机持久 UUID | **尚未与 BI / Adjust 对齐**；为空则不发 dispatch |
| GAID | `AdvertisingIdClient`（IO 线程） | 取不到发空串 |
| 机型 | `Build.MODEL`，空则 `unknown` | 已实现 |
| Install Referrer | Play Install Referrer SDK | 首次取到后持久化，之后只读本地；去控制字符、trim、截断 2048；超时/失败发空串。不接 Adjust，不追加 `adjust-pass` |
| 语言 | `toLanguageTag()`，如 `en-US` | 已实现 |
| 系统版本 | `Build.VERSION.RELEASE`，空则 `unknown` | 已实现 |

公共头：`Accept: */*`、`Content-Type: application/json; charset=UTF-8`、`X-App-Id`、`X-App-Version`、`X-Environment`（debug=`staging`，release=`prod`）。每次 dispatch 前报 `biconfig_request`。

`api/cloak` 入参相同；成功条件是 HTTP 2xx **且** `code == 200`（不认 0）。

### 4.3 响应 key（照抄 Apollo，放在 `data` 里）

| 容器 key | 内容 | 使用方 |
|---|---|---|
| `ac_ad_config` | 17 个位置 → 单元列表 | 广告层 |
| `ac_nat_config` | `switch_open` / `jump_percent` | 原生关闭键 |
| `new_config` | 退后台通知策略（4 场景 × 冷却/上限/延迟） | 通知层「新」判据 |
| `acpop_config` | 广告点击弹窗策略 + 免打扰时段 + `ac_a` | 通知层「老」判据、闹钟排程 |
| `pop_refresh` | 通知重发 | 重发循环 |
| `media_config` | `switch` / `intervalTime` | media2 一路 |
| `win_pop_config` | 窗口弹窗误触 | **仅解析，未接线** |
| `ads_limit` | `ac_ads_limit` | `SpBlockingLogic` |
| `global_block` | `global_block_switch` | 总闸 |
| `google_play_block` | `google_play_block` | 总闸 |
| `vpn_config_switch` | `vpn_config_switch` | VPN 拦截 |
| `virtual_block_switch` | `virtual_block_switch` | 虚拟键 |

`ac_u_limit` 以小写 l 为准（`AcPopConfig` 的 SerializedName）；`ac_u_Limit` 是 Apollo DefaultConfig 的笔误，解析时两种都认。

**下发可保留、客户端忽略**（依赖未移植的买量 / 设备 / 用户维度）：`gsbnnsk`、`ac_black_refer_user`、`adb_block_switch`、`samsung_ac_pop`、`samsung_new_config`、`code_config`。

位置白名单校验：不在 `SpAdPosition.ALL` 里的 key 会被丢弃并打 warn。

总闸解析：key 缺失保持当前值；解析异常按 Apollo 回落（`ads_limit=30`、`global=1`、`google_play=0`、`vpn=1`、`virtual=1`）。

---

## 5. 广告层

### 5.1 17 个位置

`SpAdPosition.ALL`（顺序与 Apollo `AdConfig` 的字段顺序一致）：

`ac_launch`、`ac_language_int`、`ac_guide_int`、`ac_file_int`、`ac_top_int`、`ac_check_int`、`ac_mainback_int`、`ac_uninstall1_int`、`ac_uninstall2_int`、`ac_main_nat`、`ac_recent_nat`、`ac_recent_banner`、`ac_setting_nat`、`ac_uninstall1_nat`、`ac_uninstall2_nat`、`ac_language_nat`、`ac_guide_nat`

单条配置：`ad_unit_id` / `ad_paltfrom`（Apollo 拼错的原样保留）/ `ad_type`（`op` 开屏、`int` 插屏、`nat` 原生、`banner` Banner）/ `ad_timelimit`（秒）。

**本地默认：一个单元 ID 都没有**（`SpAdConfig()` 是空 Map）。所以现在跑起来不加载任何广告，等中台把单元下发下来。

### 5.2 总闸 `SpBlockingLogic`

去掉用户/设备维度后只剩「总闸 + 兜底」：

| 判据 | 默认值 | 语义 |
|---|---|---|
| `canShowAllAd()` | `globalBlockSwitch=1`、`googlePlayBlock=0` → **放行** | 关总闸放行；否则 `googlePlayBlock != 1` 放行 |
| `canShowAdByShowCountLimit()` | 上限 **50** 次/日，跨天归零 | 展示成功后 `updateAdShowCount()` +1 |
| `canSendSystemNotification()` | 同上 | 通知、前台服务共用一个判据 |
| `isVPNBlock()` | `vpnConfigSwitch=0` → 关 | VPN 探测命中才拦 |
| `isHideVirtualButtons()` / `isShowNativeAdCloseButton()` / `isShowRatingDialog()` | — | 去掉用户维度后都退化成总闸判据 |
| `isShowPartialAdsOnly(position)` | **恒 `false`** | 原逻辑是「保量位 且 用户是特殊/自然量」，用户维度不迁 |
| `PART_SHOW_POSITIONS` | 常量表保留 | 将来接回用户维度用，现在不参与判断 |

> 这 5 个开关已从 `data` 里的 `ads_limit` / `global_block` / `google_play_block` / `vpn_config_switch` / `virtual_block_switch` 解析写入。key 缺失保持当前默认值。

### 5.3 缓存 `SpAdCache`

- 三类各**只留 1 条**，入库前先踢旧的（原生会 `destroy()`）。
- **取用时先出队再校验 TTL**，过期就不还（等于丢弃）。
- TTL 取该位置**列表首条**的 `ad_timelimit`；列表没配 → 直接返回 null。
- 未给 TTL 时按 0 处理，等于「立刻过期」，避免旧创意被无限复用。
- 取用前先过 `canShowAdByShowCountLimit()`。
- 配置换了一版 → `clearAll()`。

### 5.4 全屏广告 `SpFullScreenAdManager`

加载（`checkAndLoadAd`）：

1. 前置：SDK 已初始化、非保量位、非 VPN、未超日限、总闸放行 —— 任一不过就静默返回。
2. 取该位置的单元列表，从第 `index` 个开始。
3. 已有同类缓存、或该单元 ID 正在加载中 → 返回（不重复加载）。
4. `AppOpenAd.load` / `InterstitialAd.load`。
5. **失败**：同一 `sessionId` 最多重试 **3** 次，用完才换列表里的**下一个单元**（不是换位置）。
6. `loadingAdStateMap` 按 `adUnitId` 串行化并发。

展示（`showAd`）：

- `isAdTryShowing` 防重入。
- 先盖 `SpFullScreenMask`（**500ms 假加载**，全屏、不可取消、隐藏系统栏；Activity 正在结束/已销毁时**不弹**，直接回调）。Apollo 常量 `LOADING_DIALOG_DURATION = 500`。
- 遮罩关闭后真正 `show()`。展示成功 → `updateAdShowCount()` + 立刻补下一次预加载；展示失败 → 报 `Shark_ad_impression_error` + 也补一次预加载。
- `showInterstitialAdNoWait(position, onDone)`：**不等**，有缓存就展示、没有立刻回调；Activity 不可用时把出队的缓存补一次预加载。

埋点：`Shark_ad_placement_request`（开始加载）、`Shark_ad_source_request`（加载成功）、`Shark_ad_source_error`（加载失败）、`Shark_ad_impression`、`Shark_ad_impression_error`、`Shark_ad_click`、`Shark_ad_close`。`Shark_ad_chance` 只在 `showInterstitialAdNoWait` 和 `SpBootActivity` 里报。

### 5.5 原生 `SpNativeAdManager`

- `checkLoadAndShowAd(position, style)`：有缓存立即显示；没有则**边加载边每 200ms 轮询**等就绪。
- 失败同样换下一个单元；单元 ID 为空串直接跳到下一个。
- 展示前先把容器置 `GONE`，绑好数据再 `VISIBLE` 并 `updateAdShowCount()`。
- **关闭键行为**（Apollo `displayBigNativeAd` / `displayNativeAd` 原样）：
  - `switch_open != 1` → 关闭键整体隐藏（用户找不到关闭入口）。
  - 总闸不放行 → 关闭键隐藏。
  - 否则点关闭时掷 `[0,100)`：**小于 `jump_percent` 就替用户点一次广告 CTA**，否则才真的隐藏。
- `release()`：取消轮询、清容器、`NativeAd.destroy()`。

⚠️ **已知缺口**：GMA 的 `NativeAd` 没有点击回调（Apollo 那版白牌 SDK 有 `setAdEventCallback`），所以**原生广告的点击收不到**，不会触发 `ad_click` 提醒；原生也没有 `Shark_ad_click` / `Shark_ad_impression` 埋点。要补得换 SDK 或自己在 `NativeAdView` 上挂监听。代码里已用 `ponytail:` 注记标出。

### 5.6 Banner

- **不缓存**，每次进页面现拉。
- 尺寸「屏宽 dp × 70」，取 Apollo 写法。
- 传一个单元就用列表第一条；`isReleased` 后不再加载。
- `onAdImpression` 里 +1 展示计数；`onAdClick` 记点击。
- **`release()` 必须由页面在销毁时调用**，否则 `AdView` 泄漏。

### 5.7 收入上报 `SpAdIncomeReport`

Apollo 三路，我们只接了一路：

| 通道 | Apollo | 本工程 |
|---|---|---|
| Firebase `ad_impression_revenue`（`value` / `currency` / `network`） | 有 | ✅ 已实现 |
| Adjust `AdjustAdRevenue("admob_sdk")` | 有 | ❌ 工程无 Adjust 依赖 |
| Facebook `AppEventsLogger` | 有 | ❌ 工程无 Facebook 依赖 |

值口径：`valueMicros / 1_000_000.0`（**原币种金额，不是 eCPM**）。`network` 取中介网络名，缺省 `Admob`。

⚠️ **两个待确认点**：
1. `currency` 按 Apollo 原样**硬编码 `"USD"`**，但 `value` 是原币种金额。两者不一致时口径会错，要不要改成 `AdValue.currencyCode` 需要拍板。
2. Adjust / Facebook 两路要补依赖才能开，代码里留了升级路径注释。

**展示级收入只报 `ad_impression_revenue` 这一个事件**（全工程唯一入口）。

### 5.8 现在真正接到页面的位置（重要）

开屏结束按 Apollo `SplashActivity.nextPage`：卸载入口 → `SpUnInstall01Activity`；首次打开 → `SpGuideLanguageActivity` → `SpGuideInfoActivity`；否则 → `CamShellActivity`。引导页 Skip 才把 `IS_FIRST_OPEN` 写成 false。

| 位置 | 接在哪 | 形态 / 触发 |
|---|---|---|
| `ac_launch` | `CamPdfApp` 启动预加载 + `SpBootActivity` 展示 | 开屏 |
| `ac_language_nat` | `SpGuideLanguageActivity` 进页先 `GONE`，就绪才显示 | 原生 small |
| `ac_language_int` | 进页预加载；点 Next 或 5 秒倒计时结束 `showInterstitialAdNoWait` | 插屏，无缓存不等待 |
| `ac_guide_nat` | `SpGuideInfoActivity` 进页先 `GONE`，就绪才显示 | 原生 big |
| `ac_guide_int` | 进页预加载；点 Skip 后 `showInterstitialAdNoWait` | 插屏，无缓存不等待 |
| `ac_main_nat` | `CamShellActivity` 进页加载 | 原生 small，挂在底栏上方 |
| `ac_recent_banner` | `CamShellActivity` 进页加载 | Banner，挂在原生与底栏之间。Apollo 位名叫 recent，但是挂在首页 Activity 上 |
| `ac_file_int` | 底栏切 Home / Recent / Made；点扫描 | 插屏。距上次展示不足 60 秒不弹；无缓存只预加载。扫描会等关闭再进拍照页 |
| `ac_top_int` | 首页 PDF / Word / Excel / PPT / TXT 类型切换 | 插屏，同样 60 秒节流；默认 PDF 第一次选中不弹 |
| `ac_check_int` | `CamDocRouter.open` 打开文件 | 插屏，关掉或无缓存后再进阅读器 |
| `ac_mainback_int` | `CamPdfReaderActivity` / `CamOfficeReaderActivity` / 设置里的 `CamLanguageActivity` 返回 | 插屏，进页预加载 |
| `ac_setting_nat` | `CamSettingsActivity` 进页加载 | 原生 big |
| `ac_uninstall1_int` | `SpUnInstall01Activity` 进页预加载 + 点「卸载」展示 | 插屏 |
| `ac_uninstall1_nat` | `SpUnInstall01Activity` 进页加载展示 | 原生 small |
| `ac_uninstall2_int` | `SpUnInstall02Activity` 进页预加载 + 确认框「卸载」后展示 | 插屏 |
| `ac_uninstall2_nat` | `SpUnInstall02Activity` 进页先 `GONE`，就绪才显示 | 原生 small |

**`ac_recent_nat` 不展示。** Apollo `HomeActivity` 没有任何页面调用这个位置，本工程也不新建展示位。

展示前仍走 `SpBlockingLogic`（部分展示、次数、VPN、总闸）。插屏「不等待」与两步卸载页同一套 `showInterstitialAdNoWait`。

### 5.9 UMP / WebView 预热

- `SpUmpGate`：EEA/UK/瑞士的同意表单，超时 2.5s 兜底，`DEBUG_FORCE_EEA` 默认关（要验 GDPR 表单时临时开，需要 AdMob 隐私消息配置 + 测试机）。
- `SpWebWarmup`：用 `about:blank` 预热一次 Chromium 再销毁，**保留一个存活的 WebView 会让 AdMob 拿不到 JS 引擎**，App Open 广告就加载不出来。1.5s 兜底销毁。

---

## 6. 通知子系统

### 6.1 7 个触发场景

| 场景 | 触发源 | 文案池 | 先发 media2 | 延迟 |
|---|---|---|---|---|
| `scheduled` | 回前台后定时轮询（4s 预热，之后每 60s） | 定时池 | 是 | 无 |
| `unlock` | `USER_PRESENT` / `USER_UNLOCKED` 后 1.5s | 解锁池 | 是 | 无 |
| `alarm_clock` | `AlarmManager` 到点 | 闹钟池 | 是 | 无 |
| `ad_click` | 点了广告 | **解锁池** | 是 | `adclick_delay` |
| `home_click` | Home 键 | **定时池** | 否 | `home_delay` |
| `recent_click` | 最近任务键 | **定时池** | 否 | `recent_delay` |
| `no_page_exist` | 页面全退（退到桌面） | **闹钟池** | 否 | `exit_delay` |

> 文案池交叉复用（`ad_click` 用解锁池、`home/recent` 用定时池、`no_page` 用闹钟池）是 **Apollo 的原样，不是笔误**。

统一三段式：

1. **挑文案** —— 从 `SpNotifyCatalog` 的轮换池取下一条（顺序推进，跨进程重启接着轮）。
2. **分流** —— 有通知权限且总闸放行 → 再过场景频控 → 等延迟秒 → **普通提醒 + 媒体式提醒各发一条**，计数 +1；没有通知权限 → 退到媒体式那一路，只发媒体式，计数 +1。
3. **延迟之后、真正发之前再查一次「是不是回到前台了」** —— 用户已经回到 App 里就不打扰。

延迟触发（`home/recent/exit/ad_click`）**额外要求远端配置已经到过一次**，没到就不发（Apollo `if (remoteConfig.newConfig != null) return`）。

### 6.2 三类通知

| 类型 | 渠道 | 版式 | 通知 ID | 发出埋点 |
|---|---|---|---|---|
| 普通提醒 | `channel_reader_normal` / `_silent` | `sp_notify_sys_big` / `sp_notify_sys_small` | 按场景，3 个 ID 里挑一个没被占用的 | `system_notification_trigger`（带 `type`） |
| 媒体式提醒 | `channel_reader_media` / `_silent` | 大版式 | 固定 `945458` | `media_notification_trigger`（带 `type`） |
| media2 | `channel_ado_pdf_media_two` | 大版式 | 固定 `698228` | 无 |
| 常驻（前台服务） | `channel_sp_persistent` | `sp_notify_persistent_big` / `_small` | 固定 `234397` | `always_notification_trigger` |

渠道优先级统一 `IMPORTANCE_HIGH`；`silent` 变体不响不震不亮。

**通知 ID 轮换**（Apollo `getCheckNotificationID`）：每个场景 3 个 ID，**从后往前**挑第一个状态栏里没占用的；都占着就退回最后一个。

**文案轮换**：三种池各 8 条，`index = stored + 1`，越界回 0。**首次调用拿到的是第 2 条**（`stored + 1` 而不是 `stored`）—— 原样保留。

**版式差异**：Apollo 的媒体式用 `MediaStyle` + 假 `MediaSession`；本工程没引 `androidx.media`，用「大图 + 自定义按钮」等价格式替代，用户可见结果一致。要真正变媒体通知，升级路径是加依赖再换 `MediaStyle`。

### 6.3 重发循环（`pop_refresh`）

`pop_refresh_switch == 1` 且**亮屏**时：先在后台协程里用同一个 ID 重复 `times` 次、每次间隔 `interval` 秒再发一遍；第 0 次是响铃版，之后是静音版。观感是「这条提醒一直挂在最上面」。中途熄屏或回到前台立刻收手。默认 `times=30` / `interval=2s`。

### 6.4 两套频控（`SpNotifyFilter`）

两套并存，按场景分流（Apollo 的历史包袱，不是笔误）：

| 判据 | 场景 | 配额来源 | 额外条件 |
|---|---|---|---|
| **老** `oldSceneFilter` | `scheduled` / `unlock` / `alarm_clock` | `acpop_config`（`ac_t` / `ac_u` / `ac_a` + 上限） | 免打扰时段（只在其内发）+ 不在前台 |
| **新** `newSceneFilter` | `home_click` / `recent_click` / `no_page_exist` / `ad_click` | `new_config`（`ac_home` / `ac_recent` / `ac_exit` / `ac_adclick` + 上限） | 要求远端配置已到 |

两套都是「距上次 ≥ 冷却分钟 **且** 当日次数 < 上限」；跨天把对应计数归零。

**免打扰时段**：默认 `nopop_start=24` / `nopop_end=6`，代码取 `from = min(24,6) = 6`、`to = max(24,6) = 24`，判据是 `hour < from || hour >= to` 就**不发**。净效果：**只在 06:00–24:00 发**，凌晨 0–6 点静音。

### 6.5 广播接收器

| 接收器 | 注册方式 | 触发 | 动作 |
|---|---|---|---|
| `SpPhoneLockReceiver` | 清单（`directBootAware`） | `USER_PRESENT` / `USER_UNLOCKED` 等 | 等 1.5s 发解锁提醒；**任何**广播都等 2s 点一次保活 |
| `SpKeyReceiver` | **必须动态注册** | `CLOSE_SYSTEM_DIALOGS` + `reason` | `reason` 含 `home` / `recent` → 发对应提醒 + 点保活；1.5s 去抖 |
| `SpAlarmReceiver` | 清单（无 filter，靠 PendingIntent） | 闹钟到点 | 发闹钟提醒 + 点保活 + **无条件重排下一个闹钟** |

`CLOSE_SYSTEM_DIALOGS` 在 Android 8 以后不进清单广播白名单，**只有动态注册收得到**，这是必须的。动态注册用 `RECEIVER_NOT_EXPORTED`。

> **差异**：Apollo 除了清单注册，还在 `ApoApplication` 里动态注册了 `PhoneLockRe` 的另一个实例（`USER_PRESENT` + `SCREEN_ON/OFF`），**同一件事会被触发两遍**。这里只留清单注册，不重复。

### 6.6 闹钟排程

- 到点时间 = 现在 + `acpop_config.ac_a` 分钟；`setAndAllowWhileIdle(RTC_WAKEUP, ...)`，Doze 里也能醒。
- `force = false`：已经排过还没到就跳过（开机 / 启动路径）。`force = true`：无条件重排（闹钟响了之后）。
- 排之前 `waitAcPopConfig()` 最长等 5s（每 200ms 看一眼）。因为本地默认 `ac_a = 30`，正常路径第一轮就返回，轮询是为跟 Apollo 时序一致。
- RequestCode `41385`，下次时间落在 `sp_set_alarm_next_time`。

### 6.7 生命周期钩子（`SpNoticeLifecycle`）

- **Activity 集合空了** → 发 `no_page_exist`（用户把页面都退了 = 退到桌面）。
- **前台计数减到 ≤ 0 且这期间点过广告** → 发 `ad_click`。点击标记是**一次性的**（`SpAdSession.consumeAdClick()` 取走即清零），不会重复发。

Apollo 还有两件事这里不做（与通知无关）：`toReStartApp` 触发的重新拉起 Splash、全部页面销毁时置 `isColdStart`。

---

## 7. 保活

### 7.1 两条拉起路径

| 方法 | 手段 | 谁调 |
|---|---|---|
| `launchForegroundService` | 直接 `startForegroundService` | `SpAssistanceLaunchJobService.onStartJob` |
| `startLaunchService` | 排一个 `minimumLatency = 0` 的 Job（ID `46555`），借系统把进程叫起来 | 按键 / 解锁 / 开机 / 闹钟 |

**「先排 Job、再在 Job 里起前台服务」是核心**：后台直接起前台服务会被系统拒，而 JobScheduler 一定能把进程拉起来。

### 7.2 三个壳

| 类 | 说明 |
|---|---|
| `SpApoForegroundService` | 前台服务，`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`（29+）；`START_STICKY`；`isRunning` 静态标记给启动器判重。`onStartCommand` 每次回来重挂通知；通知被划掉时补报一次 `always_notification_trigger` |
| `SpAssistanceLaunchJobService` | `onStartJob` 点一次前台服务，**永远返回 `false`**（干完就结束，不占调度）；`onStopJob` 也 `false` |
| `SpYoutubeService` | 清单里声明 `android.media.MediaRouteProviderService`，只挂一条系统 binder 连接、本身不干活 |

### 7.3 冷启动挂常驻通知（`startAppCreateTask`）

`delay(5000)`（等启动抖动过去、避开开屏）→ 然后：

- **Android 12+ 或前台服务已在跑 → 只补发常驻通知**，不起前台服务；
- 否则 → `startForegroundService`。

**12+ 只发通知不起服务是 Apollo 的原始分支，不是笔误**：12 起后台起前台服务会被 `ForegroundServiceStartNotAllowedException` 拦，而 12+ 系统本来也不会因为这个通知回收进程。进程真要拉起来走 `startLaunchService` 那条 Job。

### 7.4 开机 / 覆盖安装

`BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`，并行两件事：

1. 等 **1500ms** → 排零延迟 Job 点起进程与前台服务；
2. 立刻无条件重排闹钟。

等 1500ms 的原因：开机瞬间系统正忙，JobScheduler 直接排会被丢；覆盖安装同理，包内组件还没就绪。

### 7.5 常驻通知

- 自定义版式：PDF / EXCEL / PPT 三个入口各挂一个 PendingIntent，都进 `SpBootActivity`，靠 `jump_type` 区分；`from_type = persistent_notify`。
- **兜底版**：自定义版式炸了退化成一条普通通知（标题 App 名、正文取定时池下一条）。
- 前台服务里先试自定义版式、失败再试兜底版、再失败 `stopSelf()`。
- 发之前判重：已经在状态栏里、或没通知权限 → 直接返回。不去重会闪一下。

---

## 8. 卸载挽留

### 8.1 完整链路

```
桌面「卸载」快捷方式（app_shortcuts.xml / delete_action）
  → SpLeaveEntryActivity           中转一帧，补 NEW_TASK | CLEAR_TASK
  → SpBootActivity                 冷启动（isTaskRoot 才能过）
  → SpUnInstall01Activity          第 1 步（开屏 nextPage 优先于首次引导）
  → SpUnInstall02Activity          第 2 步
  → 系统「应用详情」页（ACTION_APPLICATION_DETAILS_SETTINGS）
```

`CamShellActivity` 仍保留 `maybeOpenLeaveFlow`，防止以后还有人把 `sp_open_leave` 直接丢给首页。

**为什么要有中转页**：静态快捷方式的 intent **带不了 flag**，而开屏要求自己必须是 task root（否则直接 `finish()`）——App 已在后台时点快捷方式就会什么都不发生。所以由 `SpLeaveEntryActivity` 把 action / extras / data / categories 原样搬到去开屏的 intent 上，补 `NEW_TASK | CLEAR_TASK`（Apollo 的 `268468224` 就是这两位）再拉起，清掉旧任务栈让开屏成为新的 task root。界面沿用开屏布局，只活一帧，切过去不闪。

### 8.2 入口判定（`SpLeaveEntry`）

| 项 | 值 | 说明 |
|---|---|---|
| `ENABLED` | **`true`** | 代码侧总开关（Black 2026-10-10 定：打开） |
| `ACTION_DELETE_ENTRY` | `com.scanpro.cam.pdf.delete` | 与两份 `app_shortcuts.xml` 里的 action 一致 |
| `EXTRA_OPEN_LEAVE` | `sp_open_leave` | 开屏转交主界面时的标记 |

**开关有两处，必须同时改**：`SpLeaveEntry.ENABLED` 和 `app/src/debug|release/res/xml/app_shortcuts.xml` 里的 `delete_action`。Android 不支持用一个开关同时管代码和快捷方式。要关掉整条流程，两处都要处理。

`shortcuts.xml` 里那条 intents 的 `targetPackage` 两份不同：debug 是 `com.test.lwapp`、release 是 `com.scanpro.cam.pdf`——**debug 的 applicationId 被 `androidComponents` 改过**，照着改否则装机后点快捷方式报「应用不存在」。

### 8.3 第 1 步 `SpUnInstall01Activity`

| 控件 | Apollo id | 动作 |
|---|---|---|
| `btn_back` | `backImage` | 回首页 |
| `tv_action_01` / `tv_action_02` | `textAction01` / `02` | 回首页 |
| `btn_stay` | `stay` | 回首页 |
| `btn_uninstall` | `uninstall` | 先插屏 `ac_uninstall1_int`，关闭后进第 2 步 |
| `ad_layout` | `adLayout` | 原生 `ac_uninstall1_nat`（small），进页立即加载 |

- 两个问题卡（`card_issue_01` / `card_issue_02`）**整卡不可点**：Apollo 只给里面的 `textAction0x` 挂了点击，卡片本身没有监听。照原样。
- 进页预加载插屏；点卸载走 `showInterstitialAdNoWait` —— **不等**，有缓存就展示、没有就直接进第 2 步。

### 8.4 第 2 步 `SpUnInstall02Activity`

| 控件 | Apollo id | 动作 |
|---|---|---|
| `btn_back` / `btn_stay` | `backImage` / `stay` | 回首页 |
| `content_layout_0x` + `check_image_0x` | `contentLayout0x` / `checkImage0x` | 切换勾选（`ic_selected` / `ic_unselected`） |
| `btn_uninstall` | `uninstall` | 弹底部确认框 |
| `ad_layout` | `adLayout` | 原生 `ac_uninstall2_nat`（small），**进页先 `GONE`，就绪才显示** |

确认框（`dialog_sp_uninstall_confirm.xml`）：**不可取消**、底部弹出、`dimAmount 0.7`。

- 点「卸载」→ 关框 → 插屏 `ac_uninstall2_int` → 关闭后跳系统应用详情页；
- 点「继续使用」→ 关框 → 回首页。

三个勾选项**纯记录用户意图**，Apollo 和本页都不读取、不上报，勾不勾都不影响后续流程。

> **旧的 `SpLeaveActivity` / `SpLeaveFeedbackActivity` 及其布局、字符串已删除**（Black 2026-10-10 定），不要在别处引用。

---

## 9. 埋点总表（代码里实际会发出的事件）

环境：Firebase Analytics。事件名照抄 Apollo `InsertEvent` / `Shark_*`。

| 事件名 | 在哪发 | 参数 |
|---|---|---|
| `app_open_cold` | `CamPdfApp` + `SpBootActivity` | 无 |
| `ad_sdk_ready` | `SpAdMobInit` | 无 |
| `ad_impression_revenue` | `SpAdIncomeReport` | `value` / `currency` / `network` |
| `Shark_ad_chance` | `SpBootActivity`、全屏/原生/Banner manager | `scene`（开屏时）、`type` |
| `Shark_ad_placement_request` | 三个 manager | `position`/`scene`、`unit`、`session` |
| `Shark_ad_source_request` | 三个 manager | 同上 |
| `Shark_ad_source_error` | 三个 manager | 同上 + `message` |
| `Shark_ad_impression` | 全屏、Banner | 无参数 |
| `Shark_ad_impression_error` | 全屏 | `code`、`message` |
| `Shark_ad_click` | 全屏、Banner | 无参数 |
| `Shark_ad_close` | 全屏 | 无参数 |
| `biconfig_request` | `SpConfigRemoteSource` 每次 dispatch 前 | 无 |
| `system_notification_trigger` | `SpSendManager` | `type`（场景） |
| `media_notification_trigger` | `SpSendManager` | `type` |
| `always_notification_trigger` | `SpPersistentNotifyManager`、`SpApoForegroundService` | 无 |
| `system_notification_click` | `SpBootActivity` | `type` |
| `media_notification_click` | `SpBootActivity` | `type` |
| `always_notification_click` | `SpBootActivity` | 无 |
| `system_notification_allow` | `SpNotifyPermit`（**只报一次**，落库标记） | 无 |
| `system_notification_popup_view` | `SpBootActivity` | 无 |
| `rating_dialog_show` / `rating_submit_low` / `rating_submit_high` | `SpRateSheet` | — |
| `convert_success` | `CamCaptureReviewActivity` | — |

> `SpTracker` 里还声明了一批**目前没人发**的常量（`trigger_*_ntf` / `click_*_ntf` 那 12 条文案级事件、`cue_skip_*`、FCM、悬浮窗、`ad_show_splash` 等）。它们是照抄 Apollo 时一并落下的，等对应功能接线时用，**不要以为已经在报**。

---

## 10. 与 ApolloPDF 的逐条差异（研发会问的）

| # | Apollo | 本工程 | 原因 |
|---|---|---|---|
| 1 | Firebase Remote Config 拉配置 | 自建中台配置（`config/`） | Black 要求 |
| 2 | 买量 / 用户 / 设备维度判据 | 全部去掉，只留总闸 + 兜底 | Black 要求 |
| 3 | 白牌预加载 SDK（`ads_mobile_sdk`） | GMA 原生 `AppOpenAd` / `InterstitialAd` / `AdLoader` / `AdView` | 我们接 AdMob |
| 4 | `MediaStyle` + 假 `MediaSession` | 大图 + 自定义按钮等价格式 | 没引 `androidx.media` |
| 5 | 反射 + AES 解类名 | 直接调用 | 反分析手法，行为一致 |
| 6 | 清单 + 动态各注册一遍 `PhoneLockRe` | 只留清单注册 | 去掉重复触发 |
| 7 | 硬编码测试机 ID 在 `RequestConfiguration` | `SpAdDevFlags` / `BuildConfig.USE_TEST_ADS` 控制，源码不留设备 ID | 干净 |
| 8 | 三路收入上报 | 只接 Firebase 一路 | 工程无 Adjust / Facebook 依赖 |
| 9 | 原生广告有点击回调 | 无（GMA 限制） | 见 §5.5 缺口 |
| 10 | 机型分流（xiaomi / oneplus / samsung 跳过 media2） | 不迁 | 设备维度 |

---

## 11. 现状：现在装上去会是什么样（重要）

按代码静态判断（**未在设备上验证**）：

| 现象 | 原因 |
|---|---|
| **一个广告都不会加载** | release 的 Base URL 仍空，不发请求；debug 会发 `api/dispatch`，但远端 `data` 里还没有本项目的广告单元时同样不会加载 |
| **开屏直接进主界面** | `ac_launch` 没有单元，`presentLaunchAd` 立刻回调 `onDone` |
| **四个延迟场景的通知不发**（`home_click` / `recent_click` / `no_page_exist` / `ad_click`） | 它们要求「远端配置已到过一次」，`remotePayloadReady` 在 Unavailable 时保持 `false` |
| **三个无延迟场景的通知会发**（`scheduled` / `unlock` / `alarm_clock`） | 它们**没有**这道前置，只要通知权限 + 总闸 + 频控 + 不在 0–6 点就发 |
| **保活会起来** | 与配置无关，冷启动 5s 后挂常驻通知（12+ 只发通知） |
| **卸载挽留两步页能打开、但没有广告** | 入口已开（`ENABLED = true` + 快捷方式），广告位没 ID |

**debug 混淆 Key 已填。** 联调看的是远端 `data` 是否下发了本项目的 Apollo JSON（尤其 `ac_ad_config`）。release 的 Base URL 还空着，等 prod 地址。

---

## 12. 待办 / 需要拍板

| # | 事项 | 卡住什么 | 谁回 |
|---|---|---|---|
| 1 | release 的 `api/dispatch` Base URL；以及后端 `data` 按本项目 Apollo JSON 下发 | 线上包配置、广告、四个延迟场景通知、cloak | 中台 / Black |
| 2 | `ad_impression_revenue` 的 `currency` 要不要从硬编码 `"USD"` 改成 `AdValue.currencyCode` | 非美元地区的收入口径 | Black |
| 3 | 要不要补 Adjust / Facebook 依赖（三路收入上报） | 收入回传完整度 | Black |
| 4 | `refer` 字段：ScanPro 是否接 Install Referrer | 归因字段拿不到真实值 | Black |
| 5 | `distinctId` 与 BI / Adjust 的设备标识对齐 | 配置请求里的设备标识目前是本机 UUID 占位 | Black |
| 6 | `SpBlockingLogic` 的 5 个开关已从 dispatch `data` 解析；运营要能动需后端下发对应 key | 已接线，待联调 | — |
| 7 | 16 个广告位已接到对应页面；`ac_recent_nat` 按反编译不展示 | 已接线 | — |
| 8 | 原生广告点击收不到（`ad_click` 提醒少一路触发）要不要补 | 一个通知触发场景 | Black |
| 9 | `SpSceneCode.kt` 文件头注释写「45 个常量」，实际是 42 个（40 场景码 + 2 个 `from_type` 取值） | 注释与代码不符，需核对 Apollo 原表 | 研发 |

---

## 13. 验证

静态核对我已经做完（**没有编译、没有运行**）：

- 移植代码引用的资源全部存在：`sp_ic_notify`、`sp_notify_btn_anim`、`sp_shape_notify_btn`、`ic_home_list_*`、`ic_selected` / `ic_unselected`、`cam_app_mark`；6 个通知/对话/挽留布局；`SpFullScreenLoadingDialog` style。
- 布局里的 view id 与代码逐项对上：4 个通知布局、2 个挽留页、1 个确认框、2 个原生版式（含 `MediaView` / `NativeAdView`）。
- 无真实广告 ID 残留（单元 ID 全部来自远端，本地为空）。

装机验证的清单另有一份，逐项对照打勾用：[pdfcam 移植功能装机验证清单.md](</Users/blackc/Developer/flux/document/业务流程SOP/pdfcam 移植功能装机验证清单.md>)。

> 这些都要**装机**才能验：通知权限框时机、三类通知的实际观感、重发循环是否顶住、解锁/Home/最近键/退桌面四个触发、闹钟到点、保活存活率、快捷方式落点。本地不编不跑，交 Black 安排。
