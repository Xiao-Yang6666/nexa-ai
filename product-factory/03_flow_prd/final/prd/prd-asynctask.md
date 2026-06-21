# PRD — 异步任务中心（FL-asynctask）

> 分片：异步任务中心 D5（MJ/Suno/视频任务）。对应流程图 `flow/FL-asynctask.md`、状态矩阵 `PAGE-STATE-MATRIX.md §F`。
> 数据对象字段一律复用 `DATA-MODEL.md`（`Task §9`），终态枚举 `TaskStatus`：`NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN`。
> 关键函数/常量：`InitTask`、`UpdateWithStatus`（CAS）、`TaskGetAllUserTask`、`TaskGetAllTasks`、`TaskCountAllTasks`、`GetByTaskId`、`GetByTaskIds`、`GetTimedOutUnfinishedTasks`、`GetResultURL`、`ToVideoStatus`、`TaskStatusSuccess`、`TaskStatusFailure`。任务计费衔接见 `FL-billing.md`（预扣/退款），本片标注复用不重画倍率。
> 本片覆盖功能 ID：**F-2001 / F-2002 / F-2005 / F-2007 / F-2008 / F-2003 / F-2006 / F-2004 / F-2011 / F-2009 / F-2010**。

---

## AT-1 任务提交后的状态机轮询循环（NOT_START→…→SUCCESS/FAILURE，CAS 守卫）

- **功能 ID / 优先级**：F-2001、F-2002、F-2007、F-2008 / P0
- **来源**：`model/task.go InitTask`（`Status=TaskStatusNotStart`、`Progress=0%` 落库）、`UpdateWithStatus(fromStatus,…)` CAS（`RowsAffected>0` 才赢）、状态机合法顺序 `NOT_START→SUBMITTED→QUEUED→IN_PROGRESS`、`progress==100%`/上游终态进 SUCCESS、错误进 FAILURE、无法识别进 UNKNOWN
- **角色 / Owner**：登录用户（查询）/ 系统（状态机·轮询·CAS）；Owner 模块 = 异步任务中心
- **触发**：relay 提交任务成功后 `InitTask` 落库，后台轮询/回调推进状态机

### 1. 场景
relay 提交成功后 `InitTask` 以 `Status=NOT_START、Progress=0%` 落库。后台轮询/回调按状态机合法顺序 `NOT_START→SUBMITTED→QUEUED→IN_PROGRESS` 推进，每次写入都用 `UpdateWithStatus(fromStatus,…)` 做 CAS：以 `fromStatus` 为 WHERE 守卫 UPDATE，`RowsAffected>0` 才算赢得更新（防并发覆盖），否则放弃本拍等下一轮。`progress==100%` 或上游回报终态进 `SUCCESS`（写 `FinishTime`），上游报错进 `FAILURE`（写 `FailReason`），无法识别进 `UNKNOWN`。终态（SUCCESS/FAILURE）不可再被普通 bulk update 覆盖。终态触发退款/差额结算（移交 AT-4）。这是「提交落库 → 轮询循环（推进 ⊕ CAS 失败回退）→ 终态分叉」的自循环。

### 2. 前置条件
- relay 提交成功才 `InitTask` 落库。
- 状态推进须符合合法顺序，非法跃迁丢弃本拍。
- 任何写入须经 `UpdateWithStatus`（CAS），`RowsAffected>0` 才生效。

### 3. 主流程（对应 AT-1 节点 at_sub→at_succ/at_fail/at_unk）
1. relay 提交任务成功（at_sub）→`InitTask` 落库 `Status=NOT_START Progress=0%`（at_init）。
2. 后台轮询/回调一拍（at_poll）→读上游进度与当前 `fromStatus`（at_read）。
3. 判状态推进是否合法（at_legal）→非法跃迁丢弃本拍不写库（at_skip）→回 at_poll。
4. 合法则 `UpdateWithStatus` CAS 判 `RowsAffected>0`（at_cas）→false 被他进程改写回 at_skip；true 赢得更新判是否达终态（at_term）。
5. `progress<100%` 非终态→at_skip 回轮询；`progress=100%`/上游成功→`Status=SUCCESS` 写 `FinishTime`（at_succ）；上游报错→`Status=FAILURE` 写 `FailReason`（at_fail）；无法识别→`Status=UNKNOWN`（at_unk）。
6. at_succ / at_fail 触发退款/差额结算移交 AT-4（at_settle）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 非法状态跃迁 | at_legal-非法 | 丢弃本拍不写库 | 非法跃迁丢弃态 |
| `UpdateWithStatus` `RowsAffected=0` | at_cas-false | 放弃本拍等下一轮 | CAS 失败回退态 |
| `progress<100%` 非终态 | at_term-非终态 | 不进终态等下一拍 | 进行中态 |
| `progress=100%`/上游成功 | at_term-成功 | `Status=SUCCESS` 写 `FinishTime` | 成功终态 |
| 上游报错 | at_term-报错 | `Status=FAILURE` 写 `FailReason` | 失败终态 |
| 无法识别上游回报 | at_term-无法识别 | `Status=UNKNOWN` | 未知终态 |

### 5. 数据对象（复用 DATA-MODEL `Task §9`）
- **状态机字段** `Task.Status(status) TaskStatus`（`varchar(20);index`，枚举 `NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN`，`UpdateWithStatus` 以 `fromStatus` 为 CAS 守卫）。
- **进度/终态时间** `Task.Progress(progress) string varchar(20)`（`==100%` 判成功）、`Task.FinishTime(finish_time) int64 index`（SUCCESS 写入）、`Task.FailReason(fail_reason) string`（FAILURE 写入）。
- **落库标识** `Task.TaskID(task_id) string varchar(191);index`、`Task.Platform(platform) constant.TaskPlatform varchar(30);index`、`Task.Action(action) string varchar(40);index`、`Task.SubmitTime int64 index`（`InitTask` 落库）。

### 6. 验收标准
- [ ] relay 提交成功后 `InitTask` 落库一行 `Status=NOT_START`、`Progress=0%`，返回 `task_id`。
- [ ] 推进不符合 `NOT_START→SUBMITTED→QUEUED→IN_PROGRESS` 顺序的跃迁 → 本拍不写库（非法跃迁丢弃）。
- [ ] `UpdateWithStatus(fromStatus,…)` 在 `fromStatus` 不匹配（被他进程改写）时 `RowsAffected=0` → 本拍放弃写入，状态不变。
- [ ] `progress==100%` 或上游回报成功 → `Status=TaskStatusSuccess` 且 `FinishTime` 被写入。
- [ ] 上游报错 → `Status=TaskStatusFailure` 且 `FailReason` 被写入。
- [ ] 任务已处于终态（SUCCESS/FAILURE）→ 普通 bulk update 不能覆盖其状态（仅 CAS 守卫路径可写）。

### 7. 所触及页面状态（对齐 §F「任务状态机/详情」）
提交成功态（NOT_START，progress=0%）· 排队/进行中态（SUBMITTED/QUEUED/IN_PROGRESS，progress 递增）· 非法跃迁丢弃态（内部）· CAS 失败回退态（RowsAffected=0 内部）· 成功终态（SUCCESS，FinishTime，触发结算）· 失败终态（FAILURE，FailReason，触发退款）· 未知终态（UNKNOWN）。

---

## AT-2 用户任务列表分页查询与多条件过滤（self-scope 隔离 + Omit channel_id）

- **功能 ID / 优先级**：F-2003、F-2006 / P1
- **来源**：`model/task.go TaskGetAllUserTask`（强制 `Where(user_id=?)` 隔离 C5、`Omit(channel_id)`、`PrivateData json:"-"` 不序列化、`task_id/action/status/platform/时间区间` 过滤、`id desc` 分页）、`GetByTaskId`（user_id+task_id 双条件）、`GetByTaskIds`（按 id 集合批量）
- **角色 / Owner**：登录用户；Owner 模块 = 异步任务中心
- **触发**：用户 `GET /api/task/self`（带过滤参数）

### 1. 场景
用户查任务列表经 `TaskGetAllUserTask` 强制 `Where(user_id=本人)` 隔离（C5），`Omit(channel_id)` 不泄露渠道，`PrivateData`（含 key）以 `json:"-"` 永不序列化。支持 `task_id/action/status/platform/时间区间` 过滤，按 `id desc` 分页。单任务走 `GetByTaskId`（user_id+task_id 双条件保证只能拉本人），批量走 `GetByTaskIds`（按 id 集合返回数组）。这是「过滤装配 → 强制隔离查询 → 字段裁剪 → 分页/单拉/批量分叉」的数据获取流。

### 2. 前置条件
- 用户会话过 C1 + UserAuth。
- 所有查询强制带 `user_id=本人` 隔离（C5）。
- 返回须 `Omit(channel_id)` 且 `PrivateData` 不序列化。

### 3. 主流程（对应 AT-2 节点 qt_in→qt_out）
1. `GET /api/task/self` 带过滤参数（qt_in），判 UserAuth（qt_auth）→否 401（qt_401）。
2. 是则强制 `Where user_id=本人` C5 隔离（qt_scope）→装配过滤 `task_id/action/status/platform/时间区间`（qt_filt）→`Omit channel_id`、`PrivateData json:"-"` 不序列化（qt_omit）。
3. 判查询模式（qt_mode）：列表分页→`Order id desc` Limit/Offset（qt_page）；单任务 fetch→`GetByTaskId` user_id+task_id 双条件（qt_one）；list-by-condition→`GetByTaskIds` 按 id 集合返回数组（qt_batch）。
4. 三路汇到返回任务列表不含 channel_id/key（qt_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| UserAuth 失败 | qt_auth-否 | 拒绝 | 未登录拒绝态（401） |
| 强制隔离 | qt_scope | `Where user_id=本人` | self-scope 隔离态 |
| 带过滤参数 | qt_filt | 装配 status/platform/时间区间 | 过滤生效态 |
| 列表分页 | qt_mode-列表 | `Order id desc` Limit/Offset | 列表分页态 |
| 单任务 fetch | qt_mode-单任务 | `GetByTaskId` 双条件 | 单任务详情态 |
| list-by-condition | qt_mode-批量 | `GetByTaskIds` 集合 | 批量拉取态 |

### 5. 数据对象（复用 DATA-MODEL `Task §9`）
- **隔离/裁剪键** `Task.UserId(user_id) int index`（`TaskGetAllUserTask` 强制 `Where(user_id=本人)`）、`Task.ChannelId(channel_id) int index`（`Omit(channel_id)` 不返回）、`Task.PrivateData(-) TaskPrivateData json`（`json:"-"` 永不序列化）。
- **过滤字段** `Task.TaskID(task_id)`、`Task.Action(action) varchar(40);index`、`Task.Status(status) TaskStatus varchar(20);index`、`Task.Platform(platform) varchar(30);index`、`Task.SubmitTime int64 index`（时间区间）。
- **排序/单拉键** `Task.ID(id) int64`（`Order id desc` 分页）；`GetByTaskId` 以 `user_id + task_id` 双条件；`GetByTaskIds` 按 `id` 集合批量。

### 6. 验收标准
- [ ] 未过 UserAuth → 返回 401，不查询。
- [ ] `TaskGetAllUserTask` 仅返回 `user_id=本人` 的任务，他人任务不出现。
- [ ] 带 `status=SUCCESS`/`platform=suno`/时间区间过滤 → 返回命中子集（过滤生效）。
- [ ] 返回的任务对象不含 `channel_id` 字段，且 `PrivateData`（含 key）不被序列化。
- [ ] 列表分页 `Order id desc`，分页 `total`/`page_size` 正确。
- [ ] 单任务 `GetByTaskId` 以 `user_id+task_id` 双条件，他人 `task_id` 无法拉取；`GetByTaskIds` 按 id 集合返回任务数组。

### 7. 所触及页面状态（对齐 §F「用户任务列表」）
未登录拒绝态（401）· self-scope 隔离态 · 过滤生效态 · 字段裁剪态（channel_id 不出现、PrivateData/key 不泄露）· 列表分页态（终态）· 单任务详情态（终态）· 批量拉取态（终态）。

---

## AT-3 管理端全量任务列表与超时未完成任务后台扫描（CAS 保护退款）

- **功能 ID / 优先级**：F-2004、F-2011 / P1
- **来源**：`model/task.go TaskGetAllTasks`（无 user_id 限制含 channel_id、AdminAuth）、`TaskCountAllTasks`（总数）、`GetTimedOutUnfinishedTasks`（按 cutoff 扫描 `progress!=100% AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff`）、超时退款经 `UpdateWithStatus` CAS
- **角色 / Owner**：管理员（全量看板）/ 系统（后台超时扫描）；Owner 模块 = 异步任务中心
- **触发**：管理员 `GET /api/task`；后台定时任务按 cutoff 扫描

### 1. 场景
管理端 `GET /api/task` 走 `TaskGetAllTasks`（无 user_id 限制，含 channel_id，仅 AdminAuth），支持 `channel_id/user_id/user_ids/platform/action/status/时间区间` 过滤与分页（`TaskCountAllTasks` 出总数）。并行地后台定时任务用 `GetTimedOutUnfinishedTasks` 按 cutoff 扫描命中 `progress!=100% AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff` 的任务批量超时处理，超时标记触发退款时必须走 `UpdateWithStatus`（CAS）避免覆盖已自然完成的任务。两条独立数据流（管理端只读看板、系统后台写扫描）共享同一 Task 表。

### 2. 前置条件
- 管理端接口仅 AdminAuth 可访问。
- 超时扫描命中条件 `progress!=100% AND 非终态 AND submit_time<cutoff`。
- 超时退款写入须经 `UpdateWithStatus`（CAS）守卫。

### 3. 主流程（对应 AT-3 双泳道 ADMIN / SYS）
管理端（ADMIN）：
1. `GET /api/task`（aw_in），判 AdminAuth（aw_auth）→普通用户 403（aw_403）。
2. 管理员则过滤 `channel_id/user_ids/platform/status/时间`（aw_filt）→`TaskGetAllTasks` 含 channel_id（aw_q）→`TaskCountAllTasks` 出总数（aw_cnt）→全量任务分页列表（aw_out）。

系统后台（SYS）：
3. 定时触发 cutoff（sw_tick）→`GetTimedOutUnfinishedTasks`（sw_scan）→判 `progress!=100% AND 非终态 AND submit_time<cutoff`（sw_cond）。
4. 不命中→保留任务不动（sw_keep）；命中→`UpdateWithStatus` CAS 是否成功（sw_cas）→false 已自然完成回 sw_keep；true 标记超时触发退款（sw_to）。
5. 两泳道经 `aw_q -. 共享 Task 表 .- sw_scan` 共享同一 Task 表。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 普通用户访问 `/api/task` | aw_auth-普通用户 | 拒绝 | 越权拒绝态（403） |
| 管理员带跨用户过滤 | aw_filt | `channel_id/user_ids` 过滤 | 全量过滤态 |
| 出总数 | aw_cnt | `TaskCountAllTasks` | 分页总数态 |
| 命中超时条件 | sw_cond-命中 | 进 CAS 判定 | 超时命中态（内部） |
| 命中但 CAS `RowsAffected=0` | sw_cas-false | 跳过（已自然完成） | 超时 CAS 保护态（内部） |
| 命中且 CAS 成功 | sw_cas-true | 标记超时触发退款 | 超时标记退款态 |

### 5. 数据对象（复用 DATA-MODEL `Task §9`）
- **全量过滤键（含 channel_id）** `Task.ChannelId(channel_id) int index`（管理端返回，区别用户自助接口）、`Task.UserId(user_id) int index`（支持 `user_id/user_ids` 跨用户过滤）、`Task.Platform/Action/Status`、`Task.SubmitTime int64 index`（时间区间）。
- **超时扫描条件** `Task.Progress(progress) varchar(20)`（`!=100%`）、`Task.Status(status) TaskStatus`（`NOT IN(FAILURE,SUCCESS)`，即非终态）、`Task.SubmitTime(submit_time) int64 index`（`<cutoff`）。
- **CAS 守卫退款** `UpdateWithStatus(fromStatus,…)`：以 `Task.Status` 为 CAS 守卫，`RowsAffected>0` 才标记超时退款（避免覆盖已自然完成任务）。

### 6. 验收标准
- [ ] 普通用户访问 `GET /api/task` → 返回 403（越权拒绝），不返回数据。
- [ ] 管理员按 `channel_id`/`user_ids` 跨用户过滤 → 命中对应子集，且返回项含 `channel_id`。
- [ ] `TaskCountAllTasks` 返回与过滤条件一致的 `total`，分页正确。
- [ ] `GetTimedOutUnfinishedTasks` 仅命中 `progress!=100% AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff` 的任务。
- [ ] 命中超时任务的退款写入经 `UpdateWithStatus`（CAS）；若该任务已自然完成（`fromStatus` 不匹配）则 `RowsAffected=0` 跳过，不误覆盖。
- [ ] 仅命中且 CAS 成功的任务被标记超时并触发退款。

### 7. 所触及页面状态（对齐 §F「管理端全量看板 + 超时扫描」）
越权拒绝态（普通用户 403）· 全量过滤态（channel_id/user_ids 跨用户）· 含 channel_id 列表态 · 分页总数态（TaskCountAllTasks 出 total 终态）· 超时命中态（内部）· 超时 CAS 保护态（已自然完成被跳过 内部）· 超时标记退款态（命中且 CAS 成功）。

---

## AT-4 任务终态退款差额结算与产物展示（计费上下文重算 + ResultURL 回退）

- **功能 ID / 优先级**：F-2009、F-2010 / P1
- **来源**：`model/task.go` 读 `PrivateData.BillingContext` 重算、`BillingSource`（subscription/wallet）分流退款、`PerCallBilling=true` 跳过差额结算、退款经 `UpdateWithStatus` CAS、`GetResultURL`（优先 `PrivateData.ResultURL`，旧数据回退 `FailReason`）、MJ 返回 `ImageUrl/VideoUrl/Buttons`、视频 `ToVideoStatus`、`PrivateData.Key` 禁返用户
- **角色 / Owner**：系统（退款结算）/ 登录用户（看产物）；Owner 模块 = 异步任务中心
- **触发**：任务轮询到终态或超时（移交自 AT-1/AT-3）

### 1. 场景
任务到终态或超时后读 `PrivateData.BillingContext` 重算实际额度。先判 `PerCallBilling=true`（按次计费）→跳过差额结算；否则按终态类型分流：`SUCCESS` 走重算实际额度多退少不补差额结算，`FAILURE/超时` 按 `BillingSource` 退回预扣（`subscription` 走订阅退款 `SubscriptionPreConsumeRecord refunded`、`wallet` 走令牌额度退回）。所有退款/结算经 `UpdateWithStatus` CAS 守卫执行。产物展示仅 `SUCCESS` 任务：`GetResultURL` 优先取 `PrivateData.ResultURL`，旧数据回退 `FailReason`（历史兼容）；MJ 额外返回 `ImageUrl/VideoUrl/Buttons`，视频走 `ToVideoStatus`。`PrivateData.Key` 禁返用户。这是「终态触发 → 按次/按量分流 → BillingSource 分流退款 → 产物取值回退」的多级分流树。

### 2. 前置条件
- 终态触发自 AT-1（轮询）或 AT-3（超时扫描）。
- `PerCallBilling=true` 的按次任务终态跳过差额结算。
- 退款/结算写入须经 `UpdateWithStatus`（CAS）。
- 仅 `Status=SUCCESS` 任务展示产物，`PrivateData.Key` 不下发。

### 3. 主流程（对应 AT-4 节点 rf_term→rf_out）
1. 任务进入终态/超时（rf_term）→读 `PrivateData.BillingContext`（rf_ctx）。
2. 判 `PerCallBilling=true`（rf_percall）→是按次跳过差额结算（rf_skip）；否按量判终态类型（rf_state）。
3. `SUCCESS`→重算实际额度多退少不补差额结算（rf_diff）；`FAILURE/超时`→判 `BillingSource`（rf_src）：`subscription` 订阅项退回预扣（rf_sub）/ `wallet` 令牌额度退回（rf_wal）。
4. rf_diff / rf_sub / rf_wal 汇到经 `UpdateWithStatus` CAS 守卫写入（rf_cas）。
5. rf_skip / rf_cas 汇到判 `Status=SUCCESS` 展示产物（rf_show）→否不展示产物（rf_none）；是判 ResultURL 来源（rf_url）→新数据取 `PrivateData.ResultURL`（Key 禁返）（rf_priv）/ 旧数据回退 `FailReason`（rf_back）。
6. rf_priv / rf_back 汇到展示 `ImageUrl/VideoUrl/Buttons` + video status（rf_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `PerCallBilling=true` | rf_percall-是 | 跳过差额结算 | 按次计费跳过态（内部） |
| 按量 + `SUCCESS` | rf_state-SUCCESS | 重算多退少不补 | 成功差额结算态 |
| 按量 + `FAILURE/超时` + `subscription` | rf_src-subscription | 订阅项预扣转 refunded | 订阅退款态 |
| 按量 + `FAILURE/超时` + `wallet` | rf_src-wallet | 令牌额度退回 | 钱包退款态 |
| `Status≠SUCCESS` | rf_show-否 | 不展示产物 | 非成功不展示态 |
| ResultURL 旧数据 | rf_url-旧数据 | 回退 `FailReason` | 旧数据回退态 |

### 5. 数据对象（复用 DATA-MODEL `Task §9`）
- **计费上下文** `Task.PrivateData(-) TaskPrivateData json`（`json:"-"` 不下发；内含 `BillingContext`/`BillingSource`/`PerCallBilling`/`ResultURL`/`Key`）：`PerCallBilling=true` 跳过差额；`BillingSource=subscription/wallet` 决定退款路径。
- **终态分流键** `Task.Status(status) TaskStatus`（`SUCCESS` 走差额结算、`FAILURE/超时` 走退款；仅 `SUCCESS` 展示产物）、`Task.Quota(quota) int`（重算实际消费额度，多退少不补）。
- **产物取值** `GetResultURL` 优先 `PrivateData.ResultURL`、旧数据回退 `Task.FailReason(fail_reason) string`；对外 `Task.Data(data) json.RawMessage json`（MJ 含 `ImageUrl/VideoUrl/Buttons`，视频经 `ToVideoStatus`）；`PrivateData.Key` 禁返用户。

### 6. 验收标准
- [ ] `PerCallBilling=true`（按次计费）任务到终态 → 跳过差额结算，不触发退款。
- [ ] 按量任务 `Status=SUCCESS` → 按 `PrivateData.BillingContext` 重算实际 `Quota`，多退少不补。
- [ ] 按量任务 `Status=FAILURE/超时` 且 `BillingSource=subscription` → 订阅预扣记录转 refunded（退回预扣）。
- [ ] 按量任务 `Status=FAILURE/超时` 且 `BillingSource=wallet` → 令牌额度退回预扣。
- [ ] 所有退款/结算写入经 `UpdateWithStatus`（CAS 守卫），不使用无守卫 bulk update。
- [ ] 仅 `Status=TaskStatusSuccess` 任务展示产物；`GetResultURL` 优先取 `PrivateData.ResultURL`，旧数据回退 `FailReason`；`PrivateData.Key` 不出现在返回中。

### 7. 所触及页面状态（对齐 §F「退款结算 + 产物展示」）
按次计费跳过态（内部）· 成功差额结算态（多退少不补）· 订阅退款态（预扣转 refunded）· 钱包退款态（令牌额度退回）· CAS 守卫写入态（内部）· 非成功不展示态（Status≠SUCCESS）· 产物展示态（PrivateData.ResultURL，Key 不泄露 终态）· 旧数据回退态（ResultURL 回退 FailReason 终态）。

---

## AT-5 MJ 多 action 提交路由与不支持连通性测试约束

- **功能 ID / 优先级**：F-2005、F-2006 / P1（F-2005）/ P2（F-2006）
- **来源**：`/mj/submit/:action` 按 action 类型构造 MJ 任务（`imagine/change/blend/describe/modal/shorten/action/edits/video`）记 `Action/Prompt` 进状态机、不支持 action 返参数错误、`channel-test.go unsupportedTestChannelTypes`（Midjourney/MidjourneyPlus 不支持连通性测试）、mjproxy 拉不到账号 + AutoBan 禁用
- **角色 / Owner**：登录用户（提交）/ 系统（路由·测试约束）；Owner 模块 = 异步任务中心
- **触发**：`POST /mj/submit/:action`

### 1. 场景
`/mj/submit/:action` 按 action 类型构造 MJ 任务并记录 `Action/Prompt` 进入状态机：`imagine` 记 Prompt、`change/action` 带 Buttons、`blend` 多图混合、`describe` 反推、`modal/shorten/edits` 各对应任务、`video` 视频任务；不支持的 action 返回参数错误。MJ/MidjourneyPlus 渠道在 `channel-test.go` 的 `unsupportedTestChannelTypes` 中明确不支持连通性测试；mjproxy 拉不到账号实例且 AutoBan 开启时禁用渠道。这是「action 分支路由 → 构造对应任务 → 进状态机」的提交侧分发（区别 AT-1 的状态机视角）。

### 2. 前置条件
- `:action` 须为支持的 MJ action 之一。
- 提交成功后写 MJ 表并移交 AT-1 轮询。
- Midjourney/MidjourneyPlus 渠道不进入连通性测试。

### 3. 主流程（对应 AT-5 节点 mj_in→mj_enq/mj_err）
1. `POST /mj/submit/:action`（mj_in），判 action 类型（mj_sw）。
2. `imagine`→构造 IMAGINE 任务记 Prompt（mj_im）；`change/action`→构造 CHANGE/ACTION 任务带 Buttons（mj_ch）；`blend`→构造 BLEND 多图混合（mj_bl）；`describe`→构造 DESCRIBE 反推（mj_de）；`modal/shorten/edits`→构造对应任务（mj_md）；`video`→构造 VIDEO 任务（mj_vd）；不支持→返回参数错误（mj_err）。
3. 各构造分支汇到写 MJ 表进状态机 AT-1（mj_enq）。
4. 连通性测试时（mj_enq -. 连通性测试时 .-）判渠道类型（mj_test）→Midjourney/MidjourneyPlus 返回 `channel test is not supported`（mj_nots）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `action=imagine` | mj_sw-imagine | 构造 IMAGINE 记 Prompt | imagine 提交态 |
| `action=change/action` | mj_sw-change/action | 构造带 Buttons 任务 | change/action 态 |
| `action=blend/describe/modal/shorten/edits/video` | mj_sw 对应分支 | 构造对应任务 | 各 action 态 |
| 不支持的 action | mj_sw-不支持 | 返回参数错误 | 不支持 action 态 |
| 构造完成 | mj_enq | 写 MJ 表移交 AT-1 | 进状态机态 |
| 渠道类型 Midjourney/MidjourneyPlus | mj_test | 返回 not supported | 连通性测试不支持态 |

### 5. 数据对象（复用 DATA-MODEL `Task §9`）
- **action 路由键** `Task.Action(action) string varchar(40);index`（`imagine/change/blend/describe/modal/shorten/action/edits/video`，不支持值返参数错误）、`Task.Platform(platform) varchar(30);index`（MJ 平台标识）。
- **任务载荷** `Task.Properties(properties) Properties json`（记 Prompt/Buttons 等 action 派生属性）、`Task.Data(data) json.RawMessage json`（对外产物数据）。
- **入状态机** `Task.Status(status) TaskStatus`（写库初始 `NOT_START`，移交 AT-1）、`Task.SubmitTime int64 index`、`Task.TaskID(task_id) varchar(191);index`（返回 task_id）。

### 6. 验收标准
- [ ] `POST /mj/submit/imagine` → 新增一行 `Action=IMAGINE` 任务且记录 Prompt，返回 `task_id`。
- [ ] `change/action` → 构造带 Buttons 的派生任务；`blend/describe/modal/shorten/edits/video` → 各构造对应 action 任务。
- [ ] 不支持的 `:action` → 返回参数错误，不写 MJ 表。
- [ ] 构造成功的任务写入 MJ 表后移交 AT-1 状态机轮询（初始 `NOT_START`）。
- [ ] 对 Midjourney/MidjourneyPlus 渠道发起连通性测试 → 返回 `channel test is not supported`（在 `unsupportedTestChannelTypes` 中）。
- [ ] mjproxy 拉不到账号实例且渠道 AutoBan 开启 → 触发渠道禁用（复用 CH-3）。

### 7. 所触及页面状态（对齐 §F「MJ 提交路由」）
imagine 提交态（Action=IMAGINE，返回 task_id）· change/action 态（带 Buttons）· blend/describe/modal/shorten/edits/video 各 action 态 · 不支持 action 态（参数错误）· 进状态机态（写 MJ 表移交 AT-1 终态）· 连通性测试不支持态（Midjourney/MidjourneyPlus not supported）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-2001 | AT-1 |
| F-2002 | AT-1 |
| F-2007 | AT-1 |
| F-2008 | AT-1 |
| F-2003 | AT-2 |
| F-2006 | AT-2 / AT-5 |
| F-2004 | AT-3 |
| F-2011 | AT-3 |
| F-2009 | AT-4 |
| F-2010 | AT-4 |
| F-2005 | AT-5 |

无 `[BLOCKER]`。

---

## 补充：Task UNKNOWN 终态的预扣额度退款处置（状态机缺口补漏）

> 评审发现 AT-1 状态机未覆盖 `UNKNOWN` 终态的资金处置。补充说明如下（不新增功能 ID，归属 AT-1 / F-2001 计费侧）。

### 背景与问题
`TaskStatus` 枚举（DATA-MODEL §9）含 `UNKNOWN`，但轮询结算 `service/task_polling.go` 的状态 `switch` 只识别 `SUBMITTED/QUEUED/IN_PROGRESS/SUCCESS/FAILURE` 五态：`SUCCESS` 触发结算（`shouldSettle=true`）、`FAILURE` 触发退款（`shouldRefund=true`，`quota!=0` 时），而 `UNKNOWN` 落入 `default` 分支返回 `error("unknown task status %s")`——**当次轮询既不结算也不退款，任务保持未完成态**，预扣额度仍处于冻结状态不会就地返还。

### 处置规则（兜底退款由超时清扫兜底）
- **不就地退款**：上游返回 `UNKNOWN`（含 mjproxy 返回未知状态）时，本次轮询 `default` 分支不推进终态、不动额度，任务留在未完成集合等待下一轮重试拉取真实状态——避免上游短暂返回 UNKNOWN 就误退款。
- **超时强制 FAILURE + 退款**：任务持续 `UNKNOWN`/未完成超过 `constant.TaskTimeoutMinutes` 分钟，`sweepTimedOutTasks` 用 per-task CAS（`UpdateWithStatus(oldStatus)`）将其强制置 `FAILURE`、`Progress=100%`、`FailReason="任务超时（N分钟）"`，CAS 抢占成功（`won=true`）且**非 legacy 任务**（`SubmitTime >= 2026-02-22`）且 `Quota != 0` 时调 `RefundTaskQuota` 全额退预扣。
- **legacy 任务不退款**：`SubmitTime < legacyTaskCutoff(1740182400)` 的旧系统遗留任务，`FailReason="任务超时（旧系统遗留任务，不进行退款，请联系管理员）"`，**跳过 RefundTaskQuota**，由管理员人工处理。
- **退款落账范围**：`RefundTaskQuota` 退还顺序 = ① 资金来源（`taskAdjustFunding(-quota)`，钱包或订阅按 BillingSource 回补）→ ② 令牌额度（`taskAdjustTokenQuota(-quota)`）→ ③ 写 `Log`（`Type=6(Refund)`、`Quota=task.Quota`、`Other` 含 `task_id`/`reason`）；`quota==0` 直接 return 不动账。
- **CAS 防重**：`sweepTimedOutTasks` 与正常轮询并发时，CAS `won=false`（已被正常轮询推进）则跳过本次清扫与退款，杜绝重复退款。

### 验收标准（二元可测）
- [ ] 上游单次返回 `UNKNOWN` → 该任务不置终态、`User.Quota`/`Token` 额度不变、不写 Refund 日志，任务留在未完成集合。
- [ ] 非 legacy 任务持续未完成超 `TaskTimeoutMinutes` → `Task.Status` 被 CAS 置 `FAILURE`、`Progress=100%`，且 `Quota!=0` 时调 `RefundTaskQuota` 全额退回资金来源 + 令牌额度，写 `Log.Type=6`。
- [ ] legacy 任务（`SubmitTime < 1740182400`）超时 → 置 `FAILURE` 但 **不退款**，`FailReason` 含「旧系统遗留任务，不进行退款」。
- [ ] 超时清扫与正常轮询并发推进同一任务 → CAS 仅一方 `won=true` 执行退款，另一方 `won=false` 跳过，`Log.Type=6` 仅一条。
- [ ] `Task.Quota==0`（免费任务）超时置 FAILURE → `RefundTaskQuota` 直接 return，不写 Refund 日志。
