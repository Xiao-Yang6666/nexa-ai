# FL-relay — Relay 网关多协议中转（D11/D13）流程图

> 分片：Relay 网关（F-3026~F-3037、F-3057）、视频内容代理（F-4046）。
> 角色：登录用户/外部客户端（携 token key）/ 系统（鉴权·选渠·转发·计费·禁用）/ 各厂商上游。
> 跨切面契约见 `../OVERALL-FLOW.md §3`：C1 鉴权（TokenAuth）、C5 限流（ModelRequestRateLimit）、Distribute 中间件。Relay 与计费的衔接见 `FL-billing.md BL-2`（预扣/结算），与选渠见 `FL-channel.md CH-2/CH-5`，本文件只标注「调 BL-2 预扣」「调 CH-2 选渠」不重画。
> 后端：`controller/relay.go`、`router/relay-router.go`、`relay/relay_mode.go`、`relay/channel/*`、`controller/video_proxy.go`。关键：`ShouldRetryByStatusCode`、`RelayFormat*`、`RetryTimes`。

---

## 场景 RL-1 · OpenAI 兼容端点主链路（鉴权→额度→选渠→转发→计费→记日志）（F-3026/F-3037）

> 业务规则：`POST /v1/chat/completions` 经中间件链 `TokenAuth → ModelRequestRateLimit → Distribute` 进入 Relay：鉴权失败拒绝、限流超限拒绝；转发前调 BL-2 预扣（userQuota<=0 → 403 SkipRetry）；调 CH-2 选满足渠道；转发上游后按真实 token 结算（多退少不补）并落 Log + UseData。本图为请求-响应时序，刻意用 sequenceDiagram 表达「客户端↔网关↔计费↔渠道↔上游」多参与方交互，区别于其余 flowchart。

```mermaid
sequenceDiagram
  participant C as 客户端
  participant G as Relay 网关
  participant A as 鉴权/限流
  participant B as 计费(BL-2)
  participant S as 选渠引擎(CH-2)
  participant U as 上游厂商
  C->>G: POST /v1/chat/completions (Bearer sk-)
  G->>A: TokenAuth + ModelRequestRateLimit
  A-->>G: 鉴权失败/限流超限 → 拒绝
  A-->>G: 通过 (Distribute 注入分组/模型)
  G->>B: PreConsumeQuota 预扣
  B-->>G: userQuota<=0 → 403 SkipRetry
  B-->>G: 冻结 FinalPreConsumedQuota
  G->>S: GetRandomSatisfiedChannel
  S-->>G: 无可用渠道 → 上抛错误
  S-->>G: 选定渠道
  G->>U: 转发请求 (流式/非流式)
  U-->>G: 响应 + usage(真实 token)
  G->>B: 按真实 token 结算 (多退少不补)
  B-->>G: PostConsumeQuota 落 Log+UseData
  G-->>C: 回传响应
```

屏幕状态清单（RL-1 OpenAI 主链路，外部 API + 内部态）：
- 鉴权失败拒绝态（TokenAuth） ← 异常
- 限流超限拒绝态（ModelRequestRateLimit） ← 异常
- 预扣 403 态（userQuota<=0，SkipRetry，复用 BL-2） ← 异常
- 无可用渠道态（选渠失败，复用 CH-2） ← 异常
- 额度冻结态（FinalPreConsumedQuota）
- 转发中态（流式/非流式）
- 结算完成态（真实 token 多退少不补，Log+UseData 入账） ← 终态

---

## 场景 RL-2 · 端点路径识别与多协议格式分发（Path2RelayMode 前缀顺序）（F-3027~F-3036/F-3057）

> 业务规则：`Path2RelayMode` 按路径定 `RelayMode` 与 `RelayFormat`——`/v1/responses/compact` **必须先于** `/v1/responses` 匹配（前缀顺序错会误判）；`/v1/embeddings` 及任意 `HasSuffix(embeddings)` 判 Embeddings；`/images/variations` 走 `RelayNotImplemented`；`/v1/edits` 是独立 `RelayModeEdits`（区别于 `/images/edits`）；`/v1/messages` 走 Claude、`/v1beta/models/*` 走 Gemini、`/v1/realtime` 走 WebSocket 升级。本图为路径分发的有序匹配树，节点对应真实路由判定。

```mermaid
flowchart TD
  rd_in([请求进入 relay-router]) --> rd_p1{路径 = /v1/responses/compact?}
  rd_p1 -->|是 先匹配| rd_compact[RelayFormatOpenAIResponsesCompaction]
  rd_p1 -->|否| rd_p2{路径 = /v1/responses?}
  rd_p2 -->|是| rd_resp[RelayFormatOpenAIResponses]
  rd_p2 -->|否| rd_p3{路径 = /v1/messages?}
  rd_p3 -->|是| rd_claude[RelayFormatClaude 原生]
  rd_p3 -->|否| rd_p4{路径 前缀 /v1beta/models?}
  rd_p4 -->|是| rd_gemini[RelayFormatGemini 原生]
  rd_p4 -->|否| rd_p5{路径 = /v1/realtime?}
  rd_p5 -->|是| rd_ws[RelayFormatOpenAIRealtime · WebSocket 升级]
  rd_p5 -->|否| rd_p6{HasSuffix embeddings?}
  rd_p6 -->|是| rd_emb[RelayFormatEmbedding]
  rd_p6 -->|否| rd_p7{路径 = /v1/images/variations?}
  rd_p7 -->|是| rd_ni[RelayNotImplemented 未实现]:::err
  rd_p7 -->|否| rd_p8{路径 = /v1/edits?}
  rd_p8 -->|是| rd_edits[RelayModeEdits 独立 legacy]
  rd_p8 -->|否| rd_def[默认 RelayFormatOpenAI chat/completions]
  rd_compact --> rd_go([分发到对应 adapter 态]):::term
  rd_resp --> rd_go
  rd_claude --> rd_go
  rd_gemini --> rd_go
  rd_ws --> rd_go
  rd_emb --> rd_go
  rd_edits --> rd_go
  rd_def --> rd_go
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（RL-2 协议分发，系统内部态）：
- compact 优先匹配态（先于 /v1/responses）
- responses / claude / gemini / realtime 分发态
- embeddings 后缀匹配态
- variations 未实现态（RelayNotImplemented） ← 异常
- edits 独立 legacy 态（区别 images/edits）
- 默认 chat/completions 态
- 分发到 adapter 态 ← 终态

---

## 场景 RL-3 · 上游错误处理与按状态码重试/禁用渠道（F-3037）

> 业务规则：上游返回错误码时 `ShouldRetryByStatusCode(code)` 决定重试还是跳过；可重试且未达 `RetryTimes` 则换渠道重试（复用 CH-2/CH-5），不可重试直接返回；满足条件且渠道 `AutoBan=1` 时 `DisableChannel`（复用 CH-3）；错误日志经 `MaskSensitiveErrorWithStatusCode` 脱敏后 `RecordErrorLog`（记 channel/model/status_code）；错误响应按 `RelayFormat`（OpenAI/Claude/Gemini）分别构造。本图为「重试判定 ⊕ 禁用判定 ⊕ 脱敏记录」三条并行处置汇聚到格式化返回。

```mermaid
flowchart TD
  re_err([上游返回错误状态码]) --> re_retry{ShouldRetryByStatusCode?}
  re_retry -->|可重试| re_count{重试次数 < RetryTimes?}
  re_count -->|是| re_again[换渠道重试 复用 CH-2/CH-5]:::norm
  re_count -->|否 用尽| re_giveup[重试耗尽 → 终态返回错误]:::err
  re_retry -->|不可重试| re_stop[跳过重试]
  re_again --> re_ban
  re_stop --> re_ban{渠道 AutoBan=1 且命中禁用条件?}
  re_ban -->|是| re_disable[DisableChannel 复用 CH-3 · 通知root]
  re_ban -->|否| re_keep[渠道保持状态]
  re_disable --> re_log
  re_keep --> re_log[MaskSensitiveErrorWithStatusCode 脱敏 → RecordErrorLog]
  re_log --> re_fmt{按 RelayFormat 构造错误?}
  re_fmt -->|OpenAI| re_oai[OpenAI 错误结构]
  re_fmt -->|Claude| re_cla[Claude 错误结构 StatusCode]
  re_fmt -->|Gemini| re_gem[Gemini 错误结构]
  re_oai --> re_ret([错误响应返回态]):::term
  re_cla --> re_ret
  re_gem --> re_ret
  re_giveup --> re_ret
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
  classDef norm fill:#eef2ff,stroke:#4f46e5
```

屏幕状态清单（RL-3 错误处理/重试，系统内部态）：
- 可重试换渠道态（复用 CH-2/CH-5）
- 重试耗尽态（>=RetryTimes，返错） ← 异常
- 跳过重试态（不可重试码）
- 渠道自动禁用态（AutoBan 命中，复用 CH-3，通知 root）
- 渠道保持态
- 错误脱敏记录态（MaskSensitive，记 channel/model/status_code）
- OpenAI/Claude/Gemini 格式化错误态
- 错误响应返回态 ← 终态

---

## 场景 RL-4 · 厂商原生协议适配（37 adapter 请求/响应双向转换）（F-3034/F-3035/F-3036）

> 业务规则：请求分发到渠道类型对应 adapter（`relay/channel/` 下 37 个，渠道类型常量至 57），由 adapter 将**统一入参**转为厂商原生协议、再将厂商响应解析回统一格式；adapter 缺失则该渠道类型无法中转；新增渠道按 AGENTS Rule4 决定是否加入 `streamSupportedChannels`（不支持流式则降级非流）。本图为「入站统一格式 → 适配转换 → 上游 → 反向解析 → 出站统一格式」的双向管线，中段含流式能力分支。

```mermaid
flowchart LR
  ad_in([统一格式请求 + 选定渠道类型]) --> ad_find{该类型 adapter 存在?}
  ad_find -->|否| ad_miss[adapter 缺失 → 该渠道无法中转]:::err
  ad_find -->|是| ad_conv[adapter 转为厂商原生协议]
  ad_conv --> ad_stream{请求要流式 且 在 streamSupportedChannels?}
  ad_stream -->|否 不支持流式| ad_block[降级为非流式调用]
  ad_stream -->|是| ad_sse[以 SSE 流式调用上游]
  ad_block --> ad_call[发往厂商上游]
  ad_sse --> ad_call
  ad_call --> ad_resp{上游响应解析成功?}
  ad_resp -->|解析失败| ad_perr[响应解析错误 → 交 RL-3 处置]:::err
  ad_resp -->|成功| ad_back[adapter 反解析回统一格式]
  ad_back --> ad_ok([统一格式响应出站态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（RL-4 厂商适配，系统内部态）：
- adapter 缺失态（该渠道类型无法中转） ← 异常
- 协议转换态（统一→厂商原生）
- 非流式降级态（不在 streamSupportedChannels）
- 流式 SSE 态（支持流式）
- 响应解析失败态（交 RL-3） ← 异常
- 反向解析态（厂商→统一格式）
- 统一格式响应出站态 ← 终态

---

## 场景 RL-5 · 视频内容网关代理（本人校验 + SSRF + 流式回传）（F-4046）

> 业务规则：`GET /videos/:task_id/content` 经 `VideoProxy`——校验 task **属本人**（`GetByTaskId` 带 userID）且 `Status==TaskStatusSuccess`；按 `channel.Type` 分支解析上游 URL（Gemini 用 `task.PrivateData.Key + x-goog-api-key`、Vertex、OpenAI/Sora 拼 `/v1/videos/.../content`）；`data:` URL 走 base64 解码直出；其余 URL 经 `ValidateURLWithFetchSetting` SSRF 校验后 `io.Copy` 流式回写，响应设 `Cache-Control max-age=86400`；任务非本人/不存在 → 404，未完成 → 当前状态错误，SSRF 不过 → 403。本图为「归属校验 → 状态校验 → 类型分支取 URL → SSRF → 回传」的代理下发链。

```mermaid
flowchart TD
  vp_in([GET /videos/:task_id/content]) --> vp_own{task 属本人 且 存在?}
  vp_own -->|否| vp_404[404 任务不存在/非本人]:::err
  vp_own -->|是| vp_done{Status = TaskStatusSuccess?}
  vp_done -->|否| vp_state[返回当前任务状态错误 未完成]:::err
  vp_done -->|是| vp_type{按 channel.Type 解析 URL?}
  vp_type -->|Gemini| vp_g[用 PrivateData.Key + x-goog-api-key]
  vp_type -->|Vertex| vp_v[Vertex 分支取地址]
  vp_type -->|OpenAI/Sora| vp_o[拼 /v1/videos/.../content]
  vp_g --> vp_scheme{URL 是 data: 协议?}
  vp_v --> vp_scheme
  vp_o --> vp_scheme
  vp_scheme -->|是| vp_b64[base64 解码直出]
  vp_scheme -->|否 http| vp_ssrf{ValidateURLWithFetchSetting 通过?}
  vp_ssrf -->|否| vp_403[403 SSRF 校验未通过]:::err
  vp_ssrf -->|是| vp_copy[io.Copy 流式回写 · Cache-Control max-age=86400]
  vp_b64 --> vp_ok([视频内容下发态]):::term
  vp_copy --> vp_ok
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（RL-5 视频内容代理）：
- 404 态（任务不存在/非本人） ← 异常
- 任务未完成态（Status≠Success，返回当前状态） ← 异常
- 渠道类型分支态（Gemini/Vertex/OpenAI·Sora 取 URL）
- data: 直出态（base64 解码）
- SSRF 拦截态（403，校验未通过） ← 异常
- 流式回写态（io.Copy，Cache-Control max-age=86400）
- 视频内容下发态 ← 终态

---

## 场景 RL-6 · 端到端经营转发链路（协议识别→两层映射→减法约束→选渠容灾→头尾转换→双价记账→落 Log）（兼容层 / 经营链路）

> 业务规则（唯一权威 = `../COMPAT-BILLING-DECISIONS.md §10` 完整一笔请求链路 ①~⑨，对齐 prd-relay RL-7）：客户用 OpenAI 或 Anthropic 格式发 `model=C`，一笔请求按固定链路串行编排——① **协议识别** 定 `inFmt`（复用 RL-2）；② **两层模型映射** `C →(客户层 L1 UserModelAlias，user>group)→ A →(超管层 L2 PlatformModelMapping)→ B`，B 客户永不可见、带环检测+最大跳数（明细见 `FL-model.md ML-6`）；③ **key 级减法约束** 校验 A/端点是否在该 key 的允许范围内（**默认全开放行**，纯减法自我约束非权限闸门）；④ **渠道池路由** 以 B 为键按优先级/权重选一供应商，挂了按 CH-5 容灾兜底切下一个（**售价恒定不随兜底波动**）；⑤ **协议头尾对比** `inFmt vs 选中供应商协议` → 相等直通、不等转换（复用 RL-4/RL-6 协议适配，全链唯一一次）；⑥ **扣客户** = 对外模型 A 基准售价 × 分组折扣系数（`quota_sell`，客户可见）；⑦ **记成本** = 实际选中渠道 × 真实模型 B 的成本倍率（`cost`，明细见 `FL-billing.md BL-6`），成本缺失记 0 + 告警；⑧ **响应** 按 `inFmt` 转回客户；⑨ **落 Log** = C/A/B + 实际供应商 + 协议转换标记 + 售价/成本/利润（`DATA-MODEL §5` 已落 10 字段）。本图为「识别→映射→约束→选渠→转换→双价→落账」的端到端经营编排主链，判定菱形含「协议是否相等 / 渠道是否可用 / 成本是否缺失」。

```mermaid
flowchart TD
  e2_in([客户发 model=C · OpenAI 或 Anthropic 格式]) --> e2_proto[① 协议识别 定 inFmt 复用 RL-2]
  e2_proto --> e2_map[② 两层映射 C→A→B 复用 FL-model ML-6]
  e2_map --> e2_loop{映射成环/超最大跳数?}
  e2_loop -->|是| e2_eloop[映射环/超跳 → 终态返回错误]:::err
  e2_loop -->|否 得 A 与 B| e2_lim{③ key 级减法约束 · A 与端点在允许范围?}
  e2_lim -->|否 减法命中| e2_deny[403 key 自我约束拒绝 SkipRetry]:::err
  e2_lim -->|是/默认全开放行| e2_route[④ 选渠 以 B 为键查 Ability 按优先级/权重 复用 CH-2]
  e2_route --> e2_avail{渠道池有可用供应商?}
  e2_avail -->|否 全挂| e2_none[无可用渠道 → 上抛错误]:::err
  e2_avail -->|挂了 兜底切下一个| e2_route
  e2_avail -->|是 选中供应商 Y| e2_cmp{⑤ 头尾对比 inFmt == Y供应商协议?}
  e2_cmp -->|相等| e2_pass[直通 仅替换 model 为 B 复用 RL-4]
  e2_cmp -->|不等| e2_conv[协议转换 inFmt⇄Y 唯一一次 复用 RL-4/RL-6]
  e2_pass --> e2_call[转发上游 Y 调用真实模型 B]
  e2_conv --> e2_call
  e2_call --> e2_sell[⑥ 扣客户 quota_sell = A基准售价 × 分组折扣 售价恒定]
  e2_sell --> e2_cost{⑦ 记成本 渠道Y×B 成本倍率存在?}
  e2_cost -->|缺失| e2_cost0[cost=0 + 告警 复用 BL-6]:::err
  e2_cost -->|命中| e2_costok[cost = ChannelModelCost CostRatio]
  e2_cost0 --> e2_resp
  e2_costok --> e2_resp[⑧ 响应按 inFmt 转回客户]
  e2_resp --> e2_log[⑨ 落 Log · C/A/B + 供应商Y + 协议转换标记 + 售价/成本/利润]
  e2_log --> e2_ok([端到端经营落账态 · profit = quota_sell − cost]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（RL-6 端到端经营链路，系统内部态）：
- 协议识别态（定 inFmt，复用 RL-2）
- 两层映射态（C→A→B，B 客户不可见，复用 FL-model ML-6）
- 映射环/超跳拒绝态（环检测+最大跳数） ← 异常
- key 级减法约束拒绝态（减法命中，默认全开放行） ← 异常
- 兜底切供应商态（挂了切下一个，售价恒定）
- 无可用渠道态（全挂上抛，复用 CH-2/CH-5） ← 异常
- 协议直通态（inFmt==供应商协议）/ 协议转换态（不等，唯一一次，复用 RL-4/RL-6）
- 售价扣费态（quota_sell = A基准价 × 分组折扣，客户可见恒定）
- 成本缺失告警态（cost=0 + 告警，复用 BL-6） ← 异常
- 成本记账态（渠道Y×B 成本倍率）
- 响应转回 inFmt 态
- Log 落账态（C/A/B + 供应商 + 协议转换标记 + 售价/成本/利润，profit=售价−成本） ← 终态
