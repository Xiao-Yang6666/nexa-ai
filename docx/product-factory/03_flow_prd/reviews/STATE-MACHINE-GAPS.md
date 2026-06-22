# 状态机缺口 — STATE-MACHINE-GAPS

> 配套 `ARCHITECTURE-REVIEW.md` 第 2/5 项。被点名的六类生命周期（Token/Channel/Order/Subscription/Task/支付）状态枚举与迁移守卫均已显式（见评审第 2 项表），本文件记录残余的终态处置/未入机迁移缺口。

## 已显式（不在缺口内，列此以正名）

| 状态机 | 枚举来源 | 守卫举证 |
|---|---|---|
| Token | DATA-MODEL §2 Token Status 枚举 + prd-token TK-2 §3/§4 | 启用过期/耗尽拒绝守卫 + 软删 DeletedAt |
| Channel | prd-channel CH-3 §3 步骤 1-7 | `AutomaticDisableChannelEnabled ∧ AutoBan==1` 守卫；仅 AutoDisabled 才自愈 |
| Order(充值) | prd-billing BL-1 §3 | pending→success，验签+幂等守卫 |
| Subscription | DATA-MODEL §8.3 + prd-billing BL-3 §3 | active 判定 = `status=active AND end_time>now` |
| Task | DATA-MODEL §9 TaskStatus + prd-asynctask AT-1 §3 | 合法顺序 + CAS `UpdateWithStatus` |

## 缺口清单（均非阻断）

| # | 状态机 | 缺口描述 | 引用位置 | 严重度 |
|---|---|---|---|---|
| SMG-1 | Task `UNKNOWN` 终态 | AT-1 §3 步骤 5 定义"无法识别→`Status=UNKNOWN`"，但 **AT-4 退款分流树只分 `SUCCESS` 与 `FAILURE/超时`，`UNKNOWN` 不在分流中**。UNKNOWN 任务的预扣额度是否退款、是否重扫、是否人工介入未规约。资金可能悬挂。 | prd-asynctask AT-1 §3、AT-4 §3 步骤 2-3 | 中 |
| SMG-2 | Subscription 配额重置态 | DATA-MODEL §8.1 `QuotaResetPeriod∈{never/daily/weekly/monthly/custom}` + §8.3 `LastResetTime/NextResetTime`，但 **BL-3 状态机无"配额重置"迁移**（active 态下周期性重置 AmountUsed 的迁移未画）。 | prd-billing BL-3 §3 | 中 |
| SMG-3 | Token Expired/Exhausted 的恢复 | TK-2 §3 定义 Enabled→Expired/Exhausted，但**未定义 Expired 态续期后能否回 Enabled**（仅定义启用已过期令牌被拒）。续期（改 ExpiredTime）是否触发态迁移未显式。 | prd-token TK-2 §3 | 低 |
| SMG-4 | Channel ManualDisabled 与 AutoDisabled 的状态区分落库 | CH-3 §5 提到 `ChannelStatusAutoDisabled` 区别于 `ChannelStatusManuallyDisabled`，但 DATA-MODEL §3 `Status int default:1`（"1=启用/其他=禁用"）**未列出这两个具体禁用值的数值**。自愈守卫"仅 AutoDisabled 才恢复"依赖该数值区分，需明确常量值。 | prd-channel CH-3 §5、DATA-MODEL §3 | 中 |
| SMG-5 | 充值订单 pending 的超时清理 | BL-1 §4「用户放弃/超时未支付→订单保持 pending 可重发」，但 **pending 订单是否有 TTL/清理任务未规约**，长期堆积无回收策略。 | prd-billing BL-1 §4 | 低 |
| SMG-6 | 多 Key 单 Key 状态机 | prd-channel CH-1 §5 ChannelInfo `MultiKeyStatusList`（key index→status）+ prd-nfr-rbac X2「单 Key 失效仅禁该 Key」，但**单个 Key 的禁用/恢复状态机（key 级 AutoBan）未单独成块**，仅在渠道级 CH-3 状态机中隐含。key 级状态枚举与迁移条件未显式。 | prd-channel CH-1/CH-3、prd-nfr-rbac X2 §4 | 低 |

## 对评审第 2 项 PASS 的影响

SMG-1~SMG-6 不推翻第 2 项 PASS：被点名的六类状态机本体（枚举+主迁移+守卫）已规约到可实现级别。缺口是**终态边缘处置（UNKNOWN）、周期性迁移（配额重置）、禁用值常量数值（Auto vs Manual）**等补强项，建议 PM 在进入开发前补：
1. SMG-1（UNKNOWN 资金处置）与 SMG-4（禁用值数值）优先级最高——前者关资金安全，后者关自愈正确性。
2. 其余为健壮性补强，不阻断 UI/原型。
