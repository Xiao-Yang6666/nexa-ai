# PRD — Relay 网关多协议中转（FL-relay）

> 分片：Relay 网关多协议中转 D11/D13、视频内容代理。对应流程图 `flow/FL-relay.md`、状态矩阵 `PAGE-STATE-MATRIX.md §J`。
> 数据对象字段一律复用 `DATA-MODEL.md`（`Token §2`、`Channel §3`、`Log §5`、`Task §9`）逐字段抽取的真实字段。
> 关键函数/常量：`TokenAuth`、`ModelRequestRateLimit`、`Distribute`、`Path2RelayMode`、`RelayFormat*`、`ShouldRetryByStatusCode`、`common.RetryTimes`、`MaskSensitiveErrorWithStatusCode`、`RecordErrorLog`、`DisableChannel`、`streamSupportedChannels`、`VideoProxy`、`ValidateURLWithFetchSetting`、`TaskStatusSuccess`。Relay 与计费衔接见 `FL-billing.md BL-2`，与选渠/禁用见 `FL-channel.md CH-2/CH-3/CH-5`，本片只标注复用不重画。
> 本片覆盖功能 ID：**F-3026 / F-3027 / F-3028 / F-3029 / F-3030 / F-3031 / F-3032 / F-3033 / F-3034 / F-3035 / F-3036 / F-3037 / F-3057 / F-4046 / F-3060 / F-3061 / F-3062**.
> 协议兼容层 / 两层模型映射 / 端到端经营链路（RL-6/RL-7/RL-8）业务规则唯一权威 = `COMPAT-BILLING-DECISIONS.md`（§1 协议兼容 / §2 两层映射 / §10 端到端链路）；架构 = `reviews/COMPAT-LAYER-ARCHITECTURE.md`；数据对象 = `reviews/COMPAT-LAYER-DATA-OBJECTS.md` + `reviews/BILLING-DATA-OBJECTS.md`，Log 字段以 `DATA-MODEL.md §5` 已落 10 字段为准。本片只标注复用与衔接，不重画两层映射/计费明细（见 `FL-billing.md`、`FL-model.md`、`FL-channel.md`）。

---

## RL-1 OpenAI 兼容端点主链路（鉴权→额度→选渠→转发→结算→记日志）

- **功能 ID / 优先级**：F-3026、F-3037 / P0
- **来源**：FC-086（`router/relay-router.go POST /chat/completions 与 /completions` 走 `Relay(RelayFormatOpenAI)`、中间件链 `TokenAuth → ModelRequestRateLimit → Distribute`、转发前调 BL-2 预扣、转发后按真实 token 结算落 Log+UseData）
- **角色 / Owner**：登录用户 / 外部客户端；Owner 模块 = Relay 网关（外部 API + 系统内部态）
- **触发**：客户端 `POST /v1/chat/completions` 或 `/v1/completions`（携 `Bearer sk-` token key）

### 1. 场景
请求经中间件链 `TokenAuth → ModelRequestRateLimit → Distribute` 进入 Relay：`TokenAuth` 鉴权失败拒绝、`ModelRequestRateLimit` 超限拒绝，`Distribute` 注入分组/模型。转发前调 BL-2 `PreConsumeQuota` 预扣，`userQuota<=0` 时返回 403 且 SkipRetry（不重试）；否则冻结 `FinalPreConsumedQuota`。调 CH-2 `GetRandomSatisfiedChannel` 选满足渠道，无可用渠道上抛错误。转发上游后按响应 `usage` 的真实 token 结算（多退少不补），`PostConsumeQuota` 落 `Log` + UseData，回传响应。这是「客户端↔网关↔计费↔选渠↔上游」的多参与方时序链路。

### 2. 前置条件
- 请求携 `Token.Key`（`sk-` 前缀）过 `TokenAuth`。
- 令牌过 `ModelRequestRateLimit` 限流（C5）。
- BL-2 预扣需 `userQuota>0`；结算以上游回报 `usage` 真实 token 为准。

### 3. 主流程（对应 RL-1 时序 C→G→A→B→S→U→C）
1. 客户端 `POST /v1/chat/completions`（`Bearer sk-`）发往网关。
2. 网关交 `TokenAuth + ModelRequestRateLimit`：鉴权失败/限流超限→拒绝；通过则 `Distribute` 注入分组/模型。
3. 网关调 BL-2 `PreConsumeQuota` 预扣：`userQuota<=0`→403 SkipRetry；否则冻结 `FinalPreConsumedQuota`。
4. 网关调 CH-2 `GetRandomSatisfiedChannel`：无可用渠道→上抛错误；否则选定渠道。
5. 网关向上游转发（流式/非流式）→上游回响应 + `usage`（真实 token）。
6. 网关按真实 token 结算（多退少不补）→BL-2 `PostConsumeQuota` 落 `Log` + UseData→回传响应给客户端。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `TokenAuth` 失败 | A→G 鉴权失败 | 拒绝请求 | 鉴权失败拒绝态（401） |
| `ModelRequestRateLimit` 超限 | A→G 限流超限 | 拒绝请求 | 限流超限拒绝态 |
| `userQuota<=0` | B→G 预扣 | 403 SkipRetry（复用 BL-2） | 预扣 403 态（不重试） |
| 无可用渠道 | S→G | 上抛错误（复用 CH-2） | 无可用渠道态 |
| 结算 | B→G `PostConsumeQuota` | 按真实 token 多退少不补落 Log+UseData | 结算完成态 |

### 5. 数据对象（复用 DATA-MODEL `Token §2` + `Channel §3` + `Log §5`）
- **鉴权键** `Token.Key(key) string`（`varchar(128);uniqueIndex`，`sk-` 前缀）、`Token.Status(status) int default:1`（非启用拒绝）、`Token.Group(group) string`（Distribute 注入分组）。
- **转发渠道** `Channel.Id(id)`、`Channel.Type(type) int`、`Channel.Key(key) not null`（CH-2 选定后转发用）。
- **结算落账** `Log`：`UserId(user_id) int index`、`ModelName(model_name)`、`PromptTokens(prompt_tokens) int default:0`、`CompletionTokens(completion_tokens) int default:0`、`Quota(quota) int default:0`、`ChannelId(channel) int index`、`TokenId(token_id) int`、`IsStream(is_stream) bool`、`UseTime(use_time) int`、`Type(type) int`（`2=Consume`）。

### 6. 验收标准
- [ ] `TokenAuth` 失败（无效 `Token.Key` 或 `Status` 非启用）→ 返回鉴权失败（401），不进入 Relay。
- [ ] `ModelRequestRateLimit` 超限 → 拒绝请求（限流码），不预扣。
- [ ] BL-2 预扣时 `userQuota<=0` → 返回 403 且 SkipRetry（不触发跨渠道重试）。
- [ ] CH-2 选渠返回 nil/err（无满足渠道）→ 上抛无可用渠道错误，不转发上游。
- [ ] 转发成功 → 按上游 `usage` 真实 token 结算（多退少不补），写一条 `Log`（`Type=2 Consume`，含 `prompt_tokens/completion_tokens/quota/channel/model_name`）。
- [ ] 流式与非流式请求均能完成转发并落 `Log`（`IsStream` 正确标记）。

### 7. 所触及页面状态（对齐 §J「OpenAI 主链路」）
鉴权失败拒绝态（TokenAuth）· 限流超限拒绝态 · 预扣 403 态（userQuota<=0 复用 BL-2）· 无可用渠道态（复用 CH-2）· 额度冻结态（FinalPreConsumedQuota 内部）· 转发中态（流式/非流式）· 结算完成态（真实 token 多退少不补 Log+UseData 入账 终态）。

---

## RL-2 端点路径识别与多协议格式分发（Path2RelayMode 前缀有序匹配）

- **功能 ID / 优先级**：F-3027、F-3028、F-3029、F-3030、F-3031、F-3032、F-3033、F-3034、F-3035、F-3057 / P1（多数）/ P2（F-3030/F-3031/F-3033/F-3057）
- **来源**：FC-087~FC-097（`relay/relay_mode.go Path2RelayMode`：`/v1/responses/compact` 先于 `/v1/responses`、`HasSuffix(embeddings)` 判 Embeddings、`/images/variations` 走 `RelayNotImplemented`、`/v1/edits` 独立 `RelayModeEdits`、`/v1/messages` Claude、`/v1beta/models/*` Gemini、`/v1/realtime` WebSocket 升级）
- **角色 / Owner**：登录用户 / 外部客户端；Owner 模块 = Relay 网关（系统内部态）
- **触发**：请求进入 relay-router 按路径判 `RelayMode` 与 `RelayFormat`

### 1. 场景
`Path2RelayMode` 按路径有序匹配定 `RelayMode/RelayFormat`，前缀顺序敏感：`/v1/responses/compact` **必须先于** `/v1/responses` 匹配（顺序错会把 compact 误判为普通 responses）；`/v1/messages` 走 `RelayFormatClaude` 原生、`/v1beta/models` 前缀走 `RelayFormatGemini` 原生、`/v1/realtime` 走 `RelayFormatOpenAIRealtime`（WebSocket 升级）；`HasSuffix(embeddings)` 任意后缀判 `RelayFormatEmbedding`；`/v1/images/variations` 走 `RelayNotImplemented`；`/v1/edits` 是独立 `RelayModeEdits`（区别于 `/images/edits`）；其余默认走 `RelayFormatOpenAI`。最终分发到对应 adapter。

### 2. 前置条件
- 路由匹配按固定前缀顺序自上而下短路。
- `/v1/responses/compact` 的判定置于 `/v1/responses` 之前。
- `embeddings` 以后缀匹配（不限定完整路径）。

### 3. 主流程（对应 RL-2 节点 rd_in→rd_go）
1. 请求进入 relay-router（rd_in），判 `=/v1/responses/compact`（rd_p1）→是走 `RelayFormatOpenAIResponsesCompaction`（rd_compact）。
2. 否判 `=/v1/responses`（rd_p2）→是 `RelayFormatOpenAIResponses`（rd_resp）。
3. 否判 `=/v1/messages`（rd_p3）→是 `RelayFormatClaude` 原生（rd_claude）。
4. 否判前缀 `/v1beta/models`（rd_p4）→是 `RelayFormatGemini` 原生（rd_gemini）。
5. 否判 `=/v1/realtime`（rd_p5）→是 `RelayFormatOpenAIRealtime` WebSocket 升级（rd_ws）。
6. 否判 `HasSuffix(embeddings)`（rd_p6）→是 `RelayFormatEmbedding`（rd_emb）。
7. 否判 `=/v1/images/variations`（rd_p7）→是 `RelayNotImplemented`（rd_ni）。
8. 否判 `=/v1/edits`（rd_p8）→是 `RelayModeEdits` 独立 legacy（rd_edits）；否则默认 `RelayFormatOpenAI`（rd_def）。
9. 各分发分支汇流到分发到对应 adapter 态（rd_go）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `=/v1/responses/compact` | rd_p1-是 | 先匹配 compact | compact 优先匹配态 |
| `=/v1/messages` / 前缀 `/v1beta/models` / `=/v1/realtime` | rd_p3/p4/p5-是 | Claude / Gemini / Realtime 分发 | 原生协议分发态 |
| `HasSuffix(embeddings)` | rd_p6-是 | `RelayFormatEmbedding` | embeddings 后缀匹配态 |
| `=/v1/images/variations` | rd_p7-是 | `RelayNotImplemented` | variations 未实现态 |
| `=/v1/edits` | rd_p8-是 | `RelayModeEdits`（独立） | edits 独立 legacy 态 |
| 其余路径 | rd_def | `RelayFormatOpenAI` | 默认 chat/completions 态 |

### 5. 数据对象（复用 路径分发运行态 + DATA-MODEL `Channel §3`）
- **分发判定输入**：HTTP 请求路径（`Path2RelayMode` 入参，非持久化字段），输出 `RelayMode` 与 `RelayFormat` 常量。
- **下游渠道** `Channel.Type(type) int`：分发出的 `RelayFormat` 在转发阶段映射到对应渠道类型的 adapter（衔接 RL-4）。
- **变体说明**：`RelayNotImplemented`（variations）与 `RelayModeEdits`（legacy edits）为终止/独立常量，不进入普通 OpenAI 链。

### 6. 验收标准
- [ ] `POST /v1/responses/compact` → 命中 `RelayFormatOpenAIResponsesCompaction`，且该判定在 `/v1/responses` 之前（顺序对调会误判）。
- [ ] 任意以 `embeddings` 结尾的路径（含 `/v1/embeddings`）→ 判 `RelayFormatEmbedding`。
- [ ] `POST /v1/images/variations` → 返回 `RelayNotImplemented`（未实现响应，非 500）。
- [ ] `POST /v1/edits` → 判独立 `RelayModeEdits`，与 `/images/edits` 区分。
- [ ] `/v1/messages` → `RelayFormatClaude`；`/v1beta/models/*` 前缀 → `RelayFormatGemini`；`/v1/realtime` → `RelayFormatOpenAIRealtime`（WebSocket 升级）。
- [ ] 未命中任何特殊前缀的路径 → 默认 `RelayFormatOpenAI`（chat/completions）。

### 7. 所触及页面状态（对齐 §J「协议格式分发」系统内部态）
compact 优先匹配态（先于 /v1/responses）· responses/claude/gemini/realtime 分发态 · embeddings 后缀匹配态 · variations 未实现态（RelayNotImplemented）· edits 独立 legacy 态 · 默认 chat/completions 态 · 分发到 adapter 态（终态）。

---

## RL-3 上游错误处理与按状态码重试/禁用渠道（错误码可测）

- **功能 ID / 优先级**：F-3037 / P0
- **来源**：FC-086（`controller/relay.go ShouldRetryByStatusCode(code)`、`common.RetryTimes`、`service.DisableChannel`、`RecordErrorLog(MaskSensitiveErrorWithStatusCode)`、按 RelayFormat 构造错误）
- **角色 / Owner**：系统（错误处置）/ root（被通知人）；Owner 模块 = Relay 网关（系统内部态）
- **触发**：上游返回错误状态码

### 1. 场景
上游返回错误码时 `ShouldRetryByStatusCode(code)` 决定可重试还是跳过：可重试且重试次数 `< common.RetryTimes` 则换渠道重试（复用 CH-2/CH-5），达 `RetryTimes` 上限则重试耗尽返回错误；不可重试直接跳过。并行地判渠道 `AutoBan=1` 且命中禁用条件则 `DisableChannel`（复用 CH-3，通知 root），否则渠道保持状态。错误日志经 `MaskSensitiveErrorWithStatusCode` 脱敏后 `RecordErrorLog`（记 channel/model/status_code）。最后按 `RelayFormat`（OpenAI/Claude/Gemini）分别构造错误响应结构返回。三条处置（重试判定 ⊕ 禁用判定 ⊕ 脱敏记录）汇聚到格式化返回。

### 2. 前置条件
- `ShouldRetryByStatusCode(code)` 给出该状态码是否可重试。
- 重试上限为 `common.RetryTimes`。
- 仅 `Channel.AutoBan=1` 且命中禁用条件的渠道才自动禁用。

### 3. 主流程（对应 RL-3 节点 re_err→re_ret）
1. 上游返回错误状态码（re_err），判 `ShouldRetryByStatusCode`（re_retry）。
2. 可重试→判重试次数 `< RetryTimes`（re_count）：是换渠道重试复用 CH-2/CH-5（re_again）；否重试耗尽终态返错（re_giveup）。
3. 不可重试→跳过重试（re_stop）。
4. re_again / re_stop 汇到判 `Channel.AutoBan=1` 且命中禁用条件（re_ban）：是 `DisableChannel` 复用 CH-3 通知 root（re_disable）；否渠道保持状态（re_keep）。
5. 两路汇到 `MaskSensitiveErrorWithStatusCode` 脱敏 → `RecordErrorLog`（re_log）。
6. 按 `RelayFormat` 构造错误（re_fmt）→OpenAI（re_oai）/ Claude（re_cla）/ Gemini（re_gem）；与 re_giveup 一并汇到错误响应返回态（re_ret）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 可重试码且 `重试次数 < RetryTimes` | re_count-是 | 换渠道重试（CH-2/CH-5） | 可重试换渠道态 |
| 可重试码且 `重试次数 >= RetryTimes` | re_count-否 | 终止重试返错 | 重试耗尽态 |
| 不可重试码 | re_retry-不可重试 | 跳过重试 | 跳过重试态 |
| `AutoBan=1` 且命中禁用条件 | re_ban-是 | `DisableChannel` + 通知 root | 渠道自动禁用态 |
| `AutoBan=0` 或未命中 | re_ban-否 | 不禁用 | 渠道保持态 |
| `RelayFormat`=OpenAI/Claude/Gemini | re_fmt | 分别构造错误结构 | 对应格式化错误态 |

### 5. 数据对象（复用 DATA-MODEL `Channel §3` + `Log §5`）
- **禁用判定/写状态** `Channel.AutoBan(auto_ban) *int default:1`（仅 `=1` 才允许自动禁用）、`Channel.Status(status) int`（命中置自动禁用，复用 CH-3）、`Channel.StatusCodeMapping(status_code_mapping)`（状态码禁用映射来源）。
- **错误日志** `Log`：`Type(type) int`（`5=Error`）、`ChannelId(channel) int index`、`ModelName(model_name) string`、`Content(content) string`（经 `MaskSensitiveErrorWithStatusCode` 脱敏后写）、`Other(other) string`（附 `status_code` 等 JSON）。
- **重试调度量**：`common.RetryTimes`（重试上限，`>=` 判耗尽）；`ShouldRetryByStatusCode(code)` 的状态码判定（运行态）。

### 6. 验收标准
- [ ] 上游返回可重试状态码且当前重试次数 `< common.RetryTimes` → 换渠道发起重试（复用 CH-2/CH-5）。
- [ ] 重试次数达到 `common.RetryTimes` → 不再重试，返回错误（重试耗尽态）。
- [ ] 上游返回 `ShouldRetryByStatusCode` 判为不可重试的状态码 → 直接跳过重试。
- [ ] 命中禁用条件且渠道 `AutoBan=1` → `DisableChannel`，`Channel.Status` 置自动禁用并通知 root。
- [ ] 命中禁用条件但 `AutoBan=0` → 渠道保持状态，不禁用。
- [ ] 错误日志写入前经 `MaskSensitiveErrorWithStatusCode` 脱敏，`Log`（`Type=5 Error`）记录 `channel/model_name/status_code`。
- [ ] 错误响应按 `RelayFormat` 分别构造：OpenAI 错误结构 / Claude 错误结构（含 StatusCode）/ Gemini 错误结构。

### 7. 所触及页面状态（对齐 §J「错误处理/重试」系统内部态）
可重试换渠道态（复用 CH-2/CH-5）· 重试耗尽态（>=RetryTimes 返错）· 跳过重试态（不可重试码）· 渠道自动禁用态（AutoBan 命中 复用 CH-3 通知 root）· 渠道保持态 · 错误脱敏记录态（MaskSensitive 记 channel/model/status_code）· OpenAI/Claude/Gemini 格式化错误态 · 错误响应返回态（终态）。

---

## RL-4 厂商原生协议适配（37 adapter 请求/响应双向转换 + 流式能力）

- **功能 ID / 优先级**：F-3034、F-3035、F-3036 / P1
- **来源**：FC-095/FC-096/FC-097（`relay/channel/` 下 37 个 adapter、渠道类型常量至 57、`gemini/claude` 原生 adapter、AGENTS Rule4 `streamSupportedChannels`）
- **角色 / Owner**：系统（协议适配）；Owner 模块 = Relay 网关（系统内部态）
- **触发**：请求被分发到选定渠道类型的上游

### 1. 场景
请求分发到渠道类型对应 adapter（`relay/channel/` 下 37 个），由 adapter 把**统一入参**转为厂商原生协议、再把厂商响应反解析回统一格式。该渠道类型 adapter 缺失则无法中转。请求要流式时判该渠道是否在 `streamSupportedChannels`：不在则降级为非流式调用，在则以 SSE 流式调用上游（AGENTS Rule4）。上游响应解析失败交 RL-3 处置，成功则反解析回统一格式出站。这是「入站统一格式 → 适配转换 →（流式分支）→ 上游 → 反向解析 → 出站统一格式」的双向管线。

### 2. 前置条件
- 渠道类型经 CH-2 选定（`Channel.Type`）。
- 该类型须有对应 adapter（否则无法中转）。
- 流式能力由该渠道是否在 `streamSupportedChannels` 决定。

### 3. 主流程（对应 RL-4 节点 ad_in→ad_ok）
1. 统一格式请求 + 选定渠道类型入站（ad_in），判该类型 adapter 存在（ad_find）→不存在则无法中转（ad_miss）。
2. 存在则 adapter 转为厂商原生协议（ad_conv）→判请求要流式且在 `streamSupportedChannels`（ad_stream）：否降级非流式（ad_block）；是以 SSE 流式调用（ad_sse）。
3. 两路汇到发往厂商上游（ad_call）→判上游响应解析成功（ad_resp）：失败交 RL-3 处置（ad_perr）；成功反解析回统一格式（ad_back）→统一格式响应出站态（ad_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 该类型 adapter 缺失 | ad_find-否 | 中止 | adapter 缺失态（无法中转） |
| 流式请求 + 不在 `streamSupportedChannels` | ad_stream-否 | 降级非流式 | 非流式降级态 |
| 流式请求 + 在 `streamSupportedChannels` | ad_stream-是 | SSE 流式调用 | 流式 SSE 态 |
| 上游响应解析失败 | ad_resp-失败 | 交 RL-3 | 响应解析失败态 |
| 上游响应解析成功 | ad_resp-成功 | 反解析回统一格式 | 反向解析态→出站 |

### 5. 数据对象（复用 DATA-MODEL `Channel §3`）
- **adapter 选择键** `Channel.Type(type) int default:0`（渠道类型 provider 编号，决定 `relay/channel/` 下哪个 adapter；缺失则无法中转）。
- **转发密钥/基址** `Channel.Key(key) not null`、`Channel.BaseURL(base_url) *string default:''`（adapter 转厂商原生协议时拼接）。
- **流式能力** `streamSupportedChannels`（按渠道类型枚举的流式支持集合，AGENTS Rule4 决定是否加入；不在集合则降级非流）。

### 6. 验收标准
- [ ] 选定渠道类型存在对应 `relay/channel/` adapter → 统一入参被转为该厂商原生协议（Gemini/Claude 等）。
- [ ] 选定渠道类型无对应 adapter → 该渠道无法中转（返回 adapter 缺失态）。
- [ ] 流式请求且渠道在 `streamSupportedChannels` → 以 SSE 流式调用上游。
- [ ] 流式请求但渠道不在 `streamSupportedChannels` → 降级为非流式调用。
- [ ] 上游响应解析成功 → adapter 反解析回统一格式出站。
- [ ] 上游响应解析失败 → 交 RL-3 错误处置（不直接返回未处理错误）。

### 7. 所触及页面状态（对齐 §J「厂商适配」系统内部态）
adapter 缺失态（该渠道类型无法中转）· 协议转换态（统一→厂商原生）· 非流式降级态（不在 streamSupportedChannels）· 流式 SSE 态（支持流式）· 响应解析失败态（交 RL-3）· 反向解析态（厂商→统一格式）· 统一格式响应出站态（终态）。

---

## RL-5 视频内容网关代理（本人校验 + 终态校验 + SSRF + 流式回传）

- **功能 ID / 优先级**：F-4046 / P1
- **来源**（`controller/video_proxy.go VideoProxy`：校验 task 属本人且 `Status==TaskStatusSuccess`、按 `channel.Type` 分支解析 URL（Gemini 用 `task.PrivateData.Key + x-goog-api-key`、Vertex、OpenAI/Sora 拼 `/v1/videos/.../content`）、`data:` URL base64 直出、`ValidateURLWithFetchSetting` SSRF 校验后 `io.Copy` 流式回写、`Cache-Control max-age=86400`）
- **角色 / Owner**：登录用户；Owner 模块 = Relay 网关（视频内容代理）
- **触发**：用户对已完成视频任务请求 `GET /videos/:task_id/content`

### 1. 场景
`VideoProxy` 先校验 task **属本人**（`GetByTaskId` 带 userID）且存在，否则 404；再校验 `Status==TaskStatusSuccess`，未完成返回当前状态错误。通过后按 `channel.Type` 分支解析上游 URL：Gemini 用 `task.PrivateData.Key + x-goog-api-key`、Vertex 取地址、OpenAI/Sora 拼 `/v1/videos/.../content`。`data:` 协议 URL 走 base64 解码直出；其余 http URL 经 `ValidateURLWithFetchSetting` SSRF 校验，未过 403，过则 `io.Copy` 流式回写并设 `Cache-Control max-age=86400`。这是「归属校验 → 终态校验 → 类型分支取 URL → SSRF → 回传」的代理下发链。

### 2. 前置条件
- 任务须存在且 `UserId` 等于当前用户（`GetByTaskId` 双条件）。
- 任务须为终态成功 `Status==TaskStatusSuccess`。
- 非 `data:` URL 须过 `ValidateURLWithFetchSetting` SSRF 校验。

### 3. 主流程（对应 RL-5 节点 vp_in→vp_ok）
1. `GET /videos/:task_id/content`（vp_in），判 task 属本人且存在（vp_own）→否 404（vp_404）。
2. 是则判 `Status=TaskStatusSuccess`（vp_done）→否返回当前任务状态错误未完成（vp_state）。
3. 是则按 `channel.Type` 解析 URL（vp_type）→Gemini 用 `PrivateData.Key + x-goog-api-key`（vp_g）/ Vertex 取地址（vp_v）/ OpenAI·Sora 拼 `/v1/videos/.../content`（vp_o）。
4. 三路汇到判 URL 是否 `data:` 协议（vp_scheme）→是 base64 解码直出（vp_b64）；否 http 经 `ValidateURLWithFetchSetting`（vp_ssrf）→未过 403（vp_403）；过则 `io.Copy` 流式回写 `Cache-Control max-age=86400`（vp_copy）。
5. vp_b64 / vp_copy 汇到视频内容下发态（vp_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| task 不存在/非本人 | vp_own-否 | 拒绝 | 404 态 |
| `Status≠TaskStatusSuccess` | vp_done-否 | 返回当前状态错误 | 任务未完成态 |
| `channel.Type`=Gemini/Vertex/OpenAI·Sora | vp_type | 按类型解析 URL | 渠道类型分支态 |
| URL 为 `data:` | vp_scheme-是 | base64 解码直出 | data: 直出态 |
| SSRF 校验未过 | vp_ssrf-否 | 拒绝 | SSRF 拦截态（403） |
| SSRF 校验通过 | vp_ssrf-是 | `io.Copy` 回写 | 流式回写态（max-age=86400） |

### 5. 数据对象（复用 DATA-MODEL `Task §9` + `Channel §3`）
- **归属/终态校验** `Task`：`UserId(user_id) int index`（`GetByTaskId` 与 task_id 双条件保证仅本人）、`TaskID(task_id) string varchar(191);index`、`Status(status) TaskStatus`（须 `==SUCCESS`，枚举 `NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN`）。
- **URL 解析私钥** `Task.PrivateData(-) TaskPrivateData json`（`json:"-"` 不下发；Gemini 取其中 `Key` + `x-goog-api-key`）。
- **类型分支键** `Channel.Type(type) int`（Gemini/Vertex/OpenAI·Sora 各分支取上游地址）、`Channel.BaseURL(base_url) *string`（拼 `/v1/videos/.../content`）。

### 6. 验收标准
- [ ] task 不存在或 `Task.UserId` 不等于当前用户 → 返回 404，不下发内容。
- [ ] `Task.Status != TaskStatusSuccess`（含 `IN_PROGRESS`/`FAILURE` 等）→ 返回当前任务状态错误（未完成），不取上游 URL。
- [ ] `channel.Type`=Gemini → 用 `Task.PrivateData.Key + x-goog-api-key` 解析 URL，且 `PrivateData` 不下发给用户。
- [ ] URL 为 `data:` 协议 → base64 解码直出，不走 SSRF/HTTP 拉取。
- [ ] 非 `data:` URL 经 `ValidateURLWithFetchSetting` 未通过 → 返回 403（SSRF 拦截）。
- [ ] SSRF 通过 → `io.Copy` 流式回写，响应头含 `Cache-Control: max-age=86400`。

### 7. 所触及页面状态（对齐 §J「视频内容代理」）
404 态（任务不存在/非本人）· 任务未完成态（Status≠Success 返回当前状态）· 渠道类型分支态（Gemini/Vertex/OpenAI·Sora 取 URL）· data: 直出态（base64 解码）· SSRF 拦截态（403 校验未通过）· 流式回写态（io.Copy，Cache-Control max-age=86400）· 视频内容下发态（终态）。

---

## RL-6 协议兼容层（一个 key 通吃端点：OpenAI ⇄ Anthropic 双向互转 + 可插拔适配器 + IR）

- **功能 ID / 优先级**：F-3060 / P0
- **来源**：COMPAT-BILLING-DECISIONS §1（一个 key 通吃端点）、COMPAT-LAYER-ARCHITECTURE §1/§2（管线、协议适配器抽象、IR 中间表示）、复用 `relay/channel/adapter.go` 的 `ConvertOpenAIRequest`/`ConvertClaudeRequest` + `types/relay_format.go` 的 `RelayFormat`
- **角色 / Owner**：登录用户 / 外部客户端；Owner 模块 = Relay 网关（协议兼容层 `relay/compat` + `relay/compat/ir`）
- **触发**：客户端用 OpenAI 格式（`POST /v1/chat/completions`）或 Anthropic 格式（`POST /v1/messages`）打进同一把 key

### 1. 场景
客户端无论用 **OpenAI 格式** 还是 **Anthropic 格式（`/v1/messages`）** 打进来都接（本期只做这两个协议，注册表预留 Gemini/embedding/图像/语音扩展位，未实现时回落现有 per-channel 直转路径，不阻断现网）。网关把入站报文按入站协议适配器（`inAdapter`）解析进协议无关的 **IR 中间表示**（`ChatIR`），底层按「最终目标模型 B 所属供应商要求的协议」（`targetProto`，由选中 `Channel.Type` 推导）用目标协议适配器（`targetAdapter`）从 IR 序列化出站、调上游，再把上游响应反解析回 IR、按客户入站协议（`inFmt`）序列化回客户。**只看头和尾**：协议转换只在「确定最终目标模型 B + 选中 Channel」之后做唯一一次决策——`inFmt == targetProto` 则直通（passthrough，仅替换 model 字段为 B 透传），不等才走 IR 转换；中间模型映射跳几层（C→A→B）都不影响协议转换决策（ADR-COMPAT-04）。协议适配器是「注册表 + IR」的可插拔壳，OpenAI/Claude 实现内部复用现有 `Adaptor.ConvertOpenAIRequest/ConvertClaudeRequest`，不重写已验证 convert 逻辑（ADR-COMPAT-02）。

### 2. 前置条件
- 入站协议已由 RL-2 `Path2RelayMode`/`GenRelayInfoXxx` 识别为 `RelayFormat`（`/v1/chat/completions`→`RelayFormatOpenAI`、`/v1/messages`→`RelayFormatClaude`）。
- 协议适配器在进程启动 `init()` 注册进注册表（本期仅 `openai`、`claude`）；`Get(f)` 命中才走 compat 壳，未命中回落现有 per-channel 直转。
- 协议转换只在「确定 B + 选中 Channel」后做一次（RL-7 第 ⑤ 步），输入仅 `inFmt`（头）与 `targetProto`（尾）。

### 3. 主流程（对应 RL-6 节点 cp_in→cp_out）
1. 客户报文入站（cp_in），按 `inFmt` 取 `inAdapter`，判注册表是否命中（cp_reg）→未命中回落现有 per-channel 直转（cp_legacy）。
2. 命中则 `inAdapter.ParseRequest(raw)` → IR `ChatIR`（cp_parse）。
3. 经 RL-7 两层映射定 B、选中 Channel 得 `targetProto` 后，做头尾对比 `inFmt == targetProto`（cp_cmp）：相等→标记 `passthrough=true` 仅替换 model 为 B 直通（cp_pass）；不等→能力预检 `inAdapter.Capabilities() ∩ targetAdapter.Capabilities()`（cp_cap）后 `targetAdapter.SerializeRequest(IR)` 出站（cp_ser）。
4. 调上游（cp_call）→上游响应按 `targetProto` `ParseResponse`→IR `ChatRespIR`（cp_rparse）→按 `inFmt` `SerializeResponse` 回客户（cp_rser）；流式逐 chunk `ParseStreamChunk`/`SerializeStreamChunk`（可能 1→N event）。
5. passthrough 时直接透传上游响应流（cp_rpass）。两路汇到客户入站协议响应出站态（cp_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| `inFmt` 注册表未命中 | cp_reg-否 | 回落现有 per-channel 直转 | 兼容回落态（不阻断现网） |
| `inFmt == targetProto` | cp_cmp-相等 | 标记 passthrough，仅替换 model=B 透传 | 协议直通态 |
| `inFmt != targetProto` | cp_cmp-不等 | IR → targetProto 序列化转换 | 协议转换态（唯一一次） |
| 能力缺口（入站用 tools 但目标 `Tools=false`） | cp_cap-缺口 | 按 API-STATE「能力降级/拒绝」规则 | 能力降级/拒绝态 |
| 流式响应 | cp_rser | 逐 chunk 转，1→N SSE event | 流式回转态 |

### 5. 数据对象（复用 DATA-MODEL `Channel §3` + COMPAT-LAYER-DATA-OBJECTS §4/§5 IR/RelayInfo）
- **入站协议（头）** `RelayInfo.InboundFormat = types.RelayFormat`（复用现有 `RelayFormat`，`openai`/`claude`）；**目标协议（尾）** `RelayInfo.TargetProtocol`（选渠后由 `Channel.Type→protocol` 推导）；**直通标记** `RelayInfo.Passthrough bool`（`inFmt==target`）。
- **IR 中间表示**（内存，`relay/compat/ir`，不落库）：请求 `ChatIR{ Model(=B), System, Messages, Tools, ToolChoice, Stream, MaxTokens, ... PassthroughExtras }`、响应 `ChatRespIR{ StopReason, Usage{PromptTokens,CompletionTokens} }`、流式 `ChatDeltaIR` + `StreamState`。
- **适配器选择键** `Channel.Type(type) int`：决定 `targetProto` 与（passthrough 时）现有 `relay/channel/` adapter；`Channel.Key`、`Channel.BaseURL`（出站调上游）。
- **Log 协议落点**（复用 DATA-MODEL §5）：`InboundProtocol(inbound_protocol)`、`UpstreamProtocol(upstream_protocol)`、`ProtocolConverted(protocol_converted) bool`（false=直通）。

### 6. 验收标准
- [ ] 客户用 OpenAI 格式（`/v1/chat/completions`）与 Anthropic 格式（`/v1/messages`）打同一把 key 均被受理，不要求客户端改动。
- [ ] 入站协议 == 目标供应商协议 → `protocol_converted=false`（passthrough 直通，仅替换 model 为 B）。
- [ ] 入站协议 != 目标供应商协议 → 经 IR 双向转换，`protocol_converted=true`，响应转回客户入站协议。
- [ ] 协议转换只在「确定 B + 选中 Channel」后发生一次；中间模型映射跳数（C→A→B）不触发额外协议转换。
- [ ] 注册表未命中的协议（Gemini/embedding/...）→ 回落现有 per-channel 直转，不阻断现网；新增协议 = 实现 `ProtocolAdapter` + `init()` 注册一行，管线零改动。
- [ ] `Log.inbound_protocol`/`upstream_protocol`/`protocol_converted` 三字段正确落库。

### 7. 所触及页面状态（对齐 §J「协议兼容层」系统内部态）
入站解析态（inAdapter ParseRequest→IR）· 兼容回落态（注册表未命中走 per-channel 直转）· 协议直通态（inFmt==targetProto，passthrough）· 协议转换态（IR→targetProto 唯一一次）· 能力降级/拒绝态（能力缺口）· 流式回转态（逐 chunk 1→N）· 客户入站协议响应出站态（终态）。

---

## RL-7 端到端经营转发链路（协议识别→两层映射→减法约束→选渠容灾→头尾转换→双价记账→落 Log）

- **功能 ID / 优先级**：F-3061 / P0
- **来源**：COMPAT-BILLING-DECISIONS §10（完整一笔请求链路 end-to-end，①~⑨）、COMPAT-LAYER-ARCHITECTURE §1/§3（管线、两层映射执行次序 ADR-COMPAT-03）、BILLING-DATA-OBJECTS §3/§5（双价记账字段、PriceData/RelayInfo）、衔接 BL-2 计费 / CH-2·CH-5 选渠容灾 / RL-6 协议转换
- **角色 / Owner**：登录用户（分组=折扣等级）/ 外部客户端；Owner 模块 = Relay 网关（端到端经营链路编排）
- **触发**：客户（分组=vip，key）用入站协议发 `model=C`

### 1. 场景
一笔请求按 DECISIONS §10 固定链路串行编排：① **协议识别** `inFmt`（RL-2）；② **两层模型映射** `C →(客户层 L1 UserModelAlias)→ A →(超管层 L2 PlatformModelMapping)→ B`，B 客户不可见，先客户层再超管层、1对1 纯字符串替换、带环检测+最大跳数（FL-model）；③ **key 级减法约束校验** A 是否在该 key 的 `ModelLimits` 允许范围内（默认全开放行，纯减法自我约束非权限闸门）+ 端点减法 `inFmt ∈ EndpointLimits`（可选，默认全开）；④ **渠道池路由** 按 `(Token.Group/User.Group) × B` 查 Ability，多供应商按优先级/权重选一个，挂了按 CH-5 容灾兜底切下一个（售价恒定不随兜底波动）；⑤ **协议头尾对比转换** `inFmt vs 选中供应商协议` → 相等直通、不等转换（RL-6，唯一一次）；⑥ **扣客户** 按对外模型 A 的基准售价 × 分组折扣系数（`quota_sell`，客户可见）；⑦ **记成本** 按实际选中渠道 × 真实模型 B 的成本倍率（`quota_cost`，客户不可见，不乘折扣）；⑧ **响应** 按 `inFmt` 转回客户；⑨ **落 Log** C/A/B + 实际供应商 Y + 协议转换标记 + 售价/成本/利润。**映射不掺路由**（ADR-COMPAT-03）：L1/L2 只产出模型名，负载/容灾仍由 Ability + 现有路由完成；统计 B 取 L2 后快照（L3 渠道级 B' 仅写 `Other` 诊断）。

### 2. 前置条件
- 已过 RL-1 主链路前置（TokenAuth/限流/BL-2 预扣 `userQuota>0`）。
- 两层映射在 `Distribute` 之前/早段执行（Ability 路由维 `model` 必须是 B 而非 C/A，ADR-COMPAT-03）。
- 售价键 = A（`PublicModel.BasePriceRatio`/`GetModelRatio(A)`）× 分组折扣 `GetGroupRatio(UsingGroup)`；成本键 = `(实际 ChannelId, L2 后 B)` 的 `ChannelModelCost.CostRatio`，结算阶段取。

### 3. 主流程（对应 RL-7 节点 e2e_in→e2e_log，串联 DECISIONS §10 ①~⑨）
1. 客户（分组=vip，key）用入站协议发 `model=C`（e2e_in）→① 协议识别 `inFmt`（e2e_proto）。
2. ② 两层映射 `C→A→B`（e2e_map）：L1 客户层 C→A（user>group）、L2 超管层 A→B；C/A/B 三态写 ctx，B 客户不可见。
3. ③ key 级减法约束校验（e2e_limit）：A ∈ `ModelLimits`？（默认全开放行）+ `inFmt ∈ EndpointLimits`？（可选默认全开）→ 不在范围拒绝。
4. ④ 渠道池路由（e2e_route）：按 `Group × B` 查 Ability 按优先级/权重选渠（如选中 Y）；挂了按 CH-5 容灾兜底切下一个，售价恒定。
5. ⑤ 协议头尾对比（e2e_conv）：`inFmt vs Y供应商协议` → 相等直通 / 不等转换（复用 RL-6，唯一一次）。
6. ⑥ 扣客户（e2e_sell）：`quota_sell = BasePriceRatio(A) × GroupRatio(vip) × tokens`（客户可见，恒定）。
7. ⑦ 记成本（e2e_cost）：`quota_cost = CostRatio(Y, B) × tokens`（不乘折扣，客户不可见）；`(Y,B)` 无成本行→`quota_cost=0`、`quota_profit=quota_sell` 并 `Other` 写 `cost_missing`。
8. ⑧ 响应按 `inFmt` 转回客户（e2e_resp，复用 RL-6 回转）。
9. ⑨ 落 Log（e2e_log，终态）：C/A/B + 实际供应商 Y + `protocol_converted` + `quota_sell/quota_cost/quota_profit`（`quota_profit = quota_sell − quota_cost`，可为负=亏损告警）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| L1/L2 任一层未命中 | e2e_map | 该层恒等（C 未配则 A=C；A 未配底仓则 B=A） | 恒等透传态 |
| 映射成环/超最大跳数 | e2e_map | 环检测中止（FL-model 内核） | 映射环检测拒绝态 |
| A ∉ `ModelLimits` 或 `inFmt ∉ EndpointLimits` | e2e_limit-否 | 拒绝（减法约束命中） | key 级约束拒绝态 |
| 选中渠道挂掉 | e2e_route | CH-5 容灾兜底切下一个，售价恒定 | 兜底切供应商态（售价不变） |
| `inFmt == 供应商协议` | e2e_conv-相等 | 直通透传（仅替换 model=B） | 协议直通态 |
| `(Y,B)` 无 ChannelModelCost 行 | e2e_cost-缺失 | `quota_cost=0`、`quota_profit=quota_sell`、`Other` 写 cost_missing | 成本缺失告警态 |
| `quota_cost > quota_sell` | e2e_log | `quota_profit<0` 落库 | 亏损告警态（看板可筛） |

### 5. 数据对象（复用 DATA-MODEL §5 Log + Channel §3 + BILLING-DATA-OBJECTS §1/§2/§5）
- **三段模型落点** `Log`：`RequestedModel(requested_model)=C`（客户可见）、`ResolvedPublicModel(resolved_public_model)=A`（客户可见、定价键）、`ActualUpstreamModel(actual_upstream_model)=B`（**仅 admin/root**，取 L2 后值）；`ModelName(model_name)=C`（兼容现网）；L3 渠道重定向 B' 写 `Other` 的 `{"channel_redirect":"B'"}` 仅诊断。
- **协议落点** `Log`：`InboundProtocol`、`UpstreamProtocol`、`ProtocolConverted`。
- **双价记账** `Log`：`QuotaSell(quota_sell)=BasePriceRatio(A)×GroupRatio×tokens`（=现有 `Quota`，客户可见）、`QuotaCost(quota_cost)=CostRatio(Y,B)×tokens`（不乘折扣，仅 admin/root）、`QuotaProfit(quota_profit)=quota_sell−quota_cost`（仅 admin/root，可负）。
- **路由/成本键** `Channel.Id(id)`（实际选中 Y，第 ⑦ 步成本主键之一）、`Ability(group×model=B→channel)`（复用，映射不掺路由）；成本表 `ChannelModelCost(channel_id, upstream_model=B)→CostRatio`（结算阶段精确取一行）。
- **运行期承载**（内存）`RelayInfo`：`ResolvedPublicModel(A)`、`UpstreamModelName(B,复用)`、`ChannelId`、`UsingGroup`、`QuotaSell/QuotaCost/QuotaProfit`；`PriceData`：`CostRatio/CompletionCostRatio/CostMissing`。

### 6. 验收标准
- [ ] 执行次序固定 L1(C→A) → L2(A→B) → key 减法校验 → 选渠 → 头尾转换 → 双价记账 → 落 Log；选渠维 `model` 用 B（非 C/A）。
- [ ] 客户付费 `quota_sell` 只随对外模型 A + 分组折扣变化，兜底切供应商时售价恒定不波动。
- [ ] 成本 `quota_cost` 随实际选中渠道 × B 走，按 `(ChannelId,B)` 精确取 `ChannelModelCost`；兜底切渠道后取新渠道成本行。
- [ ] `quota_profit = quota_sell − quota_cost` 逐笔落库，可为负（亏损告警）；`(ChannelId,B)` 无成本行→`quota_cost=0`、`quota_profit=quota_sell`、`Other` 写 `cost_missing`。
- [ ] 一条 `Type=2 Consume` Log 同时含 C/A/B 三段 + 实际供应商 channel + 协议三字段 + 售价/成本/利润。
- [ ] A ∉ key `ModelLimits`（开启时）或 `inFmt ∉ EndpointLimits`（开启时）→ 拒绝；默认全开则放行。

### 7. 所触及页面状态（对齐 §J「端到端经营链路」系统内部态）
协议识别态 · 两层映射态（C→A→B）· 恒等透传态（层未命中）· 映射环检测拒绝态 · key 级约束拒绝态（减法命中）· 兜底切供应商态（售价恒定）· 协议直通/转换态 · 双价记账态（quota_sell/cost/profit）· 成本缺失告警态 · 亏损告警态 · Log 落账态（C/A/B+供应商+协议+三金额，终态）。

---

## RL-8 IR 覆盖 OpenAI ⇄ Anthropic 五大差异点（D1–D5 双向往返）

- **功能 ID / 优先级**：F-3062 / P1
- **来源**：COMPAT-LAYER-ARCHITECTURE §2.2（IR 要点、ADR-COMPAT-01）、COMPAT-LAYER-DATA-OBJECTS §4（ChatIR/Message/ContentBlock/ChatRespIR/StreamState 字段级）、API-STATE §错误/停止映射
- **角色 / Owner**：系统（协议适配）；Owner 模块 = Relay 网关（`relay/compat/ir`）
- **触发**：RL-6 在 `inFmt != targetProto` 时把入站报文经 IR 转出站、把上游响应经 IR 转回客户

### 1. 场景
首版 IR 只强建模 OpenAI ⇄ Anthropic 的**五个差异点**（ADR-COMPAT-01），其余字段走 `PassthroughExtras` 旁路透传降低首版工作量：
- **D1 system 位置**：OpenAI 来自 `messages[role=system]`，Anthropic 来自顶层 `system` 字段 → IR 统一收进 `ChatIR.System []ContentBlock`，序列化反向还原到各协议对应位置。
- **D2 content 结构**：OpenAI `content` 可为字符串或数组，Anthropic 恒为 block 数组 → IR 统一为 `[]ContentBlock`，OpenAI 序列化时纯单 text 退化回字符串。
- **D3 tools**：OpenAI `tools[].function{name,parameters}` ⇄ Anthropic `tools[]{name,input_schema}`；assistant `tool_calls`/`role=tool` 消息 ⇄ Anthropic `tool_use`/`tool_result` content block，双向往返。
- **D4 stop_reason**：IR 枚举 `end_turn/max_tokens/stop_sequence/tool_use` 双向映射 OpenAI `finish_reason(stop/length/tool_calls)` ⇄ Anthropic `stop_reason(end_turn/max_tokens/stop_sequence/tool_use)`。
- **D5 usage 字段名**：OpenAI `usage.prompt_tokens/completion_tokens` ⇄ Anthropic `usage.input_tokens/output_tokens` → IR 统一 `UsageIR{PromptTokens, CompletionTokens, TotalTokens}`（计费 token 口径统一，供 RL-7 双价记账）。

流式（SSE）经 `StreamState` 保存 message 开闭状态 / content block index / 累计 usage，把 Anthropic 多 event（`message_start`/`content_block_delta`/`message_delta`/`message_stop`）与 OpenAI `data:{choices:[{delta}]}`+`[DONE]` 互转（`SerializeStreamChunk` 可能 1→N）。

### 2. 前置条件
- RL-6 已判定需转换（`inFmt != targetProto`）。
- IR 为内存运行期结构，不落库。
- 五差异点之外的私有参数走 `PassthroughExtras map[string]json.RawMessage` 旁路。

### 3. 主流程（对应 RL-8 节点 ir_in→ir_out）
1. 入站报文进 `ParseRequest`（ir_in）→ 装填 IR：D1 system 归位 `ChatIR.System`（ir_d1）、D2 content 统一 block 数组（ir_d2）、D3 tools 归一（ir_d3）。
2. 目标协议 `SerializeRequest` 反向还原 D1/D2/D3 到目标协议形态（ir_ser）→调上游。
3. 上游响应 `ParseResponse`（ir_resp）→ D4 stop_reason 映射进 IR 枚举（ir_d4）、D5 usage 字段名归一 `UsageIR`（ir_d5）。
4. 按入站协议 `SerializeResponse` 反向映射 D4/D5（ir_rser）；流式经 `StreamState` 逐 chunk 互转（ir_stream，可能 1→N event）→统一往返完成态（ir_out）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| OpenAI system 在 messages / Anthropic 在顶层 | ir_d1 | 统一收进 `ChatIR.System`，序列化反向还原 | D1 system 归位态 |
| OpenAI content 为字符串 | ir_d2 | IR 统一 block，OpenAI 序列化退化回字符串 | D2 content 统一态 |
| 含 tools / tool_calls | ir_d3 | function ⇄ tool_use/tool_result 往返 | D3 tools 往返态 |
| 上游回 finish_reason / stop_reason | ir_d4 | IR 枚举双向映射 | D4 stop_reason 映射态 |
| usage 字段名差异 | ir_d5 | prompt/completion ⇄ input/output 归一 | D5 usage 归一态 |
| 五点外私有参数 | ir_in | `PassthroughExtras` 旁路透传 | 旁路透传态 |
| 流式响应 | ir_stream | StreamState 逐 chunk 互转，1→N event | 流式 IR 往返态 |

### 5. 数据对象（复用 COMPAT-LAYER-DATA-OBJECTS §4 IR，内存不落库）
- **请求 IR** `ChatIR{ Model(=B), System []ContentBlock(D1), Messages []Message, Tools []Tool(D3), ToolChoice, Stream, MaxTokens, Temperature/TopP, StopSequences, Metadata, PassthroughExtras }`。
- **消息/内容** `Message{ Role, Content []ContentBlock }`、`ContentBlock{ Type(text/image/tool_use/tool_result), Text, ImageSource, ToolUse, ToolResult }`（D2）。
- **响应 IR** `ChatRespIR{ Id, Model, Role, Content []ContentBlock, StopReason(D4 IR 枚举), Usage UsageIR{PromptTokens,CompletionTokens,TotalTokens}(D5) }`；流式 `ChatDeltaIR` + `StreamState`（message 开闭 / block index / 累计 usage）。
- **Log 衔接**：`UsageIR` 的 token 经 RL-7 用于 `quota_sell/quota_cost` 计费口径统一；`protocol_converted=true` 标记本笔走了 IR 转换。

### 6. 验收标准
- [ ] D1：OpenAI `messages[role=system]` 与 Anthropic 顶层 `system` 互转后语义一致，序列化各归原位。
- [ ] D2：OpenAI 字符串 content 与 Anthropic block 数组互转无丢失；纯单 text 序列化回 OpenAI 退化为字符串。
- [ ] D3：tools 定义与 tool_use/tool_result 调用在两协议间往返，function ⇄ tool_use 对应正确。
- [ ] D4：`finish_reason(stop/length/tool_calls)` ⇄ `stop_reason(end_turn/max_tokens/stop_sequence/tool_use)` 双向映射正确。
- [ ] D5：`usage.prompt_tokens/completion_tokens` ⇄ `usage.input_tokens/output_tokens` 归一，计费 token 口径不偏。
- [ ] 流式：Anthropic 多 event 与 OpenAI delta+`[DONE]` 经 StreamState 互转，`SerializeStreamChunk` 支持 1→N。
- [ ] 五差异点之外的私有参数经 `PassthroughExtras` 透传不丢。

### 7. 所触及页面状态（对齐 §J「IR 五差异点」系统内部态）
D1 system 归位态 · D2 content 统一态 · D3 tools 往返态 · D4 stop_reason 映射态 · D5 usage 归一态 · 旁路透传态（PassthroughExtras）· 流式 IR 往返态（StreamState 1→N）· 统一往返完成态（终态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-3026 | RL-1 |
| F-3037 | RL-1 / RL-3 |
| F-3027 | RL-2 |
| F-3028 | RL-2 |
| F-3029 | RL-2 |
| F-3030 | RL-2 |
| F-3031 | RL-2 |
| F-3032 | RL-2 |
| F-3033 | RL-2 |
| F-3034 | RL-2 / RL-4 |
| F-3035 | RL-2 / RL-4 |
| F-3057 | RL-2 |
| F-3036 | RL-4 |
| F-4046 | RL-5 |
| F-3060 | RL-6 |
| F-3061 | RL-7 |
| F-3062 | RL-8 |

无 `[BLOCKER]`。
