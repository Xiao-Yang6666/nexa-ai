# REPO-INSPECTION — new-api 产品能力域全景

> 来源：开源仓库 `repo/new-api/`（QuantumNous/new-api，Go + Gin + GORM + React）。本文件以**产品能力域**视角组织，不是文件级 inventory。每个域标注源码 footprint（控制器/模型/路由/设置）作为可审计证据。
> 权威：P0 功能/领域逻辑/数据模型/后台能力是否存在。**不**作为最终视觉/品牌权威。
> 本轮重跑硬要求：穷尽 repo 能力面。`controller/*.go`=69，`model/*.go`=39，`relay/channel/*` 顶层 adapter=37，`setting/**`=49 个 .go，`constant/channel.go` 渠道类型常量到 57（Codex=57，超过 AGENTS.md 自述「40+」）。

## 技术栈与架构（证据：AGENTS.md / main.go / go.mod）
- 后端：Go 1.22+、Gin、GORM v2；数据库三选一全兼容（SQLite / MySQL≥5.7.8 / PostgreSQL≥9.6）；缓存 Redis + 内存。
- 前端：React 19 + TS + Rsbuild + Base UI + Tailwind（`web/default/`）；另有 classic 主题（React18 + Vite + Semi）。
- 分层：Router → Controller → Service → Model；`relay/channel/<provider>/` 为各上游 provider adapter。
- i18n：后端 go-i18n（en/zh）；前端 i18next（en/zh/fr/ru/ja/vi）。
- 鉴权：JWT、WebAuthn/Passkey、OAuth（GitHub/Discord/OIDC/LinuxDO）、WeChat、Telegram、2FA。

---

## 能力域全景

### D1. 账号与身份（Account & Identity）
- footprint：`controller/user.go`、`model/user.go`(User struct 1083 行)、`controller/oauth.go`、`controller/custom_oauth.go`、`controller/wechat.go`、`controller/telegram.go`、`controller/passkey.go`、`controller/twofa.go`、`oauth/`、`setting/system_setting/{oidc,discord,passkey}.go`、`model/user_oauth_binding.go`、`model/custom_oauth_provider.go`、`model/passkey.go`、`model/twofa.go`。
- 能力：注册/登录/登出、邮箱验证、找回密码、用户 CRUD（管理端 ManageUser/CreateUser/SearchUsers）、角色（admin/common/root）、用户分组、用户级 setting/备注。
- 多渠道身份绑定：GitHubId / DiscordId / OidcId / WeChatId / **TelegramId** / LinuxDOId / StripeCustomer。
- OAuth：标准 provider 统一路由 `/api/oauth/:provider`（GitHub/Discord/OIDC/LinuxDO），非标准 WeChat / **Telegram** 单独路由；自定义 OAuth provider 管理（root，discovery + CRUD）。
- 安全：Passkey（注册/登录/校验/删除，含管理端重置）、2FA（setup/enable/disable/备份码/管理端统计与禁用）、通用二次验证 `/api/verify`（SecureVerificationRequired 中间件保护取 channel key 等敏感动作）。

### D2. 签到 / 每日奖励（Check-in）★旗舰能力#1
- footprint：`controller/checkin.go`（GetCheckinStatus / DoCheckin）、`model/checkin.go`（Checkin 表，唯一约束 user_id+checkin_date 防并发重复签到；UserCheckin 随机额度奖励；事务/SQLite 双路径）、`setting/operation_setting/checkin_setting.go`（Enabled/MinQuota/MaxQuota）。
- 路由：`GET /api/user/checkin`（状态+本月记录+累计统计）、`POST /api/user/checkin`（执行，含 TurnstileCheck）。
- 业务：随机额度奖励（MinQuota..MaxQuota），签到成功写 LogTypeSystem 日志，异步刷新额度缓存。

### D3. 邀请返利 / 分销（Affiliate）★旗舰能力#2
- footprint：`model/user.go`（AffCode 唯一索引、AffCount、AffQuota「邀请剩余额度」、AffHistoryQuota「邀请历史额度」、InviterId）、`controller/user.go`（GetAffCode 生成/返回邀请码、TransferAffQuota 邀请额度转可用额度、注册时按 affCode 解析 inviterId 并记 Insert(inviterId)）、`common.QuotaForInviter`/`QuotaForNewUser`。
- 路由：`GET /api/user/self/aff`（取邀请码）、`POST /api/user/self/aff_transfer`（邀请额度划转）。
- 业务：新用户用邀请码注册 → 邀请人 AffCount++ 且 AffQuota/AffHistoryQuota += QuotaForInviter；邀请额度可转为可用额度。

### D4. Telegram 登录 / Bot 鉴权（Telegram）★旗舰能力#3
- footprint：`controller/telegram.go`（TelegramLogin / TelegramBind / checkTelegramAuthorization——基于 BotToken 的 HMAC-SHA256 校验 Telegram Login Widget 回调参数）、`model/user.go`（TelegramId 字段、FillUserByTelegramId、IsTelegramIdAlreadyTaken）、`common.TelegramOAuthEnabled` / `common.TelegramBotToken`。
- 路由：`GET /api/oauth/telegram/login`、`GET /api/oauth/telegram/bind`。
- 业务：Telegram 登录免密注册/登录；已登录用户绑定 TelegramId；绑定后重定向 `/console/personal`。

### D5. 异步任务中心（Async Task Center：MJ / Suno / 视频）★旗舰能力#4
- footprint：`model/task.go`（Task 通用任务表：TaskID/Platform/Status/Progress/SubmitTime/StartTime/FinishTime/FailReason/Quota/PrivateData；TaskStatus 状态机 NOT_START→SUBMITTED→QUEUED→IN_PROGRESS→SUCCESS/FAILURE/UNKNOWN）、`model/midjourney.go`（Midjourney 任务表：Action/MjId/Prompt/Status/Progress/Buttons/ImageUrl/VideoUrl，GetAllUserTask/GetAllTasks 列表+分页+过滤）、`controller/task.go`（GetUserTask/GetAllTask）、`controller/task_video.go`、`controller/midjourney.go`（GetUserMidjourney/GetAllMidjourney）、`relay/channel/task/{suno,kling,sora,vidu,hailuo,jimeng,doubao,ali,vertex,gemini}/`、`constant/task.go`（TaskPlatform suno/mj、SunoAction MUSIC/LYRICS）、`relay/constant/relay_mode.go`（MidjourneyImagine/Describe/Blend/Change/Action/Modal/Shorten/Video/Edits、SunoFetch/SunoSubmit/SunoFetchByID 等）。
- 路由：管理/用户任务列表 `GET /api/task`、`GET /api/task/self`、`GET /api/mj`、`GET /api/mj/self`；relay 提交侧 `/mj/submit/*`（imagine/change/blend/describe/modal/shorten/video/edits…）、`/mj/task/:id/fetch`、`/mj/task/list-by-condition`、`/suno/submit/:action`、`/suno/fetch`、`/suno/fetch/:id`。
- 业务：长耗时生成任务（绘图/音乐/视频）提交→轮询/回调→进度追踪→列表→重试/计费修正（Task.Group「修正计费用」）。

### D6. 预填分组（Prefill Group）★旗舰能力#5
- footprint：`model/prefill_group.go`（PrefillGroup：Name 唯一、Type=model/tag/endpoint、Items JSON 数组、软删除）、`controller/prefill_group.go`（GetPrefillGroups/CreatePrefillGroup/UpdatePrefillGroup/DeletePrefillGroup）。
- 路由：`/api/prefill_group`（AdminAuth，GET/POST/PUT/DELETE）。
- 业务：可复用的「组」预设（模型组/标签组/端点组），用于前端下拉快速填充渠道/令牌配置，减少重复输入。

### D7. 渠道管理与上游路由（Channel & Routing）
- footprint：`controller/channel.go`、`channel-test.go`、`channel-billing.go`、`channel_upstream_update.go`、`channel_affinity_cache.go`、`model/channel.go`（Channel：Type/Key/Weight/Priority/Group/Models/ModelMapping/StatusCodeMapping/AutoBan/Tag/Setting/ParamOverride/HeaderOverride/多 Key 模式 IsMultiKey/MultiKeyMode/MultiKeyStatusList/轮询索引）、`model/ability.go`、`model/channel_cache.go`、`model/channel_satisfy.go`、`service/channel_select.go`。
- 能力：渠道 CRUD/搜索/批量、测试（单/全）、余额更新、按 tag 启禁/编辑、复制渠道、多 Key 管理、上游模型探测与同步（fetch_models/upstream_updates detect&apply）、Ollama 模型 pull/delete/version。
- 路由算法：优先级（Priority）+ 权重（Weight）随机选择满足渠道（GetRandomSatisfiedChannel）；自动禁用（AutoBan）+ 状态码映射；多 Key 轮询；亲和缓存（见 D9）；**跨分组重试**（见 D10）。

### D8. 计费与额度（Billing & Quota）
- footprint：`controller/billing.go`、`controller/pricing.go`、`controller/ratio_config.go`、`controller/ratio_sync.go`、`controller/subscription*.go`、`model/pricing*.go`、`model/subscription.go`、`model/topup.go`、`model/redemption.go`、`setting/ratio_setting/{model_ratio,group_ratio,cache_ratio,expose_ratio,exposed_cache,compact_suffix}.go`、`setting/billing_setting/tiered_billing.go`、`pkg/billingexpr/*`（含 expr.md）、`service/quota.go`、`service/tiered_settle.go`、`relay/helper/price.go`。
- 计费模型：
  - **倍率计费**：model_ratio（模型倍率）× group_ratio（分组倍率）× completion_ratio（补全倍率）；缓存倍率 cache_ratio。
  - **表达式/阶梯计费**（pkg/billingexpr）：单条表达式定义全部计价逻辑，变量 p/c/cr/cc/cc1h/img/ai/ao/len，函数 tier/param/header/has/hour 等；按 len 判档；`p`/`c` 自动排除已单独计价子类；请求规则 `|||when(...)*N`；版本化 v1:。
  - **预扣额度**（pre-consume）：请求前按估算 token 冻结额度（BillingSnapshot），上游返回后按真实 token 结算（TryTieredSettle），多退少不补。
- 充值/兑换/订阅：充值（多支付渠道见 INTEGRATION-LIST）；兑换码 Redemption（Quota/批量 Count/过期）；**订阅计费**（SubscriptionPlan/SubscriptionOrder/UserSubscription active|expired|cancelled、SubscriptionPreConsumeRecord consumed|refunded——订阅维度也有预扣/退款）。

### D9. 亲和缓存（Channel Affinity Cache）
- footprint：`setting/operation_setting/channel_affinity_setting.go`、`controller/channel_affinity_cache.go`、`model/channel_satisfy.go`、`logRoute /channel_affinity_usage_cache`、`optionRoute /channel_affinity_cache`。
- 业务：按规则（model_regex/path_regex/key_sources：gjson/header/context）把同一会话键（如 Codex `prompt_cache_key`、Claude `metadata.user_id`）粘到同一渠道，命中后续请求复用缓存；SwitchOnSuccess、TTL、MaxEntries、SkipRetryOnFailure；内置 codex/claude CLI 透传 header 模板。

### D10. 跨分组重试（Cross-Group Retry）
- footprint：`service/channel_select.go`（CacheGetRandomSatisfiedChannel：auto 分组逐组耗尽优先级后切下一组；ContextKeyAutoGroupIndex/AutoGroupRetryIndex 跟踪）、`setting/auto_group.go`、`model/token.go`（CrossGroupRetry 字段「跨分组重试，仅 auto 分组有效」）、`common.RetryTimes`。
- 业务：令牌可选 auto 分组 + 跨组重试，单组优先级用尽后自动切换下一分组重试，提升可用性。

### D11. 令牌 / API Key（Token）
- footprint：`controller/token.go`、`model/token.go`（Token：Key/RemainQuota/UnlimitedQuota/ExpiredTime(-1 永不过期)/ModelLimits(Enabled+列表)/AllowIps/Group/CrossGroupRetry/UsedQuota）、`model/token_cache.go`、`controller/usedata.go`（用量）。
- 能力：令牌 CRUD/搜索、批量删除、取/批量取明文 key（CriticalRateLimit+DisableCache）、模型白名单、IP 白名单、按令牌分组、用量查询 `/api/usage/token`。

### D12. 模型/供应商元数据与广场（Models / Vendors / Pricing 公开）
- footprint：`controller/model.go`、`model_meta.go`、`model_sync.go`、`missing_models.go`、`vendor_meta.go`、`pricing.go`、`rankings.go`、`model/model_meta.go`、`model/vendor_meta.go`、`model/missing_models.go`、`model/usedata_rankings.go`。
- 能力：模型元数据 CRUD/搜索、供应商（vendor）元数据 CRUD、上游模型同步预览/执行、缺失模型检测、公开价格页 `/api/pricing`、模型排行 `/api/rankings`、`DashboardListModels`/`GetUserModels`。

### D13. Relay 网关（多协议 API 中转）
- footprint：`controller/relay.go`、`router/relay-router.go`、`relay/`、37 个 `relay/channel/<provider>/` adapter。
- 端点类型（relay_mode.go）：chat/completions、completions、embeddings、moderations、images(generations/edits/variations)、edits、rerank、audio(speech/transcription/translation)、responses(+compact)、realtime(WebSocket)、Gemini 原生 `/v1beta/models/*`、Claude 原生 `/v1/messages`、MJ/Suno 任务端点。
- 兼容：OpenAI 协议为主，Gemini/Claude/Tencent/Xunfei/Zhipu 等原生协议各自 adapter 转换。

### D14. 部署管理（Deployments / io.net）
- footprint：`controller/deployment.go`、`pkg/ionet`、deploymentsRoute（settings/test-connection/hardware-types/locations/available-replicas/price-estimation/containers/logs/extend…）。
- 业务：模型部署集群管理（疑似对接 io.net GPU 部署）；硬件类型/地域/副本/价格预估/容器日志/续期。

### D15. 日志 / 用量数据 / 审计（Logs / UseData / Audit）
- footprint：`controller/log.go`、`usedata.go`、`audit.go`、`rankings.go`、`model/log.go`、`usedata.go`、`usedata_rankings.go`、logRoute/dataRoute。
- 能力：全量/自助日志查询+搜索、日志统计、按 key 查日志、配额按日数据（GetAllQuotaDates/ByUser/self）、审计、亲和缓存用量统计。

### D16. 运营/系统设置与运维（Settings / Ops）
- footprint：`controller/option.go`、`setup.go`、`misc.go`、`performance.go`、`perf_metrics.go`、`uptime_kuma.go`、`return_path.go`、`secure_verification.go`、`payment_compliance.go`、`console_migrate.go`、`model/option.go`、`setup.go`、`perf_metric.go`、`setting/**`。
- 能力：系统初始化 setup、全站选项（root）、性能统计/磁盘缓存清理/强制 GC/日志文件管理、性能指标 perf-metrics（公开 summary）、Uptime-Kuma 状态、公告/用户协议/隐私政策/关于/首页内容、支付合规确认、控制台设置迁移、限流配置（GlobalAPIRateLimit/ModelRequestRateLimit/CriticalRateLimit/EmailVerificationRateLimit/SearchRateLimit）、敏感词、auto_group、用户可用分组、主题。

### D17. Playground / 在线试用
- footprint：`controller/playground.go`、relay-router `/pg/chat/completions`。
- 业务：站内对话试用（对应网站首页「问点什么…」输入框 + 模型选择）。

---

## 数据对象（核心 entity，证据：model/*.go struct）
User、Token、Channel、Ability、Redemption、TopUp、SubscriptionPlan/Order/UserSubscription/PreConsumeRecord、Checkin、Task、Midjourney、PrefillGroup、ModelMeta、VendorMeta、MissingModels、Log、UseData、QuotaData、Option、PerfMetric、Passkey、TwoFA、CustomOAuthProvider、UserOAuthBinding、ChannelCache/Satisfy。

## License / 归属风险（仅记录，不下法律结论）
- 仓库含 LICENSE / NOTICE / THIRD-PARTY-LICENSES.md；AGENTS.md Rule5 声明 new-api/QuantumNous 品牌为受保护标识。
- 本产品为「参考逻辑、自建产品」，**S1 不做商用法律结论**，license 合规待后续阶段确认（红线见 PROJECT.md）。
