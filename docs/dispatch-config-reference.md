# 项目 B 配置下发字段说明

本文记录当前 B 客户端的实际解析，并非服务端完整契约。两份 `dispatch-config-*.example.json` 是独立的 `api/dispatch` **响应体**，均为非生产演示：广告单元 ID 为无效占位符、Facebook 字段留空；不可用于真实投放。示例不包含真实服务器、凭据或个人标识。debug/release 的示例值不代表环境实际配置。字段应由后台确认后使用，勿直接将示例部署至任何环境。

## 请求与调度

debug 与 release 使用相同的 `POST {baseUrl}api/dispatch` 路径、八项请求语义、JSON UTF-8 格式和响应解析规则；环境头分别为 `staging` 与 `prod`，构建资源中的八个请求字段名不同。`POST {baseUrl}api/cloak` 也使用同一请求体，但其业务成功码规则不同，不属于本示例响应。地址来自对应构建变体资源，必须以 `https://` 开头且以 `/` 结尾；请求字段名必须非空、互不重复且不能为 `REPLACE_*`。它们是协议字段名而非凭据。地址在此不展示，非生产地址示例可写作 `https://example.invalid/`。

公共请求头：`Accept: */*`、`Content-Type: application/json; charset=UTF-8`、`X-App-Id` 为构建包名、`X-App-Version` 为完整构建版本名、`X-Environment` 为 debug 的 `staging` 或 release 的 `prod`。

| 请求语义 | debug 字段名 | release 字段名 | 字符串值来源 / 缺省 |
|---|---|---|---|
| 包名 | `hne8oo` | `0348op` | 应用包名 |
| 版本 | `ke3har` | `4bq2wz` | 构建版本名中 `-` 前的部分，与请求头版本不一定相同 |
| Distinct ID | `1bfui7` | `yec72l` | 去控制字符、去首尾空格后最多 256 字符；空则不发送 dispatch |
| 广告标识 | `pz9zqb` | `i2zqnf` | 获取不到为空串 |
| 设备型号 | `b3dgej` | `kxuecn` | 空时 `unknown` |
| 安装来源 | `tsd589` | `6qqrie` | Play Install Referrer；获取失败为空串 |
| 语言 | `0ifdut` | `2094ym` | 当前语言标记 |
| 系统版本 | `y1ywr3` | `e8netx` | 空时 `unknown` |

八项均发送，非 Distinct ID 字段可为空。先应用有效缓存；配置刷新按最近一次请求尝试或成功时间节流 3 小时，一轮最多 3 次、每次最多 20 秒，失败回落缓存。`cloak` 的业务成功仅认 `code=200`，与 dispatch 不同。

## 响应外层与通用规则

`api/dispatch` 要求 HTTP 2xx 和 JSON 对象响应体。`code` 必须存在且非 null，按整数读取，`0` 或 `200` 成功；其他值或缺失失败。注意无法转成整数的非 null 值经宽松读取可能变为 `0`，不要依赖此容错，应明确发送整数 `0` 或 `200`。`msg` 为可选消息，缺失时读取 `message`，都缺省为空串。`data` 必需且非 null，可为 JSON 对象或内容为 JSON 对象的非空字符串；空对象 `{}` 可接受，数组、普通文本、空字符串不接受。下文路径都相对于 `data`。

除标为条件必需的广告条目字段外，各业务字段均可省略；未知字段忽略。业务块可为对象或对象的 JSON 字符串；其中广告位置必须是数组，不是 JSON 数组字符串。无块/字段时下列默认值适用，但总闸按其特殊规则处理。数字建议发 JSON 整数，布尔建议发 JSON 布尔；客户端部分读取允许转换，出现但无法按整数/长整数转换时可能得到 `0` 而不是默认值，不应依赖错误类型容错。空对象可令配置状态变为已就绪，但不提供广告位。广告配置每次应用以空配置为基础重新组装；普通业务块缺失重置为各自默认值。

## 元信息

| 路径 | 类型 | 默认 / 作用 |
|---|---|---|
| `facebook_app_id` | string | `""`；Facebook 初始化参数，非生产示例留空 |
| `client_token` | string | `""`；Facebook 初始化参数，非生产示例留空 |
| `isAcquiredUser` | boolean（兼容数字和字符串） | `false`；用于一次性买量/自然量标记；数字非零、字符串 `true`（不区分大小写）或 `1` 为真 |

## 广告配置

`ac_ad_config` 是位置到广告条目数组的对象，默认无位置。仅接受以下 17 个位置；未知位置忽略。数组按优先级组织；空数组或没有有效条目的位置不产生广告配置。两份完整响应示例中的空数组明确表示未提供该位置的有效广告，非空条目的 ID 仅为无效占位符。

| 位置 | 已知用途或类型 |
|---|---|
| `ac_launch` | 开屏 |
| `ac_language_int`、`ac_guide_int` | 语言、引导插屏 |
| `ac_file_int`、`ac_top_int`、`ac_check_int`、`ac_mainback_int` | 文件、顶部、检查、主页面返回插屏 |
| `ac_uninstall1_int`、`ac_uninstall2_int` | 两个卸载挽留页插屏 |
| `ac_main_nat`、`ac_setting_nat` | 主页面、设置原生广告 |
| `ac_recent_nat` | 已识别，但当前没有页面展示 |
| `ac_recent_banner` | Banner，实际挂载于首页 |
| `ac_uninstall1_nat`、`ac_uninstall2_nat` | 两个卸载挽留页原生广告 |
| `ac_language_nat`、`ac_guide_nat` | 语言、引导原生广告 |

每个数组项是对象：

| 字段 | 类型 | 必需 / 默认 / 作用 |
|---|---|---|
| `ad_unit_id` | string | 必须非空；否则该条丢弃。示例 ID 无效 |
| `ad_type` | string | 必须非空；否则该条丢弃。已知 `op` 开屏、`int` 插屏、`nat` 原生、`banner` 横幅；非空不等于能被对应管理器使用 |
| `ad_paltfrom` | string | 可选，默认空串；协议原拼写，不能擅改为 `ad_platform`；示例 `admob` |
| `ad_timelimit` | integer | 可选，秒；缺失、无效或小于等于 0 时为 600；位置缓存 TTL 使用列表首项 |

`ac_nat_config.switch_open`：integer，默认 `1`；`1` 显示原生广告关闭按钮，其他值隐藏。旧资料中的 `ac_nat_config.jump_percent` 目前不解析，不能假定有效。

### 开屏时序 `ad_show_config`

| 字段 | 类型 | 默认 | 作用 / 约束 |
|---|---|---:|---|
| `launch_min_ms` | long integer | 1500 | 开屏最短停留毫秒；限制到 0–3000 |
| `launch_wait_ms` | long integer | 8000 | 等广告上限毫秒；负数归 0，无上限 |
| `catchup_ms` | long integer | 20000 | 主页补展示等待窗口毫秒；负数归 0，无上限 |
| `preload_on_background` | boolean | true | 已有会话退后台时预加载 |
| `catchup_enabled` | boolean | true | 是否允许补展示 |

## 后台通知配置

以下字段都是可选 integer，整块缺失时使用表中默认值。除下文明确列出的限制外，B 的解析器不裁剪这些整数；建议发送非负的冷却、延迟和次数，小时边界发送 0–24。`ac_on` 在 B 场景过滤中为 0 时关闭，非 0 时放行；次数上限小于等于 0 时相关场景不会通过次数检查，负延迟不会产生等待。冷却检查在当前时间严格大于上次时间加冷却时才通过。延迟通知路径还要求已应用过有效远端或缓存配置；通知权限、前后台状态和总闸也影响实际发送。配置就绪不代表通知必定显示。

### `new_config`

| 字段 | 默认 | 说明 |
|---|---:|---|
| `ac_on` | 1 | 新通知策略总开关 |
| `ac_home` | 30 | Home 场景冷却，分钟 |
| `ac_home_limit` | 40 | Home 当日上限 |
| `home_delay` | 3 | Home 延迟，秒 |
| `ac_exit` | 30 | 无页面/退出场景冷却，分钟 |
| `ac_exit_limit` | 40 | 该场景当日上限 |
| `exit_delay` | 3 | 该场景延迟，秒 |
| `ac_recent` | 30 | 最近任务场景冷却，分钟 |
| `ac_recent_limit` | 40 | 该场景当日上限 |
| `recent_delay` | 5 | 该场景延迟，秒 |
| `ac_adclick` | 30 | 广告点击场景冷却，分钟 |
| `ac_adclick_limit` | 40 | 该场景当日上限 |
| `adclick_delay` | 2 | 该场景延迟，秒 |

### `acpop_config`

| 字段 | 默认 | 说明 |
|---|---:|---|
| `nopop_start` | 24 | 免打扰小时边界；客户端用它与 `nopop_end` 的较小/较大值构成可发送区间 `[min,max)`，区间外拦截；默认 24/6 时 0–5 点拦截 |
| `nopop_end` | 6 | 同上，小时边界 |
| `ac_on` | 1 | 旧通知策略开关 |
| `ac_t` | 30 | 定时场景冷却，分钟 |
| `ac_t_limit` | 30 | 定时场景当日上限 |
| `ac_u` | 5 | 解锁场景冷却，分钟 |
| `ac_u_limit` | 40 | 解锁场景当日上限；优先使用此拼写，也兼容旧拼写 `ac_u_Limit` |
| `ac_a` | 30 | 闹钟场景冷却，分钟；release 闹钟排期限制在 15–1440 分钟，debug 排期固定 1 分钟 |
| `ac_a_limit` | 40 | 闹钟场景当日上限 |

### 其他通知块

| 路径 | 默认 | 说明 |
|---|---:|---|
| `pop_refresh.pop_refresh_switch` | 1 | 整数，等于 1 且亮屏时启用重发，否则普通通知直接发送 |
| `pop_refresh.times` | 30 | 整数，一轮循环的总发送次数，包含第一次；最小 0。开关开启且亮屏时设为 0，该循环不会发送 |
| `pop_refresh.interval` | 2 | 整数，循环发送间隔，秒；最小 1 |
| `media_config.switch` | 1 | 整数，等于 1 时启用 media2，其他值关闭 |
| `media_config.intervalTime` | 30 | 整数，media2 节流间隔，分钟；最小 0 |
| `win_pop_config.first_interval` | 0 | 整数，已解析；当前未接入可确认的窗口行为，单位未确认 |
| `win_pop_config.jump_percent` | 0 | 整数，已解析并限制 0–100；当前未接入可确认的窗口行为 |
| `win_pop_config.winpop_jump_switch` | 0 | 整数，已解析；当前未接入可确认的窗口行为 |

## 广告与通知总闸

下列每个外层块均可为对象或对象 JSON 字符串。缺块、null 或内层字段缺失/null 时**保留当前值**，不是每次都重置。整个外层块无法解析成对象时采用“块错误回退”；内层错误类型经整数读取可能变成 0。初始值仅指进程初始状态。

| 外层.内层 | 类型 | 初始值 | 块错误回退 | 已确认行为 |
|---|---|---:|---:|---|
| `ads_limit.ac_ads_limit` | integer | 50 | 30 | 每日广告展示上限 |
| `global_block.global_block_switch` | integer | 1 | 1 | 与 Google Play 闸共同决定是否放行 |
| `google_play_block.google_play_block` | integer | 0 | 0 | 与总闸共同决定是否放行 |
| `vpn_config_switch.vpn_config_switch` | integer | 0 | 1 | 值为 1 且检测到 VPN 时拦截广告 |
| `virtual_block_switch.virtual_block_switch` | integer | 1 | 1 | 非 0 时隐藏虚拟按键 |

当前广告、系统通知和前台服务的共同放行条件为 `global_block_switch == 0` 或 `google_play_block != 1`；不应按字段名推断完整 A 端的用户或设备过滤语义。

## A 通知桥接及待确认事项

A 通知侧读取缓存的 B 原始 `data.acpop_config`，支持对象或对象 JSON 字符串，不改 B 的下发键，也不读取 A 风格的新响应字段。与上文 B 默认值不同，桥接逐字段独立取值；缺少或无法按整数读取时使用下表的桥接默认值。可读数字字符串仅兼容整数格式，建议统一发送 JSON 整数。桥接的 `ac_on` 仅当值等于 1 时开启，区别于 B 过滤器的非 0 开启。

| B 字段 | 桥接到 A 字段 | 桥接默认值：普通 / 韩国三星 | 单位 |
|---|---|---|---|
| `ac_on` | `coIsSwitch` | `true` / `true` | 开关；仅 1 为 true |
| `ac_t` | `cotTime` | 15 / 20 | 分钟 |
| `ac_t_limit` | `cotCount` | 80 / 60 | 次 |
| `ac_u` | `couTime` | 0 / 0 | 分钟 |
| `ac_u_limit`，兼容 `ac_u_Limit` | `couCount` | 80 / 60 | 次；小写优先 |
| `ac_a` | `coaTime` | 15 / 20 | 分钟 |
| `ac_a_limit` | `coaCount` | 80 / 60 | 次 |
| `nopop_start` | `freeTimeOne` | 23 / 23 | 小时 |
| `nopop_end` | `freeTimeTwo` | 6 / 6 | 小时 |

韩国三星按 A 现有选择规则：设备制造商为 `samsung` 且系统首选 locale 的国家为 `KR`，两者大小写不敏感；不满足时采用普通默认值。已下发的可读值优先于这些默认值，三星规则不覆盖它们。B 和 A 分别读取同一个块，缺字段时上述桥接默认与 B 默认可能不同；B 侧 `ac_a` 的排期裁剪不代表桥接值会被裁剪。

A 的 `isVoice=0`、`rpCount=0`、`rpVoiceCt=1`、`delayTm=0` 为桥接固定值，没有对应 B 下发字段；不要将这些 A 字段加入 B 协议并假定生效。

其余已知差异：原生 `ac_nat_config.jump_percent` 不读取；`win_pop_config` 仅解析，未接入可确认的窗口行为；广告与通知总闸未迁移买量、设备、用户维度；`ad_show_config` 为 B 可选时序块，未确认 A 是否有等价字段。普通媒体通知当前会跳过三星设备，不能据此推断 media2 也采用同样规则。`win_pop_config.first_interval` 的单位及实际服务端契约、线上广告 ID 待后台确认；未知字段被忽略，不应臆造映射。

主要依据：`config/SpConfigWire.kt`、`SpConfigRemoteSource.kt`、`SpConfigRepository.kt`、`SpConfigBootstrapper.kt`、`SpConfigStore.kt`，`ads/SpAdPosition.kt`、`SpAdShowConfig.kt`、`SpBlockingLogic.kt`，`notice/SpNotifyConfig.kt`、`SpNotifyFilter.kt`、`SpTriggerManager.kt`、`SpSendManager.kt`，以及 `nor/remote/RemoteConfigHelper.kt`、`nor/horus/UserPopBridge.kt`、`nor/ads/tool/UserParms.kt`。如果源码和旧文档不一致，以当前源码为准。
