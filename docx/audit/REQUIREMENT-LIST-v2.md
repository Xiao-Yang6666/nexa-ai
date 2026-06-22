# REQUIREMENT-LIST-v2 — Nexa AI 平台新一轮可执行需求清单

> 来源：设计↔代码一致性审计（见同目录 `AUDIT-REPORT.md`）。设计为基准，代码为被审对象。
> 用途：供多个 Claude Code worker 并行/串行开发。每条可独立分发，依赖已标注。
> 设计基准锚点：`product-factory/07_dev_contract/final/openapi.yaml`、`03_flow_prd/final/prd/{prd-relay,prd-model,prd-billing,prd-channel}.md`、`reviews/BILLING-MODEL-ARCHITECTURE.md`、`DATA-MODEL.md §5/§16-§19`。
> 代码基准：`/root/nexa-app/backend`（分支 `feat/close-mgmt-loops-and-routing`，编译通过）。
> 优先级：P0=链路命脉（不通则整个产品空转）/ P1=重要（命脉通了的下一层）/ P2=增强（叶子）。
> 审计日期：2026-06-22。

---

## 优先级总览与开发顺序

**命脉一句话**：先把转发主干串起来（REQ-01→02→03→04→05），让一笔 OpenAI 非流式请求能真正打到上游并双价记账落 Log；这条通了，"apikey/组/模型不搭嘎" 即消失。

### P0 命脉（6 条）— 按依赖严格排序
```
REQ-01 (HTTP client 基建)  ─┐
                            ├─→ REQ-03 (选渠接线) ─┐
REQ-02 (转发主干编排骨架) ──┘                      ├─→ REQ-05 (双价计费+落Log接线) → 命脉闭环✅
                            └─→ REQ-04 (OpenAI协议转换接线 + 调上游) ┘
REQ-06 (key 减法校验接线) ── 并入主干，依赖 REQ-02
```
**串行主链**：REQ-01 → REQ-02 → {REQ-03 ∥ REQ-04} → REQ-05。REQ-06 依赖 REQ-02，可与 REQ-03/04 并行。

### P1 重要（6 条）
REQ-07(Claude协议) · REQ-08(流式SSE) · REQ-09(RL-3错误重试/禁渠) · REQ-10(/v1/models真数据) · REQ-11(RL-5视频代理) · REQ-12(利润看板聚合查询)

### P2 增强（5 条）
REQ-13(Group注册中心/DESIGN-FIX) · REQ-14(embeddings/responses/edits端点真转发) · REQ-15(Gemini协议预留位) · REQ-16(stub甄别清理) · REQ-17(allow_ips/IP限流接线)

---

# P0 — 链路命脉

## [REQ-01] Relay 出站 HTTP Client 基础设施（调上游的物理通道）
- **类型**：MISSING
- **归属模块**：`relay/infrastructure`（新增 `relay/infrastructure/http` 或 `relay/infrastructure/upstream`）
- **设计依据**：`prd-relay.md RL-1 §3.5`（"网关向上游转发"）、`RL-4`（adapter 转厂商原生协议调上游 `Channel.Key+BaseURL`）、`BILLING-MODEL-ARCHITECTURE.md §6` 第12步 Outbound 调上游、architect ref「协议转换只看头尾」。
- **当前状态**：relay/channel/billing/routing **四个域零 HTTP client**（`grep WebClient|RestClient|RestTemplate|HttpClient` 仅命中 oauth/ops/deployment 域）。relay 无任何发起上游请求的能力。
- **目标（验收标准）**：
  - 新增一个 relay 出站 HTTP 端口（domain port）+ 基础设施实现（推荐 Spring `RestClient` 或 `WebClient`；与现网 oauth 域选型保持一致）。
  - 支持：按 `Channel.BaseURL + path` 拼接、注入 `Channel.Key` 鉴权头、透传/改写请求体 byte[]、非流式返回上游响应（status+headers+body）、流式返回响应体 stream（为 REQ-08 预留）。
  - 含超时、连接复用配置。可写一个集成测试用 MockWebServer/WireMock 打桩验证非流式 round-trip。
  - **安全**：上游 Key 不得落日志；SSRF 由 BaseURL 白名单/校验防护（与 RL-5 `ValidateURLWithFetchSetting` 同源思路）。
- **优先级**：P0
- **依赖**：无（最底层基建，最先做）
- **可并行**：是（无前置，独立 worker 可立即开工）

## [REQ-02] 转发主干编排骨架接线（RelayForwardUseCase 串起 RL-7 20 步）
- **类型**：DIVERGED（主干孤岛 → 接线）
- **归属模块**：`relay/application`（`RelayForwardUseCase`）+ `relay/interfaces/api`（`RelayController.forwardRelay`）
- **设计依据**：`prd-relay.md RL-1 / RL-7`（端到端编排 ①~⑨）、`BILLING-MODEL-ARCHITECTURE.md §6`（20 步精确时序，每步读哪张表/写什么 ctx/算什么）。
- **当前状态**：`RelayForwardUseCase.java:27-33` 构造器只注入 `l2Repo/l1Repo/logRepo`，仅有 `resolveDispatch()`+`resolveModel()` 两个真方法，**无 forward 方法**。`RelayController.java:154-162` `forwardRelay()` 一律返 501。
- **目标（验收标准）**：
  - `RelayForwardUseCase` 新增 `forward(path, body, authContext)` 方法，按 RL-7 顺序编排：① 协议识别（已有 `resolveDispatch`）→ ② C→A→B 映射（已有 `resolveModel`）→ ③ key 减法校验（REQ-06）→ ④ 选渠（REQ-03）→ ⑤ 协议转换（REQ-04）→ 调上游（REQ-01）→ ⑥⑦ 双价记账（REQ-05）→ ⑨ 落 Log（REQ-05）。
  - 构造器注入：`ResolveChannelRouteUseCase`、`ChannelModelCostRepository`、`DualPriceBilling`、relay HTTP 端口（REQ-01）、`ProtocolRegistry`、预扣/结算所需 billing 端口。
  - 本条**先把骨架接通到「OpenAI 非流式、单渠道、不重试」最小可用路径**（complex 分支留给 REQ-03/09），让 `POST /v1/chat/completions` 真实返回上游响应而非 501。
  - 验收：`curl POST /v1/chat/completions`（带合法 token + 已配 Ability+ChannelModelCost 的模型）→ 返回真实上游 JSON（非 501），且 `RelayInfo` 内 C/A/B 三段、ChannelId、UsingGroup 均被填充。
- **优先级**：P0
- **依赖**：REQ-01（需要 HTTP client 才能真调上游；可先接桩 client 并行开发，REQ-01 完成后替换）
- **可并行**：与 REQ-01 弱并行（接口先行）；REQ-03/04/05/06 都挂在它上面，是命脉总枢纽

## [REQ-03] 选渠子系统接入转发主干（group×B → Ability 加权随机 + CH-4 亲和 + CH-5 重试）
- **类型**：DIVERGED（真实组件孤岛 → 接线）
- **归属模块**：`routing/application`（`ResolveChannelRouteUseCase`）↔ `relay/application`（消费）
- **设计依据**：`prd-relay.md RL-7 §3.4`（按 `(Token.Group/User.Group)×B` 查 Ability 选渠，CH-5 容灾兜底）、`prd-channel.md CH-2/CH-4/CH-5`、`BILLING-MODEL-ARCHITECTURE.md §6` 第7步 Distribute 选渠。
- **当前状态**：`ResolveChannelRouteUseCase.java:38`（`@Service`，真实，CH-4 亲和+CH-5 重试，有单测）+ `AbilityBackedChannelSelectionAdapter`（真实，Ability group×B 加权随机）**无任何生产调用方**（`grep` 仅 javadoc+测试引用）。即审计指出的「选渠真组件是孤岛」。
- **目标（验收标准）**：
  - `RelayForwardUseCase.forward()` 在两层映射得到 B 后，调 `ResolveChannelRouteUseCase` 用 `(UsingGroup, B)` 选出渠道，写 `RelayInfo.ChannelId/Channel.Type`。
  - 确认生产注入的是 `AbilityBackedChannelSelectionAdapter`（非 `StubChannelSelectionAdapter`）。
  - 选渠失败（无可用渠道）→ 上抛「无可用渠道」错误（对齐 RL-1 §4），不转发。
  - CH-5 容灾：选中渠道挂掉时按重试调度切下一个，售价恒定（成本随新渠道，见 REQ-05）。
  - 验收：构造「同一 group×B 挂两个渠道」场景，curl 多次命中按权重分布；禁用其一后请求自动落到另一个；无渠道时返回明确错误码。
- **优先级**：P0
- **依赖**：REQ-02（挂在主干编排上）
- **可并行**：与 REQ-04、REQ-06 并行（互不相干的环节）

## [REQ-04] OpenAI 协议转换接入主干 + 头尾 passthrough 决策（调用 ProtocolAdapter 出站）
- **类型**：DIVERGED（非流式真实但主干不调）
- **归属模块**：`relay/infrastructure/protocol`（`OpenAiProtocolAdapter`）↔ `relay/application`
- **设计依据**：`prd-relay.md RL-6`（头尾对比，inFmt==targetProto 直通 / 不等走 IR 转换，唯一一次）、`RL-8 D1-D5`（非流式）、`BILLING-MODEL-ARCHITECTURE.md §6` 第10/12/14步。
- **当前状态**：`OpenAiProtocolAdapter` 非流式 `parseRequest/serializeRequest/parseResponse/serializeResponse` 真实；流式 `parseStreamChunk/serializeStreamChunk` 返 `List.of()`（`OpenAiProtocolAdapter.java:196-206`，留 REQ-08）。主干未调用任何 adapter。
- **目标（验收标准）**：
  - 主干在选渠后做头尾决策：`inFmt == protocolOf(Channel.Type)` → `passthrough=true`（仅替换 model 为 B 透传 body）；不等 → 经 `ProtocolRegistry.get(inFmt).parseRequest → IR → targetAdapter.serializeRequest` 转换出站。
  - 上游响应反向：passthrough 直通 / 否则 `targetAdapter.parseResponse → IR → inAdapter.serializeResponse` 回客户。
  - 本条只覆盖**非流式 OpenAI⇄OpenAI（passthrough）与 OpenAI 入站**；Claude 出站依赖 REQ-07，流式依赖 REQ-08。
  - 写 `Log.inbound_protocol/upstream_protocol/protocol_converted` 三字段。
  - 验收：OpenAI 入站打到 OpenAI 上游 → `protocol_converted=false` 直通；响应正确回传；IR usage（D5）归一供计费。
- **优先级**：P0
- **依赖**：REQ-02、REQ-01
- **可并行**：与 REQ-03、REQ-06 并行

## [REQ-05] 双价记账接入结算 + 落 Log（quota_sell/quota_cost/quota_profit + C/A/B）
- **类型**：DIVERGED（DualPriceBilling 真实但零调用方）
- **归属模块**：`relay/domain/service`（`DualPriceBilling`）+ `billing`（预扣/结算）+ `channel`（`ChannelModelCostRepository`）+ `relay/domain/model`（`RelayLog`）
- **设计依据**：`prd-relay.md RL-7 ⑥⑦⑨`、`prd-billing.md BL-7/BL-8/BL-9`、`BILLING-MODEL-ARCHITECTURE.md §5/§6`（第8/16-20步，ADR-BILL-02 成本不乘 GroupRatio）、`DATA-MODEL.md §5`（Log 三金额字段）/`§19`（ChannelModelCost）。
- **当前状态**：`DualPriceBilling`（双价计算真实）**零调用方**（`grep src/main` 无引用）。`ChannelModelCostRepository` 存在。转发链无预扣/结算/落 Log 路径（审计事实 5：字段在无人读）。
- **目标（验收标准）**：
  - 主干结算阶段调 `DualPriceBilling`：售价 `quota_sell = BasePriceRatio(A) × GetGroupRatio(UsingGroup) × tokens`（用 A + group_ratio，恒定）；成本 `quota_cost = CostRatio(实际ChannelId, B) × tokens`（不乘 GroupRatio）；`quota_profit = quota_sell − quota_cost`。
  - 成本行缺失 `(ChannelId,B)` → `quota_cost=0`、`quota_profit=quota_sell`、`Other` 写 `cost_missing`（不阻断，对齐 RL-7 §4）。
  - 预扣（选渠后）+ 结算（响应后多退少补）按 RL-1 接 billing 域预扣/结算端口。
  - 落一条 `Log Type=2 Consume`：含 C/A/B 三段 + 协议三字段 + channel_id + quota_sell/cost/profit。客户视图 DTO 不含 B/成本/利润（三道闸序列化层）。
  - 兜底切渠后 ChannelId 变 → 自动取新渠道成本行（售价不变）。
  - 验收：curl 一笔请求 → DB `logs` 新增一行三段模型+三金额齐全；admin 查 Log 见全链，user 查 Log 仅见 C/A+quota_sell。
- **优先级**：P0
- **依赖**：REQ-02、REQ-03（需 ChannelId）、REQ-04（需 IR usage token 口径）
- **可并行**：否（命脉收口，需 03/04 产出）

## [REQ-06] key 级减法校验接入主干（ModelLimits 对 A + EndpointLimits 对 inFmt，默认全开）
- **类型**：DIVERGED（字段在，转发链无人读）
- **归属模块**：`relay/application` + `token/domain`
- **设计依据**：`prd-model.md ML-8 §3`（key 级减法约束）、`prd-relay.md RL-7 ③`、`BILLING-MODEL-ARCHITECTURE.md §4.2`（校验对象=A，L1 之后 L2 之前；端点在 L1 前）、`DATA-MODEL.md §2 + Token 端点扩展`。
- **当前状态**：`Token.modelLimits/modelLimitsEnabled/endpointLimitsEnabled` 字段已在（`token/domain/model/Token.java`），语义已注释为减法约束，但**转发链无校验调用**（审计事实 5）。
- **目标（验收标准）**：
  - 主干在 L1(C→A) 之后、L2(A→B) 之前校验：`ModelLimitsEnabled=true` 时 A 必须 ∈ `model_limits`，否则拒绝（默认 false 全开放行）。
  - 入站协议识别后、L1 前校验 `EndpointLimits`（可选，默认全开）。
  - 拒绝走明确错误码（沿用现有 ModelLimits 拒绝语义），非权限闸门语义（自我约束）。
  - 验收：建一个 `model_limits_enabled=true` 且只放行某 A 的 key → 请求该 A 成功、请求另一 A 被拒；关闭开关后全部放行。
- **优先级**：P0
- **依赖**：REQ-02
- **可并行**：与 REQ-03、REQ-04 并行

---

# P1 — 重要

## [REQ-07] Claude 协议适配器完整实现（RL-6/RL-8 D1-D5 非流式双向）
- **类型**：MISSING
- **归属模块**：`relay/infrastructure/protocol`（`ClaudeProtocolAdapter`）
- **设计依据**：`prd-relay.md RL-8 D1-D5`、`COMPAT-LAYER-ARCHITECTURE §2`、`prd-relay.md RL-6`。
- **当前状态**：`ClaudeProtocolAdapter.java:56-77` 四个核心方法全 `throw ProtocolConversionException("not yet implemented")`。
- **目标（验收标准）**：
  - 实现 `parseRequest/serializeRequest/parseResponse/serializeResponse`（非流式），覆盖 D1 system 顶层位置 / D2 content block 数组 / D3 tools(input_schema)⇄tool_use/tool_result / D4 stop_reason / D5 usage(input/output_tokens) 双向往返。
  - 验收：`POST /v1/messages`（Anthropic 格式）打到 OpenAI 上游 → 经 IR 转换 `protocol_converted=true`，响应转回 Anthropic 格式；五差异点单测往返无丢失。
- **优先级**：P1
- **依赖**：REQ-04（IR 转换主干接线已通）
- **可并行**：与 REQ-08~REQ-12 并行

## [REQ-08] 流式 SSE 双向转换（OpenAI + Claude，StreamState 1→N）
- **类型**：MISSING
- **归属模块**：`relay/infrastructure/protocol` + `relay/application`（流式转发） + REQ-01 流式通道
- **设计依据**：`prd-relay.md RL-8`（流式 StreamState）、`RL-4`（streamSupportedChannels 降级）、`RL-6 §3.4`（逐 chunk 1→N）。
- **当前状态**：`OpenAiProtocolAdapter.java:196-206` + `ClaudeProtocolAdapter.java:80-89` 流式方法均返 `List.of()`。主干无流式转发路径。
- **目标（验收标准）**：
  - 实现两 adapter 的 `parseStreamChunk/serializeStreamChunk`（OpenAI `data:{choices:[{delta}]}`+`[DONE]` ⇄ Anthropic `message_start/content_block_delta/message_delta/message_stop`，`StreamState` 维护开闭/index/累计 usage）。
  - 主干支持流式转发（`stream:true` 时 SSE 回传）；渠道不在 streamSupportedChannels 时降级非流式。
  - 流式结束按累计 usage 走 REQ-05 双价记账。
  - 验收：curl `stream:true` → 收到 SSE 增量；OpenAI⇄Claude 流式互转 1→N 正确；末尾计费落 Log。
- **优先级**：P1
- **依赖**：REQ-04、REQ-07（Claude 流式）、REQ-01（流式通道）、REQ-05（流式结算）
- **可并行**：部分（OpenAI 流式可先于 Claude 流式）

## [REQ-09] 上游错误处理 + 按状态码重试/自动禁用渠道（RL-3）
- **类型**：MISSING
- **归属模块**：`relay/application` + `channel/domain`（禁用）+ `routing`（重试切渠）
- **设计依据**：`prd-relay.md RL-3`（ShouldRetryByStatusCode/RetryTimes/DisableChannel/MaskSensitiveError/按 RelayFormat 构造错误）。
- **当前状态**：无实现。
- **目标（验收标准）**：
  - 上游错误码 → 判可重试且 `<RetryTimes` 换渠道重试（复用 REQ-03 选渠/CH-5）；耗尽返错；不可重试跳过。
  - `AutoBan=1` 且命中条件 → 禁用渠道并通知（对齐 CH-3）；`AutoBan=0` 保持。
  - 错误日志经脱敏后 `Log Type=5 Error`（记 channel/model/status_code）。
  - 错误响应按 inFmt（OpenAI/Claude）构造。
  - 验收：mock 上游 500（可重试）→ 切渠重试；mock 401（不可重试）→ 直接返；AutoBan 命中 → 渠道 Status 置禁用。
- **优先级**：P1
- **依赖**：REQ-02、REQ-03
- **可并行**：与 REQ-07/08/10/11/12 并行

## [REQ-10] /v1/models 返回真实对外模型 A 全集（F-3034）
- **类型**：MISSING
- **归属模块**：`relay/interfaces/api`（`RelayController.models`）+ `relay`/`model` 域查询
- **设计依据**：`prd-model.md ML-6`（对外全集=PublicModel Enabled=true）、`ML-7`（候选只返 A 不含 B）、openapi `/v1/models`。
- **当前状态**：`RelayController.java:81-84` 返空 list TODO。
- **目标（验收标准）**：
  - 返回 `PublicModel.Enabled=true AND DeletedAt IS NULL` 的 public_name 集（A 全集），OpenAI models 列表格式；**绝不含 B**（三道闸序列化层）。
  - 验收：curl `GET /v1/models` 带 token → 返回上架对外模型 A 列表；下架的不出现；无任何 upstream_name(B)。
- **优先级**：P1
- **依赖**：无强依赖（可独立做，PublicModel 表已存在）；逻辑上属主干周边
- **可并行**：是

## [REQ-11] 视频内容代理 VideoProxy（RL-5：归属校验→终态→类型分支→SSRF→流式回写）
- **类型**：MISSING
- **归属模块**：`relay`（新增 `VideoProxyUseCase`）+ `relay/interfaces/api`（`videoContent`）
- **设计依据**：`prd-relay.md RL-5`（F-4046）、openapi `/v1/videos/{task_id}/content`、`DATA-MODEL.md §9 Task`。
- **当前状态**：`RelayController.java:144-149` 返 501 TODO。
- **目标（验收标准）**：
  - 校验 task 属本人且存在（否 404）→ `Status==SUCCESS`（否返当前状态）→ 按 `channel.Type` 解析 URL（Gemini 用 PrivateData.Key+x-goog-api-key / Vertex / OpenAI·Sora 拼路径）→ `data:` base64 直出 / 其余经 SSRF 校验（未过 403）→ `io.Copy` 流式回写 `Cache-Control max-age=86400`。
  - `PrivateData` 不下发。
  - 验收：本人已完成视频 task → 流式拿到内容；非本人 →404；未完成→状态错误；SSRF 非法 URL→403。
- **优先级**：P1
- **依赖**：REQ-01（HTTP/流式拉取）；任务域 Task 模型
- **可并行**：是（与协议链路相对独立）

## [REQ-12] 利润看板聚合查询（按模型/供应商/分组 GROUP BY Log）
- **类型**：MISSING
- **归属模块**：`billing`/`usagelog` 域（管理端查询）
- **设计依据**：`BILLING-MODEL-ARCHITECTURE.md §5.3`（DECISIONS §9）、`prd-billing.md BL-9`、openapi 管理端统计端点。
- **当前状态**：Log 三金额字段设计已落（REQ-05 写入后才有数据），无聚合查询接口。
- **目标（验收标准）**：
  - 提供 admin/root 聚合接口：`GROUP BY resolved_public_model(A)` / `channel(channel_id)` / `group`，度量 `SUM(quota_sell/quota_cost/quota_profit)` + 利润率。
  - 走索引；GORM/JPA 三库兼容写法（避免 DB 专有函数）。
  - 仅 admin/root 可见（含成本/利润）。
  - 验收：跑若干请求后，curl admin 看板接口 → 返回按模型/供应商/分组的售价/成本/利润聚合。
- **优先级**：P1
- **依赖**：REQ-05（先有双价 Log 数据）
- **可并行**：接口可先建，数据依赖 REQ-05

---

# P2 — 增强

## [REQ-13] Group 轻量注册中心（DESIGN-FIX：补 group 引用完整性）
- **类型**：DESIGN-FIX
- **归属模块**：新增 `group`/`identity` 子域 或并入现有 admin 配置
- **设计依据**：审计 §1.3 + architect ref「分组退化为权限范围+折扣系数」。设计有意让 group 为自由字符串，但缺中央枚举/校验。
- **当前状态**：group 全链为裸 varchar（`token.group/channels.group/abilities.group`），无主表、无校验、无法枚举有效 group、无折扣系数绑定的管理入口。
- **目标（验收标准）**：
  - 引入轻量 Group 注册（group 名 + 折扣系数 group_ratio + 可选权限范围 + 启用），不强行改成外键（保持 Ability 字符串路由不变），但提供：枚举有效 group、写 token/ability 时校验 group 存在、折扣系数集中管理（替代散落 KV）。
  - **先评估必要性再实施**（非命脉，可能与现有 group_ratio KV 重叠）。
  - 验收：admin 可列出所有 group + 折扣；创建 token 选 group 时从注册表取候选；引用不存在 group 给出提示。
- **优先级**：P2
- **依赖**：建议在 P0 命脉打通后评估（避免过早建模）
- **可并行**：是（独立子域）

## [REQ-14] 其余对外端点真实转发（embeddings/responses/responses-compact/edits）
- **类型**：MISSING
- **归属模块**：`relay/interfaces/api` + `relay/application`
- **设计依据**：`prd-relay.md RL-2`（F-3027/28/29/33 路径分发）、openapi 对应路径。
- **当前状态**：均走 `forwardRelay()` → 501。
- **目标（验收标准）**：主干打通后（REQ-02），这些端点复用同一转发链路真实转发（注意 compact 必须先于 responses 匹配、edits 独立 mode、embeddings 后缀匹配）。验收：各端点 curl 返回真实上游响应，路径分发顺序正确。
- **优先级**：P2
- **依赖**：REQ-02、REQ-04
- **可并行**：是

## [REQ-15] 协议注册表 Gemini/embedding 扩展位（回落不阻断现网）
- **类型**：MISSING（设计预留）
- **归属模块**：`relay/infrastructure/protocol`
- **设计依据**：`prd-relay.md RL-6 §1`（注册表预留 Gemini/embedding/图像/语音，未实现回落 per-channel 直转，不阻断）。
- **当前状态**：仅 openai/claude 注册位。
- **目标（验收标准）**：未命中协议回落直通（passthrough）不报错；新增协议 = 实现 ProtocolAdapter + init 注册一行，管线零改动。验收：未注册协议入站走回落直通不 500。
- **优先级**：P2
- **依赖**：REQ-04
- **可并行**：是

## [REQ-16] Stub 组件甄别与生产接线确认（ChannelProbe/ChannelSelection）
- **类型**：EXTRA-CLEANUP
- **归属模块**：`channel/infrastructure/probe`、`routing/infrastructure/selection`
- **设计依据**：审计 §2.3。
- **当前状态**：`StubChannelProbeClient`、`StubChannelSelectionAdapter` 与真实实现并存。
- **目标（验收标准）**：确认生产环境注入真实实现（`AbilityBackedChannelSelectionAdapter`）；探活 stub 真实化或明确标注仅测试用（按 Profile 隔离）。验收：生产 Profile 启动时选渠走 Ability 版，stub 不被注入主链路。
- **优先级**：P2
- **依赖**：REQ-03
- **可并行**：是

## [REQ-17] allow_ips IP 白名单 + 限流接入转发主干（ModelRequestRateLimit）
- **类型**：DIVERGED（字段在，转发链无人读）
- **归属模块**：`relay/application` + `token/domain`
- **设计依据**：`prd-relay.md RL-1 §3.2`（TokenAuth→ModelRequestRateLimit）、`DATA-MODEL.md §2`（allow_ips）。
- **当前状态**：`Token.allowIps` 字段在，转发链无 IP 校验/限流（审计事实 5）。
- **目标（验收标准）**：TokenAuth 后校验请求 IP ∈ allow_ips（空=不限）；接入 ModelRequestRateLimit 限流（超限拒绝、不预扣）。验收：配 allow_ips 的 key 从非白名单 IP 调用被拒；限流超限返回限流码。
- **优先级**：P2
- **依赖**：REQ-02
- **可并行**：是

---

## 依赖关系图（并行/串行决策）

```
P0 命脉串行主链：
  REQ-01 ──┬─────────────────────────────┐
           │                             ▼
  REQ-02 ──┼──→ REQ-03 ──┐         REQ-05 (收口闭环 ✅)
           │    REQ-04 ──┼──────────→ ▲
           │    REQ-06 ──┘            │
           └─ REQ-04 需 REQ-01 ───────┘

P0 内并行组：{REQ-03, REQ-04, REQ-06} 三者互不依赖，可三个 worker 同时领
P0 收口：REQ-05 必须等 03+04 完成（串行）

P1 全部依赖 P0 主干通（REQ-02 起）：
  REQ-07(Claude) → 解锁 REQ-08(流式Claude)
  REQ-08 需 REQ-04+07+01+05
  REQ-09 需 REQ-02+03
  REQ-10 几乎独立（PublicModel 表已在）→ 可早做
  REQ-11 需 REQ-01（独立于协议链）
  REQ-12 需 REQ-05 有数据
  → REQ-07/09/10/11 可并行铺 4 个 worker

P2 增强：REQ-13(评估后做)、14/15/16/17 多数独立可并行
```

**推荐排期**：
1. **Wave 1（串行起步）**：REQ-01 + REQ-02（骨架，1-2 worker，REQ-02 可先接桩 client）。
2. **Wave 2（并行爆发）**：REQ-03 ∥ REQ-04 ∥ REQ-06（3 worker）+ REQ-10 ∥ REQ-11（独立 2 worker）。
3. **Wave 3（命脉收口）**：REQ-05（双价计费落 Log，命脉闭环）。
4. **Wave 4（P1 铺开）**：REQ-07 ∥ REQ-09 ∥ REQ-12 → REQ-08。
5. **Wave 5（P2）**：REQ-14/15/16/17 并行；REQ-13 先评估必要性。
