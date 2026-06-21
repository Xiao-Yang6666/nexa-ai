# FL-billing — 计费与钱包（D8）流程图

> 分片：计费与额度 / 钱包（F-2038~F-2048、F-2042 预扣、F-2046/F-2047 订阅）。
> 角色：登录用户（充值/兑换/订阅）/ 系统（预扣·结算·退款）/ 管理员（倍率·兑换码·订阅作废）/ 访客（价格页）。
> 跨切面契约见 `../OVERALL-FLOW.md §3`：C1 会话鉴权、C5 限流、C7 支付合规闸门（payment_compliance）。涉及金额流转的节点不重画 C1/C7，仅标注「过C7」。
> 后端：`controller/topup.go`、`controller/redemption.go`、`model/subscription.go`、`service/pre_consume_quota.go`、`service/quota.go`、`service/tiered_settle.go`。关键常量：`QuotaPerUnit`、`TrustQuota`、`FinalPreConsumedQuota`。

---

## 场景 BL-1 · 在线充值入账（支付下单→回调→幂等入账）（F-2044）

> 业务规则：用户发起充值先过 C7 支付合规闸门，创建本地 TopUp 单（pending）后跳支付渠道；**额度入账只发生在支付回调验签通过后**，回调以订单号做幂等键，重复回调（订单已 paid）直接返回成功不重复加额度；验签失败丢弃。这是「先下单、回调才入账」的两段式，本图突出回调侧的幂等守卫。

```mermaid
flowchart TD
  pay_open([用户填充值金额 · 过C7]) --> pay_chk{合规已确认?}
  pay_chk -->|未确认| pay_block[拒绝下单 → 引导确认合规声明]:::err
  pay_chk -->|已确认| pay_order[创建 TopUp 单 status=pending 写订单号]
  pay_order --> pay_jump[跳转支付渠道收银台]
  pay_jump --> pay_wait{用户是否完成支付?}
  pay_wait -->|放弃/超时| pay_expire[订单保持 pending → 可重新发起]:::err
  pay_wait -->|完成| pay_cb[支付渠道异步回调网关]
  pay_cb --> pay_sign{回调验签通过?}
  pay_sign -->|否| pay_drop[丢弃伪造回调 不入账]:::err
  pay_sign -->|是| pay_idem{订单已是 paid?}
  pay_idem -->|是 重复回调| pay_ok2([幂等返回成功 不重复加额度]):::term
  pay_idem -->|否 首次| pay_credit[user.Quota += 充值额度 · 标 paid]
  pay_credit --> pay_done([充值入账成功态 · 余额刷新]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（BL-1 充值入账）：
- 充值金额填写态（过 C7 合规闸门）
- 合规未确认拒绝态（引导确认声明） ← 异常
- 待支付态（订单 pending，跳收银台）
- 放弃/超时态（订单保持 pending，可重发） ← 异常
- 回调验签失败丢弃态（伪造回调） ← 异常
- 幂等成功态（重复回调不重复加额度） ← 终态
- 充值入账成功态（Quota 增加、余额刷新） ← 终态

---

## 场景 BL-2 · 按量请求的预扣→调用→结算/失败返还（多退少不补）（F-2042）

> 业务规则：转发前 `PreConsumeQuota` 估算预扣——`userQuota<=0` 直接 403「额度不足」且 SkipRetry；`userQuota-preConsumed<0` 也 403；`userQuota>TrustQuota 且令牌额度>TrustQuota` 的信任令牌 `preConsumedQuota=0` 跳过预扣。预扣成功记 `FinalPreConsumedQuota` 并扣减。上游返回后按真实 token 结算：差额「多退少不补」；请求失败异步 `ReturnPreConsumedQuota` 全额返还。本图为「冻结额度→真实消费→差额回补」的资金生命周期，含信任令牌旁路。

```mermaid
flowchart TD
  pc_in([relay 转发前预扣]) --> pc_q0{userQuota <= 0?}
  pc_q0 -->|是| pc_403a[403 额度不足 · SkipRetry+NoRecordErrorLog]:::err
  pc_q0 -->|否| pc_trust{信任令牌? userQuota>TrustQuota 且 令牌额度>TrustQuota}
  pc_trust -->|是 旁路| pc_skip[preConsumedQuota=0 不预扣]
  pc_trust -->|否| pc_est[估算 preConsumed = 估算token × 倍率]
  pc_est --> pc_q1{userQuota - preConsumed < 0?}
  pc_q1 -->|是| pc_403b[403 预扣费额度失败 · 跳过重试]:::err
  pc_q1 -->|否| pc_freeze[扣减用户额度 记 FinalPreConsumedQuota]
  pc_freeze --> pc_call
  pc_skip --> pc_call[转发上游调用]
  pc_call --> pc_res{上游调用结果?}
  pc_res -->|失败| pc_refund[ReturnPreConsumedQuota 全额返还 FinalPreConsumedQuota]
  pc_refund --> pc_failend([失败已返还态 · 余额复原]):::term
  pc_res -->|成功| pc_real[按真实 token 算实际消费]
  pc_real --> pc_diff{实际 vs 预扣?}
  pc_diff -->|实际 < 预扣| pc_back[多退 差额回补用户额度]
  pc_diff -->|实际 >= 预扣| pc_nofix[少不补 仅补记不再追扣]
  pc_back --> pc_settle[PostConsumeQuota 落 Log + UseData]
  pc_nofix --> pc_settle
  pc_settle --> pc_okend([结算完成态 · 用量入账]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（BL-2 预扣结算，系统/用户感知）：
- 余额不足 403 态（userQuota<=0，SkipRetry） ← 异常
- 信任令牌旁路态（跳过预扣，preConsumedQuota=0）
- 预扣失败 403 态（扣后为负，跳过重试） ← 异常
- 额度已冻结态（记 FinalPreConsumedQuota）
- 调用失败全额返还态（余额复原） ← 终态
- 多退差额回补态（实际<预扣）
- 少不补态（实际>=预扣，仅补记）
- 结算完成态（Log+UseData 入账） ← 终态

---

## 场景 BL-3 · 订阅生命周期（开通→活跃判定→到期/作废）（F-2046/F-2047）

> 业务规则：用户下订阅订单生成 `UserSubscription`，状态在 `active/expired/cancelled` 间流转；**活跃判定 = status=active AND end_time>now**；到期（end_time<=now）自动转 expired；管理员 `AdminInvalidateUserSubscription` 置 cancelled 且 end_time=now 立即结束。订阅维度请求按 `BillingSource=subscription` 从订阅项预扣（`SubscriptionId>0`），失败将预扣记录从 consumed 转 refunded。本图为订阅实体状态机 + 结算分支，刻意用状态图表达多态迁移。

```mermaid
stateDiagram-v2
  [*] --> Pending : 创建订阅订单
  Pending --> Active : 支付成功生成 UserSubscription
  Pending --> OrderFail : 支付失败/取消
  OrderFail --> [*]
  Active --> Consuming : 订阅维度请求 BillingSource=subscription
  Consuming --> Active : 结算成功记 consumed
  Consuming --> Refunded : 请求失败 consumed→refunded 退回订阅额度
  Refunded --> Active : 额度复原继续可用
  Active --> Expired : end_time<=now 自动到期
  Active --> Cancelled : 管理员作废 置 end_time=now 立即结束
  Active --> WalletGuard : 订阅额度耗尽
  WalletGuard --> Active : allow_wallet_overflow=true 钱包兜底
  WalletGuard --> OverflowBlock : allow_wallet_overflow=false 阻止兜底
  OverflowBlock --> Active : 返回订阅额度不足
  Expired --> [*]
  Cancelled --> [*]
  note right of WalletGuard
    存在不允许溢出的活跃订阅时
    阻止用钱包兜底 (subscription.go:844)
  end note
```

屏幕状态清单（BL-3 订阅生命周期）：
- 订阅订单待支付态（Pending）
- 下单失败态（支付失败/取消） ← 异常终态
- 活跃态（status=active AND end_time>now）
- 订阅消费中态（BillingSource=subscription 预扣）
- 订阅失败退回态（consumed→refunded） ← 异常
- 钱包兜底态（allow_wallet_overflow=true）
- 兜底被阻态（overflow=false，订阅额度不足） ← 异常
- 自动到期态（end_time<=now，expired） ← 终态
- 管理员作废态（cancelled，end_time=now 立即结束） ← 终态

---

## 场景 BL-4 · 兑换码兑换（一次性、过期、已用守卫）（F-2045）

> 业务规则：管理员按 Quota 生成单个或批量（Count）兑换码；用户兑换时校验——码不存在/格式错 → 拒绝；已使用 → 不可重复兑换；已过期 → 拒绝；有效则额度入账并把码置为已用（一次性）。本图为短链线性校验串，与 BL-1 充值的两段式刻意不同：无外部回调，校验全在本地事务内完成。

```mermaid
flowchart LR
  rd_in([用户提交兑换码]) --> rd_find{码存在且格式合法?}
  rd_find -->|否| rd_bad[无效兑换码 → 拒绝]:::err
  rd_find -->|是| rd_used{码状态 = 已使用?}
  rd_used -->|是| rd_dup[已被兑换 不可重复]:::err
  rd_used -->|否| rd_exp{已过期?}
  rd_exp -->|是| rd_expire[过期码拒绝兑换]:::err
  rd_exp -->|否| rd_credit[额度入账 user.Quota += code.Quota]
  rd_credit --> rd_mark[事务内置码为已用 一次性]
  rd_mark --> rd_ok([兑换成功态 · 余额增加]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（BL-4 兑换码）：
- 兑换码输入态
- 无效码态（不存在/格式错） ← 异常
- 重复兑换态（已使用） ← 异常
- 过期码态（拒绝） ← 异常
- 兑换成功态（额度入账、码置已用） ← 终态

---

## 场景 BL-5 · 阶梯/表达式计费配置与冻结快照结算（F-2040/F-2041）

> 业务规则：管理员为模型配 `billingexpr` 表达式（变量 p/c/cr/cc/img/ai/ao/len，函数 tier/param/header/has/hour），`tier` 按 `len` 分档切单价；`p/c` 在引用子类变量时**自动排除该子类**避免重复计价；表达式版本化 `v1:`、负值归零。请求前冻结 `TieredBillingSnapshot`（含 BillingMode/GroupRatio/QuotaPerUnit/EstimatedTier），结算时 `TryTieredSettle` 仅当 `snap.BillingMode==tiered_expr` 用真实 token 重算，否则返回 false 走旧倍率逻辑。本图为「配置→冻结→重算」的双轨判定，配置侧与结算侧并存。

```mermaid
flowchart TD
  bx_cfg([管理员配置模型表达式]) --> bx_parse{表达式语法/版本 v1: 合法?}
  bx_parse -->|否| bx_perr[表达式校验失败 → 拒绝保存]:::err
  bx_parse -->|是| bx_save[保存 tiered_expr 配置]
  bx_save --> bx_req[请求进入计费]
  bx_req --> bx_snap[冻结 TieredBillingSnapshot · 记 EstimatedTier]
  bx_snap --> bx_call[转发上游获取真实 token]
  bx_call --> bx_try{snap.BillingMode == tiered_expr?}
  bx_try -->|否| bx_legacy[TryTieredSettle 返回false → 走旧倍率结算]
  bx_legacy --> bx_legend([倍率模式结算态]):::term
  bx_try -->|是| bx_tier{真实 len 落在哪一档?}
  bx_tier -->|低档| bx_t1[按低档单价计]
  bx_tier -->|跨阈值高档| bx_t2[按高档单价计 切档]
  bx_t1 --> bx_excl[p/c 自动扣除已单列子类 cr/cc/img]
  bx_t2 --> bx_excl
  bx_excl --> bx_neg{中间结果 < 0?}
  bx_neg -->|是| bx_zero[负值归零]
  bx_neg -->|否| bx_keep[保留计算值]
  bx_zero --> bx_final[用快照倍率重算实际额度]
  bx_keep --> bx_final
  bx_final --> bx_ok([表达式结算完成态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（BL-5 表达式/阶梯计费）：
- 表达式配置编辑态
- 表达式校验失败态（语法/版本非法） ← 异常
- 快照冻结态（记 EstimatedTier）
- 旧倍率结算态（非 tiered_expr，TryTieredSettle=false） ← 终态
- 低档计价态 / 高档切档态（按真实 len 分档）
- 子类排除态（p/c 扣除 cr/cc/img）
- 负值归零态
- 表达式结算完成态（快照重算实际额度） ← 终态

---

## 场景 BL-6 · 成本/售价分离计费（售价挂对外模型恒定、成本随实际供应商、利润逐笔可算）（兼容层 / 经营计费）

> 业务规则（唯一权威 = `../COMPAT-BILLING-DECISIONS.md §4` 成本/售价分离 + §6 分组纯折扣，对齐 prd-billing / prd-relay RL-6 ⑥⑦）：一笔请求结算时**售价与成本两路独立**——**售价** `quota_sell` = 对外模型 A 的基准售价倍率（`PublicModel.BasePriceRatio`/`GetModelRatio(A)`）× 分组折扣系数（`GetGroupRatio(UsingGroup)`，free=1.0/vip=0.85/svip=0.7），**对客户恒定**，不管内部走了哪个供应商、兜底切供应商也不波动；**成本** `cost` = 实际选中渠道 × 真实模型 B 的成本倍率（`ChannelModelCost.CostRatio`，超管手填，挂在「供应商渠道×B」上），按真实 token 用量计；**利润** `profit = quota_sell − cost`，逐笔可算、可按 模型/供应商/分组 聚合（利润看板）；**成本缺失**（该渠道×B 未配倍率）则 `cost=0` + 告警（不阻断结算，利润虚高需运营补录）；`profit<0` 亏损也记账并告警（识别该供应商在亏）。本图为「售价路（恒定）⊕ 成本路（随供应商）」双轨并行汇聚到利润落账，含成本缺失旁路。

```mermaid
flowchart TD
  bs_in([请求结算 · 已知 A/B/实际渠道Y/分组/真实token]) --> bs_split[双价分离结算]
  bs_split --> bs_sell[售价路 取对外模型 A 基准售价倍率]
  bs_split --> bs_cost[成本路 定位 实际渠道Y × 真实模型 B]
  bs_sell --> bs_ratio[× 分组折扣系数 GetGroupRatio · free1.0/vip0.85/svip0.7]
  bs_ratio --> bs_sellv[quota_sell = A基准价 × 折扣 × 真实token · 对客户恒定]
  bs_cost --> bs_has{ChannelModelCost 渠道Y×B 成本倍率存在?}
  bs_has -->|缺失| bs_cost0[cost = 0 + 告警 · 利润虚高待补录]:::err
  bs_has -->|命中| bs_costv[cost = CostRatio × 真实token · 随实际供应商]
  bs_sellv --> bs_profit
  bs_cost0 --> bs_profit
  bs_costv --> bs_profit[profit = quota_sell − cost]
  bs_profit --> bs_neg{profit < 0 亏损?}
  bs_neg -->|是| bs_loss[记账 + 亏损告警 · 识别该供应商在亏]:::err
  bs_neg -->|否| bs_pos[正常利润]
  bs_loss --> bs_log
  bs_pos --> bs_log[落 Log · quota_sell/cost/profit 三金额 + C/A/B + 供应商Y]
  bs_log --> bs_ok([双价记账完成态 · 可按 模型/供应商/分组 聚合利润]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（BL-6 成本/售价分离计费，系统/经营态）：
- 双价分离结算态（售价路 ⊕ 成本路并行）
- 售价计算态（A 基准价 × 分组折扣，对客户恒定、兜底不波动）
- 成本定位态（实际渠道Y × 真实模型 B）
- 成本缺失告警态（cost=0 + 告警，利润虚高待补录） ← 异常
- 成本计算态（CostRatio × 真实 token，随实际供应商）
- 利润计算态（profit = quota_sell − cost）
- 亏损告警态（profit<0，识别亏损供应商） ← 异常
- 双价记账落账态（quota_sell/cost/profit 三金额 + C/A/B + 供应商，可聚合利润看板） ← 终态
