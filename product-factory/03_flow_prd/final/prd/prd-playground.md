# PRD — Playground 在线试用（FL-playground）

> 分片：Playground 在线试用（D17）。对应流程图 `flow/FL-playground.md`（3 图：PG-1 首页入口 / PG-2 对话提交主链路 / PG-3 错误处理）、状态矩阵 `PAGE-STATE-MATRIX.md`。
> 数据对象字段一律复用 `DATA-MODEL.md §1 User / §2 Token`；临时令牌为运行期内存构造（非落库实体），其字段语义对齐 §2 Token 的 `Name/Group`。
> 跨切面契约见 `OVERALL-FLOW.md §3`：C1 鉴权（`playgroundRouter /pg` 挂 `UserAuth`）；转发/选渠/计费/重试不在本片重画，分别见 `prd-relay.md`（RL-1 主链路、RL-3 上游错误处置）、`prd-billing.md`（BL-2 预扣判定）、`prd-channel.md`（CH-2 选渠失败）。
> 后端：`controller/playground.go Playground`、`controller/misc.go GetStatus`。
> 本片覆盖功能 ID：**F-4038 / F-4039 / F-4040**。

---

## PG-1 首页对话框入口与登录引导（匿名「问点什么…」→ C1 闸门 → 进 Playground）

- **功能 ID / 优先级**：F-4040 / P2（入口渲染依赖 F-4039 GetStatus）
- **来源**：FC-122（WEBSITE-COVERAGE §4 首页对话输入框 placeholder「问点什么…」；后端落地 `controller/playground.go Playground`，`/pg` 路由挂 `UserAuth`）；入口配置聚合自 FC-121 `controller/misc.go GetStatus`
- **角色 / Owner**：访客 Guest（未登录）/ 用户 User（登录后）；Owner 模块 = Playground（衔接公开站）
- **触发**：访客在营销首页对话框点「发送」

### 1. 场景
营销首页拉 `GetStatus` 渲染对话输入框，placeholder 固定文案「问点什么…」，作为 Playground 试用诱导入口。访客点「发送」时，因目标路由 `/pg` 受 `UserAuth` 保护，须先经契约 C1 鉴权闸门判定登录态：未登录 → 引导登录（externalAuth gate），登录成功回流到 Playground；已登录 → 直接进入 Playground 对话视图，达到「就绪态」（待选模型/输入）。本块只画入口闸门与登录回流，不画对话提交（见 PG-2）。

### 2. 前置条件
- 首页对话框已由 `GetStatus`（F-4039）渲染，placeholder 文案 = 「问点什么…」。
- `/pg` 路由受 `UserAuth` 中间件保护（契约 C1）。
- 当前会话登录态由是否持有有效 JWT 决定（`User` 鉴权态）。

### 3. 主流程（对应 PG-1 节点 pg_home→pg_ready）
1. 访客打开营销首页（pg_home）→ `GetStatus` 渲染对话框 placeholder「问点什么…」（pg_status）。
2. 点击「发送」（pg_send）→ 进入登录态判定（pg_auth）。
3. 未登录（pg_auth-否/匿名）→ 引导登录，复用 C1 externalAuth gate（pg_login）→ 登录成功回到 Playground（pg_back）。
4. 已登录（pg_auth-是）→ 进入 Playground 对话视图（pg_enter）。
5. 回流与直达两路汇入对话视图（pg_enter）→ Playground 就绪态（待选模型/输入，pg_ready，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 匿名点「发送」 | pg_auth-否 | 触发 C1 externalAuth gate，跳登录 | 匿名引导登录态（闸门拦截） |
| 登录成功回流 | pg_back | 携登录态回到 Playground | 登录回流态 |
| 已登录点「发送」 | pg_auth-是 | 不经登录页直达对话视图 | Playground 就绪态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option / §1 User）
- **读** `Option` KV（经 `GetStatus` 聚合）：首页对话框可见性与 placeholder 文案来源；本块只消费 `GetStatus` 返回，不直接读敏感键。
- **会话** `User`（鉴权态）：是否持有有效 JWT（对照 §1 User `Role`、`Status=1` 为可用），决定走 C1 闸门还是直达。
- **不涉及落库写**：本块为入口判定，无 `Token`/`Log` 写入。

### 6. 验收标准
- [ ] 首页对话框 placeholder 文案为「问点什么…」（与 `GetStatus` 渲染一致）。
- [ ] 匿名（无 JWT）点「发送」→ 跳转登录闸门（C1），HTTP 不进入 `/pg`，不发起对话请求。
- [ ] 登录成功后从登录闸门回流到 Playground 对话视图（不停留在登录页）。
- [ ] 已登录（持有效 JWT，`Status=1`）点「发送」→ 直接进入 Playground 就绪态，不经登录页。
- [ ] 被封禁用户（`User.Status≠1`）点「发送」→ 不进入 Playground 就绪态（鉴权拒绝）。

### 7. 所触及页面状态（对齐 PG-1 首页入口）
入口渲染态（GetStatus，placeholder「问点什么…」）· 匿名引导登录态（C1 闸门，异常/闸门）· 登录回流态（登录成功回到 Playground）· Playground 就绪态（待选模型/输入，终态）。

---

## PG-2 站内对话提交主链路（拒绝 access_token → 临时令牌构造 → relay 流式输出）

- **功能 ID / 优先级**：F-4038 / P0
- **来源**：FC-120（`controller/playground.go Playground` 拒绝 `use_access_token` 返 `ErrorCodeAccessDenied`；`GenRelayInfo(RelayFormatOpenAI)`；构造临时令牌 `{Name:playground-<group>, Group:UsingGroup}` 经 `SetupContextForToken` 后调 `Relay`；按用户实际额度计费）
- **角色 / Owner**：用户 User（self-scope，按本人额度计费）；Owner 模块 = Playground
- **触发**：用户在 Playground 选定模型并 `POST /pg/chat/completions`

### 1. 场景
用户在 Playground 选模型后提交对话，请求先经中间件链 `UserAuth + Distribute + SystemPerformanceCheck`。系统过载时 `SystemPerformanceCheck` 直接拒绝（系统繁忙）。通过后 `Playground` 处理器**拒绝携带 `use_access_token` 的请求**（返 `ErrorCodeAccessDenied`「暂不支持使用 access token」），强制走临时令牌路径：`GenRelayInfo(RelayFormatOpenAI)` 构造转发上下文，构造**运行期临时令牌** `{Name:playground-<group>, Group:UsingGroup}`，经 `SetupContextForToken` 注入上下文后调 `Relay`（复用 RL-1 选渠/转发/计费，**按用户实际额度计费**，不消耗独立试用配额）。流式响应逐 chunk（SSE）渲染到对话气泡，非流式则一次性渲染。本块只画 Playground 特有的「中间件→拒绝 access_token→临时令牌构造→流式渲染」，转发内部不重画（见 RL-1）。

### 2. 前置条件
- 用户已登录（PG-1 已过 C1），`/pg/chat/completions` 受 `UserAuth + Distribute + SystemPerformanceCheck` 中间件链保护。
- 用户已在 Playground 选定一个模型。
- 用户分组 `UsingGroup` 已确定（来自 `User.Group`，对照 §1 User `Group`）。
- 用户当前可用额度 = `User.Quota - User.UsedQuota`（对照 §1 User 字段）。

### 3. 主流程（对应 PG-2 节点 pp_in→pp_done）
1. `POST /pg/chat/completions` 携选定模型（pp_in）→ 中间件链 `UserAuth + Distribute + SystemPerformanceCheck`（pp_mw）。
2. 判 `SystemPerformanceCheck` 是否通过（pp_perf）：系统过载 → 拒绝「系统繁忙」（pp_busy）。
3. 通过 → 判 `use_access_token`（pp_tok）：为真 → 返 `ErrorCodeAccessDenied`「暂不支持使用 access token」（pp_deny）。
4. 否 → `GenRelayInfo(RelayFormatOpenAI)`（pp_info）→ 构造临时令牌 `Name=playground-<group>，Group=UsingGroup`（pp_temp）。
5. `SetupContextForToken` 注入上下文（pp_setup）→ 调 `Relay`（复用 RL-1，按用户实际额度计费，pp_relay）。
6. 判流式响应（pp_stream）：是（SSE）→ 逐块渲染到对话气泡（pp_chunk）；否 → 一次性渲染完整回复（pp_full）。
7. 两路汇入对话完成（额度按实际扣减，pp_done，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 系统过载 | pp_perf-系统过载 | `SystemPerformanceCheck` 拒绝，不进入转发 | 系统繁忙态（异常） |
| 请求含 `use_access_token` | pp_tok-是 | 返 `ErrorCodeAccessDenied`，不构造临时令牌 | access_token 拒绝态（异常） |
| 通过校验 | pp_tok-否 | 构造临时令牌 `playground-<group>` 并 `SetupContextForToken` | 临时令牌构造态 → 上下文注入态 |
| 流式响应 | pp_stream-是 | SSE 逐 chunk 推送 | 流式输出态（气泡逐块） |
| 非流式响应 | pp_stream-否 | 一次性返回完整 body | 非流式输出态 |

### 5. 数据对象（复用 DATA-MODEL §2 Token / §1 User）
- **运行期临时令牌**（非落库，对齐 §2 Token 字段语义）：`Name=playground-<group>`（字符串，`<group>`=用户 `UsingGroup`）、`Group=UsingGroup`（来自 `User.Group`）；不写 `Token` 表、不生成 `Key`、不占用用户令牌列表。
- **读** `User`（§1）：`Group`（决定 `UsingGroup` 与选渠/倍率）、`Quota`/`UsedQuota`（计费扣减基准，按用户实际额度而非临时配额）、`Status`（=1 方可调用）。
- **写**：转发计费由 RL-1 链路落 `Log`（§5 Log，`Type=2 Consume`、`TokenName=playground-<group>`、`Group`、`PromptTokens/CompletionTokens/Quota/UseTime/IsStream`）；本块不重复定义计费写。
- **拒绝标识**：`use_access_token`=true 时返 `ErrorCodeAccessDenied`，请求体不被转发。

### 6. 验收标准
- [ ] `POST /pg/chat/completions` 携 `use_access_token=true` → 返回 `ErrorCodeAccessDenied`，消息含「暂不支持使用 access token」，且未发起上游转发。
- [ ] 不携 access_token 的合法请求 → 服务端构造临时令牌且 `Name` 形如 `playground-<group>`、`Group=UsingGroup`（与用户分组一致）。
- [ ] 临时令牌**不**出现在该用户的令牌列表中（断言 `Token` 表无对应落库记录）。
- [ ] `SystemPerformanceCheck` 不通过时返回「系统繁忙」拒绝，且不进入临时令牌构造与转发。
- [ ] 流式请求（`stream=true`）→ 响应以 SSE 逐 chunk 到达；非流式 → 单次完整 body。
- [ ] 对话完成后，扣减额度命中**用户本人**额度（`User.UsedQuota` 增量 = 本次 `Log.Quota`），非独立试用配额。

### 7. 所触及页面状态（对齐 PG-2 对话提交）
系统繁忙态（SystemPerformanceCheck 未过，异常）· access_token 拒绝态（ErrorCodeAccessDenied，异常）· 临时令牌构造态（playground-<group>，Group=UsingGroup）· 上下文注入态（SetupContextForToken）· 流式输出态（SSE 逐块渲染）· 非流式输出态（一次性完整回复）· 对话完成态（按用户实际额度扣减，终态）。

---

## PG-3 试用错误处理与额度/选渠失败回显（relay 错误路径 → 对话气泡降级显示）

- **功能 ID / 优先级**：F-4038 / P0（与 PG-2 同功能，错误回显视角）
- **来源**：FC-120（Playground 调 `Relay` 后复用 RL-1/RL-3 错误路径，Playground 侧以对话气泡降级回显，而非整页报错）
- **角色 / Owner**：用户 User（self-scope）；Owner 模块 = Playground
- **触发**：Playground 调 `Relay` 返回（成功或各类失败）

### 1. 场景
Playground 转发后复用 relay 错误路径，但以 Playground UI 回显视角组织（区别 RL-3 的系统处置视角）：成功 → 气泡渲染回复（见 PG-2）；失败按来源分类——额度不足（`userQuota<=0 → 403`，复用 BL-2 预扣判定）、无可用渠道（选渠失败，复用 CH-2）、上游错误（按状态码 `ShouldRetry`，复用 RL-3：可重试已自动换渠道仍失败 → 「上游暂不可用」；不可重试 → 回显**脱敏后**的上游错误信息）。所有失败均以**对话气泡内降级提示**呈现（不整页 500），用户可改模型/输入重试或放弃。本块只画 Playground 侧回显与重试闭环，不重画 relay 内部的重试/AutoBan/选渠（见 RL-3/CH-2）。

### 2. 前置条件
- PG-2 已构造临时令牌并调 `Relay`（本块承接 `Relay` 返回结果）。
- relay 内部的重试/禁用/选渠机制由 RL-3、CH-2 负责（本块不重画）。
- 错误信息回显前须经脱敏（不向 UI 暴露上游原始密钥/内部栈，对齐 NFR-S04，见 `prd-nfr-rbac.md` NR-S04）。

### 3. 主流程（对应 PG-3 节点 pe_relay→pe_end）
1. Playground 调 `Relay` 返回（pe_relay）→ 判成功（pe_ok）。
2. 成功 → 气泡渲染回复（pe_show，终态，承接 PG-2）。
3. 失败 → 判错误来源（pe_kind）：
   - 额度不足 403 → 气泡提示「额度不足」（复用 BL-2，pe_quota）。
   - 无可用渠道 → 气泡提示「当前分组无可用渠道」（复用 CH-2，pe_chan）。
   - 上游错误 → 判 `ShouldRetry`（复用 RL-3，pe_up）：可重试已自动换渠道仍失败 → 「上游暂不可用」（pe_retryfail）；不可重试 → 回显脱敏上游错误信息（pe_hard）。
4. 各失败气泡汇入「是否改模型/输入重试」（pe_again）：重试 → 回到 pe_relay；放弃 → 结束试用（pe_end，终态）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| relay 成功 | pe_ok-成功 | 渲染回复内容到气泡 | 成功回显态（终态） |
| 额度不足（userQuota<=0，403） | pe_quota | 复用 BL-2 预扣判定，气泡降级提示 | 额度不足气泡态（异常） |
| 无可用渠道 | pe_chan | 复用 CH-2 选渠失败，气泡降级提示 | 无可用渠道气泡态（异常） |
| 上游错误·可重试换渠道仍失败 | pe_retryfail | 复用 RL-3 ShouldRetry，提示「上游暂不可用」 | 上游可重试仍失败态（异常） |
| 上游错误·不可重试 | pe_hard | 回显脱敏后错误信息 | 上游不可重试态（异常） |
| 用户重试 | pe_again-重试 | 携新模型/输入重发，回到 pe_relay | 改模型/输入重试态 |
| 用户放弃 | pe_again-放弃 | 终止试用会话 | 放弃结束态（终态） |

### 5. 数据对象（复用 DATA-MODEL §1 User / §2 Token / §5 Log）
- **读** `User.Quota`/`User.UsedQuota`（§1）：额度不足判定基准（`userQuota<=0 → 403`，复用 BL-2）。
- **读** 选渠能力（§4 Ability，经 CH-2）：当前分组无 `Enabled=true` 渠道 → 无可用渠道分支；本块只消费 CH-2 结论。
- **错误回显字段**：上游错误信息经脱敏后回显（不含原始 `Channel.Key`、不含内部栈），对齐 NFR-S04 脱敏规则。
- **失败日志**：失败转发由 RL-3 落 `Log`（§5，`Type=5 Error`，含 `Content` 错误描述、`ChannelId`），本块不重复定义。

### 6. 验收标准
- [ ] relay 成功 → Playground 气泡渲染回复内容（不展示原始 HTTP 状态行）。
- [ ] `userQuota<=0` 触发的 403 → 气泡内显示「额度不足」降级提示，页面不整页报错（无 500 页）。
- [ ] 当前分组无可用渠道 → 气泡内显示「当前分组无可用渠道」，且不向用户暴露内部选渠细节。
- [ ] 上游错误经自动换渠道后仍失败（RL-3 ShouldRetry 路径耗尽）→ 气泡显示「上游暂不可用」。
- [ ] 上游不可重试错误回显时，气泡文案**不包含** `sk-`/`Bearer` 形态密钥串或内部栈帧（已脱敏，对照 NFR-S04）。
- [ ] 任一失败气泡下点「重试」→ 携新输入重新走 PG-2 主链路；点「放弃」→ 进入放弃结束态。

### 7. 所触及页面状态（对齐 PG-3 错误处理）
成功回显态（气泡渲染回复，终态）· 额度不足气泡态（403，复用 BL-2，异常）· 无可用渠道气泡态（复用 CH-2，异常）· 上游可重试仍失败态（自动换渠道后仍失败，异常）· 上游不可重试态（脱敏错误回显，异常）· 改模型/输入重试态（用户重发）· 放弃结束态（终态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-4038 | PG-2（提交主链路）+ PG-3（错误处理） |
| F-4039 | PG-1（GetStatus 渲染入口，主块见 prd-public PUB-1） |
| F-4040 | PG-1（首页对话框 Playground 入口） |

> 跨片引用：F-4039/F-4040 的入口闸门主块在 `prd-public.md`（PUB-1/PUB-2），本片以 Playground 视角补 PG-1 入口就绪闸门；转发/计费/选渠/重试见 prd-relay / prd-billing / prd-channel；脱敏规则见 prd-nfr-rbac NR-S04。

无 `[BLOCKER]`。
