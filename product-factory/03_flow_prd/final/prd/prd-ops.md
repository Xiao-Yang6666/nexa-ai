# PRD — 运营 / 系统设置 / 运维（FL-ops）

> 分片：运营与运维（运营与运维能力域 + 渠道运维）。对应流程图 `flow/FL-ops.md`（5 图）、状态矩阵 `PAGE-STATE-MATRIX.md`。
> 数据对象字段一律复用 `DATA-MODEL.md §1 User / §15 Option`；性能/日志/部署为运行期结构（`PerformanceStats/LogFile/UptimeMonitor`，非本地 DB 表，字段以 controller 返回为准）。
> 跨切面契约见 `OVERALL-FLOW.md §3`：`optionRoute/performanceRoute` 挂 RootAuth；C3 二次验证（合规确认仅会话非 access_token）。倍率细节见 `FL-billing.md`。
> 本片覆盖功能 ID：**F-4015 / F-4016 / F-4017 / F-4018 / F-4019 / F-4020 / F-4021 / F-4022 / F-4023 / F-4024 / F-4025 / F-4026 / F-4027 / F-4028 / F-4029 / F-4030 / F-4031 / F-4032 / F-4033 / F-4034 / F-4035 / F-4036 / F-4045**。

---

## OP-1 系统初始化 setup（探测分叉 → 三项表单校验 → 创建 root 置标记，幂等防重）

- **功能 ID / 优先级**：F-4015、F-4016 / P0
- **来源**：FC-106（`controller/setup.go GetSetup`：读 `constant.Setup`、`RootUserExists`、`DatabaseType`；`PostSetup`：username<=12、password>=8 且两次一致、创建 `Role=RoleRootUser Quota=100000000`、写模式、`Create model.Setup`、`constant.Setup=true`）
- **角色 / Owner**：匿名（首装向导）；Owner 模块 = 运营与运维
- **触发**：首访 `GET /api/setup` 探测；管理员 `POST /api/setup` 提交

### 1. 场景
全站只能初始化一次的安装向导。探测 `GET /api/setup`（匿名）：已初始化返回 `status=true` 直接进登录；未初始化返回 `root_init=RootUserExists()` 与 `database_type`（按 `UsingMySQL/PostgreSQL/SQLite`）。提交 `POST /api/setup`：已初始化返回「系统已经初始化完成」（幂等防重）；校验 `username<=12`、`password>=8` 且两次一致；通过后创建 `Role=RoleRootUser、Quota=100000000` 的 root，写 `SelfUseModeEnabled/DemoSiteEnabled`，`Create model.Setup`，置 `constant.Setup=true`。

### 2. 前置条件
- 探测/提交均匿名可访问（`POST` 带 anonymousRequestBodyLimit）。
- 未初始化时 `constant.Setup=false`。
- 提交体含 `username/password/password 确认/SelfUseModeEnabled/DemoSiteEnabled`。

### 3. 主流程（对应 OP-1 节点 su_get→su_ok）
1. `GET /api/setup` 探测（su_get），判 `constant.Setup`（su_done）：已初始化 → `status=true` 进登录（su_skip）；未初始化 → 返回 `root_init/database_type`（su_info）。
2. `POST /api/setup` 提交（su_post），判已初始化（su_re）：是 → 「系统已经初始化完成」（su_e0）。
3. 校验 `username<=12`（su_un）：超长 → 报错（su_e1）。
4. 校验 `password>=8`（su_pw）：过短 → 报错（su_e2）。
5. 校验两次密码一致（su_cf）：不一致 → 报错（su_e3）。
6. 创建 `Role=RoleRootUser Quota=1亿`（su_create）→ 写 `SelfUseModeEnabled/DemoSiteEnabled`（su_mode）→ `Create model.Setup`、`constant.Setup=true`（su_mark）→ 初始化完成（su_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 已初始化探测 | su_done-是 | status=true 进登录 | 已初始化短路态 |
| 已初始化再提交 | su_re-是 | 幂等拒绝 | 重复初始化拒绝态 |
| `username>12` | su_un-超长 | 拒绝创建 | 用户名过长态 |
| `password<8` | su_pw-过短 | 拒绝创建 | 密码过短态 |
| 两次密码不一致 | su_cf-不一致 | 拒绝创建 | 两次不一致态 |
| 全部通过 | su_ok | 创建 root + 置标记 | 初始化完成态 |

### 5. 数据对象（复用 DATA-MODEL §1 User + §15 Option/Setup）
- **写** `User`：`Username(username)`（<=12）、`Password(password)`（>=8，存哈希）、`Role(role)=RoleRootUser`、`Quota(quota)=100000000`。
- **写** `Option`：`SelfUseModeEnabled`、`DemoSiteEnabled`。
- **写** `Setup`（`model.Setup`）+ `constant.Setup=true`（一次性标记）。
- **探测返回**：`status`、`root_init`（`RootUserExists()`）、`database_type`（mysql/postgres/sqlite）。

### 6. 验收标准
- [ ] 未初始化 `GET /api/setup` → 返回 `root_init` 与 `database_type`（mysql/postgres/sqlite 之一）；已初始化 → `status=true`。
- [ ] 已初始化后再 `POST /api/setup` → 返回「系统已经初始化完成」，不重复创建。
- [ ] `username` 长度 >12 → 报用户名过长，不创建。
- [ ] `password` 长度 <8 或两次不一致 → 分别报错，不创建。
- [ ] 合法提交 → 创建 `Role=RoleRootUser`、`Quota=100000000` 用户，`constant.Setup` 置 true。

### 7. 所触及页面状态（对齐 OP-1 系统初始化）
已初始化短路态（status=true 进登录）· 未初始化探测态（root_init/database_type）· 重复初始化拒绝态 · 用户名过长态 · 密码过短态 · 两次不一致态 · 创建 root 态（Quota=1亿）· 初始化完成态（constant.Setup=true）。

---

## OP-2 全站选项读取与更新（敏感键剔除 + 逐键校验闸门链 + 审计仅记 key）

- **功能 ID / 优先级**：F-4017、F-4018、F-4032、F-4035 / P0（F-4032/F-4035 P1）
- **来源**：FC-107/FC-114/FC-117（`GetOptions` 跳过后缀 Token/Secret/Key/secret/api_key + 追加 CompletionRatioMeta；`UpdateOption` 逐键校验 + `recordManageAudit("option.update",{key})`；限流分组 `CheckModelRequestRateLimitGroup`；主题 `theme.frontend∈default/classic`）
- **角色 / Owner**：Root（RootAuth）；Owner 模块 = 运营与运维
- **触发**：Root 进系统设置加载配置（读）/ 修改单个配置项（写）

### 1. 场景
全站配置读写共享 `OptionMap`，关键在两侧的安全闸门。读 `GetOptions` 遍历 `OptionMap` **跳过后缀 Token/Secret/Key/secret/api_key 的键**（不泄露敏感配置），追加 `CompletionRatioMeta` 元信息。写 `UpdateOption` 逐键校验：启用 OAuth 但未填 ClientId → 「请先填入…」；`theme.frontend` 非 `default/classic` → 「无效的主题值」；`ModelRequestRateLimitGroup` 经 `CheckModelRequestRateLimitGroup` 校验 `[count,duration]` JSON；`payment_setting.compliance_*` **禁止经此改**；`QuotaForInviter` 正值需先确认支付合规（见 OP-5）。成功后审计 **仅记 key 不记 value**。

### 2. 前置条件
- 操作者为 Root（`optionRoute` 挂 RootAuth）。
- `OptionMap` 已 `InitOptionMap()`。
- 写入的 `key/value` 须通过该 key 对应的合法性分支。

### 3. 主流程（对应 OP-2 节点 op_op→op_wout/op_rout）
1. 判选项操作（op_op）。
2. 读 `GetOptions`（op_read）→ 跳过敏感后缀键（op_mask）→ 追加 `CompletionRatioMeta`（op_meta）→ 返回过滤后列表（op_rout，终态）。
3. 写 `UpdateOption`，判 key 类型（op_key）逐键校验：OAuth.enabled 缺 ClientId → 「请先填入 ClientId」（op_e1）；`theme.frontend` 非 default/classic → 「无效的主题值」（op_e2）；`ModelRequestRateLimitGroup` JSON 非法 → 校验失败（op_e3）；`payment_setting.compliance_*` → 禁改（op_block）；`QuotaForInviter` 正值未确认合规 → 要求先确认（op_e4）。
4. 校验通过 → `model.UpdateOption` 落库（op_save）→ `recordManageAudit option.update 仅记 key`（op_audit）→ 选项更新成功（op_wout，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 读取遇敏感后缀键 | op_mask | 跳过不返回 | 敏感键剔除态 |
| 启用 OAuth 缺 ClientId | op_cid-空 | 拒绝 | OAuth 缺 ClientId 态 |
| `theme.frontend` 非法值 | op_th-否 | 拒绝 | 主题非法态 |
| 限流分组 JSON 非法 | op_rl-否 | 校验失败 | 限流分组校验失败态 |
| 改 `compliance_*` | op_block | 禁改 | 合规字段禁改态 |
| `QuotaForInviter` 正值未确认合规 | op_comp-未确认 | 拒绝，要求先确认 | 邀请额度需合规态 |
| 校验通过 | op_wout | 落库 + 审计仅记 key | 更新成功态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option + §5 Log）
- **读过滤** `Option` KV：遍历 `OptionMap`，跳过 key 后缀 ∈ `{Token, Secret, Key, secret, api_key}`；追加 `key=CompletionRatioMeta`。
- **写校验** `Option.key`：`GitHubOAuthEnabled/discord.enabled/oidc.enabled`（依赖 ClientId）、`theme.frontend`（枚举 default/classic）、`ModelRequestRateLimitGroup`（JSON `map[string][2]int`）、`payment_setting.compliance_*`（禁改）、`QuotaForInviter`（正值需合规）。
- **审计** `Log`（manage audit）：`recordManageAudit("option.update", {key})`，**仅记 key 不记 value**。

### 6. 验收标准
- [ ] `GetOptions` 返回项不含任何以 `Token`/`Secret`/`Key`/`secret`/`api_key` 结尾的键，且额外含 `CompletionRatioMeta` 项。
- [ ] 启用某 OAuth（如 `GitHubOAuthEnabled=true`）但未填对应 ClientId → 返回「请先填入…」，不落库。
- [ ] `theme.frontend` 设为非 `default/classic` 的值 → 返回「无效的主题值」，不落库。
- [ ] 经此接口改 `payment_setting.compliance_*` → 被拒（禁改）。
- [ ] 任一选项更新成功后，审计日志含 `option.update` 与该 `key`，但**不含** value 明文。

### 7. 所触及页面状态（对齐 OP-2 选项读写）
敏感键剔除态 · CompletionRatioMeta 注入态（读终态）· OAuth 缺 ClientId 态 · 主题非法态 · 限流分组校验失败态 · 合规字段禁改态 · 邀请额度需合规态（闸门）· 更新成功态（审计仅记 key）。

---

## OP-3 性能监控与运维动作（实时统计 + 磁盘缓存 10 分钟保护 / GC / 重置）

- **功能 ID / 优先级**：F-4019、F-4020、F-4021 / P1（F-4020/F-4021 P2）
- **来源**：FC-108（`GetPerformanceStats` 读 `runtime.ReadMemStats`；`ClearDiskCache` 调 `CleanupOldDiskCacheFiles(10*time.Minute)`；`ForceGC` 调 `runtime.GC()`；`ResetPerformanceStats` 调 `ResetDiskCacheStats()`）
- **角色 / Owner**：Root（RootAuth）；Owner 模块 = 运营与运维（运维 SRE 画像）
- **触发**：Root 打开性能监控面板查看；点清缓存/强制 GC/重置统计

### 1. 场景
运维操作面板。`GetPerformanceStats` 实时读 `runtime.ReadMemStats` 组装 `CacheStats/MemoryStats(Alloc/Sys/NumGC/NumGoroutine)/DiskCacheInfo/DiskSpaceInfo(used_percent)/PerformanceConfig`（含 CPU/内存/磁盘阈值）。三类运维动作：`ClearDiskCache` 调 `CleanupOldDiskCacheFiles(10*time.Minute)` **仅删 10 分钟以上未使用**文件（保护进行中请求不误删）；`ForceGC` 调 `runtime.GC()`（幂等）；`ResetPerformanceStats` 调 `ResetDiskCacheStats()`（幂等）。

### 2. 前置条件
- 操作者为 Root（`performanceRoute` 挂 RootAuth）。
- 性能面板已打开（实时采集就绪）。
- 清缓存动作按 10 分钟阈值判定文件是否可删。

### 3. 主流程（对应 OP-3 节点 pm_in→pm_clrout/pm_gcout/pm_rstout）
1. 打开性能监控面板（pm_in，RootAuth）→ `GetPerformanceStats runtime.ReadMemStats`（pm_read）→ 组装 Mem/Disk/Goroutine/阈值实时统计（pm_stat）。
2. 判运维动作（pm_act）：无 → 面板渲染 num_goroutine/num_gc/used_percent（pm_view，终态）。
3. 清磁盘缓存：判文件 >10min 未使用（pm_clr）：<=10min 进行中 → 保护不删（pm_keep）；>10min 不活跃 → `CleanupOldDiskCacheFiles` 删除（pm_del）→ 「不活跃的磁盘缓存已清理」（pm_clrout）。
4. 强制 GC → `runtime.GC()` 幂等（pm_gc）→ 「GC 已执行」（pm_gcout）。
5. 重置统计 → `ResetDiskCacheStats()` 幂等（pm_rst）→ 「统计信息已重置」（pm_rstout）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 仅查看 | pm_view | 实时采集渲染 | 实时统计态 |
| 文件 <=10min 进行中 | pm_clr-<=10min | 保护不删 | 缓存保护态 |
| 文件 >10min 不活跃 | pm_del | 删除 | 缓存清理态「不活跃的磁盘缓存已清理」 |
| 强制 GC | pm_gc | `runtime.GC()` 幂等 | GC 执行态「GC 已执行」 |
| 重置统计 | pm_rst | `ResetDiskCacheStats` 幂等 | 统计重置态「统计信息已重置」 |

### 5. 数据对象（运行期 PerformanceStats + DiskCache）
- **读** `PerformanceStats`：`CacheStats`、`MemoryStats`（`Alloc/Sys/NumGC/NumGoroutine`）、`DiskCacheInfo`、`DiskSpaceInfo`（`used_percent`）、`PerformanceConfig`（CPU/内存/磁盘阈值）。
- **写动作** `DiskCache`：`CleanupOldDiskCacheFiles(10min)` 仅删 10 分钟以上未使用文件；`ResetDiskCacheStats()` 重置计数。
- **GC**：`runtime.GC()`（无返回数据，幂等）。

### 6. 验收标准
- [ ] `GetPerformanceStats` 返回含 `num_goroutine`、`num_gc`、`disk_space_info.used_percent` 与 CPU/内存/磁盘阈值。
- [ ] `ClearDiskCache` 仅删除 10 分钟以上未使用的缓存文件，10 分钟内文件不被删（进行中请求保护）。
- [ ] `ClearDiskCache` 成功返回「不活跃的磁盘缓存已清理」。
- [ ] `ForceGC` 返回「GC 已执行」，连续两次调用均成功（幂等）。
- [ ] `ResetPerformanceStats` 返回「统计信息已重置」（幂等）。

### 7. 所触及页面状态（对齐 OP-3 性能监控+运维动作）
实时统计态（num_goroutine/num_gc/used_percent + 阈值）· 缓存保护态（<=10min 不删）· 缓存清理态 · GC 执行态 · 统计重置态。

---

## OP-4 日志文件维护与 Uptime-Kuma 状态接入（按数量/天数清理 + 并发拉取聚合）

- **功能 ID / 优先级**：F-4022、F-4023、F-4026 / P2
- **来源**：FC-108/FC-110（`GetLogFiles` 仅收 `oneapi-*.log` 名降序；`CleanupLogFiles` mode∈by_count/by_days、value 正整数、跳过 `GetCurrentLogPath()`；`GetUptimeKumaStatus` errgroup 并发拉 status-page+heartbeat、`uptimeKeySuffix=_24`）
- **角色 / Owner**：Root（日志维护，RootAuth）/ 匿名（Uptime 状态读取）；Owner 模块 = 运营与运维
- **触发**：Root 在日志管理页加载/清理日志文件；前端状态页请求 `GET /api/uptime/status`

### 1. 场景
两条独立运维数据流。本地日志文件维护：`GetLogFiles` 仅收**前缀 `oneapi-` 且后缀 `.log`** 的文件按名降序（`LogDir` 空则 `Enabled=false`），返回 `file_count/total_size/oldest_time/newest_time`；`CleanupLogFiles` 的 `mode` 须 `by_count`（保留最新 N 个）/`by_days`（删早于 N 天）否则「invalid mode」，`value` 须正整数否则「invalid value」，**跳过当前活动日志** `GetCurrentLogPath()`，部分失败返回 `failed_files` 且 `success=false`。外部状态：`GetUptimeKumaStatus` 读 `GetUptimeKumaGroups()`（未配置返回空数组），`errgroup` 并发拉各组 `/api/status-page/{slug}` 与 `/heartbeat/{slug}`，按 monitorID 匹配 `uptimeList(_24 后缀)` 与 heartbeatList，单组拉取失败返回该组空 monitors。

### 2. 前置条件
- 日志维护操作为 Root（`performanceRoute` 挂 RootAuth）；Uptime 状态匿名可读。
- 日志维护要求 `LogDir` 已配置（否则 `Enabled=false`）。
- Uptime 接入要求 `console_setting.uptime_kuma_groups` 已配置（否则空数组）。

### 3. 主流程（对应 OP-4 两 subgraph）
1. 本地日志：`GetLogFiles`（lf_list）→ 判 `LogDir` 配置（lf_dir）：空 → `Enabled=false`（lf_off）；有 → 仅收 `oneapi-*.log` 名降序（lf_pick）→ `file_count/total_size/oldest/newest`（lf_meta）→ `CleanupLogFiles`（lf_clean）→ 判 mode（lf_mode）：非 by_count/by_days → 「invalid mode」（lf_em）；判 value 正整数（lf_val）：否 → 「invalid value」（lf_ev）；是 → 跳过当前活动日志 `GetCurrentLogPath`（lf_skip）→ 判全部成功（lf_res）：部分失败 → `failed_files success=false`（lf_fail）；全成功 → 删除数+释放字节（lf_ok）。
2. Uptime：`GET /api/uptime/status`（uk_in）→ 判 `GetUptimeKumaGroups` 配置（uk_grp）：未配置 → 空数组（uk_empty）；有 → `errgroup` 并发拉 status-page+heartbeat（uk_fan）→ 按 monitorID 匹配 `uptimeList(_24)` + heartbeatList（uk_match）→ 判单组拉取失败（uk_one）：失败 → 该组空 monitors（uk_ze）；成功 → 组内 monitors 可用率/状态/分组（uk_okm）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `LogDir` 未配置 | lf_dir-空 | `Enabled=false` | LogDir 未配置态 |
| mode 非法 | lf_mode-否 | 拒绝 | 清理 mode 非法态「invalid mode」 |
| value 非正整数 | lf_val-否 | 拒绝 | 清理 value 非法态「invalid value」 |
| 当前活动日志 | lf_skip | 跳过 `GetCurrentLogPath` | 活动日志保护态 |
| 清理部分失败 | lf_fail | `failed_files`、success=false | 部分失败态 |
| Uptime 未配置 | uk_grp-未配置 | 返回空数组 | Uptime 未配置态 |
| 单组拉取失败 | uk_one-失败 | 该组空 monitors | 单组失败态 |

### 5. 数据对象（运行期 LogFile + UptimeMonitor）
- **日志** `LogFile`：仅 `oneapi-*.log`（前缀 `oneapi-`、后缀 `.log`），`file_count`、`total_size`、`oldest_time`、`newest_time`；`LogDir` 空 → `Enabled=false`。
- **清理参数**：`mode`（`by_count`/`by_days`）、`value`（正整数）；跳过 `GetCurrentLogPath()`；部分失败 → `failed_files` + `success=false`。
- **Uptime** `UptimeMonitor`：按 `GetUptimeKumaGroups()` 各组并发拉取，按 monitorID 匹配 `uptimeList`（`_24` 后缀）+ heartbeatList，返回每 monitor 名称/可用率/状态/分组。

### 6. 验收标准
- [ ] `LogDir` 未配置 → `GetLogFiles` 返回 `Enabled=false`；已配置 → 仅列出 `oneapi-*.log` 文件，含 `file_count/total_size/oldest_time/newest_time`。
- [ ] `CleanupLogFiles` 的 `mode` 非 `by_count/by_days` → 返回「invalid mode」；`value<1` → 返回「invalid value」。
- [ ] 清理时当前活动日志（`GetCurrentLogPath()`）不被删除。
- [ ] 清理部分失败 → 返回 `failed_files` 且 `success=false`。
- [ ] `GetUptimeKumaStatus` 未配置 groups → 返回空数组；某组拉取失败 → 该组返回空 monitors（不影响其他组）。

### 7. 所触及页面状态（对齐 OP-4 日志维护+Uptime 接入）
LogDir 未配置态 · 日志文件列表态（oneapi-*.log）· 清理 mode 非法态 · 清理 value 非法态 · 活动日志保护态 · 部分失败态（failed_files）· 清理成功态 · Uptime 未配置态 · Uptime 并发聚合态（_24 可用率+心跳）· 单组失败态 · Uptime 正常态。

---

## OP-5 支付合规确认与控制台旧设置迁移（会话闸门写 5 项 + 旧键转 console_setting.*）

- **功能 ID / 优先级**：F-4030、F-4031 / P0（F-4031 P2）
- **来源**：FC-112（`ConfirmPaymentCompliance` 拒 access_token、`req.Confirmed=true`、写 5 项 `payment_setting.compliance_*`）、`MigrateConsoleSetting`（ApiInfo/FAQ 截断 50、UptimeKumaUrl+Slug 同存才迁、Delete 旧键 + InitOptionMap）
- **角色 / Owner**：Root（RootAuth）；Owner 模块 = 运营与运维
- **触发**：Root 在合规弹窗勾选确认提交；Root 触发控制台旧设置迁移

### 1. 场景
两条独立 Root 运维流。合规确认 `ConfirmPaymentCompliance`（C3 会话闸门）：**拒绝 access_token**（`use_access_token` → 403「requires dashboard session」，仅会话可确认）；`req.Confirmed` 必为 true（false → 「请确认合规声明」）；成功写 5 个 `payment_setting.compliance_*`（confirmed/terms_version/confirmed_at/confirmed_by/confirmed_ip）并返回 `terms_version/confirmed_by`。控制台迁移 `MigrateConsoleSetting`：`ApiInfo/FAQ` 截断 50 条转 `console_setting.api_info/faq`，`UptimeKumaUrl+Slug` 同时存在才迁为 `uptime_kuma_groups`，`Delete` 旧键并 `InitOptionMap` 重载。

### 2. 前置条件
- 操作者为 Root（`optionRoute` 挂 RootAuth）。
- 合规确认须经会话（非 API token），且勾选 Confirmed。
- 迁移读 `AllOption` 旧键。

### 3. 主流程（对应 OP-5 节点 cm_root→cm_cout/cm_mout）
1. Root 运维操作（cm_root），判操作（cm_op）。
2. 合规确认：判 `use_access_token`（cm_tok）：是 → 403「requires dashboard session」（cm_e1）；否会话 → 判 `req.Confirmed=true`（cm_conf）：false → 「请确认合规声明」（cm_e2）；true → 写 5 项 compliance_confirmed/terms_version/at/by/ip（cm_write）→ 返回 terms_version/confirmed_by（cm_cout，终态）。
3. 控制台迁移：读 `AllOption`（cm_read）→ ApiInfo/FAQ 截断 50 条转 console_setting.api_info/faq（cm_trunc）→ 判 `UptimeKumaUrl` 与 `Slug` 同时存在（cm_uk）：缺一 → 不迁 uptime（cm_skipuk）；都有 → 迁为 uptime_kuma_groups（cm_miguk）→ `Delete` 旧键（cm_del）→ `InitOptionMap` 重载（cm_reload）→ 迁移完成旧键删除（cm_mout，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 合规确认用 access_token | cm_tok-是 | 403 拒绝 | access_token 拒绝态「requires dashboard session」 |
| `Confirmed=false` | cm_conf-false | 拒绝 | 未勾选态「请确认合规声明」 |
| `Confirmed=true` 会话 | cm_write | 写 5 项 compliance_* | 合规写入态（返回 terms_version/confirmed_by） |
| ApiInfo/FAQ 超 50 条 | cm_trunc | 截断 50 转 console_setting.* | ApiInfo/FAQ 截断态 |
| UptimeKumaUrl/Slug 缺一 | cm_uk-缺一 | 不迁 uptime | 缺一不迁 |
| Url+Slug 同存 | cm_miguk | 迁为 uptime_kuma_groups | uptime 条件迁移态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option）
- **合规写** `Option`：`payment_setting.compliance_confirmed`、`compliance_terms_version`、`compliance_confirmed_at`、`compliance_confirmed_by`、`compliance_confirmed_ip`（共 5 项；其中 `terms_version` 与隐私页版本语义关联，见 prd-public PUB-4 / prd-nfr-rbac F-5021）。
- **迁移写** `Option`：`console_setting.api_info`、`console_setting.faq`（ApiInfo/FAQ 截断 50 条）、`uptime_kuma_groups`（仅 url+slug 同存时）；`Delete` 旧键 `ApiInfo/Announcements/FAQ/UptimeKumaUrl/UptimeKumaSlug` 后 `InitOptionMap` 重载。

### 6. 验收标准
- [ ] 用 access_token 调 `ConfirmPaymentCompliance` → 返回 403「requires dashboard session」。
- [ ] `req.Confirmed=false` → 返回「请确认合规声明」，不写入。
- [ ] 会话且 `Confirmed=true` → 写入 5 个 `payment_setting.compliance_*` 选项，返回 `terms_version`、`confirmed_by`。
- [ ] 迁移时 ApiInfo/FAQ 超 50 条 → 被截断为 50 条转入 `console_setting.api_info/faq`。
- [ ] 迁移时仅当 `UptimeKumaUrl` 与 `Slug` 同时存在才迁为 `uptime_kuma_groups`；旧键被 `Delete` 后 `InitOptionMap` 重载。

### 7. 所触及页面状态（对齐 OP-5 合规确认+控制台迁移）
access_token 拒绝态 · 未勾选态 · 合规写入态（5 项落库）· ApiInfo/FAQ 截断态 · uptime 条件迁移态/缺一不迁 · 旧键删除重载态。

---

## OP-6 公开内容读取 + 性能汇总 + 限流分组/敏感词/自动分组/i18n 配置（运营配置面）

- **功能 ID / 优先级**：F-4024、F-4025、F-4027、F-4028、F-4029、F-4033、F-4034、F-4036 / P1（F-4024/F-4025/F-4029/F-4033/F-4036 P2）
- **来源**：FC-109（`GetPerfMetricsSummary/GetPerfMetrics`）、FC-111（`GetUserAgreement/GetPrivacyPolicy/GetNotice/GetAbout/GetHomePageContent`）、FC-115（敏感词 `setting/sensitive.go`）、FC-116（自动分组 `setting/auto_group.go`）、FC-118（i18n `i18n/i18n.go`）
- **角色 / Owner**：匿名/用户（公开读取与性能汇总）/ Root（敏感词/自动分组配置）；Owner 模块 = 运营与运维
- **触发**：前端读公开内容 / 请求性能汇总 / Root 配置敏感词与自动分组 / 请求按语言渲染

### 1. 场景
本块归并运营配置面与公开内容读取的若干校验型/读取型功能。性能汇总 `GetPerfMetricsSummary`（hours 默认 24，聚合活动分组+auto）与单模型 `GetPerfMetrics`（model 必填，过滤非活动分组）。公开内容读取 `GetUserAgreement/GetPrivacyPolicy/GetNotice/GetAbout/GetHomePageContent`（匿名可读，持 `OptionMapRWMutex.RLock`）。Root 配置：敏感词 `SensitiveWords`（换行拆分，`ShouldCheckPromptContent` = 两开关与运算）、自动分组 `UpdateAutoGroupsByJsonString`（JSON 列表 + `DefaultUseAutoGroup`）。i18n 按用户设置或 Accept-Language 选 `zh-CN/zh-TW/en`。

### 2. 前置条件
- 公开读取与性能汇总：匿名/用户可访问（性能汇总受 `HeaderNavModulePublicOrUserAuth("pricing")` 控制）。
- 敏感词/自动分组配置经选项接口由 Root 配置。
- i18n 来自请求上下文（用户设置优先，否则 Accept-Language）。

### 3. 主流程
1. 性能汇总：`GetPerfMetricsSummary`（hours 默认 24）聚合 `activeGroups=GetGroupRatioCopy 键 + auto` → `QuerySummaryAll`；`GetPerfMetrics` 校验 `model` 非空（空 → 400「model is required」）→ `Query{Model,Group,Hours}` → `filterActiveGroups`。
2. 公开内容：`GetUserAgreement/GetPrivacyPolicy` 返 `LegalSetting`；`GetNotice/GetAbout/GetHomePageContent` 返 `OptionMap[Notice/About/HomePageContent]`（持 RLock）。
3. 敏感词：`SensitiveWordsFromString` 按换行拆词表，`ShouldCheckPromptContent` 在 `CheckSensitiveEnabled` 与 `CheckSensitiveOnPromptEnabled` 均 true 时才返回 true。
4. 自动分组：`UpdateAutoGroupsByJsonString` 解析 JSON 列表，`ContainsAutoGroup` 对 auto 分组返 true，`GetStatus` 暴露 `default_use_auto_group`。
5. i18n：`GetLangFromContext` 先取用户 `UserSetting` 语言，未登录回退 `ParseAcceptLanguage`，`T/Translate` 渲染。

### 4. 分支与异常
| 触发条件 | 系统行为 | 用户可见结果 |
|---|---|---|
| `GetPerfMetrics` model 为空 | 返回 400「model is required」 | 单模型缺 model 态 |
| 性能汇总 hours 缺省 | 默认 24 | 24h 汇总态 |
| 公开内容未配置 | 返回空字符串 | 空内容态 |
| 敏感词两开关非全开 | `ShouldCheckPromptContent=false` | 不检查 prompt |
| 自动分组 JSON 列表 | 解析为 auto 分组成员 | 自动分组配置态 |
| 已登录请求 | 用 `UserSetting` 语言 | 对应语言渲染 |
| 未登录请求 | 回退 Accept-Language | 按头语言渲染 |

### 5. 数据对象（复用 DATA-MODEL §15 Option/LegalSetting/§1 User）
- **性能** `PerfMetric`：`GetPerfMetricsSummary`（hours 默认 24，`activeGroups`+auto）、`GetPerfMetrics`（`model` 必填，`Groups=filterActiveGroups`）。
- **公开内容** `LegalSetting.UserAgreement/PrivacyPolicy`、`Option[Notice/About/HomePageContent]`（`OptionMapRWMutex.RLock`）。
- **敏感词** `SensitiveWords`（换行拆分词表）、`CheckSensitiveEnabled`/`CheckSensitiveOnPromptEnabled`（`ShouldCheckPromptContent` = 二者与运算）。
- **自动分组** `AutoGroup`：`DefaultUseAutoGroup`、`UpdateAutoGroupsByJsonString`、`ContainsAutoGroup`；`GetStatus.default_use_auto_group`。
- **i18n** `UserSetting` 语言 / `Accept-Language`：`zh-CN/zh-TW/en`。

### 6. 验收标准
- [ ] `GetPerfMetricsSummary` 不带 `hours` → 默认聚合近 24 小时；`GetPerfMetrics` 缺 `model` → 返回 400「model is required」。
- [ ] `GetUserAgreement/GetPrivacyPolicy/GetNotice/GetAbout/GetHomePageContent` 匿名可读；未配置项返回空字符串。
- [ ] 敏感词换行字符串被拆为词表；仅当 `CheckSensitiveEnabled` 与 `CheckSensitiveOnPromptEnabled` 均为 true 时 `ShouldCheckPromptContent` 返回 true。
- [ ] `UpdateAutoGroupsByJsonString` 解析 JSON 列表后，`ContainsAutoGroup("auto")` 返回 true，`GetStatus` 暴露 `default_use_auto_group`。
- [ ] 已登录用户按其 `UserSetting` 语言返回文案；未登录回退 `Accept-Language`；支持 `zh-CN/zh-TW/en`。

### 7. 所触及页面状态（对齐 OP-3/OP-4 公开内容与配置面）
24h 性能汇总态 · 单模型缺 model 态（400）· 公开内容渲染态/空内容态 · 敏感词词表态（两开关与运算）· 自动分组配置态（default_use_auto_group）· 语言渲染态（用户设置/Accept-Language）。

---

## OP-7 Codex 渠道上游用量查询（OAuth key 解析 + 401/403 自动刷新重试）

- **功能 ID / 优先级**：F-4045 / P1
- **来源**：FC（`controller/codex_usage.go GetCodexChannelUsage`：校验 `ch.Type==ChannelTypeCodex` 且非 `IsMultiKey`、`codex.ParseOAuthKey` 取 access_token/account_id、`FetchCodexWhamUsage`、401/403 且有 refresh_token 时 `RefreshCodexOAuthToken` 后重试并回写 key）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 渠道运维
- **触发**：管理员在渠道页 `GET /channel/:id/codex/usage` 查 Codex 渠道用量

### 1. 场景
管理员查看某 Codex 渠道在上游的 wham 用量。该接口对渠道类型有强约束：必须 `ch.Type==ChannelTypeCodex` 且非 multi-key；从渠道 Key 解析 OAuth（`access_token/account_id`）后调 `FetchCodexWhamUsage`；若上游返回 401/403 且渠道有 `refresh_token`，自动 `RefreshCodexOAuthToken` 刷新后重试，并把新 token 回写渠道 Key（凭证轮换，受 NFR-S01 加密存储约束）。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- 目标渠道 `Type==ChannelTypeCodex` 且非 multi-key。
- 渠道 Key 可解析出 OAuth `access_token/account_id`。

### 3. 主流程
1. `GET /channel/:id/codex/usage`，校验渠道类型为 Codex（非 Codex → 「channel type is not Codex」）。
2. 校验非 multi-key（multi-key → 「multi-key channel is not supported」）。
3. `codex.ParseOAuthKey` 取 `access_token/account_id`（缺失分别报错）。
4. `FetchCodexWhamUsage` 拉用量。
5. 上游返回 401/403 且有 `refresh_token` → `RefreshCodexOAuthToken` 刷新 → 重试 → 回写渠道 Key（密文存储）。
6. 成功 → 返回 wham 用量数据。

### 4. 分支与异常
| 触发条件 | 系统行为 | 用户可见结果 |
|---|---|---|
| 渠道非 Codex 类型 | 拒绝 | 「channel type is not Codex」 |
| Codex 渠道为 multi-key | 拒绝 | 「multi-key channel is not supported」 |
| access_token/account_id 缺失 | 分别报错 | OAuth 解析失败态 |
| 上游 401/403 且有 refresh_token | 刷新 token 后重试并回写 Key | 自动刷新重试态 |
| 重试成功 | 返回 wham 用量 | 用量返回态 |

### 5. 数据对象（复用 DATA-MODEL §3 Channel）
- **读** `Channel`：`Type(type)==ChannelTypeCodex`、`ChannelInfo.IsMultiKey`（须 false）、`Key(key)`（含 OAuth，`ParseOAuthKey` 取 `access_token/account_id/refresh_token`）。
- **写回** `Channel.Key(key)`：401/403 刷新 token 后回写新 OAuth key（加密静态存储，遵 NFR-S01）。
- **上游返回**：wham 用量数据。

### 6. 验收标准
- [ ] 对非 Codex 类型渠道调用 → 返回「channel type is not Codex」。
- [ ] 对 multi-key 的 Codex 渠道调用 → 返回「multi-key channel is not supported」。
- [ ] Key 无法解析出 `access_token` 或 `account_id` → 分别报错。
- [ ] 上游返回 401/403 且渠道有 `refresh_token` → 触发 `RefreshCodexOAuthToken`，刷新后重试，新 token 回写渠道 Key。
- [ ] 重试成功 → 返回上游 wham 用量数据。

### 7. 所触及页面状态（渠道页 Codex 用量视图）
非 Codex 拒绝态 · multi-key 拒绝态 · OAuth 解析失败态 · 自动刷新重试态（401/403→刷新→重试→回写 Key）· 用量返回态。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-4015 | OP-1 |
| F-4016 | OP-1 |
| F-4017 | OP-2 |
| F-4018 | OP-2 |
| F-4019 | OP-3 |
| F-4020 | OP-3 |
| F-4021 | OP-3 |
| F-4022 | OP-4 |
| F-4023 | OP-4 |
| F-4024 | OP-6 |
| F-4025 | OP-6 |
| F-4026 | OP-4 |
| F-4027 | OP-6 |
| F-4028 | OP-6 |
| F-4029 | OP-6 |
| F-4030 | OP-5 |
| F-4031 | OP-5 |
| F-4032 | OP-2 |
| F-4033 | OP-6 |
| F-4034 | OP-6 |
| F-4035 | OP-2 |
| F-4036 | OP-6 |
| F-4045 | OP-7 |

无 `[BLOCKER]`。
