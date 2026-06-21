# PRD — 部署管理（io.net 集成，FL-deploy）

> 分片：部署管理（io.net 外部 SaaS 代理）。对应流程图 `flow/FL-deploy.md`（5 图）、状态矩阵 `PAGE-STATE-MATRIX.md`。
> 数据对象：开关读 `DATA-MODEL.md §15 Option`（`model_deployment.ionet.enabled/api_key`）；部署/容器对象为 io.net 上游代理结构（`ionet` 包 `Deployment/Container`，非本地 DB 表，字段以 controller 返回为准）。
> 跨切面契约见 `OVERALL-FLOW.md §3`：`deploymentsRoute` 挂 AdminAuth；多数走 `EnterpriseClient`，部分走普通 `Client`。**实际计费以 io.net 为准**。
> 本片覆盖功能 ID：**F-3039 / F-3040 / F-3041 / F-3042 / F-3043 / F-3044 / F-3045 / F-3046 / F-3047 / F-3048 / F-3049 / F-3050 / F-3051 / F-3052 / F-3053 / F-3054 / F-3055 / F-3056**。

---

## DP-1 io.net 集成开关查询与连接测试（三态派生 + key 来源回退）

- **功能 ID / 优先级**：F-3039、F-3040 / P1
- **来源**：FC-098（`GetModelDeploymentSettings` 读 `OptionMap[ionet.enabled/api_key]`；`TestIoNetConnection`：req 空回退 stored key、`NewEnterpriseClient`、`GetMaxGPUsPerContainer`、`APIError` 透传 message）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 部署管理
- **触发**：管理员打开部署管理页（读设置）→ 点「测试连接」提交 api_key

### 1. 场景
部署管理依赖 io.net 是否就绪。`GetModelDeploymentSettings` 从 `OptionMap` 派生三态：`enabled`（开关等于 true）、`configured`（api_key 非空）、`can_connect`（enabled 且 configured）。连接测试 `TestIoNetConnection` 用 `NewEnterpriseClient` 验证并 `GetMaxGPUsPerContainer`：请求 key 为空时回退 stored key（两者皆空则「api_key is required」），key 无效则透传 `APIError.Message`（空 message 兜底「failed to validate api key」），成功返回 `hardware_count/total_available`。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- `OptionMap[model_deployment.ionet.enabled]`、`[...api_key]` 已加载（可为空）。
- 测试请求可携 `api_key`（为空则回退 stored key）。

### 3. 主流程（对应 DP-1 节点 ds_in→ds_ok）
1. 打开部署管理页（ds_in），`GetModelDeploymentSettings` 读 `OptionMap`（ds_get）。
2. 判 `ionet.enabled=true`（ds_en）：否 → `enabled=false, can_connect=false`（ds_off）。
3. 是 → 判 `api_key` 非空（ds_cfg）：否 → `configured=false, can_connect=false`（ds_nocfg）；是 → `configured=true, can_connect=true`（ds_ready）。
4. 点测试连接（ds_test），判 `req.APIKey` 为空（ds_key）：空且无 stored key → 「api_key is required」（ds_e1）；空回退 stored / 非空 → `NewEnterpriseClient` 验证（ds_val）。
5. 判 `GetMaxGPUsPerContainer` 成功（ds_res）：失败 → 透传 `APIError.Message`（空兜底 failed to validate api key，ds_e2）；成功 → 返回 `hardware_count/total_available`（ds_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `ionet.enabled=false` | ds_en-否 | 派生 enabled=false | 未启用态（can_connect=false） |
| enabled 但 api_key 空 | ds_cfg-否 | 派生 configured=false | 已启用未配置态 |
| 测试 req 空且无 stored key | ds_key-空且无 stored | 拒绝测试 | key 缺失态「api_key is required」 |
| key 无效 | ds_res-失败 | 透传 APIError.Message 或兜底 | key 无效态 |
| 验证成功 | ds_res-成功 | 返回硬件量 | 测试成功态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option + ionet 设置结构）
- **读** `Option`：`model_deployment.ionet.enabled`（bool）、`model_deployment.ionet.api_key`（string，敏感键，列表读取时被剔除）。
- **派生返回**：`provider=io.net`（固定）、`enabled`、`configured`、`can_connect`。
- **测试返回**：`hardware_count`、`total_available`（`GetMaxGPUsPerContainer` 结果）。

### 6. 验收标准
- [ ] `ionet.enabled=false` → 返回 `enabled=false`、`can_connect=false`、`provider=io.net`。
- [ ] `enabled=true` 但 `api_key` 为空 → 返回 `configured=false`、`can_connect=false`。
- [ ] `enabled=true` 且 `api_key` 非空 → 返回 `configured=true`、`can_connect=true`。
- [ ] 测试时 req `api_key` 为空且无 stored key → 返回「api_key is required」。
- [ ] 测试用无效 key → 返回上游 `APIError.Message`（message 为空时返回「failed to validate api key」）；用有效 key → 返回 `hardware_count`、`total_available`。

### 7. 所触及页面状态（对齐 DP-1 集成开关+连接测试）
未启用态（enabled=false，can_connect=false）· 已启用未配置态（configured=false）· 就绪态（can_connect=true）· key 缺失态（「api_key is required」）· key 无效态（透传/兜底）· 测试成功态（hardware_count/total_available）。

---

## DP-2 部署生命周期（创建→运行→续期/更新/重命名→删除终止）

- **功能 ID / 优先级**：F-3044、F-3045、F-3046、F-3047、F-3048 / P1（F-3045/3046/3047/3048 P2）
- **来源**：FC-098/FC-100（`CreateDeployment`→`DeployContainer`、`ExtendDeployment`、`UpdateDeployment`、`UpdateDeploymentName`(CheckClusterNameAvailability)、`DeleteDeployment`(requireDeploymentID)）
- **角色 / Owner**：管理员（AdminAuth，多数 EnterpriseClient）；Owner 模块 = 部署管理
- **触发**：管理员新建部署 / 对运行中部署续期、更新、重命名、删除

### 1. 场景
部署对象的状态生命周期：`CreateDeployment` 绑定 `DeploymentRequest` → `DeployContainer` 下发容器，成功进运行态（返回 `deployment_id/status`）。运行态可自环维护——续期（`ExtendDeployment` 延长 compute 分钟）、更新（`UpdateDeployment` 配置变更）、重命名（`UpdateDeploymentName`：空名拒绝、`CheckClusterNameAvailability` 预检不可用则拒）。删除走 `DeleteDeployment`（`requireDeploymentID` → 发终止请求 → 「termination requested」）进终止态。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- io.net 已就绪（`can_connect=true`，见 DP-1）。
- 续期/更新/重命名/删除均需有效 `deployment_id`。

### 3. 主流程（对应 DP-2 状态机）
1. `[*]→创建中`：`CreateDeployment` 绑定 `DeploymentRequest`。
2. 创建中：`ShouldBindJSON` 非法 → 绑定失败态（`[*]` 终止）；`DeployContainer` 成功 → 运行中态（返回 `deployment_id/status`）。
3. 运行中自环：续期 `ExtendDeployment`（更新 `compute_minutes_remaining`）；更新 `UpdateDeployment`（配置变更）；重命名 `UpdateDeploymentName`（空名/不可用 → 命名拒绝态，重试可用名回运行中）。
4. 运行中 → 终止中：`DeleteDeployment`（`requireDeploymentID`）→ 已终止态（`termination requested successfully`）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 创建请求体非法 | 创建中→绑定失败 | `ShouldBindJSON` 报错，不下发 | 绑定失败态 |
| 创建成功 | 创建中→运行中 | `DeployContainer` 返回 id/status | 运行中态 |
| 续期 | 运行中自环 | `ExtendDeployment` 延长 compute 分钟 | 续期成功态（compute_minutes_remaining 更新） |
| 重命名空名/不可用 | 运行中→命名拒绝 | 空名「cannot be empty」/不可用「is not available」/预检失败 | 命名拒绝态 |
| 删除 | 运行中→终止中→已终止 | `DeleteDeployment` 发终止请求 | 已终止态（termination requested） |

### 5. 数据对象（io.net 代理结构 Deployment）
- **写/读** `Deployment`（io.net 代理）：`deployment_id`、`status`、`compute_minutes_remaining`、`time_remaining`（`ComputeMinutesRemaining` 换算）。
- **创建请求** `DeploymentRequest`（`ShouldBindJSON` 绑定）；**续期请求** `ExtendDurationRequest`；**更新请求** `UpdateDeploymentRequest`。
- **重命名** `name`（必填非空，`CheckClusterNameAvailability` 预检）。

### 6. 验收标准
- [ ] `CreateDeployment` 请求体非法 → 返回绑定错误，不下发容器。
- [ ] `CreateDeployment` 合法 → 返回 `deployment_id`、`status`，进运行中态。
- [ ] 对运行中部署 `ExtendDeployment` → 返回更新后的 `compute_minutes_remaining`、`time_remaining`。
- [ ] `UpdateDeploymentName` 空名 → 返回「deployment name cannot be empty」；不可用名 → 返回「name is not available」。
- [ ] `DeleteDeployment`（有效 deployment_id）→ 返回 termination requested successfully；缺 id → 返回「deployment ID is required」。

### 7. 所触及页面状态（对齐 DP-2 部署生命周期）
绑定失败态 · 运行中态（deployment_id/status）· 续期成功态（compute_minutes_remaining 更新）· 配置更新态 · 命名拒绝态（空名/不可用/预检失败）· 终止中态 · 已终止态（termination requested）。

---

## DP-3 部署列表查询与状态计数聚合（分页 + status_counts + 本地名称过滤 + 详情）

- **功能 ID / 优先级**：F-3041、F-3042、F-3043 / P1（F-3042 P2）
- **来源**：FC-099/FC-100（`GetAllDeployments`→`ListDeployments(created_at desc)`、`mapIoNetDeployment`、`computeStatusCounts`；`SearchDeployments`：status 透传、keyword 本地 Name Contains；`GetDeployment`：requireDeploymentID）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 部署管理
- **触发**：管理员浏览部署列表/搜索/点开详情

### 1. 场景
部署看板数据获取流：`ListDeployments`（按 `created_at desc`）→ `mapIoNetDeployment` 映射 `time_remaining/hardware_info/compute_minutes_remaining` → `computeStatusCounts` 聚合各状态计数（running/completed/failed…含 all）。搜索时 `status` 透传上游过滤、`keyword` 在**本地**按 `Name Contains`（小写包含）过滤，keyword 非空时 `total=len(filtered)`。详情 `GetDeployment`（`requireDeploymentID`）返回 `total_gpus/total_containers/compute_minutes_served/locations/container_config`。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- io.net 就绪。
- 详情请求须带 `deployment_id`。

### 3. 主流程（对应 DP-3 节点 dl_in→dl_out）
1. `GET 部署列表/搜索`（dl_in）→ `ListDeployments SortBy=created_at desc`（dl_up）。
2. `mapIoNetDeployment` 换算 `time_remaining/hardware_info`（dl_map）→ `computeStatusCounts` 聚合 running/completed/failed/all（dl_cnt）。
3. 判请求维度（dl_branch）：纯列表 → 分页 page/page_size/total（dl_page）；status 过滤 → 透传上游（dl_st）；keyword 搜索（dl_kw）→ 空返全部（dl_all）/非空本地 Name Contains 小写过滤 total=len(filtered)（dl_local）；查详情（dl_detail）→ id 空「deployment ID is required」（dl_e）/有 id 返回 total_gpus/locations/container_config（dl_one）。
4. 各分支汇入部署看板渲染（dl_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 纯列表 | dl_page | 按 created_at desc 分页 | 列表分页态 + 状态计数态 |
| status 过滤 | dl_st | status 透传上游过滤 | status 过滤态 |
| keyword 非空 | dl_local | 本地 Name Contains 小写过滤，total 修正 | keyword 本地过滤态 |
| keyword 空 | dl_all | 返回全部 | 全部列表 |
| 详情 id 空 | dl_detail-id 空 | 返回必填错误 | 详情缺 id 态 |
| 详情有 id | dl_one | 返回 GPU/容器/地域/配置明细 | 详情态 |

### 5. 数据对象（io.net 代理结构 Deployment）
- **列表项** `Deployment`：`time_remaining`、`hardware_info`、`compute_minutes_remaining`、`status`。
- **聚合** `status_counts`：含 `all` 与各状态（running/completed/failed…）计数。
- **搜索** `status`（上游透传过滤）、`keyword`（本地 `Name Contains` 小写过滤，非空时 `total=len(filtered)`）。
- **详情** `total_gpus`、`total_containers`、`compute_minutes_served`、`locations`、`container_config`、`gpus_per_container`、`amount_paid`、`completed_percent`。

### 6. 验收标准
- [ ] 列表按 `created_at desc` 排序返回，含 `page/page_size/total` 分页字段。
- [ ] 响应含 `status_counts`，且含 `all` 与各具体状态计数。
- [ ] 列表项 `time_remaining` 由 `compute_minutes_remaining` 经 `ComputeMinutesRemaining` 换算（时分）。
- [ ] 搜索带 `keyword` → 仅返回名称小写包含该关键词的部署，`total=过滤后数量`；keyword 为空 → 返回全部。
- [ ] 详情请求缺 `deployment_id` → 返回「deployment ID is required」；带有效 id → 返回 `total_gpus/locations/container_config`。

### 7. 所触及页面状态（对齐 DP-3 列表/搜索/详情）
列表分页态（created_at desc）· 状态计数态（status_counts 含 all）· time_remaining 换算态 · status 过滤态 · keyword 本地过滤态/keyword 空返回全部 · 详情缺 id 态 · 详情态（total_gpus/locations/container_config）。

---

## DP-4 容器列表/详情/日志查询（三级钻取 + 双 ID 校验 + 日志参数规整）

- **功能 ID / 优先级**：F-3054、F-3055、F-3056 / P2
- **来源**：FC-100（`ListDeploymentContainers`(requireDeploymentID)、`GetContainerDetails`(requireDeploymentID+requireContainerID, details==nil)、`GetDeploymentLogs`(container_id 必填, limit 默认100上限1000, RFC3339 时间, follow)）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 部署管理
- **触发**：管理员进入某部署的容器视图 → 点开容器详情 → 查看日志

### 1. 场景
以容器为中心的三级钻取流（区别 DP-3 的部署级看板）：容器列表 `ListDeploymentContainers`（`requireDeploymentID`，Workers 映射 `container_id/device_id/uptime_percent/public_url/events(time,message)`，无容器返回空数组 total=0）。容器详情 `GetContainerDetails`（deployment 与 container 双 ID 必填，`details==nil` 返回「container details not found」）。日志 `GetDeploymentLogs`（`container_id` 必填，`limit` 默认 100 上限 1000，`level/stream/cursor/follow` 过滤，`start_time/end_time` 按 RFC3339 解析，非法时间被忽略，用普通 Client）。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- 列表/详情需有效 `deployment_id`；详情/日志另需有效 `container_id`。
- 日志时间参数须为 RFC3339 格式（非法被忽略）。

### 3. 主流程（对应 DP-4 节点 ct_in→ct_out）
1. 进入容器视图（ct_in）→ `ListDeploymentContainers requireDeploymentID`（ct_list）。
2. 判有容器（ct_have）：无 → 空数组 total=0（ct_empty→视图就绪 ct_done）；有 → Workers 映射 container_id/uptime/public_url/events（ct_map）。
3. 点某容器（ct_pick），判 `requireDeploymentID & requireContainerID`（ct_did）：任一空 → 对应必填错误（ct_e1）；双 ID 齐 → 判 `details!=nil`（ct_det）：nil → 「container details not found」（ct_e2）；有 → 返回 events/public_url/uptime_percent（ct_show）。
4. 查日志 `GetDeploymentLogs`（ct_log），判 `container_id` 必填（ct_cid）：缺 → 「container_id parameter is required」（ct_e3）；有 → limit 默认 100 上限 1000 截断（ct_lim）→ 判时间 RFC3339（ct_time）：非法字符串忽略继续（ct_ignore）；合法/follow=true → level/stream/cursor 过滤跟随（ct_fetch）→ 返回日志流（ct_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 无容器 | ct_have-无 | 返回空数组 total=0 | 空容器态 |
| 详情缺 deployment/container id | ct_did-任一空 | 返回对应必填错误 | 详情缺 ID 态 |
| `details==nil` | ct_det-nil | 返回 not found | 详情不存在态 |
| 日志缺 container_id | ct_cid-缺 | 返回必填错误 | 日志缺 container_id 态 |
| limit>1000 | ct_lim | 截断为 1000 | limit 截断态 |
| 时间非 RFC3339 | ct_time-非法 | 忽略非法时间继续 | 非法时间忽略态 |

### 5. 数据对象（io.net 代理结构 Container）
- **列表项** `Container`：`container_id`、`device_id`、`uptime_percent`、`public_url`、`events`（数组，含 `time/message`）。
- **详情** `GetContainerDetails`：`events`、`public_url`、`uptime_percent`（`details==nil` → not found）。
- **日志参数**：`container_id`（必填）、`limit`（默认 100，上限 1000）、`level/stream/cursor/follow`、`start_time/end_time`（RFC3339，非法忽略）。

### 6. 验收标准
- [ ] 部署无容器 → `ListDeploymentContainers` 返回空数组且 `total=0`。
- [ ] 容器详情缺 `deployment_id` 或 `container_id` → 返回对应必填错误；`details==nil` → 返回「container details not found」。
- [ ] 日志缺 `container_id` → 返回「container_id parameter is required」。
- [ ] 日志 `limit=5000` → 实际被截断为 1000。
- [ ] 日志 `start_time` 传非 RFC3339 字符串 → 被忽略，请求继续（不报错）；`follow=true` → 启用跟随。

### 7. 所触及页面状态（对齐 DP-4 容器列表/详情/日志）
空容器态（total=0）· 容器列表态（container_id/uptime/public_url/events）· 详情缺 ID 态 · 详情不存在态（details==nil）· 容器详情态 · 日志缺 container_id 态 · limit 截断态（>1000→1000）· 非法时间忽略态 · 日志流态（level/stream/cursor，follow 跟随）。

---

## DP-5 部署资源选型查询（硬件/地域/副本/价格预估/集群名可用性）

- **功能 ID / 优先级**：F-3049、F-3050、F-3051、F-3052、F-3053 / P1（F-3050/3051/3053 P2）
- **来源**：FC-099/FC-100（`GetHardwareTypes`、`GetLocations`(普通 Client, total=0 回退 len)、`GetAvailableReplicas`(hardware_id 必填 Atoi>0, gpu_count 默认1)、`GetPriceEstimation`、`CheckClusterNameAvailability`(name 必填)）
- **角色 / Owner**：管理员（AdminAuth）；Owner 模块 = 部署管理
- **触发**：管理员新建部署时进入资源选型向导

### 1. 场景
新建部署前的并列资源选型查询面板（区别生命周期与看板）：硬件类型 `GetHardwareTypes`（返回 `hardware_types/total=类型数/total_available=总可用副本`）；地域 `GetLocations`（普通 Client，上游 total=0 时回退 `len(Locations)`）；可用副本 `GetAvailableReplicas`（`hardware_id` 必填且 `Atoi>0`，缺失/非法分别报错，`gpu_count` 默认 1 非正回退 1）；价格预估 `GetPriceEstimation`（绑定 `PriceEstimationRequest`，实际计费以 io.net 为准）；集群名 `CheckClusterNameAvailability`（`name` 必填，返回 `available/name`）。多个独立校验入口汇成「配置就绪→可创建部署」。

### 2. 前置条件
- 操作者为管理员（AdminAuth）。
- io.net 就绪。
- 查副本须先选硬件（`hardware_id`）；查集群名须填 `name`。

### 3. 主流程（对应 DP-5 节点 rs_in→rs_pout）
1. 新建部署进资源选型向导（rs_in）→ `GetHardwareTypes`（rs_hw）返回 `hardware_types/total/total_available`（rs_hwout）。
2. 选硬件后查副本 `GetAvailableReplicas`（rs_rep）：`hardware_id` 缺 → 「hardware_id parameter is required」（rs_e1）；非法/<=0 → 「invalid hardware_id parameter」（rs_e2）；`gpu_count` 非正 → 回退 1（rs_g1）；合法 → 返回可用副本数（rs_repok）。
3. `GetLocations`（rs_loc，普通 Client）→ locations，total=0 回退 `len(Locations)`（rs_locout）。
4. 输入集群名 `CheckClusterNameAvailability`（rs_name），判 name 非空（rs_nm）：空 → 「name parameter is required」（rs_e3）；有 → 返回 `available/name`（rs_nmout）。
5. 副本/地域/集群名就绪 → `GetPriceEstimation`（rs_price，绑定 `PriceEstimationRequest`）→ 返回价格预估（rs_pout，实际计费以 io.net 为准）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 副本查询缺 hardware_id | rs_rep-缺 | 返回必填错误 | 副本缺 hardware_id 态 |
| hardware_id 非法/<=0 | rs_rep-非法 | 返回参数错误 | 副本非法 hardware_id 态 |
| gpu_count 非正 | rs_g1 | 回退 gpu_count=1 | gpu_count 回退态 |
| 地域上游 total=0 | rs_locout | 回退 len(Locations) | 地域态 |
| 集群名缺 name | rs_nm-空 | 返回必填错误 | 集群名缺 name 态 |
| 价格预估 | rs_pout | 返回 priceResp（io.net 为准） | 价格预估态 |

### 5. 数据对象（io.net 代理结构 Deployment 选型）
- **硬件** `hardware_types`（数组）、`total`（类型数）、`total_available`（总可用副本）。
- **副本** `GetAvailableReplicas`：入参 `hardware_id`（必填，`Atoi>0`）、`gpu_count`（默认 1，非正回退 1）；出参可用副本数。
- **地域** `locations`、`total`（上游 0 回退 `len(Locations)`）。
- **价格** `PriceEstimationRequest`（`ShouldBindJSON` 绑定）→ `priceResp`（实际计费以 io.net 为准）。
- **集群名** `name`（必填）→ `available`（bool）、`name`。

### 6. 验收标准
- [ ] `GetHardwareTypes` → 返回 `hardware_types` 数组、`total`=类型数、`total_available`=总可用副本数。
- [ ] `GetAvailableReplicas` 缺 `hardware_id` → 返回「hardware_id parameter is required」；`hardware_id` 非数字或 <=0 → 返回「invalid hardware_id parameter」。
- [ ] `GetAvailableReplicas` 的 `gpu_count` 传 0 或负 → 实际按 1 计算。
- [ ] `GetLocations` 上游 `total=0` → 返回的 `total` 回退为 `len(Locations)`。
- [ ] `CheckClusterNameAvailability` 缺 `name` → 返回「name parameter is required」；带 name → 返回 `available` 布尔与 `name`。

### 7. 所触及页面状态（对齐 DP-5 资源选型）
硬件类型态（hardware_types/total/total_available）· 副本缺 hardware_id 态 · 副本非法 hardware_id 态 · gpu_count 回退态 · 可用副本态 · 地域态（total=0 回退列表长度）· 集群名缺 name 态 · 集群名可用性态（available/name）· 价格预估态（priceResp，io.net 为准）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-3039 | DP-1 |
| F-3040 | DP-1 |
| F-3041 | DP-3 |
| F-3042 | DP-3 |
| F-3043 | DP-3 |
| F-3044 | DP-2 |
| F-3045 | DP-2 |
| F-3046 | DP-2 |
| F-3047 | DP-2 |
| F-3048 | DP-2 |
| F-3049 | DP-5 |
| F-3050 | DP-5 |
| F-3051 | DP-5 |
| F-3052 | DP-5 |
| F-3053 | DP-5 |
| F-3054 | DP-4 |
| F-3055 | DP-4 |
| F-3056 | DP-4 |

无 `[BLOCKER]`。
