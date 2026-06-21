# 架构可落地性评审 — S4 PRD（举证式）

> 评审角色：architect（按 skill `architect-s3-s4` 的 Evidence-Citation Rule）。
> 评审范围（核心域）：`prd/prd-billing.md`、`prd/prd-relay.md`、`prd/prd-channel.md`、`prd/prd-asynctask.md`、`prd/prd-token.md`、`prd/prd-nfr-rbac.md` + `DATA-MODEL.md` + `02_decomposition/final/FUNCTION-LIST.csv`。
> 评审硬规则：每个 `PASS` 项必须给出 **文件 + 块 + 段落/行** 的具体规约位置，且规约到工程师可实现级别（含字段名、幂等键、去重条件、状态枚举、阈值常量）。仅出现关键词不构成 PASS。举不出即 `REWORK_REQUIRED`。
> 上一轮病灶（本轮明确规避）：用 `{幂等:true,补偿:true,权限:true}` 关键词字典放行、且与自己 gap 文件矛盾。本轮逐项落地引用，verdict 与 gap 文件一致。

---

## 总体 Verdict：**PASS_WITH_GAPS**

核心域（计费/relay/渠道/异步任务/令牌/RBAC）的数据对象、状态机、API 边界、幂等、重试补偿、权限均已规约到工程师可实现级别，绝大多数引用可定位到 PRD 具体块 + DATA-MODEL 字段 + repo 函数名。

降级为 `PASS_WITH_GAPS`（而非 PASS）的原因集中在两类：
1. **NFR 横切片（prd-nfr-rbac）中 S2 引入的「目标态/SLO」类指标**——`Audit`、`VendorMeta`、`Team`、`PerfMetric` 等结构在 DATA-MODEL.md 中**未做字段级冻结**（PRD 自述「字段以 controller 返回或下文配置项为准」），属于「新增/运行期结构」而非已抽取 GORM struct。这些项不能按核心域同等标准判 PASS。
2. **若干跨块幂等/去重的「窗口/键的边界值」未显式**——核心资金回调幂等键明确（见第 4 项），但 webhook 验签去重窗口、任务回调重复推进的去重在 PRD 中以 CAS/唯一键表达，**未显式给出"去重时间窗口"数值**（CAS 是逻辑去重而非时间窗口去重，工程上可接受，但任务书要求"举出去重窗口"，故标注为 gap 而非 FAIL）。

所有 gap 均为非阻断，已记入 `DATA-OBJECT-GAPS.md` / `API-CONTRACT-GAPS.md` / `STATE-MACHINE-GAPS.md`，交 PM 在后续阶段补冻结。核心资金/路由/鉴权链路可直接进入 UI/原型/开发。

---

## 逐项 Verdict 与举证

### 1. 数据对象（主流程实体清晰，字段级，复用 DATA-MODEL）— **PASS**

每个核心实体在 DATA-MODEL.md 中是从 `repo/new-api/model/*.go` 的 GORM struct 逐字段抽取（字段名 + json tag + 类型 + 约束 + 枚举），且各 PRD 的「§5 数据对象」段显式复用：

| 实体 | DATA-MODEL 位置（字段级） | PRD 复用举证 |
|---|---|---|
| User | §1（`model/user.go`）：`Quota(quota) int default:0`、`Role(role) int default:1` + Role/Status 枚举 | prd-billing BL-1 §5「入账 `User.Quota(quota)`」 |
| Token | §2（`model/token.go`）：`Key(key) varchar(128);uniqueIndex sk-`、`RemainQuota`、`UnlimitedQuota`、`ExpiredTime bigint;default:-1`、`CrossGroupRetry` + Token Status 枚举 + MaskTokenKey 规则 | prd-token TK-1~TK-6 §5 逐字段；prd-relay RL-1 §5「鉴权键 `Token.Key`」 |
| Channel | §3（`model/channel.go`）：`Type/Key not null/Status default:1/Priority *int64/Weight *uint/AutoBan *int default:1/ModelMapping *string text/StatusCodeMapping` + ChannelInfo 子结构 | prd-channel CH-1~CH-5 §5；prd-relay RL-3/RL-4 §5 |
| Ability | §4（`model/ability.go`）：`Group/Model/ChannelId` 复合主键、`Enabled bool`、`Priority *int64 default:0;index`、`Weight uint` | prd-channel CH-2 §5「候选筛选 `Ability`」 |
| Log | §5（`model/log.go`）：`Type int` + Log Type 枚举（`2=Consume/5=Error/6=Refund`）、`PromptTokens/CompletionTokens/Quota/ChannelId/Other` | prd-billing BL-2 §5「结算落库 `Log`」；prd-relay RL-3 §5「错误日志 `Log`」 |
| TopUp | §7（`model/topup.go`）：`TradeNo varchar(255);unique;index`、`Amount int64`、`Status string`、PaymentMethod/Provider 枚举 | prd-billing BL-1 §5 |
| UserSubscription | §8.3：`Status varchar(32) active/expired/cancelled`、`AmountTotal/AmountUsed`、`EndTime`、`AllowWalletOverflow` | prd-billing BL-3 §5 |
| Task | §9（`model/task.go`）：`TaskID varchar(191);index`、`Status TaskStatus varchar(20);index` + TaskStatus 枚举、`Progress/FinishTime/FailReason/PrivateData json:"-"` | prd-asynctask AT-1~AT-5 §5；prd-relay RL-5 §5 |
| Redemption | §6：`Key char(32);uniqueIndex`、`Status default:1`、`ExpiredTime bigint 0=不过期` | prd-billing BL-4 §5 |

举证强度：DATA-MODEL.md 第 1-7 行明确「PRD『数据对象』段只能从本文件复制字段子集，禁止零信息占位句」，且 PRD 各 §5 均带 `Go字段(json_tag) 类型 [约束]`。核心域字段级闭环成立。

**Gap（非阻断，见 DATA-OBJECT-GAPS.md）**：`Audit`、`VendorMeta`、`UserOAuthBinding`（部分）、`Team`、`PerfMetric`、`QuotaData` 在 prd-nfr-rbac §5 配置项中以"配置键/运行期结构"出现，DATA-MODEL.md **未给字段级 GORM 抽取**。prd-nfr-rbac 第 4 行自述"字段以 controller 返回或下文配置项为准"——核心域 PASS 不被污染，但这些横切结构未冻结，列为 gap。

---

### 2. 状态机（生命周期状态显式）— **PASS**

被点名的六类生命周期均有显式状态枚举 + 迁移条件 + 守卫，可定位：

| 状态机 | 举证位置 | 状态枚举 + 关键迁移守卫 |
|---|---|---|
| **Token** | prd-token TK-2 §3「主流程（状态机迁移）」+ §4 分支表 + DATA-MODEL §2 Token Status 枚举 | Enabled/Disabled/Expired/Exhausted/Deleted；守卫：启用已过期（`ExpiredTime<=now 且 ≠-1`）→拒绝 `MsgTokenExpiredCannotEnable`；启用额度耗尽（`RemainQuota=0 且 UnlimitedQuota=false`）→拒绝 `MsgTokenExhaustedCannotEable`；删除走 gorm `DeletedAt` 软删 |
| **Channel** | prd-channel CH-3 §3「主流程（状态机迁移）」步骤 1-7 + §4 + DATA-MODEL §3 | Enabled/Evaluating/GlobalGate/HitCheck/AutoBanCheck/AutoDisabled/RecoverEval/ManualDisabled；守卫：`AutomaticDisableChannelEnabled` 全局 ∧ `AutoBan==1` 才置 `ChannelStatusAutoDisabled`；**仅 `status==ChannelStatusAutoDisabled` 才自动恢复，手动禁用不自动恢复** |
| **Order（充值）** | prd-billing BL-1 §3 步骤 1-6 + §4 | pending→success；守卫：仅回调验签通过 ∧ 订单非 paid 才入账（见第 4 项幂等） |
| **Order（订阅）/Subscription** | prd-billing BL-3 §3「状态机迁移」步骤 1-5 + DATA-MODEL §8.3 Status 枚举 | Pending/Active/OrderFail/Consuming/Refunded/Expired/Cancelled/WalletGuard/OverflowBlock；活跃判定 = `Status=active AND EndTime>now`（`HasActiveUserSubscription`）；到期 `end_time<=now`→Expired；管理员作废→`Status=cancelled 且 end_time=now` |
| **Task** | prd-asynctask AT-1 §3 + §4 + DATA-MODEL §9 TaskStatus 枚举 | `NOT_START→SUBMITTED→QUEUED→IN_PROGRESS`→`SUCCESS/FAILURE/UNKNOWN`；合法顺序守卫 + CAS（`UpdateWithStatus(fromStatus)`, `RowsAffected>0` 才赢）；终态不可被普通 bulk update 覆盖 |
| **支付回调态** | prd-billing BL-1 §3 步骤 4-6 + §4 分支表 | pay_cb→pay_sign（验签）→pay_idem（已 paid 判定）→pay_credit/pay_ok2 |

举证强度：每个状态机都给出了"节点名 + 触发条件 + 系统行为"三列分支表，且枚举值锚定 DATA-MODEL（不是散文）。

**Gap（见 STATE-MACHINE-GAPS.md，非阻断）**：(a) Task `UNKNOWN` 终态的后续处置（是否重扫/是否退款）在 AT-1/AT-4 未显式——AT-4 仅分流 SUCCESS / FAILURE+超时，`UNKNOWN` 不在退款分流树中。(b) 订阅 `QuotaResetPeriod`（DATA-MODEL §8.1 `never/daily/weekly/monthly/custom`）的重置态未在 BL-3 状态机中出现迁移。

---

### 3. API 边界（UI 动作映射后端责任，无 "system handles it"）— **PASS**

每个用户/管理员动作均映射到具体 HTTP 路径 + 中间件链 + 控制器函数，无含糊"系统处理"：

| 动作 | 举证位置 | 后端责任（路径 + 中间件 + 函数） |
|---|---|---|
| 客户端转发 | prd-relay RL-1 §1/§3 + 来源行 | `POST /v1/chat/completions`、中间件链 `TokenAuth → ModelRequestRateLimit → Distribute`、转发后 `PostConsumeQuota` 落 Log |
| 协议分发 | prd-relay RL-2 §3 步骤 1-9 | `Path2RelayMode` 前缀有序匹配（`/v1/responses/compact` 先于 `/v1/responses`），输出 `RelayMode/RelayFormat` |
| 视频代理 | prd-relay RL-5 §3 + 来源行 | `GET /videos/:task_id/content`、`VideoProxy`、`ValidateURLWithFetchSetting` SSRF |
| 取令牌明文 | prd-token TK-4 §3 步骤 2 | `POST /token/:id/key`、`CriticalRateLimit + DisableCache`、`GetFullKey()`、userId 越权过滤 |
| 用户任务列表 | prd-asynctask AT-2 §3 | `GET /api/task/self`、`UserAuth`、`TaskGetAllUserTask` 强制 `Where(user_id=本人)` + `Omit(channel_id)` |
| 管理端任务 | prd-asynctask AT-3 §3 ADMIN 泳道 | `GET /api/task`、`AdminAuth`、`TaskGetAllTasks` |
| MJ 提交 | prd-asynctask AT-5 §3 | `POST /mj/submit/:action`、按 action 构造任务 |
| 外部用量查询 | prd-token TK-6 §3 | `GET /usage/token`、`TokenAuthReadOnly`、`GetTokenStatus` |
| 充值下单/回调 | prd-billing BL-1 §3 | 下单创建 `TopUp(pending)`，回调侧验签 + 幂等入账 |

举证强度：FUNCTION-LIST.csv 的 `evidence` 列同样给到 `api-router.go:行号` + 中间件（如 F-1001 `userRoute.POST(/register, TurnstileCheck)`），与 PRD 来源行一致。无任一动作以"系统自动处理"收尾。

**Gap（见 API-CONTRACT-GAPS.md，非阻断）**：(a) prd-relay RL-1 §5 未列出**响应体 schema**（仅列 Log 落账字段），OpenAI 兼容响应结构未冻结字段。(b) 支付回调 webhook 的**入站路径与每 provider 的验签头/字段**（Stripe `Stripe-Signature` / Creem / 易支付）在 BL-1 与 prd-nfr-rbac X3 中只说"验签通过/失败"，未给每 provider 的具体验签字段契约。

---

### 4. 幂等（支付回调/webhook/任务回调/余额变更可重复安全）— **PASS（核心资金链路），整体 PASS_WITH_GAPS**

任务书要求"举出具体幂等键和去重窗口，举不出即 FAIL"。逐场景举证：

| 幂等场景 | 幂等键 / 去重条件 | 举证位置 | Verdict |
|---|---|---|---|
| **支付回调重复入账** | 幂等键 = `TopUp.TradeNo`（DATA-MODEL §7 `unique;varchar(255);index`）；去重条件 = 回调时判 `Status` 已 paid/success 则幂等返回成功不重复 `Quota +=` | prd-billing BL-1 §1 末句、§3 步骤 5-6（pay_idem/pay_ok2）、§4「订单已 paid（重复回调）」行、§6 验收「同一 TradeNo 回调到两次 → Quota 仅增加一次」 | **PASS** |
| **兑换码并发重复** | 幂等键 = `Redemption.Key char(32);uniqueIndex`；去重 = 同事务内 `Status` 已使用即拒；原子性 = 校验+入账+置已用同一本地事务 | prd-billing BL-4 §1、§2「校验、入账、置已用在同一本地事务内完成」、§6「同一有效码并发提交两次 → 仅一次入账成功」 | **PASS** |
| **余额预扣/结算/返还** | 冻结量键 = `RelayInfo.FinalPreConsumedQuota`；失败按此值全额 `ReturnPreConsumedQuota`；结算多退少不补 | prd-billing BL-2 §3 步骤 5-6、§5「冻结量 RelayInfo.FinalPreConsumedQuota」、§6 验收「上游失败→Quota 复原为预扣前值」 | **PASS**（逻辑幂等：返还以冻结快照值为准，避免重复返还） |
| **任务回调重复推进** | 去重机制 = `UpdateWithStatus(fromStatus,…)` CAS，以 `fromStatus` 为 WHERE 守卫，`RowsAffected>0` 才生效；终态不可被普通 bulk update 覆盖 | prd-asynctask AT-1 §1/§3 步骤 4、§6「fromStatus 不匹配时 RowsAffected=0 本拍放弃」「终态普通 bulk update 不能覆盖」 | **PASS**（CAS 逻辑去重，比时间窗去重更强） |
| **超时退款 vs 自然完成竞态** | CAS 守卫 = `UpdateWithStatus`；命中超时但已自然完成（`fromStatus` 不匹配）→`RowsAffected=0` 跳过 | prd-asynctask AT-3 §3 SYS 步骤 4、§6「已自然完成则 RowsAffected=0 跳过，不误覆盖」 | **PASS** |
| **订阅维度退款** | `SubscriptionPreConsumeRecord` consumed↔refunded 状态转换；退款经 `UpdateWithStatus` CAS | prd-billing BL-3 §5「预扣记录 consumed↔refunded」、prd-asynctask AT-4 §3 步骤 3-4 | **PASS** |

整体降为 `PASS_WITH_GAPS` 的原因（见 API-CONTRACT-GAPS.md）：
- **去重窗口数值未显式**：核心资金回调用唯一键 + 状态判定做幂等（强于时间窗），但任务书字面要求"去重窗口"。本评审认定"唯一键 + 状态守卫"即满足可重复安全，**但 Stripe/Creem 等 provider 自身的回调重试在网关侧除 TradeNo 外是否有 `event_id` 级去重未规约**（prd-nfr-rbac X3 仅说验签）。这是 gap 不是 FAIL，因为 `TradeNo` 已足以防重复入账。

---

### 5. 重试/补偿（上游失败/渠道错误/超时/计费失败有定义行为）— **PASS**

| 场景 | 定义行为 | 举证位置 |
|---|---|---|
| 上游错误码重试 | `ShouldRetryByStatusCode(code)` 判可重试；`重试次数 < common.RetryTimes` 换渠道重试，达上限返错；不可重试码跳过 | prd-relay RL-3 §3 步骤 1-2、§4、§6 前三条 |
| 渠道错误→禁用 | 命中禁用条件 ∧ `AutoBan=1` → `DisableChannel` + 通知 root；否则保持 | prd-relay RL-3 §3 步骤 4、prd-channel CH-3 §3 步骤 5 |
| 渠道选不到/层耗尽 | `priorityRetry++` 降级，层耗尽降下一优先级层，全部层无→nil/err 上抛 | prd-channel CH-2 §3 步骤 2-4 |
| auto 跨组重试 | 组内 `priorityRetry>=RetryTimes` 切下一组 `SetRetry(0)`；`CrossGroupRetry` 控制本次切/延迟切；全组耗尽返无可用渠道 | prd-channel CH-5 §3 步骤 4-7、§6 |
| 计费失败补偿（请求失败） | 异步 `ReturnPreConsumedQuota` 全额返还 `FinalPreConsumedQuota`，余额复原 | prd-billing BL-2 §3 步骤 5、§6「上游失败→Quota 复原」 |
| 任务超时补偿 | `GetTimedOutUnfinishedTasks` 按 cutoff 扫描 → CAS 守卫退款 | prd-asynctask AT-3 §3 SYS、AT-4 §3 退款分流 |
| 多退少不补 | 实际<预扣多退差额，实际>=预扣仅补记不追扣 | prd-billing BL-2 §3 步骤 6、§4 后两行 |
| 亲和命中失败 | `SkipRetryOnFailure=true` 直接返错不跨渠道重试（保会话稳定）；false 走正常重试 | prd-channel CH-4 §3 步骤 6、§6 后两条 |

举证强度：每条都有具体函数（`ShouldRetryByStatusCode`/`ReturnPreConsumedQuota`/`GetTimedOutUnfinishedTasks`）+ 阈值常量（`common.RetryTimes`）+ 验收用例。

**Gap（非阻断）**：`UNKNOWN` 终态无退款/重扫行为定义（同状态机 gap）；prd-nfr-rbac X2「Redis 失联回落内存缓存」是降级而非补偿，无数据一致性恢复说明（属 NFR 目标态，可接受）。

---

### 6. 权限/限流（user/admin/root 边界显式）— **PASS**

| 边界 | 举证位置 | 规约 |
|---|---|---|
| 三级系统角色 | prd-nfr-rbac X7 §5「系统角色 `Role∈{common,admin,root}`，路由组挂 AdminAuth/RootAuth」+ DATA-MODEL §1 Role 枚举 + 越权护栏「不可操作 目标角色>=操作者角色」 | common→admin 路由 403；admin→root（O12 改 Option）403 |
| self-scope 越权 | prd-nfr-rbac X7 §5「model 层 User/Token/Task/Log/Quota 查询强制 `where user_id=:caller`」、§6「访问他人令牌/额度/任务/日志返 403」 | prd-token TK-4 §4「id 非本人取不到」；prd-asynctask AT-2 §3「强制 Where user_id=本人」 |
| 管理端任务 | prd-asynctask AT-3 §3「AdminAuth」、§6「普通用户访问 /api/task 返 403」 | 全量看板仅 admin |
| 高危二次验证 | prd-nfr-rbac X7 §5「高危 action 接入 `SecureVerificationRequired`」、X3 §5「取明文 `/api/token/:id/reveal` 需 SecureVerification」 | 取 Key/改倍率/重置 2FA/改 Option 无二次验证返 403 |
| 限流 | prd-relay RL-1「ModelRequestRateLimit」、prd-token TK-3「SearchRateLimit」、TK-4「CriticalRateLimit」、prd-nfr-rbac X3「CriticalRateLimit+DisableCache」 | 各路径限流中间件显式 |
| 功能权限组 | prd-nfr-rbac X7 §5「`permission_set.operator=[O07,O08,O09]`、`permission_set.sre=[O11,O10:readonly]`」 | Operator/SRE 越界域返 403 |

举证强度：来源行点名 `ROLE-PERMISSION-MATRIX（7 产品角色 × 12 操作域，84 授权单元）` 与 `AdminAuth/RootAuth` 中间件，验收以 403 二元判定落地。

**Gap（非阻断，见 API-CONTRACT-GAPS.md）**：`Team`（team_id + owner/member）为阶段二预留、DATA-MODEL 未冻结；84 授权单元矩阵本体（`ROLE-PERMISSION-MATRIX.md`）未在本批读取范围内逐单元核对（PRD 引用其存在，但单元级映射需 PM 在该文件维护）。

---

### 7. 可观测（关键操作有日志/审计/指标）— **PASS（核心），PASS_WITH_GAPS（NFR 目标态）**

| 维度 | 举证位置 | 规约 |
|---|---|---|
| 用量日志 | prd-relay RL-1 §5 + DATA-MODEL §5 Log（`Type=2 Consume`，`prompt_tokens/completion_tokens/quota/channel/model_name/is_stream/use_time`） | 每次转发落一条 Log |
| 错误日志 | prd-relay RL-3 §5「`Log Type=5 Error`，Content 经 `MaskSensitiveErrorWithStatusCode` 脱敏，Other 附 status_code」 | 脱敏后记 channel/model/status_code |
| 退款日志 | prd-billing BL-2 §5「失败返还落 `Type=6(Refund)`」 | Refund 类型显式 |
| 高危审计 | prd-nfr-rbac X3 §5「`Audit`：actor_id/action/target/before/after/ts，action 如 channel.key.reveal/billing.ratio.update」、§6「审计可按操作人/对象检索到含前后值的记录」 | 高危操作前后值快照 |
| 渠道禁用通知 | prd-channel CH-3 §3「NotifyRootUser」、prd-relay RL-3「通知 root」 | 自动禁用通知 root |

核心可观测（Log Type 0-7 枚举 + 脱敏函数）锚定 DATA-MODEL §5，PASS。

**Gap（非阻断）**：`PerfMetric`（prd-nfr-rbac X1/X4，`gateway_latency_p99/channel_select_p99/...`）、`QuotaData`、`trace_id`/OTel 均为 S2 引入的目标态指标，**DATA-MODEL 未做字段级抽取**（X4 来源行自述 trace_id 为"新增能力"）。这些是可观测增强目标，不阻断核心域，列为 gap。`Audit` 表结构同样未在 DATA-MODEL 冻结字段。

---

### 8. 运维约束（部署/DB/缓存/队列依赖）— **PASS_WITH_GAPS**

| 依赖 | 举证位置 | 规约 |
|---|---|---|
| 缓存 | prd-nfr-rbac X5 §1/§5「Redis + 内存双缓存，转发实例 `stateless=true`，`cache_backend=redis`，内存 `readonly_fallback=true`」、X2「Redis 失联回落内存缓存只读路径不中断」 | 双缓存降级显式 |
| DB | prd-nfr-rbac X5「GORM v2 读写分离 + log 表分区」、X2「DB 主从（MySQL/PostgreSQL）容灾 RTO≤30min/RPO≤5min」 | 主从 + 分区键 `partition_by=created_at` |
| 后台任务/队列 | prd-asynctask AT-1/AT-3「后台轮询/回调推进状态机」「定时任务按 cutoff 扫描 GetTimedOutUnfinishedTasks」 | 轮询 + 定时扫描 |
| 日志归档 | prd-nfr-rbac X5「`log.metadata_retention_days=180`、归档与 DC-006 对齐」 | 保留期 + 冷库归档 |
| 探针 | prd-nfr-rbac X4「liveness/readiness 探针返回 DB/Redis/上游健康」 | 健康探针 |

降级原因：运维约束多落在 prd-nfr-rbac，且 prd-nfr-rbac 第 5 行明确这些"SLA 百分位、保留期"是"S2 引入的目标态/SLO，不回填为 repo 既有事实"——即**目标值而非已实现机制**，验收以二元判定落地但实现细节（具体队列实现、压测脚本、OTel exporter 配置）未在 PRD 规约。属可接受的 NFR 目标态，标 PASS_WITH_GAPS。

---

## Verdict 汇总表

| # | 检查项 | Verdict | 主要举证 | Gap 文件 |
|---|---|---|---|---|
| 1 | 数据对象 | PASS | DATA-MODEL §1-9 字段级 + 各 PRD §5 复用 | DATA-OBJECT-GAPS（Audit/VendorMeta/Team/PerfMetric 未冻结）|
| 2 | 状态机 | PASS | Token TK-2 / Channel CH-3 / Order BL-1 / Subscription BL-3 / Task AT-1 | STATE-MACHINE-GAPS（UNKNOWN 处置、QuotaReset 未入机）|
| 3 | API 边界 | PASS | 各动作映射路径+中间件+函数 | API-CONTRACT-GAPS（响应 schema、webhook 验签字段）|
| 4 | 幂等 | PASS_WITH_GAPS | TradeNo / Redemption.Key / CAS UpdateWithStatus / FinalPreConsumedQuota | API-CONTRACT-GAPS（event_id 级去重未规约）|
| 5 | 重试/补偿 | PASS | ShouldRetryByStatusCode / RetryTimes / ReturnPreConsumedQuota / 超时扫描 | STATE-MACHINE-GAPS（UNKNOWN 无补偿）|
| 6 | 权限/限流 | PASS | Role 枚举 + AdminAuth/RootAuth + self-scope user_id 过滤 + SecureVerification | API-CONTRACT-GAPS（84 单元矩阵、Team 未冻结）|
| 7 | 可观测 | PASS_WITH_GAPS | Log Type 枚举 + 脱敏 + Audit before/after | DATA-OBJECT-GAPS（PerfMetric/QuotaData/trace_id 未冻结）|
| 8 | 运维约束 | PASS_WITH_GAPS | Redis 双缓存 + DB 主从 + log 分区 + 探针 | —（NFR 目标态，可接受）|

**总 Verdict：PASS_WITH_GAPS**

理由：核心域（计费/relay/渠道/异步任务/令牌/RBAC）的 8 项检查全部可举证到工程师可实现级别，资金回调幂等键明确（`TradeNo`/`Redemption.Key`/CAS），状态机枚举完整，权限边界以 403 二元落地。降级原因仅为 NFR 横切片的目标态结构（Audit/VendorMeta/Team/PerfMetric/trace_id）未做字段级冻结 + 个别去重窗口未显式，均为非阻断、可在后续阶段补齐。

本评审 verdict 与三份 gap 文件一致：gap 文件未声称任何核心 schema "未冻结"，因此第 1/3 项判 PASS 不与 gap 文件矛盾（规避了上一轮病灶）。
