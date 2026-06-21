# 数据对象缺口 — DATA-OBJECT-GAPS

> 配套 `ARCHITECTURE-REVIEW.md` 第 1/7 项。仅记录字段级未冻结/未抽取的实体。核心域实体（User/Token/Channel/Ability/Log/TopUp/Subscription/Task/Redemption）已在 `DATA-MODEL.md §1-9` 做 GORM struct 逐字段抽取，**不在缺口内**。

## 判定基线

`DATA-MODEL.md` 第 1-7 行声明该文件是"S4 PRD 数据对象段唯一权威"，且字段需 `Go字段(json_tag): 类型 [约束]` 形式。凡 PRD §5 引用、但 DATA-MODEL 未给字段级条目的结构，列为缺口。

## 缺口清单（均非阻断，交 PM 在后续阶段补冻结）

| # | 实体/结构 | 引用位置 | 缺口描述 | 严重度 |
|---|---|---|---|---|
| DOG-1 | `Audit` | prd-nfr-rbac X3 §5、X7 §5 | PRD 给出字段语义 `actor_id/action/target/before/after/ts` 与 action 取值（`channel.key.reveal` 等），但 DATA-MODEL 无 §Audit 条目，无类型/约束/索引/表名。审计是高危操作可观测的落点，需冻结为 GORM struct。 | 中 |
| DOG-2 | `VendorMeta` | prd-nfr-rbac X6 §5 | 仅给 `region∈{domestic,overseas}`、`region_visible`，无字段级定义（provider 主键关联、表名、其余字段）。合规分组境内选渠依赖此结构。 | 中 |
| DOG-3 | `PerfMetric` / `QuotaData` | prd-nfr-rbac X1/X4/X5 §5 | 列出大量指标键（`gateway_latency_p99`、`channel_select_p99`、`request_rate`、`cache_hit_ratio` 等）作为"配置项/指标标签"，但 DATA-MODEL 无 §PerfMetric/§QuotaData 字段抽取。X4 来源行自述 `controller/perf_metrics.go`，应可从 repo 抽取冻结。 | 低（NFR 目标态）|
| DOG-4 | `Team`（团队/工作区）| prd-nfr-rbac X7 §5 | `team_id` 外键 + `team_role∈{owner,member}`，明确标注"阶段二预留"。当前阶段不阻断，但若进入阶段二需补 DATA-MODEL。 | 低（阶段二）|
| DOG-5 | `trace_id` / OTel span | prd-nfr-rbac X4 §5 | trace_id 为"新增能力"，未落任何字段载体（是否写 Log.RequestId/独立表）。Log 已有 `RequestId varchar(64)` / `UpstreamRequestId varchar(128)`（DATA-MODEL §5），需明确 trace_id 是否复用 RequestId。 | 低 |
| DOG-6 | `ChannelAffinityRule` / `ChannelAffinitySetting` | prd-channel CH-4 §5 | 给出 `ModelRegex/PathRegex/KeySources/SkipRetryOnFailure/TTLSeconds`、`Enabled/SwitchOnSuccess/MaxEntries/DefaultTTLSeconds`，但 DATA-MODEL 无对应条目（PRD 标注为"亲和规则/缓存运行态"，非持久化 GORM）。若为内存配置可豁免，但需 PM 确认其持久化形态。 | 低（运行态）|
| DOG-7 | `RelayInfo.TieredBillingSnapshot` / `FinalPreConsumedQuota` | prd-billing BL-2 §5、BL-5 §5 | 计费上下文运行态（非 DB 持久化），DATA-MODEL 未列。属请求级内存结构，工程上可接受不冻结为表，但 `TieredBillingSnapshot` 的 `BillingMode/GroupRatio/QuotaPerUnit/EstimatedTier` 字段类型未在任何契约文件定义。 | 低（运行态）|
| DOG-8 | `SubscriptionPreConsumeRecord` | prd-billing BL-3 §5、prd-asynctask AT-4 §5 | 订阅维度退款核心载体（consumed↔refunded），DATA-MODEL §8 仅列 Plan/Order/UserSubscription 三表，**无 SubscriptionPreConsumeRecord 字段级条目**。退款幂等依赖其状态字段，建议补冻结。 | 中 |

## 对核心域 PASS 的影响说明

DOG-1~DOG-8 均**不涉及** User/Token/Channel/Log/TopUp/UserSubscription/Task/Redemption 这八个核心资金/路由/鉴权实体——它们在 DATA-MODEL 已字段级冻结。故 `ARCHITECTURE-REVIEW.md` 第 1 项判 PASS 与本文件不矛盾：本文件未声称任何核心 schema 未冻结。需补的是横切/NFR/订阅预扣记录结构。
