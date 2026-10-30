# pdfcam 移植功能装机验证清单

对象：`project/pdfcam`（`com.scanpro.cam.pdf`，线上包，GP 在线）。照 ApolloPDF 1.0.1 移植的通知 / 保活 / 卸载挽留三块。
用途：本地只写不编不跑（铁则见 `.cursor/rules/online-package-no-build.mdc`），所以静态核对与装机核对分开：第一节是已经做完的静态结论，第二节交给拿到包的人逐条勾。

出处：`data/apks/apollo/`（反编译）、`version/version-history.md` v0.3.0。
改动（2026-10-12）：初版。补 7 语种挽留页文案。

---

## 一、静态核对（已做，不编译不运行）

| # | 核对项 | 命令 | 结果 |
|---|---|---|---|
| 1 | XML 良构：清单 + 8 份 `strings.xml` + 两份 `app_shortcuts.xml` | `xmllint --noout <file>` | 11/11 通过 |
| 2 | 挽留页文案齐全：8 个语种各 17 条 `sp_uninstall_*`，无重复 key | `rg -c 'name="sp_uninstall_' <file>` | 8×17，无重 |
| 3 | 无悬空引用：旧页、旧布局、旧图、旧文案 | `rg 'SpLeaveActivity\|SpLeaveFeedbackActivity\|activity_sp_leave\|bg_btn_outline_gray\|ic_feedback_top\|leave_system_failed\|openSystemLeave\|sp_nav_leave'` | 0 命中 |
| 4 | 布局 id ↔ 代码：代码里用到的 `binding.*` 都在布局里有同名 `@+id` | 对账 `binding\.\w+` 与 `@\+id/` | 单向全中 |
| 5 | 布局引用的 `@string/`、`@drawable/` 都存在 | 逐个 `rg` | 全存在 |
| 6 | 广告位常量：`AC_UNINSTALL1_INT/NAT`、`AC_UNINSTALL2_INT/NAT` | `rg UNINSTALL ads/SpAdPosition.kt` | 4 个都在 |
| 7 | 清单：`SpLeaveEntryActivity` `exported="true"`，两步页各一条，快捷方式 meta-data 挂在开屏上 | 读 `AndroidManifest.xml` | 符合 |
| 8 | 快捷方式两份：release `targetPackage=com.scanpro.cam.pdf`、debug `=com.test.lwapp`（debug 的 applicationId 被 `androidComponents` 改过） | 读两份 `app_shortcuts.xml` + `app/build.gradle.kts` | 一致 |
| 9 | 改动过的 Kotlin 括号配平 | 计数 `{} () []` | 6 个文件全 0 |
| 10 | 语种数与工程一致（无多余 locale 目录） | `ls res/values*` | 8 个：默认 + pt/de/es/fr/hi/in/ru |

---

## 二、装机核对清单（交给拿到包的人）

前置：

- [ ] 装包后**先正常启动一次** App。Android 的静态快捷方式要 App 跑过一次之后才由桌面索引出来，没跑过看不到「卸载」。
- [ ] 手机联网，并确认 App 拿到过一次远端配置。7 个通知场景在 `SpAdsHub.hasRemotePayload() == false` 时**一条都不发**，这是照 Apollo 的规矩，不是 bug。

### A. 桌面「卸载」快捷方式（本次新开的入口）

| # | 步骤 | 预期 |
|---|---|---|
| A1 | 长按桌面图标 | 出现「卸载」快捷方式，图标为 App 图标 |
| A2 | App 在前台，点快捷方式 | 清任务栈 → 回到开屏 → 首页 → 弹出第 1 步挽留页 |
| A3 | 先在 App 里翻几页，再点快捷方式 | 同样清栈回开屏再进挽留页（Apollo 就是这么设计的，用户会丢当前位置） |
| A4 | App 退到后台（按 Home）后点快捷方式 | 到第 1 步挽留页。**这条是加 `SpLeaveEntryActivity` 中转的原因**：直连开屏会被 `isTaskRoot` 判掉、静默无反应 |
| A5 | 杀掉进程后点快捷方式 | 到第 1 步挽留页 |
| A6 | 埋点里查这次开屏 | 场景码为 `ac_launch_uninstall`；另有一条 `Shark_ad_chance` 带 `scene` + `type` |
| A7 | 装 debug 包（applicationId `com.test.lwapp`）点快捷方式 | 能拉起 App，不报「应用不存在」 |

### B. 两步挽留页

第 1 步（`SpUnInstall01Activity`）：

- [ ] 进页立刻出现原生广告 `ac_uninstall1_nat`；同时预加载插屏 `ac_uninstall1_int`
- [ ] 顶部返回、两个问题卡里的 action、底部「Keep it」→ 都回首页
- [ ] 问题**卡片本体点不动**（只有卡内 action 能点）——Apollo 原样，不是漏挂
- [ ] 点「Uninstall」：有插屏缓存 → 展示，关掉后进第 2 步；无缓存 → **不等**，直接进第 2 步

第 2 步（`SpUnInstall02Activity`）：

- [ ] 进页原生位先隐藏，广告就绪后才显示（`ac_uninstall2_nat`）；同时预加载插屏 `ac_uninstall2_int`
- [ ] 三行原因：点整行、点左边方框都能切换勾选；勾选状态**不上报**（Apollo 也没报）
- [ ] 顶部返回、「Please reconsider…」按钮 → 都回首页
- [ ] 点「Uninstall」→ 底部确认框：贴底、左右两个按钮、点框外和按返回键都关不掉（不可取消）、背景变暗
- [ ] 确认框「Uninstall」→ 关框 → 插屏 `ac_uninstall2_int` → 关掉后跳系统「应用详情」页（不是直接卸）
- [ ] 确认框「Continue using」→ 关框 → 回首页

### C. 通知（7 场景 × 3 类）

前置：系统通知权限已给；`SpAdsHub` 拿到过配置。

| 场景 | 怎么触发 | 预期 |
|---|---|---|
| 解锁 | 灭屏再解锁 | 1.5 秒后发（这一条无延迟配置） |
| 闹钟 | 等 `AlarmManager` 到点（`SpAlarmReceiver`，无 intent-filter，只由内部排） | 发一条；发完自动重排下一次 |
| 定时 | App 启动后排的定时任务到点 | 发 |
| 广告点击 | 在插屏/全屏广告上点一下，然后**退到后台**（`SpAdSession` 消费一次后清零，所以同一轮只发一条） | 延迟 2 秒（`adClickDelay`）发「媒体式」那条 |
| Home 键 | 按 Home 退到桌面 | 延迟 3 秒（`homeDelay`） |
| 最近任务 | 按最近任务键 | 延迟 5 秒（`recentDelay`） |
| 无页面 | 最后一个页面销毁（一路按返回退出，或任务被清） | 延迟 3 秒（`exitDelay`） |

- [ ] 以上每个场景都收到通知；延迟秒数在拿不到远端配置时用上面的本地默认值
- [ ] 点**普通提醒**：进 App → 埋点 `system_notification_click`，带 `type`=触发场景
- [ ] 点**媒体式提醒**：进 App → 埋点 `media_notification_click`，**且那条通知自己消失**
- [ ] 点**常驻通知**（PDF/EXCEL/PPT 三入口）：进 App → 埋点 `always_notification_click`，无 `type`
- [ ] 三条点击无论哪条，都停掉通知重发循环（不再反复弹）
- [ ] 通知文案与 ID 每次轮换（连点两次同一场景，文案/ID 不同）
- [ ] 同一 ID 的重发：`pop_refresh` 每 2 秒重发一次、最多 30 次，用来把通知顶回状态栏最上面（`pop_refresh` 开关为 1 且屏幕亮着才刷）
- [ ] 「媒体式」那一条还会按 `media_config.intervalTime` 周期重发（默认 30 分钟）

### D. 保活

- [ ] 冷启 App 后约 5 秒（`delay(5000)`，避开开屏广告）挂出常驻通知，带 PDF/EXCEL/PPT 三个快捷入口
- [ ] 三个入口点进去分别到对应文件类型页（`jump_type` 区分）
- [ ] **Android 12 及以上：这一步只补一条常驻通知，不起前台服务**——这是 ApolloPDF 的原始分支（12 起后台起前台服务会被系统拦），不是 bug，别当缺陷报
- [ ] Android 11 及以下：常驻通知挂出时前台服务同时起来（通知栏能看到服务类型）
- [ ] 在最近任务里划掉 App：进程被回收后由 JobScheduler 零延迟任务（`ASSISTANCE_JOB_ID = 46555`）把前台服务拉回来
- [ ] 把常驻通知划掉：服务下次回来时补报一次 `always_notification_trigger` 埋点，并重挂通知
- [ ] 常驻通知是**独立一张卡片**，不会被折叠进「N 条通知」的 bundle 里（`setGroupSummary` 的作用）
- [ ] 设置 → 通知里只有一条常驻渠道 `channel_sp_persistent_high`，不该再出现旧的 `channel_sp_persistent`
- [ ] Android 12+ 起常驻通知应当**立刻**出现，而不是起服务后约 10 秒才冒出来（`FOREGROUND_SERVICE_IMMEDIATE`）
- [ ] 提醒发完约 8 秒后，常驻通知会被重新顶到**比它更早的通知之上**（ID 轮换刷新）。注意它**压不过刚响过的提醒**——这是系统排序行为，不是 bug，别当缺陷报
- [ ] **深浅色**：系统开深色模式后，普通提醒 / 媒体式 / 常驻三条通知的正文都要看得清（浅字压深底）；切回浅色模式是深字压浅底
- [ ] 重启手机：`BOOT_COMPLETED` 拉服务 + 重排闹钟
- [ ] 覆盖安装：`MY_PACKAGE_REPLACED` 同样重排
- [ ] 在系统设置里「强行停止」App：**不会**自启（Android 限制，Apollo 也一样），这一条只是确认没有意外行为

---

## 三、不能静态确认的（要装机才知道，或要 Black 定）

| # | 事项 | 为什么静态读不出来 |
|---|---|---|
| 1 | 以上全部条目 | 本地禁编译禁运行，只能装机看 |
| 2 | 7 语种挽留页文案质量 | 我按旧 `leave_*` 与 ApolloPDF 对应词条拼的，新译的有 4 条/语种（副标题、「Keep it」、确认框标题与正文）。**要人校**；德语、西班牙语的敬语/你称在 Apollo 原文里本身就不统一，校的时候一并定 |
| 3 | 远端配置的字段是否齐 | 挽留页与通知的开关、频控、延迟都在远端配置里（`SpNewConfig` / `SpAcPopConfig` / `SpPopRefresh` / `SpMediaConfig`），本地只有默认值。配置后台没这些字段的话，装机看到的是全默认行为 |
| 4 | 桌面快捷方式在个别 launcher 上的表现 | 各厂商 launcher 索引静态快捷方式的时机不同 |
| 5 | 若将来打开评分弹窗自动触发 | `SpRateFlow.AUTO_PROMPT_ENABLED` 现为 `false`，全关。开之前要给它补一条排除挽留入口的判断（`maybePromptOnHome` 已有，`maybePromptFirstLaunch` 没有），否则装机首次点快捷方式时评分弹窗会盖在挽留页上 |

---

## 四、相关文件

| 用途 | 文件 |
|---|---|
| 入口判定与中转 | `project/pdfcam/app/src/main/java/com/scanpro/cam/pdf/leave/SpLeaveEntry.kt`、`SpLeaveEntryActivity.kt` |
| 两步挽留页 | `.../leave/SpUnInstall01Activity.kt`、`SpUnInstall02Activity.kt` |
| 快捷方式 | `project/pdfcam/app/src/{debug,release}/res/xml/app_shortcuts.xml` |
| 挽留页文案 | `project/pdfcam/app/src/main/res/values*/strings.xml`（17 条 `sp_uninstall_*`） |
| 通知 | `.../notice/`（`SpTriggerManager`、`SpSendManager`、`SpNotifyCreator`、`SpNotifyConfig`） |
| 保活 | `.../keepalive/` |
| 变更记录 | `version/version-history.md` v0.3.0 |
