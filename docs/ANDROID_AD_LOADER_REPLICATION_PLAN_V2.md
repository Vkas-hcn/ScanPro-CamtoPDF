# 广告加载逻辑复刻计划 V2：三源混合策略（PDF260713_p01 为主）

> 结论先行：**PDF260713_p01 比 SmartPdfScan_luow 更合适，但两个源都要用。**
> PDF260713_p01（下称 PDF26）= 同一套 `com.zppko.pdfplus.nor` 框架的 **AdMob 变体**（开屏已用 AppOpenAd 实现、TTL 生效、失败自动换位、OnPaid 收益、内建 UMP）；
> 但它只实现了**开屏（op）一种广告**，没有插屏/原生/横幅，也没有频控（AdCount*）和广告埋点（TrackAd）层。
> SmartPdfScan_luow 有完整的插屏/原生骨架 + 频控 + 埋点，但 SDK 是 TradPlus。
> 所以：**骨架/开屏/收益/UMP 从 PDF26 拿，插屏/原生骨架与频控/埋点从 SmartPdfScan_luow 拿，横幅新增，广告位与配置保留 B 的。**

---

## 1. 为什么 PDF26 更合适（三源对比）

| 对比项 | SmartPdfScan_luow | PDF260713_p01 | B 的需求 | 谁更合适 |
|---|---|---|---|---|
| 框架 | 同款 nor 框架 | 同款 nor 框架（同一家族） | 完全复刻 A 逻辑 | 平手 |
| 广告 SDK | TradPlus（TPSplash/TPInterstitial/TPNative） | **AdMob（AppOpenAd）** | AdMob | ✅ PDF26 |
| 开屏实现 | TradPlus | ✅ AppOpenAd + FullScreenContentCallback + OnPaidEvent，**现成可用** | 开屏位 ac_launch | ✅ PDF26 |
| 插屏/原生实现 | TradPlus 骨架（IntAdsLoad/NatAdsLoad） | ❌ 没有（常量被注释） | 8 个插屏位 + 8 个原生位 | ⚠️ SmartPdfScan 骨架 + PDF26 的 AdMob 写法 |
| 横幅 | ❌ 没有 | ❌ 没有 | ac_recent_banner | ➕ 两个源都没有，新增 |
| TTL 过期 | ✗ 恒不生效（zisTimeOut=false） | ✅ 已生效（zTime×1000） | ad_timelimit 已下发 | ✅ PDF26 |
| 加载失败换下一单元 | ✗ 无（单 loader 死等） | ✅ 已内建：zmyAdsLoadFFFF → getNewLoadConfig(place, weight) 逐级降级 | 保持 fallback 能力 | ✅ PDF26 |
| 收益上报 | TradPlus ecpm（注释掉大半） | ✅ OnPaidEvent → Price.qqq（USD 换算 + 三路 + 累积） | 收益口径待定（见 D11） | ✅ PDF26（口径可选） |
| UMP | OmUtil（trad 版） | ✅ ImpressionUtils.init 内建 UMP（OmUtil） | B 已有 SpUmpGate | 平手（保留 B 或换 PDF26，见 D12） |
| 频控 | ✅ AdsConf + ADManagerMy + AdCountOpen/Inter/Nati（分类型日展示/点击/gap） | ❌ 完全没有 | 见 D3 | ✅ SmartPdfScan |
| 广告埋点 | ✅ TrackAd/TrackBean/ADBlockReason | ❌ 完全没有 | 见 D5 | ✅ SmartPdfScan |
| 配置来源 | BI dispatch（pps_ad_data） | Firebase Remote Config（pps_ad_data） | B 的 dispatch（ac_ad_config） | 都只复用「格式解析」思想，字段映射到 B 协议 |
| 应用层 | com.smart.pdf.scanner | com.buncead.common.pdfpro | B 的 com.scanpro.cam.pdf | 两者应用层都不迁 |

---

## 2. 三源分工总表（复刻清单）

### 2.1 从 PDF260713_p01 移植（原样或轻适配）★ 主体

| 文件 | 说明 | 适配点 |
|---|---|---|
| `nor/ads/tool/BaseAdsLoad.kt` | 加载骨架：**TTL 生效 + 失败自动换位 + sendValueToServer(AdValue)** | `BaseActivity`→B 的 Activity；`isHide()`→B 前后台判断；`AppConfig` 等 import 清理 |
| `nor/ads/ImpressionUtils.kt` | 池编排：canReloadAds 复用、show 前 eager reload、内建 UMP init | `loadAdsSettingList` 字段映射 B 协议；`init()` 的 UMP 段按 D12 决定接线 |
| `nor/ads/mob/OpenAdsLoad.kt` | 开屏：AppOpenAd 加载/展示/回调 | 几乎原样（B 的 Activity 类型） |
| `nor/ads/mob/OpenRequest.kt` | AppOpenAd.load 封装 + 收益转发 | 原样 |
| `nor/ads/tool/OmUtil.kt` | UMP 封装 | 若 D12 选保留 B 的 SpUmpGate 则不接线（登记） |
| `nor/ads/tool/Price.kt` | OnPaid 收益：UsdFromAdMicros 换算 + 三路 + 累积 | 按 D11：保留 B 口径则 sendValueToServer 内部改调 B 的 SpAdIncomeReport；换 PDF26 口径则需补 FbHelp/AdJustHelp 等价调用（B 已有 Adjust/Facebook 初始化） |
| `nor/ads/sett/AdsSetting.kt` | 配置模型 | 原样 |
| `nor/ads/helper/CodeKeyAndEvent.kt` | 位/类型/来源常量 | 位常量换 B 的 17 位（SpAdPosition.ALL）；补回 int/nat 类型常量（PDF26 注释掉了）；补 banner 类型；`ddd_FIRM_ADMOB="admob"` 启用 |

### 2.2 从 SmartPdfScan_luow 移植（PDF26 没有的部分）

| 文件 | 说明 | 适配点 |
|---|---|---|
| `nor/ads/mob/IntAdsLoad.kt` | 插屏骨架（childLoad/childShow/回调/AdCountInter/TrackAd 调用点） | TradPlus 调用按 PDF26 OpenAdsLoad 的 AdMob 模式改写：InterstitialAd.load + FullScreenContentCallback + OnPaidEvent→sendValueToServer；展示后对象失效→重建（PDF26 里 openAd=null 同款处理） |
| `nor/ads/mob/NatAdsLoad.kt` | 原生骨架（childLoad/getAdView/AdCountNati/TrackAd） | AdLoader 加载 NativeAd；渲染保留 B 的 big/small 版式层（D7） |
| `mymy/ads/AdsConf.kt`、`count/ADManagerMy.kt`、`count/AdCountOpen/Inter/Nati.kt` | 分类型日频控 | 原样（KvUtil 补齐） |
| `mymy/ads/poi/TrackAd.kt`、`TrackBean.kt`、`BlockReason.kt` | 广告埋点 | FirebaseAna 补齐或直接 FirebaseAnalytics；AdTrackingHelperMy→B 的 AdTrackingHelper；事件名按 D5 |
| `bockcock/adjust/KvUtil.kt`、`FirebaseAna.kt` | 依赖补齐 | 原样 |
| `buncead/common/util/NetUtil.kt`（可选，按 D4） | 网络/VPN 前置 | op_vpn_limit→B 的 vpn 开关（语义反转登记） |

### 2.3 新增（两个源都没有）

- `nor/ads/mob/BannerAdsLoad.kt`：复刻 A 骨架 + AdMob AdView，不缓存（B 现行为）。

### 2.4 保留 B 不动

- 17 个位 + `ac_ad_config` 下发（`SpAdPosition/SpAdInfo/SpAdConfig/SpConfigStore` 原样）
- 页面接线与全部时序：SpBootActivity 开屏时序（launch_min/wait/catchup）、热启动（SpHotStartGate）、补展示（SpLaunchAdCatchup）、60 秒节流（SpPageAdTriggers）、500ms 遮罩（SpFullScreenMask）、WebView 预热（SpWebWarmup）
- SpAdsHub 对外 API 签名（fullScreen/native/banner/preloadLaunchAd/awaitOpenAd/presentLaunchAd/hasOpenAd/configGeneration）——只换内部实现
- B 的收益/埋点通道（SpAdIncomeReport/SpTracker/AdTrackingHelper），按 D5/D11
- UMP（SpUmpGate），按 D12
- RemoteConfigHelper.getRecentData(KEY_ADS_CONFIG) 已返回 `ac_ad_config`，无需新协议

### 2.5 删除

- SpAdCache、SpAdBean、SpFullScreenAdManager、SpNativeAdManager（渲染并入）、SpBannerAdManager

---

## 3. 决策点（V2 更新）

| # | 决策点 | V1 状态 | V2 变化 |
|---|---|---|---|
| D1 | 加载失败是否换下一单元 | 需拍板（推荐照 A 无 fallback） | ✅ **自动解决**：PDF26 的 BaseAdsLoad 已内建「失败→getNewLoadConfig 按权重换下一单元」，直接复刻 |
| D2 | TTL 是否生效 | 需拍板（推荐适配生效） | ✅ **自动解决**：PDF26 已实现，直接复刻 |
| D3 | 频控换 A 的分类型（AdCount*） | 待确认 | ⏳ **仍需确认**（PDF26 无频控，此层只能来自 SmartPdfScan）。推荐：换 A |
| D4 | 加载前置网络/VPN 检查 | 待确认 | ⏳ **仍需确认**。注意：PDF26 的开屏流程连 netCheck 都没有（只有 UMP→load→等→show）；SmartPdfScan 有 NetUtil+AdCount 前置。推荐：B 页面时序不动，前置检查放 SpAdsHub API 内（NetUtil 适配 + xxxCanShow） |
| D5 | 埋点事件名照 A 还是保 B 的 Shark_* | 待确认 | ⏳ **仍需确认**。推荐：照 A（TrackAd 原样） |
| D6 | 开屏流程 | 保留 B 时序 | 不变（保留 B） |
| D7 | 原生渲染 | 加载=A 渲染=B | 不变 |
| D8 | Banner 新增 | 新增 | 不变 |
| D9 | SpAdsHub 外壳保留 | 保留 | 不变 |
| D10 | 删除清单 | 同 V1 | 不变 |
| **D11** | 收益上报口径 | — | 🆕 新增：PDF26 用 OnPaidEvent + UsdFromAdMicros 换算真实 USD + 三路 + 累积（total_ads_revenue_001）；B 现状是 SpAdIncomeReport（micros/1e6 原币种 + currency 硬编码 USD）。**推荐：调用点照 PDF26（OnPaidEvent→sendValueToServer），实现内部仍调 B 的 SpAdIncomeReport（口径不变）**；若你想顺便换成 PDF26 的 USD 换算口径也可以（B 已有 Adjust/Facebook 初始化，三路能实现） |
| **D12** | UMP 用谁的 | — | 🆕 新增：PDF26 的 OmUtil（框架内建）vs B 的 SpUmpGate（已在用）。**推荐：保留 B 的 SpUmpGate**（时机不变），PDF26 的 init()/OmUtil 复刻但 UMP 段不接线（登记） |

---

## 4. 实施步骤

- **P1（PDF26 主体）**：复刻 `BaseAdsLoad/ImpressionUtils/OpenAdsLoad/OpenRequest/OmUtil/Price/AdsSetting/CodeKeyAndEvent`，适配 B 的 Activity 类型与 17 位常量；`loadAdsSettingList` 字段映射 `boxaxj→ad_unit_id、opxmlf→ad_paltfrom、ujhscs→ad_type、vksnx→ad_timelimit、gkqcx→(缺省0)`。
- **P2（SmartPdfScan 补齐）**：`IntAdsLoad/NatAdsLoad` 骨架照抄、SDK 调用按 PDF26 模式改写；`AdsConf/ADManagerMy/AdCount*/TrackAd/TrackBean/BlockReason/KvUtil/FirebaseAna` 原样；新增 `BannerAdsLoad`。
- **P3（接线，B 页面零改动）**：SpAdsHub 内部切池（按 D3/D4 放入 gating）；UMP/收益按 D11/D12；`RemoteConfigHelper.getRecentData(KEY_ADS_CONFIG)` 沿用。
- **P4（清理+验证）**：删旧实现；`assembleDebug`；真机验收：四类广告 × 加载成功/失败换位/展示/关闭/点击、TTL 过期、跨天频控、gap、60 秒节流、热启动、UMP、关闭键、开屏时序回归。
- **迁移日志**：每处「源(文件) → B 适配」登记进提交说明。

---

## 5. 风险与边界

1. PDF26 是另一个应用（com.pdf.master.android.pdf）的代码，只取 `nor/ads` 与依赖类；应用层（pdfpro）、Firebase Remote Config 配置链不迁。
2. PDF26 的失败换位依赖「每条配置有 weight」；B 协议无 weight → 全部按 0（数组顺序即优先级），行为=B 现有 fallback 链顺序，可接受。
3. AdMob 开屏/插屏展示后对象失效：PDF26 已用 `openAd=null` 处理开屏；插屏/原生按同模式写（展示后置空、下次 load 重建）。
4. 原生广告无点击回调（GMA 限制）→ 原生 showClick 埋点收不到（B 现状已如此，pdfcam 文档已登记）。
5. 频控换 A 后 `ads_limit` 下发字段失效（D3 确认项）。
6. 依赖已齐：B 现有 tracksdk 3.0.3（含 UsdFromAdMicros）、play-services-ads、UMP、Adjust、Facebook —— 预计无需新增依赖。
