# PRD — 渠道管理与上游路由（FL-channel）

> 分片：渠道管理与上游路由 D7/D10。对应流程图 `flow/FL-channel.md`、状态矩阵 `PAGE-STATE-MATRIX.md §G`。
> 数据对象字段一律复用 `DATA-MODEL.md §3 Channel / §4 Ability`。
> 关键常量：`ChannelStatusAutoDisabled`、`common.RetryTimes`、`priorityRetry`。
> 本片覆盖功能 ID：**F-2016 / F-2020 / F-2021 / F-2023 / F-2024 / F-2028 / F-2029 / F-2034 / F-2035 / F-2036 / F-2037 / F-2050 / F-2051 / F-2052 / F-2053**。
> 兼容层+计费反溯（2026-06）新增块：**CH-6 供应渠道池（A→B 绑多渠道，复用 Ability/Channel 路由）/ CH-7 供应商成本配置（ChannelModelCost 挂渠道×B 超管手填）**（权威源 `COMPAT-BILLING-DECISIONS.md §3/§4`、`reviews/COMPAT-LAYER-ARCHITECTURE.md §3.3`、`reviews/BILLING-MODEL-ARCHITECTURE.md §2`、`DATA-MODEL.md §19`）。

---

## CH-1 渠道创建/编辑（多 Key 模式 + 模型映射落库）

- **功能 ID / 优先级**：F-2016、F-2020、F-2021 / P0（F-2016）/ P1（F-2020/F-2021）
- **来源**：FC-042/FC-047（`model/channel.go` Channel struct、`ChannelInfo` 多 key 子结构、`MultiKeyModeRandom/Polling`、`ModelMapping *string text`、`controller/channel.go` CRUD）
- **角色 / Owner**：管理员；Owner 模块 = 渠道管理与上游路由
- **触发**：管理员填渠道表单提交（过 AdminAuth）

### 1. 场景
管理员创建/编辑一个上游渠道，需填类型、密钥、支持模型、分组、优先级、权重等核心配置，默认 `Status=1(启用)`。可开启多 Key 模式：随机模式存 `MultiKeyStatusList` 随机选 Key，轮询模式存 `PollingIndex` 轮询并跳过禁用 Key。`ModelMapping` 为 JSON 文本（空则不重定向）。表单写库前需对必填字段与 JSON 映射做校验，校验通过后渠道才被选渠引擎纳入候选集（CH-2 的输入）。

### 2. 前置条件
- 管理员会话过 C1 + AdminAuth。
- `Type/Key/Models` 为必填字段。
- 多 Key 模式由 `ChannelInfo.IsMultiKey` 与 `MultiKeyMode` 决定分支落库。

### 3. 主流程（对应 CH-1 节点 cc_form→cc_ok）
1. 管理员填渠道表单（cc_form），判 `Type/Key/Models` 必填是否齐全（cc_v1）。
2. 缺字段→必填校验失败高亮缺项（cc_e1）；齐全→判是否开启多 Key（cc_v2）。
3. 否（单 Key）→单 Key 直存（cc_single）；是→判 `MultiKeyMode`（cc_mode）：随机→存 `MultiKeyStatusList` 随机选 Key（cc_rand）；轮询→存 `PollingIndex` 轮询跳过禁用 Key（cc_poll）。
4. 三路合流判是否配置 `ModelMapping`（cc_map）：JSON 非法→映射解析失败拒绝保存（cc_e2）；空→不重定向直存（cc_nomap）；合法 JSON→存 `ModelMapping` 文本（cc_yesmap）。
5. 两路合流写 `Channel` 表 `Status=1` 默认启用（cc_persist）→渠道已保存态进入选渠候选（cc_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `Type/Key/Models` 缺字段 | cc_v1-缺字段 | 拒绝保存 | 必填校验失败高亮缺项 |
| 多 Key 随机模式 | cc_mode-随机 | 存 `MultiKeyStatusList` | 随机选 Key 模式态 |
| 多 Key 轮询模式 | cc_mode-轮询 | 存 `PollingIndex` 跳过禁用 Key | 轮询模式态 |
| `ModelMapping` JSON 非法 | cc_map-JSON非法 | 拒绝保存 | 映射解析失败态 |
| `ModelMapping` 为空 | cc_map-空 | 不重定向直存 | 无映射态 |

### 5. 数据对象（复用 DATA-MODEL §3 Channel）
- **写** `Channel`：`Type(type)`、`Key(key)`（`not null`）、`Models(models)`、`Group(group)`（`default:'default'`）、`Priority(priority) *int64`（`default:0`）、`Weight(weight) *uint`（`default:0`）、`Status(status)`=1 默认启用、`Name(name)`、`ModelMapping(model_mapping) *string text`（空=不重定向）。
- **多 Key 子结构** `ChannelInfo`：`IsMultiKey(is_multi_key)`、`MultiKeySize(multi_key_size)`、`MultiKeyStatusList`（随机模式 key index→status）、`MultiKeyPollingIndex`（轮询模式）、`MultiKeyMode`。

### 6. 验收标准
- [ ] 缺 `Type`/`Key`/`Models` 任一 → 拒绝保存，高亮缺项，不写 `Channel` 表。
- [ ] 单 Key 渠道保存 → `ChannelInfo.IsMultiKey=false`，`Key` 直存。
- [ ] 多 Key 随机模式保存 → 写 `MultiKeyStatusList`，`MultiKeyMode=随机`。
- [ ] 多 Key 轮询模式保存 → 写 `MultiKeyPollingIndex`，轮询选 Key 时跳过 status≠启用 的 Key。
- [ ] `ModelMapping` 填非法 JSON → 拒绝保存（映射解析失败）；填空 → 不重定向直存。
- [ ] 创建成功 → `Channel.Status=1`，可被选渠引擎纳入候选。

### 7. 所触及页面状态（对齐 §G「渠道创建/编辑」）
渠道表单编辑态（Type/Key/Models/Group/Priority/Weight）· 必填校验失败态（缺字段高亮）· 单 Key 直存态 · 多 Key 随机模式态 / 多 Key 轮询模式态 · 映射解析失败态（JSON 非法）· 无映射态（ModelMapping 空）· 渠道已保存态（默认 Status=1 纳入候选）。

---

## CH-2 优先级分层 + 权重随机选渠道

- **功能 ID / 优先级**：F-2028 / P0
- **来源**：FC-053（`service/channel_select.go:155-159` 非 auto 分组调 `model.GetRandomSatisfiedChannel(group,model,retry)`、`priorityRetry` 层级映射、同层 `Weight` 加权随机）
- **角色 / Owner**：系统（relay 选渠）；Owner 模块 = 渠道管理与上游路由（系统内部态）
- **触发**：relay 为请求选择满足渠道

### 1. 场景
选渠是二级选择：先在满足（分组+模型）的渠道集里按 `Priority` 分层，`retry` 次数映射到当前 `priorityRetry` 层级逐层降级；同一优先级层内再按 `Weight` 加权随机抽签选一个。`Weight/Priority` 默认 0。当前层渠道耗尽则降到下一优先级层，全部层都无满足渠道则返回 nil/err 上抛「无可用渠道」。这是「先按优先级定层、层内再权重抽签」的逻辑，与重试循环（CH-5）联动。

### 2. 前置条件
- relay 已确定请求的分组与模型。
- 候选集来自 `Ability`（`Enabled=true` 的分组×模型→渠道路由能力）。
- `retry` 次数映射到当前 `priorityRetry` 层级。

### 3. 主流程（对应 CH-2 节点 sc_req→sc_sel）
1. relay 为请求选渠道（sc_req），筛满足 group+model 的渠道集（sc_filter）。
2. 判候选集是否为空（sc_empty）：是→返回 nil/err 上抛无可用渠道（sc_nil）；否→按 retry 映射当前 `priorityRetry` 层（sc_layer）。
3. 判当前层是否有渠道（sc_pick）：无（该层耗尽）→`priorityRetry++` 降到下一优先级层（sc_down）→判是否已到最低层（sc_bottom）：是→sc_nil；否→回 sc_pick。
4. 有→同层按 `Weight` 加权随机抽签（sc_weight）→选定渠道态进入转发（sc_sel）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 候选集为空 | sc_empty-是 | 返回 nil/err | 无可用渠道上抛 |
| 当前层无渠道 | sc_pick-无 | `priorityRetry++` 降级 | 进下一优先级层 |
| 已到最低层仍无渠道 | sc_bottom-是 | 返回 nil/err | 无可用渠道 |
| 当前层有渠道 | sc_pick-有 | 同层 `Weight` 加权随机 | 选定渠道转发 |

### 5. 数据对象（复用 DATA-MODEL §4 Ability + §3 Channel）
- **候选筛选** `Ability`：`Group(group)`+`Model(model)`+`ChannelId(channel_id)`（复合主键）、`Enabled(enabled)=true`（选渠只取 enabled=true）。
- **分层键** `Ability.Priority(priority) *int64`（`default:0;index`）/ `Channel.Priority(priority)`：`retry` 映射到 `priorityRetry` 层。
- **抽签键** `Ability.Weight(weight) uint`（`default:0;index`）/ `Channel.Weight(weight)`：同层加权随机。

### 6. 验收标准
- [ ] 同分组同模型多渠道按 `Priority` 分层 → 高优先级层先被选，层耗尽后 `priorityRetry++` 降到下一层。
- [ ] 同一优先级层内 → `Weight` 越大被选概率越高（加权随机）。
- [ ] 候选集（满足 group+model 且 `Enabled=true`）为空 → 返回 nil/err，上抛无可用渠道。
- [ ] 所有优先级层都无满足渠道 → 返回 nil/err（已到最低层）。
- [ ] `Weight=0`、`Priority=0` 默认值下仍能正常完成单层等概率随机选择。

### 7. 所触及页面状态（对齐 §G「选渠道」系统内部态）
候选筛选态（满足 group+model）· 无可用渠道态（候选集空 nil/err）· 当前优先级层选择态（priorityRetry 映射）· 层耗尽降级态（priorityRetry++ 下一层）· 权重加权抽签态（同层 Weight）· 选定渠道态（进入转发）。

---

## CH-3 渠道自动禁用与自动恢复（AutoBan 阈值 → ChannelStatusAutoDisabled）

- **功能 ID / 优先级**：F-2023、F-2024 / P0（F-2023）/ P1（F-2024）
- **来源**：FC-049/FC-050（`service/channel.go ShouldDisableChannel/DisableChannel/ShouldEnableChannel/EnableChannel`、`model/channel.go GetAutoBan(AutoBan==1)`、`AutomaticDisableChannelEnabled`、`AutomaticEnableChannelEnabled`、`NotifyRootUser`）
- **角色 / Owner**：系统（禁用·恢复判定）/ root（被通知人）；Owner 模块 = 渠道管理与上游路由
- **触发**：relay 调用渠道返回错误（禁用判定）；被自动禁用渠道后续请求成功（恢复判定）

### 1. 场景
relay 调渠道出错时进入禁用判定：全局 `AutomaticDisableChannelEnabled` 开启且（`IsChannelError` 或状态码命中 `ShouldDisableByStatusCode` 或错误命中 `AutomaticDisableKeywords`），且渠道 `AutoBan=1`，才置 `ChannelStatusAutoDisabled` 并 `NotifyRootUser`；`SkipRetryError` 不触发禁用。反向恢复：被自动禁用渠道的后续请求成功且 `AutomaticEnableChannelEnabled` 开启时 `ShouldEnableChannel` 恢复启用并通知 root——**仅 `status==ChannelStatusAutoDisabled` 才自动恢复，手动禁用不自动恢复**。这是禁用判定与恢复判定的对偶状态机。

### 2. 前置条件
- 禁用判定需全局开关 `AutomaticDisableChannelEnabled` 与渠道级 `AutoBan=1` 同时成立。
- 自动恢复需 `AutomaticEnableChannelEnabled` 开启且本次请求无错误。
- 仅 `status==ChannelStatusAutoDisabled` 的渠道允许自动恢复。

### 3. 主流程（对应 CH-3 状态机迁移）
1. 渠道默认启用（Enabled）；relay 调用返回错误 → Evaluating。
2. Evaluating --`SkipRetryError` 不禁用--> Enabled；--非跳过错误--> GlobalGate。
3. GlobalGate --`AutomaticDisableChannelEnabled=false` 全局关--> Enabled；--全局开启--> HitCheck。
4. HitCheck --未命中 channelError/状态码/关键词--> Enabled；--命中禁用条件--> AutoBanCheck。
5. AutoBanCheck --`AutoBan=0` 跳过禁用--> Enabled；--`AutoBan=1` 置 `ChannelStatusAutoDisabled` + `NotifyRootUser`--> AutoDisabled。
6. AutoDisabled --后续请求成功--> RecoverEval：`AutomaticEnableChannelEnabled=false` 不恢复 → AutoDisabled；开启且本次无错误 → `EnableChannel` + 通知 root → Enabled。
7. Enabled --管理员手动/按 tag 禁用--> ManualDisabled --仅管理员手动启用（不自动恢复）--> Enabled。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `SkipRetryError` | Evaluating→Enabled | 不禁用 | 渠道保持启用 |
| `AutomaticDisableChannelEnabled=false` | GlobalGate→Enabled | 全局关，不禁用 | 渠道保持启用 |
| 未命中禁用条件 | HitCheck→Enabled | 不禁用 | 渠道保持启用 |
| 命中条件但 `AutoBan=0` | AutoBanCheck→Enabled | 跳过禁用 | 渠道保持启用 |
| 命中条件且 `AutoBan=1` | AutoBanCheck→AutoDisabled | 置 `ChannelStatusAutoDisabled` + `NotifyRootUser` | 自动禁用态（已通知 root） |
| 后续成功 + `AutomaticEnableChannelEnabled=false` | RecoverEval→AutoDisabled | 不恢复 | 维持自动禁用 |
| 手动禁用渠道后续成功 | ManualDisabled | 不自动恢复 | 仅管理员可手动启用 |

### 5. 数据对象（复用 DATA-MODEL §3 Channel）
- **判定/写状态** `Channel.Status(status)`：命中阈值置 `ChannelStatusAutoDisabled`（区别于手动禁用 `ChannelStatusManuallyDisabled`）；恢复置启用。
- **禁用开关** `Channel.AutoBan(auto_ban) *int`（`default:1`，`GetAutoBan()=AutoBan==1` 才禁用）；全局 `AutomaticDisableChannelEnabled` / `AutomaticEnableChannelEnabled`。
- **命中条件源** `Channel.StatusCodeMapping(status_code_mapping)`（`ShouldDisableByStatusCode`）；错误命中 `AutomaticDisableKeywords`；`types.ChannelError`（`IsChannelError`）。

### 6. 验收标准
- [ ] relay 出错且为 `SkipRetryError` → 渠道不被禁用，`Status` 保持启用。
- [ ] 全局 `AutomaticDisableChannelEnabled=false` → 即便命中错误也不禁用。
- [ ] 命中禁用条件但渠道 `AutoBan=0` → 跳过禁用，`Status` 不变。
- [ ] 命中禁用条件且 `AutoBan=1` → `Channel.Status` 置 `ChannelStatusAutoDisabled` 并 `NotifyRootUser`。
- [ ] 被自动禁用渠道后续请求成功且 `AutomaticEnableChannelEnabled=true` → 恢复启用并通知 root。
- [ ] 手动禁用（`ChannelStatusManuallyDisabled`）渠道后续请求成功 → 不自动恢复，仅管理员可手动启用。

### 7. 所触及页面状态（对齐 §G「自动禁用/恢复」系统+管理员可见）
启用态（Status=1）· 错误评估态（relay 出错触发判定）· 跳过禁用态（SkipRetryError）· 全局开关关闭态（不禁用）· 命中但 AutoBan=0 态（跳过禁用）· 自动禁用态（ChannelStatusAutoDisabled 已通知 root）· 自动恢复态（请求成功+开关开 回启用+通知 root）· 手动禁用态（按 tag/手动 不自动恢复）。

---

## CH-4 会话亲和键提取与渠道粘连（命中即复用 + SkipRetryOnFailure）

- **功能 ID / 优先级**：F-2029、F-2034 / P0（F-2029）/ P1（F-2034）
- **来源**：FC-068（`setting/operation_setting/channel_affinity_setting.go` 规则结构、内置 codex（gpt-* + /v1/responses + gjson prompt_cache_key）/ claude（claude-* + /v1/messages + gjson metadata.user_id）规则、`SwitchOnSuccess`、`SkipRetryOnFailure`）
- **角色 / Owner**：系统（亲和缓存命中·回写）；Owner 模块 = 渠道管理与上游路由（系统内部态）
- **触发**：请求进入选渠前

### 1. 场景
请求命中亲和规则时，按 `model_regex/path_regex/key_sources` 提取会话键查缓存：命中且未过期则复用上次成功渠道（保证同会话粘在同一渠道，利于上游缓存命中）；否则回退正常选渠（CH-2）。`SwitchOnSuccess=true` 时仅请求成功才把会话粘到该渠道并续期缓存。命中 `SkipRetryOnFailure=true` 的规则若失败则直接返回错误不跨渠道重试，避免缓存被刷到别的渠道破坏会话稳定性。这是缓存命中分叉 + 成功才回写的闭环。

### 2. 前置条件
- 亲和功能由 `ChannelAffinitySetting.Enabled` 控制。
- 规则可为内置（codex/claude）或自定义，含 `ModelRegex/PathRegex/KeySources`。
- 缓存键由 `key_sources`（gjson/request_header/context_int/context_string）提取。

### 3. 主流程（对应 CH-4 节点 af_in→af_ok/af_end/af_fail）
1. 请求进入选渠前（af_in），判是否命中某亲和规则（af_rule）。
2. 否→无亲和走 CH-2 普通选渠（af_normal）→交回选渠引擎态（af_end）。
3. 是→按 `key_sources` 提取会话键（af_key），判缓存是否命中且未过期（af_cache）。
4. 否（未命中）→回 af_normal；是→复用上次成功渠道（af_stick）→转发到粘连渠道（af_call）。
5. 判调用是否成功（af_res）：成功→判 `SwitchOnSuccess`（af_switch）：是→成功才回写/续期缓存键→渠道（af_write）；否→不更新缓存（af_nowrite）。两路→亲和命中成功态会话稳定（af_ok）。
6. 失败→判规则 `SkipRetryOnFailure`（af_skip）：true→直接返错不跨渠道重试保稳定（af_fail）；false（默认）→失败走正常跨渠道重试（af_retry）→交回选渠引擎态（af_end）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 未命中任何亲和规则 | af_rule-否 | 走 CH-2 普通选渠 | 无亲和直走选渠 |
| 缓存未命中/已过期 | af_cache-否 | 回退普通选渠 | 缓存未命中回退态 |
| 成功 + `SwitchOnSuccess=true` | af_switch-是 | 回写/续期缓存键→渠道 | 成功回写缓存态 |
| 成功 + `SwitchOnSuccess=false` | af_switch-否 | 不更新缓存 | 不回写态 |
| 失败 + `SkipRetryOnFailure=true` | af_skip-true | 直接返错，不跨渠道重试 | 失败不重试态（保稳定） |
| 失败 + `SkipRetryOnFailure=false` | af_skip-false | 走正常跨渠道重试 | 失败正常重试态 |

### 5. 数据对象（复用 DATA-MODEL §3 Channel + 亲和规则/缓存运行态）
- **复用目标** `Channel.Id(id)`：命中缓存时复用上次成功渠道的 id（粘连）。
- **规则字段** `ChannelAffinityRule`：`ModelRegex/PathRegex/KeySources`（提取会话键）、`SkipRetryOnFailure`（内置 codex/claude 均 true）、`TTLSeconds`。
- **缓存策略** `ChannelAffinitySetting`：`Enabled`、`SwitchOnSuccess`（成功才回写）、`MaxEntries`、`DefaultTTLSeconds`（缓存键→渠道映射的 TTL）。

### 6. 验收标准
- [ ] 同一 `prompt_cache_key` 连续发 gpt 请求 → 首次选定渠道后，后续命中缓存复用同一 `Channel.Id`。
- [ ] 缓存未命中或已过期 → 回退 CH-2 普通选渠，不报错。
- [ ] 请求成功且 `SwitchOnSuccess=true` → 缓存键→渠道映射被回写/续期。
- [ ] 请求成功但 `SwitchOnSuccess=false` → 不更新缓存映射。
- [ ] 命中 `SkipRetryOnFailure=true` 规则的请求失败 → 直接返回错误，不跨渠道重试（缓存不被刷到其他渠道）。
- [ ] 命中 `SkipRetryOnFailure=false`（默认自定义规则）的请求失败 → 走正常跨渠道重试。

### 7. 所触及页面状态（对齐 §G「亲和缓存」系统内部态）
无亲和直走普通选渠态（未命中规则）· 会话键提取态（key_sources）· 缓存未命中回退态（走 CH-2）· 渠道粘连复用态（命中缓存）· 成功回写缓存态（SwitchOnSuccess=true）· 不回写态（SwitchOnSuccess=false）· 失败不重试态（SkipRetryOnFailure=true 保稳定）· 失败正常重试态（默认 false）。

---

## CH-5 auto 分组逐组耗尽优先级后跨组重试（priorityRetry>=RetryTimes）

- **功能 ID / 优先级**：F-2035、F-2036、F-2037 / P0（F-2035）/ P1（F-2036/F-2037）
- **来源**：FC-072（`service/channel_select.go:83-162 CacheGetRandomSatisfiedChannel`、遍历 autoGroups、`channel==nil` 时 `SetContextKey(AutoGroupIndex,i+1)+SetRetry(0)`、`crossGroupRetry 且 priorityRetry>=RetryTimes` 时 `ResetRetryNextTry`、`common.RetryTimes`、`GetAutoGroups()` 空报「auto groups is not enabled」）
- **角色 / Owner**：系统（跨分组重试调度）/ 令牌（CrossGroupRetry 开关）；Owner 模块 = 渠道管理与上游路由（系统内部态）
- **触发**：令牌用 auto 分组发起请求

### 1. 场景
令牌使用 `auto` 分组时 `CacheGetRandomSatisfiedChannel` 遍历 autoGroups：当前组按优先级选满足渠道，组内 `priorityRetry` 用尽（`>=common.RetryTimes`）则切下一组——`SetContextKey(AutoGroupIndex,i+1)` + `SetRetry(0)` 在新组归零重试。令牌级 `CrossGroupRetry` 开启时本次仍用当前组、`ResetRetryNextTry` 下次才切组。`GetAutoGroups()` 为空返回「auto groups is not enabled」，全组耗尽返回无可用渠道。这是「组内优先级降级 → 组耗尽切组 → 组用尽终止」的嵌套循环判定。

### 2. 前置条件
- 令牌 `Group=auto`，跨组开关读 `Token.CrossGroupRetry`。
- `common.RetryTimes` 为单组内 `priorityRetry` 上限。
- `AutoGroupIndex` 上下文键指向当前分组。

### 3. 主流程（对应 CH-5 节点 ag_start→ag_use/ag_nomore/ag_disable）
1. 令牌 auto 分组发起请求（ag_start），判 `GetAutoGroups` 是否非空（ag_chk）。
2. 空→返回「auto groups is not enabled」（ag_disable）；非空→取 `AutoGroupIndex` 指向当前组（ag_enter）。
3. 当前组按 `priorityRetry` 选满足渠道（ag_sel），判是否选到渠道（ag_found）。
4. 是→用当前组渠道转发态（ag_use）；否（该层空）→判 `priorityRetry >= RetryTimes`（ag_exhaust）。
5. 否（还能降级）→`priorityRetry++` 继续本组降级（ag_inc）→回 ag_sel。
6. 是（本组耗尽）→判令牌 `CrossGroupRetry` 是否开启（ag_cross）：否→切下一组 `SetRetry(0)` priorityRetry 归零（ag_switch）；是→`ResetRetryNextTry` 本次用当前组下次切组（ag_next）→ag_switch。
7. ag_switch 判是否还有下一分组（ag_more）：有→回 ag_enter；无（全部耗尽）→全组用尽返回无可用渠道（ag_nomore）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `GetAutoGroups` 为空 | ag_chk-空 | 拒绝 | 「auto groups is not enabled」 |
| 选到渠道 | ag_found-是 | 用当前组渠道转发 | 转发态（终态） |
| `priorityRetry < RetryTimes` | ag_exhaust-否 | `priorityRetry++` 本组降级 | 组内降级 |
| `priorityRetry >= RetryTimes` 且 `CrossGroupRetry=false` | ag_cross-否 | 切下一组 `SetRetry(0)` 归零 | 直接切组 |
| `priorityRetry >= RetryTimes` 且 `CrossGroupRetry=true` | ag_cross-是 | `ResetRetryNextTry` 本次用当前组下次切 | 延迟切组 |
| 全组耗尽无下一分组 | ag_more-无 | 返回无可用渠道 | 全组用尽态 |

### 5. 数据对象（复用 DATA-MODEL §2 Token + §4 Ability）
- **触发键** `Token.Group(group)=auto`（非 auto 不进本流程）、`Token.CrossGroupRetry(cross_group_retry)`（仅 `Group=auto` 生效，控制本次切组 vs 延迟切组）。
- **候选源** `Ability`：每个 autoGroup 内按 `Group(group)`+`Model(model)`+`Enabled(enabled)=true` 取满足渠道，按 `Priority(priority)` 降级。
- **调度量** `priorityRetry`（单组内层级，`>=common.RetryTimes` 判耗尽）、上下文 `AutoGroupIndex`（当前组索引，切组 +1）、`SetRetry(0)`（新组归零）。

### 6. 验收标准
- [ ] 令牌 `Group=auto` 但 `GetAutoGroups()` 为空 → 返回「auto groups is not enabled」。
- [ ] 当前组某优先级层无渠道且 `priorityRetry < RetryTimes` → `priorityRetry++` 在本组继续降级。
- [ ] 当前组 `priorityRetry >= RetryTimes` 且 `CrossGroupRetry=false` → 立即切到下一组并 `SetRetry(0)` 归零重试。
- [ ] 当前组 `priorityRetry >= RetryTimes` 且 `CrossGroupRetry=true` → 本次仍用当前组，下次请求才切到下一组（`ResetRetryNextTry`）。
- [ ] 切到下一组时 `AutoGroupIndex` +1，新组内 `priorityRetry` 从 0 起算。
- [ ] 遍历完全部 autoGroups 仍无满足渠道 → 返回无可用渠道。

### 7. 所触及页面状态（对齐 §G「跨分组重试」系统内部态）
auto 未启用态（GetAutoGroups 空 报错）· 当前组选渠态（priorityRetry 层）· 组内降级态（priorityRetry++ 未达 RetryTimes）· 本组耗尽态（priorityRetry>=RetryTimes）· 直接切组态（CrossGroupRetry 关 本次即切归零重试）· 延迟切组态（CrossGroupRetry 开 本次用当前组下次切）· 用当前组转发态（选到渠道）· 全组耗尽态（无可用渠道）。

---

## CH-6 供应渠道池（一个对外模型 A→真实模型 B 绑多渠道，优先级/权重/容灾切换）

- **功能 ID / 优先级**：F-2050、F-2051 / P0（F-2050）/ P1（F-2051）
- **来源**：COMPAT-BILLING-DECISIONS §3（同品质多供应商兜底、§10 链路 ④）；COMPAT-LAYER-ARCHITECTURE §3.3（L2 后 B 作 Ability `model` 维查询键、映射不掺路由铁律）；BILLING-MODEL-ARCHITECTURE §2.3（同一 A→B 下挂多同品质渠道兜底、ADR-BILL-05 红线）；复用 `model/ability.go` Ability + `model/channel.go` Channel（DATA-MODEL §3/§4）
- **角色 / Owner**：超管（绑渠道池）/ 系统（按 B 选渠+容灾）；Owner 模块 = 渠道管理与上游路由
- **触发**：超管把多个同品质供应商渠道挂到同一真实模型 B 下；relay 以 L2 后 B 为 `model` 维查 Ability 选渠

### 1. 场景
两层模型映射（prd-model ML-7）把 `C→A→B` 解析出真实模型 B 后，**B 作为 Ability 的 `model` 维查询键**进入选渠（映射只产出模型名，负载/容灾不掺进映射——铁律）。一个对外模型 A 经超管层映射到真实模型 B 后，B 下可绑**多个供应商渠道**组成「供应渠道池」：同品质、仅进货价不同的供应商混在同一 B 下做多供应商兜底（DECISIONS §3）。渠道池的优先级/权重/容灾切换**完全复用现网 Ability/Channel 路由**（不新造）：CH-2 优先级分层+权重随机选一个渠道、CH-3 自动禁用/恢复、CH-5 跨分组重试都对 B 维生效。挂了兜底切下一个渠道（如供应商 X 挂切供应商 Y），切换后实际渠道 `ChannelId` 变——**售价恒定（挂 A，见 prd-billing BL-7/BL-9），成本跟实际渠道走（挂 channel×B，见 CH-7）**。
**红线（ADR-BILL-05）**：同一 A→B 下挂的多渠道必须是「同品质、仅进货价不同」的供应商；品质不同的渠道绝不进同一个 A 的兜底池——品质差异由 prd-model ML-6/ML-7「拆成不同 A→不同 B」结构性保证（A→B 一对一 + 同 B 下多渠道才兜底）。本块是把现网 Ability 路由的查询维明确为「L2 后的真实模型 B」，并约束渠道池成员的同品质红线，不改选渠算法本身。

### 2. 前置条件
- 映射已产出 L2 后 B（`RelayInfo.UpstreamModelName`，prd-model ML-7），作为 Ability `model` 维查询键。
- 同一 B 下挂的多渠道为同品质供应商（红线，不混品质）。
- 选渠/容灾算法复用 CH-2（优先级分层+权重随机）、CH-3（自动禁用/恢复）、CH-5（auto 跨组）。

### 3. 主流程（对应 CH-6 节点 cp_in→cp_sel/cp_nil）
1. 映射产出 B（cp_in）→以 `(UsingGroup × model=B)` 查 Ability 渠道池（cp_query）。
2. 判候选池是否为空（cp_empty）→空则无可用渠道上抛（cp_nil）。
3. 非空则按 `Priority` 分层（cp_layer）→同层按 `Weight` 加权随机选一个渠道（cp_pick，复用 CH-2）→选中渠道 `ChannelId` 落 RelayInfo（cp_chosen）。
4. 转发调用（cp_call），判是否失败需容灾（cp_fail）→失败则按 CH-3/CH-5 容灾切下一渠道（cp_failover）→`ChannelId` 变回 cp_query 取新渠道；成功则选定供应渠道转发态（cp_sel）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `(UsingGroup × B)` 候选池为空 | cp_empty-是 | 返回 nil/err（复用 CH-2） | 无可用渠道上抛 |
| 池内多渠道按优先级 | cp_layer | `Priority` 分层（复用 CH-2） | 高优先级层先选 |
| 同优先级层多渠道 | cp_pick | `Weight` 加权随机选一个 | 选中单渠道 |
| 选中渠道调用失败 | cp_fail-是 | CH-3 禁用判定 + CH-5 容灾切换 | 兜底切下一渠道（ChannelId 变） |
| 兜底切换后 | cp_failover | `ChannelId` 改变 → 重查池 | 售价恒定/成本跟新渠道（CH-7/BL-9） |

### 5. 数据对象（复用 DATA-MODEL §4 Ability + §3 Channel）
- **渠道池查询维** `Ability`：`Group(group)`（=UsingGroup）+ `Model(model)`（= **L2 后真实模型 B**，prd-model ML-7 产出）+ `ChannelId(channel_id)`（复合主键）、`Enabled(enabled)=true`（只取可用渠道）。
- **优先级/权重** `Ability.Priority(priority) *int64`（`default:0;index`，分层）、`Ability.Weight(weight) uint`（`default:0;index`，同层加权随机）/ `Channel.Priority`/`Channel.Weight`。
- **池成员** 多个 `Channel`（同品质、不同供应商）：选中后 `Channel.Id` 落 `RelayInfo.ChannelId`（供 CH-7 取成本、prd-billing BL-9 算利润）。
- **容灾** 复用 `Channel.Status`（CH-3 自动禁用 `ChannelStatusAutoDisabled`）、CH-5 auto 跨组——切换后 `ChannelId` 变，售价不变成本跟实际渠道。

### 6. 验收标准
- [ ] 映射产出的 L2 后 B 作为 Ability `model` 维查询键选渠（不是 C/A）。
- [ ] 同一 A→B 下挂多个同品质供应商渠道 → 组成渠道池，按 `Priority` 分层、同层 `Weight` 加权随机选一个（复用 CH-2）。
- [ ] 选中渠道调用失败 → 按 CH-3 自动禁用 + CH-5 容灾切到池内下一渠道，`ChannelId` 随之改变。
- [ ] 兜底切换供应商后 → 售价不变（挂 A），成本按新渠道取（CH-7），利润随之重算（BL-9）。
- [ ] `(UsingGroup × B)` 候选池为空 → 返回无可用渠道（复用 CH-2 行为）。
- [ ] 同一 A→B 渠道池成员均为同品质供应商（红线）；品质不同必须拆成不同 A→不同 B（prd-model ML-6/ML-7），不在同一池兜底。

### 7. 所触及页面状态（对齐 §G「供应渠道池」系统内部态）
B 维渠道池查询态（UsingGroup × model=B）· 无可用渠道态（池空 nil/err）· 优先级分层态（Priority 复用 CH-2）· 权重加权选渠态（Weight 同层）· 选中渠道态（ChannelId 落 RelayInfo）· 容灾切换态（CH-3/CH-5 切下一渠道 ChannelId 变）· 选定供应渠道转发态（终态，售价恒定成本跟实际）。

---

## CH-7 供应商成本配置（ChannelModelCost 挂渠道×真实模型 B，超管手填成本倍率）

- **功能 ID / 优先级**：F-2052、F-2053 / P0（F-2052）/ P1（F-2053）
- **来源**：COMPAT-BILLING-DECISIONS §4（成本挂渠道×B、超管手填倍率、不做自动折算）；BILLING-MODEL-ARCHITECTURE §2（Channel 无承载字段须新增表、ADR-BILL-03/04、§2.3 多供应商落表与结算取值时序）；DATA-MODEL §19 ChannelModelCost
- **角色 / Owner**：超管（手填成本倍率）/ 系统（结算阶段按渠道×B 取成本）；Owner 模块 = 渠道管理与上游路由
- **触发**：超管在「成本/售价配置页」给某渠道某真实模型 B 手填成本倍率；relay 结算阶段按 `(实际 ChannelId, L2 后 B)` 取成本

### 1. 场景
成本挂在「供应商渠道 × 真实模型 B」上（DECISIONS §4）。现网 `Channel` 无任何「按模型的成本倍率」结构化字段，故**新增表 `ChannelModelCost`**（不塞 Channel.Setting，因成本是「渠道×模型」二维，需复合唯一约束 + 可聚合索引，ADR-BILL-03）。**录入方式 = 超管后台手填**每个供应商每个模型的成本倍率（A 起步，不做「填进货单价自动折算」，留扩展位 `SourceUnitPrice` 本期不参与计算）。
**多供应商各记各的（§2.3）**：同一 A→B 下，每个挂 Ability 的渠道（CH-6 渠道池成员）各一行成本——售价同挂 A（恒定），成本分行挂 `channel×B`（X 进货便宜、Y 贵，各记各的）。
**结算取值时序（ADR-BILL-04，链路第 17 步）**：CH-6 选中渠道后 `ChannelId` 确定、L2 后 B 确定，结算阶段用 `(实际选中 ChannelId, L2 后 B)` 复合主键精确取一行 `CostRatio`——这才是「实际走的那个渠道的成本」。**兜底切换后 `ChannelId` 变→自动取到新渠道的成本行**（成本跟实际供应商走，售价恒定）。**成本缺失兜底**：`(ChannelId, B)` 无行或 `Enabled=false` → 成本记 0 + 利润标「成本缺失」告警，**不阻断计费**（售价照扣，详见 prd-billing BL-9）。

### 2. 前置条件
- 配成本走超管会话过 C1 + AdminAuth/RootAuth（客户无任何读 B/成本接口，B 不可见）。
- 复合唯一键 `uk_channel_model(channel_id, upstream_model)`：一渠道对一 B 只一条生效成本。
- 成本取值在结算阶段，主键 = CH-6 选中的实际 `ChannelId` + prd-model ML-7 的 L2 后 B。

### 3. 主流程（对应 CH-7 节点 cm_in→cm_ok / 结算 cm_take→cm_cost）
1.（配置侧）超管选渠道 × 真实模型 B 手填成本倍率（cm_in），判 `(ChannelId, UpstreamModel)` 是否已有行（cm_dup）→有则更新该行 CostRatio（cm_update）；无则 Insert 新行（cm_insert）→成本倍率已配置态（cm_ok）。
2.（结算侧）relay 结算阶段（cm_take），用 `(实际选中 ChannelId, L2 后 B)` 主键查 ChannelModelCost（cm_query）→判是否命中且 `Enabled=true`（cm_hit）。
3. 命中→取 `CostRatio`/`CompletionCostRatio`（cm_cost，CompletionCostRatio=0 回落 CostRatio×现网 CompletionRatio）→交利润计算（prd-billing BL-9）。
4. 未命中/禁用（cm_miss）→成本记 0 + 标「成本缺失」告警不阻断（cm_zero，Other 写 `{cost_missing:true}`）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 同 `(ChannelId,B)` 已有行 | cm_dup-是 | 更新 CostRatio | 成本倍率更新态 |
| `(ChannelId,B)` 无行 | cm_dup-否 | Insert 新行 | 新增成本行态 |
| 同一 A→B 下多渠道 | （多行） | 每渠道各一行成本 | 多供应商成本分行 |
| 结算命中且 Enabled | cm_hit-是 | 取 CostRatio | 实际渠道成本取值态 |
| 兜底切换后 ChannelId 变 | cm_query | 主键换→取新渠道行 | 成本跟实际供应商走 |
| `(ChannelId,B)` 缺行/禁用 | cm_miss | 成本记 0 + 告警 | 成本缺失不阻断态 |

### 5. 数据对象（复用 DATA-MODEL §19 ChannelModelCost + §3 Channel）
- **成本主键** `ChannelModelCost`：`ChannelId(channel_id)`（= 实际选中供应商渠道 `Channel.Id`，复合唯一键 1）+ `UpstreamModel(upstream_model)`（= **真实模型 B**，对齐 Ability.Model 维，复合唯一键 2，**客户不可见**）。复合唯一索引 `uk_channel_model`。
- **成本倍率** `ChannelModelCost.CostRatio(cost_ratio) float64`（超管手填，口径同 `model_ratio` 输入 token）、`CompletionCostRatio(completion_cost_ratio) float64`（输出 token；0 回落 CostRatio×现网 CompletionRatio）。
- **生效/缺失** `Enabled(enabled) bool default:true`（false=视为缺失记 0+告警）、`EffectiveTime(effective_time)`（取最新生效一条，预留版本化）。
- **扩展位** `SourceUnitPrice(source_unit_price)`（进货单价自动折算预留，本期不参与计算）、`Remark(remark)`。
- **取值时机** 结算阶段主键 `(CH-6 实际 ChannelId, ML-7 L2 后 B)` 精确取一行；内存缓存 `[channelId][upstreamModel]→CostRatio`，`InitChannelModelCostMap()` 装载，写时失效。

### 6. 验收标准
- [ ] 超管给 `(渠道X, B)` 手填 `CostRatio=0.40`、`(渠道Y, B)` 手填 `0.55` → 各落一行（多供应商成本分行，售价同挂 A 不变）。
- [ ] 同 `(ChannelId, UpstreamModel)` 复合键再次提交 → 更新该行，不产生重复行（`uk_channel_model` 唯一）。
- [ ] 结算阶段以 `(实际选中 ChannelId, L2 后 B)` 主键取成本 → 取到「实际走的那个渠道」的 `CostRatio`。
- [ ] CH-6 兜底切换供应商（X→Y）后 → `ChannelId` 变，结算自动取到 Y 的成本行（成本跟实际，售价恒定）。
- [ ] `(ChannelId, B)` 无行或 `Enabled=false` → 成本记 0、利润标「成本缺失」告警，**不阻断计费**（售价照扣）。
- [ ] `CompletionCostRatio=0` → 回落 `CostRatio × 现网 CompletionRatio` 算输出 token 成本。
- [ ] 客户任何接口均无法读到 `upstream_model(B)` 或 `cost_ratio`（仅 admin/root）。

### 7. 所触及页面状态（对齐 §G「供应商成本配置」）
成本配置编辑态（渠道×B 手填倍率）· 成本倍率更新态 / 新增成本行态 · 多供应商成本分行态（同 A→B 多渠道各一行）· 实际渠道成本取值态（结算 `(ChannelId,B)` 主键）· 成本跟实际供应商走态（兜底切换 ChannelId 变）· 成本缺失不阻断态（缺行/禁用记 0+告警）· 成本倍率已配置态（终态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-2016 | CH-1 |
| F-2020 | CH-1 |
| F-2021 | CH-1 |
| F-2023 | CH-3 |
| F-2024 | CH-3 |
| F-2028 | CH-2 |
| F-2029 | CH-4 |
| F-2034 | CH-4 |
| F-2035 | CH-5 |
| F-2036 | CH-5 |
| F-2037 | CH-5 |
| F-2050 | CH-6 |
| F-2051 | CH-6 |
| F-2052 | CH-7 |
| F-2053 | CH-7 |

无 `[BLOCKER]`。

---

## 补充：Channel 状态数值定义与自愈只恢复自动禁用（状态机缺口补漏）

> 评审发现 CH-3 自动禁用/自愈未给出 `Channel.Status` 的具体数值枚举，导致「自愈只恢复自动禁用、不碰手动禁用」这一关键守卫无法二元验收。补充如下（不新增功能 ID，归属 CH-3 / F-2023·F-2024）。

### Channel.Status 数值枚举（`common/constants.go`，固定值非 iota）
| 常量 | 值 | 含义 | 来源 |
|---|---|---|---|
| `ChannelStatusUnknown` | `0` | 未知（GORM 默认零值，业务不主动使用） | 不进选渠（Ability `Enabled` 才进） |
| `ChannelStatusEnabled` | `1` | 启用，可被选中转发 | 选渠 / 测速通过启用 |
| `ChannelStatusManuallyDisabled` | `2` | 管理员手动禁用 | 管理台手动操作 |
| `ChannelStatusAutoDisabled` | `3` | 系统因上游错误自动禁用 | `service/channel.go` `DisableChannel` |

注释明确「don't use 0, 0 is the default value」——禁用态用 `2`（手动）/`3`（自动）区分来源，0 仅作零值占位。

### 自动禁用 vs 手动禁用的状态机守卫
- **自动禁用（→3）**：`ShouldDisableChannel` 判定命中（`AutomaticDisableChannelEnabled=true` 且错误为渠道级 `IsChannelError` / 命中 `ShouldDisableByStatusCode` / 命中 `AutomaticDisableKeywords`，且非 `IsSkipRetryError`）时，`UpdateChannelStatus(..., ChannelStatusAutoDisabled=3, reason)` 置 `Status=3` 并通知 root 用户。
- **手动禁用（→2）**：管理员在管理台操作置 `Status=ChannelStatusManuallyDisabled=2`，系统侧不主动产生此值。
- **自愈只恢复自动禁用**：`ShouldEnableChannel(err, status)` 仅在 `AutomaticEnableChannelEnabled=true`、`err==nil`（探测成功）**且 `status == ChannelStatusAutoDisabled(3)`** 三条全满足时返回 `true`——即自愈/自动重启用**只针对值为 3 的自动禁用渠道**；`status==2`（手动禁用）直接 `return false`，**永不被自动恢复**，必须管理员手动启用。
- **Codex 凭据刷新同源守卫**：`shouldAutoRefreshCodexChannelStatus(status)` 同样只对 `status == ChannelStatusEnabled(1) || status == ChannelStatusAutoDisabled(3)` 放行刷新，手动禁用（2）不刷新。

### 验收标准（二元可测）
- [ ] 上游返回渠道级错误且 `AutomaticDisableChannelEnabled=true` → `Channel.Status` 被置为 `3`（`ChannelStatusAutoDisabled`），通知 root 用户。
- [ ] 管理员手动禁用渠道 → `Channel.Status=2`（`ChannelStatusManuallyDisabled`），系统不会把它改成 3。
- [ ] 探测成功（`err==nil`）+ `AutomaticEnableChannelEnabled=true` + `Status==3` → `ShouldEnableChannel` 返回 `true`，渠道被自动置回 `1`。
- [ ] 探测成功 + `Status==2`（手动禁用）→ `ShouldEnableChannel` 返回 `false`，渠道保持 `2` 不被自动启用。
- [ ] `AutomaticEnableChannelEnabled=false` → 即使 `Status==3` 且探测成功，`ShouldEnableChannel` 仍返回 `false`（总开关优先）。
- [ ] Codex 凭据刷新调度 → 仅 `Status∈{1,3}` 的 Codex 渠道入选刷新，`Status==2` 不刷新。
