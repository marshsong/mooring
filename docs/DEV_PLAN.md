# DEV_PLAN: Mooring 拴马桩 开发计划 (MVP v0)

来源: docs/PRD.md (唯一需求来源) + README.md。本文件是实施路线图,每个里程碑按"步骤、验收、证据"闭环。

## 0. 工作协议(贯穿所有里程碑)

- 复现先行:修 Bug 前必须先写 E2E 复现脚本,复现通过后再动手。
- 证据交付:每个里程碑完成必须附带 Evidence:单测/仪器测试输出、运行日志、屏幕截图。无证据视为未完成。
- 外科手术式修改:只改目标代码,不顺手重构无关区域;删除修改产生的孤儿代码。
- 架构优先:评估方案时不考虑开发工作量,优先最健壮、可扩展的方案。
- 许可证:每个新增 .kt / .java 文件顶部加 SPDX 头 `SPDX-License-Identifier: GPL-3.0-or-later` 与 `Copyright (c) 2026 <maintainer>`。
- 仓库卫生红线(最高优先级):`app/src/**` 中禁止 `com.tencent.mm`、`com\.tencent\.mm`、`视频号`、`plugin.finder`、`FinderHome`。检测特征只存在于用户自备订阅文件。代码/注释/README 中提到微信功能时一律用"超级 App 内嵌功能"等中性表述,不出现红线字符串。Mock 订阅只用 `com.example.*`。
- 决策标注:PRD 未覆盖的决策,在代码注释中标注 `// DECISION:`。

## 1. VPN 与多路径访问(用户强调,新增设计约束)

### 1.1 问题

PC 挂了 VPN(全隧道/TUN 模式,如 Clash TUN),手机无 VPN,两者在同一 Wi-Fi。VPN 可能劫持局域网流量或屏蔽 LAN,导致 PC 浏览器无法访问手机内嵌控制台 `http://<手机IP>:8765`。后续用户也可能存在同类情况(翻墙 VPN、公司 VPN、路由器 AP 隔离)。

### 1.2 访问路径设计(三路并行为一条主线)

| 路径 | 访问方式 | 适用场景 | 可达性 |
|---|---|---|---|
| A 局域网(主) | `http://<手机IP>:8765` | 正常网络,无 VPN 干扰 | 默认路径 |
| B USB 兜底(开发/受支持) | PC 执行 `adb forward tcp:8765 tcp:8765`,浏览器访问 `http://127.0.0.1:8765` | PC 挂了 VPN 或路由器 AP 隔离 | 100% 可达,走 USB 调试管道,绕过 Wi-Fi 与 VPN |
| C 手机热点 | 手机开热点,PC 连手机热点,访问 `http://192.168.x.1:8765` | 路由器 AP 隔离 / 双机不同网段 | 可达,手机即网关 |

### 1.3 由此新增的功能需求(并入对应里程碑)

1. 配对降级:手机引导页同时展示二维码与明文 Token 文本;控制台配对页支持"摄像头扫码"或"手动粘贴 Token"两种方式。原因:VPN 下 PC 连不上配对页,手动输入是唯一可行路径。
2. 连接诊断:控制台首页展示请求来源(局域网 IP 或 127.0.0.1,后者提示"USB 模式已生效");访问失败时手机引导页提供三步排障:关闭 VPN 的 LAN 拦截、改用手机热点、执行 adb forward。
3. 开发页(手机端):展示当前 IP、端口、以及可直接复制的 adb forward 命令。
4. FAQ(README + 控制台内):按常见 VPN 客户端给出 LAN 放行配置(如 Clash: TUN 模式为 192.168.0.0/16 加 DIRECT 规则;Windows 允许本地网络;关闭"仅 VPN 隧道"开关等)。
5. 配对与 UA 白名单不受影响:路径 B/C 请求到达手机时来源 IP 不同但 UA 相同,白名单按 UA 判定,三条路径共用同一配对。

### 1.4 边界

- 不做云中转、不做反向隧道,严格遵守 PRD"无云服务、仅局域网"。
- 路径 B 需要用户装有 adb(开发者默认具备),属于受支持的高级用法;MVP 不实现一键 adb,只在文档与开发页给出命令。

## 2. 前置准备

1. 确认工具链:JDK 17+,Android Studio(含 SDK Platform 34 / build-tools),Gradle 8.x,adb 可用;真机为华为(HarmonyOS/EMUI)USB 调试开启。
2. 新建 `android/` 工程目录,单模块 `app`(Kotlin 2.0 + Gradle Kotlin DSL,minSdk 26 / targetSdk 34)。
3. 建立 `.github/workflows/hygiene.yml`:对 `app/src/**` grep 红线字符串,命中即 fail;跳过 `docs/`、`README*`、`LICENSE`。
4. 建立分支纪律:每个里程碑一个分支 `m-vX`,合入 `main` 需附证据摘要。

## 3. 里程碑 M0:工程骨架 + 规则引擎 + T1 检测(只记日志不拦截)+ hygiene CI

### 步骤
1. 初始化 Gradle 工程,配置依赖:Ktor(server)、Room、DataStore、kotlinx-serialization、jsQR 不涉及(前端)。
2. 建数据模型(Kotlin data class,纯逻辑层,不依赖 Android):Target / TargetGroup / Rule / UsageDaily / EventLog / CooldownRecord / PairedClient / Subscription / detector_config。
3. 实现规则引擎(纯 Kotlin,可单测):加载目标/组/规则/订阅;判定"某目标此刻是否应拦截"(DAILY_QUOTA 每日 00:00 重置 / SCHEDULE_BLOCK 跨零点 / ALWAYS_BLOCK;组配额=成员求和,任一耗尽即拦;收紧立即生效)。
4. AccessibilityService 骨架:监听 `TYPE_WINDOW_STATE_CHANGED`,提取前台包名,与启用目标匹配;排除系统 UI/桌面/输入法/本应用(detector_config.json 提供,运行时补充默认启动器包名);计时精度 1 秒,每 10 秒落盘+离开落盘。此阶段只写 UsageDaily 与 EventLog,不弹勒马页。
5. assets 内置 `detector_config.json`(appCatalog 含视频类目录,无红线字符串)。
6. 单测:规则引擎合成逻辑、T1 包名匹配与排除清单、跨零点时段。
7. hygiene CI 落地并跑通。

### 验收
- PRD 验收 1 的"识别"部分:启用目录内应用后,前台进入能正确识别并累计用量(日志可查,不拦截)。
- 验收 11:hygiene CI 通过。

### 证据
- 单测输出(规则引擎全绿)、日志片段(前台包名识别与用量累计)、hygiene workflow 通过截图。

## 4. 里程碑 M1:T1 拦截 + 用量统计 + Room + 组配额

### 步骤
1. 接入 Room 持久化:UsageDaily(按 dateStr=yyyy-MM-dd 本地时区)、EventLog(保留 90 天,启动清理)、其余模型落库。
2. 勒马页执行器:全屏不透明覆盖 Activity(锁定触摸返回,excludeFromRecents),文案按触发原因(额度用完/禁用时段/永久禁用),底部一行"规则修改请前往电脑控制台",无任何宽限按钮。
3. 拦截触发:前台命中即判定,应拦则弹勒马页;3 秒后 `GLOBAL_ACTION_BACK`;若 5 秒后仍在目标前台则重弹,不设次数上限;服务侧 5 秒轮询兜底。
4. 组配额:组用量=成员当日用量之和,自身配额与组配额取更严者生效。
5. 拦截事件写 EventLog(时间戳/目标/触发规则 ID/原因)。
6. 仪器测试:验收 1、2、3 的自动化脚本。

### 验收
- PRD 验收 1:启用目录内应用设 1 分钟配额,用尽后 1 秒内弹出勒马页;次日 00:00 自动重置。
- 验收 2:未启用应用(系统 UI/桌面/输入法)混合使用 30 分钟零拦截。
- 验收 3:A/B 组配额共享与自身更严配额先生效。

### 证据
- 仪器测试输出、勒马页截图(三种文案)、用量/组配额日志、计时误差日志。

## 5. 里程碑 M2:T2 引擎 + 订阅加载/热更新 + Mock 全链路

### 步骤
1. 订阅解析与校验(JSON 可解析、包名合法、activityPatterns 可编译、至少一个 feature;任一失败整体拒绝,返回 SUBSCRIPTION_INVALID)。
2. T2 检测:仅处理存在已启用 FUNC 订阅的包名;去抖 `detectionDebounceMs=300`;一级窗口类名正则(activityPatterns)优先;二级节点树内容规则(titleKeywords / tabKeywords / requiredTabs / extraKeywords),遍历深度 ≤15、节点数 ≤500,超限放弃;节点遍历不在主线程。
3. 订阅导入热加载,无需重启服务。
4. 仓库提供 Mock 示例订阅(com.example.mocksuperapp)与对应 Mock 测试应用(模拟 Feed/Live 页面),供自动化和演示。
5. 自动化测试:进入 Mock Feed 600ms 内勒马页;Mock Live 永久禁用;控制台改订阅后热生效。

### 验收
- PRD 验收 4(T2 引擎自动化全链路)。

### 证据
- 自动化测试输出、Mock 应用进入拦截的计时日志、热更新前后行为对比日志。

## 6. 里程碑 M3:Ktor Web 服务 + 配对 + 目标/组/规则 API + 控制台基础版

### 步骤
1. Ktor embedded server:端口 8765,绑定 0.0.0.0,仅局域网;前台服务内守护协程崩溃自愈(5 秒退避)。
2. 令牌:首次启动生成 32 位随机 hex;除 `/api/pair` 与静态首页外,所有 API 校验 `X-Anchor-Token` 头。
3. 配对:`/api/pair` 校验 token+UA,注册 UA 白名单(最多 3 个);手机端只读:UA 不在白名单则写接口返回 403 MOBILE_READONLY。
4. REST API 全量按 5.1 表格落地(统一响应体 `{code,msg,data}`)。
5. WebSocket `/ws` 推送事件:USAGE_TICK(30s)/BLOCKED/COOLDOWN_UPDATE/RULES_CHANGED/TARGETS_CHANGED/FOCUS_UPDATE/SERVICE_STATUS。
6. 控制台前端(v0,assets 静态托管):配对页(jsQR 扫码 + 手动粘贴 Token 双入口)、应用页、组管理、规则页、状态展示。Chart.js 等前端库本地打包。
7. **接入第 1 节 VPN 设计**:引导页展示二维码+明文 Token;开发页展示 IP/端口/adb forward 命令;控制台首页连通性自检;三步排障文案。
8. 单元/仪器测试:鉴权、只读降级、各 API 主路径。

### 验收
- PRD 验收 6 的接口部分:配对成功、鉴权生效、只读端写操作 403、收紧立即生效。
- 多路径:PC 挂 VPN 场景下路径 B(adb forward)端到端可用。

### 证据
- API 测试输出(配好 token 与未配 token、PC UA 与手机 UA 对照)、WebSocket 事件日志、VPN 场景下用路径 B 打开控制台的截图。

## 7. 里程碑 M4:冷静期 + 专注模式 + 看板 + 默认规则 v2

### 步骤
1. 冷静期引擎:默认 10 分钟(5-30 可调);发起放宽→双端倒计时→到期 120 秒内"确认生效"→超时作废;同时刻仅一个进行中;取消随时可用。
2. 临时解锁:今日额外 +15 分钟,走冷静期,每日上限 2 次(可调)。
3. 专注模式:一键深度专注 N 分钟(默认 45),对全部启用目标临时 ALWAYS_BLOCK,立即生效;期间放宽请求返回 403 FOCUS_LOCKED;到期自动还原。
4. 看板:今日/7 天/30 天按目标与组用量图表、拦截次数与时间分布、事件流水(最近 50 条)、CSV 导出;前端本地打包。
5. 默认规则 v2:自动建"视频组",内置目录中已启用视频类目标自动加入;视频组 DAILY_QUOTA 45min + SCHEDULE_BLOCK 09:00-22:00;订阅中 alwaysBlock 的 FUNC 自动附 ALWAYS_BLOCK;未启用任何目标前引导页提示去电脑配置。
6. 端到端测试:验收 6 完整流程、9、10。

### 验收
- PRD 验收 6 完整(10 分钟冷静期 + 120 秒确认窗口 + 过期作废全流程)。
- 验收 9(专注模式期间放宽 403,到期还原)。
- 验收 10(默认规则 v2)。

### 证据
- 冷静期状态机测试输出、双端倒计时截图、专注模式 403 响应、看板图表截图、默认规则首次配置后视频组生效日志。

## 8. 里程碑 M5:保活强化 + 华为引导页 + 全量回归

### 步骤
1. 前台服务(`foregroundServiceType="specialUse"`,声明 `FOREGROUND_SERVICE_SPECIAL_USE` 权限)+ 常驻通知"Moored 守护中 · 今日视频组剩余 XX 分钟"。
2. BOOT_COMPLETED 自检:开机检查无障碍与 Web 服务,异常推高优先级通知(文案含"脱缰")。
3. 华为引导页:无障碍/启动管理(手动管理三项全开)/多任务加锁/通知权限,逐项自动回检红绿灯;全绿才算完成;末步展示配对二维码与明文 Token。
4. 运行自检:无障碍、电池白名单、通知权限、Web 服务四项红绿灯(并入开发者页)。
5. 全量回归:PRD 验收 1-11 逐项跑;`[MANUAL]` T2 真机自测(维护者自备订阅,功能页 1 秒内拦截,聊天/朋友圈/公众号 30 分钟零误伤,配置不入库)。

### 验收
- 验收 7(重启恢复)、验收 8(息屏 2 小时+前台 1 小时后进程存活,计时误差 <30 秒)、`[MANUAL]` 验收 5。
- 验收 1-11 全绿。

### 证据
- 重启恢复日志、存活测试计时误差对比、引导页红绿灯截图、`[MANUAL]` 自测记录、全量回归结果汇总。

## 9. 里程碑进度表

| 里程碑 | 主题 | 状态 |
|---|---|---|
| M0 | 骨架 + 规则引擎 + T1 识别 + hygiene CI | 已实现（待真机验收） |
| M1 | T1 拦截 + Room + 组配额 | 已实现（待真机验收） |
| M2 | T2 引擎 + 订阅 + Mock | 已实现（待真机验收） |
| M3 | Ktor + 配对 + API + 控制台基础版 + 多路径访问 | 已实现（待真机验收） |
| M4 | 冷静期 + 专注 + 看板 + 默认规则 v2 | 已实现（待真机验收） |
| M5 | 保活 + 华为引导 + 全量回归 | 已实现（待真机验收） |

## 10. 风险与应对(针对本计划的补充)

- VPN 干扰局域网(本计划已覆盖):路径 B/C 兜底 + 排障文案 + FAQ。
- Windows 开发 + 真机 HarmonyOS:adb 通过 USB 调试连接;无法识别的设备型号按标准 AOSP 处理。
- T2 真实订阅不可入库:所有 T2 验证用 Mock 订阅;真机自测由维护者自备,配置不入库,hygiene CI 兜底。
