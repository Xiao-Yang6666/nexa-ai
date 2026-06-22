# COMPAT-LAYER-API-STATE — API 边界 + 请求处理时序 + 状态/边界规则

> 配套：COMPAT-LAYER-ARCHITECTURE.md（主架构）、COMPAT-LAYER-DATA-OBJECTS.md（数据对象）。
> 设计铁律：复用现有中间件链（`TokenAuth → ModelRequestRateLimit → Distribute`，见 ARCHITECTURE-REVIEW §3）、现有 relay 控制器与 `Adaptor`。新增点必须落到路径/中间件/函数/执行顺序级。

---

## 1. API 边界（新增管理/自助接口）

> 入站推理端点（`/v1/chat/completions`、`/v1/messages`）**沿用现有 relay 路由**，不新增——协议识别已由现有 router + `GenRelayInfoXxx` 完成。新增的是**映射配置管理**接口。

### 1.1 超管层 PlatformModelMapping（`AdminAuth`/`RootAuth` 路由组）
| 动作 | 方法 路径 | 中间件 | 后端责任 |
|---|---|---|---|
| 列表 | `GET /api/platform_model_mapping` | `AdminAuth` | 返全字段含 B（admin 视图） |
| 新建/改 | `POST /api/platform_model_mapping`、`PUT /api/platform_model_mapping/:id` | `AdminAuth` + `CriticalRateLimit` | 写 `platform_model_mappings`，校验 `public_name` 唯一；写后失效内存缓存 |
| 删 | `DELETE /api/platform_model_mapping/:id` | `AdminAuth` | 软删 |

**绝对约束**：**无任何 `UserAuth` 路由读写此表**。客户侧拿不到入口，是 B 不可见的第一道闸。

### 1.2 客户层 UserModelAlias（`UserAuth` 自助 + `AdminAuth` 代管）
| 动作 | 方法 路径 | 中间件 | 后端责任 |
|---|---|---|---|
| 我的别名列表 | `GET /api/user_model_alias/self` | `UserAuth` | 强制 `WHERE (scope_type='user' AND scope_id=:caller) OR (scope_type='group' AND scope_id=:caller_group)`；只读本 scope |
| 新建/改我的别名 | `POST /api/user_model_alias/self`、`PUT /api/user_model_alias/self/:id` | `UserAuth` | 强制注入 `scope_id=:caller`，禁跨 scope；`target` 不校验白名单 |
| 删我的别名 | `DELETE /api/user_model_alias/self/:id` | `UserAuth` | 越权过滤 `scope_id=:caller` |
| 候选提示（联想） | `GET /api/models/public?q=` | `UserAuth` | 返**公开模型全集（A）**，**绝不含 B**（第三道闸） |
| 管理端代管 | `GET/POST/PUT/DELETE /api/user_model_alias`（带 scope 参数） | `AdminAuth` | 全量 |

### 1.3 用量/日志（角色裁字段）
| 动作 | 方法 路径 | 中间件 | 后端责任 |
|---|---|---|---|
| 我的用量 | `GET /api/log/self` | `UserAuth` | 用 `UserLogView`（C→A，**无 B**） |
| 全量用量 | `GET /api/log` | `AdminAuth` | 用 `AdminLogView`（C→A→B 全链 + 协议字段） |

---

## 2. 请求处理时序（端到端，含中间件次序）

```
1. 入站           客户 POST /v1/chat/completions(model=C, openai) 或 /v1/messages(model=C, claude)
2. TokenAuth      鉴权 sk- key → 取 Token{Group, ModelLimits} / User{Group}        [复用]
3. GenRelayInfo   set inFmt=RelayFormat(openai|claude); OriginModelName=C; RequestedModel=C
4. ★ L1 客户映射   查 UserModelAlias(user>group): C→A  (未命中则 A=C)  → set ResolvedPublicModel=A
5. ★ 白名单校验    Token.ModelLimits 校验对象 = A（见 §6 决策）；不在白名单→403/拒绝     [复用+次序新增]
6. ★ L2 超管映射   查 PlatformModelMapping(public_name=A): A→B (未命中则 B=A)  → set UpstreamModelName=B
7. ★ 环检测/跳数   L1+L2 合计跳数 ≤ MaxHops(默认 8)；检出环→错误码 mapping_cycle        [新增, §4]
8. Distribute     按 (Group × B) 查 Ability 选 Channel（负载/容灾，现有路由）           [复用]
9. ★ 头尾决策      targetProto = protocolOf(Channel.Type); Passthrough = (inFmt==targetProto)
10. L3 渠道映射    现有 model_mapped.go：B→B'（渠道内重定向，写 UpstreamModelName 终值）  [复用]
11. ★ Outbound     Passthrough? 透传(仅换 model=B') : compat.Get(inFmt).ParseRequest → IR
                   → compat.Get(targetProto).SerializeRequest(IR)  →  调上游 (DoRequest)   [新增壳+复用convert]
12. 上游响应       resp(targetProto)
13. ★ 响应回转     Passthrough? 透传 : compat.Get(targetProto).ParseResponse → IR
                   → compat.Get(inFmt).SerializeResponse(IR)  (流式见 §5)               [新增]
14. 计费+落Log     按 B 定价；Log: requested_model=C, resolved_public_model=A,
                   actual_upstream_model=B, inbound_protocol=inFmt, upstream_protocol=target,
                   protocol_converted=!Passthrough; Other.channel_redirect=B'             [扩展]
```

**★ = 本期新增/次序调整点。** 步骤 4–7 在 `Distribute`（步骤 8）之前，因为 Ability 路由维 `model` 必须是 B。

---

## 3. 协议转换难点与方案（OpenAI ⇄ Anthropic 点名）

| # | 难点 | OpenAI | Anthropic | IR 方案 |
|---|---|---|---|---|
| D1 | **system 位置** | `messages[{role:system}]` | 顶层独立 `system` 字段 | IR `System []ContentBlock`；OpenAI→提取首个 system 消息；Anthropic→读顶层。反向还原。**多个 system 消息**：合并为 IR.System（OpenAI 允许多条，Anthropic 单字段→拼接） |
| D2 | **content 结构** | string 或 `[{type:text/image_url}]` | 恒为 `[{type:text/image/...}]` block | IR 统一 block 数组；OpenAI 序列化时单 text 退化为 string；`image_url`(url/base64) ⇄ Anthropic `image.source(base64/url)` |
| D3 | **tool/function** | `tools[].function{name,description,parameters}` + assistant `tool_calls` + `role:tool` 消息 | `tools[]{name,description,input_schema}` + `tool_use`/`tool_result` content block | IR `Tools[]` + ToolUse/ToolResult block；`tool_choice` 枚举映射 `auto/none/required/具名` ⇄ `auto/any/tool` |
| D4 | **停止原因** | `finish_reason: stop/length/tool_calls/content_filter` | `stop_reason: end_turn/max_tokens/stop_sequence/tool_use` | IR 枚举居中（见 §7 映射表） |
| D5 | **usage 字段** | `usage.prompt_tokens/completion_tokens/total_tokens` | `usage.input_tokens/output_tokens`（无 total） | IR `UsageIR`；Anthropic→IR.Prompt=input, Completion=output；反向 total=prompt+completion |
| D6 | **max_tokens** | 可选（`max_tokens`/`max_completion_tokens`） | **必填** | OpenAI→Anthropic 缺失时填默认上限（按模型 ratio 配置或固定 4096，记 ADR-COMPAT-07） |
| D7 | **流式格式** | `data: {..delta..}\n\n` + `data: [DONE]` | event 序列：`message_start`/`content_block_start`/`content_block_delta`/`content_block_stop`/`message_delta`/`message_stop` | 见 §5 |

---

## 4. 防循环 / 边界规则（映射）

| 规则 | 阈值/机制 | 行为 |
|---|---|---|
| **最大跳数** | `MaxHops=8`（常量 `compat.MaxModelMapHops`） | L1+L2+（现有 L3 链）合计解析跳数超限→中止，错误码 `model_mapping_max_hops_exceeded`（500/400） |
| **环检测** | `visited map[string]bool`（复用 `model_mapped.go` 现有 `visitedModels` 模式） | 检出已访问名→`model_mapping_contains_cycle`（沿用现有错误） |
| **L1/L2 各 1 跳** | 设计上 L1、L2 各为单次 1对1 替换（非链）；L3 才是渠道内链式 | L1/L2 不自我循环；跨层环（B 又被某 C 指回）由全局 visited 拦截 |
| **A 未命中底仓且非直通** | L2 查无 + 非已知模型 | B=A 直接进路由；Ability 查无渠道→现有"无可用渠道"错误（自然 404/503，"客户的事"） |
| **映射目标不存在** | L1 的 A 在平台不存在 | 不在配置期拦（不强制白名单），调用期 Ability 无渠道→正常报错 |
| **Enabled=false** | 映射停用 | 视为该层未命中（恒等），继续下一层 |

---

## 5. 流式（SSE）两协议互转（D7 详解）

`StreamState` 持有：`messageOpen bool`、`blockIndex int`、`accumUsage UsageIR`、`modelId/id`。

### 5.1 Anthropic 入站 ← OpenAI 上游（targetProto=openai, inFmt=claude）
逐 OpenAI chunk `data:{choices:[{delta}]}` → `ParseStreamChunk` 产 `ChatDeltaIR` → `SerializeStreamChunk(claude)` **1→N**：
- 首个增量：发 `message_start` + `content_block_start(index:0)`，置 `messageOpen=true`
- 文本增量：`content_block_delta{type:text_delta}`
- `tool_calls` 增量：`content_block_start(type:tool_use)` + `input_json_delta`
- 收到 `finish_reason`：`content_block_stop` + `message_delta{stop_reason=map(finish_reason), usage}` + `message_stop`
- OpenAI `[DONE]` → 触发上述收尾（若未发过 message_stop）

### 5.2 OpenAI 入站 ← Anthropic 上游（targetProto=claude, inFmt=openai）
逐 Anthropic event → IR → OpenAI chunk：
- `message_start` → 缓存 id/model（不立即发 OpenAI chunk）
- `content_block_delta(text_delta)` → `data:{choices:[{delta:{content}}]}`
- `tool_use`/`input_json_delta` → `delta:{tool_calls:[...]}`
- `message_delta(stop_reason)` → 末 chunk `finish_reason=map(stop_reason)` + （若 inFmt 要求）`usage`
- `message_stop` → `data: [DONE]`

### 5.3 流式边界
- **首字节超时/上游中断**：已发出部分 SSE 无法回滚 → 发协议合规的终止事件（OpenAI `[DONE]`/Anthropic `message_stop`）+ 落 `Log Type=5 Error`（复用现有错误日志），usage 按已累计计费（多退少不补，对齐现有 `ReturnPreConsumedQuota` 语义）。
- **usage 时机差异**：OpenAI 需 `stream_options.include_usage`；Anthropic usage 在 `message_delta`/`message_start(input)`。回转时由 `StreamState.accumUsage` 兜底补齐，保证落 Log 的 token 数完整。

---

## 6. token 计数 / 白名单 / 错误码（边界）

### 6.1 token 计数差异
- 计费 token 以**上游真实返回的 usage**（经 D5 归一进 IR.Usage）为准；上游未返（如纯流式无 usage）→ 用现有 `service/token_counter.go` 按 B 模型本地估算（复用）。
- **B 用于定价**（L2 后），不是 C/A —— 保证暗箱替换后按真实成本/配置计费。

### 6.2 白名单（Token.ModelLimits）与映射的次序（决策）
- **校验对象 = A（L1 后，L2 前）**，理由：白名单是"客户可买的公开模型"语义，应作用在公开名 A 上；B 客户不可见不应进白名单。
- 次序：步骤 4(L1) → 步骤 5(白名单校验 A) → 步骤 6(L2)。
- 记 **ADR-COMPAT-08**：ModelLimits 校验在 A 层。

### 6.3 错误码映射（协议转换失败 / 上游错误回转）
| 场景 | 行为 | 错误码 |
|---|---|---|
| 入站报文解析失败 | `ParseRequest` err | 400 `invalid_request_error`（按 inFmt 协议错误体格式返回） |
| 能力缺口（inFmt 用 tools，targetProto `Tools=false`） | 预检拒绝 | 400 `unsupported_feature`（ADR-COMPAT-09：首版 OpenAI/Claude 能力对等，主要为未来协议预留） |
| 映射环/超跳数 | §4 | 400/500 `model_mapping_*` |
| 上游错误回转 | 上游 4xx/5xx 错误体 targetProto → IR error → inFmt error 体 | **状态码保持上游原码**；错误体翻译为 inFmt 协议的错误 schema（复用 `MaskSensitiveErrorWithStatusCode` 脱敏，落 `Log Type=5`） |
| 序列化失败（IR→目标） | `SerializeRequest` err | 500 `protocol_conversion_failed` |

> **状态码映射**：复用现有 `Channel.StatusCodeMapping`（DATA-MODEL §3）做上游码→对外码映射；协议层只负责"错误体 schema 翻译"，不改状态码逻辑。

---

## 7. 停止原因 / 错误映射表（D4 落地）

| IR StopReason | OpenAI finish_reason | Anthropic stop_reason |
|---|---|---|
| end_turn | stop | end_turn |
| max_tokens | length | max_tokens |
| stop_sequence | stop | stop_sequence |
| tool_use | tool_calls | tool_use |
| content_filter | content_filter | （Anthropic 无直接对应→映射 end_turn + 保留诊断） |

双向映射在协议适配器内实现；缺失方向用最近义兜底并记 `Log.Other` 诊断。

---

## 8. 关键状态/边界规则汇总

- **协议转换只决策一次**（步骤 9），输入仅 `inFmt`/`targetProto`，与映射跳数解耦（ADR-COMPAT-04）。
- **passthrough 优先**：inFmt==targetProto 时全程零转换零拷贝（直接透传流），性能与现网一致。
- **B 不可见三道闸**：(1) 无 user 路由读 PlatformModelMapping；(2) UserLogView 无 B；(3) 候选接口只返 A 全集。
- **映射不掺路由**：L1/L2 只产模型名 B，负载/容灾仍由 Ability + 现有重试（ARCHITECTURE-REVIEW §5）完成。
- **三库兼容**：新表/新列均 GORM AutoMigrate，JSON 用 TEXT，主键交 GORM（AGENTS.md Rule 2）。

### 新增 ADR（API/状态侧）
- **ADR-COMPAT-07**：OpenAI→Anthropic 缺 max_tokens 时填默认（模型配置或 4096）。
- **ADR-COMPAT-08**：ModelLimits 白名单校验作用在 A 层（L1 后 L2 前）。
- **ADR-COMPAT-09**：能力缺口预检拒绝；首版 OpenAI/Claude 能力对等，主要为后续协议预留。

---

## 9. 计费边界规则（增量，配套 BILLING-MODEL-ARCHITECTURE.md / BILLING-DATA-OBJECTS.md）

> 落 DECISIONS §4/§5/§6/§8 在异常/缺省/下架/兜底场景下的**记账边界**。所有规则铁律：**售价端异常可拒（关系客户实付），成本端异常不阻断（仅影响利润统计，记 0+告警）**。

### 9.1 成本缺失（ChannelModelCost 无行 / Enabled=false）
| 触发 | 行为 | 落点 |
|---|---|---|
| 结算时（链路第 17 步）`(实际 ChannelId, L2 后 B)` 在 `channel_model_costs` 无生效行 | **不阻断计费**：`quota_cost=0`、`quota_profit=quota_sell`、`PriceData.CostMissing=true` | 售价照常扣（客户体验不受影响） |
| 同上 | 告警：`Log.Other` 写 `{"cost_missing":true,"channel_id":X,"upstream_model":"B"}`；利润看板可筛「成本缺失」笔数 | DECISIONS §9 经营信号（提示超管补成本倍率） |
| `CompletionCostRatio=0`（输出成本缺省） | 回落用 `CostRatio × 现网 CompletionRatio(B)` 口径，非缺失 | 不告警（属正常缺省回落，ADR-BILL-02） |

> **理由**：成本是内部经营数据，缺失只让「这一笔利润算不全」，绝不能因此拒绝客户请求或多扣客户钱。

### 9.2 分组折扣缺省（GetGroupRatio 未命中 / 折扣值缺失）
| 触发 | 行为 | 落点 |
|---|---|---|
| `UsingGroup` 不在 `group_ratio` KV | 复用现网 `GetGroupRatio` 兜底**返回 1.0**（不打折，full 价）+ SysLog 告警 | 防止「分组缺配=免费」漏洞：缺省取 1.0（最不让利），不取 0 |
| `Group=auto`（跨组重试） | 复用现网 `ContextKeyAutoGroup` 覆盖逻辑（`quota.go:111`），按实际命中组取折扣 | 与现网一致 |
| 折扣值配成 0 | 视为「该组该模型免费」（现网 freeModel 语义，`price.go:126`）；售价=0、成本照记 | 利润=−成本（亏本告警），看板可见 |

> **铁律**：分组折扣缺省**绝不**回落为 0（会变成免费送），统一回落 **1.0**（基准价不打折）。

### 9.3 对外模型下架（PublicModel.Enabled=false）但有存量 key 仍在调
| 触发 | 行为 | 落点 |
|---|---|---|
| A 已下架（`public_models.enabled=false`），但客户存量 key/映射仍发 model=A（或 C→A 命中下架 A） | **决策（ADR-BILL-08）：下架 = 停止对客户「可见/可下单」，但不立即拒绝存量调用**——下架只从「客户目录/候选联想」移除，**调用期是否拒绝由独立开关 `RejectDisabledPublicModel` 控制** | 避免下架瞬间打断在途客户业务 |
| `RejectDisabledPublicModel=false`（默认，平滑下架） | 仍按 A 的 `base_price_ratio` 计费（售价口径不变）→ 正常路由 → 正常记成本/利润；`Log.Other` 写 `{"public_model_disabled":true}` 标记 | 客户无感，超管可在看板看到「下架仍在用」的存量 |
| `RejectDisabledPublicModel=true`（硬下架） | L1 后判定 A 下架 → 拒绝，错误码 `model_not_available`（按 inFmt 协议错误体），落 `Log Type=5` | 需要硬停时用 |
| A 软删（`deleted_at` 非空） | 等同硬下架：路由维 A→B 已无 PublicModel 商品权威 → `model_not_available` | 软删比下架更强 |

> **售价口径在下架场景仍取 A 的 base_price_ratio**：下架不改价，只改「可见/可下单」。存量计费一致性由「下架不删 model_ratio KV」保证（ADR-BILL-01a 的保存动作不因下架而清 KV）。

### 9.4 兜底切换的成本记账边界（DECISIONS §4 售价恒定/成本跟实际供应商）
| 触发 | 行为 | 落点 |
|---|---|---|
| 路由首选渠道 X 失败 → 重试切渠道 Y（现网 Ability 重试） | **售价恒定**：`quota_sell` 始终按 A 的 `base_price_ratio × GroupRatio`，与切到谁无关 | DECISIONS §4「兜底切供应商时售价恒定」 |
| 同上 | **成本跟实际成交渠道**：以**最终成功的 ChannelId**（= `RelayInfo.ChannelId` 终值）查 ChannelModelCost；失败的 X 不记成本（X 未产生上游计费 token） | 链路第 17 步用终值 ChannelId |
| 重试多次后全失败 | 无 usage → `quota_sell=0`（现网 totalTokens==0 分支，`quota.go:217`）、`quota_cost=0`、退预扣额度；落 `Log Type=5` | 复用现网 ReturnPreConsumedQuota |
| 流式中途切换（已发部分 SSE 后上游断） | 已累计 usage 按**断点时所在渠道**记成本（`StreamState.accumUsage` × 该渠道 cost_ratio）；售价同口径按 A | 对齐兼容层 §5.3 流式边界「多退少不补」 |
| 跨品质兜底（红线，DECISIONS §3） | **结构性禁止**：同一 A→B 下 Ability 只挂同品质渠道（成本不同、品质同）；不同品质是不同 A→不同 B，不会在一次重试中跨品质 | ADR-BILL-05；若运维误配跨品质渠道进同 B，属配置错误，需后台校验拦（建议 PublicModel/Ability 配置期校验） |

### 9.5 计费边界 ADR 汇总（本节新增）
- **ADR-BILL-08**：对外模型下架 = 停可见/可下单，存量调用默认平滑放行（`RejectDisabledPublicModel=false`），可切硬拒。
- **ADR-BILL-09**：成本端异常（缺行/停用）一律不阻断计费，记 0 + Log.Other 告警；售价端异常（分组缺配）回落 1.0 不回落 0。
- **ADR-BILL-10**：兜底切换售价恒定（挂 A），成本以最终成功 ChannelId 记；失败渠道不记成本；流式断点按断点渠道记。
