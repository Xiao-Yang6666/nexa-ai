# PRD — 计费与钱包（FL-billing）

> 分片：计费与额度 / 钱包 D8。对应流程图 `flow/FL-billing.md`、状态矩阵 `PAGE-STATE-MATRIX.md §H`。
> 数据对象字段一律复用 `DATA-MODEL.md §1 User / §6 Redemption / §7 TopUp / §8 Subscription`。
> 关键常量：`QuotaPerUnit`、`TrustQuota`、`FinalPreConsumedQuota`。
> 本片覆盖功能 ID：**F-2038 / F-2040 / F-2041 / F-2042 / F-2044 / F-2045 / F-2046 / F-2047 / F-2060 / F-2061 / F-2062 / F-2063 / F-2064**。
> 兼容层+计费反溯（2026-06）新增块：**BL-7 成本/售价分离 / BL-8 最终扣费=基准价×分组折扣 / BL-9 利润逐笔可算+利润看板**（权威源 `COMPAT-BILLING-DECISIONS.md §4/§5/§6/§8/§9`、`reviews/BILLING-MODEL-ARCHITECTURE.md §1-§6`、`DATA-MODEL.md §5 Log/§16 PublicModel/§19 ChannelModelCost`；售价挂对外模型 A 见 prd-model ML-6/ML-7，成本挂渠道×B 见 prd-channel CH-6/CH-7）。

---

## BL-1 在线充值入账（支付下单 → 回调 → 幂等入账）

- **功能 ID / 优先级**：F-2044 / P1
- **来源**：FC-061（`controller/topup.go`、`model/topup.go`、`TopUp` 单 + `User.Quota` 入账，过 C7 payment_compliance 合规闸门）
- **角色 / Owner**：登录用户（发起充值）/ 支付渠道（异步回调）；Owner 模块 = 计费与钱包
- **触发**：用户填充值金额提交（过 C7 合规闸门），完成支付后渠道异步回调

### 1. 场景
充值是两段式资金流：用户填金额先过 C7 支付合规闸门，系统创建本地 `TopUp` 单（pending）写入商户订单号，再跳转支付渠道收银台；**额度入账只发生在支付回调验签通过后**。回调以订单号（`TradeNo`）做幂等键——重复回调（订单已是 success/paid）直接返回成功不重复加额度；验签失败的伪造回调直接丢弃不入账。本块的核心守卫是回调侧的幂等性。

### 2. 前置条件
- 用户已登录（过 C1），充值入口过 C7 payment_compliance 合规闸门。
- 下单生成的 `TopUp.TradeNo` 全局唯一（`unique;varchar(255);index`）。
- 回调侧需对支付渠道签名验签通过。

### 3. 主流程（对应 BL-1 节点 pay_open→pay_done）
1. 用户填充值金额（过 C7）（pay_open），判合规是否已确认（pay_chk）。
2. 未确认→拒绝下单引导确认合规声明（pay_block）；已确认→创建 `TopUp` 单 `Status=pending` 写订单号（pay_order）。
3. 跳转支付渠道收银台（pay_jump），判用户是否完成支付（pay_wait）。
4. 放弃/超时→订单保持 pending 可重新发起（pay_expire）；完成→支付渠道异步回调网关（pay_cb）。
5. 判回调验签是否通过（pay_sign）：否→丢弃伪造回调不入账（pay_drop）；是→判订单是否已 paid（pay_idem）。
6. 已 paid（重复回调）→幂等返回成功不重复加额度（pay_ok2）；首次→`user.Quota += 充值额度` 标 paid（pay_credit）→充值入账成功态余额刷新（pay_done）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 合规未确认 | pay_chk-未确认 | 拒绝下单 | 引导确认合规声明态 |
| 用户放弃/超时未支付 | pay_wait-放弃 | 订单保持 pending | 可重新发起充值 |
| 回调验签失败 | pay_sign-否 | 丢弃回调，不入账 | 伪造回调被丢弃（无额度变化） |
| 订单已 paid（重复回调） | pay_idem-是 | 幂等返回成功 | 不重复加额度 |
| 首次有效回调 | pay_idem-否 | `Quota += Amount` 标 paid | 入账成功，余额增加 |

### 5. 数据对象（复用 DATA-MODEL §7 TopUp + §1 User）
- **写** `TopUp`：`UserId(user_id)`、`Amount(amount)`（充值额度，quota 单位）、`Money(money)`（支付金额）、`TradeNo(trade_no)`（`unique` 幂等键）、`PaymentMethod(payment_method)`（stripe/creem/waffo/waffo_pancake/balance）、`PaymentProvider(payment_provider)`（epay/stripe/creem/...）、`Status(status)`（pending→success）、`CreateTime/CompleteTime`。
- **入账** `User.Quota(quota)`：首次回调 `+= TopUp.Amount`；重复回调（`Status` 已 success）不再变更。

### 6. 验收标准
- [ ] 合规未确认时发起充值 → 拒绝下单 + 引导确认合规声明，不创建 success 订单。
- [ ] 下单后未支付 → `TopUp.Status` 保持 pending，用户可对同账户重新发起。
- [ ] 验签失败的回调 → `User.Quota` 不变，订单不置 success。
- [ ] 同一 `TradeNo` 的回调到达两次 → `User.Quota` 仅增加一次 `Amount`，第二次幂等返回成功不重复入账。
- [ ] 首次有效回调 → `User.Quota` 精确增加 `TopUp.Amount`，`TopUp.Status` 置 success，写 `CompleteTime`。

### 7. 所触及页面状态（对齐 §H「充值入账」）
充值金额填写态（过 C7 合规闸门）· 合规未确认拒绝态（引导确认声明）· 待支付态（订单 pending 跳收银台）· 放弃/超时态（订单保持 pending 可重发）· 回调验签失败丢弃态（伪造回调）· 幂等成功态（重复回调不重复加额度）· 充值入账成功态（Quota 增加余额刷新）。

---

## BL-2 按量请求的预扣 → 调用 → 结算/失败返还（多退少不补）

- **功能 ID / 优先级**：F-2042 / P0
- **来源**：FC-058（`service/pre_consume_quota.go PreConsumeQuota`/`ReturnPreConsumedQuota`、`service/quota.go PostConsumeQuota`、常量 `TrustQuota`、`RelayInfo.FinalPreConsumedQuota`）
- **角色 / Owner**：系统（relay 预扣·结算·返还）；Owner 模块 = 计费与钱包
- **触发**：relay 转发上游前估算预扣，上游返回后结算

### 1. 场景
按量计费是「冻结额度 → 真实消费 → 差额回补」的资金生命周期。转发前 `PreConsumeQuota` 用估算 token 算出预扣额度并冻结：`userQuota<=0` 直接 403「额度不足」且 SkipRetry；扣后为负也 403；高信任令牌（`userQuota>TrustQuota` 且令牌额度>TrustQuota）旁路跳过预扣。预扣成功记 `FinalPreConsumedQuota` 并扣减。上游返回后按真实 token 结算——实际<预扣则多退差额回补，实际>=预扣则少不补仅补记；请求失败异步 `ReturnPreConsumedQuota` 全额返还。

### 2. 前置条件
- 进入 relay 转发链路，已确定用户与令牌额度。
- 信任令牌阈值常量 `TrustQuota` 用于判定旁路。
- 预扣成功后冻结额度记入 `RelayInfo.FinalPreConsumedQuota`。

### 3. 主流程（对应 BL-2 节点 pc_in→pc_okend/pc_failend）
1. relay 转发前预扣（pc_in），判 `userQuota <= 0`（pc_q0）：是→403 额度不足 SkipRetry+NoRecordErrorLog（pc_403a）。
2. 否→判信任令牌（`userQuota>TrustQuota` 且令牌额度>TrustQuota）（pc_trust）：是（旁路）→`preConsumedQuota=0` 不预扣（pc_skip）；否→估算 `preConsumed = 估算token × 倍率`（pc_est）。
3. 判 `userQuota - preConsumed < 0`（pc_q1）：是→403 预扣费额度失败跳过重试（pc_403b）；否→扣减用户额度记 `FinalPreConsumedQuota`（pc_freeze）。
4. 转发上游调用（pc_call），判上游调用结果（pc_res）。
5. 失败→`ReturnPreConsumedQuota` 全额返还 `FinalPreConsumedQuota`（pc_refund）→失败已返还态余额复原（pc_failend）。
6. 成功→按真实 token 算实际消费（pc_real），判实际 vs 预扣（pc_diff）：实际<预扣→多退差额回补（pc_back）；实际>=预扣→少不补仅补记不再追扣（pc_nofix）。两路 `PostConsumeQuota` 落 Log + UseData（pc_settle）→结算完成态用量入账（pc_okend）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `userQuota <= 0` | pc_q0-是 | 403 + SkipRetry + NoRecordErrorLog | 额度不足，不转发不重试 |
| 信任令牌旁路 | pc_trust-是 | `preConsumedQuota=0` 不预扣 | 跳过预扣直接转发 |
| `userQuota - preConsumed < 0` | pc_q1-是 | 403 预扣失败，跳过重试 | 预扣费额度失败 |
| 上游调用失败 | pc_res-失败 | `ReturnPreConsumedQuota` 全额返还 | 余额复原 |
| 实际 < 预扣 | pc_diff-实际<预扣 | 多退差额回补用户额度 | 退回多扣部分 |
| 实际 >= 预扣 | pc_diff-实际>=预扣 | 少不补，仅补记不追扣 | 不再额外扣费 |

### 5. 数据对象（复用 DATA-MODEL §1 User + §2 Token + §5 Log）
- **预扣校验源** `User.Quota(quota)`（`userQuota<=0`→403）、`Token.RemainQuota(remain_quota)`/`Token.UnlimitedQuota(unlimited_quota)`；信任旁路阈值常量 `TrustQuota`。
- **冻结量** `RelayInfo.FinalPreConsumedQuota`：扣减 `User.Quota`，失败时按此值全额 `ReturnPreConsumedQuota`。
- **结算落库** `Log`：`Type(type)=2(Consume)`、`Quota(quota)`（真实消费）、`PromptTokens(prompt_tokens)`、`CompletionTokens(completion_tokens)`；失败返还落 `Type=6(Refund)`。

### 6. 验收标准
- [ ] `User.Quota<=0` 时发起请求 → 返回 403「额度不足」，SkipRetry 不触发跨渠道重试。
- [ ] `userQuota - preConsumed < 0` → 返回 403 预扣失败，跳过重试，不转发上游。
- [ ] `userQuota>TrustQuota` 且令牌额度>TrustQuota → `preConsumedQuota=0`，转发前不扣减 `User.Quota`。
- [ ] 上游调用失败 → `User.Quota` 复原为预扣前值（全额返还 `FinalPreConsumedQuota`）。
- [ ] 实际消费 < 预扣 → `User.Quota` 回补差额（预扣 - 实际），最终净扣 = 实际消费。
- [ ] 实际消费 >= 预扣 → 不再追扣，最终净扣 = 预扣值，`Log.Quota` 记真实消费。

### 7. 所触及页面状态（对齐 §H「预扣结算」）
余额不足 403 态（userQuota<=0 SkipRetry）· 信任令牌旁路态（preConsumedQuota=0）· 预扣失败 403 态（扣后为负跳过重试）· 额度已冻结态（记 FinalPreConsumedQuota）· 调用失败全额返还态（余额复原）· 多退差额回补态（实际<预扣）· 少不补态（实际>=预扣仅补记）· 结算完成态（Log+UseData 入账）。

---

## BL-3 订阅生命周期（开通 → 活跃判定 → 到期/作废 + 钱包兜底）

- **功能 ID / 优先级**：F-2046、F-2047 / P1
- **来源**：FC-063/FC-064（`model/subscription.go` `UserSubscription` 状态机、`GetAllActiveUserSubscriptions`/`HasActiveUserSubscription`（status=active AND end_time>now）、`AdminInvalidateUserSubscription`、`SubscriptionPreConsumeRecord` consumed/refunded、`subscription.go:844` overflow 守卫）
- **角色 / Owner**：登录用户（下单订阅）/ 系统（订阅维度预扣结算）/ 管理员（作废）；Owner 模块 = 计费与钱包
- **触发**：用户下订阅订单；订阅维度请求结算；到期/管理员作废

### 1. 场景
订阅是一个多态实体：用户下订阅订单生成 `UserSubscription`，状态在 active/expired/cancelled 间流转。**活跃判定 = status=active AND end_time>now**；到期（end_time<=now）自动转 expired；管理员 `AdminInvalidateUserSubscription` 置 cancelled 且 end_time=now 立即结束。订阅维度请求按 `BillingSource=subscription`（`SubscriptionId>0`）从订阅项预扣，记 consumed，失败将记录从 consumed 转 refunded 退回订阅额度。订阅额度耗尽时，`allow_wallet_overflow=true` 允许钱包兜底，为 false 则阻止兜底返回订阅额度不足。

### 2. 前置条件
- 订阅订单支付成功才生成 `UserSubscription`（`Status=active`）。
- 订阅维度请求需 `SubscriptionId>0`（`quota.go:409-410`）。
- 钱包兜底受 `AllowWalletOverflow` 控制。

### 3. 主流程（对应 BL-3 状态机迁移）
1. 创建订阅订单 → Pending；支付成功生成 `UserSubscription` → Active；支付失败/取消 → OrderFail（终态）。
2. Active --订阅维度请求 `BillingSource=subscription`--> Consuming；结算成功记 consumed → 回 Active；请求失败 consumed→refunded 退回订阅额度 → Refunded → 额度复原继续可用 → Active。
3. Active --`end_time<=now` 自动到期--> Expired（终态）。
4. Active --管理员作废置 `end_time=now` 立即结束--> Cancelled（终态）。
5. Active --订阅额度耗尽--> WalletGuard：`allow_wallet_overflow=true` 钱包兜底 → Active；`allow_wallet_overflow=false` → OverflowBlock 阻止兜底 → 返回订阅额度不足 → Active。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 支付失败/取消 | Pending→OrderFail | 不生成 UserSubscription | 下单失败终态 |
| 订阅请求失败 | Consuming→Refunded | 预扣记录 consumed→refunded 退回订阅额度 | 订阅额度复原 |
| `end_time<=now` | Active→Expired | 自动转 expired，不计入活跃 | 自动到期态 |
| 管理员作废 | Active→Cancelled | 置 cancelled + `end_time=now` 立即结束 | 管理员作废态 |
| 额度耗尽 + overflow=true | WalletGuard→Active | 回落钱包兜底继续可用 | 钱包兜底态 |
| 额度耗尽 + overflow=false | WalletGuard→OverflowBlock | 阻止用钱包兜底（subscription.go:844） | 返回订阅额度不足 |

### 5. 数据对象（复用 DATA-MODEL §8 Subscription）
- **写/读** `UserSubscription`：`UserId(user_id)`、`PlanId(plan_id)`、`AmountTotal(amount_total)`/`AmountUsed(amount_used)`、`StartTime(start_time)`/`EndTime(end_time)`、`Status(status)`（active/expired/cancelled）、`Source(source)`（order/admin）、`AllowWalletOverflow(allow_wallet_overflow)`。
- **活跃判定** = `Status=active` AND `EndTime>now`（`HasActiveUserSubscription`）。
- **套餐侧** `SubscriptionPlan.AllowWalletOverflow(allow_wallet_overflow)`（default true，配额耗尽后回落钱包）、`TotalAmount(total_amount)`（0=无限）；下单 `SubscriptionOrder.TradeNo(trade_no) unique`。
- **预扣记录** `SubscriptionPreConsumeRecord`：consumed（结算成功）↔ refunded（失败退回），关联 `SubscriptionId>0`。

### 6. 验收标准
- [ ] 订阅订单支付成功 → 生成 `Status=active` 的 `UserSubscription`；支付失败 → 不生成实例。
- [ ] `Status=active 且 EndTime>now` → `HasActiveUserSubscription` 判活跃为 true；`EndTime<=now` → 不计入活跃（视为 expired）。
- [ ] 订阅维度请求失败 → 对应 `SubscriptionPreConsumeRecord` 由 consumed 转 refunded，订阅额度退回。
- [ ] 管理员 `AdminInvalidateUserSubscription` → `Status=cancelled` 且 `EndTime=now`，订阅立即失效。
- [ ] 订阅额度耗尽且 `AllowWalletOverflow=true` → 请求改用钱包兜底继续可用。
- [ ] 订阅额度耗尽且 `AllowWalletOverflow=false` → 阻止钱包兜底，返回订阅额度不足。

### 7. 所触及页面状态（对齐 §H「订阅生命周期」）
订阅订单待支付态（Pending）· 下单失败态（支付失败/取消）· 活跃态（status=active AND end_time>now）· 订阅消费中态（BillingSource=subscription 预扣）· 订阅失败退回态（consumed→refunded）· 钱包兜底态（allow_wallet_overflow=true）· 兜底被阻态（overflow=false 订阅额度不足）· 自动到期态（end_time<=now expired）· 管理员作废态（cancelled end_time=now 立即结束）。

---

## BL-4 兑换码兑换（一次性 + 过期 + 已用守卫）

- **功能 ID / 优先级**：F-2045 / P2
- **来源**：FC-062（`model/redemption.go`（Quota/批量 Count/ExpiredTime）、`controller/redemption.go` 本地事务内校验入账并置已用）
- **角色 / Owner**：管理员（按 Quota 生成单个/批量）/ 登录用户（兑换）；Owner 模块 = 计费与钱包
- **触发**：用户提交兑换码

### 1. 场景
兑换码是无外部回调的本地事务式入账，与充值的两段式刻意不同。管理员按面额 `Quota` 生成单个或批量（`Count`）兑换码；用户兑换时在本地事务内逐关校验——码不存在/格式错则拒绝、已使用则不可重复兑换、已过期则拒绝；全部通过才把面额入账到 `User.Quota` 并将码置为已用（一次性）。所有校验在同一事务内完成，保证「入账」与「置已用」原子性，杜绝并发重复兑换。

### 2. 前置条件
- 用户已登录（过 C1）。
- 兑换码 `Key` 为 `char(32);uniqueIndex` 明文。
- 校验、入账、置已用在同一本地事务内完成。

### 3. 主流程（对应 BL-4 节点 rd_in→rd_ok）
1. 用户提交兑换码（rd_in），判码存在且格式合法（rd_find）。
2. 否→无效兑换码拒绝（rd_bad）；是→判码状态是否已使用（rd_used）。
3. 已使用→已被兑换不可重复（rd_dup）；否→判是否已过期（rd_exp）。
4. 已过期→过期码拒绝兑换（rd_expire）；否→额度入账 `user.Quota += code.Quota`（rd_credit）。
5. 事务内置码为已用（一次性）（rd_mark）→兑换成功态余额增加（rd_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 码不存在/格式错 | rd_find-否 | 拒绝兑换 | 无效兑换码态 |
| 码状态=已使用 | rd_used-是 | 拒绝，不重复入账 | 已被兑换态（不可重复） |
| 码已过期（`ExpiredTime` 已到且非 0） | rd_exp-是 | 拒绝兑换 | 过期码态 |
| 校验全过 | rd_credit→rd_mark | 入账 + 置已用（同事务） | 兑换成功，余额增加 |

### 5. 数据对象（复用 DATA-MODEL §6 Redemption + §1 User）
- **读校验** `Redemption`：`Key(key)`（`char(32);uniqueIndex` 查存在与格式）、`Status(status)`（1=未使用，已使用则拒绝）、`ExpiredTime(expired_time)`（`0=不过期`，已到拒绝）。
- **入账** `User.Quota(quota)` `+= Redemption.Quota(quota)`（面额，quota 单位，`default:100`）。
- **置已用（事务内）** `Redemption.Status(status)`→已使用、`RedeemedTime(redeemed_time)` 写核销时间、`UsedUserId(used_user_id)`=核销人；生成侧 `Count(count)`（`-:all` 仅批量生成用）。

### 6. 验收标准
- [ ] 提交不存在或格式非法的码 → 返回无效兑换码，`User.Quota` 不变。
- [ ] 提交 `Status` 已为已使用的码 → 拒绝，不重复入账。
- [ ] 提交 `ExpiredTime` 已过（非 0）的码 → 返回过期码拒绝。
- [ ] 提交有效码 → `User.Quota += Redemption.Quota`，同事务内 `Redemption.Status` 置已用、写 `RedeemedTime`、`UsedUserId`。
- [ ] 同一有效码并发提交两次 → 仅一次入账成功，另一次因 `Status` 已为已使用被拒。

### 7. 所触及页面状态（对齐 §H「兑换码」）
兑换码输入态 · 无效码态（不存在/格式错）· 重复兑换态（已使用）· 过期码态（拒绝）· 兑换成功态（额度入账码置已用）。

---

## BL-5 阶梯/表达式计费配置与冻结快照结算

- **功能 ID / 优先级**：F-2040、F-2041 / P0（F-2041）/ P1（F-2040）
- **来源**：FC-057（`service/tiered_settle.go BuildTieredTokenParams`/`TryTieredSettle`、`pkg/billingexpr`、变量 p/c/cr/cc/img/ai/ao/len 与函数 tier/param/header/has/hour、`RelayInfo.TieredBillingSnapshot`）
- **角色 / Owner**：管理员（配表达式）/ 系统（冻结快照·结算）；Owner 模块 = 计费与钱包
- **触发**：管理员配置模型表达式；请求进入计费冻结快照并在上游返回后重算

### 1. 场景
表达式计费允许管理员为模型配一条 `billingexpr` 表达式定义全部计价逻辑（变量 p/c/cr/cc/img/ai/ao/len，函数 tier/param/header/has/hour）。`tier` 按 `len` 分档切单价；`p/c` 在引用子类变量（cr/cc/img）时自动排除该子类避免重复计价；表达式版本化 `v1:`、中间负值归零。请求前冻结 `TieredBillingSnapshot`（含 BillingMode/GroupRatio/QuotaPerUnit/EstimatedTier），结算时 `TryTieredSettle` 仅当 `snap.BillingMode==tiered_expr` 用真实 token 重算，否则返回 false 走旧倍率逻辑。这是配置侧与结算侧并存的双轨判定。

### 2. 前置条件
- 管理员配置的表达式需语法/版本（`v1:`）合法才能保存为 `tiered_expr` 模式。
- 请求进入计费时冻结 `TieredBillingSnapshot`，记 `EstimatedTier`。
- 结算用请求前冻结快照（避免倍率中途变更影响）。

### 3. 主流程（对应 BL-5 节点 bx_cfg→bx_ok/bx_legend）
1. 管理员配置模型表达式（bx_cfg），判表达式语法/版本 `v1:` 是否合法（bx_parse）。
2. 否→表达式校验失败拒绝保存（bx_perr）；是→保存 `tiered_expr` 配置（bx_save）。
3. 请求进入计费（bx_req）→冻结 `TieredBillingSnapshot` 记 `EstimatedTier`（bx_snap）→转发上游获取真实 token（bx_call）。
4. 判 `snap.BillingMode == tiered_expr`（bx_try）：否→`TryTieredSettle` 返回 false 走旧倍率结算（bx_legacy）→倍率模式结算态（bx_legend）。
5. 是→判真实 `len` 落在哪一档（bx_tier）：低档→按低档单价（bx_t1）；跨阈值高档→按高档单价切档（bx_t2）。
6. 两路 `p/c` 自动扣除已单列子类 cr/cc/img（bx_excl）→判中间结果 < 0（bx_neg）：是→负值归零（bx_zero）；否→保留计算值（bx_keep）。
7. 两路用快照倍率重算实际额度（bx_final）→表达式结算完成态（bx_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 表达式语法/版本非法 | bx_parse-否 | 拒绝保存配置 | 表达式校验失败态 |
| `snap.BillingMode != tiered_expr` | bx_try-否 | `TryTieredSettle` 返回 false | 走旧倍率结算态 |
| 真实 `len` 跨阈值 | bx_tier-高档 | 按高档单价切档计 | 高档切档计价 |
| 引用 cr/cc/img 子类 | bx_excl | p/c 自动扣除已单列子类 | 子类排除态（防重复计价） |
| 中间结果 < 0 | bx_neg-是 | 负值归零 | 负值归零态 |

### 5. 数据对象（复用 DATA-MODEL 计费上下文 + §5 Log）
- **配置侧** 模型 `billingexpr` 表达式（`tiered_expr` 模式标记，版本 `v1:`），变量 `TokenParams(P/C/Len/CR/CC/CC1h/Img/ImgO/AI/AO)`。
- **冻结快照** `RelayInfo.TieredBillingSnapshot`：`BillingMode`（== `tiered_expr` 才重算）、`GroupRatio`、`QuotaPerUnit`、`EstimatedTier`。
- **结算落库** `Log`：`Type(type)=2(Consume)`、`Quota(quota)`（快照重算后的实际额度）、`PromptTokens(prompt_tokens)`/`CompletionTokens(completion_tokens)`。

### 6. 验收标准
- [ ] 表达式语法非法或缺 `v1:` 版本前缀 → 拒绝保存，返回校验失败。
- [ ] 配 `tier` 按 `len` 分档表达式，真实 `len` 跨阈值 → 单价切到高档计算。
- [ ] 表达式引用 `cr`（缓存读 token）→ `p` 计价时自动扣除该子类 token，不重复计价。
- [ ] 计算中间结果为负 → 归零，最终额度不为负。
- [ ] 请求模型 `BillingMode != tiered_expr` → `TryTieredSettle` 返回 false，走旧倍率结算态。
- [ ] 请求模型为 `tiered_expr` → 用冻结快照（`GroupRatio/QuotaPerUnit`）按真实 token 重算并落 `Log.Quota`。

### 7. 所触及页面状态（对齐 §H「表达式/阶梯计费」）
表达式配置编辑态 · 表达式校验失败态（语法/版本非法）· 快照冻结态（记 EstimatedTier）· 旧倍率结算态（非 tiered_expr TryTieredSettle=false）· 低档计价态 / 高档切档态（按真实 len 分档）· 子类排除态（p/c 扣除 cr/cc/img）· 负值归零态 · 表达式结算完成态（快照重算实际额度）。

---

## BL-6 基础倍率计费（模型倍率 × 分组倍率 × 补全倍率）

- **功能 ID / 优先级**：F-2038 / P0
- **来源**：FC-057（`relay/helper/price.go HandleGroupRatio`/`ModelPriceHelper`、`service/quota.go CalculateQuota`、`setting/ratio_setting` `GetModelRatio`/`GetGroupRatio`/`GetGroupGroupRatio`/`GetCompletionRatio`、常量 `common.QuotaPerUnit=500000`）
- **角色 / Owner**：系统（relay 计费链路按倍率折算 quota）；Owner 模块 = 计费与钱包
- **触发**：非 `model_price`（按次）、非 `tiered_expr`（表达式）模型的每次 relay 调用，预扣前估价与上游返回后结算各计一次倍率折算

### 1. 场景
这是按 token 计费模型的**默认计价规则**，BL-2 预扣/结算流入的「估算 token × 倍率」「真实 token × 倍率」中的「倍率」就由本块定义，BL-5 表达式计费仅在 `BillingMode==tiered_expr` 时取代它。计价倍率是三段乘积：`模型倍率(model_ratio)` 决定该模型相对基准（`1 === $0.002/1K tokens`）的单价；`分组倍率(group_ratio)` 按用户/令牌所属分组缩放（VIP 折扣或溢价）；`补全倍率(completion_ratio)` 单独放大输出 token（同一模型输出常比输入贵）。结算公式（`service/quota.go`）：先把输入 token 与「输出 token × completion_ratio」相加得「等效输入 token 数」，再乘 `model_ratio × group_ratio` 得最终 quota——即 `quota = (prompt_tokens + completion_tokens × completion_ratio) × model_ratio × group_ratio`。预扣阶段（`ModelPriceHelper`）用估算 token 走简化式 `估算token × (model_ratio × group_ratio)` 冻结额度，真实结算时再按上式重算并经 BL-2 多退少不补回补差额。本块的核心守卫是「倍率缺失兜底」与「非零倍率最小计费 1」。

### 2. 前置条件
- 模型未命中 `model_price`（按次价）且 `BillingMode != tiered_expr`（否则走 BL-5），即落入 `!usePrice` 的倍率分支。
- 已确定用户分组 `UsingGroup`（`auto` 分组经 CH-5 选组后落定，见 `HandleGroupRatio` 读 `auto_group` 上下文键）。
- 基准换算常量 `common.QuotaPerUnit=500000`（`$0.002/1K tokens` → 1 quota 单位）已装载。

### 3. 主流程（对应 BL-6 节点 br_in→br_ok）
1. relay 进入计价（br_in），判模型是否命中 `model_price` 或 `tiered_expr`（br_mode）：是→不走本块（br_skip，移交 BL-5/按次）；否→取倍率（br_take）。
2. `GetModelRatio(model)` 取模型倍率（br_mr）、`HandleGroupRatio` 取分组倍率（br_gr：优先 `GetGroupGroupRatio(userGroup,usingGroup)` 特殊倍率，未命中回落 `GetGroupRatio(usingGroup)`）、`GetCompletionRatio(model)` 取补全倍率（br_cr）。
3. 预扣阶段（br_pre）：`preConsumed = 估算token × (model_ratio × group_ratio)` 冻结额度（经 BL-2 节点 pc_est）。
4. 上游返回后按真实 token 算等效输入（br_eff）：`等效 = prompt_tokens + completion_tokens × completion_ratio`（含音频等子项按各自子倍率叠加，见数据对象）。
5. 乘合成倍率得 quota（br_calc）：`quota = 等效 × (model_ratio × group_ratio)`。
6. 判 `ratio != 0 且 quota <= 0`（br_floor）：是→`quota = 1`（非零倍率最小计费 1，br_one）；否→保留计算值（br_keep）。
7. 两路落 `Log`（Quota/PromptTokens/CompletionTokens）并经 BL-2 多退少不补结算（br_settle）→倍率结算完成态（br_ok）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 模型命中 `model_price`/`tiered_expr` | br_mode-是 | 不走倍率计价 | 移交按次/表达式计费（BL-5） |
| 模型无倍率配置（`GetModelRatio` 未命中） | br_mr-缺失 | 非自用模式→`modelPriceNotConfiguredError` 拒绝；自用模式 `SelfUseModeEnabled`→兜底 `model_ratio=37.5`；`AcceptUnsetRatioModel=true`→放行按缺失倍率续算 | 模型未定价拒绝态 / 自用兜底态 |
| 分组倍率缺失（`GetGroupRatio` 未命中） | br_gr-缺失 | 记日志 `group ratio not found` 并回落 `group_ratio=1` | 兜底按 1 倍分组计价（不阻断） |
| 分组特殊倍率命中 | br_gr-特殊 | 用 `GetGroupGroupRatio(userGroup,usingGroup)` 覆盖普通分组倍率 | 特殊分组倍率计价 |
| `ratio != 0` 但算得 `quota <= 0` | br_floor-是 | `quota = 1` 最小计费 | 非零倍率至少扣 1 |
| `group_ratio == 0` 或 `model_ratio == 0` | br_floor-否（合成为 0） | quota=0，标 freeModel 不预扣（`EnableFreeModelPreConsume=false` 时） | 免费模型不扣费 |

### 5. 数据对象（复用 DATA-MODEL §1 User + §2 Token + §5 Log；倍率为 `setting/ratio_setting` 运行时配置非 GORM 字段）
- **倍率配置源**（KV / 内存 map，经 DATA-MODEL §15 Option 持久化）：`model_ratio`（模型→倍率 map，`GetModelRatio` 未命中返回 `37.5, SelfUseModeEnabled`）、`group_ratio`（分组→倍率 map，`GetGroupRatio` 未命中返回 `1`）、`group_group_ratio`（`userGroup→{usingGroup→倍率}` 特殊倍率，`GetGroupGroupRatio` 未命中返回 `-1,false`）、`completion_ratio`（模型→补全倍率 map）。
- **分组取值键** `User.Group(group) varchar(64);default:'default'`、`Token.Group(group)`（`auto` 经 CH-5 选组落 `UsingGroup`）——作为 `group_ratio` 查表键。
- **计价基准常量** `common.QuotaPerUnit=500000`（`$0.002/1K tokens` → 1 quota）。
- **结算落库** `Log`：`Quota(quota) int default:0`（= `(prompt + completion×completion_ratio) × model_ratio × group_ratio`，非零倍率下限 1）、`PromptTokens(prompt_tokens) int`、`CompletionTokens(completion_tokens) int`、`ModelName(model_name)`、`Group(group)`；`Type(type)=2(Consume)`。
- **额度落账** `User.Quota(quota)` 按算得 quota 扣减（经 BL-2 预扣/回补），`User.UsedQuota(used_quota)` 累加。

### 6. 验收标准
- [ ] `model_ratio=2`、`group_ratio=1.5`、`completion_ratio=1`（纯输入 token）→ 单 token 计费 = `1 × 2 × 1.5 = 3` 倍基准（合成倍率 = 3）。
- [ ] `model_ratio=2`、`group_ratio=1.5`、`completion_ratio=4`，`prompt_tokens=100`、`completion_tokens=50` → 等效输入 = `100 + 50×4 = 300`，`Log.Quota = 300 × 2 × 1.5 = 900`。
- [ ] 模型无倍率配置且非自用模式、`AcceptUnsetRatioModel=false` → 返回 `modelPriceNotConfiguredError` 拒绝，不扣费。
- [ ] 模型无倍率配置且 `SelfUseModeEnabled=true` → 兜底 `model_ratio=37.5` 续算（不拒绝）。
- [ ] 分组在 `group_ratio` 表中缺失 → 回落 `group_ratio=1`（计费不为 0、不报错），SysLog 记 `group ratio not found`。
- [ ] `userGroup→usingGroup` 命中 `group_group_ratio` → 用该特殊倍率覆盖普通 `group_ratio` 参与合成。
- [ ] 合成 `model_ratio × group_ratio != 0` 但算得 `quota <= 0`（极小 token）→ `Log.Quota` 落 `1`（最小计费）。
- [ ] `group_ratio=0` 或 `model_ratio=0` → quota=0、标 freeModel，`EnableFreeModelPreConsume=false` 时不预扣。

### 7. 所触及页面状态（对齐 §H「基础倍率计费」系统内部态）
倍率计价入口态（非 model_price 非 tiered_expr）· 取倍率态（model/group/completion 三段）· 分组特殊倍率态（group_group_ratio 命中）· 分组倍率兜底态（缺失回落 1）· 模型未定价拒绝态（非自用 + 不接受未设倍率）· 自用兜底态（model_ratio=37.5）· 预扣冻结态（估算token×model×group）· 真实结算态（等效输入×model×group）· 非零倍率最小计费态（quota 置 1）· 免费模型态（合成倍率 0 不预扣）· 倍率结算完成态（落 Log.Quota）。

---

## BL-7 成本/售价分离（售价挂对外模型 A 恒定 + 成本挂渠道×B 随实际供应商）

- **功能 ID / 优先级**：F-2060、F-2061 / P0
- **来源**：COMPAT-BILLING-DECISIONS §4（成本/售价分离、兜底切供应商售价恒定成本跟实际）；BILLING-MODEL-ARCHITECTURE §1.4/§2/§6（售价挂 A 复用 GetModelRatio、成本挂渠道×B、ADR-BILL-01a 视图聚合、ADR-BILL-03/04、链路第 8/16/17 步）；DATA-MODEL §16 PublicModel / §19 ChannelModelCost / §5 Log（quota_sell/quota_cost）
- **角色 / Owner**：超管（分两处配售价/成本）/ 系统（计费链路分别取值）；Owner 模块 = 计费与钱包
- **触发**：超管在「成本/售价配置页」分两处配（对外模型基准售价 + 供应商成本倍率）；relay 计费链路售价取 A、成本取实际渠道×B

### 1. 场景
售价与成本彻底分离，两处配、各取各的（DECISIONS §4，补上的核心漏洞）：
- **售价挂「对外模型 A」**：是公开站展示的**基准价（模型级定价）**，对客户恒定，不管内部走了哪个供应商。口径 = `PublicModel.BasePriceRatio(A)`（= 现网 `GetModelRatio(A)`，prd-model ML-6 保存时同步刷 `model_ratio` KV，ADR-BILL-01a 视图聚合，计费链路零改动）。售价金额落 `Log.quota_sell`（与现有 `Quota` 同口径）。
- **成本挂「供应商渠道 × 真实模型 B」**：同一模型不同供应商成本不同，各记各的（prd-channel CH-7 `ChannelModelCost`，超管手填倍率）。
- **每笔请求**：实际走了哪个供应商，就用那个供应商的成本记账（结算阶段用 `(实际选中 ChannelId, L2 后 B)` 主键取 `cost_ratio`）；收客户钱按「对外模型基准价 × 分组折扣系数」（见 BL-8）。
- **兜底切供应商时售价恒定**（DECISIONS §4）：客户付的钱只看对外模型 A + 分组等级，不随内部切换波动；prd-channel CH-6 容灾切换后 `ChannelId` 变 → 成本自动取到新渠道的行，**售价不变、成本跟实际供应商走**。
- **成本缺失兜底**：`(ChannelId, B)` 无成本行或 `Enabled=false` → `quota_cost=0` + 标「成本缺失」告警，**不阻断计费**（售价照扣，`Other` 写 `{cost_missing:true}`）。

本块明确：BL-6 倍率计费的「模型倍率」在兼容层背景下其取值键 = **对外模型 A**（= `info.ResolvedPublicModel`，prd-model ML-7 L2 前的 A），与「内部实际走哪个 B/哪个渠道」解耦；成本是 BL-6 之外新增的、与售价分离的独立记账维度。

### 2. 前置条件
- 售价取值键 = A（prd-model ML-7 L1 后、L2 前的 `ResolvedPublicModel`），不是 C、不是 B。
- 成本取值时机 = 结算阶段，主键 = `(CH-6 选中实际 ChannelId, ML-7 L2 后 B)`（prd-channel CH-7）。
- 售价/成本分两处配：对外模型基准售价（prd-model ML-6）与供应商成本倍率（prd-channel CH-7），改完即时生效（内存缓存写时失效）。

### 3. 主流程（对应 BL-7 节点 cs_in→cs_log）
1. relay 计费（cs_in）→**售价侧**按对外模型 A 取基准售价倍率 `GetModelRatio(A)`（cs_sell，恒定不随渠道变）。
2. 选渠（prd-channel CH-6）确定实际 `ChannelId`（cs_chan）→**成本侧**结算阶段用 `(ChannelId, L2 后 B)` 取 `cost_ratio`（cs_cost）。
3. 判成本行是否命中且 Enabled（cs_hit）→否（缺失）→`quota_cost=0` + 告警不阻断（cs_miss）；是→取实际渠道成本（cs_take）。
4. 判本次是否发生兜底切供应商（cs_failover）→是则 `ChannelId` 已变、成本自动跟新渠道、**售价仍取 A 恒定**（cs_const）。
5. 两路落 `Log`：`quota_sell`（售价，= tokens×ModelRatio(A)×GroupRatio）+ `quota_cost`（成本，= tokens×CostRatio(channel,B)）（cs_log）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 售价取值 | cs_sell | 按 A 取 `GetModelRatio(A)` 恒定 | 售价挂 A 恒定（不随渠道） |
| 成本取值 | cs_cost | 按 `(ChannelId, B)` 取 `cost_ratio` | 成本挂渠道×B 各记各的 |
| 兜底切供应商 | cs_failover-是 | ChannelId 变、成本跟新渠道、售价不变 | 售价恒定成本随实际供应商 |
| 成本行缺失/禁用 | cs_hit-否 | `quota_cost=0` + 告警，不阻断 | 成本缺失态（售价照扣） |
| 同一 A 多供应商 | cs_cost | 各渠道各取各自成本行 | 多供应商成本分离 |

### 5. 数据对象（复用 DATA-MODEL §16 PublicModel + §19 ChannelModelCost + §5 Log）
- **售价侧（挂 A，恒定）** `PublicModel.BasePriceRatio(base_price_ratio)`（口径 = `model_ratio`，保存同步刷 KV，prd-model ML-6）→ 计费经 `GetModelRatio(A)` 取，A=`Log.resolved_public_model`；售价金额落 `Log.QuotaSell(quota_sell)`（与 `Quota` 同口径，**客户可见**）。
- **成本侧（挂 channel×B，随实际）** `ChannelModelCost.CostRatio(cost_ratio)`（超管手填，prd-channel CH-7）+ `CompletionCostRatio`，主键 `(channel_id, upstream_model=B)`；成本金额落 `Log.QuotaCost(quota_cost)`（**仅 admin/root**，缺失=0+告警）。
- **取值键** `Log.ResolvedPublicModel(resolved_public_model)=A`（售价键）、`Log.ActualUpstreamModel(actual_upstream_model)=B` + `Log.ChannelId(channel)`（成本键，仅 admin/root）。

### 6. 验收标准
- [ ] 售价金额 `quota_sell` = `tokens × GetModelRatio(A) × GroupRatio`，A = `resolved_public_model`，与内部实际走哪个 B/渠道无关（恒定）。
- [ ] 成本金额 `quota_cost` 按 `(实际选中 ChannelId, L2 后 B)` 取 `cost_ratio` 计算，同一 A 不同供应商成本不同各记各的。
- [ ] prd-channel CH-6 兜底切供应商（X→Y）后 → `quota_sell` 不变（售价挂 A），`quota_cost` 自动改用 Y 的 `cost_ratio`（成本跟实际供应商走）。
- [ ] `(ChannelId, B)` 无成本行或 `Enabled=false` → `quota_cost=0`、标 `cost_missing` 告警，**售价照扣不阻断**。
- [ ] 售价配在 prd-model ML-6（对外模型基准售价）、成本配在 prd-channel CH-7（供应商成本倍率），分两处，改完即时生效。
- [ ] 成本字段 `quota_cost` 仅 admin/root 可见，客户视图（UserLogView）不含。

### 7. 所触及页面状态（对齐 §H「成本/售价分离」系统内部态）
售价取值态（按 A 恒定 GetModelRatio(A)）· 成本取值态（按 ChannelId×B 取 cost_ratio）· 兜底切供应商售价恒定态（ChannelId 变成本跟新渠道）· 成本缺失不阻断态（quota_cost=0+告警）· 多供应商成本分离态 · 售价/成本落 Log 态（quota_sell + quota_cost 终态）。

---

## BL-8 最终扣费 = 对外模型基准价 × 分组折扣系数（分组退化为纯折扣）

- **功能 ID / 优先级**：F-2062 / P0
- **来源**：COMPAT-BILLING-DECISIONS §6（分组纯折扣 free=1.0/vip=0.85/svip=0.7、最终扣费=基准价×折扣系数）+ §5（模型全开、分组不控权限）；BILLING-MODEL-ARCHITECTURE §3（复用 GetGroupRatio 语义收窄、ADR-BILL-06、§3.2 扣费公式落 ModelPriceHelper）
- **角色 / Owner**：系统（按分组折扣折算售价）/ 超管（配折扣系数）；Owner 模块 = 计费与钱包
- **触发**：每次按量 relay 调用，预扣前估价与结算时各按「基准价 × 分组折扣」算一次售价金额

### 1. 场景
**分组退化为纯折扣等级**（DECISIONS §6），不再定价、不再控权限（模型全开见 prd-model ML-8）。落地 = **复用** `setting/ratio_setting/group_ratio.go` 的 `group_ratio` KV + `GetGroupRatio(name)`，语义从「权限+定价分组」收窄为「**纯折扣系数**」：示例 `free=1.0 / vip=0.85 / svip=0.7`（后台可配，落 `group_ratio` KV）。砍掉「分组圈模型」（停用 `GroupSpecialUsableGroup` 圈模型用途，ADR-BILL-06），`User.Group` 字段不动、语义收窄为纯折扣等级；升级方式按充值额/付费包等级自动升档（复用 Subscription `UpgradeGroup/DowngradeGroup`，BL-3）。
**最终扣费公式（DECISIONS §6）**：
```
最终扣费 quota_sell = 对外模型基准价 × 分组折扣系数
                    = BasePriceRatio(A) × GetGroupRatio(UsingGroup) × tokens 计费
```
对应现网 `ModelPriceHelper`：`ratio := modelRatio(A) × groupRatioInfo.GroupRatio`——**已经是这个公式**，无需改计算（与 BL-6 倍率乘积同构），只需保证 `modelRatio` 读的是 A、`GroupRatio` 读的是 `UsingGroup` 的折扣系数。公开站展示基准价（折扣系数=1 口径）；客户登录后按自己等级看折后价。本块是把 BL-6 的「分组倍率」语义明确为「纯折扣等级」，并钉死「最终扣费 = 基准价 × 折扣」这一对客户结算口径。

### 2. 前置条件
- `group_ratio` KV 配为折扣系数（free=1.0/vip=0.85/svip=0.7，后台可配，≤1 为折扣）。
- 售价取值键 = A（BL-7），折扣键 = `UsingGroup`（`auto` 经 prd-channel CH-5 选组后落定）。
- 分组不再圈模型（模型全开，prd-model ML-8）；分组只剩折扣语义。

### 3. 主流程（对应 BL-8 节点 dc_in→dc_settle）
1. relay 计价（dc_in）→取对外模型 A 基准价 `GetModelRatio(A)`（dc_base，BL-7 售价侧）。
2. 取分组折扣系数 `GetGroupRatio(UsingGroup)`（dc_disc）→判分组在 `group_ratio` 是否命中（dc_hit）→缺失回落 1.0 不阻断（dc_default）；命中取折扣值（dc_take）。
3. 合成扣费倍率 `BasePriceRatio(A) × GroupRatio`（dc_mul）→预扣阶段按估算 token 冻结（dc_pre，经 BL-2/BL-6）。
4. 结算按真实 token 算 `quota_sell = tokens × BasePriceRatio(A) × GroupRatio`（dc_settle）→经 BL-2 多退少不补落账。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 分组折扣命中（free/vip/svip） | dc_hit-是 | 取 `GetGroupRatio(UsingGroup)` | 按等级折扣计价 |
| 分组在 `group_ratio` 缺失 | dc_hit-否 | 回落 `group_ratio=1.0` 不阻断 | 兜底无折扣（基准价） |
| free 分组（=1.0） | dc_take | 折扣系数 1.0 | 不打折 = 基准价 |
| svip 分组（=0.7） | dc_take | 折扣系数 0.7 | 七折 |
| 模型可用性 | （不在本块） | 分组不控模型（全开，ML-8） | 分组只影响价格不影响可用模型 |

### 5. 数据对象（复用 DATA-MODEL §1 User + §16 PublicModel + §5 Log；`group_ratio` 为运行时配置）
- **基准价键** `PublicModel.BasePriceRatio(A)`（= `GetModelRatio(A)`，BL-7 售价侧，对客户恒定）。
- **折扣系数源** `group_ratio` KV（`GetGroupRatio(UsingGroup)`，语义=纯折扣 free=1.0/vip=0.85/svip=0.7，缺失回落 1.0）；分组键 `User.Group(group) varchar(64);default:'default'` / `Token.Group(group)`（`auto` 经 CH-5 落 `UsingGroup`），语义收窄为纯折扣等级（不再圈模型）。
- **升档** 复用 `SubscriptionPlan.UpgradeGroup/DowngradeGroup`（BL-3）按充值额/付费包自动升降档。
- **扣费落库** `Log.QuotaSell(quota_sell)` = `tokens × BasePriceRatio(A) × GetGroupRatio(UsingGroup)`（= 现有 `Quota` 口径，客户可见）。

### 6. 验收标准
- [ ] `free` 分组（系数 1.0）→ 最终扣费 = 对外模型基准价（不打折）。
- [ ] `vip` 分组（系数 0.85）→ 最终扣费 = 基准价 × 0.85；`svip`（0.7）→ 基准价 × 0.7。
- [ ] 最终扣费 `quota_sell` = `tokens × BasePriceRatio(A) × GetGroupRatio(UsingGroup)`，A=`resolved_public_model`。
- [ ] 分组在 `group_ratio` 缺失 → 回落折扣系数 1.0（按基准价计），不报错不阻断。
- [ ] 分组只影响价格、**不影响可用模型**（模型全开，prd-model ML-8）；停用 `GroupSpecialUsableGroup` 圈模型。
- [ ] 公开站展示基准价（折扣=1 口径）；客户登录后按自身分组等级看折后价。
- [ ] 充值额/付费包升档 → `User.Group` 改变 → 后续请求按新等级折扣系数计价（复用 Subscription UpgradeGroup）。

### 7. 所触及页面状态（对齐 §H「分组折扣扣费」系统内部态）
取基准价态（BasePriceRatio(A) 恒定）· 取分组折扣态（GetGroupRatio(UsingGroup)）· 折扣命中计价态（free/vip/svip）· 折扣缺失回落 1.0 态 · 合成扣费倍率态（基准价×折扣）· 预扣冻结态 · 最终扣费结算态（quota_sell 落 Log 终态）· 分组只控价不控模型态（全开背景）。

---

## BL-9 利润逐笔可算 + 利润看板（按模型/供应商/分组聚合）

- **功能 ID / 优先级**：F-2063、F-2064 / P0（F-2063）/ P1（F-2064）
- **来源**：COMPAT-BILLING-DECISIONS §9（利润分析看板 admin、按模型/供应商/分组看利润）+ §4（利润=售价-成本逐笔可算）+ §8（Log 记售价/成本/利润、视图裁剪）；BILLING-MODEL-ARCHITECTURE §5（Log 利润字段扩展、ADR-BILL-02 成本不打折扣、§5.3 看板数据源=Log 聚合、ADR-BILL-07）；DATA-MODEL §5 Log（quota_sell/quota_cost/quota_profit + 视图 DTO）
- **角色 / Owner**：系统（逐笔算利润落 Log）/ 超管·管理员（看利润看板）；Owner 模块 = 计费与钱包
- **触发**：每笔请求结算时算 `利润=售价-成本` 落 Log；admin 打开利润看板按维度聚合

### 1. 场景
**利润 = 售价 − 成本，逐笔可算、可按模型/供应商/分组聚合**（DECISIONS §4/§9）。结算阶段（链路第 18 步）算三个金额冗余落 `Log`（便于看板直接聚合，避免每次重算）：
- `quota_sell`（售价金额，= BL-8 最终扣费，**乘 GroupRatio** = 客户实付，客户可见）。
- `quota_cost`（成本金额，= `tokens × CostRatio(channel,B)`，**不乘 GroupRatio**，ADR-BILL-02；理由：分组折扣是「卖给客户的让利」与我方进货成本无关）。
- `quota_profit = quota_sell − quota_cost`（逐笔利润，可为负=亏损告警；落库冗余列，仅 admin/root 可见）。
→ **利润随分组折扣变化**：svip(0.7) 客户利润更薄，这正是看板要暴露的经营信号。
**利润看板（admin，DECISIONS §9）数据源 = Log 表聚合**（落字段后直接 GROUP BY，不另建数仓，ADR-BILL-07）。推荐聚合维度（均为 Log 可索引列）：
- 按**模型**：`GROUP BY resolved_public_model(A)` → 看哪个对外模型利润薄/定价偏低。
- 按**供应商**：`GROUP BY channel(channel_id)` + join channel_name → 看哪个供应商在亏、该给谁多导流量。
- 按**分组**：`GROUP BY group` → 看哪个等级折扣过狠。
- 度量：`SUM(quota_sell)`/`SUM(quota_cost)`/`SUM(quota_profit)`/利润率 `SUM(profit)/SUM(sell)`。
**视图裁剪（B 不可见 + 成本不可见，DECISIONS §8）**：`UserLogView` 只给 C/A + `quota_sell`（客户视角实付），**无 B、无成本/利润**；`AdminLogView` 给全链 C→A→B + 协议 + 售价/成本/利润 + channel_id（利润看板数据源）。三库兼容用 GORM `Select(...).Group(...)`，避免 MySQL-only 函数。

### 2. 前置条件
- 结算阶段已有 `quota_sell`（BL-7/BL-8 售价侧）与 `quota_cost`（BL-7/prd-channel CH-7 成本侧）。
- Log 聚合列已建/补建 index（`resolved_public_model`/`channel`/`group`/`created_at`）。
- 利润看板仅 admin/root 可访问；成本/利润字段仅 admin/root 可见。

### 3. 主流程（对应 BL-9 节点 pf_settle→pf_board / 看板 pf_query→pf_view）
1. 结算（pf_settle）→算售价 `quota_sell`（乘 GroupRatio，pf_sell）+ 算成本 `quota_cost`（不乘 GroupRatio，pf_cost）。
2. 算利润 `quota_profit = quota_sell − quota_cost`（pf_profit）→判是否为负（pf_neg）→负则标亏损告警（pf_alarm）；落 Log 三金额冗余列（pf_log）。
3.（看板侧）admin 打开利润看板（pf_query）→按维度 GROUP BY（模型/供应商/分组，pf_group）→算度量 SUM/利润率（pf_metric）→按角色选 DTO 渲染（pf_view，AdminLogView 含全链+成本利润）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 算售价 | pf_sell | `quota_sell` 乘 GroupRatio（客户实付） | 售价含折扣 |
| 算成本 | pf_cost | `quota_cost` 不乘 GroupRatio（ADR-BILL-02） | 成本只看进货 |
| 利润为负 | pf_neg-是 | `quota_profit<0` 标亏损告警 | 亏损告警（经营信号） |
| 成本缺失 | （承 BL-7） | `quota_cost=0` → `quota_profit=quota_sell` | 利润虚高告警 cost_missing |
| 看板按模型聚合 | pf_group-模型 | `GROUP BY resolved_public_model(A)` | 各对外模型利润/利润率 |
| 看板按供应商聚合 | pf_group-供应商 | `GROUP BY channel` + join name | 各供应商盈亏（导流量决策） |
| 看板按分组聚合 | pf_group-分组 | `GROUP BY group` | 各等级折扣盈亏 |
| 客户访问利润数据 | pf_view-user | UserLogView 裁掉成本/利润/B | 客户看不到成本利润 |

### 5. 数据对象（复用 DATA-MODEL §5 Log + 视图 DTO）
- **逐笔三金额** `Log.QuotaSell(quota_sell)`（售价=实付，客户可见）、`Log.QuotaCost(quota_cost)`（成本=tokens×CostRatio 不乘折扣，仅 admin/root）、`Log.QuotaProfit(quota_profit)=quota_sell−quota_cost`（利润，可为负，仅 admin/root）。
- **聚合维度键** `Log.ResolvedPublicModel(resolved_public_model)=A`（按模型）、`Log.ChannelId(channel)`+`ChannelName`（按供应商）、`Log.Group(group)`（按分组）、`Log.CreatedAt(created_at)`（时间维，均 index）。
- **视图裁剪 DTO** `UserLogView`（C/A + quota_sell，**无 B/成本/利润**）/ `AdminLogView`（全链 C→A→B + 协议 + 售价/成本/利润 + channel_id，看板数据源）。
- **成本缺失标记** `Log.Other` 写 `{cost_missing:true}`（成本=0 时利润虚高，看板告警，承 BL-7）。

### 6. 验收标准
- [ ] 每笔结算 → `quota_profit = quota_sell − quota_cost` 逐笔落 Log（冗余列，看板直接聚合不重算）。
- [ ] `quota_sell` 乘 GroupRatio（客户实付）、`quota_cost` **不乘** GroupRatio（ADR-BILL-02）→ svip 客户利润比 free 客户更薄（折扣影响利润）。
- [ ] 利润看板按 `resolved_public_model(A)` 聚合 → 识别哪个对外模型利润薄/定价偏低。
- [ ] 利润看板按 `channel(channel_id)` 聚合 → 识别哪个供应商在亏、该给谁导流量。
- [ ] 利润看板按 `group` 聚合 → 识别哪个等级折扣过狠。
- [ ] `quota_profit < 0` → 标亏损告警；成本缺失（`cost_missing`）→ 利润虚高告警。
- [ ] 客户（UserLogView）看不到 `quota_cost`/`quota_profit`/`actual_upstream_model(B)`；仅 admin/root（AdminLogView）可见全链。
- [ ] 聚合查询用 GORM `Select/Group` 三库兼容（不用 MySQL-only 函数）。

### 7. 所触及页面状态（对齐 §H「利润看板」）
逐笔算售价态（乘折扣）· 逐笔算成本态（不乘折扣 ADR-BILL-02）· 逐笔算利润态（售价-成本）· 亏损告警态（profit<0）· 成本缺失利润虚高告警态 · 三金额落 Log 态 · 利润看板按模型聚合态 / 按供应商聚合态 / 按分组聚合态 · 角色视图裁剪态（UserLogView 无成本利润 / AdminLogView 全链 终态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-2038 | BL-6 |
| F-2040 | BL-5 |
| F-2041 | BL-5 |
| F-2042 | BL-2 |
| F-2044 | BL-1 |
| F-2045 | BL-4 |
| F-2046 | BL-3 |
| F-2047 | BL-3 |
| F-2060 | BL-7 |
| F-2061 | BL-7 |
| F-2062 | BL-8 |
| F-2063 | BL-9 |
| F-2064 | BL-9 |

无 `[BLOCKER]`。
