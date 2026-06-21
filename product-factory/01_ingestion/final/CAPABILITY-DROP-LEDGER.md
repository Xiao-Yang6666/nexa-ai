# CAPABILITY-DROP-LEDGER — repo_domain_blind 33 域逐个裁决

> 角色：机械门 `repo_capability_coverage.py` 判出 33 个 repo 能力域文件在 FEATURE-CANDIDATES（function ledger）里无 footprint，判 REWORK_REQUIRED。
> 本台账对 33 个域**逐个人工裁决**，使 drop 可审计、可机械核对。
> 裁决类型：`drop`（测试/内部基础设施，非独立产品功能面）｜`已覆盖`（被某 FC / INTEGRATION-LIST 覆盖，只是文件名没对上）｜`补candidate`（真产品能力被漏，应补 FC）。
> 证据均一手来自 `repo/new-api/`。脚本命令见 HANDOFF / 任务说明。

## 裁决总表（33 行）

| # | 域名 | 证据路径 | 裁决 | 理由 |
|---|---|---|---|---|
| 1 | channel_cache | model/channel_cache.go:21 `InitChannelCache()`（group2model2channels 内存缓存） | drop | 内部基础设施：渠道路由表的内存缓存实现，无独立产品功能面（路由能力已由 FC-053 覆盖） |
| 2 | channel_satisfy | model/channel_satisfy.go:8 `IsChannelEnabledForGroupModel()` | drop | 内部基础设施：缓存命中判定辅助函数，服务于 FC-053 优先级+权重路由，无独立功能面 |
| 3 | channel_test_internal_test | controller/channel_test_internal_test.go（package controller，3 个 func Test） | drop | 测试代码非产品能力（*_test.go 单元测试） |
| 4 | channel_upstream_update_test | controller/channel_upstream_update_test.go（11 个 func Test） | drop | 测试代码非产品能力；被测对象 channel_upstream_update.go 已由 FC-051 覆盖 |
| 5 | codex_usage | controller/codex_usage.go:20 `GetCodexChannelUsage()`；router/api-router.go:253 `GET /channel/:id/codex/usage` | 补candidate | 真产品能力：查询 Codex 渠道上游用量/配额的管理端点，是独立功能面。FC-067 只提 codex 亲和缓存 header 模板，未含此用量查询端点（详见 BLOCKER 节，建议补 FC-127） |
| 6 | db_time | model/db_time.go:7 `GetDBTimestamp()` | drop | 内部基础设施：跨 DB（PG/SQLite/MySQL）取数据库时间戳工具，无产品功能面 |
| 7 | errors | model/errors.go:4 `var (ErrDatabase…)` 等错误常量 | drop | 内部基础设施：错误哨兵变量定义，无产品功能面 |
| 8 | model_extra | model/model_extra.go:3 `GetModelEnableGroups()`/`GetModelQuotaTypes()` | drop | 内部基础设施：模型→可用分组/计费类型的缓存查询辅助，服务于 FC-055/FC-085，无独立功能面 |
| 9 | model_list_test | controller/model_list_test.go（2 个 func Test） | drop | 测试代码非产品能力 |
| 10 | model_owned_by_test | controller/model_owned_by_test.go（5 个 func Test） | drop | 测试代码非产品能力 |
| 11 | model_owner_test | model/model_owner_test.go（1 个 func Test） | drop | 测试代码非产品能力 |
| 12 | payment_method_guard_test | model/payment_method_guard_test.go（4 个 func Test） | drop | 测试代码非产品能力（支付方式合规守卫的测试，被测能力归 FC-112 支付合规） |
| 13 | payment_webhook_availability | controller/payment_webhook_availability.go:9 `isPaymentComplianceConfirmed()`/`isStripeTopUpEnabled()` 等可用性探测 | 已覆盖 | 已覆盖于 INTEGRATION-LIST §6「支付通用 webhook 可用性」+ FC-112 支付合规；为支付能力的内部可用性自检，非独立产品功能面 |
| 14 | payment_webhook_availability_test | controller/payment_webhook_availability_test.go（5 个 func Test） | drop | 测试代码非产品能力 |
| 15 | pricing_default | model/pricing_default.go:8 `defaultVendorRules`（模型名→厂商默认映射规则） | drop | 内部基础设施：模型→vendor 的默认推断规则表，服务于 FC-081 VendorMeta，无独立功能面 |
| 16 | pricing_refresh | model/pricing_refresh.go:6 `RefreshPricing()`（强制刷新定价缓存） | drop | 内部基础设施：定价缓存强制刷新工具，服务于 FC-060 倍率配置同步，无独立功能面 |
| 17 | return_path | controller/return_path.go:9 `paymentReturnPath()`（支付回跳路径拼接） | 已覆盖 | 已覆盖于支付能力（FC-061 充值 / FC-063 订阅）；为支付回跳 URL 拼接内部工具，无独立功能面 |
| 18 | subscription_payment_creem | controller/subscription_payment_creem.go:23 `SubscriptionRequestCreemPay()` | 已覆盖 | 已覆盖于 FC-063 订阅计划与订单 + INTEGRATION-LIST §4 Creem；为订阅支付的 provider 适配实现 |
| 19 | subscription_payment_epay | controller/subscription_payment_epay.go:24/118/173 `SubscriptionRequestEpay/Notify/Return` | 已覆盖 | 已覆盖于 FC-063 + INTEGRATION-LIST §4 ePay（易支付，支付宝/微信扫码） |
| 20 | subscription_payment_stripe | controller/subscription_payment_stripe.go:23 `SubscriptionRequestStripePay()` | 已覆盖 | 已覆盖于 FC-063 + INTEGRATION-LIST §4 Stripe |
| 21 | subscription_payment_waffo_pancake | controller/subscription_payment_waffo_pancake.go:23 `SubscriptionRequestWaffoPancakePay()` | 已覆盖 | 已覆盖于 FC-063 + INTEGRATION-LIST §4 Waffo Pancake |
| 22 | swag_video | controller/swag_video.go:23 `VideoGenerations()`/`KlingText2VideoGenerations()` 等 | 已覆盖 | 已覆盖于 FC-036 视频生成任务（Sora/Kling/Vidu/…）；本文件为 swagger 注解 + 视频生成端点 wrapper，功能面同 FC-036 |
| 23 | task_cas_test | model/task_cas_test.go（10 个 func Test） | drop | 测试代码非产品能力（任务 CAS 乐观锁测试，被测能力归 FC-030 任务状态机） |
| 24 | token_cache | model/token_cache.go:11 `cacheSetToken()`/`cacheDeleteToken()`（Redis 令牌缓存） | drop | 内部基础设施：令牌的 Redis 缓存读写实现，服务于 FC-073~079 令牌能力，无独立功能面 |
| 25 | token_test | controller/token_test.go（package controller，import testing） | drop | 测试代码非产品能力 |
| 26 | topup_creem | controller/topup_creem.go:144 `RequestCreemPay()`/:229 `CreemWebhook()` | 已覆盖 | 已覆盖于 FC-061 充值（多支付渠道）+ INTEGRATION-LIST §4/§6 Creem（含 webhook） |
| 27 | topup_stripe | controller/topup_stripe.go:137 `RequestStripePay()`/:147 `StripeWebhook()` | 已覆盖 | 已覆盖于 FC-061 + INTEGRATION-LIST §4/§6 Stripe（含 webhook） |
| 28 | topup_waffo | controller/topup_waffo.go:132 `RequestWaffoPay()`/:319 `WaffoWebhook()` | 已覆盖 | 已覆盖于 FC-061 + INTEGRATION-LIST §4 Waffo |
| 29 | topup_waffo_pancake | controller/topup_waffo_pancake.go:348 `RequestWaffoPancakePay()`/:434 `WaffoPancakeWebhook()` | 已覆盖 | 已覆盖于 FC-061 + INTEGRATION-LIST §4 Waffo Pancake |
| 30 | topup_waffo_pancake_test | controller/topup_waffo_pancake_test.go（2 个 func Test） | drop | 测试代码非产品能力 |
| 31 | user_cache | model/user_cache.go:16 `UserBase` + Redis 用户缓存读写 | drop | 内部基础设施：用户基础信息的 Redis 缓存实现，服务于 FC-004~007 用户能力，无独立功能面 |
| 32 | video_proxy | controller/video_proxy.go:33 `VideoProxy()`；router/video-router.go:16 `GET /videos/:task_id/content` | 补candidate | 真产品能力：经网关代理/下发生成视频内容的端点，是独立功能面。FC-036/FC-038 覆盖视频「生成」与「产物展示」，未含「内容代理/下发」服务端点（详见 BLOCKER 节，建议补 FC-128） |
| 33 | video_proxy_gemini | controller/video_proxy_gemini.go:15 `getGeminiVideoURL()` | 已覆盖 | 已覆盖于 video_proxy（同 #32 的 Gemini 分支实现）+ FC-036；为 video_proxy 的内部 provider 分支，无独立功能面，随 #32 补 FC-128 一并涵盖 |

## 裁决汇总

| 裁决 | 数量 | 域 |
|---|---|---|
| **drop**（测试代码） | 11 | channel_test_internal_test, channel_upstream_update_test, model_list_test, model_owned_by_test, model_owner_test, payment_method_guard_test, payment_webhook_availability_test, task_cas_test, token_test, topup_waffo_pancake_test, （共 10 个 *_test.go）+ — 见下 |
| **drop**（内部基础设施） | 9 | channel_cache, channel_satisfy, db_time, errors, model_extra, pricing_default, pricing_refresh, token_cache, user_cache |
| **已覆盖**（FC/INTEGRATION 已含，文件名没对上） | 11 | payment_webhook_availability, return_path, subscription_payment_creem, subscription_payment_epay, subscription_payment_stripe, subscription_payment_waffo_pancake, swag_video, topup_creem, topup_stripe, topup_waffo, topup_waffo_pancake, video_proxy_gemini |
| **补candidate**（真产品能力被漏，BLOCKER） | 2 | codex_usage, video_proxy |

> 精确计数：drop=20（10 测试 + 9 基础设施 + 1 已在「已覆盖」复核为基础设施的项已并入）；实际逐行：
> - drop（测试 *_test.go）：#3, #4, #9, #10, #11, #12, #14, #23, #25, #30 = **10**
> - drop（内部基础设施）：#1, #2, #6, #7, #8, #15, #16, #24, #31 = **9**
> - 已覆盖：#13, #17, #18, #19, #20, #21, #22, #26, #27, #28, #29, #33 = **12**
> - 补candidate（BLOCKER）：#5 codex_usage, #32 video_proxy = **2**
> 合计 10 + 9 + 12 + 2 = **33** ✅

## BLOCKER — 真漏的产品能力（非测试/非基础设施）

发现 **2 个**真产品能力面在 FEATURE-CANDIDATES 中无 FC 落点，构成 BLOCKER（producer 应补 FC）：

1. **codex_usage**（建议补 **FC-127**，归 D7 渠道管理）
   - 能力：`GET /channel/:id/codex/usage` — 查询 Codex 渠道上游用量/配额（含 MultiKey 聚合）。
   - 证据：controller/codex_usage.go:20 `GetCodexChannelUsage`；router/api-router.go:253。
   - 现状：FC-067 仅覆盖 codex 亲和缓存 header 模板，INTEGRATION-LIST §1.5 仅备注「含 codex_usage」，但 FC 候选池无此「上游用量查询」功能面。属管理端 niche 能力，但为独立功能面，应显式补 FC 供 S2 裁决。

2. **video_proxy**（建议补 **FC-128**，归 D5 异步任务中心 或 D13 Relay）
   - 能力：`GET /videos/:task_id/content` — 经网关代理/下发生成视频内容（含 Gemini provider 分支 video_proxy_gemini）。
   - 证据：controller/video_proxy.go:33 `VideoProxy`；controller/video_proxy_gemini.go:15；router/video-router.go:16。
   - 现状：FC-036 覆盖视频「生成」、FC-038 覆盖「产物展示（VideoUrl）」，但「视频内容经网关代理下发」是独立的服务端点（关系到鉴权、流量、跨域、API key 注入），无 FC 落点，应显式补 FC 供 S2 裁决。

> 两者均非旗舰、非高频，但属真实独立功能面，按机械门要求不可静默 drop，须由 producer 补进 FEATURE-CANDIDATES（本 reviewer 不修改 FC 产物）。
