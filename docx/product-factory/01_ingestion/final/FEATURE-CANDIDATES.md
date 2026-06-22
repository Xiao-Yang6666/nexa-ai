# FEATURE-CANDIDATES — 候选功能清单（FC-xxx）

> 来源：`REPO-INSPECTION.md` 能力域全景（P0 功能/逻辑权威，repo `repo/new-api/`）+ `WEBSITE-COVERAGE.md`（P0 视觉/公开体验，routifyapi.com）。
> 角色：S1 候选池。**穷尽**枚举 repo 能力面 + 公开站点候选，作为 S2 反向覆盖基准（S2 必须逐条裁决：保留/改造/省略/增强）。
> 编号规则：FC-001..FC-NNN，按能力域 D1..D17 分组。标 ★ 者为本轮 5 个旗舰能力落点。
> 状态列：`repo证实`=源码确证；`站点证实`=镜像证据；`GAP`=证据不足（详见 SEMANTIC-GAPS.md）。

## D1. 账号与身份（Account & Identity）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-001 | 邮箱注册 / 登录 / 登出 | D1 | controller/user.go | repo证实 |
| FC-002 | 邮箱验证码（注册校验 / 找回密码） | D1 | controller/user.go、EmailVerificationRateLimit | repo证实 |
| FC-003 | 找回 / 重置密码 | D1 | controller/user.go | repo证实 |
| FC-004 | 用户 CRUD（管理端 ManageUser/CreateUser/SearchUsers） | D1 | controller/user.go | repo证实 |
| FC-005 | 用户角色（admin/common/root）与权限分级 | D1 | model/user.go | repo证实 |
| FC-006 | 用户分组（user group，影响分组倍率/可用渠道） | D1 | model/user.go、setting 可用分组 | repo证实 |
| FC-007 | 用户级 setting / 备注（admin 备注、个人偏好） | D1 | controller/user.go | repo证实 |
| FC-008 | GitHub OAuth 登录 / 绑定 | D1 | controller/oauth.go、`/api/oauth/github` | repo证实 |
| FC-009 | Discord OAuth 登录 / 绑定 | D1 | controller/oauth.go、setting/system_setting/discord.go | repo证实 |
| FC-010 | OIDC OAuth 登录 / 绑定（通用 OIDC discovery） | D1 | controller/oauth.go、system_setting/oidc.go | repo证实 |
| FC-011 | LinuxDO OAuth 登录 / 绑定 | D1 | controller/oauth.go、model/user.go LinuxDOId | repo证实 |
| FC-012 | WeChat（微信）登录 / 绑定（非标准 provider） | D1 | controller/wechat.go | repo证实 |
| FC-013 | 自定义 OAuth provider 管理（root，discovery + CRUD） | D1 | controller/custom_oauth.go、model/custom_oauth_provider.go | repo证实 |
| FC-014 | 多渠道身份绑定管理（统一 UserOAuthBinding） | D1 | model/user_oauth_binding.go | repo证实 |
| FC-015 | Passkey / WebAuthn 注册 / 登录 / 校验 / 删除（含管理端重置） | D1 | controller/passkey.go、model/passkey.go | repo证实 |
| FC-016 | 2FA 双因子（setup/enable/disable/备份码/管理端统计与禁用） | D1 | controller/twofa.go、model/twofa.go | repo证实 |
| FC-017 | 通用二次验证 `/api/verify`（SecureVerificationRequired 保护敏感动作） | D1 | controller/secure_verification.go | repo证实 |
| FC-018 | Telegram 登录 / 绑定（HMAC 校验 Login Widget） ★#3 | D1 | controller/telegram.go | repo证实 |

## D2. 签到 / 每日奖励（Check-in）★旗舰#1

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-019 | 每日签到（随机额度奖励 MinQuota..MaxQuota） ★#1 | D2 | controller/checkin.go、model/checkin.go | repo证实 |
| FC-020 | 签到状态查询（本月记录 + 累计统计） ★#1 | D2 | `GET /api/user/checkin` | repo证实 |
| FC-021 | 签到防重复（user_id+checkin_date 唯一约束 + 事务） ★#1 | D2 | model/checkin.go | repo证实 |
| FC-022 | 签到开关与额度区间配置（管理端 Enabled/Min/Max） ★#1 | D2 | setting/operation_setting/checkin_setting.go | repo证实 |
| FC-023 | 签到人机校验（TurnstileCheck） | D2 | controller/checkin.go | repo证实 |

## D3. 邀请返利 / 分销（Affiliate）★旗舰#2

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-024 | 生成 / 获取个人邀请码（aff_code 唯一） ★#2 | D3 | controller/user.go GetAffCode、`GET /api/user/self/aff` | repo证实 |
| FC-025 | 邀请码注册归因（解析 inviterId + 记录 InviterId） ★#2 | D3 | controller/user.go、model/user.go | repo证实 |
| FC-026 | 邀请奖励发放（邀请人 AffCount++ / AffQuota += QuotaForInviter） ★#2 | D3 | common.QuotaForInviter | repo证实 |
| FC-027 | 新用户邀请奖励（QuotaForNewUser） ★#2 | D3 | common.QuotaForNewUser | repo证实 |
| FC-028 | 邀请额度划转为可用额度（TransferAffQuota） ★#2 | D3 | `POST /api/user/self/aff_transfer` | repo证实 |
| FC-029 | 邀请统计（AffCount / AffQuota / AffHistoryQuota 展示） ★#2 | D3 | model/user.go | repo证实 |

## D5. 异步任务中心（Async Task Center：MJ / Suno / 视频）★旗舰#4

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-030 | 通用异步任务记录与状态机（NOT_START→SUBMITTED→QUEUED→IN_PROGRESS→SUCCESS/FAILURE） ★#4 | D5 | model/task.go | repo证实 |
| FC-031 | 用户任务列表 / 进度追踪（GetUserTask，分页 + 过滤） ★#4 | D5 | controller/task.go、`GET /api/task/self` | repo证实 |
| FC-032 | 管理端全量任务列表（GetAllTask） ★#4 | D5 | `GET /api/task` | repo证实 |
| FC-033 | Midjourney 绘图任务（imagine/change/blend/describe/modal/shorten/action/edits/video） ★#4 | D5 | model/midjourney.go、relay `/mj/submit/*` | repo证实 |
| FC-034 | MJ 任务查询 / 按条件拉取（fetch / list-by-condition） ★#4 | D5 | `/mj/task/:id/fetch`、`/mj/task/list-by-condition` | repo证实 |
| FC-035 | Suno 音乐 / 歌词任务（MUSIC/LYRICS，submit/fetch/fetch-by-id） ★#4 | D5 | relay/channel/task/suno、`/suno/submit/:action` | repo证实 |
| FC-036 | 视频生成任务（Sora/Kling/Vidu/Hailuo/Jimeng/Doubao/Vertex/Gemini） ★#4 | D5 | controller/task_video.go、relay/channel/task/* | repo证实 |
| FC-037 | 任务计费修正与重试（Task.Group「修正计费用」） ★#4 | D5 | model/task.go | repo证实 |
| FC-038 | 任务结果产物展示（ImageUrl / VideoUrl / Buttons） ★#4 | D5 | model/midjourney.go | repo证实 |

## D6. 预填分组（Prefill Group）★旗舰#5

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-039 | 预填分组 CRUD（model/tag/endpoint 三类型） ★#5 | D6 | controller/prefill_group.go、model/prefill_group.go | repo证实 |
| FC-040 | 预填分组用于前端下拉快速填充渠道/令牌配置 ★#5 | D6 | `/api/prefill_group`（AdminAuth） | repo证实 |
| FC-041 | 预填分组 Items JSON 数组 + 软删除 ★#5 | D6 | model/prefill_group.go | repo证实 |

## D7. 渠道管理与上游路由（Channel & Routing）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-042 | 渠道 CRUD / 搜索 / 批量操作 | D7 | controller/channel.go、model/channel.go | repo证实 |
| FC-043 | 渠道连通性测试（单 / 全量 test） | D7 | controller/channel-test.go | repo证实 |
| FC-044 | 渠道余额更新（channel-billing 查询上游余额） | D7 | controller/channel-billing.go | repo证实 |
| FC-045 | 按 tag 启禁 / 编辑 / 复制渠道 | D7 | controller/channel.go | repo证实 |
| FC-046 | 多 Key 模式管理（IsMultiKey/MultiKeyMode/状态列表/轮询索引） | D7 | model/channel.go | repo证实 |
| FC-047 | 模型映射 / 重定向（ModelMapping） | D7 | model/channel.go | repo证实 |
| FC-048 | 状态码映射（StatusCodeMapping） | D7 | model/channel.go | repo证实 |
| FC-049 | 自动禁用渠道（AutoBan） | D7 | model/channel.go | repo证实 |
| FC-050 | 参数 / 请求头覆写（ParamOverride / HeaderOverride） | D7 | model/channel.go | repo证实 |
| FC-051 | 上游模型探测与同步（fetch_models / upstream detect & apply） | D7 | controller/channel_upstream_update.go | repo证实 |
| FC-052 | Ollama 模型管理（pull / delete / version） | D7 | controller/channel.go | repo证实 |
| FC-053 | 优先级 + 权重随机路由（GetRandomSatisfiedChannel） | D7 | service/channel_select.go、model/ability.go | repo证实 |
| FC-054 | 渠道亲和缓存（见 D9） | D7 | controller/channel_affinity_cache.go | repo证实 |

## D8. 计费与额度（Billing & Quota）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-055 | 倍率计费（model_ratio × group_ratio × completion_ratio） | D8 | setting/ratio_setting/* | repo证实 |
| FC-056 | 缓存倍率计费（cache_ratio / exposed_cache） | D8 | setting/ratio_setting/cache_ratio.go | repo证实 |
| FC-057 | 表达式 / 阶梯计费（pkg/billingexpr，变量 p/c/cr/cc/img/ai/ao/len + tier/param/header/has/hour） | D8 | pkg/billingexpr/expr.md | repo证实 |
| FC-058 | 阶梯计费结算（TryTieredSettle / tiered_settle.go） | D8 | service/tiered_settle.go、setting/billing_setting/tiered_billing.go | repo证实 |
| FC-059 | 预扣额度（pre-consume，BillingSnapshot 冻结 → 真实结算多退少不补） | D8 | service/quota.go | repo证实 |
| FC-060 | 倍率配置管理 / 同步（ratio_config / ratio_sync） | D8 | controller/ratio_config.go、ratio_sync.go | repo证实 |
| FC-061 | 充值 / 余额查询（topup，多支付渠道） | D8 | controller/topup.go、model/topup.go | repo证实 |
| FC-062 | 兑换码（Redemption：Quota / 批量 Count / 过期） | D8 | model/redemption.go | repo证实 |
| FC-063 | 订阅计划与订单（SubscriptionPlan / Order / UserSubscription active|expired|cancelled） | D8 | controller/subscription.go、model/subscription.go | repo证实 |
| FC-064 | 订阅预扣 / 退款记录（SubscriptionPreConsumeRecord consumed|refunded） | D8 | model/subscription.go | repo证实 |
| FC-065 | 公开价格页 / 模型价格暴露（expose_ratio / pricing） | D8 | controller/pricing.go | repo证实 |

## D9. 亲和缓存（Channel Affinity Cache）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-066 | 会话粘连同一渠道（按 model_regex/path_regex/key_sources 提取会话键） | D9 | setting/operation_setting/channel_affinity_setting.go | repo证实 |
| FC-067 | 内置 codex / claude CLI header 透传模板（prompt_cache_key / metadata.user_id） | D9 | channel_affinity_setting.go | repo证实 |
| FC-068 | 亲和缓存策略（SwitchOnSuccess / TTL / MaxEntries / SkipRetryOnFailure） | D9 | controller/channel_affinity_cache.go | repo证实 |
| FC-069 | 亲和缓存用量统计（channel_affinity_usage_cache） | D9 | logRoute | repo证实 |

## D10. 跨分组重试（Cross-Group Retry）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-070 | auto 分组逐组耗尽优先级后切下一组重试 | D10 | service/channel_select.go | repo证实 |
| FC-071 | 令牌级跨分组重试开关（CrossGroupRetry，仅 auto 分组有效） | D10 | model/token.go、setting/auto_group.go | repo证实 |
| FC-072 | 全局重试次数配置（common.RetryTimes） | D10 | common.RetryTimes | repo证实 |

## D11. 令牌 / API Key（Token）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-073 | 令牌 CRUD / 搜索 / 批量删除 | D11 | controller/token.go、model/token.go | repo证实 |
| FC-074 | 取 / 批量取明文 key（CriticalRateLimit + DisableCache 保护） | D11 | controller/token.go | repo证实 |
| FC-075 | 令牌剩余额度 / 无限额度 / 过期时间（-1 永不过期） | D11 | model/token.go | repo证实 |
| FC-076 | 令牌模型白名单（ModelLimits Enabled + 列表） | D11 | model/token.go | repo证实 |
| FC-077 | 令牌 IP 白名单（AllowIps） | D11 | model/token.go | repo证实 |
| FC-078 | 令牌按分组（Group） | D11 | model/token.go | repo证实 |
| FC-079 | 令牌用量查询（`/api/usage/token`、UsedQuota） | D11 | controller/usedata.go | repo证实 |

## D12. 模型 / 供应商元数据与广场（Models / Vendors / Pricing）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-080 | 模型元数据 CRUD / 搜索（ModelMeta） | D12 | controller/model.go、model/model_meta.go | repo证实 |
| FC-081 | 供应商（vendor）元数据 CRUD（VendorMeta） | D12 | controller/vendor_meta.go、model/vendor_meta.go | repo证实 |
| FC-082 | 上游模型同步预览 / 执行（model_sync） | D12 | controller/model_sync.go | repo证实 |
| FC-083 | 缺失模型检测（missing_models） | D12 | controller/missing_models.go、model/missing_models.go | repo证实 |
| FC-084 | 模型排行榜（rankings，公开） | D12 | controller/rankings.go、model/usedata_rankings.go | repo证实 |
| FC-085 | 模型广场 / 用户可见模型（DashboardListModels / GetUserModels） | D12 | controller/model.go | repo证实 |

## D13. Relay 网关（多协议 API 中转）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-086 | OpenAI 兼容 chat/completions + completions 中转 | D13 | relay/、relay_mode.go | repo证实 |
| FC-087 | embeddings 中转 | D13 | relay_mode.go | repo证实 |
| FC-088 | images（generations / edits / variations）中转 | D13 | relay_mode.go | repo证实 |
| FC-089 | audio（speech / transcription / translation）中转 | D13 | relay_mode.go | repo证实 |
| FC-090 | moderations 内容审核中转 | D13 | relay_mode.go | repo证实 |
| FC-091 | rerank 重排序中转 | D13 | relay_mode.go | repo证实 |
| FC-092 | edits 中转 | D13 | relay_mode.go | repo证实 |
| FC-093 | responses（+compact）中转 | D13 | relay_mode.go | repo证实 |
| FC-094 | realtime（WebSocket 实时）中转 | D13 | relay_mode.go | repo证实 |
| FC-095 | Gemini 原生协议中转（`/v1beta/models/*`） | D13 | relay/channel/gemini | repo证实 |
| FC-096 | Claude 原生协议中转（`/v1/messages`） | D13 | relay/channel/claude | repo证实 |
| FC-097 | 各厂商原生协议适配（Tencent/Xunfei/Zhipu 等转换） | D13 | relay/channel/* | repo证实 |

## D14. 部署管理（Deployments / io.net）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-098 | 模型部署集群管理（settings / test-connection） | D14 | controller/deployment.go、pkg/ionet | repo证实 |
| FC-099 | 硬件类型 / 地域 / 可用副本查询 | D14 | deploymentsRoute | repo证实 |
| FC-100 | 部署价格预估 / 容器日志 / 续期 | D14 | deploymentsRoute | repo证实 |

## D15. 日志 / 用量数据 / 审计（Logs / UseData / Audit）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-101 | 全量 / 自助日志查询 + 搜索（管理端 + 用户） | D15 | controller/log.go、model/log.go | repo证实 |
| FC-102 | 日志统计 / 按 key 查日志 | D15 | controller/log.go | repo证实 |
| FC-103 | 配额按日数据（GetAllQuotaDates / ByUser / self，QuotaData） | D15 | controller/usedata.go、model/usedata.go | repo证实 |
| FC-104 | 用量排行（usedata_rankings） | D15 | controller/rankings.go | repo证实 |
| FC-105 | 审计日志（audit） | D15 | controller/audit.go | repo证实 |

## D16. 运营 / 系统设置与运维（Settings / Ops）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-106 | 系统初始化向导（setup） | D16 | controller/setup.go、model/setup.go | repo证实 |
| FC-107 | 全站选项配置（root，option CRUD） | D16 | controller/option.go、model/option.go | repo证实 |
| FC-108 | 性能统计 / 磁盘缓存清理 / 强制 GC / 日志文件管理 | D16 | controller/performance.go | repo证实 |
| FC-109 | 性能指标 perf-metrics（公开 summary） | D16 | controller/perf_metrics.go、model/perf_metric.go | repo证实 |
| FC-110 | Uptime-Kuma 状态接入 | D16 | controller/uptime_kuma.go | repo证实 |
| FC-111 | 公告 / 用户协议 / 隐私政策 / 关于 / 首页内容管理 | D16 | controller/misc.go | repo证实 |
| FC-112 | 支付合规确认（payment_compliance） | D16 | controller/payment_compliance.go | repo证实 |
| FC-113 | 控制台设置迁移（console_migrate） | D16 | controller/console_migrate.go | repo证实 |
| FC-114 | 限流配置（GlobalAPI / ModelRequest / Critical / EmailVerification / Search RateLimit） | D16 | setting/** | repo证实 |
| FC-115 | 敏感词 / 内容过滤配置 | D16 | setting/** | repo证实 |
| FC-116 | 用户可用分组配置（auto_group / 可用分组） | D16 | setting/auto_group.go | repo证实 |
| FC-117 | 主题切换（default / classic） | D16 | setting/** | repo证实 |
| FC-118 | 多语言切换（后端 en/zh，前端 en/zh/fr/ru/ja/vi） | D16 | i18n/ | repo证实 |
| FC-119 | 额度预警通知（email / webhook / bark 三渠道） | D16 | NotificationSettings.jsx | repo证实 |

## D17. Playground / 在线试用

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-120 | 站内对话试用 Playground（模型选择 + 对话） | D17 | controller/playground.go、`/pg/chat/completions` | repo证实 |

## 公开站点候选（routifyapi.com，视觉/营销层）

| FC | 候选功能 | 域 | 来源 | 状态 |
|---|---|---|---|---|
| FC-121 | 营销首页（定位文案 / 三大卖点 / 厂商展示 / 指标条） | Web | WEBSITE-COVERAGE §3 | 站点证实 |
| FC-122 | 首页对话输入框 Playground 入口（「问点什么…」） | Web | WEBSITE-COVERAGE §4 | 站点证实 |
| FC-123 | 用户协议公开页 | Web | /agreement | 站点证实 |
| FC-124 | 隐私政策公开页 | Web | /privacy | 站点证实 |
| FC-125 | 主题切换 / 语言切换公开控件 | Web | WEBSITE-COVERAGE §4 | 站点证实 |
| FC-126 | 控制台 / 模型广场 / API Keys 主入口（动态域，未访问） | Web | app.routifyapi.com | GAP（SG-001） |
| FC-127 | Codex 渠道上游用量查询（GET /channel/:id/codex/usage） | Console/Admin | controller/codex_usage.go | repo 证实（S1 评审补回） |
| FC-128 | 生成视频内容网关代理下发（GET /videos/:task_id/content，含 gemini 分支） | Relay | controller/video_proxy.go | repo 证实（S1 评审补回） |

---

## 自检：5 旗舰能力 FC 落点

| 旗舰能力 | FC 编号 |
|---|---|
| #1 签到 / 每日奖励 | FC-019, FC-020, FC-021, FC-022, FC-023 |
| #2 邀请返利 / 分销 | FC-024, FC-025, FC-026, FC-027, FC-028, FC-029 |
| #3 Telegram 登录 | FC-018 |
| #4 异步任务中心 | FC-030 ~ FC-038 |
| #5 预填分组 | FC-039, FC-040, FC-041 |

> FC 总数：128（FC-001 ~ FC-128）。覆盖 D1~D17 全部 17 个能力域 + 公开站点候选 + S1 评审补回 codex_usage / video_proxy 两个端点。
