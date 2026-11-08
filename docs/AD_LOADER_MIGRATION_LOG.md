# 广告加载内核复刻迁移日志（PDF260713_p01 → ScanPro-CamtoPDF）

> 原则：PDF26 代码尽量原样复制，只改必须适配 B 环境的地方。每处改动登记如下。

## 新增文件（来源）

| 文件 | 来源 | 方式 |
|---|---|---|
| `com/zppko/pdfplus/nor/ads/tool/BaseAdsLoad.kt` | PDF26 | 复制+适配（见 §适配）
| `com/zppko/pdfplus/nor/ads/ImpressionUtils.kt` | PDF26 | 复制+适配
| `com/zppko/pdfplus/nor/ads/mob/OpenAdsLoad.kt` | PDF26 | 复制+适配
| `com/zppko/pdfplus/nor/ads/mob/OpenRequest.kt` | PDF26 | 原样复制
| `com/zppko/pdfplus/nor/ads/mob/IntAdsLoad.kt` | 新增（SmartPdfScan 骨架 + PDF26 AdMob 模式） | 无 TrackAd/AdCount（对齐 PDF26）
| `com/zppko/pdfplus/nor/ads/mob/NatAdsLoad.kt` | 新增（SmartPdfScan 骨架 + AdMob AdLoader） | 同上
| `com/zppko/pdfplus/nor/ads/tool/OmUtil.kt` | PDF26 | 原样复制，**未接线**（B 用 SpUmpGate）
| `com/zppko/pdfplus/nor/ads/tool/Price.kt` | PDF26 | 复制+适配
| `com/zppko/pdfplus/nor/ads/sett/AdsSetting.kt` | PDF26 | 原样复制
| `com/zppko/pdfplus/nor/ads/helper/CodeKeyAndEvent.kt` | PDF26 | 复制+适配（B 的 17 位）
| `com/bockcock/adjust/FbHelp.kt` | PDF26 | 原样复制
| `com/bockcock/adjust/KvUtil.kt` | PDF26 | 复制+适配
| `com/mymy/mymy/ads/AdsConf.kt` | SmartPdfScan | 原样复制（D3 频控，PDF26 无此层）
| `com/mymy/mymy/ads/count/ADManagerMy.kt` | SmartPdfScan | 原样复制
| `com/mymy/mymy/ads/count/AdCountOpen.kt` | SmartPdfScan | 原样复制
| `com/mymy/mymy/ads/count/AdCountInter.kt` | SmartPdfScan | 原样复制
| `com/mymy/mymy/ads/count/AdCountNati.kt` | SmartPdfScan | 原样复制

## 适配点登记（原始 → 适配 → 原因）

| # | 位置 | A 原始（PDF26 或 SmartPdfScan） | B 适配后 | 原因 |
|---|---|---|---|---|
| 1 | ImpressionUtils.loadAdsSettingList | 解析 `{boxaxj,opxmlf,ujhscs,vksnx,gkqcx}`，位表 `cuu_launch` | 解析 B 协议 `{ad_unit_id,ad_paltfrom,ad_type,ad_timelimit}`；位表=17 位白名单；`zWeight=数组长度-下标`（合成，保降级链）；保留 `SpAdDevFlags.preferTestUnits()` 测试单元映射 | B 配置不动 |
| 2 | BaseAdsLoad.zShoweee / ImpressionUtils.show | `com.buncead.common.pdfpro.my.BaseActivity` + `act.isHide()` | `android.app.Activity` + `!act.isFinishing && !act.isDestroyed` | B 基类不同 |
| 3 | Price.qqq | `AppConfig.isBundle()` | `!BuildConfig.DEBUG` | B 无 AppConfig/IS_BUNDLE |
| 4 | Price.qqq | `AdJustHelp.sendRevenue(usd,currency)` | `AdTrackingHelper.trackAdRevenueAdjust(usd)` | B 的 Adjust 走自有通道（不复制 AdJustHelp，避免双初始化） |
| 5 | Price.qqq | `com.buncead.common.pdfpro.my.Util` | `com.buncead.common.util.Util` | B 包路径 |
| 6 | CodeKeyAndEvent | 5 位 + op/int/nat | B 17 位（SpAdPosition.ALL 值）+ op/int/nat/banner + `ddd_FIRM_ADMOB` | B 广告位与 SDK |
| 7 | KvUtil | `com.buncead.common.pdfpro.PdfApp.app` | `CamPdfApp.get()` | B 无 PdfApp |
| 8 | BaseAdsLoad import | `AdJustHelp/AppConfig/pdfpro.my.Util`（未使用） | 移除（B 无这些类） | 不存在的 import 无法编译 |
| 9 | IntAdsLoad/NatAdsLoad childLoad | — | 空 `zId` 显式 `zmyAdsLoadFFFF("")` | PDF26 OpenRequest 对空 ID 静默跳过会让加载标志卡死；B 无默认广告位 |
| 10 | ImpressionUtils | — | 回补 `fireAnative(place)`（SmartPdfScan 原方法） | PDF26 无原生，B 渲染层取用需要 |
| 11 | SpFullScreenAdManager | B 旧实现（SpAdCache + 重试3次 + manager 级状态） | 接线层：池 load/show + SpBlockingLogic 5 闸（去 50/日，改 AdCount*）+ SpAdShowGate + 500ms 遮罩；show 用 `whenOnVisible=false` | D3/D4/D9：公开方法签名不变，内部切池 |
| 12 | SpNativeAdManager | B 旧实现（AdLoader + SpAdCache） | 接线层：池 load + 200ms 轮询 + fireAnative + B 原渲染层（big/small/关闭键）；计数 `nati_addShowCount` 在渲染成功 | D3/D7/D9 |
| 13 | SpPageAdTriggers | 直读 SpAdCache + manager.showAd | `hasReadyAd` + `showFullScreenAd` 委托接线层；60 秒节流不变 | 缓存已删除 |
| 14 | SpBannerAdManager | `SpAdIncomeReport.sendAdIncome` | `Price.qqq(valueMicros, currencyCode)` | 收益统一对齐 PDF26；banner 其余保留 B |
| 15 | SpAdsHub | SpAdCache 读写 + 展示成功后补载 | 池 API（loadSuccess/load/show）；配置变更不再清缓存（池在 load() 重读，已加载广告按 TTL 过期）；展示后补载由池 show() 的 eager reload 承担 | PDF26 池语义 |
| 16 | CamPdfApp | — | `ADManagerMy.start(this)`（随启动恢复/重置频控计数） | D3 频控初始化 |
| 17 | 埋点 | B 的 Shark_* / SpTrackEvents.adBlocked/adShowTimeout / AdTrackingHelper life 会话（manager 层） | 删除（对齐 PDF26：加载/展示/关闭/点击无事件，仅日志）；页面层（SpBootActivity 等）保留不动 | D5 用户指令 |
| 19 | BaseAdsLoad.zDismiss / ImpressionUtils.show | `act.lifecycle`（PDF26 的 BaseActivity 是 AppCompatActivity） | `android.app.Activity` 无 lifecycle 属性 → `act as? LifecycleOwner` 判空后再挂 onResume 观察者，否则立即 finish | 参数类型适配（#2）的连带修复 |
| 20 | BaseAdsLoad/ImpressionUtils 日志 | PDF26 源码中池日志（`hhhh广告`）均为注释状态 | 解除原注释恢复真实日志（未改文案），作为真机验收证据基础 | 测试可观测性（真机验收发现无日志依据） |
| 21 | SpNativeAdManager.startCheckShowAd | B 旧实现 3 秒截断轮询 + 加载回调补渲染 | 池无完成回调 → 轮询持续到 loadSuccess/页面销毁（去掉 3 秒上限） | 真机验收发现晚到填充不渲染，修复后加载成功即渲染 |
| 18 | 频控 | `ads_limit.ac_ads_limit`（全局 50/日） | 惰性；改 AdCount* 分类型（open 50/50、inter 50/50、native 100/50 + gap 1000ms）；show 计数在接线层，click 计数不接 | D3（PDF26 无点击埋点） |

## 已知边界（登记不修）

- 展示失败/遮罩期间 TTL 过期时，接线层无法区分「已展示」与「未展示」（PDF26 的 zDismiss 不区分），60 秒节流时间戳可能被失败路径更新（影响极小）。
- GMA NativeAd 无点击回调：原生无点击计数/埋点（PDF26 同款，B 此前已知缺口）。
- 池覆盖旧 Loader 时不会 destroy 旧原生广告对象（A 家族原行为）；B 的 SpNativeAdManager.release/替换渲染时会 destroy 当前渲染对象。
- `ImpressionUtils.init()/OmUtil` 复刻未接线，UMP 仍由 B 的 SpUmpGate 承担。
- B 的 `DefaultConfigHelper.getDefAds()` 兜底为 A 家族格式，池解析后为空 → 配置不可用时无默认广告（B 原语义）。