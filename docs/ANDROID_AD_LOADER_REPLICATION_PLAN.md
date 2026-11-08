# 广告加载逻辑复刻计划：A(SmartPdfScan) → B(ScanPro-CamtoPDF)

> 目标：把 B 现在自有的广告「加载内核」整体替换为 A 项目 `com.zppko.pdfplus.nor.ads` + `com.mymy.mymy.ads` 那套逻辑。
> 方式：**完全复刻**（类名、方法名、常量、调用链照搬 A），只对「TradPlus→AdMob」和 B 环境差异做最小适配并逐条登记。
> 前提（用户已确认）：广告位/配置仍用 B 的（17 位 + `ac_ad_config`）；加载展示时机不变；仍用 AdMob；**不做 eCPM 竞争**。

---

## 0. 需求复述（请确认是否与你的想法一致）

1. **复刻的**：A 的广告「加载逻辑」——数据模型（AdsSetting）、每位置一个 Loader 的池模型（ImpressionUtils）、加载骨架（BaseAdsLoad）、分类型日频控（AdCount*）、广告埋点（TrackAd/TrackBean/ADBlockReason）。即「怎么加载、怎么缓存、怎么判断能不能展示、怎么记埋点」全部照 A。
2. **不变的**：B 的 17 个广告位与 `ac_ad_config` 下发格式；页面接线与展示时机（哪页加载哪页展示、开屏时序 launch_min/wait/catchup、热启动开屏、60 秒插屏节流、UMP、500ms 遮罩、收入上报）。
3. **换 SDK 不换骨架**：A 的 `OpenAdsLoad/IntAdsLoad/NatAdsLoad` 骨架照搬，内部的 TradPlus 调用换成 AdMob（AppOpenAd/InterstitialAd/AdLoader/AdView）。
4. **不做的**：TradPlus 中介、eCPM 读取与竞争（`getEcpm/parseEcpm/stepnext1_ecpm`）、A 的 5 个广告位、A 的 `pps_ad_data` 配置格式。

一句话：**B 的「什么时候在哪加载/展示」不变，B 的「加载器与频控内核」换成 A 的代码。**

---

## 1. A 广告加载逻辑全貌（本次要复刻的文件清单）

### 1.1 数据模型层（原样复刻）

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `nor/ads/sett/AdsSetting.kt` | 单条广告配置（zId/zFirm/zType/zTime/zWeight/zPlace + equals） | ✅ 原样 |
| `nor/ads/helper/CodeKeyAndEvent.kt` | 位常量、类型常量（op/int/nat）、来源常量（admob/tradplus/topon）、场景码常量 | ⚠️ 适配：位常量换成 B 的 17 位（`SpAdPosition.ALL`）；新增 `xxdx_TYPE_BANNER="banner"`（A 没有 Banner）；`ddd_FIRM_*` 保留，B 固定用 `admob` |

### 1.2 频控层（原样复刻）

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `mymy/ads/AdsConf.kt` | 分类型默认频控（open 50/50、inter 50/50、native 100/50、gap 1000ms；A 还会被 `pps_refer_data` 远程覆盖） | ✅ 原样；远程覆盖字段 B 协议没有 → 只保留默认值（可选增强：后台以后在 `ac_ad_config` 同级加字段） |
| `mymy/ads/count/ADManagerMy.kt` | 跨天重置管理（start/getCount/isToday） | ✅ 原样 |
| `mymy/ads/count/AdCountOpen.kt` / `AdCountInter.kt` / `AdCountNati.kt` | 分类型日展示/日点击计数 + 间隔检查（`xxxCanShow()` = 次数 + gap） | ✅ 原样（依赖 KvUtil，需补齐，见 §2） |
| `nor/ads/InterShCount.kt` | 「打开 N 次插屏 1 次」（int_op_c） | ⭕ 可选，默认不迁（B 保留 60 秒节流=时机不变） |

### 1.3 加载骨架层（原样复刻 + 少量适配）

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `nor/ads/tool/BaseAdsLoad.kt` | 抽象基类：加载状态机（zLoadeee/zmyAdsLoadSSSS/FFFF）、展示入口（zShoweee→childShow/zDismiss）、可用性（zcanUsable/zIsShowAds）、TTL（zisTimeOut/remainingTime）、getEcpm | ⚠️ 适配：`BaseActivity`→`Activity`；`act.isHide()`→B 前后台判断；`zLoadedTimeForAds` 加 protected 访问器（供 AdMob 子类覆写 `zisTimeOut` 使 TTL 生效，见 D2）；`getEcpm()` 保留空实现（用户不做 ecpm） |
| `nor/ads/ImpressionUtils.kt` | 编排核心：每位置一个 Loader 的池 `myPoolzzdd`；`load(place)` 按权重取配置建 Loader；`loadSuccess(place)`；`show(...)`；`fireAnative(place)`；`getNewLoadConfig` | ⚠️ 适配：删除 `getEcpm/parseEcpm`（不做 ecpm 竞争）；`loadAdsSettingList` 改解析 B 协议字段；`show` 参数适配；数据源仍走 `RemoteConfigHelper.getRecentData(KEY_ADS_CONFIG)`（B 已实现该入口，返回 `ac_ad_config`，改动最小） |

### 1.4 SDK 实现层（骨架照搬、SDK 换 AdMob）★ 最大适配点

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `nor/ads/mob/OpenAdsLoad.kt` | 开屏：childLoad/childShow（TradPlus TPSplash）、onAdShowed/Clicked/Dismissed 里调 `AdCountOpen` + `TrackAd` | ⚠️ 适配：`TradAdOpen` → 新的 `AdmobAdOpen`；回调结构保留 A 的 Load/Show callback 语义；展示后 AdMob 对象即失效 → dismiss 后重建 load（A 的 `nowAd.loadAd()` 对 AdMob 无效，需登记） |
| `nor/ads/mob/IntAdsLoad.kt` | 插屏：同上（TPInterstitial） | ⚠️ 适配：`TradAdInter` → `AdmobAdInter` |
| `nor/ads/mob/NatAdsLoad.kt` | 原生：childLoad + `getAdView(point)`（A 的 childShow 本来就是空的，页面拿对象自己挂 UI） | ⚠️ 适配：`TradAdNative` → `AdmobAdNative`（AdLoader 加载 NativeAd）；渲染仍走 B 现有版式（见 D7） |
| `nor/ads/mob/BannerAdsLoad.kt` | **B 新增**：A 没有 Banner。复刻 A 骨架（childLoad/childShow/zcanUsable），AdView 实现，不缓存（保持 B 现行为） | ➕ 新增 |
| `nor/ads/trad/TradAd*.kt`、`NativeUtils.java`、`OpenRequest.kt` | TradPlus 封装/收益 | ❌ 不迁（TradPlus 不用；收益上报保留 B 的 `SpAdIncomeReport`） |

### 1.5 埋点层（原样复刻 + 通道适配）

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `mymy/ads/poi/BlockReason.kt` | `ADBlockReason` 常量（disabled/daily_cap/…） | ✅ 原样（注意文件名与对象名不一致，照 A） |
| `mymy/ads/poi/TrackBean.kt` | 埋点载体 + `getTradBean`（op/int/其他） | ⚠️ 适配：新增 banner 分支（`TrackBean.banner(...)`）；`ddd_FIRM_TRADPLUS` 传参处换 `admob` |
| `mymy/ads/poi/TrackAd.kt` | 事件方法：loadStart/loadSuccess/loadFailed/showSuccess/showDismiss/showClick/blocked/adShowTimeout | ⚠️ 适配：`FirebaseAna`（需补，见 §2）→ B 环境；`AdTrackingHelperMy` → B 已有的 `analytics/AdTrackingHelper`（API 同名同参，B 已复刻过）；事件名是否照 A 见 **D5** |
| `mymy/ads/poi/AdTrackingHelperMy.kt` | TrackSDK 广告埋点封装 | ❌ 不迁：B 已有等价 `analytics/AdTrackingHelper.kt`（trackAdRequestGMA/trackAdFilledWithSource/trackAdFailed/trackAdClickWithSource 同名同参） |

### 1.6 依赖补齐

| A 文件 | 角色 | 复刻方式 |
|---|---|---|
| `bockcock/adjust/KvUtil.kt` | AdCount* 的 SharedPreferences 封装 | ✅ 原样复刻（B 的 bockcock 包目前只有 Pin/Pt） |
| `bockcock/adjust/FirebaseAna.kt` | FirebaseAnalytics 单例封装（TrackAd 依赖） | ✅ 原样复刻（极小类） |
| `buncead/common/util/NetUtil.kt` | `isUseNet_noVpn()`：有网 + VPN 判定（A 的页面在 load 前置检查） | ⚠️ 适配：`op_vpn_limit` 来自 A 的 ConfigBean（未迁）→ 换成 B 的 `vpn_config_switch`（**注意语义**：A `op_vpn_limit=0`=拦 VPN；B `vpn_config_switch` 语义以 B 的 `SpBlockingLogic.isVPNBlock` 为准，映射时要对齐，不能照抄） |
| A 的 `ConfigBean`（inter_op_count_interval/op_vpn_limit/AdsConf 远程覆盖） | 不迁 | ❌ 用 B 现有开关替代（见 D4），登记 |

---

## 2. 必须修改/适配点汇总（逐条登记模板已含原因）

| # | 位置 | A 原始 | B 适配后 | 原因 |
|---|---|---|---|---|
| 1 | `OpenAdsLoad/IntAdsLoad/NatAdsLoad` | TradPlus（TPSplash/TPInterstitial/TPNative + TradAd* 封装） | 新增 `AdmobAdOpen/AdmobAdInter/AdmobAdNative/AdmobAdBanner` 封装，提供 A 同形状接口（getAD/isReady/loadAd/showAd/回调），三个 Loader 骨架与埋点调用点不动 | 用户定：AdMob 替换 TradPlus |
| 2 | `CodeKeyAndEvent` | 5 个位（cuu_launch 等） | B 的 17 位（`SpAdPosition.ALL` 的值）+ `banner` 类型 | 用户定：广告位/配置不变 |
| 3 | `ImpressionUtils.loadAdsSettingList` | 解析 `{boxaxj,opxmlf,ujhscs,vksnx,gkqcx}` | 解析 B 的 `{ad_unit_id,ad_paltfrom,ad_type,ad_timelimit}`；`zWeight` 缺省 0（远端数组顺序=优先级，A 的 `sortedByDescending` 稳定排序保序） | B 协议无 weight 字段 |
| 4 | `BaseAdsLoad.zShoweee/show` | `BaseActivity` + `act.isHide()` | `Activity` + B 前后台判断（`SpAdSession.isForeground` 或 finishing 检查） | B 基类是 `SpActivity`，无 isHide() |
| 5 | `BaseAdsLoad` | `zisTimeOut()` 恒 false（TradPlus 自动过期）；`zLoadedTimeForAds` private | 加 `protected fun zLoadedTime()`；AdMob 子类覆写 `zisTimeOut` 用 `setting.zTime` 使 `ad_timelimit` 生效 | B 已下发 ad_timelimit=3000，保留 B 语义（D2） |
| 6 | `TrackAd` | `FirebaseAna` + `AdTrackingHelperMy` | `FirebaseAna` 原样补齐；`AdTrackingHelperMy`→B 的 `AdTrackingHelper`（同名 API） | B 已有一套等价埋点封装 |
| 7 | `TrackBean` | op/int/其他→nati | 加 banner 分支 | B 有 Banner 位 |
| 8 | `NetUtil` | `ConfigBean.op_vpn_limit` | B 的 VPN 开关（语义反转处登记） | A 的 ConfigBean 未迁 |
| 9 | `ImpressionUtils` | `getEcpm/parseEcpm` | 删除；A 的 `SpBootActivity.stepnext1_ecpm` 不迁 | 用户定：不做 ecpm 竞争 |
| 10 | AdMob 展示后行为 | `nowAd.loadAd()` 复用对象 | dismiss/show 失败后销毁对象并重新 childLoad 一条新广告 | AdMob 全屏广告一次性（A 的复用写法仅 TradPlus 有效） |
| 11 | 原生点击埋点 | TradPlus 回调有 onAdClicked | GMA NativeAd 无点击回调 → `AdCountNati.nati_addClickCount/TrackAd.showClick` 收不到（与 B 现状相同，pdfcam 文档 §5.5 已登记的已知缺口） | SDK 限制 |

---

## 3. 需要你拍板的决策点（每项附推荐）

| # | 问题 | A 做法 | B 现状 | 影响 | 推荐 |
|---|---|---|---|---|---|
| **D1** | 每位置**单 Loader**（无运行时多单元 fallback） | 一个位只建一个 Loader；配置里取权重最高/第一条非空 ID；加载失败不自动换下一个单元，等下次 `load()` 再试 | 一个位可配多个单元：同 session 重试 3 次后换下一单元 | 完全复刻 A = 放弃运行时 fallback，填充率可能略降（当前远端每位置只配 1 条，实际无差异） | ✅ 按 A 原样（完全复刻优先） |
| **D2** | TTL 是否生效 | `zisTimeOut()` 恒 false，`vksnx` 不生效 | `ad_timelimit` 已下发（3000s）且现逻辑生效 | 完全照 A 会让广告无限复用直到下次 load | ✅ 适配：AdMob 子类覆写 `zisTimeOut` 让 TTL 生效（保留 B 语义，属「酌情适配」） |
| **D3** | 频控体系换成 A？ | 分类型：open 50/50、inter 50/50、native 100/50、gap 1000ms，展示+点击都计数，跨天重置 | 全局 50 次/日（`ads_limit`）+ 插屏 60 秒节流 | 换 A 后：各类型独立上限、新增 gap；`ads_limit.ac_ads_limit` 失去作用；60 秒节流（SpPageAdTriggers）属于「时机」保留 | ✅ 换 A 的 AdCount*（这是 A 逻辑的一部分）；60 秒节流保留；`ads_limit` 字段作废（登记） |
| **D4** | 加载前置：网络/VPN 检查 | 页面在 load 前查 `NetUtil.isUseNet_noVpn()` + `xxxCanShow()` | manager 内查 SpBlockingLogic（总闸/VPN/日上限） | A 的前置检查放哪？B 页面不动 → 把 A 的检查放进 SpAdsHub 对外 API 内部（行为等价 A、位置不同）；B 的 5 总闸（global_block 等）A 没有 | ✅ gating 放 SpAdsHub API 内：`NetUtil(适配) + AdCount*.xxxCanShow()`；B 的 `SpBlockingLogic` 5 总闸保留（A 广告路径本来也没有全局买量闸——canShowAdsss_a2 只闸通知/评分） |
| **D5** | 广告埋点事件名 | A：`ad_request_op/ad_filled_op/ad_failed_op/ad_close_op/ad_click_op/ad_blocked_op` + TrackSDK `ad_close/ad_blocked/ad_show_timeout` | B：`Shark_ad_*` 规范（SpTracker）+ SpTrackEvents | 完全复刻 A = 换掉 B 埋点口径（BI 侧要同步改） | ✅ 照 A（调用点与事件名都照 A，TrackAd 原样）；若运营要求保 B 口径，TrackAd 内做事件名映射（一行改动），实施时二选一，默认照 A |
| **D6** | 开屏流程 | A：netCheck→并行加载 3 位→ecpm 比较→展示 | B：launch_min_ms/launch_wait_ms/catchup/热启动 | 用户说时机不变 | ✅ B 时序整体保留（A 的 runTasks/stepnext 不迁）；仅把底层「取开屏广告」换成 A 的池（`load(ac_launch)` + `loadSuccess(ac_launch)` + `show`） |
| **D7** | 原生广告渲染 | A：`fireAnative` 拿 TPNative，页面自己 addView | B：big/small 两种版式 + 关闭键行为（switch_open/jump_percent）+ 200ms 轮询等待 | 完全照 A 会丢掉 B 的版式与关闭键行为 | ✅ 加载=A（NatAdsLoad 池），渲染=B 现有版式层保留（关闭键/轮询是展示时机，不动） |
| **D8** | Banner | A 没有 banner 类型 | B 有 ac_recent_banner | 必须新增 | ✅ 新增 `BannerAdsLoad`：复刻 A 骨架 + AdMob AdView，不缓存（B 现行为） |
| **D9** | SpAdsHub 外壳 | A 无此层（页面直调 ImpressionUtils） | B 页面全部走 SpAdsHub API（fullScreen/native/banner/preloadLaunchAd/awaitOpenAd/presentLaunchAd/hasOpenAd/configGeneration） | 改页面接线=时机/页面大改 | ✅ 保留 SpAdsHub 对外 API 签名不变，内部实现从「SpAdCache + manager」切换为「A 的池」；B 页面代码一行不动 |
| **D10** | 删除清单 | — | SpAdCache/SpAdBean/SpFullScreenAdManager/SpNativeAdManager(渲染部分并入)/SpBannerAdManager | — | ✅ 删除；保留：SpAdPosition/SpAdInfo/SpAdConfig/SpBlockingLogic/SpUmpGate/SpWebWarmup/SpFullScreenMask/SpAdIncomeReport/SpAdSession/SpHotStartGate/SpLaunchAdCatchup/SpPageAdTriggers/SpAdDevFlags/SpAdShowConfig |

> 默认推荐=✅ 处。如果某项你选另一侧，告诉我编号即可。

---

## 4. 实施计划（确认 D1–D10 后执行）

### P1 复刻基础层（`com.mymy.mymy.ads`、`com.bockcock.adjust`、`com.buncead.common.util`）
- 原样：`AdsConf`、`ADManagerMy`、`AdCountOpen/Inter/Nati`、`BlockReason`、`TrackBean`(加 banner 分支)、`KvUtil`、`FirebaseAna`
- 适配：`TrackAd`（AdTrackingHelperMy→AdTrackingHelper；事件名按 D5）、`NetUtil`（VPN 语义映射）

### P2 复刻模型与骨架（`com.zppko.pdfplus.nor.ads`）
- 原样：`sett/AdsSetting`
- 适配：`helper/CodeKeyAndEvent`（17 位 + banner + admob）、`tool/BaseAdsLoad`（Activity 参数、TTL 访问器）、`ImpressionUtils`（B 协议解析、删 getEcpm）

### P3 AdMob 实现层（新增 `mob/adm/*` 封装 + 四个 Loader）
- 新增：`AdmobAdOpen/AdmobAdInter/AdmobAdNative/AdmobAdBanner`（复刻 A `trad/TradAd*` 的接口形状：getAD/init 加载/showAd/isReady/adSourceName）
- 适配：`mob/OpenAdsLoad/IntAdsLoad/NatAdsLoad`（骨架照搬）；新增 `mob/BannerAdsLoad`

### P4 接线（B 侧，页面零改动）
- `SpAdsHub`：内部切池——`fullScreen()/native()/banner()/preloadLaunchAd/awaitOpenAd/presentLaunchAd/hasOpenAd` 底层改为调 `ImpressionUtils`；gating 按 D4 放进 API 内部；`applyPayload` 后 `configGeneration` 通知照旧（A 的 `load()` 每次重读配置天然自愈晚到配置）
- `RemoteConfigHelper.getRecentData(KEY_ADS_CONFIG)` 已就绪（返回 `ac_ad_config`），无需新协议

### P5 清理 + 验证
- 删除 D10 清单；全量 `assembleDebug`；真机验收：开屏/插屏/原生/横幅四类 × 加载成功/失败/展示/关闭/点击埋点、TTL 过期、跨天频控归零、gap、60 秒节流、热启动、UMP、关闭键行为、开屏时序（launch_min/wait/catchup）不回归

### 迁移日志
每处「A 原始 → B 适配」按 §2 表格登记进提交说明（与保活迁移同一纪律）。

---

## 5. 风险与已知边界（提前告知）

1. **填充率**：D1 选 A 原样后，每个位只有一条链，加载失败无自动换位（等下一次页面 load 重试）。远端每位置配 1 条时无感知。
2. **埋点口径**：D5 若照 A，B 现有 `Shark_*` 事件会消失，BI 看板口径需同步。
3. **原生点击**：GMA NativeAd 无点击回调，`ad_click` 场景提醒少一路（B 现状已如此，pdfcam 文档已登记）。
4. **频控**：换 A 后 `ads_limit` 下发字段失效；A 的远程频控覆盖字段（pps_refer_data 里的 op_day_sh 等）B 协议没有，默认值兜底。
5. **VPN 语义**：A `op_vpn_limit=0`=开启拦截，B `vpn_config_switch=0`=关闭拦截，适配时以 B 的 `SpBlockingLogic.isVPNBlock` 为准。
6. **AdMob 一次性**：开屏/插屏展示后必须重建 load（A 代码里 `nowAd.loadAd()` 复用的写法对 AdMob 无效，属必要适配）。
7. **不迁清单**（避免误解）：TradPlus 全套、`ImpressExt/OmUtil`（UMP 保留 B 的 SpUmpGate）、`OpenRequest`（收入保留 B 的 SpAdIncomeReport）、`InterShCount`（60 秒节流保留 B）、A 的 `SpBootActivity` 开屏流程（保留 B 时序）、`ExcActivity`、`ConfigBean`。
