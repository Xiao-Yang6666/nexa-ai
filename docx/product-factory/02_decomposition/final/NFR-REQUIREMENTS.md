# NFR-REQUIREMENTS — 非功能需求 + 数据与合规（横切维度）

> 适用范围：基于 new-api 底层逻辑构建的 AI API 网关 SaaS「Routify」。
> 角色：本文件覆盖上一轮 S2 **完全缺失**的三个企业级横切维度中的两个——**NFR（非功能需求）** 与 **数据/合规**。第三维度「权限与租户」见同目录 `ROLE-PERMISSION-MATRIX.md`。
> 权威边界：功能/逻辑/数据模型是否存在 → repo new-api 一手（REPO-INSPECTION / INTEGRATION-LIST / GLOSSARY）；具体阈值（QPS/延迟/SLA 百分位）repo 未固化 → 本文件按 **企业级 AI 网关行业基线** 给出**可度量目标值**，并显式标注为「目标/SLO」而非 repo 既有事实。
> 度量原则：每条 NFR 均给 **指标 + 目标值 + 测量方法**，可被 S6 写成验收用例、被运行期监控直接采集。
> 编号：NFR-Pxx 性能 / NFR-Axx 可用性 / NFR-Sxx 安全 / NFR-Oxx 可观测性 / NFR-Exx 伸缩性 / DC-xxx 数据与合规。

---

## 0. NFR 体系总览（分 5 大类 + 数据合规 1 大类）

| 类别 | 编号段 | 关注点 | 条目数 |
|---|---|---|---|
| 性能 Performance | NFR-P01~P08 | QPS、首字延迟、转发开销、并发、计费/选渠开销 | 8 |
| 可用性 Availability | NFR-A01~A08 | 渠道降级、跨组重试、AutoBan、容灾、SLA、限流保护 | 8 |
| 安全 Security | NFR-S01~S10 | 密钥加密存储、Key 不回显、prompt 脱敏、鉴权、限流、防刷 | 10 |
| 可观测性 Observability | NFR-O01~O07 | 指标、告警、链路追踪、日志分级、用量数据 | 7 |
| 伸缩性 Scalability | NFR-E01~E06 | 无状态横扩、缓存、DB、渠道数、租户规模 | 6 |
| 数据与合规 Data & Compliance | DC-001~DC-012 | 保留期、PII、prompt/响应脱敏与留存、数据出境 | 12 |

> NFR 不是散点：每个上层类别下条目互相支撑（如 P 的「转发开销」是 A 的「降级判定」前提，S 的「prompt 脱敏」是 DC 的「留存策略」实现手段，O 的「指标」是 A 的「SLA 度量」与 E 的「扩容触发」数据源）。

---

## 1. 性能 Performance（NFR-Pxx）

> 网关型产品的性能本质 = **自身转发开销** + **路由/计费/限流判定开销**，不含上游推理耗时（不可控）。因此延迟指标必须区分「网关附加延迟」与「端到端延迟」。

| ID | 指标 | 目标值（SLO） | 测量方法 | 关联 repo 证据 |
|---|---|---|---|---|
| NFR-P01 | 网关附加延迟（不含上游）p50 | ≤ 15 ms | APM 打点：入站收齐请求体 → 选渠完成 → 发出上游请求 的耗时差 | service/channel_select.go 选渠路径 |
| NFR-P02 | 网关附加延迟 p99 | ≤ 60 ms | 同上，p99 百分位 | — |
| NFR-P03 | 流式首字延迟（TTFB，端到端）p95 | ≤ 上游 TTFB + 80 ms | 流式响应首个 chunk 时间戳 − 客户端请求时间戳，扣除上游 TTFB | relay/ 流式透传链路 |
| NFR-P04 | 单实例稳态吞吐 | ≥ 1500 req/s（chat 转发，含选渠+预扣+日志） | 压测工具（k6/wrk）恒定负载 10 min，错误率 < 0.1% | controller/relay.go |
| NFR-P05 | 单实例并发连接（含流式/realtime WS） | ≥ 5000 并发长连接 | 建立 N 路 SSE/WS 并维持，观测内存与丢连率 | relay realtime WS、流式 SSE |
| NFR-P06 | 选渠决策开销（GetRandomSatisfiedChannel + 亲和缓存命中） | p99 ≤ 5 ms | 选渠函数内部计时埋点 | service/channel_select.go、channel_affinity_setting.go |
| NFR-P07 | 预扣额度（pre-consume）+ 结算（settle）单次开销 | p99 ≤ 8 ms（缓存命中路径） | service/quota.go / tiered_settle.go 计时埋点 | service/quota.go、service/tiered_settle.go |
| NFR-P08 | 计费表达式（billingexpr）单次求值 | p99 ≤ 2 ms | DSL 求值器基准测试（go bench） | pkg/billingexpr |

**派生功能项**：性能指标埋点（→ F-5001）、压测/基准门禁（→ F-5002）。

---

## 2. 可用性 Availability（NFR-Axx）

> 可用性核心机制 repo **已有一手实现**：AutoBan（坏渠道自动禁用）、StatusCodeMapping（错误规整触发重试）、跨分组重试（auto 分组）、多 Key 轮询、亲和缓存 SkipRetryOnFailure。NFR 在此之上定**目标值与编排策略**。

| ID | 指标 / 机制 | 目标值（SLO） | 测量方法 | 关联 repo 证据 |
|---|---|---|---|---|
| NFR-A01 | 网关服务月度可用性 SLA（控制面 + 数据面） | 数据面 ≥ 99.9%，控制面 ≥ 99.5% | 健康探针 + 合成监控按分钟采样，月度聚合 | — |
| NFR-A02 | 渠道降级：单上游 provider 故障时请求自动绕行 | 故障渠道命中后 ≤ 1 次重试内切换到健康渠道；用户侧成功率不掉档 | 注入单 provider 5xx，观测端到端成功率 | StatusCodeMapping、AutoBan、GetRandomSatisfiedChannel |
| NFR-A03 | 跨分组重试（auto 分组） | 单分组优先级耗尽后自动切下一分组；总重试次数 ≤ RetryTimes 配置上限 | 构造首选分组全失败，验证切组 | service/channel_select.go、common.RetryTimes、token.CrossGroupRetry |
| NFR-A04 | AutoBan 自愈 | 渠道恢复后可经周期探测/手动测试重新启用，无需重启 | channel-test 全量测试触发重启用 | controller/channel-test.go、AutoBan |
| NFR-A05 | 多 Key 故障隔离 | 单 Key 失效仅禁用该 Key，渠道按 MultiKeyMode 继续轮询其余 Key | 失效一个 Key，验证渠道仍可用 | model/channel.go MultiKeyMode/MultiKeyStatusList |
| NFR-A06 | 限流保护（过载不雪崩） | 超限请求返回 429 而非拖垮上游/DB；Critical 操作独立限流桶 | 超阈压测，观测降级为 429 而非 5xx | GlobalAPIRateLimit/ModelRequestRateLimit/CriticalRateLimit |
| NFR-A07 | 缓存/DB 失联降级 | Redis 失联时回落内存缓存继续选渠（只读路径不中断） | 切断 Redis，验证转发仍可服务 | Redis + 内存缓存双路径（AGENTS.md） |
| NFR-A08 | 容灾 RTO / RPO | RTO ≤ 30 min、RPO ≤ 5 min（DB 主从 + 定期备份） | 演练：主库故障 → 从库接管计时 | DB 三选一全兼容（运维期配置） |

**派生功能项**：渠道健康度看板（→ F-5003）、SLA 月报（→ F-5004）、容灾演练手册（→ F-5005）。

---

## 3. 安全 Security（NFR-Sxx）

> 本类是企业级 SaaS 的红线。repo 已有 SecureVerificationRequired 中间件、CriticalRateLimit + DisableCache（取明文 key 路径）、2FA/Passkey/Turnstile 等一手基础。NFR 在此固化**密钥加密存储、Key 不回显、prompt 脱敏**三条核心硬要求。

| ID | 要求 | 可度量目标 | 测量方法 | 关联 repo 证据 |
|---|---|---|---|---|
| NFR-S01 | **上游渠道 Key 加密静态存储** | DB 中 Channel.Key、支付 WebhookSecret 等敏感字段以 AES-256-GCM（或 KMS 信封加密）密文存储，明文不落库 | 直查 DB 字段为密文；密钥轮换可执行 | model/channel.go Key、setting/payment_*.go WebhookSecret |
| NFR-S02 | **令牌 Key 取用受控** | 列表/详情接口默认**不回显**完整明文 sk-key（仅展示掩码 `sk-***xxxx`）；取明文走独立受保护接口 + CriticalRateLimit + 二次验证 | 抓包验证列表无明文；明文接口需 SecureVerification | controller/token.go（CriticalRateLimit+DisableCache）、secure_verification.go |
| NFR-S03 | **管理端取渠道 Key 二次验证** | 后台查看上游 Key 必须经 `/api/verify` 通用二次验证（SecureVerificationRequired） | 无二次验证调用返回 403 | secure_verification.go、REPO-INSPECTION D1 |
| NFR-S04 | **日志中 prompt/响应脱敏** | 默认**不**记录完整 prompt/响应正文；如开启内容留存须脱敏 PII（邮箱/手机/身份证/卡号/API Key 形态串）后存储；脱敏规则可配置 | 注入含 PII 的 prompt，验证日志中已掩码 | model/log.go（默认记元数据；正文留存为可选策略，见 DC-005） |
| NFR-S05 | **传输加密** | 所有对外端点强制 TLS 1.2+；管理面禁用明文 HTTP | TLS 扫描（testssl.sh）无弱套件 | — |
| NFR-S06 | **鉴权强度** | 支持 JWT + 2FA + Passkey/WebAuthn；管理端/Root 操作强制 2FA 可配 | 关闭 2FA 的管理账号触发策略告警 | controller/twofa.go、passkey.go |
| NFR-S07 | **防刷/人机校验** | 注册、签到、找回密码受 Turnstile + 分级限流保护 | 脚本批量请求被拦 | TurnstileCheck、EmailVerificationRateLimit |
| NFR-S08 | **IP 白名单 / 令牌作用域最小化** | 令牌支持 AllowIps、ModelLimits 模型白名单，越权调用拒绝 | 白名单外 IP / 名单外模型返回 403 | model/token.go AllowIps/ModelLimits |
| NFR-S09 | **敏感操作审计留痕** | 取 Key、改计费倍率、禁用渠道、改全站 Option 等高危操作写审计日志（操作人/时间/对象/前后值） | 审计查询可检索到操作记录 | controller/audit.go、log.go |
| NFR-S10 | **Webhook 入站验签** | Stripe/Creem 等支付回调强制校验签名（WebhookSecret），拒绝伪造 | 伪造签名回调被拒 | StripeWebhookSecret、CreemWebhookSecret |

**派生功能项**：Key 掩码与受控取用（→ F-5006）、Key/Secret 加密存储（→ F-5007）、prompt 脱敏管线（→ F-5008）、高危操作审计（→ F-5009）。

---

## 4. 可观测性 Observability（NFR-Oxx）

> repo 已有 perf-metrics（公开 summary）、Uptime-Kuma 接入、Log/UseData/Rankings、磁盘缓存/GC 管理。NFR 把这些上升为**指标-告警-追踪三件套**。

| ID | 维度 | 可度量目标 | 测量方法 | 关联 repo 证据 |
|---|---|---|---|---|
| NFR-O01 | 核心指标采集（RED） | 暴露 Prometheus 指标：每渠道/每模型的请求率(R)、错误率(E)、延迟分布(D)；额度消费速率 | `/metrics` 可被抓取，含上述维度标签 | controller/perf_metrics.go、PerfMetric |
| NFR-O02 | 业务指标 | 实时额度余额预警、充值/订阅转化、签到/邀请活跃 | 指标面板可见 | usedata.go、checkin、aff |
| NFR-O03 | 告警 | 渠道错误率 > 阈值、额度预警、限流触发率、p99 延迟超 SLO 触发多渠道告警（Email/Webhook/Bark） | 注入故障验证告警送达 | NotificationSettings（email/webhook/bark） |
| NFR-O04 | 分布式链路追踪 | 每请求贯穿 trace_id（入站→选渠→上游→结算），支持 OpenTelemetry 导出 | 单请求可在 APM 中端到端串联 | relay 链路（埋点为新增能力） |
| NFR-O05 | 日志分级与检索 | 日志分 system/调用/审计 类型，支持按用户/令牌/渠道/模型/时间检索 | 日志查询接口按条件过滤命中 | controller/log.go、usedata.go |
| NFR-O06 | 健康探针 | 提供 liveness/readiness 探针 + Uptime-Kuma 上报 | 探针返回组件健康（DB/Redis/上游探测） | controller/uptime_kuma.go、misc.go |
| NFR-O07 | 用量数据准实时 | QuotaData 按日聚合延迟 ≤ 5 min；排行榜可查 | 对账用量数据与日志一致 | usedata.go、usedata_rankings.go |

**派生功能项**：Prometheus 指标导出(→ F-5010)、多渠道告警编排(→ F-5011)、链路追踪 trace_id(→ F-5012)。

---

## 5. 伸缩性 Scalability（NFR-Exx）

| ID | 维度 | 可度量目标 | 测量方法 | 关联 repo 证据 |
|---|---|---|---|---|
| NFR-E01 | 无状态横向扩展 | 转发实例无本地状态（会话/选渠状态走 Redis），可水平加节点线性提吞吐 | 2→4 实例，吞吐近线性增长 | Redis 共享缓存、内存缓存为只读回落 |
| NFR-E02 | 缓存命中率 | 渠道/令牌/Ability 缓存命中率 ≥ 95%，DB 选渠查询占比 < 5% | 缓存命中率指标 | model/channel_cache.go、token_cache.go、ability |
| NFR-E03 | DB 扩展 | 兼容 MySQL/PostgreSQL 读写分离；日志表可分区/归档 | 读副本承接查询负载 | GORM v2、log 表 |
| NFR-E04 | 渠道规模 | 支撑 ≥ 1000 个渠道、≥ 52 类上游 provider 同时在线选渠不退化 | 大规模渠道配置下 NFR-P06 仍达标 | constant/channel.go(57)、INTEGRATION-LIST |
| NFR-E05 | 限流弹性 | 限流阈值按实例数/租户分级可配，扩容时自动放大全局桶 | 改实例数后全局限流随之调整 | GlobalAPIRateLimit 等 |
| NFR-E06 | 租户/用户规模 | 单部署支撑 ≥ 10 万用户、≥ 50 万令牌的列表/计费不退化 | 大数据量下分页查询 p99 达标 | model/user.go、token.go |

**派生功能项**：无状态化与共享缓存(→ F-5013)、缓存命中率监控(→ F-5014)、日志归档分区(→ F-5015)。

---

## 6. 数据与合规 Data & Compliance（DC-xxx）

> 关键 GAP：SG-006 指出 repo 为**单租户**用户/分组/角色模型；合规策略需在此前提下定，并为后续多租户预留（见 ROLE-PERMISSION-MATRIX 多租户决策）。

| ID | 主题 | 可度量策略 | 测量方法 | 关联 repo / GAP |
|---|---|---|---|---|
| DC-001 | 数据分级 | 数据分四级：① 凭证类（渠道 Key/令牌 Key/支付 Secret）② PII（邮箱/手机/第三方 OAuth ID/IP）③ 内容类（prompt/响应正文）④ 计量/运营类（额度/日志元数据/用量） | 字段清单标注分级 | model/* 全表 |
| DC-002 | 凭证类保留 | 渠道 Key/令牌 Key 加密存储，删除即不可逆销毁；轮换记录保留 90 天审计 | 删除后 DB 无残留明文 | NFR-S01/S02 |
| DC-003 | PII 处理 | OAuth 第三方 ID（GitHub/Discord/OIDC/LinuxDO/WeChat/Telegram）仅存绑定标识，不存第三方明文资料；邮箱/手机最小化收集；用户可申请导出/删除（账号注销级联） | 注销后 PII 字段清空或匿名化 | model/user.go OAuth binding、user_oauth_binding.go |
| DC-004 | IP 数据 | 调用日志中的来源 IP 默认保留 ≤ 90 天后匿名化（末段置零） | 超期日志 IP 已匿名 | model/log.go、token.AllowIps |
| DC-005 | **prompt/响应留存策略** | 默认**仅留元数据**（模型、token 数、耗时、渠道、费用），**不留**正文；如租户/合规要求开启正文留存，须：①经 NFR-S04 脱敏 ②设独立保留期（默认 ≤ 30 天）③可按用户关闭 | 正文留存开关 + 保留期可配且生效 | model/log.go、NFR-S04 |
| DC-006 | 日志保留期 | 调用日志元数据默认保留 180 天；审计日志保留 ≥ 1 年；超期自动归档/清理 | 定时任务清理超期数据 | log.go、usedata 归档 |
| DC-007 | 用量/计费数据保留 | QuotaData/账单数据保留 ≥ 3 年（财税/对账合规）；不可删除仅可归档 | 历史账单可追溯 3 年 | usedata.go、topup.go、subscription.go |
| DC-008 | 数据出境（关键） | 用户 prompt 转发至境外上游（OpenAI/Anthropic/Gemini 等）构成**数据跨境**；须：①向用户明示「请求将转发至所选 provider 所在地区」②按 provider 标注数据驻地③允许按分组限制仅用境内 provider（合规分组） | 合规分组仅命中境内渠道 | INTEGRATION-LIST §1（国际 vs 国内 provider）、group_ratio/可用分组 |
| DC-009 | 数据驻地标注 | 每个 channel/provider 标注数据处理地区（境内/境外），价格页与控制台可见 | provider 元数据含 region 字段 | vendor_meta.go、INTEGRATION-LIST |
| DC-010 | 同意与告知 | 隐私政策/用户协议可配并需用户接受；含数据出境与内容留存条款 | 未接受协议不可调用 | controller/option.go（隐私政策/用户协议内容） |
| DC-011 | 删除权 / 可携权 | 支持账号注销（级联删除令牌/绑定/PII）、用量数据导出 | 注销与导出接口可用 | model/user.go、usedata.go |
| DC-012 | 备份加密 | DB 备份静态加密；备份保留期与 DC-006/007 对齐；恢复演练含合规校验 | 备份文件为密文 | 运维期配置、NFR-A08 |

**派生功能项**：数据分级登记(→ F-5016)、prompt 留存开关与保留期(→ F-5017)、合规分组(仅境内 provider)(→ F-5018)、数据出境告知(→ F-5019)、账号注销级联删除(→ F-5020)、隐私政策同意闸门(→ F-5021)。

---

## 7. NFR ↔ 既有 repo 能力对照（反 overclaim）

| NFR 主题 | repo 已有一手能力 | NFR 新增（目标值/编排，非 repo 事实） |
|---|---|---|
| 可用性 | AutoBan、StatusCodeMapping、跨组重试、多 Key、亲和缓存 | SLA 百分位、RTO/RPO、降级成功率目标 |
| 安全 | SecureVerification、CriticalRateLimit、2FA/Passkey/Turnstile、多层限流 | Key 加密静态存储、Key 不回显掩码、prompt 脱敏管线（目标态） |
| 可观测性 | perf-metrics、Uptime-Kuma、Log/UseData/Rankings | Prometheus RED 指标、OTel 链路追踪、多渠道告警 SLO |
| 伸缩性 | Redis+内存双缓存、channel/token cache、三类 DB | 无状态横扩目标、命中率/规模量化目标 |
| 数据合规 | 日志/用量模型、OAuth 绑定、隐私政策内容位 | 保留期、脱敏、数据出境、删除权（多为目标策略） |

> 标注「目标态/SLO」的条目为本阶段引入的 NFR 决策，**不得**回填为 repo 既有事实。
