# GLOSSARY — 领域术语表（new-api 核心黑话）

> 来源：repo `repo/new-api/`（model/*.go、setting/**、pkg/billingexpr/expr.md、constant/*、relay/*）。
> 角色：统一 S1→S6 全程领域语言。所有术语以 repo 一手证据为准，标注源码 footprint。
> 约定：本产品「视觉壳=Routify，底层逻辑=New API」，下列术语为底层逻辑词汇。

## A. 网关 / 路由核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **Relay（中转 / 网关）** | 把客户端请求按协议转发到上游 AI provider 的核心链路，是产品本体。 | relay/、controller/relay.go |
| **Channel（渠道）** | 一个上游 provider 接入实例，含 Type/Key/Weight/Priority/Group/Models 等。一个 provider 可配多个渠道。 | model/channel.go |
| **Channel Type（渠道类型）** | 上游 provider 的协议类型枚举（OpenAI=1、Anthropic=14、Gemini=24…编到 57）。 | constant/channel.go |
| **端点类型（Relay Mode / Endpoint Type）** | 对外暴露的 API 形态：chat/completions、completions、embeddings、moderations、images、audio、rerank、responses、realtime、Claude `/v1/messages`、Gemini `/v1beta`、MJ/Suno 任务端点等。 | relay/constant/relay_mode.go |
| **Ability（能力表）** | 渠道 × 模型 × 分组的可用性映射，路由选渠道时按此匹配「满足条件渠道」。 | model/ability.go |
| **模型映射 / 重定向（Model Mapping / Redirect）** | 把客户端请求的模型名重写为上游实际模型名（如 `gpt-4`→上游别名）。 | model/channel.go ModelMapping |
| **状态码映射（Status Code Mapping）** | 把上游返回状态码重映射，用于错误规整 / 触发重试或禁用。 | model/channel.go StatusCodeMapping |
| **AutoBan（自动禁用）** | 渠道连续失败时自动置为禁用，避免持续打到坏渠道。 | model/channel.go AutoBan |
| **多 Key 模式（Multi-Key）** | 单渠道挂多个上游 key，按 MultiKeyMode 轮询 / 随机，独立维护各 key 状态。 | model/channel.go IsMultiKey/MultiKeyMode |
| **参数 / 请求头覆写（ParamOverride / HeaderOverride）** | 渠道级强制覆盖请求体参数或 HTTP 头。 | model/channel.go |

## B. 计费 / 额度核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **Quota（额度）** | 用户 / 令牌的可消费配额，内部以整数计量；消费即扣减。 | service/quota.go、model/user.go |
| **倍率计费（Ratio Billing）** | 最终价 = model_ratio（模型倍率）× group_ratio（分组倍率）× completion_ratio（补全倍率）。 | setting/ratio_setting/* |
| **model_ratio（模型倍率）** | 单个模型相对基准价的倍数。 | setting/ratio_setting/model_ratio.go |
| **group_ratio（分组倍率）** | 用户分组的价格系数（不同分组不同折扣）。 | setting/ratio_setting/group_ratio.go |
| **completion_ratio（补全倍率）** | 输出 token 相对输入 token 的价格倍数。 | setting/ratio_setting |
| **cache_ratio（缓存倍率）** | 命中上游缓存（cached tokens）部分的优惠倍率。 | setting/ratio_setting/cache_ratio.go |
| **expose_ratio / exposed_cache** | 对外公开展示的价格倍率（价格页用），与内部计费倍率分离。 | setting/ratio_setting/expose_ratio.go |
| **阶梯计费（Tiered Billing）** | 按用量档位（如 token 长度档 len）分段定价。 | setting/billing_setting/tiered_billing.go、service/tiered_settle.go |
| **表达式计费（billingexpr）** | 用单条 DSL 表达式定义全部计价逻辑。变量：`p`(prompt)/`c`(completion)/`cr`(cache read)/`cc`(cache create)/`cc1h`/`img`/`ai`(audio in)/`ao`(audio out)/`len`；函数：`tier()`/`param()`/`header()`/`has()`/`hour()`；请求规则 `|||when(...)*N`；版本前缀 `v1:`。`p`/`c` 自动排除已单独计价子类。 | pkg/billingexpr/expr.md |
| **预扣额度（Pre-consume）** | 请求前按估算 token **冻结**额度（BillingSnapshot），上游返回后按真实 token **结算**（TryTieredSettle），多退少不补。 | service/quota.go、service/tiered_settle.go |
| **BillingSnapshot（计费快照）** | 预扣时刻记录的冻结额度 + 估算依据，结算时对账用。 | service/quota.go |
| **充值（TopUp）** | 用户通过支付网关 / 兑换码增加 Quota 的记录。 | model/topup.go |
| **兑换码（Redemption）** | 可批量生成、含 Quota / Count / 过期时间的充值码。 | model/redemption.go |
| **订阅（Subscription）** | 周期性计费计划（SubscriptionPlan / Order / UserSubscription，状态 active|expired|cancelled），含订阅维度预扣 / 退款记录（PreConsumeRecord consumed|refunded）。 | model/subscription.go |

## C. 令牌 / 身份核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **Token / API Key（令牌 / 密钥）** | 用户创建的对外调用凭证（`sk-...`），独立额度、模型白名单、IP 白名单、分组、过期时间。注意：上游 Channel 的 `Key` 是另一概念（上游凭证）。 | model/token.go |
| **ModelLimits（模型白名单）** | 令牌可调用的模型集合（Enabled + 列表）。 | model/token.go |
| **AllowIps（IP 白名单）** | 令牌允许来源 IP。 | model/token.go |
| **UnlimitedQuota（无限额度）** | 令牌不限额度标志；ExpiredTime=-1 表示永不过期。 | model/token.go |
| **用户分组（User Group）** | 用户所属分组，决定 group_ratio 与可用渠道集合。 | model/user.go、setting 可用分组 |
| **角色（Role）** | admin / common / root 三级权限。 | model/user.go |

## D. 高级路由 / 缓存核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **渠道分组（Channel Group）** | 渠道归属的逻辑分组；令牌 / 用户按分组匹配渠道。特殊分组 `auto` 触发跨组重试。 | model/channel.go Group、setting/auto_group.go |
| **优先级 + 权重路由** | 同分组内先按 Priority 选最高优先级，再按 Weight 随机选满足渠道（GetRandomSatisfiedChannel）。 | service/channel_select.go |
| **跨分组重试（Cross-Group Retry）** | 仅 `auto` 分组有效；单组优先级耗尽后自动切换下一分组重试，提升可用性。令牌级开关 CrossGroupRetry。 | service/channel_select.go、model/token.go |
| **亲和缓存（Affinity Cache）** | 按会话键（如 Codex `prompt_cache_key`、Claude `metadata.user_id`）把同一会话粘到同一渠道，命中后续请求复用上游缓存。配置 SwitchOnSuccess/TTL/MaxEntries/SkipRetryOnFailure。 | setting/operation_setting/channel_affinity_setting.go |
| **会话键 / key_sources** | 亲和缓存提取会话键的来源：gjson（请求体路径）/ header / context，配 model_regex / path_regex 匹配。 | channel_affinity_setting.go |

## E. 增长 / 运营核心（旗舰能力）

| 术语 | 定义 | 证据 |
|---|---|---|
| **签到（Check-in）** ★#1 | 每日一次随机额度奖励（MinQuota..MaxQuota）；user_id+checkin_date 唯一约束防重复。 | controller/checkin.go、model/checkin.go |
| **邀请返利 / 分销（Affiliate / Aff）** ★#2 | 邀请码（AffCode）裂变：被邀人注册归因到 InviterId，邀请人获 AffQuota（邀请剩余额度）/ AffHistoryQuota（历史累计），可划转为可用额度。 | model/user.go、controller/user.go |
| **AffCode（邀请码）** | 用户唯一邀请码，新用户注册时填写以建立邀请关系。 | model/user.go |
| **QuotaForInviter / QuotaForNewUser** | 邀请成功时分别发给邀请人 / 新用户的额度。 | common.QuotaForInviter / QuotaForNewUser |
| **TransferAffQuota（邀请额度划转）** | 把累积的邀请奖励额度转为可正常消费的额度。 | controller/user.go |
| **Telegram 登录** ★#3 | 基于 Telegram Login Widget + BotToken HMAC-SHA256 校验的免密登录 / 账号绑定。 | controller/telegram.go |

## F. 异步任务核心（旗舰#4）

| 术语 | 定义 | 证据 |
|---|---|---|
| **异步任务中心（Task Center）** ★#4 | 长耗时生成任务（绘图 / 音乐 / 视频）的提交→轮询/回调→进度→列表→重试统一体系。 | model/task.go、controller/task.go |
| **Task 状态机** ★#4 | NOT_START→SUBMITTED→QUEUED→IN_PROGRESS→SUCCESS / FAILURE / UNKNOWN。字段含 Progress / FailReason / Quota / PrivateData。 | model/task.go |
| **TaskPlatform** | 任务所属平台（suno / mj …）。 | constant/task.go |
| **Midjourney 任务** ★#4 | Action/MjId/Prompt/Buttons/ImageUrl/VideoUrl；端点 imagine/change/blend/describe/modal/shorten/action/edits/video。 | model/midjourney.go |
| **Suno 任务** ★#4 | 音乐 / 歌词生成（SunoAction MUSIC/LYRICS）。 | constant/task.go |

## G. 预填 / 元数据核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **预填分组（Prefill Group）** ★#5 | 可复用的「组」预设（Type=model/tag/endpoint，Items JSON 数组），用于前端下拉快速填充渠道 / 令牌配置，减少重复输入。 | model/prefill_group.go |
| **ModelMeta（模型元数据）** | 模型的展示 / 分类 / 价格等元信息。 | model/model_meta.go |
| **VendorMeta（供应商元数据）** | provider / 厂商的展示元信息（模型广场分组用）。 | model/vendor_meta.go |
| **Missing Models（缺失模型）** | 上游探测到但本地未登记的模型，供管理端补登。 | model/missing_models.go |

## H. 数据 / 运维核心

| 术语 | 定义 | 证据 |
|---|---|---|
| **Log（日志）** | 每次调用 / 系统事件的明细（LogTypeSystem 等类型）。 | model/log.go |
| **UseData / QuotaData（用量数据）** | 按日 / 按用户聚合的配额消费数据。 | model/usedata.go |
| **Rankings（排行）** | 模型 / 用户用量排行榜。 | model/usedata_rankings.go |
| **Option（系统选项）** | 全站 KV 配置项（root 可改）。 | model/option.go |
| **PerfMetric（性能指标）** | 网关性能统计（公开 summary）。 | model/perf_metric.go |
| **限流（Rate Limit）** | GlobalAPI / ModelRequest / Critical / EmailVerification / Search 多层限流。 | setting/** |
| **Turnstile** | Cloudflare 人机校验（用于签到 / 注册防刷）。 | controller/checkin.go |

## I. 产品 / 品牌

| 术语 | 定义 | 证据 |
|---|---|---|
| **Routify** | 本产品对外品牌（视觉壳），定位「原生满血、远低官方价格、一行接入」。 | WEBSITE-COVERAGE.md |
| **New API** | 底层开源逻辑来源（QuantumNous/new-api）。AGENTS.md Rule5 声明其品牌为受保护标识。 | repo AGENTS.md |
| **io.net** | 可选 GPU 模型部署集群上游。 | controller/deployment.go |
