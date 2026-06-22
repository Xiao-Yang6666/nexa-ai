# FL-token — 令牌管理（D3）流程图

> 分片：令牌管理（F-3001~F-3012）。
> 角色：登录用户（令牌拥有者）/ 外部客户端（携 key 调用）/ 系统。
> 跨切面契约见 `../OVERALL-FLOW.md §3`：C1 会话鉴权（UserAuth）、C5 限流（CriticalRateLimit/SearchRateLimit/DisableCache）。本文件不重画 C1/C5，仅在节点标注「过 C1」「过 C5」。
> 后端实现集中于 `controller/token.go` + `model/token.go`，常量 `maxQuotaValue=1000000000*QuotaPerUnit`、`searchHardLimit=100`、`maxUserTokens=operation_setting.GetMaxUserTokens()`、Name 上限 50 字符。

---

## 场景 TK-1 · 创建令牌（额度区间 + 令牌数上限 + 名称长度三重串行校验）（F-3001/F-3008）

> 业务规则：`UnlimitedQuota=true` 跳过额度校验；否则 `RemainQuota<0` → `MsgTokenQuotaNegative`、`RemainQuota>10亿*QuotaPerUnit` → `MsgTokenQuotaExceedMax`；`Name>50` → `MsgTokenNameTooLong`；当前用户令牌数达 `GetMaxUserTokens()` → 「已达到最大令牌数量限制(N)」；通过后 `GenerateKey()` 生成唯一 `sk-` 前缀 key 写 Token 表，`ExpiredTime` 默认 -1（永不过期）。校验链是核心：任一关卡失败即拒。

```mermaid
flowchart TD
  C0([用户点「新建令牌」填表 · 过C1]) --> C1[提交 name/quota/expired/unlimited]
  C1 --> C2{Name 长度 > 50?}
  C2 -->|是| CE1[名称过长 MsgTokenNameTooLong]:::err
  C2 -->|否| C3{当前令牌数 >= maxUserTokens?}
  C3 -->|是| CE2[已达最大令牌数量限制N]:::err
  C3 -->|否| C4{UnlimitedQuota = true?}
  C4 -->|是 跳过额度校验| C8[GenerateKey 生成唯一 sk- key]
  C4 -->|否| C5{RemainQuota < 0?}
  C5 -->|是| CE3[额度为负 MsgTokenQuotaNegative]:::err
  C5 -->|否| C6{RemainQuota > 10亿*QuotaPerUnit?}
  C6 -->|是| CE4[额度超上限 MsgTokenQuotaExceedMax]:::err
  C6 -->|否| C8
  C8 --> C9[写入 Token 表 · ExpiredTime 默认-1]
  C9 --> COK([创建成功 · 返回打码 key 态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（TK-1 创建令牌）：
- 新建令牌表单默认态（额度/有效期/无限开关）
- 名称过长态（MsgTokenNameTooLong） ← 异常
- 令牌数达上限态（已达最大令牌数量限制(N)） ← 异常
- 额度为负态（MsgTokenQuotaNegative） ← 异常
- 额度超上限态（MsgTokenQuotaExceedMax） ← 异常
- 无限额度跳过校验提示态（UnlimitedQuota）
- 创建成功返回打码 key 态 ← 终态

---

## 场景 TK-2 · 令牌生命周期状态机（启用→禁用→过期→耗尽→删除）（F-3006/F-3007）

> 业务规则：`status_only` 分支仅改 `Status` 不覆盖其他字段；从禁用启用时校验——已过期（`ExpiredTime<=now 且 ≠-1`）→ `MsgTokenExpiredCannotEnable`，额度耗尽且非无限 → `MsgTokenExhaustedCannotEable`；删除走 gorm `DeletedAt` 软删除。本图为状态机：不同当前态触发不同迁移与守卫条件。

```mermaid
stateDiagram-v2
  [*] --> Enabled : 创建成功
  Enabled --> Disabled : status_only 置禁用
  Disabled --> Enabled : 启用 [未过期 且 (有额度 或 无限)]
  Disabled --> EnableReject : 启用 [已过期 ExpiredTime<=now≠-1]
  Disabled --> EnableReject : 启用 [额度耗尽 且 非无限]
  EnableReject --> Disabled : 提示后退回禁用
  Enabled --> Expired : 时间到 ExpiredTime<=now
  Enabled --> Exhausted : RemainQuota 耗尽 且 非无限
  Expired --> Deleted : 软删除 DeletedAt
  Exhausted --> Deleted : 软删除 DeletedAt
  Disabled --> Deleted : 软删除 DeletedAt
  Enabled --> Deleted : 软删除 DeletedAt
  Deleted --> [*]
  note right of EnableReject
    MsgTokenExpiredCannotEnable
    / MsgTokenExhaustedCannotEable
  end note
```

屏幕状态清单（TK-2 生命周期）：
- 启用态（可正常调用）
- 禁用态（status_only 切换，调用被拒）
- 启用被拒态（已过期，MsgTokenExpiredCannotEnable） ← 异常
- 启用被拒态（额度耗尽且非无限，MsgTokenExhaustedCannotEable） ← 异常
- 已过期态（ExpiredTime<=now）
- 额度耗尽态（RemainQuota 用尽且非无限）
- 已删除态（DeletedAt 软删除，列表不再显示） ← 终态

---

## 场景 TK-3 · 令牌列表查询 + 关键词搜索（key 自动打码 + LIKE 注入防护）（F-3002/F-3003）

> 业务规则：列表 `GetAllUserTokens(user_id 过滤, Order id desc)`，每项 key 经 `MaskTokenKey` 脱敏（≤4 全打码、≤8 保留首尾2位、否则形如 `abcd**********wxyz`）；搜索 `sanitizeLikePattern` 用 `!` 作 ESCAPE，连续 `%%` 拒绝、`%` 超过 2 个拒绝、含 `%` 时去 % 后关键词需 ≥2 字符，`limit>100` 强制截断为 100，并过 `SearchRateLimit`。两条入口（直列表 / 搜索）合流到脱敏渲染。

```mermaid
flowchart TD
  Q0([进入令牌管理页 · 过C1]) --> Q1{有搜索关键词?}
  Q1 -->|否 直列表| Q2[GetAllUserTokens user_id过滤 · id desc 分页]
  Q1 -->|是 走搜索| S1[过 SearchRateLimit 限流]
  S1 --> S2{含连续 %% ?}
  S2 -->|是| SE1[非法模式拒绝]:::err
  S2 -->|否| S3{% 数量 > 2 ?}
  S3 -->|是| SE2[通配符过多拒绝]:::err
  S3 -->|否| S4{含% 且 去%后关键词 < 2字符?}
  S4 -->|是| SE3[关键词过短拒绝]:::err
  S4 -->|否| S5[sanitizeLikePattern ! 转义 · limit截断100]
  S5 --> S6[SearchUserTokens 当前用户范围]
  S6 --> Q3
  Q2 --> Q3[逐项 MaskTokenKey 脱敏]
  Q3 --> Q4{结果集为空?}
  Q4 -->|是| QEMP([空列表态 · 引导新建]):::term
  Q4 -->|否| QOK([打码令牌分页列表态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（TK-3 列表/搜索）：
- 令牌分页列表态（key 已 MaskTokenKey 脱敏）
- 空列表态（无令牌，引导新建） ← 终态
- 搜索非法模式态（连续 %%） ← 异常
- 搜索通配符过多态（% > 2） ← 异常
- 搜索关键词过短态（去 % 后 < 2 字符） ← 异常
- 搜索被限流态（SearchRateLimit）
- 搜索结果分页态（打码） ← 终态

---

## 场景 TK-4 · 令牌密钥访问（掩码默认展示 + 受控取明文 + 批量上限）（F-3004/F-3005）

> 业务规则：列表中 key 默认掩码；单个取明文 `POST /token/:id/key` 经 `CriticalRateLimit + DisableCache`，返回 `GetFullKey()`，越权（非本人 `GetTokenByIds 带 userId` 取不到）；批量 `GetTokenKeysBatch` 要求 `len(Ids)≤100`，否则 `MsgBatchTooMany{Max:100}`，空 ids → `MsgInvalidParams`。掩码→受控明文的安全升级路径，单/批两分支。

```mermaid
flowchart TD
  K0([列表中令牌默认掩码展示]) --> K1{用户要单个 还是 批量?}
  K1 -->|单个复制| U1[POST /token/:id/key · 过C5 CriticalRateLimit+DisableCache]
  U1 --> U2{该 id 属当前 userId?}
  U2 -->|否 越权| UE1[非本人令牌取不到 → 拒绝]:::err
  U2 -->|是| U3[返回 GetFullKey 完整明文]
  U3 --> UOK([单个明文展示态 · 一次性复制]):::term
  K1 -->|批量导出| B1{ids 为空?}
  B1 -->|是| BE1[MsgInvalidParams]:::err
  B1 -->|否| B2{len ids > 100?}
  B2 -->|是| BE2[MsgBatchTooMany Max:100]:::err
  B2 -->|否| B3[GetTokenKeysByIds 带userId过滤越权]
  B3 --> B4[组装 id→key 映射 仅本人令牌]
  B4 --> BOK([批量 keysMap 导出态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（TK-4 密钥访问）：
- 掩码默认展示态（列表内 key 脱敏）
- 单个越权拒绝态（非本人令牌） ← 异常
- 单个明文一次性展示态（DisableCache，引导立即复制） ← 终态
- 批量参数无效态（ids 空，MsgInvalidParams） ← 异常
- 批量超上限态（>100，MsgBatchTooMany） ← 异常
- 批量 keysMap 导出态（仅本人令牌） ← 终态

---

## 场景 TK-5 · 令牌额度策略组合配置（无限/有限 + IP白名单 + 模型白名单 + 分组）（F-3008/F-3009/F-3010/F-3011）

> 业务规则：四个相互独立的限制维度叠加在一个令牌上——`UnlimitedQuota` 决定额度模式；`ModelLimitsEnabled=true` 时 `GetModelLimitsMap()` 生成允许模型布尔表（白名单外拒）；`AllowIps` 按换行解析、空/nil 不限制；`Group` + `CrossGroupRetry`（仅 `Group=auto` 生效）。本图为并行配置汇聚而非线性流程：每个开关各自落库，最终合成一份调用约束。

```mermaid
flowchart TD
  P0([编辑令牌策略表单 · 过C1]) --> P1[/并行配置四个维度/]
  P1 --> PA{额度模式?}
  PA -->|无限| PA1[UnlimitedQuota=true 不校验上下限]
  PA -->|有限| PA2[RemainQuota 走 TK-1 区间校验]
  P1 --> PB{ModelLimitsEnabled?}
  PB -->|开启| PB1[ModelLimits JSON 存 text · GetModelLimitsMap]
  PB -->|关闭| PB2[不限制模型]
  P1 --> PC{填了 AllowIps?}
  PC -->|是| PC1[GetIpLimits 按换行切分去空格逗号]
  PC -->|空/nil| PC2[不限制来源 IP]
  P1 --> PD{Group = auto?}
  PD -->|是| PD1[CrossGroupRetry 生效 跨分组重试]
  PD -->|普通分组| PD2[CrossGroupRetry 标志被忽略]
  PA1 --> PM[合成令牌调用约束]
  PA2 --> PM
  PB1 --> PM
  PB2 --> PM
  PC1 --> PM
  PC2 --> PM
  PD1 --> PM
  PD2 --> PM
  PM --> POK([策略已保存 · 调用受四维约束态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（TK-5 额度策略）：
- 策略配置表单态（额度模式/模型/IP/分组四区）
- 无限额度态（跳过上下限校验）
- 有限额度态（走区间校验）
- 模型白名单启用态（白名单外模型将被拒）
- 模型不限制态（ModelLimitsEnabled=false）
- IP 白名单生效态（多行解析）
- IP 不限制态（AllowIps 空/nil）
- 分组 auto + 跨分组重试态（CrossGroupRetry 生效）
- 普通分组态（CrossGroupRetry 忽略）
- 策略保存成功态（四维约束合成） ← 终态

---

## 场景 TK-6 · 令牌用量查询（OpenAI 兼容 credit_summary，外部 Bearer 鉴权）（F-3012）

> 业务规则：外部客户端 `GET /usage/token` 经 `TokenAuthReadOnly` 只读鉴权；无 `Authorization` 头 → 401，非 bearer 格式 → 401，key 无效 → `MsgTokenGetInfoFailed`；`GetTokenStatus` 返回 `object=credit_summary`，`total_granted=RemainQuota+UsedQuota`、`total_used=UsedQuota`，`ExpiredTime=-1` 时 `expires_at` 归零。本图为外部 API 调用链，与控制台内查询形态不同（无会话、Bearer 解析）。

```mermaid
flowchart TD
  G0([外部客户端 GET /usage/token]) --> G1{有 Authorization 头?}
  G1 -->|否| GE1[401 缺少鉴权]:::err
  G1 -->|是| G2{Bearer 格式 sk- 前缀?}
  G2 -->|否| GE2[401 格式非法]:::err
  G2 -->|是| G3[去 sk- 前缀提取 key · TokenAuthReadOnly]
  G3 --> G4{key 有效且存在?}
  G4 -->|否| GE3[MsgTokenGetInfoFailed]:::err
  G4 -->|是| G5[GetTokenStatus 组装 credit_summary]
  G5 --> G6{ExpiredTime = -1?}
  G6 -->|是 永不过期| G7[expires_at 归零]
  G6 -->|否| G8[expires_at = ExpiredTime]
  G7 --> G9[total_granted=Remain+Used · total_used=Used]
  G8 --> G9
  G9 --> GOK([返回 credit_summary 用量态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（TK-6 用量查询，外部 API）：
- 缺少鉴权态（无 Authorization，401） ← 异常
- 格式非法态（非 bearer，401） ← 异常
- key 无效态（MsgTokenGetInfoFailed） ← 异常
- 永不过期归零态（ExpiredTime=-1 → expires_at=0）
- credit_summary 用量返回态（total_granted/total_used/expires_at） ← 终态
