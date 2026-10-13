# 先读我

## 这是什么

`pdfcam` 的完整源码包。这个工程产出的包名是 **`com.scanpro.cam.pdf`**（线上包）。

里面装的是把竞品 ApolloPDF 1.0.1 的四块能力移植进来的代码：

1. 广告位编排（17 个位置、位置→单元 fallback、缓存 TTL、遮罩、原生关闭键行为）
2. 通知拉活（7 个触发场景、3 类通知、两套频控、重发循环）
3. 进程保活（前台服务 + JobScheduler 兜底 + 开机恢复）
4. 卸载挽留（桌面快捷方式 → 两步挽留页）

变现账号、配置下发走的仍是我们自己的，没有照抄竞品的账号。

## 包内容

```
pdfcam-移植代码-20261012/
├── ScanPro-CamToPDF/                  完整工程（可直接用 Android Studio 打开）
├── pdfcam 移植代码说明（给研发）.md     逐模块代码逻辑、埋点总表、与 Apollo 的差异、待办
├── pdfcam 移植功能装机验证清单.md       装机验证用，逐项打勾
└── README-先读我.md                   本文件
```

工程里的 `.git` 历史、`local.properties`（机器相关）、构建产物均已剔除。

## 构建前提

| 项 | 值 / 说明 |
|---|---|
| Gradle | 9.4.1（`gradle/wrapper` 已含，用 `./gradlew` 即可，不用另装） |
| Android Gradle Plugin | 9.2.1 |
| JDK | AGP 9.2.1 需要 JDK 17 或更高【假设，未在本机验证】 |
| compileSdk / targetSdk / minSdk | **37** / 36 / 26 → 本机需装 **Android SDK Platform 37** |
| 签名 | `cam.jks` 已含在包里（口令写在 `app/build.gradle.kts` 的 `signingConfigs`） |
| Firebase | `app/src/debug/google-services.json`（`com.test.lwapp`）与 `app/src/release/google-services.json`（`com.scanpro.cam.pdf`）**都已含**，不用另找 |
| `local.properties` | **未含**（里面是打包机器的 SDK 路径）。用 Android Studio 打开会自动生成；命令行构建需自己写一行 `sdk.dir=<你的 Android SDK 路径>` |

## 构建命令

```bash
./gradlew assembleDebug     # 出 com.test.lwapp（测试包名）
./gradlew assembleRelease   # 出 com.scanpro.cam.pdf（线上包名）
```

---

## ⚠️ 两条铁则，动手前先看

### 1. `com.scanpro.cam.pdf` 是线上包 —— 本地不能编译、不能运行、不能打包

不是「尽量别」，是铁则。任何情况下都不许在**这个工程的原样副本**上跑：

- `./gradlew` 的 `assemble` / `bundle` / `install` / `build` / `test` / `lint` / `sync`
- 用它的签名出 APK / AAB
- `adb install` / `adb shell am start` / Android Studio 的 Run

需要「编一下才能确认」的事，停下来提出来。

### 2. 要在本地跑，先建隔离后的测试仓

从这份源码复制一份出来，**逐项换掉下面这些线上生产标识**，换完之后才允许在本地编译运行。原样副本不动。

| # | 要改什么 | 在哪 |
|---|---|---|
| 1 | 线上包名 `com.scanpro.cam.pdf` | `app/build.gradle.kts` 的 `defaultConfig.applicationId` |
| 2 | 线上签名 | `cam.jks` 换成自己的测试签名文件与口令（`app/build.gradle.kts` 的 `signingConfigs.common`） |
| 3 | 线上 AdMob App ID `ca-app-pub-3840731205415196~4572255037` | `app/build.gradle.kts` release 的 `manifestPlaceholders["admobAppId"]` |
| 4 | 线上 Firebase 项目 `scanpro-cam-to-pdf-e2f41` | `app/src/release/google-services.json`（换成自己的测试项目，或改用 debug 那份的包名） |
| 5 | 桌面快捷方式的落点包名 | `app/src/release/res/xml/app_shortcuts.xml` 的 `intent.targetPackage`（debug 那份是 `com.test.lwapp`） |
| 6 | debug 变体包名 `com.test.lwapp`（若与自己的测试环境冲突） | `app/build.gradle.kts` 的 `androidComponents` 段 |

**不用改**：源码里**没有任何真实广告单元 ID**，全部由远端配置下发，本地一律为空。所以隔离时不存在「忘了换广告位」的风险。

---

## 本地未验证的事项（重要）

这份源码在打包前**没有编译、没有运行过**（原因见铁则 1）。已经做完的只是**静态核对**：

- 325 个文件齐全（含 gradle wrapper、签名、两份 `google-services.json`）
- 96 个 Kotlin 文件、97 个 XML 里，所有 `R.*` / `@drawable` / `@string` / `@layout` / `@id` 等资源引用都能在 `res/` 里找到
- `AndroidManifest.xml` 里注册的每个组件类都存在
- 工程里没有真实广告单元 ID 残留

**没有验到**的：能不能真的编过、跑起来什么表现。第一次构建若报错，属于预期内，按报错修即可。

另外，这份源码目前**跑起来看不到广告**——配置下发的接口地址还是空占位（`config/SpConfigWire.kt`），要等中台的接入文档。原因见说明文档 §11。
