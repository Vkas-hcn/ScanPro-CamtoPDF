# Android 后台功能迁移报告

## 结论与验证状态

本报告记录 A（SmartPdfScan）后台能力接入 B（ScanPro-CamtoPDF）的当前结果。后续本地检查已补齐构建记录、部分合并清单检查和 Pixel 8 真机通知测试。**本轮设备测试为 `OK (2 tests)`，属于有限范围验收，不能据此保证 Android 后台常驻或全部业务回归通过。**

| 项目 | 当前证据 |
|---|---|
| 后台类接入 | 已存在于 B 的 `com.zppko.pdfplus.nor` 源码包，`Zppkoo.onCreate` 由 `CamPdfApp` 调用 |
| 通知 UI 适配 | 真机断言确认通知实际发布；通知外观、折叠和深浅色尚未完成视觉验收 |
| 配置桥接 | 真机验证开关、解锁频控及声音/重复/延迟字段解析；未覆盖全部配置组合 |
| Manifest | 已检查 Debug 合并产物中的后台 Service/Receiver、FCM 及 Job 权限；Release 合并产物存在，未完成逐项审计 |
| 旧后台状态 | 有一次性清理器；固定身份清理有边界，未完成真实旧版覆盖升级测试 |
| debug/release JSON | 两份响应体和字段参考文档存在；本轮未新增 JSON parser 验证 |
| 编译/测试 | 已有终端记录显示 Debug、Release（排除 Crashlytics mapping 上传）、Debug Lint 及单测构建成功；Lint 报告 0 个 Error、255 个 Warning。修改后的设备测试包编译成功 |
| 设备行为 | Pixel 8 / Android 15，`com.npro.mac.fdp.debug` 1.0.3：后台通知验收和应用上下文测试 2/2 通过 |

### 本地 MCP 与本轮真机验收

已执行 `uv run artemis mcp --install all`，Cursor 配置写入 `C:\Users\vkas\.cursor\mcp.json`。已通过本地 ARTEMIS 获取截图及界面层级，不需要新增 API key；自主 AI 委派仍受其独立凭据检查限制。本轮使用本地 ARTEMIS 观测、ADB 操作及 AndroidJUnitRunner 执行，未委派自主 AI 任务。已安装并启用 ARTEMIS Accessibility Helper v6；缺少 scrcpy 的可选录屏能力未使用。

真机验收已通过以下检查：

- 启动配置就绪、启动页结束后回到首页。
- 前台服务启动，常驻通知 4856 发布；主动调用通知移除回调可补回通知。这里不是实际手势划除测试。
- 前台抑制召回，Home 后进入后台并自动发布普通/媒体通知。
- 解锁通知发送及频控、定时发送器、闹钟发送器、Home 延迟发送器；闹钟部分仅调用发送器，未验证系统到点交付。
- 远端关闭开关抑制发送；原始声音/重复/延迟字段解析。未实际计时验证分钟级延迟和重复次数。
- 各文件分类的路由参数覆盖旧导航参数；真实媒体通知 PendingIntent 触发后媒体通知清除。分类路由断言不代表每个分类页面都已完成 UI 验收。
- 应用上下文包名正确。

初轮设备执行被残留 UIAutomator 会话阻塞；释放会话后通过设备入口运行，避开 Windows 锁定的旧 Gradle 报告目录。测试发现并消除了两类干扰：启动真实配置覆盖临时配置，以及 Home 的并行延迟发送器与频控断言共用通知 ID。改动限于 `BackgroundAcceptanceTest.kt`：等待启动配置和首页就绪，重新应用临时配置，完成自动召回检查后取消并等待 Home 延迟任务，明确等待旧通知清除。业务源码未在本轮修改。

可复现执行（Windows PowerShell；设备上须已安装对应 Debug 应用及测试包，且不能同时运行 UIAutomator 观测）：

```powershell
adb -s 37311FDJH005D5 shell am instrument -w -r -e class com.scanpro.cam.pdf.BackgroundAcceptanceTest,com.scanpro.cam.pdf.ExampleInstrumentedTest com.npro.mac.fdp.debug.test/androidx.test.runner.AndroidJUnitRunner
```

须检查输出中的 `OK (2 tests)`，不能仅凭 ADB 的退出码判断断言通过。本轮执行耗时 18.164 秒，证据保存在 `C:\Users\vkas\.cursor\projects\c-Users-vkas-StudioProjects-ScanPro-CamtoPDF\terminals\511678.txt`。

ARTEMIS 界面观察另确认：引导页 Skip 可进入首页，首页可进入设置，设置页 Home 后以 MAIN/LAUNCHER 启动入口返回仍保留原页；对应日志记录了开屏机会及超时。扫描入口进入系统权限窗口，尚未完成相机授权、拍摄及生成 PDF。文件列表显示文件访问授权提示，阅读功能未验证。

## 迁移映射

| 职责 | B 当前落点 | 处理与边界 |
|---|---|---|
| 初始化和调度入口 | `CamPdfApp.kt`、`nor/Zppkoo.kt` | B 保留 WorkManager、AdMob、Adjust、TrackSDK 与既有统计初始化；完成旧状态清理后调用 A 后台初始化。不启动 A TradPlus、BI/RemoteConfig 引导或 A LifecycleUtil。 |
| A 原始后台核心 | `nor/RAction.kt`、`Route.kt`、`Zppkoo.kt`、`horus/`、`ss/`、`tool/`、`remote/` | 使用 A 的包名、类名和执行路径；按 B 环境适配资源、配置和 SDK 接口。目录内有 `HintExt01`–`HintExt05`、`HintExt04_lan`、`HintEx06`、`HintUtil`、`HintMt`、`HintSup`、`CallingSetting`、`UserPop`、`ChannelUtil`、`AbcLaunceJob`、`TChWorkerRec`、`WidgetPinnedReceiver`、`CCPdfToolService`、`PRePhoneBootRec`、`MFrMessageRec`、`PhotoLockRec`、`Tool`、`MKV`、`AppCacheUtil` 及 Remote Config 支撑类。此表为当前目录级映射，不是 A/B 文件字节差异报告。 |
| 配置与协议 | `config/`、`ads/`、`nor/horus/UserPopBridge.kt`、`nor/remote/` | 保持 B 的 `api/dispatch` 请求、响应及原始字段；桥接 `ac_t/ac_t_limit`→`cotTime/cotCount`、`ac_u/ac_u_limit`→`couTime/couCount`、`ac_a/ac_a_limit`→`coaTime/coaCount`，另映射免打扰时间。缺项用已记录的 A 桥接默认，不擅自开启服务端开关。三星普通/韩国设备选择和单位见配置参考。 |
| 通知样式与落点 | `nor/horus/HintNotifyViews.kt` 及 `HintExt01/HintMt/HintSup/CallingSetting` | A 触发逻辑使用 B 文案、图片和 B 页面入口参数；通知布局和展示参数有适配。系统通知的实际尺寸、系统折叠样式及点击端到端行为仍需真机验证。 |
| 启动与点击参数 | `boot/SpBootActivity.kt`、`notice/SpNotificationEntry.kt` | 保留 B 原启动、引导、广告与内容页路径；识别通知入参后在原启动流程结束时转发目标页面参数。通知权限回答后告知 Application 后台入口。 |
| B 旧后台 | `keepalive/`、旧发送/调度实现 | 删除重复发送链、B 独有持久周期与 D+1 召回逻辑；当前 `keepalive/` 下保留 `LegacyBackgroundCleanup.kt`。删除逐文件清单需以可运行 Git diff 核对，本环境无法取得。 |
| 共享通知业务 | `notice/SpNotificationEntry.kt`、`SpNotifyCatalog.kt`、`SpNotifyConfig.kt`、`SpNotifyCreator.kt`、`SpNotifyModels.kt`、`SpNotifySupport.kt`、`SpNotifyTheme.kt` | 这些 B 通知数据、内容路由或展示支持仍保留，不能因旧后台发送器删除而整目录移除。旧 layout 中的历史注释仍可见旧管理器名称，不代表存在活跃调用。 |
| A 的 Firebase 主题 | `nor/horus/HintEx06.kt`、`LegacyBackgroundCleanup.kt` | 新主题使用 A 已确认的 `smart_pdf`；清理器对 B 旧主题 `scanpro_pdf` 退订，并等待异步完成后才注册替代链。FCM 到达只记录事件，不由 B 旧代码主动拉起服务。 |
| Manifest | `app/src/main/AndroidManifest.xml` | Debug 合并清单包含 `AbcLaunceJob`、`CCPdfToolService`、`MFrMessageRec`、`PRePhoneBootRec`、`TChWorkerRec`、`WidgetPinnedReceiver`、`TextService`；`AbcLaunceJob` 受 `BIND_JOB_SERVICE` 保护。Debug target SDK 为 36；导出组件和全部目标 SDK 行为未完成审计。 |

## 启动时序与清理限制

1. `CamPdfApp` 启动时保留 B 的业务 SDK 初始化；旧任务本地清理和 `scanpro_pdf` 退订在后台新调度注册前执行。
2. 清理器校验组件身份后取消已知 Job ID，避免误取消同 ID 的其他服务；Application 对残留的 B `SpResidentLaunchJob` 再做身份复核。
3. 通知权限可能早于清理和后台初始化完成。权限就绪事件现在暂存，待 `Zppkoo.onCreate` 成功后启动前台通知，避免早到事件被丢弃。
4. A `Zppkoo` 初始化若中途抛出异常，本进程不重放可能已部分注册的回调，以免重复注册；这意味着部分失败需进程重启后再试，尚未在故障注入中验证。
5. 清理使用固定旧 Job、Alarm requestCode、通知 ID 和 Widget PendingIntent 身份。历史 A 随机 requestCode 闹钟无法通过当前固定列表可靠枚举；一次性清理成功不代表全部历史状态必然消失。清理器按清单保留通知 channel 和用户 channel 设置。
6. `USE_EXACT_ALARM`、`SCHEDULE_EXACT_ALARM` 与前台服务声明不能保证系统在所有版本、厂商策略或权限状态下允许后台启动；不得将迁移解释为系统强制常驻。

## 下发配置交付

- 完整字段语义、默认值、请求/响应格式、业务块、通知映射和未确认字段见 [`dispatch-config-reference.md`](dispatch-config-reference.md)。
- debug 响应示例：[`dispatch-config-debug.example.json`](dispatch-config-debug.example.json)。
- release 响应示例：[`dispatch-config-release.example.json`](dispatch-config-release.example.json)。
- 两份示例均是**非生产 schema/example**：广告单元 ID 是无效占位符，Facebook 字段为空；`code` 分别示例为 200 与 0。未读取或生成真实线上凭据、端点或广告密钥，不可直接作为投放配置部署。
- 受命令执行环境阻塞，不能报告 JSON parser 已通过；当前只能确认文件文本结构可读且示例值明确标为非生产占位。

## 验证缺口与限制

本次对 `CamPdfApp.kt`、`SpBootActivity.kt` 和 A 接入类的定向静态检查未发现编辑器诊断错误；B 旧发送器名称的源码命中集中在启动页历史注释、一次性清理器和遗留 layout 注释，没有发现其他业务类的活跃调用。该搜索不是完整编译器或全变体引用证明。

最初的命令执行环境曾受 Windows sandbox helper 阻断；这一历史阻塞已解除，本轮已取得工作区状态、构建记录及实际设备结果，见首节。本轮 Debug/Release 构建证据来自此前成功终端记录，测试源码修改后另行重编译了 AndroidTest APK；不能把此前因 mapping 上传导致的整条 Release 命令失败等同于源码编译失败。

仍未覆盖：完整 A/B 逐文件差异、两份示例的 JSON parser 校验、Release 清单逐项检查、通知权限拒绝、FCM 真实下行、系统闹钟到点交付、真实旧版本覆盖升级、通知外观与深浅色、多 Android 版本，以及 PDF/扫描/AdMob 完整业务回归。通知开关与解锁频控已在本轮临时本地配置下通过；不能外推为所有线上配置组合通过。随机身份旧闹钟和系统后台启动限制继续作为已知边界。
