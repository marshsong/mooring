\# PRD：Mooring 拴马桩 — 跨端屏幕自律控制器（MVP v0）



\## 0. 文档说明（给 Codex 的前置指令）



\- 本文档为唯一需求来源。按里程碑顺序开发，每个里程碑需满足对应验收标准后才进入下一个。

\- 技术栈固定：\*\*Kotlin + minSdk 26 + targetSdk 34\*\*，单模块 Android 应用，控制台前端打包于 assets。不引入云服务、不开发原生桌面客户端。

\- Web 服务使用 \*\*Ktor embedded server\*\*；存储使用 \*\*Room + DataStore\*\*。

\- 许可证 \*\*GPL-3.0\*\*：仓库已含 LICENSE 文件；每个新增 `.kt` / `.java` 文件顶部添加 SPDX 头：`SPDX-License-Identifier: GPL-3.0-or-later` 与 `Copyright (c) 2026 <maintainer>`。

\- \*\*仓库卫生红线（法律防火墙，最高优先级）\*\*：`app/src/\*\*`（含代码与 assets）中禁止出现以下任何字符串：`com.tencent.mm`、`com\\.tencent\\.mm`、`视频号`、`plugin.finder`、`FinderHome`。特定应用的识别特征只能以"订阅文件"形式由最终用户在自己设备上导入；仓库内仅提供通用引擎与 Mock 示例订阅。M0 需建立 CI 检查（`.github/workflows/hygiene.yml`，grep 上述字符串，命中即 fail）。`docs/`、`README\*`、`LICENSE` 不在检查范围。

\- 遇到本文档未覆盖的细节，按"最大化防作弊、最小化手机端操作入口、通用引擎优先于特定应用"原则自行决策，并在代码注释中标注 `// DECISION:`。



\## 1. 产品概述



\### 1.1 背景与问题



短视频产品利用人性弱点设计，独立 App（抖音、B 站、YouTube 等）可以靠卸载解决；但超级 App（如微信）因工作原因不可卸载，其内嵌信息流（如视频号）入口无法真正关闭。现有屏幕自律产品的共同缺陷：\*\*钥匙和锁在同一台设备上\*\*——规则存在手机里，冲动上头时用户随时可以自己关掉。



\### 1.2 产品定位



Mooring（拴马桩）是一套\*\*权限物理分离\*\*的屏幕自律工具：



\- \*\*手机是执行器\*\*：通过无障碍服务检测并拦截用户指定的目标（任意 App / 超级 App 内嵌功能），手机端不提供任何规则修改入口；

\- \*\*电脑是控制台\*\*：所有规则在电脑浏览器中管理（手机内嵌 Web 服务，局域网访问）；

\- \*\*双层能力\*\*：T1 应用级（任意 App，包名匹配，永不失效）+ T2 功能级（超级 App 内嵌页面，订阅驱动，特征热更新）；

\- \*\*主张配额，不主张戒断\*\*：被卸载的 App 可以装回来放进共享组额度。



\### 1.3 目标用户（MVP 仅一人：开发者本人）



华为手机用户（HarmonyOS 4.x / EMUI）；日常主要工作设备为 PC/Mac；需要限制自己的短视频使用但不戒断通讯。



\### 1.4 核心价值主张



> 规则在电脑上，钥匙不在手边。只关该关的，不动该留的。



\## 2. 术语表



| 术语 | 定义 |

|---|---|

| 目标（Target） | 被管控的实体。三种形态：`APP:<package>`（T1，整应用）、`FUNC:<package>:<featureId>`（T2，应用内功能，来自订阅）、`GROUP:<groupId>`（组，作为规则作用对象） |

| T1 / T2 | 应用级检测（前台包名匹配）/ 功能级检测（窗口类名正则 + 内容关键词，订阅驱动） |

| 规则（Rule） | 作用于目标的限制策略：每日限额 / 时段禁用 / 永久禁用 |

| 应用组（TargetGroup） | 多个目标共享一份每日配额；组内任一目标的消耗都计入组配额 |

| 拦截（Block） | 目标命中"应拦截状态"时执行的强制打断 |

| 勒马页（Rein Page） | 全屏拦截覆盖页，产品对外统一叫法 |

| 冷静期（Cooldown） | 放宽规则的强制等待延迟，期间修改不生效 |

| 收紧 / 放宽 | 收紧=降低配额/新增禁用/启用目标（立即生效）；放宽=提高配额/解除禁用/停用目标（需冷静期） |

| 订阅（Subscription） | 用户导入的 T2 检测特征文件（JSON），定义某超级 App 内各功能的识别规则 |

| 控制台（Console） | 手机内嵌 HTTP 服务托管的 Web 页面，供电脑浏览器访问 |

| 配对令牌（Token） | 控制台鉴权凭据，仅通过二维码分发给电脑浏览器 |

| 已拴牢 / 脱缰 | Moored：服务正常工作中 / Adrift：监测服务掉线（用于状态与通知文案） |



\## 3. 系统架构



\### 3.1 组成（单应用内四部分）



1\. \*\*规则引擎（核心）\*\*：加载目标、组、规则、订阅，判定"某目标此刻是否应被拦截"，输出判定结果与配额状态。

2\. \*\*无障碍监测服务（AccessibilityService）\*\*：T1 前台包名检测 + T2 订阅驱动的页面检测，计时，触发拦截。

3\. \*\*勒马页执行器\*\*：全屏覆盖 + 可选自动返回。

4\. \*\*内嵌 Web 服务（Ktor）+ 控制台前端\*\*：托管静态页面与 REST/WebSocket API，仅监听局域网。



\### 3.2 架构决策（已定，不要更换）



1\. 控制台前端由手机端静态托管（assets），电脑浏览器访问 `http://<手机IP>:8765`。"电脑控制"= 电脑上的浏览器，MVP 不做桌面客户端。

2\. \*\*通用引擎 + 数据驱动\*\*：引擎代码不含任何特定应用特征。T1 目标来自内置目录/用户添加的包名；T2 目标只来自用户导入的订阅文件。

3\. 检测的通用参数（去抖、排除包、目录）存放于 assets 内置 `detector\_config.json`，可经控制台更新。

4\. 仓库卫生红线见第 0 节，CI 强制。



```

电脑浏览器 ──局域网──► Ktor (:8765) ──► 规则引擎（Target/Group/Rule/Subscription）

&#x20;                           │                     ▲

&#x20;                           │ WebSocket            │ 判定查询

&#x20;                           ▼                     │

&#x20;                    控制台前端(assets)   AccessibilityService

&#x20;                                         ├── T1：前台包名匹配

&#x20;                                         └── T2：订阅特征匹配

&#x20;                                                  │

&#x20;                                                  ▼

&#x20;                                            勒马页（全屏拦截）

```



\## 4. 功能需求



\### 4.1 检测



\#### F1 T1 应用级检测

\- 监听 `TYPE\_WINDOW\_STATE\_CHANGED`，取前台包名，与已启用的 `APP:` 目标集合匹配。

\- 排除：系统 UI、桌面启动器、输入法、本应用自身（排除清单由 `detector\_config.json` 提供，运行时自动补充默认启动器包名）。

\- 计时：前台包名命中即累计，精度 1 秒；每 10 秒落盘 + 离开时落盘，防进程被杀丢数据。

\- 性能要求：单次事件处理 ≤ 50ms；不做任何节点树遍历。



\#### F2 T2 功能级检测（订阅驱动）

\- 仅当某包名存在\*\*已启用的 FUNC 订阅\*\*时，才处理该包的 `TYPE\_WINDOW\_CONTENT\_CHANGED`（去抖 `detectionDebounceMs=300`）；其余包的事件全部丢弃。

\- 两级判定，命中任一级即认为处于该功能页面：

&#x20; - 一级（优先）：窗口组件类名匹配订阅中的 `activityPatterns` 正则；

&#x20; - 二级（兜底）：节点树内容匹配 `contentRules`（标题关键词 + Tab 关键词）；遍历深度 ≤ 15、节点数 ≤ 500，超限放弃本次二级检测。

\- 订阅导入后热加载，无需重启服务。

\- 订阅文件格式见第 5.4 节；仓库内提供 Mock 示例订阅（`com.example.mocksuperapp`）供开发与自动化测试。



\#### F3 规则引擎与多规则合成

\- 规则类型与参数：



| 类型 | 参数 | 语义 |

|---|---|---|

| `DAILY\_QUOTA` | `quotaMinutes` | 每日 00:00（本地时区）重置 |

| `SCHEDULE\_BLOCK` | `startHHmm`, `endHHmm` | 时段内直接拦截（支持跨零点） |

| `ALWAYS\_BLOCK` | 无 | 永久禁用 |



\- 动作 `action`：`OVERLAY\_ONLY`（仅覆盖）/ `OVERLAY\_AND\_BACK`（覆盖+自动返回，默认）。

\- 合成规则：同一目标多条规则任一触发即拦截；对 `APP:`/`FUNC:` 目标，其自身配额与所属组配额同时生效，\*\*任一耗尽即拦截\*\*；组配额消耗 = 组内所有成员当日用量之和。



\#### F4 拦截执行（勒马页）

\- 触发条件（满足其一）：当日用量 ≥ 配额（自身或组）；处于禁用时段；`ALWAYS\_BLOCK`。

\- 动作序列：弹出全屏勒马页（不透明主题，锁触摸返回）→ 3 秒后执行一次 `GLOBAL\_ACTION\_BACK` → 若 5 秒后仍检测到目标前台，重复覆盖+返回，不设次数上限。

\- 勒马页文案（按触发原因）："今日额度已用完，剩余可申请时间：明日 00:00" / "当前为禁用时段" / "此功能已永久禁用"；底部一行小字："规则修改请前往电脑控制台"。\*\*不得出现任何宽限/继续使用按钮。\*\*

\- 勒马页 `excludeFromRecents`；服务侧 5 秒轮询兜底重弹。

\- 拦截事件写入事件日志（时间戳、目标、触发规则 ID、原因）。



\#### F5 内嵌 Web 服务

\- 端口固定 `8765`，绑定 `0.0.0.0`，仅局域网可达；崩溃由前台服务内守护协程自动重启（5s 退避）。

\- 鉴权：除 `/api/pair` 与静态首页外，所有 API 需携带请求头 `X-Anchor-Token`。令牌为 32 位随机 hex，首次启动生成，仅经二维码展示。

\- \*\*手机端只读\*\*：已配对设备的 UA 白名单存于服务端；UA 不在白名单的客户端（含手机自带浏览器）只渲染只读页，写接口返回 `403 MOBILE\_READONLY`。最多 3 个已配对浏览器。

\- WebSocket `/ws` 推送实时事件（见 5.3）。



\#### F6 手机端 UI（刻意极简，3 个页面）

1\. \*\*引导页\*\*：三步——开启无障碍（跳系统页+回检）、保活设置指引（见 7.2，逐项回检红绿灯）、展示配对二维码。

2\. \*\*状态页\*\*：服务状态（已拴牢/脱缰）、今日各目标与组的剩余额度、生效规则摘要。\*\*无任何编辑控件。\*\*

3\. \*\*开发者页\*\*（连点版本号 7 次激活）：导出检测日志、粘贴/上传订阅、查看原始事件、展示当前 IP 与端口。



\#### F7 保活机制（华为专项）

\- 前台服务（`foregroundServiceType="specialUse"`）+ 常驻通知："Mooring 守护中 · 今日视频组剩余 23 分钟"。

\- `BOOT\_COMPLETED` 自检：开机检查无障碍与 Web 服务状态，异常则推送高优先级通知（文案含"脱缰"字样）。

\- 应用内"运行自检"：无障碍权限、电池白名单、通知权限、Web 服务四项红绿灯。

\- 引导用户：设置 → 应用和服务 → 应用启动管理 → 手动管理三项全开；多任务卡片下拉加锁。



\### 4.2 电脑端（Web 控制台）



\#### F8 页面与功能

1\. \*\*配对页\*\*：jsQR 摄像头扫码（二维码内容 `{"ip","port":8765,"token","deviceName"}`），全程本地，token 存 localStorage，同时注册浏览器 UA 白名单。

2\. \*\*应用页\*\*：`GET /apps/installed` 列出手机已安装的可启动应用（包名+名称），支持勾选启用；叠加内置目录（见 `detector\_config.json` 的 `appCatalog`）一键启用；支持手动输入任意包名。每项可设独立配额与时段。另设"已安装但未管控"提醒区（防组配额被绕过）。

3\. \*\*组管理\*\*：创建组、添加成员目标、设置组配额；组配额与成员自身配额并行生效（取更严）。

4\. \*\*规则页\*\*：按目标/组列出规则，新增/修改/删除；收紧立即生效，放宽走冷静期。

5\. \*\*冷静期\*\*（默认 10 分钟，可在 5–30 分钟调整）：发起放宽 → 双端显示倒计时 → 到期后 120 秒内点击"确认生效"才应用，超时自动作废；同时刻仅允许一个进行中冷静期。

6\. \*\*临时解锁\*\*：申请"今日额外 +15 分钟"，走冷静期，每日上限 2 次（可在设置调整）。

7\. \*\*订阅页（T2）\*\*：粘贴或 URL 导入订阅 JSON → 结构校验（`400 SUBSCRIPTION\_INVALID`）→ 列出其中的 FUNC 目标，逐个启用/停用并设规则；显示订阅版本。

8\. \*\*专注模式\*\*：一键"深度专注 45 分钟"——对全部已启用目标执行临时 `ALWAYS\_BLOCK`（收紧，立即生效），倒计时显示于页眉，期间一切放宽请求返回 `403 FOCUS\_LOCKED`，到期自动还原。

9\. \*\*看板\*\*：今日/7 天/30 天按目标与组的用量图表（Chart.js 本地打包）、拦截次数与时间分布、事件流水（最近 50 条）、CSV 导出。



\#### F9 默认初始规则（v2，开箱即用）

\- 自动创建组"视频组"：内置目录中\*\*已被用户启用\*\*的视频类 App 目标自动加入；若存在已启用的 FUNC 目标（来自订阅）且用户勾选加入，同样纳入。

\- 视频组：`DAILY\_QUOTA 45min` + `SCHEDULE\_BLOCK 09:00–22:00`。

\- 订阅中标记 `alwaysBlock: true` 的 FUNC 目标（如直播类）：启用时自动附 `ALWAYS\_BLOCK`。

\- 未启用任何目标前，引导页提示用户前往电脑完成首次配置。



\## 5. 通信协议与数据格式



\### 5.1 REST API（前缀 `/api`，除标注外均需 Token）



| 方法 | 路径 | 说明 |

|---|---|---|

| POST | `/pair` | 入参 `{token, userAgent}`；校验通过注册 UA 并返回 `{deviceName, paired:true}` |

| GET | `/status` | 服务状态、无障碍开关、电量、今日各目标/组用量、生效规则、进行中冷静期 |

| GET | `/apps/installed` | 已安装可启动应用列表（包名+标签），供应用页选择 |

| GET | `/catalog` | 内置应用目录 |

| GET / POST | `/targets` | 已启用目标列表 / 启用目标（=收紧，立即生效） |

| DELETE | `/targets/{id}` | 停用目标（=放宽，触发冷静期） |

| GET / POST | `/groups` | 组列表 / 建组 |

| PUT / DELETE | `/groups/{id}` | 改组 / 删组（若构成放宽则触发冷静期） |

| GET / POST | `/rules` | 规则列表 / 新增（放宽类参数触发冷静期） |

| PUT / DELETE | `/rules/{id}` | 修改 / 删除（放宽触发冷静期） |

| POST | `/cooldown` | 发起放宽冷静期，返回 `{cooldownId, expiresAt, changePreview}` |

| POST | `/cooldown/{id}/confirm` | 到期窗口内确认生效 |

| POST | `/cooldown/{id}/cancel` | 取消（随时可取消） |

| POST | `/focus` | 入参 `{minutes}`，立即收紧全部目标 |

| GET / POST | `/subscriptions` | 订阅列表 / 导入（body 为订阅 JSON 文本） |

| PUT | `/subscriptions/{id}` | 启用/停用订阅（停用=放宽） |

| GET | `/usage?days=n` | 用量序列（含组聚合） |

| GET | `/events?limit=50` | 事件流水 |

| POST | `/detector-config` | 上传通用检测配置（校验后热加载） |



\*\*统一响应体\*\*：`{code: 0, msg: "ok", data: {...}}`；错误码：`400 SUBSCRIPTION\_INVALID` / `401 TOKEN\_INVALID` / `403 MOBILE\_READONLY` / `403 FOCUS\_LOCKED` / `404 NOT\_FOUND` / `409 COOLDOWN\_ACTIVE` / `410 COOLDOWN\_EXPIRED`。



\### 5.2 放宽请求示例



```json

POST /api/cooldown

{

&#x20; "type": "RULE\_UPDATE",

&#x20; "ruleId": "r-001",

&#x20; "patch": { "quotaMinutes": 60 },

&#x20; "reason": "optional\_user\_note"

}

→ 202 { "code":0, "data": { "cooldownId":"cd-123", "expiresAt": 1760000000000,

&#x20;      "changePreview": { "field":"quotaMinutes", "from":45, "to":60 } } }

```



\### 5.3 WebSocket 事件（`/ws`）

`USAGE\_TICK`（每 30s，各目标与组剩余秒数）、`BLOCKED`、`COOLDOWN\_UPDATE`、`RULES\_CHANGED`、`TARGETS\_CHANGED`、`FOCUS\_UPDATE`、`SERVICE\_STATUS`（Moored/Adrift）。



\### 5.4 订阅文件格式（T2）与通用检测配置



\*\*订阅 JSON（用户导入，仓库不入库真实特征）：\*\*



```json

{

&#x20; "subscriptionVersion": 1,

&#x20; "name": "Mock Super App Feed Blocker",

&#x20; "apps": \[

&#x20;   {

&#x20;     "package": "com.example.mocksuperapp",

&#x20;     "features": \[

&#x20;       {

&#x20;         "featureId": "MOCK\_FEED",

&#x20;         "label": "Mock Feed",

&#x20;         "alwaysBlock": false,

&#x20;         "activityPatterns": \["com\\\\.example\\\\.mocksuperapp\\\\.feed\\\\..\*"],

&#x20;         "contentRules": { "titleKeywords": \["MOCKFEED"], "tabKeywords": \["FOR YOU", "FOLLOWING"], "requiredTabs": 1 }

&#x20;       },

&#x20;       {

&#x20;         "featureId": "MOCK\_LIVE",

&#x20;         "label": "Mock Live",

&#x20;         "alwaysBlock": true,

&#x20;         "activityPatterns": \["com\\\\.example\\\\.mocksuperapp\\\\.live\\\\..\*"],

&#x20;         "contentRules": { "extraKeywords": \["LIVE NOW"] }

&#x20;       }

&#x20;     ]

&#x20;   }

&#x20; ]

}

```



\- 导入后每个 `feature` 成为一个 `FUNC:<package>:<featureId>` 目标，可独立启用、设额度、加组。

\- 校验规则：JSON 可解析、包名格式合法、`activityPatterns` 可编译为正则、至少一个 feature。任一失败整体拒绝。



\*\*内置 `detector\_config.json`（assets，通用参数 + T1 目录，不含任何红线字符串）：\*\*



```json

{

&#x20; "configVersion": 2,

&#x20; "detectionDebounceMs": 300,

&#x20; "excludedPackages": \[],

&#x20; "appCatalog": \[

&#x20;   { "package": "com.ss.android.ugc.aweme", "label": "Douyin", "category": "video" },

&#x20;   { "package": "com.smile.gifmaker", "label": "Kuaishou", "category": "video" },

&#x20;   { "package": "tv.danmaku.bili", "label": "Bilibili", "category": "video" },

&#x20;   { "package": "com.xingin.xhs", "label": "RED", "category": "video" },

&#x20;   { "package": "com.google.android.youtube", "label": "YouTube", "category": "video" },

&#x20;   { "package": "com.zhiliaoapp.musically", "label": "TikTok", "category": "video" },

&#x20;   { "package": "com.instagram.android", "label": "Instagram", "category": "social" }

&#x20; ]

}

```



\## 6. 数据模型（Room + DataStore）



```

Target(targetId PK, label, kind APP|FUNC, package, groupId?, source CATALOG|CUSTOM|SUBSCRIPTION, enabled, createdAt)

TargetGroup(id PK, name, createdAt)

Rule(id PK, targetId, type DAILY\_QUOTA|SCHEDULE\_BLOCK|ALWAYS\_BLOCK, quotaMinutes?, startHHmm?, endHHmm?, action, enabled, createdAt, updatedAt)

UsageDaily(dateStr, targetId, usedSeconds)   // PK(dateStr,targetId)；dateStr=yyyy-MM-dd 本地时区；组用量=成员求和

EventLog(id PK auto, ts, type, targetId?, ruleId?, detailJson)   // 保留 90 天，启动清理

CooldownRecord(id PK, payloadJson, status, requestedAt, expiresAt)

PairedClient(id PK, userAgent, firstSeenAt, lastSeenAt)

Subscription(id PK, name, configJson, version, enabled, importedAt, updatedAt)

// DataStore: token, cooldownMinutes(默认10), extraUnlockMaxPerDay(默认2), focusDefaultMinutes(默认45)

```



无云同步；控制台提供"清空所有数据"（属放宽，走冷静期）。



\## 7. 非功能需求



\### 7.1 性能与稳定

\- T1：前台切换到判定 ≤ 300ms；T2 一级路径：进入页面到勒马页 ≤ 600ms。

\- 无障碍单次事件处理 ≤ 50ms；T2 节点遍历不在主线程。

\- Web 服务崩溃自愈（5s 退避）；内存常驻 ≤ 80MB。



\### 7.2 华为适配清单（引导页逐项覆盖，内置文案，每项自动回检）

\- 无障碍：设置 → 辅助功能 → 无障碍 → 已下载的应用 → Mooring → 开启

\- 启动管理：设置 → 应用和服务 → 应用启动管理 → Mooring → 关闭"自动管理"，手动允许三项

\- 多任务加锁：最近任务卡片下拉出现锁图标

\- 通知权限：设置 → 通知 → Mooring → 允许

\- 全绿才算完成引导



\### 7.3 隐私

\- T1 不读取任何屏幕内容，仅感知前台包名。

\- T2 节点文本仅内存中关键词匹配，匹配后立即丢弃，不落盘不上传；事件日志只记目标与规则，不记页面内容。

\- 除局域网 Web 服务外不发任何外部请求；`usesCleartextTraffic` 仅服务本地 HTTP。



\### 7.4 仓库卫生

\- CI（`.github/workflows/hygiene.yml`）：对 `app/src/\*\*` grep 第 0 节列出的红线字符串，命中即失败；Mock 订阅与单测 fixture 仅使用 `com.example.\*`。



\## 8. MVP 验收标准



1\. \*\*T1 拦截\*\*：启用目录内任一应用并设 1 分钟配额，用尽后每次进入 1 秒内弹出勒马页；次日 00:00 自动重置。

2\. \*\*T1 零误伤\*\*：未启用的应用（含系统界面、桌面、输入法）连续混合使用 30 分钟零拦截。

3\. \*\*组配额\*\*：A、B 两目标入组共享 45 分钟；A 消耗 30 分钟后，B 使用至第 15 分钟即被拦；A 单独设 10 分钟上限时，A 第 10 分钟先被拦（自身配额更严先生效）。

4\. \*\*T2 引擎（自动化）\*\*：安装 Mock 示例应用 + 导入 Mock 订阅，进入 Mock Feed 600ms 内勒马页；Mock Live 永久禁用；控制台修改订阅后热生效，无需重启。

5\. \*\*T2 真机（\[MANUAL] 维护者自测项，配置不入库）\*\*：维护者用自备的真实订阅文件在自己设备上验证：功能页 1 秒内拦截、聊天/朋友圈/公众号 30 分钟零误伤。

6\. \*\*配对与只读\*\*：电脑扫码后可查看用量、收紧立即生效、放宽走完 10 分钟冷静期 + 120 秒确认窗口 + 过期作废全流程；手机自带浏览器打开同 URL 为只读页，写操作全部 403。

7\. \*\*重启恢复\*\*：手机重启后前台服务与 Web 服务自动恢复；无障碍未被手动关闭时监测自动恢复。

8\. \*\*存活\*\*：息屏 2 小时 + 前台使用 1 小时混合场景后进程存活、计时误差 < 30 秒。

9\. \*\*专注模式\*\*：期间一切放宽请求 403，到期规则自动还原。

10\. \*\*默认规则 v2\*\*：全新安装 + 控制台首次配置后，视频组规则按 F9 生效。

11\. \*\*仓库卫生\*\*：hygiene CI 通过。



\## 9. 里程碑



| 里程碑 | 内容 | 验收 |

|---|---|---|

| M0 | 工程骨架 + 规则引擎 + T1 检测（只记日志不拦截）+ hygiene CI | 验收 1 的识别部分、11 |

| M1 | T1 拦截 + 用量统计 + Room + 组配额 | 验收 1、2、3 |

| M2 | T2 引擎 + 订阅加载/热更新 + Mock 全链路 | 验收 4 |

| M3 | Ktor Web 服务 + 配对 + 目标/组/规则 API + 控制台基础版 | 验收 6 的接口部分 |

| M4 | 冷静期 + 专注模式 + 看板 + 默认规则 v2 | 验收 6 完整、9、10 |

| M5 | 保活强化 + 华为引导页 + 全量回归 | 验收 7、8；\[MANUAL] 5 |



\## 10. 明确不做（Out of Scope，MVP 禁止实现）



iOS 端；云同步与账号体系；原生桌面客户端；PC 前台应用感知联动；委托监督（他人托管）；Shizuku/root 提权路径；\*\*官方内置任何真实超级 App 的 T2 订阅\*\*（永远只走用户自备/社区订阅，这是法律防火墙的一部分）。



\## 11. 已知风险与应对



\- \*\*超级 App 改版导致 T2 失效\*\*：特征全部在订阅 JSON 中，更新订阅即可，引擎不动；T1 额度不受影响，工具永不整体失灵。

\- \*\*`TYPE\_WINDOW\_CONTENT\_CHANGED` 事件洪水\*\*：去抖 + 仅处理存在已启用订阅的包名。

\- \*\*勒马页被划掉\*\*：`excludeFromRecents` + 服务侧 5 秒轮询重弹。

\- \*\*用户直接关闭无障碍\*\*：Android 限制无法技术阻止；靠自检通知 + 引导红绿灯抬高关闭成本，这是产品接受的边界。

\- \*\*局域网 IP 漂移\*\*：二维码实时反映当前 IP；控制台不可达时引导重扫；mDNS 域名支持列入 v0.2。

\- \*\*组配额绕过\*\*（改用未启用 App）：控制台"已安装未管控"提醒区 + 看板展示未纳入目标的视频类安装应用用量。



