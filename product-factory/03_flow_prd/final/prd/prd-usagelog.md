# PRD — 日志与用量（FL-usagelog）

> 分片：日志与用量 D4。对应流程图 `flow/FL-usagelog.md`、状态矩阵 `PAGE-STATE-MATRIX.md §E`。
> 数据对象字段一律复用 `DATA-MODEL.md §5 Log`（Log Type 枚举 0=Unknown 1=Topup 2=Consume 3=Manage 4=System 5=Error 6=Refund 7=Login）。
> 调用明细 / 三段模型 / 两侧视图（UL-9/UL-10）业务规则唯一权威 = `COMPAT-BILLING-DECISIONS.md`（§2 两层映射 / §8 统计日志）；数据对象 = `reviews/COMPAT-LAYER-DATA-OBJECTS.md §3`（三段模型字段 + UserLogView/AdminLogView）+ `reviews/BILLING-DATA-OBJECTS.md §3`（售价/成本/利润金额字段），Log 字段以 `DATA-MODEL.md §5` 已落 10 字段为准（`requested_model/resolved_public_model/actual_upstream_model/inbound_protocol/upstream_protocol/protocol_converted/user_agent/quota_sell/quota_cost/quota_profit`）。转发链路落点见 `FL-relay.md RL-7`。
> 本片覆盖功能 ID：**F-4001 / F-4002 / F-4003 / F-4004 / F-4005 / F-4006 / F-4007 / F-4008 / F-4009 / F-4010 / F-4011 / F-4012 / F-4013 / F-4014 / F-4015 / F-4016**.

---

## UL-1 日志查询（管理端八维全量 vs 用户自助本人范围）

- **功能 ID / 优先级**：F-4001、F-4002 / P0
- **来源**：FC-101（`controller/log.go GetAllLogs`、`GetUserLogs`、`api-router.go logRoute.GET("/") AdminAuth`、`logRoute.GET("/self") UserAuth`）
- **角色 / Owner**：管理员（AdminAuth 全量）/ 登录用户（UserAuth 仅本人）；Owner 模块 = 日志与用量
- **触发**：管理员进 `GET /log/` 设过滤翻页；或用户进 `GET /log/self` 看本人日志

### 1. 场景
同一份日志列表存在两个入口，走不同的范围闸门。管理员需要在全站日志里按多维度（类型、时间区间、用户名、令牌名、模型名、渠道、分组、请求 ID）任意组合定位某条调用记录；普通用户只能查本人日志，且自助接口刻意不暴露 username/channel 这类跨用户维度，以杜绝越权刺探他人调用情况。权限分叉决定了可用过滤维度，这是本块的核心。

### 2. 前置条件
- 管理端：会话过 C1 且角色为 AdminAuth（`logRoute.GET("/")`）。
- 用户自助：会话过 C1（`logRoute.GET("/self")`），`userId` 取自上下文 `c.GetInt("id")`，不可由请求参数覆盖。
- 不传 `type` 时按 `LogTypeUnknown=0` 处理，返回全部类型。

### 3. 主流程（对应 UL-1 节点 D0→AOK/UOK）
1. 进入日志页（D0），判断鉴权身份（D1）。
2. AdminAuth 全量入口 `GetAllLogs`（A1），可选八维过滤（A2）。
3. 判是否传了 `type`（A3）：否→`type=0` 返回全部类型（A4）；是→按指定 `type` 子集（A5）。
4. 两路合流分页 `total + page_size`（A6）→ 管理端全量日志分页态（AOK）。
5. UserAuth 自助入口 `GetUserLogs` 强制 `user_id=当前用户`（U1）。
6. 判是否尝试带 username/channel 维度（U2）：是→自助接口不暴露该维度，忽略（UE1）；否→按 type/时间/token/model/group 过滤本人（U3）。
7. 分页 `total` 仅本人记录（U4）→ 用户自助本人日志分页态（UOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 管理端不传 `type` | A3-否 | 按 `LogTypeUnknown=0` 不限类型 | 返回全部 type 日志 |
| 管理端传 `type` | A3-是 | 仅返回该 `Type` 子集 | 类型筛选生效 |
| 自助接口带 username/channel | U2-是 | 接口层不接受该维度 → 忽略 | 维度受限，仅本人过滤生效 |
| 普通用户访问 `GET /log/` | D1（AdminAuth 闸门） | 拒绝（非 AdminAuth） | 无权限，不返回全量日志 |

### 5. 数据对象（复用 DATA-MODEL §5）
- **读** `Log`：`Type(type)`（过滤维 + 0=Unknown 全量）、`CreatedAt(created_at)`（时间区间，复合索引 idx_created_at_id）、`Username(username)`、`TokenName(token_name)`、`ModelName(model_name)`、`ChannelId(channel)`、`Group(group)`、`RequestId(request_id)`、`UpstreamRequestId(upstream_request_id)`。
- **越权过滤键** `Log.UserId(user_id)`：自助接口强制 `=当前用户`（复合索引 idx_user_id_id）。
- **自助接口不下发维度**：`Username(username)`、`ChannelId(channel)`（管理端独有）。

### 6. 验收标准
- [ ] 管理端传 `type=2` → 仅返回 `Type=2(Consume)` 记录，其余类型不出现。
- [ ] 管理端不传 `type` → 返回含 Topup/Consume/Manage/Login 等多种 `Type` 的混合列表。
- [ ] 用户 A 访问 `GET /log/self` → 返回项 `UserId` 全等于 A，无 `UserId≠A` 的记录。
- [ ] 自助接口请求体携带 `username=他人` → 该参数被忽略，仍只返回本人日志。
- [ ] 普通用户调用 `GET /log/` → 被 AdminAuth 拒绝，不返回任何他人日志。
- [ ] 管理端与自助接口分页 `total`、`page_size` 字段均正确反映过滤后总数。

### 7. 所触及页面状态（对齐 §E「日志查询」）
管理端八维过滤面板态 · 全部类型态（未传 type=0）· 指定类型子集态（传 type）· 管理端全量分页列表态 · 自助维度受限态（username/channel 不可用）· 用户自助本人日志分页态（强制 user_id）。

---

## UL-2 用量统计看板（消费日志聚合 quota/rpm/tpm）

- **功能 ID / 优先级**：F-4004、F-4005 / P1
- **来源**：FC-102（`controller/log.go GetLogsStat`、`GetLogsSelfStat`、`model/log.go SumUsedQuota Where(type=LogTypeConsume)`、`logRoute /stat AdminAuth`、`/self/stat UserAuth`）
- **角色 / Owner**：管理员（全站）/ 登录用户（本人 username）；Owner 模块 = 日志与用量
- **触发**：管理员或用户打开用量统计卡片

### 1. 场景
看板卡片需把消费明细聚合成三项指标：累计消耗额度 quota、每分钟请求数 rpm、每分钟 token 数 tpm。聚合只统计真实消费记录（`Type=2 Consume`），充值/管理/登录等非消费日志不计入。管理端可随过滤项变化看全站，用户自助看板的 username 来自上下文不可伪造。看板按数据获取流绘制，终态既要有可视化卡片态，也要覆盖区间内无消费的空态。

### 2. 前置条件
- 管理端 `GET /log/stat` 过 C1 + AdminAuth；用户自助 `GET /log/self/stat` 过 C1 + UserAuth。
- 自助接口 `username` 取自上下文 `c.GetString("username")`，请求参数不可覆盖。
- 聚合查询固定 `Where(type=2 LogTypeConsume)`。

### 3. 主流程（对应 UL-2 节点 V0→VOK）
1. 打开用量统计看板（V0），确定统计范围（admin 全站 / self 本人 username）（V1）。
2. 请求 `SumUsedQuota`，过滤 `type=2 Consume`（V2）。
3. 判后端聚合返回（V3）：出错/超时→加载失败态（VE1）；成功→进下一步。
4. 判区间内是否有消费记录（V4）：无→空态 quota/rpm/tpm 全 0 占位（VEMP）；有→进渲染。
5. 渲染 quota 卡片（V5）→ rpm 卡片（V6）→ tpm 卡片（V7）→ 可视化看板态三指标卡片（VOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 聚合查询出错/超时 | V3-出错 | 不渲染卡片，提供重试 | 加载失败重试态 |
| 区间内无 `Type=2` 记录 | V4-无 | 三指标置 0 占位图 | 空态（quota/rpm/tpm=0） |
| 自助接口请求带 `username=他人` | V1（上下文 username） | username 取上下文不可覆盖 | 仅统计本人消费 |

### 5. 数据对象（复用 DATA-MODEL §5）
- **聚合源** `Log`：仅 `Type(type)=2(Consume)` 的行参与统计。
- **聚合字段** `Log.Quota(quota)`（本次消费额度，求和得 quota 卡片）、`Log.PromptTokens(prompt_tokens)`+`CompletionTokens(completion_tokens)`（换算 tpm）；rpm 由记录条数 / 区间分钟数得出。
- **范围键** `Log.Username(username)`（自助看板固定为当前用户）。

### 6. 验收标准
- [ ] 看板返回字段恰为 `quota`、`rpm`、`tpm` 三项。
- [ ] 区间内仅有 `Type=1(Topup)` 充值记录、无 `Type=2` → 三指标返回 0（空态），不把充值额度计入 quota。
- [ ] 区间内有消费记录 → quota 等于该区间 `Type=2` 行 `Quota` 之和。
- [ ] 自助接口传 `username=他人用户名` → 返回的统计仍为当前登录用户的消费聚合。
- [ ] 聚合查询超时 → 返回加载失败态并可重试，不返回脏数据。

### 7. 所触及页面状态（对齐 §E「用量统计看板」）
看板加载中态（请求 SumUsedQuota）· 加载失败重试态（查询出错/超时）· 空态（区间内无 Consume 记录，三指标置 0 占位）· 可视化看板态（quota/rpm/tpm 三卡片渲染）。

---

## UL-3 配额按日数据看板（区间聚合 + 自助跨度 1 月护栏 + 用户维度分组）

- **功能 ID / 优先级**：F-4007、F-4008、F-4009 / P1
- **来源**：FC-103（`controller/usedata.go GetAllQuotaDates(start,end,username)`、`GetQuotaDatesByUser` → `GetQuotaDataGroupByUser`、`GetUserQuotaDates` → `GetQuotaDataByUserId`、校验 `endTimestamp-startTimestamp > 2592000`、`dataRoute.GET("/") AdminAuth`、`/users`、`/self` UserAuth）
- **角色 / Owner**：管理员（全站/指定用户/按用户分组）/ 登录用户（本人，跨度上限 1 月）；Owner 模块 = 日志与用量
- **触发**：用户进配额数据看板选区间

### 1. 场景
配额数据看板按日聚合每天的消耗，支撑趋势分析。管理端有三种视角：空 username 看全站按日、指定 username 看单用户按日、按用户维度分组看各用户排布；管理端无时间跨度限制。用户自助只能看本人，且接口设了一道护栏——区间跨度不能超过 1 个月（2592000 秒），防止单次拉取过大区间拖垮聚合查询。自助跨度护栏是与管理端的关键区别。

### 2. 前置条件
- 管理端 `dataRoute.GET("/")`、`/users` 过 C1 + AdminAuth。
- 用户自助 `dataRoute.GET("/self")` 过 C1 + UserAuth，`userId` 取上下文 `c.GetInt("id")`。
- 自助接口先校验 `endTimestamp - startTimestamp <= 2592000`。

### 3. 主流程（对应 UL-3 节点 W0→WOK）
1. 进入配额数据看板选区间（W0），判视角（W1）。
2. 管理端按日（M1）：未填 username→`GetAllQuotaDates` 全站按日聚合（M2）；填了 username→`GetAllQuotaDates` 指定用户按日（M3）。
3. 管理端按用户（M4）：`GetQuotaDataGroupByUser` 用户维度分组。
4. 用户自助（S1）：判 `end - start > 2592000`，否→`GetQuotaDataByUserId` 仅当前 userId（S2）。
5. 各路合流组装按日 QuotaData 序列（M5），判区间内是否有数据（W2）。
6. 无→空态折线图占位（WEMP）；有→按日配额折线/堆叠可视化态（WOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 管理端空 username | M1-否 | `GetAllQuotaDates` 全站聚合 | 全站按日数据 |
| 管理端填 username | M1-是 | `GetAllQuotaDates` 过滤该用户 | 指定用户按日数据 |
| 自助 `end-start > 2592000` | S1-是 | 拒绝查询 | 「时间跨度不能超过 1 个月」 |
| 区间内无数据 | W2-无 | 返回空序列 | 折线图无数据占位空态 |

### 5. 数据对象（复用 DATA-MODEL §5，QuotaData 按日/按用户聚合源自 Log）
- **聚合源** `Log`：按 `CreatedAt(created_at)` 落入对应日期桶，`Quota(quota)` 逐日求和。
- **管理端范围键** `Log.Username(username)`：空=全站、非空=指定用户；按用户分组以 `Log.UserId(user_id)` 维度切分。
- **自助范围键** `Log.UserId(user_id)`=当前用户；护栏常量 `2592000`（1 月秒数）作用于区间跨度。

### 6. 验收标准
- [ ] 管理端不填 username → 返回全站按日聚合序列。
- [ ] 管理端填 `username=A` → 返回的按日序列仅含 A 的消耗。
- [ ] 管理端按用户视角 → 返回以 user 维度分组的聚合，多用户各占一组。
- [ ] 用户自助传 `end - start = 2592001` 秒 → 拒绝 + 返回「时间跨度不能超过 1 个月」。
- [ ] 用户自助传 `end - start = 2592000` 秒（恰好 1 月）→ 放行，仅返回当前 userId 数据。
- [ ] 区间内无消费 → 返回空序列折线图占位，不报错。

### 7. 所触及页面状态（对齐 §E「配额数据看板」）
区间选择面板态 · 管理端全站按日态（空 username）· 管理端指定用户按日态 · 管理端按用户维度分组态 · 自助跨度超限态（>2592000 秒）· 空态（区间内无数据占位）· 按日配额可视化态（折线/堆叠图）。

---

## UL-4 按令牌 key 查询消费日志（外部 Bearer + 限流 + 禁缓存）

- **功能 ID / 优先级**：F-4003 / P1
- **来源**：FC-102（`controller/log.go GetLogByKey` 取 `c.GetInt("token_id")` 调 `GetLogByTokenId`、`logRoute.GET("/token") TokenAuthReadOnly + CORS + CriticalRateLimit`）
- **角色 / Owner**：外部客户端（携 key 调用）；Owner 模块 = 日志与用量
- **触发**：外部程序携 token key `GET /api/log/token`

### 1. 场景
外部程序（无会话）用令牌 key 拉取自己这把 key 产生的消费日志。链路短直、限流密集：先过 `TokenAuthReadOnly` 只读鉴权解析 key 并置入 `token_id`，再经 `CriticalRateLimit` 防高频刷取，结果禁缓存以保证拉到实时数据。`token_id==0` 表示 key 解析失败，直接判无效令牌。这条外部只读 API 链与控制台分页查询刻意不同。

### 2. 前置条件
- 请求经 `TokenAuthReadOnly` 解析 key 并过 CORS。
- 中间件将解析结果置入上下文 `token_id`。
- 受 `CriticalRateLimit` 限流保护。

### 3. 主流程（对应 UL-4 节点 T0→TOK）
1. 外部客户端携 key `GET /api/log/token`（T0），过 `TokenAuthReadOnly` 解析 key + CORS（T1）。
2. 判是否触发 `CriticalRateLimit`（T2）：是（高频）→限流拦截稍后重试（TE1）；否→进下一步。
3. 中间件置入 `token_id`（T3），判 `token_id == 0`（T4）。
4. 是→「无效的令牌」（TE2）；否→`GetLogByTokenId` 取该 token 日志（T5）。
5. 判该 token 是否有日志（T6）：无→空数组态（TEMP）；有→日志数组返回态 DisableCache（TOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 高频触发限流 | T2-是 | `CriticalRateLimit` 拦截 | 稍后重试（限流态） |
| `token_id == 0` | T4-是 | 拒绝查询 | 「无效的令牌」 |
| 该 token 无消费记录 | T6-无 | 返回空数组 | 空数组态（无消费记录） |
| 命中有效 token 且有日志 | T6-有 | 返回日志数组，禁缓存 | DisableCache 日志数组态 |

### 5. 数据对象（复用 DATA-MODEL §5 + §2 Token）
- **越权解析键** `Token.Key(key)`（去 `sk-` 解析）→ 上下文 `token_id`；`token_id==0` 判无效。
- **读** `Log`：按 `TokenId(token_id)`（`default:0;index`）过滤返回该 token 日志数组。
- **返回明细字段** `Log.Quota(quota)`、`Log.ModelName(model_name)`、`Log.CreatedAt(created_at)`、`Log.PromptTokens(prompt_tokens)`、`Log.CompletionTokens(completion_tokens)`。

### 6. 验收标准
- [ ] 解析失败导致 `token_id==0` → 返回「无效的令牌」，不查日志表。
- [ ] 有效 key 且该 token 无 Log → 返回空数组，不报错。
- [ ] 有效 key 且该 token 有日志 → 返回的每条 `TokenId` 均等于该 token 的 id。
- [ ] 高频连续请求 → 命中 `CriticalRateLimit`，返回限流稍后重试。
- [ ] 响应头含禁缓存（DisableCache），不返回浏览器/中间缓存的旧数据。

### 7. 所触及页面状态（对齐 §E「按 key 查日志」）
限流拦截态（CriticalRateLimit）· 无效令牌态（token_id==0）· 空数组态（该 token 无消费记录）· 日志数组返回态（DisableCache 不缓存）。

---

## UL-5 审计日志记录与读取（管理/用户安全/登录三类埋点汇流）

- **功能 ID / 优先级**：F-4011、F-4012、F-4013 / P0（F-4011）/ P1（F-4012/F-4013）
- **来源**：FC-105（`controller/audit.go recordManageAudit/recordUserSecurityAudit` → `model.RecordOperationAuditLog`、`model/log.go RecordLoginLog Type=LogTypeLogin(7)`、`auditContentTemplates` 英文模板渲染）
- **角色 / Owner**：管理员（高危资源操作）/ 登录用户（passkey 安全操作）/ 系统埋点（登录）；Owner 模块 = 日志与用量
- **触发**：系统侦测到敏感操作（管理操作 / 用户安全操作 / 登录成功）

### 1. 场景
三种来源的审计写入汇入同一落库通道，但按来源装填不同字段集。管理/高危资源操作（改系统设置、改渠道、生成兑换码）记 `Type=3(Manage)`，含操作者四字段（admin_id/admin_username/admin_role/auth_method）与 client IP，`content` 由 `auditContentTemplates` 英文渲染（未登记的 action 退回 action 本身）；用户安全操作（passkey 绑定/解绑）的 `adminInfo=nil`，归属用户自身，不依赖 AdminAuth 兜底；登录成功记 `Type=7(Login)`，含 username 与 IP。读取时按 type 分流：管理员见全部、用户仅见本人安全/登录审计。

### 2. 前置条件
- 管理操作经 `recordManageAudit`，需取得操作者身份（admin_id/role/auth_method）。
- 用户安全操作经 `recordUserSecurityAudit`，显式传 `adminInfo=nil`，归属 `userId`。
- 登录成功后系统调 `RecordLoginLog` 埋点。

### 3. 主流程（对应 UL-5 节点 E0→ROK/UOK）
1. 系统侦测到敏感操作（E0），判操作来源类型（E1）。
2. 管理/高危资源操作→`recordManageAudit`（MG1），判 action 是否在 `auditContentTemplates`（MG2）：在→渲染英文 content（MG3）；不在→content 退回 action 本身（MG4）。两路合流写 `Type=3` 含 admin_id/role/auth_method/IP（MG5）。
3. 用户安全（passkey 绑定/解绑）→`recordUserSecurityAudit` `adminInfo=nil`（US1）→渲染 `Registered/Deleted a passkey` 归属 userId（US2）→写 `Type=3` 无 admin_info（US3）。
4. 登录成功→`RecordLoginLog`（LG1）→写 `Type=7` 含 username + client IP + action/params（LG2）。
5. 三路落库审计记录（RD），判读取者身份（RDQ）：管理员→全量审计可见态按 type 区分（ROK）；普通用户→仅本人安全/登录审计可见态（UOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| action 在模板表 | MG2-是 | 渲染英文 content（如 Updated system setting key） | 可读审计描述 |
| action 未登记 | MG2-否 | content 退回 action 本身字符串 | 原始 action 文案 |
| 用户安全操作 | E1-用户安全 | `adminInfo=nil` 归属 userId | 写入无 admin_info 字段 |
| 普通用户读审计 | RDQ-普通用户 | 仅返回本人安全/登录审计 | 看不到他人/管理审计 |

### 5. 数据对象（复用 DATA-MODEL §5）
- **写** `Log`：`Type(type)`（管理/安全=3 Manage、登录=7 Login）、`Content(content)`（模板英文渲染 / 退回 action）、`Username(username)`（登录审计）、`Ip(ip)`（client IP）、`UserId(user_id)`（用户安全审计归属）、`Other(other)`（admin 身份四字段 / action+params JSON）。
- **读分流键** `Log.Type(type)` 区分管理(3)/登录(7)；`Log.UserId(user_id)` 限定普通用户只见本人。

### 6. 验收标准
- [ ] 管理员改系统设置 → 落 `Type=3` 一条，`Content` 为模板渲染英文，`Other` 含 admin_id/admin_username/admin_role/auth_method 与 client IP。
- [ ] action 不在 `auditContentTemplates` → `Content` 等于该 action 字符串本身（不渲染失败为空）。
- [ ] 用户绑定 passkey → 落 `Type=3` 一条，`UserId` 为该用户，无 admin_info 字段，`Content` 为 `Registered a passkey`。
- [ ] 用户登录成功 → 落 `Type=7` 一条，含登录 `Username` 与 `Ip`。
- [ ] 普通用户读审计 → 仅返回自身 `UserId` 的安全/登录审计，无 `Type=3` 管理操作或他人记录。
- [ ] 管理员读审计 → 可按 `Type` 区分查看管理(3)/登录(7)全量记录。

### 7. 所触及页面状态（对齐 §E「审计日志」）
管理操作 action 已登记态（英文模板渲染）· 管理操作 action 未登记态（退回 action 本身）· 管理审计写入态（Type=3 含 admin 身份四字段）· 用户安全审计写入态（adminInfo=nil 归属本人）· 登录审计写入态（Type=7 含 username+IP）· 管理员全量审计可见态（按 type 区分）· 用户仅本人审计可见态。

---

## UL-6 用量排行榜快照（公开 + period 校验 + 模块开关）

- **功能 ID / 优先级**：F-4010 / P2
- **来源**：FC-104（`controller/rankings.go GetRankings` 调 `service.GetRankingsSnapshot(c.DefaultQuery("period","week"))`、`api-router.go GET("/rankings") HeaderNavModuleAuth("rankings")`）
- **角色 / Owner**：匿名访客 / 登录用户（受模块开关）；Owner 模块 = 日志与用量
- **触发**：公开页或控制台请求 `GET /api/rankings`

### 1. 场景
用量排行榜以快照形式对外展示按用量排序的榜单，匿名与登录用户皆可访问，但受 `HeaderNavModuleAuth("rankings")` 模块开关控制可见性——开关关闭则入口隐藏。`period` 默认 `week`，仅接受合法枚举（week/month），非法值返回 400 + 错误信息。这是一条读取流，含模块开关闸门与枚举校验两道关卡，终态为榜单可视化态与暂无快照空态。

### 2. 前置条件
- 受 `HeaderNavModuleAuth("rankings")` 模块开关控制是否可见。
- `period` 默认值为 `week`（`c.DefaultQuery`）。
- 快照由后台周期生成，读取时不实时计算。

### 3. 主流程（对应 UL-6 节点 R0→ROK）
1. 请求 `GET /api/rankings`（R0），判 `HeaderNavModuleAuth rankings` 模块是否已开（R1）。
2. 关闭→模块未开放入口不可见（RE0）；开启→取 period 默认 week（R2）。
3. 判 period 是否属合法枚举 week/month（R3）：否→400 非法 period 错误信息（RE1）；是→`GetRankingsSnapshot period`（R4）。
4. 判快照是否已生成（R5）：否→暂无快照空态等待生成（REMP）；是→排行榜可视化态按用量排序（ROK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| rankings 模块关闭 | R1-关闭 | 入口隐藏，不暴露接口 | 模块未开放态 |
| period 非法（非 week/month） | R3-否 | 400 + 错误信息 | 非法 period 态 |
| 快照尚未生成 | R5-否 | 返回空 | 暂无快照空态 |
| 合法/默认 period 且快照已生成 | R5-是 | 返回排序快照 | 排行榜可视化态 |

### 5. 数据对象（复用 DATA-MODEL §5，排行快照源自 Log Consume 聚合）
- **聚合源** `Log`：`Type(type)=2(Consume)` 的 `Quota(quota)` 按 `UserId(user_id)`/`Username(username)` 维度求和排序得快照。
- **入参** `period`（默认 `week`，合法枚举 `week/month`）；模块开关 `HeaderNavModuleAuth("rankings")`。

### 6. 验收标准
- [ ] rankings 模块开关关闭 → 入口隐藏，请求不返回快照数据。
- [ ] 不传 period → 按默认 `week` 返回周榜快照。
- [ ] 传非法 period（如 `quarter`）→ 返回 400 + 错误信息，不返回快照。
- [ ] 传合法 `period=month` 且快照已生成 → 返回按用量排序的月榜。
- [ ] 快照尚未生成 → 返回暂无快照空态，不报 500。

### 7. 所触及页面状态（对齐 §E「排行榜快照」）
模块未开放态（HeaderNavModuleAuth 关闭，入口隐藏）· period 非法态（400 错误信息）· 暂无快照空态（未生成）· 排行榜可视化态（period 默认 week，按用量排序）。

---

## UL-7 历史日志清理（按目标时间戳分批删除，每批上限 100）

- **功能 ID / 优先级**：F-4006 / P1
- **来源**：FC-101（`controller/log.go DeleteHistoryLogs` 校验 `target_timestamp!=0` 调 `model.DeleteOldLog(ctx, targetTimestamp, 100)`、`logRoute DELETE / AdminAuth`）
- **角色 / Owner**：管理员；Owner 模块 = 日志与用量
- **触发**：管理员在日志维护设目标时间点执行清理

### 1. 场景
日志长期积累需定期清理。管理员设一个目标时间戳，删除所有早于该时间点的日志。为避免单次删除海量数据拖死数据库，`DeleteOldLog` 每批最多删 100 条，返回本批删除 count。若本批删满 100，说明区间内可能还有残留，需要管理员再次触发下一批，形成带批次循环的维护流。`target_timestamp==0` 视为未指定，直接报参数缺失。

### 2. 前置条件
- 管理员会话过 C1 + AdminAuth（`logRoute DELETE /`）。
- 请求需带 `target_timestamp`（非 0）。
- 单批删除上限固定为 100 条。

### 3. 主流程（对应 UL-7 节点 X0→XOK）
1. 管理员设目标时间点执行清理（X0），判 `target_timestamp == 0`（X1）。
2. 是→「target timestamp is required」（XE1）；否→`DeleteOldLog` 删早于 target 的日志（X2）。
3. 单批最多删 100 条（X3），判本批删除 `count == 100`（X4）。
4. 是（可能还有残留）→提示可再次执行清理下一批（X5）→等待管理员再次触发（X6）→回到 X2。
5. 否（< 100，已清空区间）→清理完成态返回累计删除 count（XOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `target_timestamp == 0` | X1-是 | 拒绝清理 | 「target timestamp is required」 |
| 本批删除 `count == 100` | X4-是 | 区间可能未清空 | 引导再次执行下一批 |
| 本批删除 `count < 100` | X4-否 | 区间已清空 | 清理完成态返回 count |

### 5. 数据对象（复用 DATA-MODEL §5）
- **删除条件键** `Log.CreatedAt(created_at)`：删除 `created_at < target_timestamp` 的行。
- **批次上限** 固定 `100`（`DeleteOldLog(ctx, targetTimestamp, 100)`），单次删除受限。
- **返回** `data = 本批删除 count`（用于判定是否需继续循环）。

### 6. 验收标准
- [ ] 传 `target_timestamp=0` → 返回「target timestamp is required」，不执行删除。
- [ ] 区间内有 250 条早于 target 的日志，单次调用 → 仅删 100 条，返回 `count=100`。
- [ ] 上一步返回 100 → 系统引导可再次执行；再调用删下一批 100 条。
- [ ] 区间剩余 50 条时调用 → 返回 `count=50（<100）`，进入清理完成态。
- [ ] 删除只影响 `created_at < target_timestamp` 的行，晚于 target 的日志保留。

### 7. 所触及页面状态（对齐 §E「历史日志清理」）
设目标时间点表单态 · 缺少时间戳态（target_timestamp==0）· 单批删除进行态（每批 ≤100）· 仍有残留可再执行态（本批满 100，引导继续）· 清理完成态（本批 <100，返回删除 count）。

---

## UL-8 渠道亲和缓存用量统计（管理端只读监控）

- **功能 ID / 优先级**：F-4014 / P2
- **来源**：FC-105（`api-router.go logRoute.GET("/channel_affinity_usage_cache") AdminAuth` 调 `controller.GetChannelAffinityUsageCacheStats`）
- **角色 / Owner**：管理员；Owner 模块 = 日志与用量
- **触发**：管理员进入亲和缓存监控页

### 1. 场景
渠道会话亲和缓存（CH-4 粘连机制）会累积每个会话键命中渠道的用量数据。管理员需要一个只读监控点查看这些 `channel_affinity_usage_cache` 统计，用于诊断缓存粘连是否健康、亲和数据分布如何。该接口挂在 logRoute 分组下受 AdminAuth 保护，是一个单一只读监控点，短链，终态为缓存命中可视化态与缓存为空态。

### 2. 前置条件
- 管理员会话过 C1 + AdminAuth（`logRoute` 分组保护）。
- 调用 `GetChannelAffinityUsageCacheStats` 读取内存缓存统计。

### 3. 主流程（对应 UL-8 节点 AC0→ACOK）
1. 管理员进入亲和缓存监控页（AC0），判是否有 AdminAuth（AC1）。
2. 否→无权限 403（ACE0）；是→`GetChannelAffinityUsageCacheStats` 读统计（AC2）。
3. 判缓存内是否有亲和用量记录（AC3）：无→缓存为空态暂无亲和数据（ACEMP）；有→亲和缓存用量统计可视化态（ACOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 非 AdminAuth | AC1-否 | 拒绝访问 | 403 无权限态 |
| 缓存内无亲和用量记录 | AC3-无 | 返回空 | 缓存为空态（暂无亲和数据） |
| 缓存内有记录 | AC3-有 | 返回统计数据 | 亲和缓存用量统计可视化态 |

### 5. 数据对象（复用 DATA-MODEL §3 Channel + 亲和缓存运行态）
- **统计载体**：`channel_affinity_usage_cache`（内存缓存，键为会话键 → 命中渠道用量）。
- **关联渠道键** `Channel.Id(id)`：缓存内记录会话键粘连到的目标渠道。
- **保护闸门**：AdminAuth（`logRoute` 分组）。

### 6. 验收标准
- [ ] 非 AdminAuth 调用 `GET /log/channel_affinity_usage_cache` → 返回 403。
- [ ] 缓存内无亲和用量记录 → 返回缓存为空态，不报错。
- [ ] 缓存内有会话键命中记录 → 返回 `channel_affinity_usage_cache` 统计数据。
- [ ] 该接口仅在 logRoute 分组下暴露，受 AdminAuth 保护。

### 7. 所触及页面状态（对齐 §E「亲和缓存统计」）
无权限态（403，非 AdminAuth）· 缓存为空态（暂无亲和用量数据）· 亲和缓存用量统计可视化态。

---

## UL-9 调用明细逐条展示（每次调用一行 + 可展开详情 + 三段模型字段 C/A/B）

- **功能 ID / 优先级**：F-4015 / P0
- **来源**：COMPAT-BILLING-DECISIONS §8（Log 三段式 + 协议 + 成本售价）、COMPAT-LAYER-DATA-OBJECTS §3（三段模型字段口径 C/A/B）、BILLING-DATA-OBJECTS §3（售价/成本/利润金额字段）、DATA-MODEL §5 Log（已落 10 新字段含 `user_agent`/`ip`）；在 UL-1 列表查询之上做「逐条调用明细行」展示增强
- **角色 / Owner**：管理员（全字段）/ 登录用户（裁剪后字段）；Owner 模块 = 日志与用量
- **触发**：用户/管理员进入用量调用明细列表，按行查看每次调用，点行展开详情

### 1. 场景
调用明细以**每次调用一行**逐条展示，主行字段固定为：时间（`CreatedAt`）/ 类型（`Type`，2=Consume）/ 模型（按视图显 C→A 或 C→A→B）/ 分组（`Group`，折扣等级）/ 计费模式（按倍率 or 按次，源自 PublicModel `UsePrice`）/ Tokens（`PromptTokens`+`CompletionTokens`）/ 费用（`quota`，= `quota_sell`，客户视角为实付）/ IP（`Ip`）/ User-Agent（`UserAgent`）/ 耗时（`UseTime`）/ 状态（成功/错误，区分 `Type=2` 与 `Type=5 Error`）。点击行可**展开详情**：补充 `RequestId`/`UpstreamRequestId`、入站/上游协议（`inbound_protocol`/`upstream_protocol`）、是否协议转换（`protocol_converted`）、完整三段模型链；管理侧详情还含 B + 实际供应商渠道 + 成本/利润。**三段模型字段口径**（DECISIONS §2/§8）：`requested_model(C)` = 客户实际输入名（客户可见）、`resolved_public_model(A)` = 平台公开名（客户可见、定价键）、`actual_upstream_model(B)` = 真实上游模型名（**客户绝不可见，仅 admin/root**）。`model_name` 保留为 C 不破坏现网报表。展示字段按角色由 UL-10 视图 DTO 裁剪决定。

### 2. 前置条件
- 列表数据源 = `Log`（`Type=2 Consume` 为主，`Type=5 Error` 计入状态列），复用 UL-1 的范围闸门（管理端全量 / 自助本人）。
- 主行/详情可展示字段由调用者角色经 UL-10 `UserLogView`/`AdminLogView` 裁剪后给出（B、成本、利润字段不出现在用户视图）。
- 三段模型字段、协议字段、金额字段以 `DATA-MODEL §5 Log` 已落 10 字段为准，明细行不新增 DB 列。

### 3. 主流程（对应 UL-9 节点 DT0→DTOK）
1. 进入调用明细列表（DT0），按角色经 UL-10 选视图 DTO（DT1）。
2. 逐条渲染主行（DT2）：时间/类型/模型/分组/计费模式/Tokens/费用/IP/UA/耗时/状态；模型列按视图显 C→A（用户）或 C→A→B（管理）。
3. 用户点击某行展开详情（DT3）：补 RequestId/协议字段/protocol_converted/完整三段链；判调用者是否 admin（DT4）。
4. 是 admin→详情含 B + 实际供应商渠道 + `quota_cost`/`quota_profit`（DT5a）；是普通用户→详情仅 C→A + `quota_sell`（实付），不含 B/成本/利润（DT5b）。
5. 两路汇到调用明细可视化态（DTOK）；区间内无调用→空态占位（DTEMP）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 该行为错误调用 | DT2 | 状态列标错误（`Type=5 Error`），费用为 0 | 错误状态行 |
| 计费模式 = 按次 | DT2 | 计费模式列显「按次/按量」（PublicModel UsePrice=true） | 按次计费行 |
| 普通用户展开详情 | DT4-否 | 仅给 C→A + 实付费用，裁掉 B/成本/利润 | 客户视图详情（无 B） |
| 管理员展开详情 | DT4-是 | 全链 C→A→B + 供应商 + 成本/利润 | 管理视图详情（全字段） |
| 区间内无调用 | DT2 | 返回空列表占位 | 调用明细空态 |

### 5. 数据对象（复用 DATA-MODEL §5 Log）
- **主行字段** `Log`：`CreatedAt(created_at)`（时间）、`Type(type)`（类型，2=Consume/5=Error 状态）、`RequestedModel(requested_model)=C`/`ResolvedPublicModel(resolved_public_model)=A`（模型列；管理侧加 `ActualUpstreamModel(actual_upstream_model)=B`）、`Group(group)`（分组）、`PromptTokens`+`CompletionTokens`（Tokens）、`Quota(quota)`（费用，=`quota_sell`）、`Ip(ip)`（IP）、`UserAgent(user_agent)`（User-Agent）、`UseTime(use_time)`（耗时）。
- **详情扩展字段** `Log`：`RequestId(request_id)`、`UpstreamRequestId(upstream_request_id)`、`InboundProtocol(inbound_protocol)`、`UpstreamProtocol(upstream_protocol)`、`ProtocolConverted(protocol_converted)`；管理侧详情加 `ChannelId(channel)`+`ChannelName(channel_name)`、`QuotaCost(quota_cost)`、`QuotaProfit(quota_profit)`。
- **三段模型口径**：`model_name`=C（兼容现网）；`requested_model(C)`/`resolved_public_model(A)` 客户可见；`actual_upstream_model(B)` 仅 admin/root。

### 6. 验收标准
- [ ] 调用明细每次调用展示为一行，主行含 时间/类型/模型/分组/计费模式/Tokens/费用/IP/User-Agent/耗时/状态。
- [ ] 点击行可展开详情，补 RequestId/协议字段/protocol_converted/完整三段模型链。
- [ ] 用户视角行/详情的费用列 = `quota_sell`（实付），模型列只显 C→A，无 B、无成本、无利润。
- [ ] 管理视角行/详情显示全链 C→A→B + 实际供应商渠道 + `quota_cost`/`quota_profit`。
- [ ] IP 取 `Log.Ip`、User-Agent 取 `Log.UserAgent`、耗时取 `Log.UseTime` 逐条正确展示。
- [ ] 错误调用（`Type=5`）状态列标错误，费用为 0；计费模式列正确区分「按倍率/按次」。

### 7. 所触及页面状态（对齐 §E「调用明细」）
调用明细主行列表态（每次调用一行）· 行展开详情态（RequestId/协议/三段链）· 客户视图详情态（C→A+实付，无 B/成本/利润）· 管理视图详情态（C→A→B+供应商+成本/利润）· 错误状态行态（Type=5）· 按次计费行态 · 调用明细空态（区间内无调用）。

---

## UL-10 两侧视图裁剪（管理侧全局全链 vs 客户侧本人 C→A 实付；B/成本/利润绝不暴露）

- **功能 ID / 优先级**：F-4016 / P0
- **来源**：COMPAT-BILLING-DECISIONS §8（客户日志只显 C→A、看不到 B 与成本；超管显全链 + 供应商 + 成本/售价/利润）+ §2 B 不可见三道闸、COMPAT-LAYER-DATA-OBJECTS §3.1（UserLogView/AdminLogView DTO）、BILLING-DATA-OBJECTS §3.1（视图裁成本/利润）、COMPAT-LAYER-ARCHITECTURE §5（B 不可见序列化层闸门）
- **角色 / Owner**：管理员（AdminLogView 全字段）/ 登录用户（UserLogView 裁剪）；Owner 模块 = 日志与用量
- **触发**：UL-1/UL-9 任一日志/明细接口按调用者角色选 DTO 序列化返回

### 1. 场景
同一份 `Log` 按调用者角色走两套**序列化层裁剪 DTO**，是「客户绝对看不到真实上游模型 B / 成本 / 利润 / 供应商」的核心闸门（不靠前端隐藏）：
- **管理侧 `AdminLogView`（全局 + 全字段）**：看全站所有人记录，含**全链 C→A→B**（`requested_model`/`resolved_public_model`/`actual_upstream_model`）+ 实际供应商渠道（`channel`/`channel_name`）+ 协议字段 + **售价/成本/利润**（`quota_sell`/`quota_cost`/`quota_profit`），是利润看板数据源。
- **客户侧 `UserLogView`（仅本人 + 裁剪）**：只看自己（`user_id` 强制等于当前用户，复用 UL-1 范围闸门）、模型链**只显 C→A**（裁掉 `actual_upstream_model(B)`）、费用**只显实付**（`quota`=`quota_sell`，裁掉 `quota_cost`/`quota_profit`），并不暴露供应商渠道。

**红线**：客户端绝不暴露真实上游模型 B / 成本 / 利润 / 供应商——否则客户看到「我点的 opus，实际调了别的便宜模型」会被当成**模型造假**投诉。裁剪发生在序列化层（DTO 按角色选），与「数据层 `actual_upstream_model` 不进 UserAuth 查询路径」「输入候选层不含 B」共同构成 B 不可见三道闸（ARCHITECTURE §5）。

### 2. 前置条件
- 调用者角色经 `AdminAuth`/`RootAuth`（→`AdminLogView`）或 `UserAuth`（→`UserLogView`）鉴别。
- 用户侧范围强制 `Log.UserId = 当前用户`（复用 UL-1，自助接口不接受跨用户维度）。
- DTO 字段裁剪在序列化层完成，不依赖前端隐藏；`actual_upstream_model`/`quota_cost`/`quota_profit` 不出现在 `UserLogView` 输出结构。

### 3. 主流程（对应 UL-10 节点 VW0→VWOK）
1. 日志/明细接口取得记录集（VW0），判调用者角色（VW1）。
2. AdminAuth/RootAuth→选 `AdminLogView`（VW2a）：序列化全链 C→A→B + 供应商渠道 + 协议 + 售价/成本/利润，范围=全站所有人。
3. UserAuth→选 `UserLogView`（VW2b）：强制 `user_id=当前用户`，序列化仅 C→A + `quota_sell`（实付），**结构级剔除** B/成本/利润/供应商。
4. 两路汇到按角色裁剪后的视图返回态（VWOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| AdminAuth/RootAuth | VW1-管理 | `AdminLogView` 全字段全站 | 全链 C→A→B + 供应商 + 售价/成本/利润 |
| UserAuth | VW1-用户 | `UserLogView` 裁剪 + 强制本人 | 仅本人、仅 C→A、仅实付费用 |
| 用户侧响应含 B/成本/利润 | VW2b | 视为闸门破坏（不允许） | 测试拦截：UserLogView 输出无该字段 |
| 用户尝试越权查他人 | VW2b | `user_id` 强制本人，跨用户维度忽略（复用 UL-1） | 仅返回本人记录 |

### 5. 数据对象（复用 DATA-MODEL §5 Log + COMPAT-LAYER-DATA-OBJECTS §3.1 + BILLING-DATA-OBJECTS §3.1）
- **`AdminLogView`（全字段）**：`requested_model(C)` + `resolved_public_model(A)` + `actual_upstream_model(B)` + `inbound_protocol`/`upstream_protocol`/`protocol_converted` + `channel`/`channel_name`（实际供应商）+ `quota_sell`/`quota_cost`/`quota_profit` + `ip`/`user_agent`/`use_time`。
- **`UserLogView`（裁剪）**：`requested_model(C)` + `resolved_public_model(A)` + `quota`(=`quota_sell` 实付) + `ip`/`user_agent`/`use_time`；**无** `actual_upstream_model(B)`、**无** `quota_cost`、**无** `quota_profit`、**无** 供应商 `channel`/`channel_name`。
- **范围键** `Log.UserId(user_id)`：`UserLogView` 强制 `=当前用户`（复用 UL-1）。

### 6. 验收标准
- [ ] AdminAuth/RootAuth 调用 → 返回 `AdminLogView`，含全链 C→A→B + 实际供应商渠道 + `quota_sell`/`quota_cost`/`quota_profit`，范围覆盖全站所有人。
- [ ] UserAuth 调用 → 返回 `UserLogView`，仅本人记录，模型链只含 C→A，费用只含 `quota_sell`（实付）。
- [ ] `UserLogView` 序列化结构中**不存在** `actual_upstream_model(B)`、`quota_cost`、`quota_profit`、供应商 `channel`/`channel_name` 字段（结构级剔除，非前端隐藏）。
- [ ] 用户无法通过任何 UserAuth 日志/明细接口拿到 B、成本、利润或真实供应商（B 不可见序列化层闸门）。
- [ ] 用户侧强制 `user_id=当前用户`，跨用户维度被忽略，不返回他人记录。

### 7. 所触及页面状态（对齐 §E「两侧视图裁剪」）
管理侧全字段视图态（AdminLogView：全站 + 全链 C→A→B + 供应商 + 售价/成本/利润）· 客户侧裁剪视图态（UserLogView：本人 + C→A + 实付）· B 不可见序列化闸门态（结构级剔除 B/成本/利润/供应商）· 越权维度忽略态（强制本人 user_id）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-4001 | UL-1 |
| F-4002 | UL-1 |
| F-4003 | UL-4 |
| F-4004 | UL-2 |
| F-4005 | UL-2 |
| F-4006 | UL-7 |
| F-4007 | UL-3 |
| F-4008 | UL-3 |
| F-4009 | UL-3 |
| F-4010 | UL-6 |
| F-4011 | UL-5 |
| F-4012 | UL-5 |
| F-4013 | UL-5 |
| F-4014 | UL-8 |
| F-4015 | UL-9 |
| F-4016 | UL-10 |

无 `[BLOCKER]`。
