# DATA-MODEL — 数据对象字段级契约（S4 PRD 数据对象段唯一权威）

> 项目：基于 new-api 的 AI API 网关 SaaS（RoutifyAPI）。
> 本文件从 `repo/new-api/model/*.go` 的 GORM struct 逐字段抽取，给出字段名 / 类型 / 约束 / 状态枚举。
> PRD「数据对象」段**只能从本文件复制字段子集**，禁止写「读取、变更、状态流转或审计」这类零信息占位句。
> 字段名以 Go struct 字段 + json tag 双列呈现：`Go字段(json_tag): 类型 [约束]`。
> 来源标注 `model/<file>.go`。

---

## 1. User（用户）— `model/user.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| Username (username) | string | `unique;index`, `validate:max=20`, 常量 `UserNameMaxLength=20` |
| Password (password) | string | `not null`, `validate:min=8,max=20`, 存哈希；`-:all` 字段 `OriginalPassword` 仅用于改密校验不落库 |
| DisplayName (display_name) | string | `index`, `validate:max=20` |
| Role (role) | int | `default:1`；枚举见下「Role 枚举」 |
| Status (status) | int | `default:1`；枚举见下「Status 枚举」 |
| Email (email) | string | `index`, `validate:max=50` |
| GitHubId (github_id) | string | `index`，第三方绑定 |
| DiscordId (discord_id) | string | `index` |
| OidcId (oidc_id) | string | `index` |
| WeChatId (wechat_id) | string | `index` |
| TelegramId (telegram_id) | string | `index`，Telegram 绑定唯一标识 |
| LinuxDOId (linux_do_id) | string | `index`，LinuxDO 绑定 |
| AccessToken (-) | *string | `char(32);uniqueIndex`，系统管理 token，`json:"-"` 不下发 |
| Quota (quota) | int | `default:0`，可用额度（quota 单位） |
| UsedQuota (used_quota) | int | `default:0`，已用额度 |
| RequestCount (request_count) | int | `default:0`，请求次数 |
| Group (group) | string | `varchar(64);default:'default'`，用户分组 |
| AffCode (aff_code) | string | `varchar(32);uniqueIndex`，本人 4 位邀请码 |
| AffCount (aff_count) | int | `default:0`，成功邀请人数 |
| AffQuota (aff_quota) | int | `default:0`，邀请剩余可划转额度 |
| AffHistoryQuota (aff_history_quota → column:aff_history) | int | `default:0`，邀请累计历史额度 |
| InviterId (inviter_id) | int | `index`，邀请人 Id，0=无邀请人 |
| Setting (setting) | string | `text`，序列化 dto.UserSetting（边栏等） |
| Remark (remark) | string | `varchar(255)`, `validate:max=255` |
| StripeCustomer (stripe_customer) | string | `varchar(64);index` |
| CreatedAt (created_at) | int64 | `autoCreateTime` |
| LastLoginAt (last_login_at) | int64 | `default:0` |
| DeletedAt | gorm.DeletedAt | `index`，软删除 |
| VerificationCode (verification_code) | string | `-:all`，仅用于邮箱验证不落库 |

**Role 枚举**（int）：`RoleCommonUser=common`（普通，默认）/ `admin`（管理员）/ `root`（超管）。越权护栏：不可操作 `目标角色 >= 操作者角色`。
**Status 枚举**（int）：`UserStatusEnabled=1`（启用，可登录）/ 禁用（≠1，被封禁不可登录）。
**邀请相关常量**：`common.QuotaForNewUser`（新用户初始额度）、`common.QuotaForInviter`（邀请人每次返利额度）、`common.QuotaPerUnit`（最小额度单位，划转下限）。

---

## 2. Token（API 令牌）— `model/token.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| UserId (user_id) | int | `index`，归属用户（越权过滤键） |
| Key (key) | string | `varchar(128);uniqueIndex`，`sk-` 前缀明文 key，列表经 `MaskTokenKey` 脱敏 |
| Status (status) | int | `default:1`；枚举见下「Token Status」 |
| Name (name) | string | `index`，名称，长度上限 50 字符（超出 `MsgTokenNameTooLong`） |
| CreatedTime (created_time) | int64 | `bigint` |
| AccessedTime (accessed_time) | int64 | `bigint`，最近调用时间 |
| ExpiredTime (expired_time) | int64 | `bigint;default:-1`，`-1`=永不过期 |
| RemainQuota (remain_quota) | int | `default:0`，剩余额度；下限 0、上限 `10亿*QuotaPerUnit`（`maxQuotaValue`） |
| UnlimitedQuota (unlimited_quota) | bool | true=无限额度，跳过额度区间校验 |
| ModelLimitsEnabled (model_limits_enabled) | bool | true=启用模型白名单 |
| ModelLimits (model_limits) | string | `text`，JSON，`GetModelLimitsMap()` 解析为允许模型布尔表 |
| AllowIps (allow_ips) | *string | `default:''`，按 `\n` 切分（`GetIpLimits()`），空/nil=不限 IP |
| UsedQuota (used_quota) | int | `default:0`，已用额度 |
| Group (group) | string | `default:''`，调用分组 |
| CrossGroupRetry (cross_group_retry) | bool | 跨分组重试，仅 `Group=auto` 生效 |
| DeletedAt | gorm.DeletedAt | `index`，软删除 |

**Token Status 枚举**（int）：`1`=启用（可调用）/ 禁用（status_only 切换，调用被拒）。派生态：已过期（`ExpiredTime<=now 且 ≠-1`）、额度耗尽（`RemainQuota` 用尽且非无限）、已删除（`DeletedAt` 非空）。
**脱敏规则 `MaskTokenKey`**：`len≤4` 全 `*`；`len≤8` 保留首尾 2 位（`xx****xx`）；否则 `前4位 + ********** + 后4位`。
**搜索约束**：`searchHardLimit=100`（limit 超 100 截断），`sanitizeLikePattern` 用 `!` 作 ESCAPE，拒绝连续 `%%`、`%`>2 个、含 `%` 时去 % 后关键词 <2 字符。
**用量返回（GetTokenStatus，OpenAI 兼容）**：`object=credit_summary`、`total_granted=RemainQuota+UsedQuota`、`total_used=UsedQuota`、`expires_at`（`ExpiredTime=-1` 时归零）。

---

## 3. Channel（上游渠道）— `model/channel.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| Type (type) | int | `default:0`，渠道类型（provider 编号） |
| Key (key) | string | `not null`，上游密钥（多 key 模式见 ChannelInfo） |
| Status (status) | int | `default:1`，1=启用 / 其他=禁用 |
| Name (name) | string | `index` |
| Weight (weight) | *uint | `default:0`，权重 |
| BaseURL (base_url) | *string | `default:''`，上游基址 |
| Models (models) | string | 支持模型列表 |
| Group (group) | string | `varchar(64);default:'default'` |
| Priority (priority) | *int64 | `default:0`，优先级 |
| AutoBan (auto_ban) | *int | `default:1`，异常自动禁用开关 |
| Balance (balance) | float64 | 渠道余额(USD) |
| UsedQuota (used_quota) | int64 | `default:0` |
| ResponseTime (response_time) | int | 毫秒，测速结果 |
| TestTime (test_time) | int64 | `bigint`，最近测试时间 |
| ModelMapping (model_mapping) | *string | `text` |
| StatusCodeMapping (status_code_mapping) | *string | `varchar(1024);default:''` |
| Tag (tag) | *string | `index` |
| Setting (setting) | *string | `text`，渠道额外设置 |
| ChannelInfo (channel_info) | ChannelInfo | `json`，多 key 模式状态（见下） |
| CreatedTime (created_time) | int64 | `bigint` |

**ChannelInfo 子结构**：`IsMultiKey(is_multi_key) bool`、`MultiKeySize(multi_key_size) int`、`MultiKeyStatusList map[int]int`（key index→status）、`MultiKeyDisabledReason map[int]string`、`MultiKeyPollingIndex int`、`MultiKeyMode constant.MultiKeyMode`。

---

## 4. Ability（分组×模型→渠道路由能力）— `model/ability.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Group (group) | string | `varchar(64);primaryKey`（复合主键 1） |
| Model (model) | string | `varchar(255);primaryKey`（复合主键 2） |
| ChannelId (channel_id) | int | `primaryKey;index`（复合主键 3） |
| Enabled (enabled) | bool | 是否可用（选渠只取 enabled=true） |
| Priority (priority) | *int64 | `default:0;index` |
| Weight (weight) | uint | `default:0;index` |
| Tag (tag) | *string | `index` |

派生 `AbilityWithChannel`：内嵌 Ability + `ChannelType(channel_type) int`（join channels.type）。

---

## 5. Log（用量/操作日志）— `model/log.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 复合索引 idx_created_at_id / idx_user_id_id |
| UserId (user_id) | int | `index` |
| CreatedAt (created_at) | int64 | `bigint`，复合索引时间维 |
| Type (type) | int | 见「Log Type 枚举」 |
| Content (content) | string | 文本描述 |
| Username (username) | string | `index;default:''` |
| TokenName (token_name) | string | `index;default:''` |
| ModelName (model_name) | string | `index;default:''` |
| Quota (quota) | int | `default:0`，本次消费/赠送额度 |
| PromptTokens (prompt_tokens) | int | `default:0` |
| CompletionTokens (completion_tokens) | int | `default:0` |
| UseTime (use_time) | int | `default:0`，耗时 |
| IsStream (is_stream) | bool | 是否流式 |
| ChannelId (channel) | int | `index` |
| ChannelName (channel_name) | string | `->` 只读 join |
| TokenId (token_id) | int | `default:0;index` |
| Group (group) | string | `index` |
| Ip (ip) | string | `index;default:''` |
| RequestId (request_id) | string | `varchar(64)` |
| UpstreamRequestId (upstream_request_id) | string | `varchar(128)` |
| Other (other) | string | 附加 JSON |
| RequestedModel (requested_model) | string | `varchar(255);index;default:''` —— **C，客户实际输入模型名**（兼容层）。**客户可见** |
| ResolvedPublicModel (resolved_public_model) | string | `varchar(255);index;default:''` —— **A，平台公开名**（L1 后/L2 前）。**客户可见** |
| ActualUpstreamModel (actual_upstream_model) | string | `varchar(255);index;default:''` —— **B，真实上游模型名**（L2 后）。**客户不可见，仅 admin/root** |
| InboundProtocol (inbound_protocol) | string | `varchar(32);default:''` —— 入站协议（openai/claude/...） |
| UpstreamProtocol (upstream_protocol) | string | `varchar(32);default:''` —— 目标供应商协议 |
| ProtocolConverted (protocol_converted) | bool | `default:false` —— 是否发生头尾协议转换 |
| UserAgent (user_agent) | string | `varchar(512);default:''` —— 调用方 User-Agent（调用明细展示，原型已落列） |
| QuotaSell (quota_sell) | int | `default:0` —— **本笔售价金额**（= BasePriceRatio(A)×GroupRatio×tokens，同 Quota 口径）。**客户可见** |
| QuotaCost (quota_cost) | int | `default:0` —— **本笔成本金额**（= tokens×CostRatio(channel,B)，不乘折扣）。**仅 admin/root** |
| QuotaProfit (quota_profit) | int | `default:0` —— **本笔利润** = quota_sell − quota_cost（可为负=亏损告警）。**仅 admin/root** |

**Log Type 枚举**（int，禁用 iota，值固定）：`0=Unknown`、`1=Topup`、`2=Consume`、`3=Manage`、`4=System`、`5=Error`、`6=Refund`、`7=Login`。

**三段模型字段口径**（落 DECISIONS §2/§8，唯一权威 COMPAT-LAYER-DATA-OBJECTS §3）：`model_name` 保留语义不破坏现网报表，取值 = `requested_model`(C)。渠道级 L3 重定向名 B' 不单设列，写入 `Other` JSON 的 `{"channel_redirect":"B'"}` 仅诊断用。
**计费金额字段口径**（落 DECISIONS §4/§8）：`quota_sell` 与现有 `Quota` 一致；`quota_cost/quota_profit` 为新增经营字段。成本行缺失时 `quota_cost=0`、`quota_profit=quota_sell`，并在 `Other` 写 `{"cost_missing":true,...}` 供看板告警。
**视图裁剪 DTO**：`UserLogView` 只给 C/A + quota_sell（客户视角实付），**无** B、无成本/利润；`AdminLogView` 给全链 C→A→B + 协议 + 售价/成本/利润 + channel_id（利润看板数据源）。
**迁移**：SQLite `ALTER TABLE logs ADD COLUMN`，三库 GORM AutoMigrate 给 default 避存量 NULL（AGENTS.md Rule 2）。

---

## 6. Redemption（兑换码）— `model/redemption.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| UserId (user_id) | int | 创建者 |
| Key (key) | string | `char(32);uniqueIndex`，兑换码明文 |
| Status (status) | int | `default:1`，1=未使用 / 已使用 / 已禁用 |
| Name (name) | string | `index` |
| Quota (quota) | int | `default:100`，面额（quota 单位） |
| CreatedTime (created_time) | int64 | `bigint` |
| RedeemedTime (redeemed_time) | int64 | `bigint`，核销时间 |
| Count (count) | int | `-:all`，仅 API 请求用（批量生成数量） |
| UsedUserId (used_user_id) | int | 核销人 |
| ExpiredTime (expired_time) | int64 | `bigint`，0=不过期 |
| DeletedAt | gorm.DeletedAt | `index` |

---

## 7. TopUp（充值订单）— `model/topup.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| UserId (user_id) | int | `index` |
| Amount (amount) | int64 | 充值额度（quota 单位） |
| Money (money) | float64 | 支付金额 |
| TradeNo (trade_no) | string | `unique;varchar(255);index`，商户订单号 |
| PaymentMethod (payment_method) | string | `varchar(50)`；枚举见下 |
| PaymentProvider (payment_provider) | string | `varchar(50);default:''`；枚举见下 |
| CreateTime (create_time) | int64 | 创建时间 |
| CompleteTime (complete_time) | int64 | 完成时间 |
| Status (status) | string | 订单状态（pending/success 等） |

**PaymentMethod 枚举**：`stripe`/`creem`/`waffo`/`waffo_pancake`/`balance`。
**PaymentProvider 枚举**：`epay`/`stripe`/`creem`/`waffo`/`waffo_pancake`/`balance`。

---

## 8. Subscription（订阅）— `model/subscription.go`

### 8.1 SubscriptionPlan（订阅套餐）

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| Title (title) | string | `varchar(128);not null` |
| Subtitle (subtitle) | string | `varchar(255);default:''` |
| PriceAmount (price_amount) | float64 | `decimal(10,6);default:0` |
| Currency (currency) | string | `varchar(8);default:'USD'` |
| DurationUnit (duration_unit) | string | `default:'month'`；枚举 `year/month/day/hour/custom` |
| DurationValue (duration_value) | int | `default:1` |
| CustomSeconds (custom_seconds) | int64 | 自定义时长秒 |
| Enabled (enabled) | bool | `default:true` |
| SortOrder (sort_order) | int | `default:0` |
| AllowBalancePay (allow_balance_pay) | *bool | `default:true` |
| AllowWalletOverflow (allow_wallet_overflow) | *bool | `default:true`，配额耗尽后回落钱包 |
| TotalAmount (total_amount) | int64 | `default:0`，套餐总额度，0=无限 |
| QuotaResetPeriod (quota_reset_period) | string | `default:'never'`；枚举 `never/daily/weekly/monthly/custom` |
| MaxPurchasePerUser (max_purchase_per_user) | int | `default:0`，0=不限 |
| UpgradeGroup (upgrade_group) | string | 购买后升级分组 |
| DowngradeGroup (downgrade_group) | string | 到期降级分组 |
| StripePriceId / CreemProductId / WaffoPancakeProductId | string | 各支付渠道商品号 |

### 8.2 SubscriptionOrder（订阅下单）

`Id`、`UserId(user_id)`、`PlanId(plan_id)`、`Money(money) float64`、`TradeNo(trade_no) unique`、`PaymentMethod`、`PaymentProvider`、`Status(status) string`、`CreateTime`、`CompleteTime`、`ProviderPayload(provider_payload) text`。

### 8.3 UserSubscription（用户订阅实例）

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| UserId (user_id) | int | `index` |
| PlanId (plan_id) | int | `index` |
| AmountTotal (amount_total) | int64 | 总额度 |
| AmountUsed (amount_used) | int64 | 已用额度 |
| StartTime (start_time) | int64 | 生效时间 |
| EndTime (end_time) | int64 | 到期时间 |
| Status (status) | string | `varchar(32)`；枚举 `active/expired/cancelled` |
| Source (source) | string | `default:'order'`；枚举 `order/admin` |
| LastResetTime / NextResetTime | int64 | 配额重置时间 |
| UpgradeGroup / PrevUserGroup / DowngradeGroup | string | 分组快照 |
| AllowWalletOverflow (allow_wallet_overflow) | bool | `default:true` |

---

## 9. Task（异步任务：视频/音乐/MJ 等）— `model/task.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| ID (id) | int64 | 主键 |
| TaskID (task_id) | string | `varchar(191);index`，第三方任务 id |
| Platform (platform) | constant.TaskPlatform | `varchar(30);index` |
| UserId (user_id) | int | `index` |
| Group (group) | string | `varchar(50)`，计费分组 |
| ChannelId (channel_id) | int | `index` |
| Quota (quota) | int | 任务消费额度 |
| Action (action) | string | `varchar(40);index`，任务类型 |
| Status (status) | TaskStatus | `varchar(20);index`；枚举见下 |
| FailReason (fail_reason) | string | 失败原因 |
| SubmitTime / StartTime / FinishTime | int64 | `index` |
| Progress (progress) | string | `varchar(20)` |
| Properties (properties) | Properties | `json` |
| Data (data) | json.RawMessage | `json`，对外数据 |
| PrivateData (-) | TaskPrivateData | `json`，含 key 等隐私，不下发 |
| CreatedAt / UpdatedAt | int64 | 时间戳 |

**TaskStatus 枚举**（string）：`NOT_START`/`SUBMITTED`/`QUEUED`/`IN_PROGRESS`/`FAILURE`/`SUCCESS`/`UNKNOWN`。

---

## 10. Checkin（签到记录）— `model/checkin.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | `primaryKey;autoIncrement` |
| UserId (user_id) | int | `not null;uniqueIndex:idx_user_checkin_date`（复合唯一 1） |
| CheckinDate (checkin_date) | string | `varchar(10);not null;uniqueIndex:idx_user_checkin_date`（复合唯一 2），格式 `YYYY-MM-DD` |
| QuotaAwarded (quota_awarded) | int | `not null`，本次随机奖励额度 |
| CreatedAt (created_at) | int64 | `bigint` |

表名 `checkins`。脱敏返回 `CheckinRecord`：仅 `checkin_date`、`quota_awarded`（不含 id/user_id）。
**CheckinSetting（`operation_setting/checkin_setting.go`）**：`Enabled(enabled) bool`（默认 false）、`MinQuota(min_quota) int`（默认 1000）、`MaxQuota(max_quota) int`（默认 10000）。奖励为 `[MinQuota,MaxQuota]` 随机值。
统计返回字段：`total_quota`/`total_checkins`/`checkin_count`/`checked_in_today`。

---

## 11. UserOAuthBinding（自定义 OAuth 绑定）— `model/user_oauth_binding.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | `primaryKey` |
| UserId (user_id) | int | `not null;uniqueIndex:ux_user_provider`（每用户每 provider 一条） |
| ProviderId (provider_id) | int | `not null;uniqueIndex:ux_user_provider + ux_provider_userid` |
| ProviderUserId (provider_user_id) | string | `varchar(256);not null;uniqueIndex:ux_provider_userid`（每 provider 一账号唯一） |
| CreatedAt (created_at) | time.Time | 绑定时间 |

表名 `user_oauth_bindings`。

---

## 12. TwoFA / TwoFABackupCode（双因子）— `model/twofa.go`

**TwoFA**：`Id`、`UserId(user_id) unique;not null`、`Secret(-) varchar(255)`（TOTP 密钥，`json:"-"` 不下发）、`IsEnabled(is_enabled) bool`、`FailedAttempts(failed_attempts) int default:0`、`LockedUntil(locked_until) *time.Time`、`LastUsedAt(last_used_at) *time.Time`、`CreatedAt`、`UpdatedAt`、`DeletedAt index`。
**TwoFABackupCode**：`Id`、`UserId(user_id) not null;index`、`CodeHash(-) varchar(255)`（备用码哈希，不下发）、`IsUsed(is_used) bool`、`UsedAt(used_at) *time.Time`、`CreatedAt`、`DeletedAt`。

---

## 13. PasskeyCredential（无密码凭据）— `model/passkey.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| ID (id) | int | `primaryKey` |
| UserID (user_id) | int | `uniqueIndex;not null` |
| CredentialID (credential_id) | string | `varchar(512);uniqueIndex;not null`，base64 |
| PublicKey (public_key) | string | `text;not null`，base64 |
| AttestationType (attestation_type) | string | `varchar(255)` |
| AAGUID (aaguid) | string | `varchar(512)`，base64 |
| SignCount (sign_count) | uint32 | `default:0` |
| CloneWarning / UserPresent / UserVerified / BackupEligible / BackupState | bool | WebAuthn 状态位 |
| Transports (transports) | string | `text` |
| Attachment (attachment) | string | `varchar(32)` |

错误：`ErrPasskeyNotFound`、`ErrFriendlyPasskeyNotFound`（"Passkey 验证失败，请重试或联系管理员"）。

---

## 14. PrefillGroup（预填充组：模型组/标签组/端点组）— `model/prefill_group.go`

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键 |
| Name (name) | string | `size:64;not null;uniqueIndex:uk_prefill_name`（软删除条件唯一） |
| Type (type) | string | `size:32;index;not null`；枚举 `model`/`tag`/`endpoint` |
| Items (items) | JSONValue | `json`，字符串数组，如 `["gpt-4o","gpt-3.5-turbo"]` |
| Description (description) | string | `varchar(255)` |
| CreatedTime / UpdatedTime | int64 | `bigint` |
| DeletedAt (-) | gorm.DeletedAt | `index` |

---

## 15. Option / Setting（系统配置 KV）— `model/option.go`

`Option`：`Key(key) string primaryKey`、`Value(value) string`。所有系统开关以 KV 形式存储，启动时 `InitOptionMap()` 装载到内存。
本批 PRD 涉及的开关键：`RegisterEnabled`、`PasswordLoginEnabled`、`PasswordRegisterEnabled`、`EmailVerificationEnabled`、`GitHubOAuthEnabled`、`LinuxDOOAuthEnabled`、`TelegramOAuthEnabled`、`WeChatAuthEnabled`。Telegram 还含 `TelegramBotToken`（HMAC 派生 key=`SHA256(BotToken)`）。

---

## 16. PublicModel（对外模型商品目录）— `model/public_model.go`

> 落 DECISIONS §3 模型分级。表名 `public_models`。一个对外模型（公开名 A）一条记录；品质不同的拆成独立记录分别定价。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增（禁手写 AUTO_INCREMENT/SERIAL） |
| PublicName (public_name) | string | `varchar(255);uniqueIndex:uk_public_name` —— **A，平台公开名**。与 PlatformModelMapping.PublicName 同键 |
| QualityTier (quality_tier) | string | `varchar(32);index;default:'full'` —— **品质标签** full/max/air/自定义（满血/增强/经济）。纯展示分类，不参与计费数值 |
| BasePriceRatio (base_price_ratio) | float64 | `default:0` —— **基准售价倍率**（对客户恒定）。口径 = model_ratio；保存时同步刷 model_ratio KV |
| UsePrice (use_price) | bool | `default:false` —— true=按次/固定价，false=按倍率 |
| BasePrice (base_price) | float64 | `default:0` —— UsePrice=true 时固定单价（同步刷 model_price KV） |
| Enabled (enabled) | bool | `default:true` —— 上下架；false=不进客户可见目录 |
| DisplayName (display_name) | string | `varchar(255);default:''` —— 公开站展示名 |
| SortOrder (sort_order) | int | `default:0` —— 公开站排序 |
| Description (description) | string | `varchar(1024);default:''` |
| CreatedTime / UpdatedTime | int64 | `bigint;autoCreateTime` / `autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index`，软删除 |

**业务规则**：`opus-4.8`/`opus-4.8-增强`/`opus-4.8-经济` = 三条独立记录（三 public_name、三 base_price_ratio、quality_tier=full/max/air）。A 的对外全集 = `enabled=true AND deleted_at IS NULL` 的 public_name 集（对外商品目录唯一权威）。售价对客户恒定（与实际渠道无关）。内存缓存 key=public_name，`InitPublicModelMap()` 装载，写时失效。

---

## 17. PlatformModelMapping（超管底仓映射 A→B，全局，客户不可见）— `model/platform_model_mapping.go`

> 落 DECISIONS §2。表名 `platform_model_mappings`。生效顺序 L2（A→B），选渠之前。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增 |
| PublicName (public_name) | string | `varchar(255);uniqueIndex:uk_public_name` —— **A**，唯一索引保证 1对1 |
| UpstreamName (upstream_name) | string | `varchar(255);not null` —— **B，真实上游模型名。客户绝不可见**（无 user 路由读接口） |
| Enabled (enabled) | bool | `default:true`，false=A 回落直通或 404 |
| Remark (remark) | string | `varchar(255)`，超管备注 |
| CreatedTime / UpdatedTime | int64 | `bigint;autoCreateTime` / `autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index` |

**作用域**：全局，无 group/user 维。内存缓存 key=public_name，`InitPlatformModelMappingMap()` 装载，写时失效。**B 不可见三道闸**：数据层无客户读 B 接口 / 序列化层客户视图只给 C→A / 候选层只返公开模型 A 全集。

---

## 18. UserModelAlias（客户层自助映射 C→A，分组/用户级）— `model/user_model_alias.go`

> 落 DECISIONS §2。表名 `user_model_aliases`。生效顺序 L1（C→A），在 L2 之前。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增 |
| ScopeType (scope_type) | string | `varchar(16);not null`；枚举 user/group（复合唯一键 1） |
| ScopeId (scope_id) | string | `varchar(64);not null`；user→user_id 字符串化，group→分组名（复合唯一键 2） |
| Alias (alias) | string | `varchar(255);not null` —— **C，客户别名**（复合唯一键 3） |
| Target (target) | string | `varchar(255);not null` —— **A，目标公开名。不强制白名单**（可硬输平台没有的名） |
| Enabled (enabled) | bool | `default:true` |
| CreatedTime / UpdatedTime | int64 | `bigint;autoCreateTime` / `autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index` |

**复合唯一索引**：`uk_scope_alias (scope_type, scope_id, alias)` —— 同作用域内别名 C 唯一（1对1）。**优先级**：同 C 命中多 scope 时 **user > group**。**输入约束**：Target 写入不校验白名单（铁律），候选由前端从公开模型全集联想但落库不拦。**越权护栏**：user 路由写入强制 `scope_type=user AND scope_id=:caller_user_id`，禁跨 scope 写。

---

## 19. ChannelModelCost（供应商成本倍率，挂渠道×真实模型 B）— `model/channel_model_cost.go`

> 落 DECISIONS §4 成本/售价分离。表名 `channel_model_costs`。结算阶段取值（链路第 17 步）。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增 |
| ChannelId (channel_id) | int | `index;uniqueIndex:uk_channel_model` —— 供应商渠道（=Channel.Id）（复合唯一键 1） |
| UpstreamModel (upstream_model) | string | `varchar(255);uniqueIndex:uk_channel_model` —— **真实模型 B**（复合唯一键 2）。**客户不可见** |
| CostRatio (cost_ratio) | float64 | `default:0` —— **成本倍率**（超管手填）。口径同 model_ratio（输入 token） |
| CompletionCostRatio (completion_cost_ratio) | float64 | `default:0` —— 成本补全倍率（输出 token）；0=回落 CostRatio×现网 CompletionRatio |
| Enabled (enabled) | bool | `default:true` —— false=视为缺失（记 0+告警） |
| EffectiveTime (effective_time) | int64 | `bigint;default:0` —— 取最新生效且 enabled 一条；预留成本版本化 |
| SourceUnitPrice (source_unit_price) | float64 | `default:0` —— **扩展位**：进货单价（自动折算预留），本期不参与计算 |
| Remark (remark) | string | `varchar(255);default:''` |
| CreatedTime / UpdatedTime | int64 | `bigint;autoCreateTime` / `autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index` |

**复合唯一索引**：`uk_channel_model (channel_id, upstream_model)` —— 一渠道对一 B 只一条生效成本。**多供应商落表**：同一 A→B 下，每个挂 Ability 的渠道各一行（售价同挂 A，成本分行挂 channel×B）。**取值时机**：结算阶段，主键 `(实际选中 ChannelId, L2 后 B)` 精确取一行（兜底切换后 ChannelId 变→自动取新渠道行）。内存缓存 `[channelId][upstreamModel]→CostRatio`，`InitChannelModelCostMap()` 装载，写时失效。

---

## Token 端点级减法约束扩展（可选，落 DECISIONS §7）

`Token`（§2）新增 2 列（可选）：`EndpointLimitsEnabled (endpoint_limits_enabled) bool default:false`（端点全开）、`EndpointLimits (endpoint_limits) text default:''`（JSON 允许的入站协议集，如 `["openai","claude"]`）。校验在 TokenAuth 后、L1 前；纯减法自我约束非权限闸门。`Token.ModelLimits` 复用不动，语义从「加法授权」明确为「减法约束」（模型全开背景）。

---

## 计费/映射运行期内存结构（不落库，逐笔快照）

- **PriceData**（`types/price_data.go`）新增：`CostRatio`/`CompletionCostRatio`/`CostMissing`（第 17 步从 ChannelModelCost 取）；现有 ModelRatio/ModelPrice/GroupRatioInfo/UsePrice 复用承载售价端。
- **RelayInfo**（`relay/common/relay_info.go`）：`RequestedModel(C)`/`ResolvedPublicModel(A)` 新增，`UpstreamModelName(B)`/`UsingGroup`/`ChannelId`/`InboundFormat(RelayFormat)` 复用，`TargetProtocol`/`Passthrough` 新增，`QuotaSell/QuotaCost/QuotaProfit` 新增。
- **IR 中间表示**（`relay/compat/ir`，仅运行期）：ChatIR/ChatRespIR/ChatDeltaIR/StreamState，覆盖 OpenAI⇄Anthropic 五差异点（D1 system 位置 / D2 content 结构 / D3 tools / D4 stop_reason / D5 usage 字段名）。详见 COMPAT-LAYER-DATA-OBJECTS §4。

---

## 新增/变更汇总（本轮兼容层+计费体系反溯）

| 对象 | 类型 | 复用/新增 | 主键/唯一键 |
|---|---|---|---|
| PublicModel | DB 表 | 新增 | uniqueIndex(public_name=A) |
| PlatformModelMapping | DB 表 | 新增 | uniqueIndex(public_name=A) |
| UserModelAlias | DB 表 | 新增 | uniqueIndex(scope_type,scope_id,alias=C) |
| ChannelModelCost | DB 表 | 新增 | uniqueIndex(channel_id,upstream_model=B) |
| Log（C/A/B+协议3+UA+售价成本利润3） | DB 列 ×10 | 扩展 | — |
| Token（endpoint_limits ×2） | DB 列 | 扩展（可选） | — |
| group_ratio / Token.ModelLimits / User.Group | — | 复用，语义收窄（纯折扣/减法约束） | — |
| Channel.ModelMapping / Ability | — | 复用不动 | — |

**新增表=4；扩展列=Log+10、Token+2（可选）；全部 GORM AutoMigrate 三库兼容。**

---

## 错误码 / 消息常量索引（本批 PRD 引用）

| 常量 | 触发场景 | 出处 |
|---|---|---|
| MsgUserExists | 注册时用户名重复 | F-1001 |
| MsgOAuthStateInvalid | OAuth 回调 state 不匹配（403） | F-1016 |
| MsgOAuthAlreadyBound | 外部 ID 已被其他账号绑定 | F-1025 |
| MsgOAuthTrustLevelLow | LinuxDO 信任级过低 | AC-4 |
| MsgTokenNameTooLong | 令牌名 >50 字符 | F-3001 |
| MsgTokenQuotaNegative | 令牌额度 <0 | F-3001 |
| MsgTokenQuotaExceedMax | 令牌额度 >10亿*QuotaPerUnit | F-3001 |
| MsgTokenExpiredCannotEnable | 启用已过期令牌 | F-3007 |
| MsgTokenExhaustedCannotEable | 启用额度耗尽令牌 | F-3007 |
| MsgBatchTooMany{Max:100} | 批量取 key ids>100 | F-3005 |
| MsgInvalidParams | 批量取 key ids 为空 | F-3005 |
| MsgTokenGetInfoFailed | 外部用量查询 key 无效 | F-3012 |
| IsTelegramIdAlreadyTaken | Telegram 绑定唯一性冲突 | F-1052 |
