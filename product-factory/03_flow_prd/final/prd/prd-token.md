# PRD — 令牌管理（FL-token）

> 分片：令牌管理 D3。对应流程图 `flow/FL-token.md`、状态矩阵 `PAGE-STATE-MATRIX.md §D`。
> 数据对象字段一律复用 `DATA-MODEL.md §2 Token`。
> 本片覆盖功能 ID：**F-3001 / F-3002 / F-3003 / F-3004 / F-3005 / F-3006 / F-3007 / F-3008 / F-3009 / F-3010 / F-3011 / F-3012**。

---

## TK-1 创建令牌（名称/令牌数/额度三重串行校验）

- **功能 ID / 优先级**：F-3001、F-3008 / P0
- **来源**：FC-073（`controller/token.go AddToken`、`GenerateKey()`、`maxQuotaValue=1000000000*QuotaPerUnit`、`operation_setting.GetMaxUserTokens()`）
- **角色 / Owner**：登录用户（令牌拥有者）；Owner 模块 = 令牌
- **触发**：用户在控制台点「新建令牌」，填 name/quota/expired/unlimited 后提交（过 C1 UserAuth）

### 1. 场景
登录用户为对接外部客户端创建一把 API 调用 key，需要在一次提交里决定额度模式（无限/有限）、有效期、名称。系统必须在生成 key 前把三类非法输入（名称过长、令牌数超限、额度越界）逐关拦截，通过后返回一把唯一的 `sk-` 明文 key 供一次性复制。

### 2. 前置条件
- 用户已登录且会话有效（过 C1 UserAuth）。
- 当前用户已有令牌数 < `operation_setting.GetMaxUserTokens()`。
- 若 `UnlimitedQuota=false`，填写的 `RemainQuota` 落在 `[0, 10亿*QuotaPerUnit]`。

### 3. 主流程（对应 TK-1 节点 C0→COK）
1. 用户提交表单 name/quota/expired/unlimited（C0→C1）。
2. 校验 `Name` 长度（C2）：≤50 字符放行。
3. 校验当前令牌数（C3）：`< maxUserTokens` 放行。
4. 判 `UnlimitedQuota`（C4）：true→跳过额度校验直接到第 7 步；false→进额度区间校验。
5. 校验 `RemainQuota >= 0`（C5）。
6. 校验 `RemainQuota <= 10亿*QuotaPerUnit`（C6）。
7. `GenerateKey()` 生成唯一 `sk-` 前缀 key（C8）。
8. 写入 Token 表，`ExpiredTime` 默认 -1（永不过期）（C9）。
9. 返回创建成功，展示打码 key（COK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| Name 长度 >50 | C2-是 | 拒绝创建，不生成 key | 提示 `MsgTokenNameTooLong` |
| 当前令牌数 ≥ maxUserTokens | C3-是 | 拒绝创建 | 提示「已达到最大令牌数量限制(N)」 |
| RemainQuota < 0（非无限） | C5-是 | 拒绝创建 | 提示 `MsgTokenQuotaNegative` |
| RemainQuota > 10亿*QuotaPerUnit（非无限） | C6-是 | 拒绝创建 | 提示 `MsgTokenQuotaExceedMax` |
| UnlimitedQuota=true | C4-是 | 跳过 C5/C6 额度校验 | 表单提示「无限额度，不校验上下限」 |

### 5. 数据对象（复用 DATA-MODEL §2）
- **写** `Token`：`Key(key)`（GenerateKey 生成 `sk-` 唯一明文）、`Name(name)`（≤50）、`RemainQuota(remain_quota)`、`UnlimitedQuota(unlimited_quota)`、`ExpiredTime(expired_time)`（default -1）、`UserId(user_id)`（当前用户）、`Status(status)`=1。
- **读** 配置：`operation_setting.GetMaxUserTokens()`、常量 `QuotaPerUnit`、`maxQuotaValue=1000000000*QuotaPerUnit`。

### 6. 验收标准
- [ ] Name=51 字符 → 不写 Token 表 + 返回 `MsgTokenNameTooLong`。
- [ ] 当前令牌数=maxUserTokens 再创建 → 拒绝 + 文案含上限数字 N。
- [ ] UnlimitedQuota=false 且 RemainQuota=-1 → 拒绝 + `MsgTokenQuotaNegative`。
- [ ] UnlimitedQuota=false 且 RemainQuota=10亿*QuotaPerUnit+1 → 拒绝 + `MsgTokenQuotaExceedMax`。
- [ ] UnlimitedQuota=true 且不填额度 → 创建成功（跳过 C5/C6）。
- [ ] 创建成功 → 返回 key 以 `sk-` 开头、全局唯一、`ExpiredTime=-1`，列表展示为打码态。

### 7. 所触及页面状态（对齐 §D「新建令牌表单」）
表单默认态（额度/有效期/无限开关）· 名称过长态 · 令牌数达上限态 · 额度为负态 · 额度超上限态 · 无限额度跳过校验提示态 · 创建成功返回打码 key 态。

---

## TK-2 令牌生命周期状态机（启用/禁用/过期/耗尽/删除）

- **功能 ID / 优先级**：F-3006、F-3007 / P0
- **来源**：`controller/token.go`（`status_only` 分支、`MsgTokenExpiredCannotEnable`、`MsgTokenExhaustedCannotEable`、gorm `DeletedAt` 软删除）
- **角色 / Owner**：令牌拥有者；Owner 模块 = 令牌
- **触发**：用户在列表对某令牌点「禁用/启用/删除」

### 1. 场景
令牌有多种生命态：可调用的启用态、被手动关掉的禁用态、时间到的过期态、额度用尽的耗尽态、软删除态。用户切换状态时，系统须用守卫条件挡住「把一把已过期或已耗尽的令牌重新启用」这类无效迁移。

### 2. 前置条件
- 用户已登录，且操作目标令牌 `UserId` = 当前用户（越权过滤）。
- 状态切换走 `status_only` 分支时仅改 `Status`，不覆盖其他字段。

### 3. 主流程（对应 TK-2 状态机迁移）
1. 创建成功 → Enabled。
2. Enabled --`status_only` 置禁用--> Disabled。
3. Disabled --启用[未过期 且 (有额度 或 无限)]--> Enabled。
4. Enabled --时间到 `ExpiredTime<=now`--> Expired。
5. Enabled --`RemainQuota` 耗尽且非无限--> Exhausted。
6. 任一态 --软删除 `DeletedAt`--> Deleted（列表不再显示）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 启用且已过期（`ExpiredTime<=now 且 ≠-1`） | Disabled→EnableReject | 拒绝启用，状态退回 Disabled | 提示 `MsgTokenExpiredCannotEnable` |
| 启用且额度耗尽且非无限 | Disabled→EnableReject | 拒绝启用，状态退回 Disabled | 提示 `MsgTokenExhaustedCannotEable` |
| 删除 | *→Deleted | gorm `DeletedAt` 软删除（非物理删） | 列表移除该令牌 |

### 5. 数据对象（复用 DATA-MODEL §2）
- **写** `Token.Status(status)`：`1=启用` ↔ 禁用（status_only 切换，不动其他字段）。
- **读守卫** `Token.ExpiredTime(expired_time)`（`-1`=永不过期）、`Token.RemainQuota(remain_quota)`、`Token.UnlimitedQuota(unlimited_quota)`。
- **写删除** `Token.DeletedAt`（软删除）。

### 6. 验收标准
- [ ] 禁用一把令牌 → `Status` 切换为禁用，其余字段（quota/name/group）值不变（status_only）。
- [ ] 对 `ExpiredTime<=now 且 ≠-1` 的禁用令牌点启用 → 拒绝 + `MsgTokenExpiredCannotEnable`，`Status` 仍为禁用。
- [ ] 对 `RemainQuota=0 且 UnlimitedQuota=false` 的禁用令牌点启用 → 拒绝 + `MsgTokenExhaustedCannotEable`。
- [ ] 删除令牌 → `DeletedAt` 被写入，列表查询不再返回该项，DB 行仍存在（软删除）。
- [ ] `UnlimitedQuota=true` 的令牌即使 RemainQuota=0 也可正常启用。

### 7. 所触及页面状态（对齐 §D「令牌生命周期」）
启用态（可调用）· 禁用态（调用被拒）· 启用被拒态（已过期）· 启用被拒态（额度耗尽且非无限）· 已过期态 · 额度耗尽态 · 已删除态（软删，列表不显示）。

---

## TK-3 令牌列表查询 + 关键词搜索（脱敏 + LIKE 注入防护）

- **功能 ID / 优先级**：F-3002、F-3003 / P0
- **来源**：`model/token.go`（`GetAllUserTokens`、`MaskTokenKey`、`sanitizeLikePattern`、`searchHardLimit=100`、`SearchRateLimit`）
- **角色 / Owner**：令牌拥有者；Owner 模块 = 令牌
- **触发**：用户进入令牌管理页（直列表）或输入关键词搜索

### 1. 场景
用户在令牌管理页浏览自己的全部令牌或按名称搜索。系统须保证：列表只返回本人令牌且 key 已脱敏；搜索输入经过 LIKE 注入防护，不允许危险通配符模式，并对结果上限和频率做限制。

### 2. 前置条件
- 用户已登录（过 C1）。
- 列表按 `user_id` 过滤、`id desc` 排序、分页。
- 走搜索分支时先过 `SearchRateLimit`。

### 3. 主流程（对应 TK-3 节点 Q0→QOK）
1. 进入令牌管理页（Q0），判断是否带搜索关键词（Q1）。
2. 无关键词：`GetAllUserTokens(user_id 过滤, id desc 分页)`（Q2）。
3. 有关键词：过 `SearchRateLimit`（S1）→ 模式校验（S2/S3/S4）→ `sanitizeLikePattern` 用 `!` 转义、limit 截断 100（S5）→ `SearchUserTokens` 当前用户范围（S6）。
4. 两路合流，逐项 `MaskTokenKey` 脱敏（Q3）。
5. 结果集为空 → 空列表态（QEMP）；否则 → 打码分页列表态（QOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 关键词含连续 `%%` | S2-是 | 拒绝搜索 | 非法模式提示 |
| 关键词 `%` 数量 >2 | S3-是 | 拒绝搜索 | 通配符过多提示 |
| 含 `%` 且去 % 后关键词 <2 字符 | S4-是 | 拒绝搜索 | 关键词过短提示 |
| `limit>100` | S5 | 强制截断为 100 | 最多返回 100 条 |
| 结果集为空 | Q4-是 | 返回空列表 | 引导「新建令牌」 |

### 5. 数据对象（复用 DATA-MODEL §2）
- **读** `Token`：按 `UserId(user_id)` 过滤、`Id(id)` desc 排序。
- **脱敏** `Token.Key(key)` 经 `MaskTokenKey`：`len≤4` 全 `*`；`len≤8` 保留首尾 2 位；否则 `前4 + ********** + 后4`。
- **搜索目标** `Token.Name(name)`，`searchHardLimit=100`。

### 6. 验收标准
- [ ] A 用户列表查询不返回任何 `UserId≠A` 的令牌。
- [ ] 列表每项 `key` 形如 `abcd**********wxyz`（>8 位时），无明文 key 下发。
- [ ] 搜索 `ab%%cd` → 拒绝（非法模式态），不查询。
- [ ] 搜索含 3 个 `%`（如 `%a%b%`）→ 拒绝（通配符过多态）。
- [ ] 搜索 `%a`（去 % 后仅 1 字符）→ 拒绝（关键词过短态）。
- [ ] 请求 limit=500 → 实际最多返回 100 条。
- [ ] 无令牌用户进入 → 空列表态 + 新建引导，不报错。

### 7. 所触及页面状态（对齐 §D「令牌列表/搜索」）
令牌分页列表态（已脱敏）· 空列表态（引导新建）· 搜索非法模式态 · 搜索通配符过多态 · 搜索关键词过短态 · 搜索被限流态（SearchRateLimit）· 搜索结果分页态（打码）。

---

## TK-4 令牌密钥访问（掩码默认 + 受控取明文 + 批量上限）

- **功能 ID / 优先级**：F-3004、F-3005 / P0
- **来源**：`controller/token.go`（`POST /token/:id/key`、`CriticalRateLimit + DisableCache`、`GetFullKey()`、`GetTokenKeysByIds`、`len(Ids)≤100`）
- **角色 / Owner**：令牌拥有者；Owner 模块 = 令牌
- **触发**：用户在列表点单个令牌「复制完整 key」或「批量导出」

### 1. 场景
列表里 key 默认掩码。用户偶尔需要完整明文去配置客户端。系统提供受控取明文路径：单个取明文要过严格限流且禁缓存，批量导出有数量上限，两条路径都必须按 `userId` 过滤防越权。

### 2. 前置条件
- 用户已登录。
- 单个取明文走 `CriticalRateLimit + DisableCache`。
- 批量 `ids` 非空且 `len(ids)≤100`。

### 3. 主流程（对应 TK-4 节点 K0→UOK/BOK）
1. 列表令牌默认掩码展示（K0），用户选单个或批量（K1）。
2. 单个：`POST /token/:id/key` 过 `CriticalRateLimit+DisableCache`（U1）→ 校验该 id 属当前 userId（U2）→ 返回 `GetFullKey()` 完整明文（U3）→ 单个明文一次性展示（UOK）。
3. 批量：校验 ids 非空（B1）→ 校验 `len≤100`（B2）→ `GetTokenKeysByIds` 带 userId 过滤（B3）→ 组装 id→key 映射（B4）→ 批量导出（BOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 单个取明文，id 非本人（GetTokenByIds 带 userId 取不到） | U2-否 | 拒绝，不返回 key | 越权拒绝提示 |
| 批量 ids 为空 | B1-是 | 拒绝 | `MsgInvalidParams` |
| 批量 `len(ids)>100` | B2-是 | 拒绝 | `MsgBatchTooMany{Max:100}` |

### 5. 数据对象（复用 DATA-MODEL §2）
- **读** `Token.Key(key)`：默认 `GetMaskedKey()`，受控取 `GetFullKey()` 完整明文。
- **越权过滤键** `Token.UserId(user_id)`、`Token.Id(id)`。
- 批量返回结构：`id→key` 映射，仅含本人令牌。

### 6. 验收标准
- [ ] 列表默认每项展示掩码 key，不含明文。
- [ ] 单个取明文：传入非本人令牌 id → 取不到 + 越权拒绝，不泄露任何 key。
- [ ] 单个取明文响应头含禁缓存（DisableCache），过 CriticalRateLimit。
- [ ] 批量 ids=[] → `MsgInvalidParams`。
- [ ] 批量 ids 长度=101 → `MsgBatchTooMany{Max:100}`。
- [ ] 批量含他人令牌 id → 返回 map 中不包含该 id 的 key。

### 7. 所触及页面状态（对齐 §D「令牌密钥访问」）
掩码默认展示态 · 单个越权拒绝态（非本人）· 单个明文一次性展示态（DisableCache，引导立即复制）· 批量参数无效态（ids 空）· 批量超上限态（>100）· 批量 keysMap 导出态（仅本人令牌）。

---

## TK-5 令牌额度策略组合配置（额度/模型/IP/分组四维）

- **功能 ID / 优先级**：F-3008、F-3009、F-3010、F-3011 / P0
- **来源**：`model/token.go`（`UnlimitedQuota`、`GetModelLimitsMap()`、`GetIpLimits()`、`Group`+`CrossGroupRetry`）
- **角色 / Owner**：令牌拥有者；Owner 模块 = 令牌
- **触发**：用户编辑令牌策略表单（过 C1）

### 1. 场景
一把令牌可叠加四个相互独立的限制维度：额度模式、模型白名单、IP 白名单、分组及跨分组重试。每个开关各自落库，最终合成一份调用约束。这是并行配置汇聚，不是线性流程。

### 2. 前置条件
- 用户已登录，编辑本人令牌。
- 四个维度可独立开关，互不阻断保存。

### 3. 主流程（对应 TK-5 节点 P0→POK，四路并行汇聚）
1. 编辑策略表单，并行配置四维（P0→P1）。
2. 额度模式（PA）：无限→`UnlimitedQuota=true` 不校验上下限（PA1）；有限→`RemainQuota` 走 TK-1 区间校验（PA2）。
3. 模型限制（PB）：开启→`ModelLimits` JSON 存 text、`GetModelLimitsMap()` 生成允许表（PB1）；关闭→不限制模型（PB2）。
4. IP 限制（PC）：填了 `AllowIps`→`GetIpLimits()` 按换行切分去空格逗号（PC1）；空/nil→不限制来源 IP（PC2）。
5. 分组（PD）：`Group=auto`→`CrossGroupRetry` 生效跨分组重试（PD1）；普通分组→`CrossGroupRetry` 标志被忽略（PD2）。
6. 四路汇聚合成令牌调用约束（PM）→ 策略保存成功（POK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 结果 |
|---|---|---|---|
| 额度模式=有限 | PA-有限 | `RemainQuota` 走 TK-1 区间校验（复用 §TK-1 的 C5/C6 边界） | 越界则按 TK-1 拒绝 |
| ModelLimitsEnabled=true | PB-开启 | 解析 `ModelLimits` 为允许模型布尔表，白名单外模型调用被拒 | 模型白名单生效 |
| AllowIps 为空/nil | PC-空 | `GetIpLimits()` 返回空，不限制来源 IP | 不做 IP 校验 |
| Group≠auto 但勾了 CrossGroupRetry | PD-普通分组 | `CrossGroupRetry` 标志被忽略 | 重试不跨分组 |

### 5. 数据对象（复用 DATA-MODEL §2）
- **写** `Token`：`UnlimitedQuota(unlimited_quota)`、`RemainQuota(remain_quota)`、`ModelLimitsEnabled(model_limits_enabled)`、`ModelLimits(model_limits)`（text JSON）、`AllowIps(allow_ips)`（`*string`，按 `\n` 切分）、`Group(group)`、`CrossGroupRetry(cross_group_retry)`。
- **解析方法** `GetModelLimitsMap()`（允许模型布尔表）、`GetIpLimits()`（IP 列表）。

### 6. 验收标准
- [ ] `UnlimitedQuota=true` 保存后，调用不受 RemainQuota 上下限约束。
- [ ] `ModelLimitsEnabled=true` 且 `ModelLimits=["gpt-4o"]` → 调用 `gpt-3.5` 被拒、调用 `gpt-4o` 放行。
- [ ] `AllowIps` 填两行 IP → `GetIpLimits()` 返回 2 个去空格的 IP；`AllowIps` 留空 → 返回空列表、任意 IP 可调。
- [ ] `Group=auto` 且 `CrossGroupRetry=true` → 调用可跨分组重试；`Group=default` 且 `CrossGroupRetry=true` → 不跨分组重试。
- [ ] 四维任一独立修改不影响其他三维已保存值。

### 7. 所触及页面状态（对齐 §D「令牌额度策略」）
策略配置表单态（四区）· 无限额度态 · 有限额度态 · 模型白名单启用态 · 模型不限制态 · IP 白名单生效态 · IP 不限制态 · 分组 auto+跨组重试态 · 普通分组态 · 策略保存成功态（四维约束合成）。

---

## TK-6 令牌用量查询（OpenAI 兼容 credit_summary，外部 Bearer 鉴权）

- **功能 ID / 优先级**：F-3012 / P1
- **来源**：`controller/token.go`（`GET /usage/token`、`TokenAuthReadOnly`、`GetTokenStatus`）
- **角色 / Owner**：外部客户端（携 key 调用）；Owner 模块 = 令牌
- **触发**：外部客户端 `GET /usage/token` 携 `Authorization: Bearer sk-xxx`

### 1. 场景
外部客户端（无会话）用令牌 key 查询自身用量额度，接口形态对齐 OpenAI 的 credit_summary。无会话，靠 Bearer 头解析 key，鉴权失败按 HTTP 码拒绝。

### 2. 前置条件
- 请求携带 `Authorization` 头，格式为 `Bearer sk-...`。
- 经 `TokenAuthReadOnly` 只读鉴权。

### 3. 主流程（对应 TK-6 节点 G0→GOK）
1. 外部客户端 `GET /usage/token`（G0），校验有 `Authorization` 头（G1）。
2. 校验 Bearer 格式且 `sk-` 前缀（G2）。
3. 去 `sk-` 前缀提取 key，过 `TokenAuthReadOnly`（G3）。
4. 校验 key 有效且存在（G4）。
5. `GetTokenStatus` 组装 credit_summary（G5）。
6. 判 `ExpiredTime=-1`（G6）：是→`expires_at` 归零（G7）；否→`expires_at=ExpiredTime`（G8）。
7. 组装 `total_granted=Remain+Used`、`total_used=Used`（G9）→ 返回 credit_summary（GOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 无 `Authorization` 头 | G1-否 | 拒绝 | HTTP 401 缺少鉴权 |
| 非 Bearer / 无 `sk-` 前缀 | G2-否 | 拒绝 | HTTP 401 格式非法 |
| key 无效/不存在 | G4-否 | 拒绝 | `MsgTokenGetInfoFailed` |
| `ExpiredTime=-1` | G6-是 | `expires_at` 归零 | 返回 `expires_at=0`（永不过期） |

### 5. 数据对象（复用 DATA-MODEL §2）
- **读** `Token`：`Key(key)`（去 `sk-` 解析）、`RemainQuota(remain_quota)`、`UsedQuota(used_quota)`、`ExpiredTime(expired_time)`。
- **返回结构（OpenAI 兼容）**：`object=credit_summary`、`total_granted=RemainQuota+UsedQuota`、`total_used=UsedQuota`、`expires_at`（`ExpiredTime=-1` 时归零，否则=`ExpiredTime`）。

### 6. 验收标准
- [ ] 请求无 `Authorization` 头 → HTTP 401。
- [ ] `Authorization: Token xxx`（非 Bearer）→ HTTP 401。
- [ ] key 不存在 → 返回 `MsgTokenGetInfoFailed`。
- [ ] 有效 key 且 `ExpiredTime=-1` → 响应 `expires_at=0`。
- [ ] 有效 key → `total_granted` 精确等于 `RemainQuota+UsedQuota`、`total_used` 等于 `UsedQuota`、`object="credit_summary"`。

### 7. 所触及页面状态（对齐 §D「令牌用量查询（外部 API）」）
缺少鉴权态（401）· 格式非法态（401）· key 无效态（MsgTokenGetInfoFailed）· 永不过期归零态（ExpiredTime=-1 → expires_at=0）· credit_summary 用量返回态（total_granted/total_used/expires_at）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-3001 | TK-1 |
| F-3002 | TK-3 |
| F-3003 | TK-3 |
| F-3004 | TK-4 |
| F-3005 | TK-4 |
| F-3006 | TK-2 |
| F-3007 | TK-2 |
| F-3008 | TK-1 / TK-5 |
| F-3009 | TK-5 |
| F-3010 | TK-5 |
| F-3011 | TK-5 |
| F-3012 | TK-6 |

无 `[BLOCKER]`。
