# PRD — 横切非功能需求 + 数据合规 + RBAC（FL-nfr-rbac）

> 分片：S4 最后一片，覆盖 `NFR-REQUIREMENTS.md`（NFR-P/A/S/O/E + DC）与 `ROLE-PERMISSION-MATRIX.md`（7 产品角色 × 12 操作域）派生的横切功能项。
> 数据对象字段复用 `DATA-MODEL.md`：`§1 User / §4 Channel / §7 Token / §13 Log / §14 PerfMetric/QuotaData / §15 Option`；`Audit/VendorMeta/UserOAuthBinding/Team` 为本片新增或运行期结构，字段以 controller 返回或下文配置项为准。
> 权威边界：repo new-api 提供一手能力底座（AutoBan/StatusCodeMapping/跨组重试/SecureVerification/CriticalRateLimit/perf_metrics/Redis 双缓存/三级角色）；本片的 SLA 百分位、保留期、脱敏规则、数据出境策略为 S2 引入的「目标态/SLO」，验收时以**可测二元判定**落地，不回填为 repo 既有事实。
> 本片覆盖功能 ID：**F-5001 ~ F-5021、F-5030 ~ F-5035**，按七大类聚为七个 PRD 块。
> 横切契约：性能埋点（X1）是可用性降级判定（X2）与伸缩扩容触发（X5）的数据源；安全脱敏（X3）是数据合规留存（X6）的实现手段；RBAC（X7）的二次验证闸门接住安全块的取明文受控路径。

---

## X1 性能 SLO 埋点与压测门禁（网关附加延迟 / 稳态吞吐 / 选渠与计费分项开销）

- **功能 ID / 优先级**：F-5001、F-5002 / P0（F-5002 P1）
- **来源**：NFR-P01~P08；`service/channel_select.go`（选渠路径）、`service/quota.go`/`tiered_settle.go`（预扣+结算）、`pkg/billingexpr`（DSL 求值）、`controller/perf_metrics.go`（PerfMetric 暴露）
- **角色 / Owner**：运维 SRE / Root 可见全量指标（self-scope 用户不可见跨渠道分项）；Owner 模块 = NFR 性能
- **触发**：每次 relay 转发请求实时打点；发布前 / 定时压测任务运行

### 1. 场景
网关型产品的延迟本质是「自身转发开销 + 路由/计费/限流判定开销」，与上游推理耗时（不可控）必须切分计量。每次 relay 请求在「入站收齐请求体 → 选渠完成 → 发出上游请求」三个时间戳间打点，得出网关附加延迟；选渠函数（GetRandomSatisfiedChannel + 亲和缓存命中）、预扣/结算、billingexpr 求值各自内部计时，分项暴露到 PerfMetric。发布门禁用 k6/wrk 对 chat 转发施加恒定负载 10 分钟，校验稳态吞吐与并发长连接数，错误率超线即阻断发布。

### 2. 前置条件
- relay 链路已注入三段时间戳埋点；PerfMetric 已 `InitPerfMetric()`，APM/Prometheus 抓取通道就绪。
- 压测在与生产同规格的单实例上跑，关闭上游真实计费（用 mock 上游或回放）以隔离网关自身开销。
- 选渠走缓存命中路径（渠道/令牌/Ability 缓存已预热），避免冷启动污染分项基线。

### 3. 适用范围
所有经 `controller/relay.go` 的 chat 转发请求（含流式 SSE）均纳入网关附加延迟埋点；选渠分项覆盖普通分组与 auto 跨组场景；计费分项覆盖预扣（pre-consume）、阶梯结算（tiered_settle）、表达式求值（billingexpr）三段。流式首字延迟（TTFB）按「首个 chunk 时间戳 − 客户端请求时间戳，扣除上游 TTFB」单独统计。realtime WS 长连接计入并发连接数指标但不计入附加延迟分项。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| 单段时间戳缺失（埋点异常） | 该请求不计入分项，打 `perf_drop` 计数 | 分项采样率 < 100% 触发埋点告警 |
| 压测稳态吞吐 < 1500 req/s | 门禁判定 FAIL，阻断发布流水线 | 发布被拒并附吞吐曲线 |
| 压测错误率 ≥ 0.1% | 门禁判定 FAIL | 发布被拒并附错误分布 |
| p99 附加延迟 > 60ms 持续 5min | 触发 NFR-O03 延迟告警（接 X4） | 告警送达运维渠道 |

### 5. 配置项（PerfMetric / 压测门禁）
- **PerfMetric**（`§14 PerfMetric`）：`gateway_latency_p50/p99`（ms）、`channel_select_p99`、`quota_preconsume_p99`、`settle_p99`、`billingexpr_p99`、`ttfb_p95`、`concurrent_conn`、`throughput_rps`，均带 `channel_id/model` 维度标签。
- **门禁配置**（`§15 Option` 派生）：`perf_gate.min_rps=1500`、`perf_gate.max_error_rate=0.001`、`perf_gate.min_concurrent=5000`、`perf_gate.load_duration_sec=600`、`perf_gate.block_on_fail=true`。

### 6. 验收标准
- [ ] 单实例 chat 转发恒定负载 10min：稳态吞吐 ≥ 1500 req/s 且错误率 < 0.1%，否则门禁阻断发布。
- [ ] 网关附加延迟 p50 ≤ 15ms 且 p99 ≤ 60ms，可在指标系统按 `channel_id/model` 维度查询。
- [ ] 维持 ≥ 5000 路 SSE/WS 并发长连接时丢连率为 0 且内存不溢出。
- [ ] 选渠决策 p99 ≤ 5ms、预扣+结算 p99 ≤ 8ms（缓存命中）、billingexpr 单次求值 p99 ≤ 2ms，三项分项独立可查。
- [ ] 流式 TTFB p95 ≤（上游 TTFB + 80ms），扣除上游分量后成立。
- [ ] 全量性能分项仅运维/Root 可见，普通用户调用指标接口返回 403。

### 7. 触及范围
relay 转发链路（埋点）· PerfMetric 指标暴露 · 选渠/计费/结算内部计时 · 发布流水线压测门禁 · APM/Prometheus 抓取（接 X4 可观测）· NFR-P01/P02/P04/P05/P06/P07/P08。

---

## X2 可用性 SLA 与降级编排（渠道降级 / 跨组重试 / AutoBan 自愈 / 容灾 RTO·RPO）

- **功能 ID / 优先级**：F-5003、F-5004、F-5005 / P0（F-5004/F-5005 P1）
- **来源**：NFR-A01~A08；`StatusCodeMapping`/`AutoBan`/`GetRandomSatisfiedChannel`（降级与切渠）、`token.CrossGroupRetry`/`common.RetryTimes`（跨组）、`controller/channel-test.go`（自愈）、`controller/uptime_kuma.go`（探针）、DB 三选一主从（容灾）
- **角色 / Owner**：渠道健康看板 = 运营/运维/管理员/Root（需 admin+ 权限）；SLA 月报只读；容灾演练 Root 审批；Owner 模块 = NFR 可用性
- **触发**：渠道命中故障即触发降级链；分钟级探针持续采样；月度聚合出报；容灾演练或主库故障

### 1. 场景
可用性核心机制 repo 已有一手实现，本块在其上定**目标值与编排策略**并落看板。单上游 provider 故障时，StatusCodeMapping 规整错误码触发重试，AutoBan 自动禁用坏渠道，GetRandomSatisfiedChannel 在 ≤1 次重试内切到健康渠道，用户侧成功率不掉档。首选分组优先级耗尽则按 CrossGroupRetry 切下一分组，总重试不超过 RetryTimes 上限。渠道恢复后经 channel-test 全量测试重新启用，无需重启。健康看板实时呈现每渠道错误率/延迟/AutoBan 状态供降级判断；分钟级探针聚合出数据面/控制面月度可用性报表。

### 2. 前置条件
- AutoBan、StatusCodeMapping、MultiKeyMode 已在渠道配置中启用；RetryTimes 与跨组重试开关已配。
- Uptime-Kuma / liveness/readiness 探针就绪，按分钟采样数据面与控制面健康。
- 容灾前提：DB 主从已配置（MySQL/PostgreSQL），定期备份且备份静态加密（接 X6 DC-012）。

### 3. 适用范围
降级编排覆盖所有上游 provider 故障（5xx/超时/限速）触发的切渠与跨组场景；AutoBan 自愈覆盖被禁渠道的周期探测与手动 channel-test 重启用；多 Key 隔离覆盖单渠道下 MultiKeyMode 轮询的单 Key 失效场景。SLA 月报覆盖数据面（转发）与控制面（管理 API）两个独立可用性目标。容灾覆盖主库故障 → 从库接管的演练，含恢复后的合规校验。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| 单 provider 5xx | StatusCodeMapping 触发重试 + AutoBan 禁渠 + 切健康渠道 | 用户侧成功率不掉档 |
| 首选分组全失败 | CrossGroupRetry 切下一分组，受 RetryTimes 限 | 切组事件记日志 |
| 重试次数耗尽 | 返回最终错误（非 5xx 雪崩） | 错误归因可在看板查 |
| 单 Key 失效 | 仅禁该 Key，渠道按 MultiKeyMode 续轮询其余 Key | 渠道仍可用 |
| Redis 失联 | 回落内存缓存，只读选渠路径不中断 | 转发仍可服务 |
| 主库故障 | 从库接管，演练计时 | RTO/RPO 达标判定 |

### 5. 配置项（看板 / SLA / 容灾）
- **健康看板**（`§4 Channel` + 运行期）：每渠道 `error_rate`、`latency_p99`、`autoban_status`、`multikey_status_list`，刷新周期 ≤ 60s。
- **SLA 月报**（`§14 PerfMetric` 聚合）：`data_plane_availability`、`control_plane_availability`，按分钟探针月度聚合。
- **容灾配置**：`disaster.rto_target_min=30`、`disaster.rpo_target_min=5`、`disaster.backup_interval`、`disaster.replica_count`。

### 6. 验收标准
- [ ] 注入单 provider 5xx：故障渠道命中后 ≤ 1 次重试内切到健康渠道，端到端成功率不掉档。
- [ ] 构造首选分组全失败：自动切下一分组，总重试次数 ≤ RetryTimes 配置上限。
- [ ] 被 AutoBan 渠道恢复后，经 channel-test 全量测试可重新启用，全程无需重启进程。
- [ ] 失效单渠道下一个 Key：仅该 Key 禁用，渠道仍按 MultiKeyMode 轮询其余 Key 可用。
- [ ] 切断 Redis：选渠回落内存缓存，只读转发路径不中断（不返回 5xx）。
- [ ] 月报可出：数据面可用性 ≥ 99.9% 且控制面 ≥ 99.5%。
- [ ] 容灾演练：主库故障 → 从库接管，RTO ≤ 30min 且 RPO ≤ 5min。

### 7. 触及范围
StatusCodeMapping/AutoBan 降级链 · GetRandomSatisfiedChannel 切渠 · CrossGroupRetry 跨组 · MultiKey 隔离 · Redis/内存双缓存降级 · channel-test 自愈 · Uptime-Kuma 探针 · DB 主从容灾 · NFR-A01~A08 · 数据源接 X1 性能埋点、告警接 X4。

---

## X3 安全红线（渠道 Key 加密静态存储 / 令牌 Key 掩码受控取明文 / prompt 脱敏 / 高危审计）

- **功能 ID / 优先级**：F-5006、F-5007、F-5008、F-5009 / P0
- **来源**：NFR-S01~S10；`model/channel.go Key`/`setting/payment_*.go WebhookSecret`（加密存储）、`controller/token.go`（CriticalRateLimit+DisableCache）+`secure_verification.go`（取明文受控）、`model/log.go`（prompt 脱敏，接 DC-005）、`controller/audit.go`（高危留痕）
- **角色 / Owner**：加密存储/脱敏管线 = 运维/Root 配置；令牌 Key 取明文 = 用户/开发者及以上 self-scope；审计查询 = admin+；Owner 模块 = NFR 安全
- **触发**：渠道 Key 写入/读取；令牌列表展示与取明文请求；记录日志时；任何高危操作发生时

### 1. 场景
本块固化企业级 SaaS 的三条安全硬要求 + 审计。上游渠道 Key、支付 WebhookSecret 等凭证字段以 AES-256-GCM（或 KMS 信封加密）密文落库，明文不入库、不出现在 DB 导出中，且支持密钥轮换。令牌列表/详情接口默认仅展示掩码 `sk-***xxxx`，取完整明文走独立受保护接口，强制经 SecureVerification 二次验证 + CriticalRateLimit + DisableCache（**接住 F-5006** 的受控取明文契约）。日志默认仅记元数据不留正文；若开启正文留存须先按 PII 规则脱敏（邮箱/手机/身份证/卡号/API Key 形态串）。取 Key、改计费倍率、禁用渠道、改全站 Option 等高危操作写审计日志，含操作人/时间/对象/前后值。

### 2. 前置条件
- 加密主密钥（或 KMS 句柄）已注入运行环境，与 DB 凭证分离存放；轮换流程已定义。
- 令牌取明文接口已挂 CriticalRateLimit + DisableCache，且前置 SecureVerificationRequired 中间件。
- prompt 脱敏规则集已配置（正则模式 + 替换策略），日志正文留存开关默认关闭。
- 审计写入通道（`controller/audit.go`）就绪，捕获前后值快照。

### 3. 适用范围
加密存储覆盖 `Channel.Key`、支付 `WebhookSecret`（Stripe/Creem）等全部凭证类字段；掩码覆盖令牌列表/详情所有展示路径；脱敏覆盖所有开启正文留存的调用日志（默认不留正文则无需脱敏但仍记元数据）；高危审计覆盖 §4 高危清单全部操作（取 Key/改倍率/封禁用户/改 Option/改限流/配 OAuth/强制 GC/部署变更）。Webhook 入站验签覆盖所有支付回调。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| DB 导出含明文 Key | 视为 P0 缺陷，加密管线判定 FAIL | 安全门禁阻断 |
| 列表接口回显完整明文 sk-key | 视为越界，掩码判定 FAIL | 抓包检出明文即失败 |
| 取明文无二次验证 | 返回 403 | 拒绝并记审计 |
| 含 PII 的 prompt 未脱敏入库 | 脱敏管线判定 FAIL | 注入用例检出明文 PII |
| 伪造签名的支付回调 | 拒绝（WebhookSecret 校验失败） | 回调被拒并记日志 |
| 高危操作未写审计 | 视为缺陷 | 审计查询无记录即失败 |

### 5. 配置项（加密 / 掩码 / 脱敏 / 审计）
- **加密**（`§4 Channel`/支付设置）：`Channel.Key`/`WebhookSecret` 存储格式 `enc:aes-256-gcm:<ciphertext>`；`crypto.master_key_ref`（KMS 句柄）、`crypto.rotation_enabled`。
- **掩码**（`§7 Token`）：列表返回 `key_masked=sk-***<last4>`；取明文接口 `/api/token/:id/reveal` 需 `SecureVerification` 通过。
- **脱敏**（`§13 Log` + `§15 Option`）：`log.content_retention=false`（默认）、`log.pii_redact_rules`（邮箱/手机/身份证/卡号/sk- 串）。
- **审计**（`Audit` 新增）：`actor_id/action/target/before/after/ts`，action 取值如 `channel.key.reveal`、`billing.ratio.update`、`channel.disable`、`option.update`。

### 6. 验收标准
- [ ] 直查 DB 与导出文件：`Channel.Key`/`WebhookSecret` 字段均为 AES-256-GCM 密文，无任何明文，且可执行密钥轮换。
- [ ] 令牌列表/详情接口抓包：仅返回 `sk-***xxxx` 掩码，无完整明文。
- [ ] 取令牌明文：必须经 SecureVerification + CriticalRateLimit 受控接口；无二次验证调用返回 403（接住 F-5006）。
- [ ] 注入含邮箱/手机/卡号/sk- 串的 prompt 并开启留存：日志中对应片段已掩码，无原始 PII。
- [ ] 默认配置下调用日志仅含元数据（模型/token 数/耗时/渠道/费用），不含正文。
- [ ] 伪造签名的支付回调被拒绝。
- [ ] 取 Key/改倍率/禁渠道/改 Option 后，审计可按操作人/对象检索到含前后值的记录。

### 7. 触及范围
Channel.Key/WebhookSecret 加密管线 · 令牌掩码与 reveal 受控接口 · SecureVerification+CriticalRateLimit+DisableCache · prompt PII 脱敏 · Audit 高危留痕 · Webhook 验签 · NFR-S01~S10 · 取明文受控接 F-5006、脱敏接 X6 DC-005、二次验证统一接 X7 F-5033。

---

## X4 可观测性（Prometheus RED 指标 / 多渠道告警 / trace_id 链路追踪）

- **功能 ID / 优先级**：F-5010、F-5011、F-5012 / P0（F-5012 P1）
- **来源**：NFR-O01~O07；`controller/perf_metrics.go`/`PerfMetric`（RED 指标）、`NotificationSettings`（email/webhook/bark 告警）、relay 链路（trace_id 为新增能力，OTel 导出）、`controller/log.go`/`usedata.go`（日志分级检索）、`controller/uptime_kuma.go`（探针）
- **角色 / Owner**：指标导出/链路追踪 = 运维/Root；告警编排 = admin+ 配置；Owner 模块 = NFR 可观测
- **触发**：持续暴露 /metrics 供抓取；阈值触发告警；每请求贯穿 trace_id

### 1. 场景
把 repo 已有的 perf-metrics、Uptime-Kuma、Log/UseData/Rankings 上升为「指标-告警-追踪」三件套。`/metrics` 暴露 Prometheus RED 指标：每渠道/每模型的请求率(R)、错误率(E)、延迟分布(D)，外加额度消费速率，均带维度标签。渠道错误率超阈、额度预警、限流触发率高、p99 延迟超 SLO 时，经 Email/Webhook/Bark 多渠道告警送达。每请求贯穿单一 trace_id，从入站经选渠、上游、结算全链路串联，支持 OpenTelemetry 导出，可在 APM 中端到端还原。日志按 system/调用/审计分级，支持按用户/令牌/渠道/模型/时间检索。

### 2. 前置条件
- PerfMetric 已采集 X1 的分项数据（本块的指标数据源依赖 X1 埋点）。
- NotificationSettings 已配置至少一个告警通道（email/webhook/bark）及各项阈值。
- relay 链路已在入站处生成 trace_id 并向下传递；OTel exporter 已配置上报端点。
- 日志已按 system/调用/审计三类型落库，索引字段（user_id/token_id/channel_id/model/ts）就绪。

### 3. 适用范围
RED 指标覆盖所有渠道与模型维度的请求率/错误率/延迟，及额度消费速率与缓存命中率（接 X5）。告警覆盖渠道错误率、额度余额预警、限流触发率、p99 延迟超 SLO 四类触发源。链路追踪覆盖全部 relay 请求（含流式），trace_id 贯穿入站→选渠→上游→结算四段。日志分级检索覆盖 system/调用/审计三类，业务指标覆盖额度余额预警、充值/订阅转化、签到/邀请活跃。健康探针覆盖 DB/Redis/上游组件。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| /metrics 抓取失败 | 监控侧标记 target down | 触发自身可观测告警 |
| 告警通道不可达 | 按通道列表降级到下一通道重试 | 多通道至少一路送达 |
| 渠道错误率超阈 | 触发对应告警 + 联动 X2 看板 | 告警在配置通道送达 |
| p99 延迟超 SLO | 触发延迟告警（数据来自 X1） | 告警附 p99 曲线 |
| trace_id 中断（某段缺失） | 该段标记 broken span | APM 中可见断链点 |
| 探针报组件不健康 | readiness 置 false，摘出负载 | 实例被 LB 摘除 |

### 5. 配置项（指标 / 告警 / 追踪 / 日志）
- **RED 指标**（`§14 PerfMetric`/`§14 QuotaData`）：`request_rate`、`error_rate`、`latency_bucket`（带 `channel_id/model` 标签）、`quota_consume_rate`、`cache_hit_ratio`。
- **告警**（`NotificationSettings`）：`alert.channel_error_threshold`、`alert.quota_warn_threshold`、`alert.ratelimit_trigger_threshold`、`alert.p99_slo_ms=60`、`alert.channels=[email,webhook,bark]`。
- **追踪**（relay 新增）：每请求 `trace_id`，OTel exporter `otel.endpoint`、`otel.sample_rate`。
- **日志**（`§13 Log`）：`log_type∈{system,relay,audit}`，检索维度 `user_id/token_id/channel_id/model/time_range`。

### 6. 验收标准
- [ ] `/metrics` 可被 Prometheus 抓取，且含每渠道/每模型的请求率、错误率、延迟分布及额度消费速率维度标签。
- [ ] 注入渠道故障：对应告警经配置的 Email/Webhook/Bark 通道送达，缺一通道时其余通道仍送达。
- [ ] p99 延迟超 60ms SLO 触发延迟告警（数据取自 X1 埋点）。
- [ ] 单请求在 APM 中可经 trace_id 端到端串联入站→选渠→上游→结算四段。
- [ ] 日志查询接口可按用户/令牌/渠道/模型/时间任意组合过滤命中正确记录。
- [ ] liveness/readiness 探针返回各组件（DB/Redis/上游）健康状态；不健康时实例被摘出。
- [ ] QuotaData 按日聚合延迟 ≤ 5min，排行榜可查且与日志对账一致。

### 7. 触及范围
PerfMetric/QuotaData 指标导出 · NotificationSettings 多渠道告警 · relay trace_id + OTel · 日志分级检索 · liveness/readiness 探针 · NFR-O01~O07 · 数据源接 X1、告警联动 X2 看板、缓存命中接 X5。

---

## X5 伸缩性（无状态横扩与共享缓存 / 缓存命中率监控 / 日志归档分区）

- **功能 ID / 优先级**：F-5013、F-5014、F-5015 / P0（F-5014/F-5015 P1）
- **来源**：NFR-E01~E06；`Redis`+内存双缓存（NFR-E01）、`model/channel_cache.go`/`token_cache.go`/`ability`（命中率，NFR-E02）、GORM v2 读写分离 + log 表分区（NFR-E03，DC-006）
- **角色 / Owner**：运维 SRE / Root；Owner 模块 = NFR 伸缩
- **触发**：扩容/缩容时；缓存命中率持续监控；定时日志归档任务

### 1. 场景
转发实例无本地状态——会话与选渠状态走 Redis 共享缓存，内存缓存仅作只读回落，故可水平加节点近线性提升吞吐。渠道/令牌/Ability 三类缓存的命中率持续监控，目标命中率 ≥ 95% 且 DB 选渠查询占比 < 5%，确保大规模渠道下选渠不退化。日志表按时间分区，超期数据按保留期归档（与 DC-006 对齐），归档不影响在线查询。限流阈值按实例数/租户分级可配，扩容时全局桶自动放大。单部署需支撑 ≥ 1000 渠道、≥ 52 类 provider、≥ 10 万用户、≥ 50 万令牌的列表/计费不退化。

### 2. 前置条件
- Redis 已部署且为转发实例共享；内存缓存仅承载只读回落路径（写穿透到 Redis）。
- channel/token/ability 缓存已预热，缓存命中率指标已接入 X4 的 RED 指标体系。
- log 表已建分区键（按 created_at 时间分区），归档目标存储（冷库/对象存储）就绪。
- 全局限流桶配置支持按实例数动态放大（`GlobalAPIRateLimit` 等）。

### 3. 适用范围
无状态横扩覆盖所有 relay 转发实例（控制面有状态部分不在此列）。缓存命中率监控覆盖渠道缓存、令牌缓存、Ability 缓存三类。日志归档覆盖调用日志元数据（默认保留 180 天）与开启正文留存的内容（独立保留期，接 X6）。限流弹性覆盖全局桶随实例数扩缩。规模目标覆盖渠道数、provider 类数、用户数、令牌数四个维度的列表/计费/分页查询不退化。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| Redis 不可用 | 回落只读内存缓存，写路径降级（接 X2） | 转发不中断、写操作排队/拒绝 |
| 缓存命中率 < 95% | 触发命中率告警（接 X4） | DB 选渠占比上升可见 |
| 扩容后吞吐不线性 | 排查本地状态泄漏点 | 吞吐曲线偏离线性即 FAIL |
| 日志表膨胀超阈 | 触发归档任务，迁超期分区到冷库 | 在线表行数回落 |
| 大数据量分页 p99 超标 | 走读副本 + 分区裁剪优化 | 分页查询 p99 达标判定 |

### 5. 配置项（横扩 / 缓存 / 归档 / 限流弹性）
- **横扩**（运行期）：转发实例 `stateless=true`，状态后端 `cache_backend=redis`，内存缓存 `readonly_fallback=true`。
- **缓存监控**（`§4 ChannelCache`/`§7 TokenCache`/Ability）：`channel_cache_hit_ratio`、`token_cache_hit_ratio`、`ability_cache_hit_ratio`、`db_select_ratio`（目标 < 5%）。
- **归档**（`§13 Log`/`§14 QuotaData`）：`log.metadata_retention_days=180`、`log.partition_by=created_at`、`log.archive_target`、归档与 DC-006 保留期对齐。
- **限流弹性**（`§15 Option`）：`GlobalAPIRateLimit` 按 `instance_count` 自动放大全局桶。

### 6. 验收标准
- [ ] 转发实例 2→4 横扩：吞吐近线性增长（≥ 1.8 倍），无本地状态导致的会话错乱。
- [ ] 渠道/令牌/Ability 缓存命中率 ≥ 95% 且 DB 选渠查询占比 < 5%，可在指标系统查询。
- [ ] 配置 ≥ 1000 渠道、≥ 52 类 provider 下选渠 p99 仍 ≤ 5ms（与 X1 NFR-P06 一致）。
- [ ] log 表超期分区按策略归档到冷库，归档期间在线日志查询不中断。
- [ ] ≥ 10 万用户、≥ 50 万令牌数据量下分页列表查询 p99 达标（走读副本）。
- [ ] 改实例数后全局限流桶随之放大，按新阈值生效。

### 7. 触及范围
Redis+内存双缓存横扩 · channel/token/ability 缓存命中率 · GORM 读写分离 · log 表分区归档 · 全局限流桶弹性 · NFR-E01~E06 · 命中率指标接 X4、Redis 降级接 X2、归档保留期接 X6 DC-006。

---

## X6 数据合规（数据分级 / prompt 留存开关 / 合规分组 / 数据出境告知 / 注销级联 / 同意闸门）

- **功能 ID / 优先级**：F-5016、F-5017、F-5018、F-5019、F-5020、F-5021 / P0（F-5016/F-5018 P1）
- **来源**：DC-001~DC-012；`model/*`（数据分级 DC-001）、`model/log.go`（prompt 留存 DC-005）、`group_ratio`+`VendorMeta`（合规分组 DC-008）、`vendor_meta.go`（驻地标注 DC-009）、`model/user.go`/`UserOAuthBinding`（注销级联 DC-003/DC-011）、`controller/option.go`（同意闸门 DC-010）
- **角色 / Owner**：分级登记/合规分组/留存开关 = 运营/Root 配置；驻地标注 = 公开可见；注销 = 用户 self-scope；同意闸门 = 访客/用户公开闸门；Owner 模块 = 数据与合规
- **触发**：初始化/字段变更（分级）；正文留存配置；合规分组选渠；展示/调用前（出境告知与同意）；账号注销

### 1. 场景
本块落地数据生命周期合规。数据分四级登记：①凭证类（渠道 Key/令牌 Key/支付 Secret）②PII（邮箱/手机/OAuth ID/IP）③内容类（prompt/响应正文）④计量运营类（额度/日志元数据/用量）。prompt 正文留存默认关闭，开启须经 X3 脱敏 + 独立保留期（默认 ≤ 30 天，接住数据合规）且可按用户关闭。合规分组在选渠时仅命中境内数据驻地的渠道。每个 provider 标注境内/境外驻地，价格页与控制台可见（**接住 F-5019** 数据出境告知）。首次调用前若用户未接受含出境与留存条款的协议则拦截（**接住 F-5021** 同意闸门）。账号注销级联删除令牌/OAuth 绑定/PII 并匿名化日志 IP。

### 2. 前置条件
- 全表字段已可标注分级（DC-001 字段清单）；分级元数据由 Root 维护。
- 隐私政策/用户协议内容已在 `controller/option.go` 配置，含数据出境与内容留存条款。
- VendorMeta 已含每 provider 的 `region`（境内/境外）字段；合规分组已在 group_ratio 中定义。
- 注销接口已实现对 User/Token/UserOAuthBinding 的级联与对 Log 中 IP 的匿名化（末段置零）。

### 3. 适用范围
分级登记覆盖 model 全表凭证/PII/内容/计量四级字段。prompt 留存开关覆盖所有调用日志正文（默认仅留元数据）。合规分组覆盖被标记为合规的 user group 的全部选渠请求。出境告知与驻地标注覆盖全部 provider 在价格页与控制台的展示，及调用前对「请求将转发至所选 provider 所在地区」的明示。同意闸门覆盖访客/用户首次调用前的协议接受校验。注销级联覆盖该用户的令牌、OAuth 绑定、PII 字段与历史日志 IP 匿名化。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| 字段未标注分级 | 分级登记判定不完整 | 字段清单缺标即 FAIL |
| 开启正文留存但未脱敏 | 拒绝写入（接 X3 脱敏管线） | 留存被阻断 |
| 合规分组请求命中境外渠道 | 选渠层拦截，仅返回境内渠道 | 境外命中即 FAIL |
| 价格页/控制台缺驻地标注 | 出境告知判定 FAIL | 页面无 region 即失败 |
| 用户未同意协议即调用 | 首次调用前拦截，返回需同意提示 | 调用被拒（接住 F-5021） |
| 注销后 PII 残留 | 注销判定 FAIL | DB 仍有明文 PII 即失败 |

### 5. 配置项（分级 / 留存 / 合规分组 / 驻地 / 注销 / 同意）
- **分级**（`model/*` 元数据）：每字段 `data_class∈{credential,pii,content,metering}`。
- **留存**（`§13 Log`/`§15 Option`）：`log.content_retention=false`（默认）、`log.content_retention_days≤30`、`log.content_retention_user_optout=true`；IP `log.ip_anonymize_after_days=90`（末段置零）。
- **合规分组**（`§4 Channel`/UserGroup/VendorMeta）：`group.compliance_only=true` → 选渠过滤 `VendorMeta.region=domestic`。
- **驻地**（`VendorMeta`）：`region∈{domestic,overseas}`，价格页与控制台 `region_visible=true`。
- **同意闸门**（`controller/option.go`/`§1 User`）：`privacy_policy_version`、`User.consent_version`，二者不匹配则拦截首次调用。
- **注销**（`§1 User`/`§7 Token`/`UserOAuthBinding`/`§13 Log`）：级联删除 + Log IP 匿名化。

### 6. 验收标准
- [ ] model 全表凭证/PII/内容/计量四级字段均有 `data_class` 标注，清单完整可审。
- [ ] 正文留存默认关闭；开启后保留期默认 ≤ 30 天且可配生效，用户可单独关闭（接住数据合规留存）。
- [ ] 合规分组发起的请求不命中任何 `region=overseas` 渠道，仅命中境内渠道。
- [ ] 价格页与控制台均可见每 provider 的数据驻地（境内/境外）标注（接住 F-5019）。
- [ ] 用户首次调用前未接受含出境与留存条款的协议 → 调用被拦截返回需同意提示（接住 F-5021）。
- [ ] 账号注销后：令牌/OAuth 绑定删除，PII 字段清空或匿名化，历史日志 IP 末段置零。
- [ ] 调用日志元数据保留 180 天、审计日志 ≥ 1 年、计费数据 ≥ 3 年（仅归档不可删），超期自动归档/清理。

### 7. 触及范围
数据分级登记 · prompt 留存开关与保留期 · 合规分组境内选渠 · VendorMeta 驻地标注 · 隐私同意闸门 · 注销级联删除与 IP 匿名化 · DC-001~DC-012 · 脱敏接 X3、归档接 X5、出境告知接 F-5019、同意接 F-5021。

---

## X7 RBAC（三级系统角色 / self-scope 越权防护 / 功能权限组 / 高危二次验证 / 矩阵配置化 / 团队预留）

- **功能 ID / 优先级**：F-5031、F-5032、F-5033 / P0；F-5030、F-5034 / P1（F-5034 P2）；F-5035 / P3
- **来源**：ROLE-PERMISSION-MATRIX（7 产品角色 × 12 操作域，84 授权单元）；`GLOSSARY` 三级角色 + `AdminAuth`/`RootAuth` 中间件、`model` 层 user_id 过滤（self-scope）、`secure_verification.go`（高危闸门）、`controller/option.go`/`Audit`（矩阵配置化）、SG-006 多租户决策（团队预留）
- **角色 / Owner**：系统角色鉴权 = 全部（中间件强制）；功能权限组/矩阵配置 = Root；self-scope = 用户/开发者；Owner 模块 = RBAC
- **触发**：每请求经中间件鉴权；访问资源时按 user_id 过滤；高危操作经二次验证；Root 分配权限组或改矩阵；阶段二创建团队

### 1. 场景
RBAC 以 repo 三级系统角色（common/admin/root）为底座，落地 7 产品角色 × 12 操作域矩阵。每请求经 AdminAuth/RootAuth 中间件鉴权，越权访问路由返回 403。User/Developer 的所有数据操作（令牌/额度/任务/日志）由后端按 user_id 强制过滤为 self-scope，访问他人资源返回 403。取 Key、改倍率、重置 2FA、改全站 Option 等高危操作统一接入 SecureVerification 二次验证闸门，无二次验证调用返回 403（接住 X3 取明文受控）。Root 可经功能权限组（运营集/运维集）给 admin 细分最小权限：Operator 仅得 O07~O09，SRE 仅得 O11 + O10 只读。矩阵可后台查看且变更可审计。团队/工作区（team_id + Owner/Member）为阶段二预留。

### 2. 前置条件
- AdminAuth/RootAuth 中间件已挂载到对应路由组（adminRoute/rootRoute/userRoute）。
- model 层查询已在 User/Token/Task/Log/Quota 上强制注入 user_id 过滤条件。
- SecureVerificationRequired 中间件已就绪，覆盖 §4 高危操作清单全部路径。
- 功能权限组开关已定义（运营集 = O07/O08/O09；运维集 = O11 + O10 只读），由 Root 分配。

### 3. 适用范围
系统角色鉴权覆盖全部需鉴权路由（访客仅 O01 + O02 注册/登录入口）。self-scope 覆盖 User/Developer 对令牌/额度/任务/日志的全部数据操作。高危二次验证覆盖取令牌明文、取上游渠道 Key、改计费倍率/表达式、封禁/重置用户 2FA、改全站 Option/限流/setup、配 OAuth provider、强制 GC/清缓存/部署变更。功能权限组覆盖 admin 的运营 vs 运维细分。矩阵配置化覆盖 84 授权单元的后台查看与变更审计。团队预留覆盖阶段二的 Team Owner/Member 二级角色（不与平台系统角色冲突）。

### 4. 失败处理
| 触发条件 | 系统行为 | 可观测结果 |
|---|---|---|
| common 角色访问 admin 路由 | AdminAuth 拒绝，返回 403 | 越权被拒并可审计 |
| admin 角色访问 root 路由（如改 Option） | RootAuth 拒绝，返回 403 | O12 仅 Root，admin 越权返 403 |
| User 访问他人令牌/日志 | user_id 过滤后命中空集，返回 403 | self-scope 越权返 403 |
| 高危操作无二次验证 | SecureVerification 拒绝，返回 403 | 拒绝并记审计（接 X3） |
| SRE 尝试改计费（O08） | 功能权限组未授予，返回 403 | 运维越界访问运营域被拒 |
| Operator 尝试运维操作（O11） | 功能权限组未授予，返回 403 | 运营越界访问运维域被拒 |

### 5. 配置项（角色 / self-scope / 权限组 / 二次验证 / 矩阵 / 团队）
- **系统角色**（`§1 User`）：`Role∈{common,admin,root}`，路由组挂 `AdminAuth`/`RootAuth`。
- **self-scope**（model 层）：User/Token/Task/Log/Quota 查询强制 `where user_id=:caller`。
- **功能权限组**（`§15 Option` 派生）：`permission_set.operator=[O07,O08,O09]`、`permission_set.sre=[O11,O10:readonly]`，由 Root 分配。
- **二次验证**（`secure_verification.go`）：高危 action 列表统一接入 `SecureVerificationRequired`。
- **矩阵**（`Audit`/`§15 Option`）：84 授权单元后台只读视图，变更写 `Audit`。
- **团队**（阶段二，`Team` 新增）：`team_id` 外键挂 User/Token/Log，`team_role∈{owner,member}`。

### 6. 验收标准
- [ ] 7 角色 × 12 操作域逐单元可测：每个 ➖/🟡 单元的越权操作均返回 403（如 common 访问 O07 渠道管理、User 访问他人 O03 令牌、admin 访问 O12 系统设置）。
- [ ] User/Developer 访问他人令牌/额度/任务/日志一律返回 403，仅命中本人资源。
- [ ] 取 Key/改倍率/重置 2FA/改 Option 无 SecureVerification 二次验证时返回 403（接住 X3 取明文受控）。
- [ ] 启用功能权限组后：Operator 仅得 O07/O08/O09，访问 O11 返 403；SRE 仅得 O11 + O10 只读，访问 O08 返 403。
- [ ] 全站 Option/OAuth provider/setup/限流仅 Root（RootAuth）可改，admin 调用返回 403。
- [ ] 矩阵可在后台只读查看 84 授权单元，且任一矩阵变更写入可检索的审计记录。
- [ ] 阶段二团队启用：Team Member 用本团队额度调用且仅 self-scope 管自己令牌，Team Owner 可查本团队用量。

### 7. 触及范围
AdminAuth/RootAuth 三级角色中间件 · model 层 user_id self-scope 过滤 · 功能权限组运营/运维细分 · SecureVerification 高危闸门 · 矩阵配置化与审计 · Team 阶段二预留 · F-5030~F-5035 · 二次验证接 X3 取明文受控、审计接 X3 高危留痕。
