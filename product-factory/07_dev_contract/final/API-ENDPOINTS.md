# API-ENDPOINTS — 可实现级 API 端点契约（S7 第2步）

> 项目：Nexa（AI API 网关 SaaS，基于 new-api 二次开发，Go + Gin + GORM）。
> 本文件是 API 契约的人读版唯一权威。每个端点到「可实现级」：路径 + 方法 + 鉴权级别 + 入参/出参 schema + 错误码 + 对应功能 ID + 幂等键（状态变更端点）。
>
> **权威对齐**：
> - 字段名逐字对齐 `03_flow_prd/final/DATA-MODEL.md`（GORM struct + json tag）、`03_flow_prd/final/prd/*.md`（校验/错误码）；不自创字段。
> - 鉴权级别查 `02_decomposition/final/ROLE-PERMISSION-MATRIX.md`：匿名 / UserAuth（common+）/ AdminAuth（admin+）/ RootAuth（root）。
> - 功能 ID 来自 `02_decomposition/final/FUNCTION-LIST.csv`（权威）；PRD 块号一并标注交叉引用。
>
> **产品铁律（贯穿所有出参 DTO）**：
> 1. **客户视图 DTO（UserView）绝不含**：成本（`quota_cost`）、利润（`quota_profit`）、上游真实模型 B（`actual_upstream_model`/`upstream_name`/`upstream_model`）、供应商渠道维（`channel_id`/`channel_name`）、`cost_ratio`。
> 2. **管理视图 DTO（AdminView）** 给全链 C→A→B + 协议 + 售价/成本/利润 + channel_id。
> 3. 凡涉及 key/secret 的字段：列表脱敏（`MaskTokenKey`），取明文走 CriticalRateLimit + DisableCache + SecureVerification。
>
> **本批覆盖模块群（按 FUNCTION-LIST `module` 列筛）**：① 账号与身份 / D1（F-1001~F-1054、F-5031~F-5034 部分）② 令牌管理（F-3001~F-3012）③ 计费与额度 / D6（F-2038~F-2048、F-6007~F-6009）④ 模型广场 + 模型元数据 + 模型同步 + 供应商元数据 + D8 模型与供应商（F-3013~F-3025、F-6001~F-6004）⑤ 渠道管理与上游路由 + 渠道运维 + D7 渠道与供应商（F-2016~F-2037、F-4045、F-6005/F-6006）。
>
> **通用响应包络**（new-api 惯例）：`{ "success": bool, "message": string, "data": <payload> }`。分页响应 `data` 形如 `{ "items": [...], "total": int, "page": int, "page_size": int }`。下文「出参 schema」只列 `data` 内字段。
>
> **幂等键图例**：状态变更端点在小节内标 `幂等键: <字段>`；纯查询端点不标。

---

# 模块一：账号与身份（D1 账号与身份）

> 鉴权：注册/登录/找回/OAuth 回调/Passkey 登录 = 匿名；`/api/user/self/*` = UserAuth（self-scope）；`/api/user/`、`/api/user/:id/*`、`/api/user/manage` = AdminAuth；自定义 OAuth provider 配置 = RootAuth。
> 字段源：DATA-MODEL §1 User / §11 UserOAuthBinding / §12 TwoFA / §13 PasskeyCredential / §15 Option。

## 1.1 注册与登录

### POST /api/user/register — 邮箱密码注册
- **功能 ID**：F-1001（+ F-1005 验证码校验、F-1040 邀请归因）
- **鉴权**：匿名（中间件链：TurnstileCheck + CriticalRateLimit + anonymousRequestBodyLimit）
- **入参**（JSON）：
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | `username` | string | 是 | `validate:max=20`，唯一 |
  | `password` | string | 是 | `validate:min=8,max=20` |
  | `email` | string | 否 | `validate:max=50`；`EmailVerificationEnabled=true` 时为必填 |
  | `verification_code` | string | 条件 | `EmailVerificationEnabled=true` 时必填（F-1005） |
  | `aff_code` | string | 否 | 邀请码（F-1040 归因，无效则 inviter_id=0） |
- **出参（UserView）**：`{ "success": true }`（不下发 password/access_token；`AccessToken` 字段 `json:"-"` 永不下发）
- **错误码**：`RegisterEnabled=false`→注册被禁；`MsgUserExists`（用户名重复）；验证码错误/过期（F-1005）；Turnstile 校验失败；CriticalRateLimit 超限。
- **幂等键**：`username`（unique;index）—— 重复用户名拒绝。
- **副作用**：`user.Role=RoleCommonUser`、`user.Quota=QuotaForNewUser`、生成 4 位 `aff_code`；带有效 aff_code 时 `inviter_id` 写邀请人、邀请人 `aff_count++`/`aff_quota+=QuotaForInviter`（F-1042/F-1043）。

### POST /api/user/login — 邮箱密码登录
- **功能 ID**：F-1002
- **鉴权**：匿名（TurnstileCheck + CriticalRateLimit）
- **入参**（JSON）：`username` string 必填、`password` string 必填。
- **出参（UserView）**：`data = { id, username, display_name, role, status, group, quota, used_quota, request_count, aff_code, aff_count, aff_quota, aff_history_quota, email, github_id, discord_id, wechat_id, telegram_id, linux_do_id, oidc_id, last_login_at }`（**裁掉** `password`/`access_token`/`remark`/`setting` 内部；普通用户 `remark` 置空 见 F-1014）。
- **错误码**：凭证错误；`Status!=UserStatusEnabled`（被封禁拒绝登录）；已开 2FA 用户需走 `/api/user/login/2fa` 第二步（F-1036）；Turnstile/限流。
- **副作用**：`setupLogin` 建立会话；写登录审计日志 `Type=7 LogTypeLogin`（F-4013）。

### GET /api/user/logout — 用户登出
- **功能 ID**：F-1003
- **鉴权**：UserAuth（无会话调用幂等返回）
- **入参**：无
- **出参**：`{ "success": true }`
- **幂等键**：会话本身（无会话时幂等成功）。

### POST /api/user/login/2fa — 2FA 登录第二步
- **功能 ID**：F-1036
- **鉴权**：匿名（CriticalRateLimit；密码登录后的二段）
- **入参**（JSON）：`code` string 必填（TOTP 6 位或备份码）；登录上下文（pending 会话或 user 标识）。
- **出参（UserView）**：同 `/api/user/login` 的 data。
- **错误码**：TOTP/备份码错误拒绝；CriticalRateLimit。

## 1.2 邮箱验证与密码找回

### GET /api/verification — 发送注册/找回邮箱验证码
- **功能 ID**：F-1004
- **鉴权**：匿名（EmailVerificationRateLimit + TurnstileCheck）
- **入参**（query）：`email` string 必填。
- **出参**：`{ "success": true }`
- **错误码**：`EmailVerificationEnabled=false` 不可用；EmailVerificationRateLimit 限流；Turnstile 失败。

### GET /api/reset_password — 发送重置密码邮件
- **功能 ID**：F-1006
- **鉴权**：匿名（CriticalRateLimit + TurnstileCheck）
- **入参**（query）：`email` string 必填。
- **出参**：`{ "success": true }`（非注册邮箱不发有效令牌，仍返回成功避免枚举）。

### POST /api/user/reset — 提交重置新密码
- **功能 ID**：F-1007
- **鉴权**：匿名（CriticalRateLimit）
- **入参**（JSON）：`email` string 必填、`token` string 必填（PasswordResetToken）、`password` 新密码 string（`validate:min=8,max=20`）。
- **出参**：`{ "success": true }`
- **错误码**：令牌无效/过期拒绝；CriticalRateLimit。
- **副作用**：更新 `user.password`，旧密码失效。

## 1.3 个人信息与设置（self-scope）

### GET /api/user/self — 获取本人信息（含邀请统计）
- **功能 ID**：F-1045（+ F-1014 备注隐藏）
- **鉴权**：UserAuth
- **入参**：无
- **出参（UserView）**：`data = { id, username, display_name, role, status, group, quota, used_quota, request_count, aff_code, aff_count, aff_quota, aff_history_quota, email, telegram_id, github_id, discord_id, wechat_id, linux_do_id, oidc_id, last_login_at, setting }`。**`remark` 对普通用户置空**（F-1014）；不下发 `password`/`access_token`。

### PUT /api/user/self/setting — 保存本人个人设置
- **功能 ID**：F-1014
- **鉴权**：UserAuth
- **入参**（JSON）：`setting` object（序列化 dto.UserSetting，含语言/边栏偏好、额度预警 `warning_type`(email/webhook/bark)/`warning_threshold`/`webhook_url`/`webhook_secret`/`bark_url` 见 F-4037）。
- **出参**：`{ "success": true }`
- **错误码**：`warning_threshold` 非正数→「预警阈值必须为正数」（F-4037）。
- **幂等键**：`user_id`（覆盖式写入，幂等）。

### GET /api/user/self/aff — 获取个人邀请码
- **功能 ID**：F-1039
- **鉴权**：UserAuth
- **入参**：无
- **出参**：`data = "<aff_code>"`（AffCode 为空时生成唯一 4 位码落库）。
- **幂等键**：`aff_code`（uniqueIndex；已有则直接返回）。

## 1.4 用户管理（AdminAuth）

### GET /api/user/ — 用户列表（分页）
- **功能 ID**：F-1008
- **鉴权**：AdminAuth
- **入参**（query）：`p` int（页码）、`page_size` int。
- **出参（AdminView）**：`items[]` = `{ id, username, display_name, role, status, email, group, quota, used_quota, request_count, aff_code, aff_count, inviter_id, remark, github_id, discord_id, wechat_id, telegram_id, last_login_at, created_at }`（**含 remark/inviter_id**，区别于 UserView）；`total`/`page`/`page_size`。

### GET /api/user/search — 搜索用户
- **功能 ID**：F-1008
- **鉴权**：AdminAuth
- **入参**（query）：`keyword` string、`p`/`page_size`。
- **出参（AdminView）**：同 `GET /api/user/`。

### GET /api/user/:id — 用户详情
- **功能 ID**：F-1008
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参（AdminView）**：单用户全字段（同列表项 + `setting`）。

### POST /api/user/ — 创建用户
- **功能 ID**：F-1009
- **鉴权**：AdminAuth
- **入参**（JSON）：`username` string 必填、`password` string 必填、`display_name` string、`role` int（不可高于自身角色）。
- **出参**：`{ "success": true }`
- **错误码**：不能创建高于自身角色的用户（越权护栏）。
- **幂等键**：`username`（unique）。

### POST /api/user/manage — 管理用户状态
- **功能 ID**：F-1010
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填、`action` string 必填（`disable`/`enable`/`promote`/`demote`/`delete`）。
- **出参**：`{ "success": true }`
- **错误码**：不可对同级或更高角色越权操作（`目标角色 >= 操作者角色` 拒绝）。
- **幂等键**：`(id, action)`—— 目标状态幂等（如已禁用再禁用无副作用）。
- **状态变更**：禁用后 `Status` 变禁用、无法登录。

### PUT /api/user/ — 更新用户资料（管理端）
- **功能 ID**：F-1011
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填、可选 `display_name`/`email`/`group`/`quota`/`remark`/`status`。
- **出参**：`{ "success": true }`
- **幂等键**：`id`（覆盖式更新）。

## 1.5 OAuth / 第三方登录（D1）

### GET /api/oauth/state — 生成 OAuth state（CSRF）暂存 aff
- **功能 ID**：F-1015（+ F-1041 OAuth 归因）
- **鉴权**：匿名（CriticalRateLimit）
- **入参**（query）：`aff` string 可选（邀请码，存 session）。
- **出参**：`data = "<state>"`（随机 state 写入会话）。
- **错误码**：回调时 state 不匹配→`MsgOAuthStateInvalid`（403）。

### GET /api/oauth/:provider — 通用 OAuth 回调（github/oidc/自定义）
- **功能 ID**：F-1016（GitHub）、F-1017（legacy_id 迁移）、F-1019（OIDC）、F-1025（自定义 provider）
- **鉴权**：匿名（已登录会话则走 handleOAuthBind 绑定分支）
- **入参**（query）：`code` string、`state` string；`:provider` ∈ github/oidc/<custom_id>。
- **出参（UserView）**：登录成功返回用户信息（同 `/login`），或绑定成功 `{ "success": true }`。
- **错误码**：state 不匹配 403；provider 未启用拒绝；`RegisterEnabled=false` 时不创建新用户；`MsgOAuthAlreadyBound`（外部 ID 已被占用）。
- **副作用**：首次回调创建 `github_id`/`oidc_id` 绑定或写 `user_oauth_bindings`（F-1025）；legacy_id 迁移 `UpdateGitHubId`（F-1017，容错）。
- **幂等键**：`(provider, provider_user_id)`（user_oauth_bindings `ux_provider_userid`）。

### GET /api/oauth/discord — Discord OAuth 登录/绑定
- **功能 ID**：F-1018
- **鉴权**：匿名 / 已登录（绑定分支）
- **入参**（query）：`code`、`state`。
- **出参（UserView）**：登录用户信息 / 绑定成功。
- **错误码**：`discord_id` 已被占用→`MsgOAuthAlreadyBound`。
- **幂等键**：`discord_id`（index，重复绑定拒绝）。

### GET /api/oauth/linuxdo — LinuxDO OAuth 登录/绑定（信任级校验）
- **功能 ID**：F-1020
- **鉴权**：匿名 / 已登录
- **入参**（query）：`code`、`state`。
- **出参（UserView）**：登录用户信息 / 绑定成功。
- **错误码**：信任级不足→`MsgOAuthTrustLevelLow`。
- **幂等键**：`linux_do_id`（index）。

### GET /api/oauth/wechat — WeChat 扫码授权发起
- **功能 ID**：F-1021
- **鉴权**：匿名（CriticalRateLimit）
- **入参**：无（微信授权态）。
- **出参**：`data`（授权态，含 code/二维码态用于 bind）。
- **错误码**：`WeChatAuthEnabled=false` 不可用。

### POST /api/oauth/wechat/bind — WeChat 绑定/登录
- **功能 ID**：F-1022
- **鉴权**：匿名（登录）/ 已登录（绑定）（CriticalRateLimit）
- **入参**（JSON）：`code` string 必填（微信授权码）。
- **出参（UserView）**：登录用户信息 / 绑定成功。
- **幂等键**：`wechat_id`（index）。

### GET /api/oauth/telegram/login — Telegram 登录（HMAC 校验）
- **功能 ID**：F-1051（+ F-1053 HMAC 防伪）
- **鉴权**：匿名（CriticalRateLimit）
- **入参**（query，Telegram Widget 参数）：`id`/`first_name`/`username`/`photo_url`/`auth_date`/`hash`（HMAC-SHA256，key=`SHA256(BotToken)`）。
- **出参（UserView）**：登录用户信息。
- **错误码**：`TelegramOAuthEnabled=false`→未开启；`hash` 不匹配→无效请求；CriticalRateLimit。

### GET /api/oauth/telegram/bind — Telegram 绑定到现有账号
- **功能 ID**：F-1052（+ F-1054 唯一性校验）
- **鉴权**：UserAuth（CriticalRateLimit）
- **入参**（query）：同 telegram/login 的 Widget 参数。
- **出参**：302 跳转 `/console/personal`。
- **错误码**：`telegram_id` 已被绑定→「该 Telegram 账户已被绑定」（F-1054）；用户已注销报错。
- **幂等键**：`telegram_id`（index，唯一绑定）。

### POST /api/custom-oauth-provider/discovery — 拉取自定义 OAuth discovery
- **功能 ID**：F-1023
- **鉴权**：RootAuth
- **入参**（JSON）：`issuer` string 必填。
- **出参（AdminView）**：`data = { authorization_endpoint, token_endpoint, userinfo_endpoint }`。

### GET/POST/PUT/DELETE /api/custom-oauth-provider[/:id] — 自定义 OAuth provider CRUD
- **功能 ID**：F-1024
- **鉴权**：RootAuth
- **入参**：POST/PUT JSON（CustomOAuthProvider 配置：name/client_id/client_secret/endpoints/scopes 等）；DELETE path `:id`。
- **出参（AdminView）**：列表/单条 provider 配置（`client_secret` 受 RootAuth 保护，审计留痕）。
- **幂等键**：provider `id`。
- **副作用**：删除后该 provider 登录入口失效。

### DELETE /api/user/self/oauth/bindings/:provider_id — 解绑本人 OAuth（自定义）
- **功能 ID**：F-1026
- **鉴权**：UserAuth（self-scope）
- **入参**（path）：`provider_id` int。
- **出参**：`{ "success": true }`（仅能解绑本人绑定）。
- **幂等键**：`(user_id, provider_id)`。

### GET /api/user/:id/oauth/bindings — 管理端查询用户 OAuth 绑定
- **功能 ID**：F-1027
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参（AdminView）**：`items[]` = `{ id, provider_id, provider_user_id, created_at }`。

### DELETE /api/user/:id/oauth/bindings/:provider_id — 管理端解绑用户 OAuth
- **功能 ID**：F-1027
- **鉴权**：AdminAuth
- **入参**（path）：`id` int、`provider_id` int。
- **出参**：`{ "success": true }`
- **幂等键**：`(user_id, provider_id)`。

## 1.6 Passkey（WebAuthn）

### POST /api/user/self/passkey/register/begin — Passkey 注册（begin）
- **功能 ID**：F-1028
- **鉴权**：UserAuth
- **入参**：无（服务端生成 challenge）。
- **出参**：`data` = WebAuthn CredentialCreationOptions。

### POST /api/user/self/passkey/register/finish — Passkey 注册（finish）
- **功能 ID**：F-1028
- **鉴权**：UserAuth
- **入参**（JSON）：WebAuthn AttestationResponse（`credential_id`/`public_key`/`attestation_type`/`transports` 等，对齐 PasskeyCredential）。
- **出参**：`{ "success": true }`
- **幂等键**：`credential_id`（uniqueIndex）。
- **副作用**：写审计 `user.passkey_register`（F-4012）。

### POST /api/user/passkey/login/begin — Passkey 登录（begin）
- **功能 ID**：F-1029
- **鉴权**：匿名（CriticalRateLimit）
- **入参**（JSON）：可选 `username`。
- **出参**：`data` = WebAuthn CredentialRequestOptions。

### POST /api/user/passkey/login/finish — Passkey 登录（finish）
- **功能 ID**：F-1029
- **鉴权**：匿名（CriticalRateLimit）
- **入参**（JSON）：WebAuthn AssertionResponse。
- **出参（UserView）**：登录用户信息（同 `/login`）。
- **错误码**：凭据校验失败→`ErrFriendlyPasskeyNotFound`「Passkey 验证失败，请重试或联系管理员」。

### POST /api/user/self/passkey/verify/begin|finish — Passkey 二次验证
- **功能 ID**：F-1030
- **鉴权**：UserAuth
- **入参**：begin 无；finish JSON = AssertionResponse。
- **出参**：`{ "success": true }`（放行受 SecureVerification 保护的敏感动作）。

### GET /api/user/self/passkey — 查询本人 Passkey 状态
- **功能 ID**：F-1031
- **鉴权**：UserAuth
- **入参**：无
- **出参**：`items[]` = `{ id, credential_id, attestation_type, sign_count, attachment }`（不下发 `public_key` 全文非必要）。

### DELETE /api/user/self/passkey — 删除本人 Passkey
- **功能 ID**：F-1031
- **鉴权**：UserAuth（self-scope）
- **入参**（query/JSON）：`credential_id` string（或删全部）。
- **出参**：`{ "success": true }`
- **幂等键**：`credential_id`。
- **副作用**：写审计 `user.passkey_delete`（F-4012）。

### DELETE /api/user/:id/reset_passkey — 管理端重置用户 Passkey
- **功能 ID**：F-1032
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`（清除目标用户全部 passkey）。
- **幂等键**：`user_id`。

## 1.7 双因子认证 2FA

### POST /api/user/self/2fa/setup — 2FA 启用第一步
- **功能 ID**：F-1033
- **鉴权**：UserAuth
- **入参**：无
- **出参**：`data = { secret, qr_code }`（TOTP 密钥；`Secret` 落库 `json:"-"`）。

### POST /api/user/self/2fa/enable — 2FA 启用第二步
- **功能 ID**：F-1033
- **鉴权**：UserAuth
- **入参**（JSON）：`code` string 必填（首个 TOTP）。
- **出参**：`{ "success": true }`
- **错误码**：TOTP 错误拒绝开启。
- **状态变更**：`is_enabled=true`。

### POST /api/user/self/2fa/disable — 2FA 关闭
- **功能 ID**：F-1034
- **鉴权**：UserAuth
- **入参**（JSON）：`code` string（TOTP）或 `password`。
- **出参**：`{ "success": true }`
- **状态变更**：`is_enabled=false`。

### POST /api/user/self/2fa/backup_codes — 生成/重置备份码
- **功能 ID**：F-1035
- **鉴权**：UserAuth（需已启用 2FA）
- **入参**：无
- **出参**：`data = { backup_codes: [...] }`（返回明文新码，旧码失效；落库存 `CodeHash`）。
- **幂等键**：调用即重置（每次生成新集合，旧失效）。

### GET /api/user/2fa/stats — 2FA 启用统计（管理端）
- **功能 ID**：F-1037
- **鉴权**：AdminAuth
- **入参**：无
- **出参（AdminView）**：`data = { enabled_count, total_users }`。

### DELETE /api/user/:id/2fa — 管理端强制关闭用户 2FA
- **功能 ID**：F-1037
- **鉴权**：AdminAuth（🔒 高危，SecureVerification + 审计）
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`
- **幂等键**：`user_id`。

## 1.8 敏感动作二次验证

### POST /api/verify — 通用敏感动作二次验证
- **功能 ID**：F-1038（+ RBAC F-5033 二次验证闸门）
- **鉴权**：UserAuth（CriticalRateLimit）
- **入参**（JSON）：验证凭据（密码 / TOTP / passkey assertion，按 SecureVerification 配置）。
- **出参**：`{ "success": true }`（放行受 `SecureVerificationRequired` 保护的动作）。
- **错误码**：验证失败拒绝；未验证则受保护动作被拦截。

## 1.9 邀请额度划转（D3，账号关联）

### POST /api/user/self/aff_transfer — 邀请额度划转为可用额度
- **功能 ID**：F-1044
- **鉴权**：UserAuth（需通过 payment_compliance）
- **入参**（JSON）：`quota` int 必填（划转额度，`>= QuotaPerUnit`）。
- **出参**：`{ "success": true }`
- **错误码**：`quota < QuotaPerUnit`→最小额度错；`AffQuota` 不足→邀请额度不足；未过 payment_compliance 拒绝。
- **状态变更**：`aff_quota -= quota`、`quota += quota`（同事务）。
- **幂等键**：无（逐次划转，非幂等；靠事务保证原子）。

---

# 模块二：令牌管理（F-3001~F-3012）

> 鉴权：全部 self-scope（UserAuth），按 `user_id` 强制过滤越权（F-5032）；取明文 key 走 CriticalRateLimit + DisableCache。
> 字段源：DATA-MODEL §2 Token。
> **客户视图铁律**：列表 `key` 字段经 `MaskTokenKey` 脱敏（`len≤4` 全 `*`；`len≤8` 保留首尾 2 位；否则 `前4 + ********** + 后4`）。

## 2.1 创建/编辑/删除令牌

### POST /api/token/ — 创建令牌
- **功能 ID**：F-3001（+ F-3008 额度/过期、F-3009 模型白名单、F-3010 IP 白名单、F-3011 分组）
- **鉴权**：UserAuth
- **入参**（JSON）：
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | `name` | string | 是 | ≤50 字符（超→`MsgTokenNameTooLong`） |
  | `remain_quota` | int | 条件 | `unlimited_quota=false` 时校验 `[0, 10亿*QuotaPerUnit]` |
  | `unlimited_quota` | bool | 否 | true=跳过额度区间校验 |
  | `expired_time` | int64 | 否 | `-1`=永不过期（default） |
  | `model_limits_enabled` | bool | 否 | true=启用模型白名单 |
  | `model_limits` | string(JSON) | 否 | 允许模型列表 |
  | `allow_ips` | string | 否 | 按 `\n` 切分，空=不限 IP |
  | `group` | string | 否 | 调用分组（`auto` 时 `cross_group_retry` 生效） |
  | `cross_group_retry` | bool | 否 | 仅 `group=auto` 生效 |
- **出参（UserView）**：`data = { id, name, key（脱敏）, status, remain_quota, unlimited_quota, expired_time, group, model_limits_enabled, model_limits, allow_ips, created_time }`。
- **错误码**：`MsgTokenNameTooLong`；令牌数达上限「已达到最大令牌数量限制(N)」（`GetMaxUserTokens()`）；`MsgTokenQuotaNegative`（<0）；`MsgTokenQuotaExceedMax`（>10亿*QuotaPerUnit）。
- **幂等键**：`key`（uniqueIndex，GenerateKey 保证唯一）。

### PUT /api/token/ — 更新令牌（含 status_only）
- **功能 ID**：F-3006（+ F-3008/F-3009/F-3010/F-3011 字段）
- **鉴权**：UserAuth（仅本人令牌）
- **入参**（JSON）：`id` int 必填、`status_only` bool（true 仅改 `status`，不覆盖其他字段）、其余同创建可选字段。
- **出参（UserView）**：更新后令牌（key 脱敏）。
- **错误码**：启用已过期令牌（`ExpiredTime<=now 且≠-1`）→`MsgTokenExpiredCannotEnable`；启用额度耗尽且非无限→`MsgTokenExhaustedCannotEable`；name>50→`MsgTokenNameTooLong`；额度越界同创建。
- **幂等键**：`id`（覆盖式；`status_only` 模式幂等切换状态）。

### DELETE /api/token/:id — 删除单个令牌
- **功能 ID**：F-3007
- **鉴权**：UserAuth（仅本人令牌）
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`（软删 `DeletedAt`）。
- **幂等键**：`id`。

### POST /api/token/batch — 批量删除令牌
- **功能 ID**：F-3007
- **鉴权**：UserAuth（`BatchDeleteTokens` 带 userId 防越权）
- **入参**（JSON）：`ids` []int 必填。
- **出参**：`data = <删除count>`。
- **错误码**：`ids` 为空→`MsgInvalidParams`。

## 2.2 查询与搜索

### GET /api/token/ — 令牌列表（分页，key 脱敏）
- **功能 ID**：F-3002
- **鉴权**：UserAuth（`GetAllUserTokens` 按 user_id 过滤，Order id desc）
- **入参**（query）：`p` int、`page_size` int。
- **出参（UserView）**：`items[]` = `{ id, name, key（脱敏）, status, remain_quota, unlimited_quota, used_quota, expired_time, group, model_limits_enabled, model_limits, allow_ips, accessed_time, created_time }`；`total`/`page_size`。

### GET /api/token/search — 令牌关键词/前缀搜索
- **功能 ID**：F-3003
- **鉴权**：UserAuth（SearchRateLimit；仅本人令牌）
- **入参**（query）：`keyword` string、`p`/`page_size`。
- **出参（UserView）**：同列表（key 脱敏）。
- **错误码/约束**：`sanitizeLikePattern` 用 `!` ESCAPE；拒绝连续 `%%`、`%`>2 个、含 `%` 时去 % 后关键词 <2 字符；`limit>100` 截断为 100（`searchHardLimit`）。

## 2.3 取明文 Key（受控）

### POST /api/token/:id/key — 获取单个令牌明文 key
- **功能 ID**：F-3004（+ RBAC F-5006 受控取明文）
- **鉴权**：UserAuth（🔒 CriticalRateLimit + DisableCache + SecureVerification）
- **入参**（path）：`id` int。
- **出参（UserView）**：`data = "<完整明文 key>"`（`GetFullKey()`，未打码；越权访问他人令牌取不到）。
- **错误码**：高频→CriticalRateLimit；越权返回错误。

### POST /api/token/keys/batch — 批量获取明文 key（上限 100）
- **功能 ID**：F-3005
- **鉴权**：UserAuth（🔒 CriticalRateLimit + DisableCache；带 userId 过滤越权）
- **入参**（JSON）：`ids` []int 必填（≤100）。
- **出参（UserView）**：`data = { "<id>": "<key>" }`（仅本人令牌的完整 key）。
- **错误码**：`ids` 为空→`MsgInvalidParams`；`len(ids)>100`→`MsgBatchTooMany{Max:100}`。

## 2.4 用量查询（OpenAI 兼容，外部客户端）

### GET /api/usage/token — 令牌用量查询（credit_summary）
- **功能 ID**：F-3012
- **鉴权**：TokenAuthReadOnly（Bearer `sk-` key 只读鉴权）
- **入参**（Header）：`Authorization: Bearer sk-xxxx`。
- **出参（UserView）**：`data = { object: "credit_summary", total_granted（=RemainQuota+UsedQuota）, total_used（=UsedQuota）, total_available, expires_at（ExpiredTime=-1 时归零）, model_limits, model_limits_enabled }`。
- **错误码**：无 `Authorization` 或非 bearer 格式→401；key 无效→`MsgTokenGetInfoFailed`。

---

# 模块三：计费与额度（D6 计费与额度，F-2038~F-2048、F-6007~F-6009）

> 鉴权：充值/订阅/兑换为 UserAuth（self-scope）；倍率/兑换码生成/订阅计划/利润看板为 AdminAuth；公开价格页匿名；倍率计费/预扣/成本售价分离为系统内部（relay 链路，无独立端点）。
> 字段源：DATA-MODEL §1 User / §5 Log / §6 Redemption / §7 TopUp / §8 Subscription / §16 PublicModel / §19 ChannelModelCost。PRD：prd-billing BL-1~BL-9。
> **计费金额视图铁律**：`quota_sell`（售价）客户可见；`quota_cost`/`quota_profit`（成本/利润）**仅 admin/root**（AdminLogView），UserLogView 绝不含。

## 3.1 系统内部计费（无独立端点，relay 链路触发，登记契约用）

> 以下功能为 relay 计费链路内部行为，**不暴露独立 HTTP 端点**，但作为功能 ID 追溯锚点登记（计费正确性由 relay 中转端点承载，见模块五/Relay 网关）。

| 功能 ID | 名称 | PRD 块 | 触发点 | 关键字段 |
|---|---|---|---|---|
| F-2038 | 倍率计费（model_ratio×group_ratio×completion_ratio） | BL-6 | relay 结算 | `Log.quota`、`Log.prompt_tokens`、`Log.completion_tokens` |
| F-2039 | 缓存倍率计费（cache_ratio/exposed_cache） | BL-5/BL-6 | 缓存命中 token | Usage `cached_tokens`/`cached_creation_tokens` |
| F-2040 | 表达式/阶梯计费（billingexpr） | BL-5 | 配表达式+结算 | `RelayInfo.TieredBillingSnapshot` |
| F-2041 | 阶梯结算（TryTieredSettle 冻结快照） | BL-5 | 上游返回结算 | `snap.BillingMode==tiered_expr` |
| F-2042 | 预扣额度（pre-consume）多退少不补 | BL-2 | relay 转发前/后 | `RelayInfo.FinalPreConsumedQuota`、`Log.type=6 Refund` |
| F-6007 | 成本/售价分离 | BL-7 | relay 结算 | `Log.quota_sell`（A 售价）/`quota_cost`（channel×B 成本） |
| F-6008 | 分组折扣计费（基准价×分组折扣系数） | BL-8 | relay 结算 | `quota_sell = tokens × BasePriceRatio(A) × GetGroupRatio(UsingGroup)` |

> 配置侧端点见 3.5（倍率配置）；成本配置端点见模块五 CH-7（ChannelModelCost）；售价配置见模块四 ML-6（PublicModel）。

## 3.2 充值与余额（F-2044）

### POST /api/topup — 发起充值下单
- **功能 ID**：F-2044
- **鉴权**：UserAuth（过 payment_compliance C7 合规闸门）
- **入参**（JSON）：`amount` int64（充值额度，quota 单位）、`money` float64（支付金额）、`payment_method` string（`stripe`/`creem`/`waffo`/`waffo_pancake`/`balance`）、`payment_provider` string（`epay`/`stripe`/...）。
- **出参（UserView）**：`data = { trade_no, pay_url/pay_params, status: "pending" }`（`Other` 内部不下发）。
- **错误码**：合规未确认→拒绝下单引导确认声明。
- **状态变更**：创建 `TopUp` `Status=pending` 写 `trade_no`。
- **幂等键**：`trade_no`（unique）。

### POST /api/topup/callback/:provider — 支付回调入账（幂等）
- **功能 ID**：F-2044
- **鉴权**：匿名（支付渠道签名验签）
- **入参**：渠道回调体（含 `trade_no` + 签名）。
- **出参**：`{ "success": true }`（验签失败丢弃不入账；重复回调幂等返回成功）。
- **状态变更**：首次有效回调 `user.Quota += TopUp.Amount`、`TopUp.Status=success` 写 `CompleteTime`。
- **幂等键**：`trade_no`（已 success 则幂等不重复加额度）。

### GET /api/user/self — 余额查询
- **功能 ID**：F-2044（复用账号 1.3，返回 `quota`/`used_quota`）。

## 3.3 兑换码（F-2045）

### POST /api/redemption/ — 生成兑换码（单个/批量）
- **功能 ID**：F-2045
- **鉴权**：AdminAuth
- **入参**（JSON）：`name` string、`quota` int（面额，default 100）、`count` int（批量数量，`-:all` 仅请求用）、`expired_time` int64（0=不过期）。
- **出参（AdminView）**：`data = [ "<key>", ... ]`（生成的兑换码明文集合）。
- **幂等键**：`key`（char(32);uniqueIndex 每码唯一）。

### POST /api/user/topup — 用户兑换兑换码
- **功能 ID**：F-2045
- **鉴权**：UserAuth
- **入参**（JSON）：`key` string 必填（char(32) 兑换码）。
- **出参（UserView）**：`data = { quota: <入账额度> }`。
- **错误码**：码不存在/格式错→无效兑换码；`Status` 已使用→已被兑换不可重复；`ExpiredTime` 已到（非 0）→过期码拒绝。
- **状态变更**（同事务）：`user.Quota += Redemption.Quota`、`Redemption.Status=已使用`、写 `RedeemedTime`/`UsedUserId`。
- **幂等键**：`key`（一次性，已用拒绝；并发靠事务）。

### GET /api/redemption/ — 兑换码列表（管理端）
- **功能 ID**：F-2045
- **鉴权**：AdminAuth
- **入参**（query）：`p`/`page_size`。
- **出参（AdminView）**：`items[]` = `{ id, name, key, status, quota, created_time, redeemed_time, used_user_id, expired_time }`。

## 3.4 订阅（F-2046 / F-2047）

### GET /api/subscription/plans — 订阅套餐列表
- **功能 ID**：F-2046
- **鉴权**：匿名 / UserAuth（公开套餐目录）
- **入参**（query）：可选过滤。
- **出参（UserView）**：`items[]` = `{ id, title, subtitle, price_amount, currency, duration_unit, duration_value, custom_seconds, total_amount, quota_reset_period, max_purchase_per_user, sort_order, enabled }`（**裁掉** `upgrade_group`/`downgrade_group` 等内部分组快照非必要展示；`stripe_price_id` 等商品号不下发客户）。

### POST /api/subscription/plans — 创建订阅套餐（管理端）
- **功能 ID**：F-2046
- **鉴权**：AdminAuth
- **入参**（JSON）：SubscriptionPlan 全字段（`title`/`price_amount`/`currency`/`duration_unit`/`duration_value`/`total_amount`/`allow_balance_pay`/`allow_wallet_overflow`/`quota_reset_period`/`upgrade_group`/`downgrade_group` 等）。
- **出参（AdminView）**：创建后套餐。
- **幂等键**：`id`。

### POST /api/subscription/order — 下订阅订单
- **功能 ID**：F-2046
- **鉴权**：UserAuth（过 payment_compliance）
- **入参**（JSON）：`plan_id` int 必填、`payment_method`/`payment_provider` string。
- **出参（UserView）**：`data = { trade_no, pay_url, status: "pending" }`。
- **状态变更**：支付成功生成 `UserSubscription` `Status=active`。
- **幂等键**：`trade_no`（SubscriptionOrder unique）。

### GET /api/subscription/self — 本人订阅查询
- **功能 ID**：F-2046
- **鉴权**：UserAuth
- **入参**：无
- **出参（UserView）**：`items[]` = `{ id, plan_id, amount_total, amount_used, start_time, end_time, status（active/expired/cancelled）, allow_wallet_overflow }`（活跃判定 `status=active AND end_time>now`）。

### POST /api/subscription/:id/invalidate — 管理端作废订阅
- **功能 ID**：F-2046
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`
- **状态变更**：`Status=cancelled`、`end_time=now` 立即结束（`AdminInvalidateUserSubscription`）。
- **幂等键**：`id`（已 cancelled 幂等）。

> **F-2047 订阅预扣/退款记录（SubscriptionPreConsumeRecord）**：系统内部（relay 结算，`BillingSource=subscription` 从订阅项扣减记 `consumed`，失败转 `refunded`），无独立端点；登记锚点见 3.1 表外补充。

## 3.5 倍率配置（F-2043）与利润看板（F-6009）

### GET /api/ratio_config — 倍率配置查询（管理端）
- **功能 ID**：F-2043
- **鉴权**：AdminAuth
- **入参**：无
- **出参（AdminView）**：`data = { model_ratio: {...}, group_ratio: {...}, completion_ratio: {...}, cache_ratio: {...} }`。

### PUT /api/ratio_config — 倍率配置更新
- **功能 ID**：F-2043
- **鉴权**：AdminAuth（🔒 改倍率 = 高危，审计前后值留痕）
- **入参**（JSON）：`model_ratio`/`group_ratio`/`completion_ratio`/`cache_ratio` map（JSON 合法性校验）。
- **出参**：`{ "success": true }`（新请求按新倍率计费）。
- **幂等键**：配置 KV 覆盖式（幂等）。

### POST /api/ratio_sync — 倍率远端同步
- **功能 ID**：F-2043
- **鉴权**：AdminAuth
- **入参**（JSON）：`source_url` string、`preview` bool（同步前可预览）。
- **出参（AdminView）**：差异预览 / 同步结果计数。

### GET /api/profit/dashboard — 利润分析看板（按维度聚合）
- **功能 ID**：F-6009（PRD BL-9）
- **鉴权**：AdminAuth（成本/利润仅 admin/root 可见）
- **入参**（query）：`dimension` string（`model`/`channel`/`group`）、`start_timestamp`/`end_timestamp` int64。
- **出参（AdminView）**：`items[]` = `{ <dimension_key>, sum_quota_sell, sum_quota_cost, sum_quota_profit, profit_rate（=profit/sell）, cost_missing_count }`；按 `dimension` GROUP BY（model→`resolved_public_model(A)`；channel→`channel(channel_id)`+`channel_name`；group→`group`）；`quota_profit<0` 标亏损。
- **铁律**：UserLogView 永不可访问此端点；客户无 `quota_cost`/`quota_profit`/`actual_upstream_model(B)` 可见路径。

## 3.6 公开价格页（F-2048）

### GET /api/pricing — 公开模型价格页
- **功能 ID**：F-2048（+ F-3023/F-6008 公开站基准价）
- **鉴权**：匿名（HeaderNavModuleAuth(pricing) 控制可见性）
- **入参**（query）：可选 locale。
- **出参（UserView/公开）**：`data = { models: [ { model_name(A), base_price_ratio（基准价/对外暴露倍率）, quality_tier, display_name, supported_endpoint, cache_ratio（exposed_cache 标记时含） } ], group_ratio: {仅可用分组}, auto_groups, pricing_version }`。
- **铁律**：仅 `expose_ratio` 标记可见的模型；公开站展示**基准价（折扣=1 口径）**，**绝不含** `actual_upstream_model(B)`/成本/供应商；登录后按分组折扣展示折后价（F-6008）。

---

# 模块四：模型广场 + 模型元数据 + 模型同步 + 供应商元数据 + D8 模型与供应商

> 覆盖功能：F-3013~F-3021（元数据/供应商/同步，AdminAuth）、F-3022~F-3025（广场/可见模型，匿名/UserAuth）、F-6001~F-6004（对外模型分级/两层映射/权限全开，AdminAuth/RootAuth/UserAuth）。
> 字段源：DATA-MODEL（ModelMeta=`model/model_meta.go Model`、VendorMeta=`model/vendor_meta.go Vendor`、§16 PublicModel、§17 PlatformModelMapping、§18 UserModelAlias）。PRD：prd-model ML-1~ML-8。
> **铁律**：客户任何接口绝不返回 `upstream_name(B)`/`upstream_model(B)`/成本（B 不可见三道闸）；候选下拉只返公开模型 A 全集。

## 4.1 模型元数据（AdminAuth，F-3013~F-3017）

### GET /api/models — 模型元数据列表（分页 + 供应商计数）
- **功能 ID**：F-3013
- **鉴权**：AdminAuth
- **入参**（query）：`p`/`page_size`。
- **出参（AdminView）**：`items[]` = `{ id, model_name, status, description, icon, tags, vendor_id, endpoints, name_rule, sync_official, created_time, updated_time }`（enrich 后含端点/渠道/分组/计费类型计数）；`total`、`vendor_counts: {vendor_id: count}`。

### GET /api/models/search — 模型元数据搜索
- **功能 ID**：F-3014
- **鉴权**：AdminAuth
- **入参**（query）：`keyword` string、`vendor` int（供应商过滤）、`p`/`page_size`。
- **出参（AdminView）**：同列表。

### POST /api/models — 创建模型元数据
- **功能 ID**：F-3015（PRD ML-1）
- **鉴权**：AdminAuth
- **入参**（JSON）：`model_name` string 必填、`description`/`icon`/`tags`/`vendor_id`/`endpoints`/`name_rule`。
- **出参（AdminView）**：创建后模型。
- **错误码**：`model_name` 空→「模型名称不能为空」；`IsModelNameDuplicated(0,name)`→「模型名称已存在」。
- **幂等键**：`model_name`（uniqueIndex `uk_model_name_delete_at`）。
- **副作用**：Insert 后 `RefreshPricing()`。

### PUT /api/models — 更新模型元数据（含 status_only）
- **功能 ID**：F-3016（PRD ML-1）
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填、`status_only` bool（true 仅改 `status`，不动 `description/icon/tags/endpoints/vendor_id/name_rule`）、其余可选。
- **出参（AdminView）**：更新后模型。
- **错误码**：`id=0`→「缺少模型 ID」；`status_only=false` 重名（排除自身）→「模型名称已存在」。
- **幂等键**：`id`（覆盖式；status_only 防误清字段）。
- **副作用**：`RefreshPricing()`。

### DELETE /api/models/:id — 删除模型元数据
- **功能 ID**：F-3017
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true, "data": null }`
- **错误码**：非数字 id→参数错误。
- **幂等键**：`id`。
- **副作用**：`RefreshPricing()`。

## 4.2 供应商元数据（AdminAuth，F-3018）

### GET /api/vendors — 供应商列表
- **功能 ID**：F-3018
- **鉴权**：AdminAuth
- **入参**（query）：`p`/`page_size`。
- **出参（AdminView）**：`items[]` = `{ id, name, icon, status }`；`total`。

### GET /api/vendors/search — 供应商搜索
- **功能 ID**：F-3018
- **鉴权**：AdminAuth
- **入参**（query）：`keyword` string。
- **出参（AdminView）**：同列表。

### POST /api/vendors — 创建供应商
- **功能 ID**：F-3018
- **鉴权**：AdminAuth
- **入参**（JSON）：`name` string 必填、`icon`/`status`。
- **出参（AdminView）**：创建后供应商。
- **错误码**：`name` 空→「供应商名称不能为空」；重名→「供应商名称已存在」。
- **幂等键**：`name`（uniqueIndex `uk_vendor_name_delete_at`）。

### PUT /api/vendors — 更新供应商
- **功能 ID**：F-3018
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填、`name`/`icon`/`status`。
- **出参（AdminView）**：更新后供应商。
- **错误码**：缺 `id`→「缺少供应商 ID」；重名→「供应商名称已存在」。
- **幂等键**：`id`。

### DELETE /api/vendors/:id — 删除供应商
- **功能 ID**：F-3018
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`
- **幂等键**：`id`。

## 4.3 上游模型同步（AdminAuth，F-3019~F-3021，PRD ML-2）

### POST /api/models/sync/preview — 上游模型同步预览（只读差异）
- **功能 ID**：F-3020（PRD ML-2）
- **鉴权**：AdminAuth
- **入参**（JSON/query）：`locale` string（`en`/`zh-CN`/`zh-TW`/`ja`，非法回退默认 URL）。
- **出参（AdminView）**：`data = { diff: { to_create_models[], to_create_vendors[], to_update_models[], to_skip_models[] } }`（**不写库**；ETag 命中 304 走 bodyCache）。
- **错误码**：上游拉取失败→「获取上游模型失败」。

### POST /api/models/sync — 上游模型同步执行
- **功能 ID**：F-3019（PRD ML-2）
- **鉴权**：AdminAuth
- **入参**（JSON）：`overwrite` bool（按需覆盖字段）、勾选的模型集。
- **出参（AdminView）**：`data = { created_models, created_vendors, updated_models, skipped_models }`（计数）。
- **错误码**：上游拉取失败→「获取上游模型失败」。
- **副作用**：创建缺失模型/供应商；`sync_official=0` 的本地模型跳过覆盖计入 `skipped_models`。
- **幂等键**：`model_name`（创建按唯一键；重复不重建）。

### GET /api/models/missing — 缺失模型检测
- **功能 ID**：F-3021（PRD ML-2）
- **鉴权**：AdminAuth
- **入参**：无
- **出参（AdminView）**：`data = [ "<被渠道引用但无 ModelMeta 的模型名>", ... ]`。

## 4.4 模型广场 / 用户可见模型（匿名/UserAuth，F-3022~F-3025）

### GET /api/rankings — 模型排行榜（公开 period 快照）
- **功能 ID**：F-3022（PRD ML-5）
- **鉴权**：匿名/UserAuth（HeaderNavModuleAuth(rankings) 控制可见）
- **入参**（query）：`period` string（默认 `week`，合法 `week`/`month`）。
- **出参（UserView/公开）**：`data = <排行快照>`（聚合自用量，**不含成本/供应商**）。
- **错误码**：非法 `period`→400 + message。

### GET /api/pricing — 公开价格页
- **功能 ID**：F-3023（同 3.6，PRD ML-4）—— 见模块三 3.6。

### GET /api/models/dashboard — 模型广场（channelId→models 映射）
- **功能 ID**：F-3024（PRD ML-3）
- **鉴权**：UserAuth
- **入参**：无
- **出参（UserView）**：`data = { "<channelId>": ["<model_name>", ...] }`（`DashboardListModels`；模型名为对外名 A，不含 B）。

### GET /api/user/self/models — 用户可见模型列表（去重）
- **功能 ID**：F-3025（PRD ML-3）
- **鉴权**：UserAuth
- **入参**：无
- **出参（UserView）**：`data = [ "<model_name>", ... ]`（用户可用分组下启用模型去重合并；用户不存在报错；可用分组为空返回空数组）。
- **铁律（F-6004 模型权限全开）**：模型全开背景下，可见模型 = 对外目录上架全集，分组不控权限只控折扣。

## 4.5 对外模型商品目录（F-6001，PRD ML-6）

### GET /api/public_models — 对外模型商品目录列表
- **功能 ID**：F-6001
- **鉴权**：AdminAuth（管理视图）/ 匿名（公开目录经 /api/pricing 暴露子集）
- **入参**（query）：`p`/`page_size`、`enabled` 过滤。
- **出参（AdminView）**：`items[]` = `{ id, public_name(A), quality_tier（full/max/air）, base_price_ratio, use_price, base_price, enabled, display_name, sort_order, description, created_time, updated_time }`。
- **出参（UserView 公开目录）**：仅 `{ public_name(A), quality_tier, display_name, base_price_ratio（基准价）, sort_order }`（**绝不含 B/成本**）。

### POST /api/public_models — 创建对外模型（品质拆分独立定价）
- **功能 ID**：F-6001（PRD ML-6）
- **鉴权**：AdminAuth（客户无写入口）
- **入参**（JSON）：`public_name` string 必填（A）、`quality_tier` string（full/max/air）、`base_price_ratio` float64、`use_price` bool、`base_price` float64、`enabled` bool、`display_name`/`sort_order`/`description`。
- **出参（AdminView）**：创建后对外模型。
- **错误码**：`public_name` 空→「对外模型名不能为空」；重名→「对外模型名已存在」；同一 A 混不同品质→红线护栏「品质不同请拆独立对外模型」。
- **幂等键**：`public_name`（uniqueIndex `uk_public_name`）。
- **副作用**：保存 `base_price_ratio`/`base_price` 同步刷 `model_ratio`/`model_price` KV（ADR-BILL-01a）。

### PUT /api/public_models — 更新对外模型（含上下架）
- **功能 ID**：F-6001（+ F-6004 上架即全员可用）
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填、可选 `quality_tier`/`base_price_ratio`/`use_price`/`base_price`/`enabled`/`display_name`/`sort_order`。
- **出参（AdminView）**：更新后对外模型。
- **状态变更**：`enabled=false`→下架不进客户目录；`enabled=true`→进对外可见目录（F-6004 全员可用）。
- **幂等键**：`id`（覆盖式；同步刷 KV）。

### DELETE /api/public_models/:id — 删除对外模型（软删）
- **功能 ID**：F-6001
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`（软删 `DeletedAt`；移出对外全集）。
- **幂等键**：`id`。

## 4.6 超管底仓映射 A→B（F-6002，PRD ML-7，客户绝不可见）

### GET /api/platform_model_mappings — 底仓映射列表
- **功能 ID**：F-6002
- **鉴权**：AdminAuth/RootAuth（**无任何 user 路由读 B 接口** —— B 不可见三道闸之一）
- **入参**（query）：`p`/`page_size`。
- **出参（AdminView）**：`items[]` = `{ id, public_name(A), upstream_name(B), enabled, remark, created_time, updated_time }`。

### POST /api/platform_model_mappings — 创建 A→B 映射
- **功能 ID**：F-6002
- **鉴权**：AdminAuth/RootAuth
- **入参**（JSON）：`public_name` string 必填（A）、`upstream_name` string 必填（B，客户不可见）、`enabled` bool、`remark`。
- **出参（AdminView）**：创建后映射。
- **幂等键**：`public_name`（uniqueIndex `uk_public_name`，保证 A→B 1对1）。

### PUT /api/platform_model_mappings — 更新 A→B 映射
- **功能 ID**：F-6002
- **鉴权**：AdminAuth/RootAuth
- **入参**（JSON）：`id` int 必填、`upstream_name`/`enabled`/`remark`。
- **出参（AdminView）**：更新后映射。
- **幂等键**：`id`（覆盖式；写时刷内存缓存 key=public_name）。

### DELETE /api/platform_model_mappings/:id — 删除 A→B 映射
- **功能 ID**：F-6002
- **鉴权**：AdminAuth/RootAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`（删后 A 回落直通或 404）。
- **幂等键**：`id`。

## 4.7 客户层自助映射 C→A（F-6003，PRD ML-7，self-scope）

### GET /api/user/self/model_aliases — 本人/本组 C→A 映射列表
- **功能 ID**：F-6003
- **鉴权**：UserAuth（self-scope，强制 `scope_type=user AND scope_id=:caller`，或本人所属 group）
- **入参**（query）：可选 `scope_type`（user/group）。
- **出参（UserView）**：`items[]` = `{ id, scope_type, scope_id, alias(C), target(A), enabled, created_time }`（**target 仅 A，绝不含 B**）。

### POST /api/user/self/model_aliases — 创建 C→A 映射
- **功能 ID**：F-6003
- **鉴权**：UserAuth（self-scope）
- **入参**（JSON）：`scope_type` string（user/group）、`alias` string 必填（C）、`target` string 必填（A，**不校验白名单**，可硬输）、`enabled` bool。
- **出参（UserView）**：创建后映射。
- **约束**：写入强制 `scope_id=:caller_user_id`（user）或本人 group；禁跨 scope 写（越权拒绝）。优先级 user>group。
- **幂等键**：`(scope_type, scope_id, alias)`（uniqueIndex `uk_scope_alias`，同作用域 C 唯一）。

### PUT /api/user/self/model_aliases/:id — 更新 C→A 映射
- **功能 ID**：F-6003
- **鉴权**：UserAuth（self-scope）
- **入参**（path）：`id`；（JSON）`target`/`enabled`。
- **出参（UserView）**：更新后映射。
- **幂等键**：`id`（仅本人 scope）。

### DELETE /api/user/self/model_aliases/:id — 删除 C→A 映射
- **功能 ID**：F-6003
- **鉴权**：UserAuth（self-scope）
- **入参**（path）：`id`。
- **出参**：`{ "success": true }`
- **幂等键**：`id`。

### GET /api/user/self/model_aliases/candidates — 候选模型联想（公开 A 全集）
- **功能 ID**：F-6003（候选层 B 不可见闸）
- **鉴权**：UserAuth
- **入参**（query）：`keyword` string 可选。
- **出参（UserView）**：`data = [ "<public_name(A)>", ... ]`（来源 = PublicModel `Enabled=true` 全集，**绝不含任何 B**；落库不强制白名单）。

> **F-6004 模型权限全开**：无独立配置端点 —— 通过「取消分组→模型过滤步」实现，可用性唯一裁决 = `PublicModel.Enabled=true`（上架即全员可用）。登记锚点：见 4.4 `GET /api/user/self/models` 与 4.5 `PUT /api/public_models`（enabled）。key 级减法约束复用 `Token.ModelLimits`（语义收窄为减法，见模块二）。

---

# 模块五：渠道管理与上游路由 + 渠道运维 + D7 渠道与供应商

> 覆盖功能：F-2016~F-2027（渠道 CRUD/测试/余额/多Key/映射/探测/Ollama，AdminAuth）、F-2028/F-2034~F-2037（选渠/亲和/重试，系统内部）、F-2029~F-2033（亲和缓存配置，AdminAuth）、F-4045（Codex 渠道用量，AdminAuth）、F-6005（供应渠道池，AdminAuth/系统）、F-6006（供应商成本配置，AdminAuth/RootAuth）。
> 字段源：DATA-MODEL §3 Channel / §4 Ability / §19 ChannelModelCost。PRD：prd-channel CH-1~CH-7。
> **铁律**：渠道 `Key`、`ChannelModelCost.cost_ratio`、`upstream_model(B)` 仅 admin/root；客户无任何读路径。

## 5.1 渠道 CRUD/搜索/批量（F-2016，PRD CH-1）

### GET /api/channel/ — 渠道列表（分页）
- **功能 ID**：F-2016
- **鉴权**：AdminAuth
- **入参**（query）：`p`/`page_size`、可选 `group`/`type`/`tag`/`status` 过滤。
- **出参（AdminView）**：`items[]` = `{ id, type, name, status, weight, base_url, models, group, priority, auto_ban, balance, used_quota, response_time, test_time, model_mapping, status_code_mapping, tag, channel_info{is_multi_key,multi_key_size,multi_key_mode,multi_key_polling_index}, created_time }`（`key` 脱敏/不全量下发）。

### GET /api/channel/search — 渠道搜索
- **功能 ID**：F-2016
- **鉴权**：AdminAuth
- **入参**（query）：`keyword` string、`p`/`page_size`。
- **出参（AdminView）**：同列表。

### POST /api/channel/ — 创建渠道
- **功能 ID**：F-2016（+ F-2020 多Key、F-2021 模型映射、F-2025 覆写、F-2022 状态码映射）
- **鉴权**：AdminAuth
- **入参**（JSON）：
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | `type` | int | 是 | 渠道类型（provider 编号，至 57） |
  | `key` | string | 是 | 上游密钥（多 Key 模式见 channel_info） |
  | `models` | string | 是 | 支持模型列表 |
  | `name` | string | 否 | |
  | `group` | string | 否 | default `default` |
  | `priority` | int64 | 否 | default 0 |
  | `weight` | uint | 否 | default 0 |
  | `auto_ban` | int | 否 | default 1 |
  | `base_url` | string | 否 | 上游基址 |
  | `model_mapping` | string(JSON) | 否 | 空=不重定向（F-2021） |
  | `status_code_mapping` | string(JSON) | 否 | ≤1024 字符（F-2022） |
  | `param_override` | string(JSON) | 否 | 请求体覆写（F-2025） |
  | `header_override` | string(JSON) | 否 | 请求头覆写（F-2025） |
  | `tag` | string | 否 | |
  | `channel_info` | object | 否 | `is_multi_key`/`multi_key_size`/`multi_key_mode`（随机/轮询） |
- **出参（AdminView）**：创建后渠道（`Status=1` 默认启用）。
- **错误码**：缺 `type`/`key`/`models`→必填校验失败；`model_mapping` JSON 非法→映射解析失败拒绝保存。
- **幂等键**：`id`（创建生成）。

### PUT /api/channel/ — 编辑渠道
- **功能 ID**：F-2016（+ F-2020/F-2021/F-2022/F-2025）
- **鉴权**：AdminAuth
- **入参**（JSON）：`id` int 必填 + 同创建可选字段。
- **出参（AdminView）**：更新后渠道。
- **幂等键**：`id`（覆盖式）。

### DELETE /api/channel/:id — 删除渠道
- **功能 ID**：F-2016
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`
- **幂等键**：`id`。

### POST /api/channel/batch — 批量操作渠道
- **功能 ID**：F-2016
- **鉴权**：AdminAuth
- **入参**（JSON）：`ids` []int、`action` string（delete 等）。
- **出参**：`data = <受影响count>`。
- **幂等键**：`(ids, action)`。

## 5.2 渠道测试/余额/按 tag 启停（F-2017~F-2019）

### GET /api/channel/test/:id — 单渠道连通性测试
- **功能 ID**：F-2017
- **鉴权**：AdminAuth
- **入参**（path）：`id` int；（query）可选 `model`。
- **出参（AdminView）**：`data = { success, time（耗时ms）, message }`。
- **错误码**：MJ/Suno/Kling/Jimeng/DoubaoVideo/Vidu 七类异步渠道→「channel test is not supported」。
- **状态变更**：写 `test_time`/`response_time`。

### GET /api/channel/test — 全量渠道测试
- **功能 ID**：F-2017
- **鉴权**：AdminAuth
- **入参**：无
- **出参（AdminView）**：各渠道测试结果汇总。

### GET /api/channel/update_balance/:id — 单渠道余额更新
- **功能 ID**：F-2018
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参（AdminView）**：`data = { balance }`（最新余额，USD）。
- **状态变更**：写 `balance`。

### GET /api/channel/update_balance — 全量余额更新
- **功能 ID**：F-2018
- **鉴权**：AdminAuth
- **入参**：无
- **出参（AdminView）**：批量刷新结果（当前同步实现）。

### POST /api/channel/tag/enable — 按 tag 批量启用渠道
- **功能 ID**：F-2019
- **鉴权**：AdminAuth
- **入参**（JSON）：`tag` string 必填。
- **出参**：`{ "success": true }`
- **状态变更**：该 tag 下渠道 `Status=ChannelStatusEnabled(1)`。
- **幂等键**：`tag`（目标状态幂等）。

### POST /api/channel/tag/disable — 按 tag 批量禁用渠道
- **功能 ID**：F-2019
- **鉴权**：AdminAuth
- **入参**（JSON）：`tag` string 必填。
- **出参**：`{ "success": true }`
- **状态变更**：该 tag 下渠道 `Status=ChannelStatusManuallyDisabled(2)`（手动禁用，不自动恢复）。
- **幂等键**：`tag`。

## 5.3 上游模型探测 / Ollama 管理（F-2026 / F-2027）

### POST /api/channel/fetch_models/:id — 上游模型探测（fetch）
- **功能 ID**：F-2026
- **鉴权**：AdminAuth
- **入参**（path）：`id` int。
- **出参（AdminView）**：`data = [ "<上游模型名>", ... ]`（预览，不改 Models）。

### POST /api/channel/:id/upstream/apply — 探测结果应用到渠道
- **功能 ID**：F-2026
- **鉴权**：AdminAuth
- **入参**（path）：`id`；（JSON）勾选的模型集。
- **出参（AdminView）**：更新后渠道 `models`。
- **幂等键**：`id`（覆盖式应用）。

### POST /api/channel/:id/ollama/pull|delete — Ollama 模型拉取/删除
- **功能 ID**：F-2027
- **鉴权**：AdminAuth
- **入参**（path）：`id`；（JSON）`model` string。
- **出参（AdminView）**：`{ "success": true }`（仅 Ollama 类型渠道适用）。
- **幂等键**：`(channel_id, model, action)`。

### GET /api/channel/:id/ollama/version — Ollama 版本查询
- **功能 ID**：F-2027
- **鉴权**：AdminAuth
- **入参**（path）：`id`。
- **出参（AdminView）**：`data = { version }`。

## 5.4 亲和缓存配置与运维（F-2029~F-2033）

> F-2029（亲和键提取/粘连）、F-2034（命中跳过重试）为系统内部行为（PRD CH-4），无独立端点；配置/清理/统计端点如下。

### GET/PUT /api/option（channel_affinity_*） — 亲和缓存策略配置
- **功能 ID**：F-2031（PRD CH-4）
- **鉴权**：RootAuth（全站 Option，经选项接口）
- **入参**（JSON via /api/option）：`ChannelAffinitySetting`：`enabled` bool、`switch_on_success` bool、`max_entries` int、`default_ttl_seconds` int；规则级 `skip_retry_on_failure`/`ttl_seconds`。
- **出参**：`{ "success": true }`
- **幂等键**：Option KV 覆盖式。

### POST /api/channel_affinity_cache/clear — 清空亲和缓存
- **功能 ID**：F-2032
- **鉴权**：AdminAuth
- **入参**（JSON）：`all` bool 或 `rule_name` string（二选一必填）。
- **出参（AdminView）**：`data = { deleted: <条数> }`。
- **错误码**：二者都不传→400「缺少参数：rule_name，或使用 all=true 清空全部」。

### GET /api/log/channel_affinity_usage_cache — 亲和缓存用量统计
- **功能 ID**：F-2033（+ F-4014 用量统计查询）
- **鉴权**：AdminAuth
- **入参**（query）：`rule_name` string 必填、`key_fp` string 必填、`using_group` string 可选。
- **出参（AdminView）**：`data = <stats>`（命中渠道用量统计）。
- **错误码**：缺 `rule_name`→「missing param: rule_name」；缺 `key_fp`→「missing param: key_fp」。

## 5.5 选渠/重试系统内部（F-2028 / F-2035 / F-2036 / F-2037 / F-2023 / F-2024）

> 以下为 relay 选渠引擎内部行为（PRD CH-2/CH-3/CH-5），**无独立 HTTP 端点**，登记为功能 ID 追溯锚点（行为由 Relay 网关中转端点承载）。

| 功能 ID | 名称 | PRD 块 | 关键字段/行为 |
|---|---|---|---|
| F-2028 | 优先级+权重随机路由选渠道 | CH-2 | `Ability.priority`/`weight`，`priorityRetry` 分层 |
| F-2023 | 渠道自动禁用（AutoBan） | CH-3 | `Channel.status=ChannelStatusAutoDisabled(3)`，`NotifyRootUser` |
| F-2024 | 渠道自动恢复（AutoEnable） | CH-3 | 仅 `status==3` 自动恢复，手动禁用(2)永不自动恢复 |
| F-2035 | auto 分组逐组耗尽后切组重试 | CH-5 | `AutoGroupIndex`、`SetRetry(0)`、`common.RetryTimes` |
| F-2036 | 令牌级跨分组重试开关 | CH-5 | `Token.cross_group_retry`（仅 auto 生效） |
| F-2037 | 全局重试次数配置 RetryTimes | CH-5 | `common.RetryTimes`（RootAuth 经 Option 配置） |

> F-2037 的配置侧：`PUT /api/option`（`RetryTimes`，RootAuth）。

## 5.6 供应渠道池（F-6005，PRD CH-6）

> 渠道池本体复用 `Ability`/`Channel` 路由（CH-2/CH-3/CH-5），选渠为系统内部行为；管理侧通过给同一真实模型 B 挂多渠道（即在多个 Channel 的 `models` 含 B + 对应分组）实现，复用 5.1 渠道 CRUD。本节登记功能 ID 并给出查询型端点。

### GET /api/channel/pool — 按对外模型 A / 真实模型 B 查供应渠道池（管理端）
- **功能 ID**：F-6005
- **鉴权**：AdminAuth/RootAuth（含 B，客户不可见）
- **入参**（query）：`public_name`(A) string 或 `upstream_model`(B) string、`group` string。
- **出参（AdminView）**：`items[]` = `{ channel_id, channel_name, upstream_model(B), group, priority, weight, status, enabled }`（同一 A→B 下的同品质渠道池成员；按 priority 分层、weight 加权随机选渠）。
- **铁律**：同一 A→B 池成员必须同品质（红线 ADR-BILL-05）；品质不同拆独立 A→B（见模块四 ML-6/ML-7）。容灾切换后 `channel_id` 变，售价恒定（挂 A）、成本跟实际渠道（挂 channel×B，见 5.7）。

## 5.7 供应商成本配置（F-6006，PRD CH-7，ChannelModelCost）

### GET /api/channel_model_costs — 成本倍率列表（管理端）
- **功能 ID**：F-6006
- **鉴权**：AdminAuth/RootAuth（成本仅 admin/root；客户无任何读路径）
- **入参**（query）：可选 `channel_id` int、`upstream_model`(B) string、`p`/`page_size`。
- **出参（AdminView）**：`items[]` = `{ id, channel_id, upstream_model(B), cost_ratio, completion_cost_ratio, enabled, effective_time, source_unit_price, remark, created_time, updated_time }`（**绝不下发客户**）。

### POST /api/channel_model_costs — 创建/更新成本倍率（超管手填）
- **功能 ID**：F-6006
- **鉴权**：AdminAuth/RootAuth
- **入参**（JSON）：`channel_id` int 必填、`upstream_model` string 必填（B）、`cost_ratio` float64（口径同 model_ratio 输入 token）、`completion_cost_ratio` float64（0=回落 CostRatio×现网 CompletionRatio）、`enabled` bool、`remark`。
- **出参（AdminView）**：落库后成本行。
- **状态变更**：同 `(channel_id, upstream_model)` 已有行则更新，无则 Insert。
- **幂等键**：`(channel_id, upstream_model)`（uniqueIndex `uk_channel_model`，一渠道对一 B 只一条生效成本）。
- **副作用**：写时刷内存缓存 `[channelId][upstreamModel]→CostRatio`。
- **结算取值（系统内部）**：结算阶段用 `(实际选中 ChannelId, L2 后 B)` 主键取 `cost_ratio`；缺行或 `enabled=false`→`quota_cost=0` + `cost_missing` 告警不阻断计费。

### DELETE /api/channel_model_costs/:id — 删除成本倍率
- **功能 ID**：F-6006
- **鉴权**：AdminAuth/RootAuth
- **入参**（path）：`id` int。
- **出参**：`{ "success": true }`（删后该渠道×B 成本视为缺失记 0+告警）。
- **幂等键**：`id`。

## 5.8 Codex 渠道运维（F-4045）

### GET /api/channel/:id/codex/usage — Codex 渠道上游用量查询
- **功能 ID**：F-4045
- **鉴权**：AdminAuth（channelRoute）
- **入参**（path）：`id` int。
- **出参（AdminView）**：`data = <Codex wham 用量数据>`。
- **错误码**：非 Codex 类型→「channel type is not Codex」；multi-key 渠道→「multi-key channel is not supported」；access_token/account_id 缺失→对应报错。
- **副作用**：上游 401/403 且有 refresh_token 时自动 `RefreshCodexOAuthToken` 重试并回写渠道 key + `InitChannelCache`（仅 `status∈{1,3}` 渠道）。
- **幂等键**：`channel_id`（刷新回写幂等于最新 token）。

---

# 覆盖对账与统计

## 功能 ID → 端点对账（本批）

| 模块 | 功能 ID 范围 | 备注 |
|---|---|---|
| 账号与身份 D1 | F-1001~F-1054 | 含登录/注册/OAuth/2FA/Passkey/邀请划转；系统级 F-1012/F-1013/F-1017/F-1042/F-1043/F-1048/F-1050/F-1053/F-1054 为内部行为/中间件，附着于对应端点（注册/登录/OAuth/签到链路） |
| 令牌管理 | F-3001~F-3012 | 全 12 功能均有端点 |
| 计费与额度 D6 | F-2038~F-2048、F-6007~F-6009 | F-2038~F-2042/F-2047/F-6007/F-6008 为 relay 内部（3.1 登记表）；其余有独立端点 |
| 模型 D8 | F-3013~F-3025、F-6001~F-6004 | F-3026~F-3037（Relay 网关）属第二批；F-6004 无独立端点（登记锚点） |
| 渠道与上游 D7 | F-2016~F-2037、F-4045、F-6005、F-6006 | F-2028/F-2023/F-2024/F-2035~F-2037 为内部（5.5 登记表）；F-2029/F-2034 内部 |

## 视图 DTO 裁剪自检（产品铁律）
- **UserView 永不含**：`quota_cost`、`quota_profit`、`actual_upstream_model(B)`、`upstream_name(B)`、`upstream_model(B)`、`cost_ratio`、`channel_id`/`channel_name`（用量/利润维）、渠道 `key` 明文（除本人令牌受控取明文）。
- **AdminView 给全链**：C→A→B + 协议 + 售价/成本/利润 + channel_id（利润看板数据源 `GET /api/profit/dashboard`）。
- **B 不可见三道闸**：数据层（PlatformModelMapping/ChannelModelCost 无 user 读接口）/ 序列化层（UserLogView 无 B）/ 候选层（`/model_aliases/candidates` 只返公开 A 全集）。

> **跨批衔接说明**：Relay 网关端点（F-3026~F-3037、F-6010/F-6011 协议兼容与端到端经营链路）、日志与用量（F-4001~F-4014、F-6012）、运营运维/部署/Playground/公开站（F-3039~F-4044、F-5xxx）由第二批 subagent 追加。本批计费/选渠的「系统内部」功能 ID 已在 3.1 / 5.5 登记表锚定，其计费/路由正确性由第二批 Relay 网关端点承载验证。

---
---

# 第二批追加（S7 第2步·第二批）

> 本批追加模块群：⑥ Relay 网关 + D9 中继转发 ⑦ 异步任务中心 ⑧ 日志与用量 + D5 ⑨ 运营与运维 ⑩ 部署管理 ⑪ 公开站点 ⑫ 增长（签到/邀请分销/Telegram）⑬ Playground ⑭ 横切（NFR/Compliance/RBAC/Security/Observability/Scalability/亲和缓存/跨分组重试）。
>
> **功能 ID 权威**：`FUNCTION-LIST.csv`。注意与 PRD 局部编号的对账差异——`prd-relay.md` 内部用 F-3060/F-3061/F-3062 指代协议兼容/端到端链路/IR 五差异点，而 FUNCTION-LIST 权威 ID 为 **F-6010（协议兼容层）/ F-6011（端到端经营转发链路）**；`prd-usagelog.md` 内部用 F-4015/F-4016 指代 UL-9/UL-10 调用明细与两侧视图，而 FUNCTION-LIST 权威 ID 为 **F-6012（调用明细逐条展示）**，F-4015/F-4016 在 FUNCTION-LIST 中是系统初始化（运营运维 §10.1）。本批以 FUNCTION-LIST 为准并交叉标注 PRD 块号。
>
> **Relay 对外 schema 铁律**：所有 `/v1/*`、`/v1beta/*` 入口的对外 API schema = **标准 OpenAI / Anthropic / Gemini 原生格式**，客户端无需改动。内部 C→A→B 两层映射、选渠、协议头尾转换、双价记账对客户**完全不感知**；客户响应按入站协议（`inbound_protocol`）原样转回。下文 Relay 端点的「入参/出参」只描述对外协议契约，内部链路标注为系统行为并锚定 PRD RL-6/RL-7。

---

# 模块六：Relay 网关多协议中转（D11/D13 + D9 中继转发）

> 鉴权：所有中转端点 = TokenAuth（`Authorization: Bearer sk-<token.key>`；Gemini/Claude 原生另支持 `x-goog-api-key` / `x-api-key`+`anthropic-version` 头）。中间件链：`TokenAuth → ModelRequestRateLimit → Distribute`。
> 字段源：DATA-MODEL §2 Token / §3 Channel / §5 Log / §9 Task。PRD：prd-relay RL-1~RL-8。
> **统一中间件错误**（所有 Relay 端点共用，下文不逐一重复）：TokenAuth 失败→401；`Token.Status≠1`（禁用/过期/耗尽）→拒绝；`ModelRequestRateLimit` 超限→限流码；`Token.AllowIps` 白名单未命中→拒绝（F-3010）；`Token.ModelLimits` 开启且模型不在白名单→拒绝（F-3009）；BL-2 预扣 `userQuota<=0`→403 SkipRetry（不重试）；CH-2 无可用渠道→无可用渠道错误。
> **统一上游错误处置**（F-3037 / RL-3）：上游错误码经 `ShouldRetryByStatusCode` 判可重试（`<common.RetryTimes` 换渠道重试）/ 不可重试（跳过）；`Channel.AutoBan=1` 且命中禁用条件→`DisableChannel` 通知 root；错误日志经 `MaskSensitiveErrorWithStatusCode` 脱敏后 `RecordErrorLog`（`Log.Type=5 Error`）；错误响应按 `RelayFormat`（OpenAI/Claude/Gemini）分别构造结构。

## 6.1 OpenAI 兼容入口

### POST /v1/chat/completions、POST /v1/completions — OpenAI Chat/Completions 中转
- **功能 ID**：F-3026（+ F-3037 错误处置；+ F-6010 协议兼容、F-6011 端到端链路；PRD RL-1/RL-2/RL-3/RL-6/RL-7）
- **鉴权**：TokenAuth（`RelayFormatOpenAI`）
- **入参**（标准 OpenAI Chat schema，对外契约）：`model` string 必填（= 客户输入名 C，内部经 C→A→B 两层映射，客户不感知）、`messages[]`、`stream` bool、`temperature`/`top_p`/`max_tokens`/`tools`/`tool_choice` 等 OpenAI 原生字段。
- **出参**：标准 OpenAI `chat.completion` / `chat.completion.chunk`（流式 SSE）；`usage.{prompt_tokens,completion_tokens,total_tokens}`。**响应按入站协议 openai 原样返回，不暴露 B/渠道/成本**。
- **错误码**：见统一中间件错误 + 统一上游错误处置（OpenAI 错误结构）。
- **系统行为（不对客户暴露）**：① 协议识别 `inbound_protocol=openai`；② 两层映射 C→A→B（`requested_model`/`resolved_public_model`/`actual_upstream_model`）；③ key 级减法约束（A∈ModelLimits、inFmt∈EndpointLimits，默认全开）；④ 按 `Group×B` 选渠 + CH-5 容灾；⑤ 头尾协议对比：`inFmt==targetProto` 直通、否则 IR 转换（`protocol_converted`）；⑥ 双价记账 `quota_sell=BasePriceRatio(A)×GroupRatio×tokens`、`quota_cost=CostRatio(channel,B)×tokens`、`quota_profit`；⑦ 落 `Log`（`Type=2 Consume`）。
- **幂等键**：无（每次调用为一笔独立计费 Log；预扣→真实 token 结算「多退少不补」，失败全额返还由 BL-2 保证）。

### POST /v1/embeddings、POST {*}/embeddings（后缀匹配）— Embeddings 中转
- **功能 ID**：F-3027（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatEmbedding`）；`HasSuffix(embeddings)` 任意后缀路径均判 Embeddings；`/engines/:model/embeddings` 走 Gemini 检索分支。
- **入参**：标准 OpenAI Embeddings schema（`model`、`input` string|array、`encoding_format`）。
- **出参**：标准 `{ object:"list", data:[{embedding,index}], model, usage }`。
- **错误码**：见统一错误。

### POST /v1/images/generations、POST /v1/images/edits、POST /v1/edits — 图像生成/编辑中转
- **功能 ID**：F-3028（generations/edits）+ F-3057（legacy `/v1/edits` 独立 `RelayModeEdits`，区别于 `/images/edits`）（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatOpenAIImage`）
- **入参**：标准 OpenAI Images schema（`model`、`prompt`、`n`、`size`、`response_format` 等）。
- **出参**：标准 `{ created, data:[{url|b64_json}] }`。
- **错误码**：见统一错误。

### POST /v1/images/variations — 图像变体（未实现）
- **功能 ID**：F-3028（PRD RL-2）
- **鉴权**：TokenAuth
- **入参/出参**：返回 `RelayNotImplemented`（明确未实现响应，**非 500**）。
- **错误码**：未实现响应（not implemented）。

### POST /v1/audio/speech、/v1/audio/transcriptions、/v1/audio/translations — 音频中转
- **功能 ID**：F-3029（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatOpenAIAudio`；speech=tts、transcriptions/translations=whisper）
- **入参**：标准 OpenAI Audio schema（speech：`model`/`input`/`voice`/`response_format`；transcription：multipart `file`/`model`）。
- **出参**：音频二进制流（speech）或转录 JSON/文本（transcription/translation）。
- **错误码**：见统一错误。

### POST /v1/moderations — 内容审核中转
- **功能 ID**：F-3030（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatOpenAI`，`RelayModeModerations`）
- **入参**：标准 `{ model, input }`。
- **出参**：标准 moderation `{ id, model, results[] }`。

### POST /v1/rerank — 重排序中转
- **功能 ID**：F-3031（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatRerank`，jina/cohere adapter）
- **入参**：`{ model, query, documents[], top_n }`（rerank 通用 schema）。
- **出参**：`{ results:[{index,relevance_score}], usage }`。

### POST /v1/responses、POST /v1/responses/compact — Responses 中转（含紧凑变体）
- **功能 ID**：F-3032（PRD RL-2）
- **鉴权**：TokenAuth；`/v1/responses/compact` **必须先于** `/v1/responses` 前缀匹配（`RelayFormatOpenAIResponsesCompaction`），否则误判（顺序敏感）。
- **入参/出参**：OpenAI Responses API schema。
- **错误码**：见统一错误。

### GET /v1/realtime — Realtime WebSocket 实时中转
- **功能 ID**：F-3033（PRD RL-2）
- **鉴权**：TokenAuth（`RelayFormatOpenAIRealtime`，WebSocket 升级而非普通 HTTP）
- **入参/出参**：WebSocket 双向消息（OpenAI Realtime 协议）；错误走 realtime 专用分支（与 HTTP 不同）。

## 6.2 厂商原生协议入口

### POST /v1/messages — Anthropic Claude 原生中转
- **功能 ID**：F-3035（+ F-6010 协议兼容、F-6011 端到端链路；PRD RL-2/RL-4/RL-6/RL-7/RL-8）
- **鉴权**：TokenAuth（`RelayFormatClaude`；可用 `x-api-key`+`anthropic-version` 头识别 Anthropic 模型列表）
- **入参**（标准 Anthropic Messages schema）：`model`（=C）、`system`（顶层）、`messages[]`、`max_tokens`、`tools[]{name,input_schema}`、`stream`。
- **出参**：标准 Anthropic `message` / SSE 多 event（`message_start`/`content_block_delta`/`message_delta`/`message_stop`）；`usage.{input_tokens,output_tokens}`。**响应按入站协议 claude 原样返回**。
- **错误码**：Claude 错误结构（含 `StatusCode`），与 OpenAI 格式不同。
- **系统行为**：入站 claude → 若目标供应商协议非 claude 则经 IR（`ChatIR`）转换（F-6010/RL-6），覆盖 OpenAI⇄Anthropic 五差异点 D1~D5（system 位置/content 结构/tools/stop_reason/usage 字段名，PRD RL-8）；`protocol_converted` 落库。

### POST /v1beta/models/*path — Gemini 原生协议中转
- **功能 ID**：F-3034（PRD RL-2/RL-4）
- **鉴权**：TokenAuth（`RelayFormatGemini`；`x-goog-api-key` 或 query key 时 `/v1/models` 也走 Gemini 检索）
- **入参/出参**：Gemini 原生 `generateContent`/`streamGenerateContent` schema（经 gemini adapter 双向转换）。
- **错误码**：Gemini 错误结构。

### 厂商原生协议适配（37 provider adapter）— 系统内部，无独立外部端点
- **功能 ID**：F-3036（PRD RL-4）
- **说明**：`relay/channel/` 下 37 个 adapter（tencent/xunfei/zhipu/ali/baidu/aws/vertex/cohere/mistral/codex 等，渠道类型常量至 57），把统一入参转厂商原生协议、反解析响应。adapter 缺失→该渠道类型无法中转。流式能力由 `streamSupportedChannels` 决定（不在集合则降级非流式）。**附着于 6.1/6.2 的中转端点**，由 `Channel.Type` 选定，无独立 HTTP 路径。

## 6.3 协议兼容层与端到端经营链路（系统内部，附着于 6.1/6.2 端点）

### F-6010 协议兼容层（OpenAI ⇄ Anthropic，PRD RL-6/RL-8）— 横切系统能力，无独立端点
- **功能 ID**：F-6010
- **说明**：客户用 OpenAI（`/v1/chat/completions`）或 Anthropic（`/v1/messages`）格式打**同一把 key** 都接（本期仅这两协议，注册表预留 Gemini/embedding/图像/语音扩展位，未命中回落 per-channel 直转不阻断现网）。「只看头尾」：在「确定 B + 选中 Channel」后做唯一一次协议决策——`inFmt==targetProto` 直通（仅替换 model 为 B），不等才经 IR（`ChatIR`/`ChatRespIR`/`StreamState`）双向转换。新增协议=实现 `ProtocolAdapter`+`init()` 注册一行，管线零改动。Log 落 `inbound_protocol`/`upstream_protocol`/`protocol_converted`。**对客户透明，无独立 HTTP 端点**，由 6.1/6.2 中转端点承载。

### F-6011 端到端经营转发链路（C→A→B 9 步，PRD RL-7）— 横切系统能力，无独立端点
- **功能 ID**：F-6011
- **说明**：一笔请求固定链路：① 协议识别 → ② 两层映射 C→(L1 UserModelAlias)→A→(L2 PlatformModelMapping)→B（带环检测+最大跳数，B 客户不可见）→ ③ key 级减法约束校验 → ④ 渠道池路由（`Group×B` 查 Ability，优先级/权重选渠 + CH-5 容灾，售价恒定）→ ⑤ 协议头尾转换（复用 F-6010）→ ⑥ 扣客户 `quota_sell=BasePriceRatio(A)×GroupRatio×tokens`（恒定）→ ⑦ 记成本 `quota_cost=CostRatio(实际渠道,B)×tokens`（不乘折扣，缺成本行→`quota_cost=0`/`quota_profit=quota_sell`+`Other.cost_missing`告警）→ ⑧ 响应按 inFmt 转回 → ⑨ 落 Log（C/A/B+实际供应商+协议三字段+售价/成本/利润，`quota_profit<0`=亏损告警）。映射不掺路由（Ability 路由维 `model`=B）。**对客户透明，无独立 HTTP 端点**。

## 6.4 视频内容代理（D9 中继转发）

### GET /videos/:task_id/content — 生成视频内容网关代理下发
- **功能 ID**：F-4046（PRD RL-5）
- **鉴权**：UserAuth（按用户上下文取 task，`GetByTaskId` 带 userID，self-scope）
- **入参**（path）：`task_id` string。
- **出参**：视频内容流式回写（`io.Copy`），响应头 `Cache-Control: max-age=86400`；`data:` URL 走 base64 解码直出。
- **错误码**：task 不存在/非本人→404；`Task.Status≠TaskStatusSuccess`（未完成）→当前状态错误；SSRF 校验（`ValidateURLWithFetchSetting`）未过→403。
- **系统行为**：按 `channel.Type` 解析上游 URL（Gemini 用 `Task.PrivateData.Key`+`x-goog-api-key`、Vertex、OpenAI/Sora 拼 `/v1/videos/.../content`）；`PrivateData`（含 Key，`json:"-"`）不下发。
- **幂等键**：`task_id`（只读代理，幂等）。

---

# 模块七：异步任务中心（D5 — 视频/音乐/MJ）

> 鉴权：提交端点（`/mj/submit/*`、`/suno/submit/*`、视频提交）+ 用户查询 `/api/task/self` = UserAuth（TokenAuth 经 relay 链路）；管理端全量 `/api/task` = AdminAuth；上游回调 = 平台回调签名/内部。
> 字段源：DATA-MODEL §9 Task（`TaskStatus` 枚举 `NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN`）。PRD：prd-asynctask AT-1~AT-5 + 补充（UNKNOWN/超时退款）。
> **DTO 裁剪铁律**：用户视图任务对象 `Omit(channel_id)` 不泄露渠道，`PrivateData`（含 key/BillingContext/ResultURL，`json:"-"`）**永不序列化**；管理视图含 `channel_id`。
> **状态机/CAS/退款为系统内部行为**（AT-1/AT-3/AT-4），无独立 HTTP 端点，登记于 7.4。

## 7.1 任务提交（relay 链路，UserAuth）

### POST /mj/submit/:action — Midjourney 任务提交
- **功能 ID**：F-2005（+ F-2001 状态机落库；PRD AT-5/AT-1）
- **鉴权**：UserAuth（TokenAuth via relay）
- **入参**（path）：`action` ∈ `imagine/change/blend/describe/modal/shorten/action/edits/video`（不支持值→参数错误）；（JSON body）按 action 派生：`prompt`（imagine）、`taskId`+`customId`（change/action 带 Buttons）、`base64Array`（blend）等 MJ 原生 schema。
- **出参**：`data = { task_id }`（落库 `Action`、`Properties` 含 Prompt/Buttons，初始 `Status=NOT_START`、`Progress="0%"`，移交 AT-1 轮询）。
- **错误码**：不支持的 `:action`→参数错误（不写 MJ 表）；Midjourney/MidjourneyPlus 渠道连通性测试→`channel test is not supported`（不影响提交，见渠道运维）。
- **幂等键**：`task_id`（第三方任务 id，uniqueIndex `varchar(191)`）。

### POST /suno/submit/:action — Suno 音乐/歌词任务提交
- **功能 ID**：F-2007（PRD AT-1）
- **鉴权**：UserAuth
- **入参**（path）：`action` ∈ `MUSIC/LYRICS`（受 `SunoModel2Action` 约束：`suno_music→MUSIC`、`suno_lyrics→LYRICS`）；（body）Suno 生成参数。
- **出参**：`data = { task_id }`（`Platform=suno`）。
- **幂等键**：`task_id`。

### POST （视频生成端点，`controller/task_video.go`）— 视频生成任务提交
- **功能 ID**：F-2008（PRD AT-1/AT-4）
- **鉴权**：UserAuth
- **入参**：按 provider adapter（Sora/Kling/Vidu/Hailuo/Jimeng/Doubao/Vertex/Gemini）的视频生成 schema（`model`、`prompt`、时长/分辨率等）。
- **出参**：`data = { task_id, status }`（status 经 `ToVideoStatus` 映射 `Queued/InProgress/Completed/Failed/Unknown`）。
- **副作用**：Gemini/VertexAi 渠道 `InitTask` 时把 ApiKey 写入 `PrivateData.Key`（`json:"-"` 禁返用户）。
- **幂等键**：`task_id`。

## 7.2 任务查询

### GET /api/task/self — 用户任务列表（分页，self-scope）
- **功能 ID**：F-2003（PRD AT-2）
- **鉴权**：UserAuth（强制 `Where(user_id=本人)` 隔离）
- **入参**（query）：`task_id`/`action`/`status`/`platform` 过滤、`start_timestamp`/`end_timestamp` 时间区间、`p`/`page_size`（`Order id desc`）。
- **出参（用户视图）**：`items[]` = `{ id, task_id, platform, action, status, fail_reason, submit_time, start_time, finish_time, progress, properties, data, created_at, updated_at }`（**`Omit(channel_id)`**、**无 `PrivateData`**）；`total`/`page`/`page_size`。
- **错误码**：UserAuth 失败→401；越权他人 user_id 不可达（强制隔离）。

### GET /mj/task/:id/fetch — MJ 单任务拉取
- **功能 ID**：F-2006（PRD AT-2/AT-5）
- **鉴权**：UserAuth（`GetByTaskId` 以 `user_id+task_id` 双条件，仅本人）
- **入参**（path）：`id`（task_id）。
- **出参（用户视图）**：单任务 `{ task_id, status, progress, image_url, video_url, buttons }`（仅 `SUCCESS` 展示产物，`GetResultURL` 优先 `PrivateData.ResultURL` 旧数据回退 `FailReason`；`PrivateData.Key` 不出现）。
- **错误码**：他人 task_id→不可拉取（404/空）。

### GET /mj/task/list-by-condition — MJ 任务按条件批量拉取
- **功能 ID**：F-2006（PRD AT-2）
- **鉴权**：UserAuth（`GetByTaskIds` 按 id 集合）
- **入参**（JSON）：`ids[]`（task_id 集合）。
- **出参（用户视图）**：任务数组（同上裁剪规则）。

### GET /suno/fetch、GET /suno/fetch/:id — Suno 任务列表/单任务拉取
- **功能 ID**：F-2007（PRD AT-1）
- **鉴权**：UserAuth
- **入参**：`/suno/fetch`（批量条件）或 `/suno/fetch/:id`（path id）。
- **出参（用户视图）**：Suno 任务状态/产物（同裁剪规则）。

### GET /api/task — 管理端全量任务列表（分页）
- **功能 ID**：F-2004（PRD AT-3）
- **鉴权**：AdminAuth（无 user_id 限制，跨用户）
- **入参**（query）：`channel_id`/`user_id`/`user_ids`/`platform`/`action`/`status` 过滤、时间区间、`p`/`page_size`。
- **出参（管理视图）**：`items[]` = 任务全字段 **含 `channel_id`**（区别用户自助接口）；`total`（`TaskCountAllTasks`）。
- **错误码**：普通用户访问→403（越权拒绝）。

## 7.3 任务取消/重试（视频/MJ，附着 relay）

> 取消/重试在 new-api 中由各 provider adapter 端点承载（如 MJ `action`、视频 provider 的 cancel）；任务状态推进统一经状态机 CAS（7.4）。无统一独立 REST 路径，归属对应提交端点的 action 分支与状态机（F-2001/F-2002）。

## 7.4 任务状态机 / CAS / 退款结算（系统内部，无独立端点）

> 以下为异步任务系统内部行为（PRD AT-1/AT-3/AT-4 + 补充），**无独立 HTTP 端点**，登记为功能 ID 追溯锚点（行为由 7.1 提交端点 + 后台轮询/超时扫描承载）。

| 功能 ID | 名称 | PRD 块 | 关键字段/行为 |
|---|---|---|---|
| F-2001 | 通用异步任务记录写入与状态机流转 | AT-1 | `InitTask`（`Status=NOT_START`/`Progress="0%"`），合法顺序 `NOT_START→SUBMITTED→QUEUED→IN_PROGRESS→SUCCESS/FAILURE/UNKNOWN` |
| F-2002 | 任务状态 CAS 条件更新（防并发覆盖） | AT-1 | `UpdateWithStatus(fromStatus,…)` 以 status 为 WHERE 守卫，`RowsAffected>0` 才赢；终态不可被普通 bulk update 覆盖 |
| F-2009 | 任务异步退款与差额结算（计费上下文重算） | AT-4 | 读 `PrivateData.BillingContext` 重算；`PerCallBilling=true` 跳过差额；`BillingSource=subscription`（订阅退款）/`wallet`（令牌额度退回）；退款经 CAS |
| F-2010 | 任务结果产物展示（ImageUrl/VideoUrl/Buttons） | AT-4 | `GetResultURL` 优先 `PrivateData.ResultURL` 旧数据回退 `FailReason`；仅 `SUCCESS` 展示 |
| F-2011 | 扫描超时未完成任务 | AT-3 | `GetTimedOutUnfinishedTasks`（`progress!=100% AND status NOT IN(FAILURE,SUCCESS) AND submit_time<cutoff`）；超时退款经 CAS |

> **UNKNOWN 终态退款补漏**（AT-1 补充，归属 F-2001 计费侧）：单次 `UNKNOWN` 不就地退款、留未完成集；持续超 `TaskTimeoutMinutes` 由 `sweepTimedOutTasks` per-task CAS 强制 `FAILURE`+`Progress=100%`，非 legacy（`SubmitTime>=1740182400`）且 `Quota!=0` 调 `RefundTaskQuota` 全额退（资金来源→令牌额度→写 `Log.Type=6 Refund`）；legacy 任务不退款（人工处理）；`Quota==0` 不写 Refund 日志；CAS `won=false` 防重复退款。

---

# 模块八：日志与用量（D4 + D5 用量与日志）

> 鉴权：管理端 `/api/log/`、`/api/data/`、`/api/log/stat`、亲和缓存统计 = AdminAuth；用户自助 `/api/log/self`、`/api/log/self/stat`、`/api/data/self` = UserAuth；按 key 查日志 `/api/log/token` = TokenAuthReadOnly + CriticalRateLimit + DisableCache。
> 字段源：DATA-MODEL §5 Log（Log Type：`0=Unknown 1=Topup 2=Consume 3=Manage 4=System 5=Error 6=Refund 7=Login`）。PRD：prd-usagelog UL-1~UL-10。
> **两侧视图 DTO 铁律（核心闸门，PRD UL-10 / F-6012）**：序列化层按角色裁剪，**不靠前端隐藏**。

## 8.0 视图 DTO 定义（F-6012 / UL-9·UL-10，权威）

### UserLogView（用户侧，仅本人 + 裁剪）
- **功能 ID**：F-6012（PRD UL-9/UL-10；FUNCTION-LIST 权威 F-6012「调用明细逐条展示」，注册用户侧）
- **字段**：`{ id, created_at, type, content, token_name, model_name(=C), requested_model(C), resolved_public_model(A), group, prompt_tokens, completion_tokens, quota(=quota_sell 实付), use_time, is_stream, ip, user_agent, request_id, inbound_protocol, upstream_protocol, protocol_converted }`。
- **结构级剔除（绝不出现）**：`actual_upstream_model(B)`、`quota_cost`、`quota_profit`、`channel`/`channel_name`（供应商维）、`upstream_request_id`（上游诊断维）、`Other` 内部 JSON。
- **范围闸门**：强制 `Log.UserId=当前用户`，跨用户维度忽略。

### AdminLogView（管理侧，全局 + 全字段）
- **功能 ID**：F-6012（PRD UL-10，超管侧）
- **字段**：UserLogView 全部 + **`actual_upstream_model(B)`** + **`channel`/`channel_name`（实际供应商）** + **`quota_sell`/`quota_cost`/`quota_profit`**（利润看板数据源）+ `upstream_request_id` + `Other`（含 `cost_missing`/`channel_redirect` 诊断）。
- **范围**：全站所有人（无 user_id 强制限制）。

## 8.1 日志列表查询

### GET /api/log/ — 管理端全量日志查询（分页）
- **功能 ID**：F-4001（PRD UL-1）
- **鉴权**：AdminAuth
- **入参**（query）：`type` int（0=全部）、`start_timestamp`/`end_timestamp`、`username`/`token_name`/`model_name`/`channel`/`group`/`request_id`/`upstream_request_id` 过滤、`p`/`page_size`。
- **出参**：`items[]` = **AdminLogView**；`total`/`page`/`page_size`。
- **错误码**：普通用户→403。

### GET /api/log/self — 用户自助日志查询（仅本人，分页）
- **功能 ID**：F-4002（PRD UL-1）
- **鉴权**：UserAuth（`userId=c.GetInt("id")` 强制本人）
- **入参**（query）：`type`、时间区间、`token_name`/`model_name`/`group` 过滤、`p`/`page_size`（**无 username/channel 维度**）。
- **出参**：`items[]` = **UserLogView**；`total`。
- **错误码**：越权查他人 user_id 不可达。

### GET /api/log/token — 按令牌明文 key 查消费日志
- **功能 ID**：F-4003（PRD UL-4）
- **鉴权**：TokenAuthReadOnly + CORS + CriticalRateLimit
- **入参**：`Authorization: Bearer sk-<key>`（中间件置 `token_id`）。
- **出参**：该 `token_id` 的日志数组（**UserLogView** 裁剪口径）。
- **错误码**：`token_id==0`→「无效的令牌」；高频→CriticalRateLimit。

### 调用明细逐条展示（行/展开详情，附着于 8.1 列表端点）
- **功能 ID**：F-6012（PRD UL-9）
- **说明**：在 UL-1 列表之上的逐条明细行——主行：时间/类型/模型（用户 C→A、管理 C→A→B）/分组/计费模式（按倍率 or 按次，源 PublicModel `UsePrice`）/Tokens/费用（=`quota_sell`）/IP/UA/耗时/状态（区分 `Type=2`/`Type=5 Error`）；展开详情补 `request_id`/`upstream_request_id`/协议三字段/完整三段链，管理侧另含 B+供应商+成本/利润。**复用 8.1 列表端点 + 8.0 视图 DTO，无新增端点/无新增 DB 列**。

## 8.2 日志统计

### GET /api/log/stat — 管理端日志统计（quota/rpm/tpm）
- **功能 ID**：F-4004（PRD UL-2）
- **鉴权**：AdminAuth
- **入参**（query）：同 `/api/log/` 过滤项。
- **出参**：`data = { quota, rpm, tpm }`（仅统计 `Type=2 Consume`）。

### GET /api/log/self/stat — 用户自助日志统计
- **功能 ID**：F-4005（PRD UL-2）
- **鉴权**：UserAuth（`username` 来自上下文，不可伪造）
- **入参**（query）：时间区间等。
- **出参**：`data = { quota, rpm, tpm }`（当前用户消费日志聚合）。

### DELETE /api/log/ — 清理历史日志（分批，每批上限 100）
- **功能 ID**：F-4006（PRD UL-7）
- **鉴权**：AdminAuth
- **入参**（query/JSON）：`target_timestamp` int64 必填（删早于该时间的日志）。
- **出参**：`data = <删除 count>`。
- **错误码**：`target_timestamp==0`→「target timestamp is required」。
- **幂等键**：`target_timestamp`（重复执行删剩余早于该时间的日志，趋于幂等）。

## 8.3 配额按日数据（D5 用量）

### GET /api/data/ — 管理端配额按日数据（可按 username）
- **功能 ID**：F-4007（PRD UL-3）
- **鉴权**：AdminAuth
- **入参**（query）：`start_timestamp`/`end_timestamp`、`username`（空=全站）。
- **出参**：`items[]` = 按日聚合 `QuotaData`（管理视图）。

### GET /api/data/users — 配额按用户聚合（全站用户维度）
- **功能 ID**：F-4008（PRD UL-3）
- **鉴权**：AdminAuth
- **入参**（query）：时间区间。
- **出参**：按用户分组配额聚合数据。

### GET /api/data/self — 用户自助配额按日数据（跨度上限 1 月）
- **功能 ID**：F-4009（PRD UL-3）
- **鉴权**：UserAuth（`userId` 来自上下文防越权）
- **入参**（query）：`start_timestamp`/`end_timestamp`（`end-start>2592000`→拒绝）。
- **出参**：本人每日 `QuotaData`。
- **错误码**：跨度超 1 月（2592000s）→「时间跨度不能超过 1 个月」。

### GET /api/log/channel_affinity_usage_cache — 渠道亲和缓存用量统计
- **功能 ID**：F-4014（PRD UL-8；与模块五 F-2033 同接口，本处登记日志维归属）
- **鉴权**：AdminAuth
- **入参**（query）：`rule_name` 必填、`key_fp` 必填、`using_group` 可选。
- **出参**：`data = <亲和缓存命中渠道用量统计>`（管理视图）。

## 8.4 审计日志记录（系统内部埋点，无独立查询端点）

> 审计写入为埋点行为，查询复用 8.1（`type=3 Manage`/`type=7 Login`）。登记功能 ID。

| 功能 ID | 名称 | PRD 块 | 关键字段/行为 |
|---|---|---|---|
| F-4011 | 管理/高危操作审计（`Type=3 Manage`） | UL-5 | `RecordOperationAuditLog`，`content` 由 action 模板英文渲染，含 `admin_id/admin_username/admin_role/auth_method` |
| F-4012 | 用户安全敏感操作审计（passkey 绑定/解绑等） | UL-5 | `adminInfo=nil`，归属操作用户自身 userId，可在本人日志可见 |
| F-4013 | 登录审计（`Type=7 Login`） | UL-5 | `RecordLoginLog` 记 `username/content/ip/action/params` |

---

# 模块九：运营与运维（运营运维 / 系统设置 / 选项配置 / 性能监控 / 合规确认）

> 鉴权：系统初始化 `GET/POST /api/setup` = 匿名（首次部署引导）；选项配置 `/api/option/*`、性能监控 `/api/performance/*`、合规确认/迁移 = **RootAuth**（全站配置/运维指标仅 root）；性能指标汇总/单模型 `perf-metrics/*` = `HeaderNavModulePublicOrUserAuth("pricing")`（公开或登录可见）；Uptime-Kuma 状态、协议/隐私/公告/关于内容 = 匿名公开读；i18n 多语言解析 = 上下文中间件。
> 字段源：DATA-MODEL §10 Checkin、§15 Option、Setup / PerformanceStats / LegalSetting / ConsoleSetting / NotificationSetting / AutoGroup（PRD prd-operation + setting/*）。FUNCTION-LIST 权威：F-4015~F-4037（运营与运维 module）。
> **注意**：FUNCTION-LIST 中 F-4015/F-4016 = 系统初始化（非 PRD usagelog 局部 F-4015/F-4016 调用明细，后者权威 ID 为 F-6012，已见模块八 §8.0）。

## 9.1 系统初始化（首次部署引导，匿名）

### GET /api/setup — 系统初始化状态查询
- **功能 ID**：F-4015
- **鉴权**：匿名
- **入参**：无
- **出参**：已初始化→`data = { status: true }`（直接结束）；未初始化→`data = { status: false, root_init: <bool RootUserExists()>, database_type: "mysql"|"postgres"|"sqlite" }`。
- **错误码**：无（探测接口恒成功）。
- **副作用**：无（读 `constant.Setup`）。

### POST /api/setup — 系统初始化提交（创建 root + 模式开关）
- **功能 ID**：F-4016
- **鉴权**：匿名（anonymousRequestBodyLimit）
- **入参**（JSON）：
  | 字段 | 类型 | 必填 | 说明 |
  |---|---|---|---|
  | `username` | string | 是 | `validate:max=12` |
  | `password` | string | 是 | `validate:min=8`，需与 `confirm_password` 一致 |
  | `confirm_password` | string | 是 | 两次一致校验 |
  | `SelfUseModeEnabled` | bool | 否 | 自用模式开关 |
  | `DemoSiteEnabled` | bool | 否 | 演示模式开关 |
- **出参**：`{ "success": true }`
- **错误码**：已初始化→「系统已经初始化完成」；`username>12`/`password<8`/两次不一致分别报错。
- **副作用**：创建 `Role=RoleRootUser`、`Quota=100000000` 用户；写 `SelfUseModeEnabled`/`DemoSiteEnabled` Option；`Create(model.Setup)`；`constant.Setup=true`。
- **幂等键**：`constant.Setup`（已初始化拒绝重复，幂等失败）。

## 9.2 全站选项配置（RootAuth）

### GET /api/option/ — 全站选项列表查询（剔除敏感键）
- **功能 ID**：F-4017
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`data[]` = 选项 `{ key, value }` 数组，**自动剔除以 `Token`/`Secret`/`Key`/`secret`/`api_key` 结尾的键**；额外追加 `key="CompletionRatioMeta"` 项（`buildCompletionRatioMetaValue`）。
- **错误码**：非 root→403。
- **副作用**：无（遍历 `common.OptionMap`，持 RLock）。

### PUT /api/option/ — 全站选项更新（逐键校验 + 依赖检查）
- **功能 ID**：F-4018（+ F-4032 分组限流、F-4033 敏感词、F-4034 自动分组、F-4035 主题，均经本接口分支落库）
- **鉴权**：RootAuth
- **入参**（JSON）：`{ key: string, value: string }`（单键更新）。
- **出参**：`{ "success": true }`
- **错误码**：启用 OAuth（GitHubOAuthEnabled/discord.enabled/oidc.enabled）但未填 `ClientId`→「请先填入...」；`theme.frontend` 非 `default`/`classic`→「无效的主题值，可选值：default（新版前端）、classic（经典前端）」（F-4035）；`ModelRequestRateLimitGroup` 非法 JSON 或非法 `[count,duration]`→校验失败（F-4032）；`GroupRatio`/`ImageRatio`/`QuotaForInviter` 正值需先确认支付合规；`payment_setting.compliance_*` **禁止经本接口改**（走 §9.5）。
- **幂等键**：`key`（覆盖式写入，幂等）。
- **副作用**：`recordManageAudit("option.update",{key})` 写审计（仅记 key 不记 value，F-4011/UL-5）。

### POST /api/option/migrate_console_setting — 控制台旧设置迁移
- **功能 ID**：F-4031
- **鉴权**：RootAuth
- **入参**：无（读 `AllOption`）。
- **出参**：`{ "success": true }`
- **副作用**：`ApiInfo`/`FAQ` 截断 50 条转 `console_setting.api_info`/`faq`；`UptimeKumaUrl`+`Slug` 同时存在才迁为 `uptime_kuma_groups`；删除旧键（ApiInfo/Announcements/FAQ/UptimeKumaUrl/UptimeKumaSlug）并 `InitOptionMap`。
- **幂等键**：旧键存在性（旧键已删则重复执行无副作用，趋于幂等）。
- **状态变更**：旧→新 Option 结构迁移。

## 9.3 性能监控与运维（RootAuth）

### GET /api/performance/stats — 性能统计查询（缓存/内存/磁盘/GC/Goroutine）
- **功能 ID**：F-4019
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`data = { cache_stats, memory_stats:{ alloc, sys, num_gc, num_goroutine }, disk_cache_info, disk_space_info:{ used_percent, ... }, performance_config:{ cpu/内存/磁盘阈值 } }`（实时 `runtime.ReadMemStats`）。
- **错误码**：非 root→403。

### DELETE /api/performance/disk_cache — 清理不活跃磁盘缓存
- **功能 ID**：F-4020
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`{ "success": true, "message": "不活跃的磁盘缓存已清理" }`
- **副作用**：`CleanupOldDiskCacheFiles(10min)`（10 分钟阈值保护进行中请求）。
- **幂等键**：无（按时间阈值删，重复执行趋于幂等）。

### POST /api/performance/gc — 强制执行 GC
- **功能 ID**：F-4021
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`{ "success": true, "message": "GC 已执行" }`
- **幂等键**：无（幂等运维动作）。

### POST /api/performance/reset_stats — 重置性能统计计数
- **功能 ID**：F-4021
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`{ "success": true, "message": "统计信息已重置" }`
- **幂等键**：无（幂等运维动作）。

### GET /api/performance/logs — 日志文件列表查询（仅 oneapi-*.log）
- **功能 ID**：F-4022
- **鉴权**：RootAuth
- **入参**：无
- **出参**：`LogDir` 未配置→`data = { enabled: false }`；否则 `data = { enabled: true, files[], file_count, total_size, oldest_time, newest_time }`（仅前缀 `oneapi-`+后缀 `.log`，按名降序）。

### DELETE /api/performance/logs — 清理过期日志文件
- **功能 ID**：F-4023
- **鉴权**：RootAuth
- **入参**（JSON/query）：`mode` string 必填（`by_count` 保留最新 N 个 / `by_days` 删早于 N 天）、`value` int 必填（>=1）。
- **出参**：`data = { deleted_count, freed_bytes, failed_files[] }`；部分失败→`success=false`+`failed_files`。
- **错误码**：`mode` 非法→「invalid mode」；`value<1`→「invalid value」；`LogDir` 未配置→报错。
- **副作用**：跳过 `GetCurrentLogPath()` 当前活动日志不删。
- **幂等键**：`mode`+`value`（重复执行删剩余符合条件文件，趋于幂等）。

### GET /api/perf-metrics/summary — 性能指标汇总（公开/登录，按 hours + 活动分组）
- **功能 ID**：F-4024
- **鉴权**：`HeaderNavModulePublicOrUserAuth("pricing")`（公开或登录可见）
- **入参**（query）：`hours` int（默认 24）。
- **出参**：`data` = 近 N 小时各活动分组（`GetGroupRatioCopy` 键 + `auto`）性能指标汇总（`QuerySummaryAll`）。
- **错误码**：查询失败→500+message。

### GET /api/perf-metrics — 单模型性能指标（model 必填）
- **功能 ID**：F-4025
- **鉴权**：`HeaderNavModulePublicOrUserAuth("pricing")`
- **入参**（query）：`model` string 必填、`group` string 可选、`hours` int（默认 24）。
- **出参**：`data = { groups:{ <活动分组|auto>: 指标 } }`（`filterActiveGroups` 仅保留活动分组）。
- **错误码**：`model` 为空→400「model is required」。

## 9.4 公开状态 / 内容读取（匿名公开）

### GET /api/uptime/status — Uptime-Kuma 状态接入（聚合可用率/状态）
- **功能 ID**：F-4026
- **鉴权**：匿名
- **入参**：无
- **出参**：`data[]` = 各监控组 `{ group, monitors:[{ name, uptime, status }] }`（按 `monitorID` 匹配 `uptimeList`(_24 后缀)与 `heartbeatList`）。
- **错误码**：未配置 groups→空数组；单组拉取失败→该组空 monitors（容错）。
- **说明**：`errgroup` 并发拉 `/api/status-page/{slug}` 与 `/heartbeat/{slug}`；httpTimeout 10s / requestTimeout 30s。

### GET /api/user_agreement — 用户协议公开内容读取
- **功能 ID**：F-4027
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = "<UserAgreement 文本>"`（`GetLegalSettings().UserAgreement`；未设置为空串）。

### GET /api/privacy_policy — 隐私政策公开内容读取
- **功能 ID**：F-4028
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = "<PrivacyPolicy 文本>"`（未设置为空串）。

### GET /api/notice — 公告内容公开读取
- **功能 ID**：F-4029
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = OptionMap["Notice"]`（持 `OptionMapRWMutex.RLock`）。

### GET /api/about — 关于页内容公开读取
- **功能 ID**：F-4029
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = OptionMap["About"]`。

### GET /api/home_page_content — 首页自定义内容公开读取
- **功能 ID**：F-4029
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = OptionMap["HomePageContent"]`。

## 9.5 支付合规确认（RootAuth + 仅会话）

### POST /api/option/payment_compliance — 支付合规声明确认
- **功能 ID**：F-4030（+ Compliance F-5021 同意闸门关联）
- **鉴权**：RootAuth，**且仅 dashboard 会话**（拒绝 access_token / API token）
- **入参**（JSON）：`confirmed` bool 必填（须为 `true`）。
- **出参**：`data = { terms_version, confirmed_by }`
- **错误码**：`use_access_token`→403「requires dashboard session」；`confirmed=false`→「请确认合规声明」。
- **副作用**：写 5 项 `payment_setting.compliance_*`（`compliance_confirmed`/`terms_version`/`confirmed_at`/`confirmed_by`/`confirmed_ip`）。
- **幂等键**：`compliance_confirmed`+`terms_version`（同版本重复确认覆盖式幂等）。
- **状态变更**：未确认→已确认（解锁支付/邀请正额度等合规闸门）。

## 9.6 运维侧横切配置（无独立 REST 端点，附着于 §9.2 PUT /api/option/）

> 以下功能 ID 的「配置写入」复用 §9.2 `PUT /api/option/`（带分支校验），「读取/运行时生效」由中间件/setting 包承载，无独立 CRUD 端点。登记功能 ID（覆盖校验须数到）。

| 功能 ID | 名称 | 承载点 | 关键校验/行为 |
|---|---|---|---|
| F-4032 | 模型请求限流分组配置 `[count,duration]` | `PUT /api/option/`（case `ModelRequestRateLimitGroup`）→ `CheckModelRequestRateLimitGroup`；运行时 `GetGroupRateLimit` | 非法 JSON/非法 `[count,duration]`→校验失败；`ModelRequestRateLimit` 中间件按分组限流 |
| F-4033 | 敏感词内容过滤配置（开关+换行词表） | `PUT /api/option/` → `setting/sensitive.go`；运行时 prompt 检查 | `ShouldCheckPromptContent`=两开关与运算；默认含 `test_sensitive` |
| F-4034 | 用户可用分组与自动分组配置 | `PUT /api/option/` → `UpdateAutoGroupsByJsonString`/`DefaultUseAutoGroup`；`GetStatus` 暴露 `default_use_auto_group` | `ContainsAutoGroup`/`GetAutoGroups` 运行时生效 |
| F-4035 | 前端主题切换配置 `default`/`classic` | `PUT /api/option/`（case `theme.frontend`）；`GetStatus.theme` 暴露 | 非 `default`/`classic`→「无效的主题值」；前端依 `GetStatus.theme` 渲染 |
| F-4036 | 后端多语言解析与切换（zh-CN/zh-TW/en） | i18n 中间件 `GetLangFromContext`（用户设置优先，回退 `Accept-Language`） | `T/Translate` 渲染；语言来自上下文不可伪造；**横切中间件，无独立端点** |
| F-4037 | 额度预警通知配置（email/webhook/bark + 阈值） | 用户侧复用 `PUT /api/user/self/setting`（模块一 §1.3，`UserSetting.warning_*`） | `warning_threshold` 非正数→「预警阈值必须为正数」；webhook 需 `webhook_url`+`webhook_secret`(Bearer) |

---

# 模块十：部署管理（io.net 企业部署集成，AdminAuth）

> 鉴权：全部 `/api/deployments/*` = **AdminAuth**（企业部署仅管理员）。部分接口用 `EnterpriseClient`（需 api_key），部分用普通 `Client`（locations/logs）。
> 字段源：DATA-MODEL Deployment / Container / Option（`model_deployment.ionet.*`）。FUNCTION-LIST 权威：F-3039~F-3056（部署管理 module，18 功能）。provider 固定 `io.net`。
> **铁律**：实际计费以 io.net 为准（价格预估仅参考）；`api_key` 等密钥经 §9.2 敏感键剔除规则不下发列表。

## 10.1 集成开关与连接

### GET /api/deployments/settings — io.net 集成设置查询
- **功能 ID**：F-3039
- **鉴权**：AdminAuth
- **入参**：无
- **出参**：`data = { provider: "io.net", enabled: <OptionMap[ionet.enabled]=="true">, configured: <api_key 非空>, can_connect: <enabled && configured> }`
- **错误码**：未启用或 key 缺失时 `getIoAPIKey` 报错（仅连接类接口触发）。

### POST /api/deployments/test — io.net 连接测试（校验 api_key）
- **功能 ID**：F-3040
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**（JSON）：`api_key` string 可选（为空回退 stored key）。
- **出参**：`data = { hardware_count, total_available }`
- **错误码**：key 为空且无 stored key→「api_key is required」；key 无效→`ionet.APIError.Message`（空时回退「failed to validate api key」）。

## 10.2 部署 CRUD 与运维

### GET /api/deployments/ — 部署列表查询（分页 + 状态计数）
- **功能 ID**：F-3041
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**（query）：`p`/`page_size`。
- **出参**：`items[]` = `{ deployment_id, name, status, time_remaining, hardware_info, compute_minutes_remaining }`（`SortBy=created_at desc`）；`status_counts = { all, running, completed, failed, ... }`；`total`/`page`/`page_size`。

### GET /api/deployments/search — 部署搜索（状态过滤 + 名称关键词）
- **功能 ID**：F-3042
- **鉴权**：AdminAuth
- **入参**（query）：`status`（透传上游过滤）、`keyword`（本地 `Name` 小写包含过滤）。
- **出参**：`items[]`（过滤后）；`keyword` 非空时 `total=len(filtered)`。

### GET /api/deployments/:id — 部署详情查询
- **功能 ID**：F-3043
- **鉴权**：AdminAuth
- **入参**（path）：`id` 必填。
- **出参**：`data = { total_gpus, total_containers, compute_minutes_served, gpus_per_container, amount_paid, completed_percent, locations[], container_config }`
- **错误码**：`id` 为空→「deployment ID is required」。

### POST /api/deployments/ — 创建部署（下发容器）
- **功能 ID**：F-3044
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**（JSON）：`ionet.DeploymentRequest`（硬件/GPU/容器配置，对齐 io.net SDK）。
- **出参**：`data = { deployment_id, status }`，message「Deployment created successfully」。
- **错误码**：请求体非法→绑定错误。
- **幂等键**：无（io.net 侧生成 `deployment_id`；调用方可借集群名 §10.3 预检避免重名）。
- **状态变更**：创建部署集群（下发上游）。

### PUT /api/deployments/:id — 部署更新（配置变更）
- **功能 ID**：F-3045
- **鉴权**：AdminAuth
- **入参**（path `id` 必填；JSON `ionet.UpdateDeploymentRequest`）。
- **出参**：`data = { status, deployment_id }`
- **错误码**：`id` 必填；请求体非法→错误。
- **幂等键**：`deployment_id`（覆盖式更新）。

### PUT /api/deployments/:id/name — 部署重命名（名称可用性预检）
- **功能 ID**：F-3046
- **鉴权**：AdminAuth
- **入参**（path `id`；JSON `name` string 必填）。
- **出参**：`{ "success": true }`
- **错误码**：`name` 为空→「deployment name cannot be empty」；名称不可用→「name is not available」；预检失败→「failed to check name availability」。
- **幂等键**：`deployment_id`+`name`。

### POST /api/deployments/:id/extend — 部署续期（延长计算时长）
- **功能 ID**：F-3047
- **鉴权**：AdminAuth
- **入参**（path `id` 必填；JSON `ionet.ExtendDurationRequest`）。
- **出参**：`data = { compute_minutes_remaining, time_remaining }`（重构后详情）。
- **错误码**：`id` 必填；请求体非法→错误。
- **状态变更**：增加 `compute_minutes_remaining`。

### DELETE /api/deployments/:id — 删除/终止部署
- **功能 ID**：F-3048
- **鉴权**：AdminAuth
- **入参**（path `id` 必填）。
- **出参**：`data = { status, deployment_id }`，message「Deployment termination requested successfully」。
- **错误码**：`id` 必填。
- **状态变更**：部署→终止（请求上游）。

## 10.3 部署元数据与价格

### GET /api/deployments/hardware-types — 硬件类型查询（含总可用量）
- **功能 ID**：F-3049
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**：无
- **出参**：`data = { hardware_types[], total: <类型数>, total_available: <总可用副本数> }`

### GET /api/deployments/locations — 部署地域查询
- **功能 ID**：F-3050
- **鉴权**：AdminAuth（普通 Client）
- **入参**：无
- **出参**：`data = { locations[], total }`（上游 `total=0` 时回退 `len(Locations)`）。

### GET /api/deployments/replicas — 可用副本查询（按硬件 ID + GPU 数）
- **功能 ID**：F-3051
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**（query）：`hardware_id` int 必填（>0）、`gpu_count` int（默认 1）。
- **出参**：`data` = 指定硬件与 GPU 数下可用副本。
- **错误码**：`hardware_id` 缺失→「hardware_id parameter is required」；非法或<=0→「invalid hardware_id parameter」；`gpu_count` 非正→回退 1。

### POST /api/deployments/price-estimation — 部署价格预估
- **功能 ID**：F-3052
- **鉴权**：AdminAuth
- **入参**（JSON）：`ionet.PriceEstimationRequest`。
- **出参**：`data` = 上游 `priceResp`。
- **错误码**：请求体非法→绑定错误。

### GET /api/deployments/check-name — 集群名称可用性查询
- **功能 ID**：F-3053
- **鉴权**：AdminAuth
- **入参**（query）：`name` string 必填。
- **出参**：`data = { available: bool, name }`
- **错误码**：`name` 为空→「name parameter is required」。

## 10.4 容器查询

### GET /api/deployments/:id/containers — 部署容器列表（含事件）
- **功能 ID**：F-3054
- **鉴权**：AdminAuth（EnterpriseClient）
- **入参**（path `id` 必填）。
- **出参**：`data = { containers:[{ container_id, device_id, uptime_percent, public_url, events:[{ time, message }] }], total }`（无容器→空数组+`total=0`）。
- **错误码**：`id` 必填。

### GET /api/deployments/:id/containers/:container_id — 容器详情查询
- **功能 ID**：F-3055
- **鉴权**：AdminAuth
- **入参**（path `id` + `container_id` 必填）。
- **出参**：`data = { events, public_url, uptime_percent, ... }`
- **错误码**：`id`/`container_id` 任一为空→对应必填错误；`details=nil`→「container details not found」。

### GET /api/deployments/:id/containers/:container_id/logs — 容器日志查询（分页/过滤）
- **功能 ID**：F-3056
- **鉴权**：AdminAuth（普通 Client）
- **入参**（path `id`；query `container_id` 必填、`limit` int（默认 100，上限 1000）、`level`/`stream`/`cursor`/`follow`、`start_time`/`end_time`（RFC3339））。
- **出参**：`data` = 容器原始日志。
- **错误码**：`container_id` 缺失→「container_id parameter is required」；`limit>1000`→截断 1000；非法时间字符串→忽略。

---

# 模块十一：公开站点（营销站 / 协议隐私页 / 公开 CTA，匿名为主）

> 鉴权：营销首页状态聚合 `GET /api/status`、协议/隐私公开页内容 = 匿名公开；控制台/模型广场/API Keys 主入口 = 跳转动态域（需登录后访问）。
> 字段源：Option / LegalSetting / ConsoleSetting（`GetStatus` 聚合）；WEBSITE-COVERAGE 镜像证据。FUNCTION-LIST 权威：F-4039~F-4044（公开站点 module）。
> **说明**：F-4040/F-4043/F-4044 多为前端交互/导航入口（无独立后端端点），登记功能 ID + 数据源；F-4041/F-4042 为前端路由页，正文数据复用 §9.4 内容读取端点。

## 11.1 营销首页状态聚合

### GET /api/status — 营销首页公开状态聚合
- **功能 ID**：F-4039
- **鉴权**：匿名
- **入参**：无
- **出参**：`data = { system_name, logo, footer_html, register_enabled, email_verification, github_oauth, discord_oauth, oidc_enabled, linuxdo_oauth, wechat_login, telegram_oauth, turnstile_check, theme, checkin_enabled, user_agreement_enabled, privacy_policy_enabled, default_use_auto_group, ... }`（按 `ApiInfoEnabled`/`AnnouncementsEnabled`/`FAQEnabled` 条件注入可选内容）。
- **错误码**：无（敏感配置不在此暴露）。

## 11.2 公开协议 / 隐私页（前端路由 + 内容端点复用）

### GET /agreement — 用户协议公开页（前端路由）
- **功能 ID**：F-4041
- **鉴权**：匿名
- **入参**：无（前端页面）
- **出参**：渲染 `UserAgreement` 正文；正文数据源 = `GET /api/user_agreement`（§9.4 F-4027）；`GetStatus.user_agreement_enabled=false` 时入口隐藏。
- **说明**：前端路由页，无独立后端业务端点（复用 §9.4 内容读取）。

### GET /privacy — 隐私政策公开页（前端路由）
- **功能 ID**：F-4042
- **鉴权**：匿名
- **入参**：无（前端页面）
- **出参**：渲染 `PrivacyPolicy` 正文；数据源 = `GET /api/privacy_policy`（§9.4 F-4028）；`GetStatus.privacy_policy_enabled=false` 时入口隐藏。
- **说明**：前端路由页，无独立后端业务端点。

## 11.3 公开交互入口（前端，无独立后端端点，登记功能 ID）

| 功能 ID | 名称 | 鉴权 | 承载点 / 数据源 | 说明 |
|---|---|---|---|---|
| F-4040 | 首页对话框 Playground 入口（placeholder「问点什么…」） | 匿名→引导登录 | 前端首页对话框；发送动作导向 `POST /pg/chat/completions`（§13 F-4038，需 UserAuth） | 前端入口，未登录点发送引导登录；无独立后端端点 |
| F-4043 | 公开主题/语言切换控件（右上「切换主题」「切换语言」） | 匿名 | 主题源 `GetStatus.theme`；语言源 i18n（F-4036） | 前端控件，主题最终值受 root `theme.frontend` 约束；无独立后端端点 |
| F-4044 | 控制台/模型广场/API Keys 主入口（动态域，证据 GAP） | 需登录 | 导航跳转 app 子域（SEMANTIC-GAPS SG-001 未捕获动态截图） | 标注证据 GAP；具体页面以 repo 控制台能力为权威；无独立后端端点 |

---

# 模块十二：增长（D2 签到 / D3 邀请返利分销 / D4 Telegram 登录 Bot）

> 鉴权：签到/签到状态 = UserAuth（签到带 TurnstileCheck）；邀请码/统计/划转 = UserAuth（self-scope）；Telegram 登录 = 匿名（HMAC 校验）；Telegram 绑定 = UserAuth；签到/Telegram 系统级（防重/HMAC/唯一性）为内部行为附着对应端点。
> 字段源：DATA-MODEL §10 Checkin + CheckinSetting、§1 User（aff_*/telegram_id）、Session。FUNCTION-LIST 权威：D2 签到 F-1046~F-1050；D3 邀请返利分销 F-1039~F-1045（邀请码/归因/奖励/划转/统计 **已见模块一**，本处仅登记系统级 + 交叉引用）；D4 Telegram F-1051~F-1054（登录/绑定 **已见模块一 §1.5**，本处仅登记系统级 + 交叉引用）。

## 12.1 每日签到（D2）

### POST /api/user/checkin — 每日签到领取随机额度
- **功能 ID**：F-1046（+ F-1048 防重复、F-1050 人机校验，系统级附着）
- **鉴权**：UserAuth（前置中间件 `TurnstileCheck`，F-1050）
- **入参**：无
- **出参**：`data = { quota_awarded: <[MinQuota,MaxQuota] 随机值>, quota: <用户更新后额度> }`
- **错误码**：`CheckinSetting.Enabled=false`→签到未启用错误；当日已签→「今日已签到」（F-1048 唯一约束 `idx_user_checkin_date` 拦截）；Turnstile 校验失败→拒绝（F-1050）。
- **副作用**：写 `Checkin{ user_id, checkin_date:YYYY-MM-DD, quota_awarded }`；`user.Quota += quota_awarded`（事务/CAS）。
- **幂等键**：`(user_id, checkin_date)`（复合唯一索引，当日重复签到被拒，幂等失败）。
- **状态变更**：当日未签→已签。

### GET /api/user/checkin — 签到状态与本月记录查询
- **功能 ID**：F-1047
- **鉴权**：UserAuth
- **入参**（query）：`month` string 可选（默认本月）。
- **出参**：`data = { total_quota, total_checkins, checkin_count, checked_in_today, records:[{ checkin_date, quota_awarded }] }`（脱敏 `CheckinRecord`，**不含 id/user_id**）。
- **错误码**：`CheckinSetting.Enabled=false`→签到未启用错误。

### 签到系统级行为（无独立端点，附着 §12.1 签到端点）

| 功能 ID | 名称 | 承载点 | 关键行为 |
|---|---|---|---|
| F-1048 | 签到防重复（唯一约束+事务） | 附着 `POST /api/user/checkin` | `(user_id,checkin_date)` 唯一索引；MySQL/PG 走事务、SQLite 顺序+回滚保证额度与记录原子；并发重复被唯一约束拦截 |
| F-1049 | 签到开关与额度区间配置 | 复用 §9.2 `PUT /api/option/`（`checkin_setting`） | `Enabled`(默认 false)/`MinQuota`(默认 1000)/`MaxQuota`(默认 10000)；关闭后签到接口返回未启用；调整 Min/Max 改奖励区间。管理端配置，AdminAuth/RootAuth |
| F-1050 | 签到人机校验 | 中间件 `TurnstileCheck` 附着 `POST /api/user/checkin` | 开启 Turnstile 时未通过校验请求被拦截，不进入签到逻辑 |

## 12.2 邀请返利分销（D3）— 交叉引用模块一

> 邀请端点已在**模块一**完整定义，本处登记交叉引用 + 系统级行为，覆盖校验须数到。

| 功能 ID | 名称 | 端点 / 承载点 | 鉴权 | 备注 |
|---|---|---|---|---|
| F-1039 | 获取个人邀请码 | `GET /api/user/self/aff`（模块一 §1.3） | UserAuth | AffCode 为空生成唯一 4 位码；幂等键 `aff_code` |
| F-1040 | 邮箱注册邀请码归因 | 附着 `POST /api/user/register`（模块一 §1.1） | 匿名 | 带有效 `aff_code` 写 `inviter_id`；无效→0 |
| F-1041 | OAuth 注册邀请码归因 | 附着 `GET /api/oauth/state?aff=xxx` + 回调（模块一 §1.5） | 匿名 | 仅 session 存在 aff 时归因 |
| F-1042 | 邀请人奖励发放（系统） | 附着新用户创建回调 | 系统 | `inviter.AffCount++`、`AffQuota/AffHistoryQuota += QuotaForInviter`（仅 inviterId 有效时） |
| F-1043 | 新用户邀请奖励（系统） | 附着新用户初始化 | 系统 | 初始 `quota += QuotaForNewUser` |
| F-1044 | 邀请额度划转为可用额度 | `POST /api/user/self/aff_transfer`（模块一 §1.3） | UserAuth | `quota>=QuotaPerUnit` 且 `AffQuota` 足额；需通过 payment_compliance；幂等键 `user_id`+本次划转 |
| F-1045 | 邀请统计展示 | 附着 `GET /api/user/self`（模块一 §1.3） | UserAuth | 返回 `aff_count`/`aff_quota`/`aff_history_quota` |

## 12.3 Telegram 登录 Bot（D4）— 交叉引用模块一

> Telegram 端点已在**模块一 §1.5** 定义，本处登记交叉引用 + 系统级 HMAC/唯一性，覆盖校验须数到。

| 功能 ID | 名称 | 端点 / 承载点 | 鉴权 | 备注 |
|---|---|---|---|---|
| F-1051 | Telegram 登录（HMAC 校验） | `GET /api/oauth/telegram/login`（模块一 §1.5） | 匿名（CriticalRateLimit） | `TelegramOAuthEnabled=false`→未开启；hash 不匹配→无效请求；按 telegram_id 登录 |
| F-1052 | Telegram 绑定到现有账号 | `GET /api/oauth/telegram/bind`（模块一 §1.5） | UserAuth | 写 `user.TelegramId` 并 302 跳 `/console/personal`；已被绑定→拒绝 |
| F-1053 | Telegram Widget HMAC 防伪（系统） | 附着 login/bind `checkTelegramAuthorization` | 系统 | 密钥=`SHA256(BotToken)`，HMAC 输入为字典序拼接非 hash 参数；篡改任一参数→hash 不等拒绝 |
| F-1054 | Telegram 绑定唯一性校验（系统） | 附着 `TelegramBind` `IsTelegramIdAlreadyTaken` | 系统 | `telegram_id` 唯一，已被占用→「该 Telegram 账户已被绑定」 |

---

# 模块十三：Playground（站内对话试用，UserAuth）

> 鉴权：`/pg/*` = UserAuth + Distribute + SystemPerformanceCheck（按用户实际额度计费）。
> 字段源：Token / User（临时令牌 `playground-<group>`）。FUNCTION-LIST 权威：F-4038（Playground module）。

### POST /pg/chat/completions — 站内对话试用 Playground
- **功能 ID**：F-4038
- **鉴权**：UserAuth + Distribute + SystemPerformanceCheck
- **入参**（JSON）：OpenAI chat/completions 兼容体（`model`、`messages[]`、`stream` 等）；按用户 `UsingGroup` 构造临时令牌上下文。
- **出参**：透传上游 chat/completions 响应（流式/非流式，复用 `RelayFormatOpenAI` 链路）。
- **错误码**：`use_access_token`→`ErrorCodeAccessDenied`「暂不支持使用 access token」；额度不足/限流同 Relay 网关（模块六）。
- **副作用**：以 `tempToken{ Name: "playground-<group>", Group: UsingGroup }` 经 `SetupContextForToken` 后调 `Relay`；按用户实际额度计费扣 `quota_sell`（落 Log，C→A 客户视图）。
- **幂等键**：无（对话请求非幂等；计费按真实 token 结算多退少不补，复用 D6 计费）。
- **说明**：禁用 access token 是关键安全闸（防止 Playground 临时令牌滥用 API key 权限）。

---

# 模块十四：横切约束（NFR / Security / Observability / Scalability / Compliance / RBAC / 亲和缓存 / 跨分组重试）

> 本模块功能**绝大多数无独立 REST 端点**——由中间件 / 基础设施 / 部署 / 序列化层 / 调度循环实现。统一标注「横切约束，无独立端点，由 <承载层> 实现」+ 功能 ID（覆盖校验须数到）。少数已有对应端点的，交叉引用前序模块。
> 字段源：FUNCTION-LIST F-5001~F-5035（NFR/Security/Observability/Scalability/Compliance/RBAC）、F-2029~F-2037（亲和缓存/跨分组重试，配置端点见模块五，系统行为见此登记）。

## 14.1 NFR（性能 / 可用性 / 容灾）

| 功能 ID | 名称 | 承载层 | 约束指标 / 行为 | 鉴权 |
|---|---|---|---|---|
| F-5001 | 网关附加延迟与转发开销埋点 | perf_metrics + relay 链路（基础设施） | p50<=15ms 且 p99<=60ms，指标系统可查（NFR-P01/P02/P06） | 运维/Root 可见全量指标 |
| F-5002 | 性能压测与基准门禁 | CI/发布流程（部署） | 稳态>=1500 req/s 且 >=5000 并发长连接错误率<0.1%（NFR-P04/P05）；失败阻断发布 | 运维/Root |
| F-5003 | 渠道健康度看板 | perf_metrics + channel-test（监控数据源；展示复用 §9.3 / 模块五渠道测试） | 故障渠道命中后<=1 次重试切换健康渠道（NFR-A02/A04） | AdminAuth+ |
| F-5004 | SLA 月度可用性报表 | uptime_kuma + perf_metrics 聚合（基础设施） | 数据面>=99.9% 控制面>=99.5%（NFR-A01）；只读 | 运营/管理员/Root |
| F-5005 | 容灾演练与备份恢复 | DB 主从+备份（基础设施/部署） | RTO<=30min 且 RPO<=5min（NFR-A08）；Root 审批 | Root |

> **横切约束，无独立 REST 端点**：F-5001/F-5002/F-5004/F-5005 由指标系统/CI/基础设施承载；F-5003 数据源为 perf_metrics（展示端点见 §9.3 perf-metrics + 模块五渠道列表/测试）。

## 14.2 Security（密钥 / 脱敏 / 审计）

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-5006 | 令牌 Key 掩码与受控取明文 | 序列化层 `MaskTokenKey` + `SecureVerification`（已见模块二 §令牌） | 列表默认掩码；取明文走 `POST /token/:id/key`（CriticalRateLimit+DisableCache+二次验证，F-3004/F-1038） |
| F-5007 | 密钥与支付 Secret 加密静态存储 | 数据层 AES-256-GCM/KMS（基础设施） | 渠道 Key/WebhookSecret 密文落库支持轮换（NFR-S01/S10）；Root 配置 |
| F-5008 | 日志 prompt/响应脱敏管线 | 日志写入层 `model/log.go`（基础设施） | 开启正文留存时按 PII 规则脱敏后存储（NFR-S04/DC-005）；默认不留正文 |
| F-5009 | 高危操作审计留痕 | 审计埋点 `RecordOperationAuditLog`（已见模块八 §8.4 F-4011） | 取 Key/改倍率/禁渠道/改 Option 等写审计含前后值（NFR-S09）；查询复用 `GET /api/log/`（type=3） |

> **横切约束**：F-5007/F-5008 由数据/日志基础设施实现，无独立端点；F-5006 取明文端点见模块二、二次验证端点 `POST /api/verify`（模块一 F-1038）；F-5009 审计写入埋点 + 查询复用模块八。

## 14.3 Observability（指标 / 告警 / 追踪）

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-5010 | Prometheus RED 指标导出 | `/metrics` 抓取端点（基础设施） | 暴露按渠道/模型的请求率/错误率/延迟与额度速率，含维度标签（NFR-O01/O02）；运维/Root |
| F-5011 | 多渠道告警编排 | NotificationSettings + 告警引擎（基础设施；配置复用 §9.2/通知设置） | 渠道错误率/额度/限流/延迟超 SLO 经 Email/Webhook/Bark 告警（NFR-O03）；AdminAuth+ 配置 |
| F-5012 | 链路追踪 trace_id 贯穿 | relay 链路中间件 + OTel 导出（基础设施） | 入站→上游→结算贯穿 trace_id 支持 OTel 导出（NFR-O04）；运维/Root |

> **横切约束，无独立业务端点**：F-5010 为 `/metrics` 监控抓取端点（基础设施，非业务 API）；F-5011 配置复用通知设置（F-4037）+ 告警引擎；F-5012 由 relay 中间件贯穿，无独立端点。

## 14.4 Scalability（横扩 / 缓存 / 归档）

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-5013 | 无状态横扩与共享缓存 | Redis 共享状态（基础设施/部署） | 转发实例无本地状态，2~4 实例吞吐近线性（NFR-E01）；运维/Root |
| F-5014 | 缓存命中率监控 | channel/token/Ability 缓存（基础设施；统计复用 §9.3） | 命中率>=95% 且 DB 选渠占比<5%（NFR-E02）；运维/Root |
| F-5015 | 日志归档与分区 | 日志表分区+归档（基础设施；清理复用模块八 `DELETE /api/log/`） | 超期日志按策略归档不影响查询（NFR-E03/DC-006）；运维/Root |

> **横切约束，无独立端点**：F-5013/F-5014/F-5015 由 Redis/缓存层/DB 分区基础设施实现；日志清理端点见模块八 F-4006。

## 14.5 Compliance（数据分级 / 留存 / 数据驻地 / 注销 / 同意闸门）

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-5016 | 数据分级登记 | 数据字典（文档/基础设施） | 凭证/PII/内容/计量四级字段标注分级（DC-001）；Root 维护 |
| F-5017 | prompt 留存开关与保留期 | 日志层 + Option（配置复用 §9.2） | 正文留存默认关，可开且独立保留期默认<=30 天（DC-005）；AdminAuth+ 配置 |
| F-5018 | 合规分组仅境内 provider | 选渠层 + group_ratio（系统） | 合规分组限定仅命中境内数据驻地渠道（DC-008）；AdminAuth+ 配置 |
| F-5019 | 数据出境告知与驻地标注 | VendorMeta（已见模块四供应商元数据，公开展示价格页） | 每 provider 标注境内外驻地并明示请求转发地区（DC-008/DC-009）；公开可见 |
| F-5020 | 账号注销级联删除 | 注销流程（self-scope） | 级联删除令牌/OAuth 绑定/PII 并匿名化日志（DC-003/DC-011）；注销后 PII 清空或匿名 |
| F-5021 | 隐私政策与出境条款同意闸门 | 调用前置闸门（中间件 + Option，关联 §9.5 合规确认） | 未接受含出境与留存条款的协议不可调用（DC-010）；公开闸门 |

> **横切约束**：F-5016/F-5017/F-5018 由数据字典/日志层/选渠层实现（配置复用选项接口）；F-5019 数据驻地标注复用模块四 VendorMeta + 公开价格页（F-3023/F-2048）；F-5020 账号注销为 self-scope 流程（级联删除，建议端点 `DELETE /api/user/self`，按 repo 实际承载）；F-5021 为调用前置同意闸门，关联 §9.5 `POST /api/option/payment_compliance`。

## 14.6 RBAC（角色 / 越权防护 / 二次验证 / 矩阵）

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-5030 | 功能权限组运营集/运维集分配 | ROLE-PERMISSION-MATRIX（配置/文档） | Root 为 admin 细分运营(O07-O09)/运维(O11+O10 只读)最小权限集；RootAuth |
| F-5031 | 三级系统角色鉴权 | `AdminAuth`/`RootAuth` 中间件（横切，贯穿全站端点） | common/admin/root 经中间件鉴权，越权路由返回 403；中间件强制 |
| F-5032 | self-scope 资源越权防护 | model 层 user_id 强制过滤（横切，贯穿令牌/额度/任务/日志） | 按 user_id 过滤，访问他人资源返回 403 |
| F-5033 | 高危操作二次验证闸门 | `SecureVerification`（`POST /api/verify`，模块一 F-1038） | 取 Key/改倍率/重置 2FA 等统一接入，无二次验证调用返回 403 |
| F-5034 | 角色操作权限矩阵配置化 | ROLE-PERMISSION-MATRIX + Audit（文档/审计） | 矩阵可后台查看与审计变更；RootAuth |
| F-5035 | 团队工作区实体（阶段二预留） | Team/team_id（阶段二，未实现） | 引入 team_id 与 Owner/Member 两级角色及共享额度池（SG-006）；阶段二启用 |

> **横切约束，无独立专属端点**：F-5031/F-5032/F-5033 为鉴权中间件/序列化层，贯穿所有前序模块端点（每端点「鉴权」栏即其落地）；F-5030/F-5034 为权限矩阵配置（文档+RootAuth 选项）；F-5035 为阶段二预留实体（本期不实现）；二次验证端点见模块一 `POST /api/verify`（F-1038）。

## 14.7 亲和缓存 / 跨分组重试（系统行为，配置端点见模块五）

> 配置/清缓存/统计端点已在**模块五**定义（F-2029~F-2033 配置、`/api/channel_affinity_cache` 清缓存/统计、`/api/log/channel_affinity_usage_cache` 用量），本处登记**系统级运行时行为**（无独立端点），交叉引用。

| 功能 ID | 名称 | 承载层 | 约束 / 行为 |
|---|---|---|---|
| F-2029 | 会话亲和键提取与渠道粘连 | 亲和缓存中间件（系统） | 按 `model_regex`/`path_regex`/`key_sources`(gjson/header/context) 提取会话键，命中缓存复用上次成功渠道；内置 codex/claude 规则 |
| F-2030 | codex/claude CLI header 透传模板 | 亲和缓存中间件（系统） | 命中规则按 `pass_headers`(keep_origin) 透传 CLI 专属 header；内置不可被普通请求覆盖 |
| F-2034 | 亲和命中跳过重试（SkipRetryOnFailure） | 选渠/重试循环（系统） | `SkipRetryOnFailure=true` 规则失败时不触发跨渠道重试（保会话稳定）；codex/claude 内置=true |
| F-2035 | auto 分组逐组耗尽优先级后切下一组 | `CacheGetRandomSatisfiedChannel`（系统） | auto 分组下逐组从当前优先级选渠，当前组耗尽切下一组重试（FC-070） |
| F-2036 | 令牌级跨分组重试开关（仅 auto 有效） | 选渠循环 + `Token.CrossGroupRetry`（系统） | 开启后当前组优先级耗尽下次重试切下一组，本次仍用当前组；非 auto 无效 |
| F-2037 | 全局重试次数配置（RetryTimes） | `common.RetryTimes`（配置复用 §9.2） | 单分组内优先级降级/重试上限；与 CrossGroupRetry 联动决定耗尽后切组或失败 |

> **横切约束，无独立端点**：F-2029/F-2030/F-2034/F-2035/F-2036 为选渠/亲和缓存调度循环的系统行为（附着 Relay 网关请求链路）；F-2037 为全局配置（复用选项接口）。亲和缓存的管理端点（配置/清缓存/统计）已在模块五登记。

---

# 附录：第二批覆盖校验总表（模块九~十四）

| 模块群 | 功能 ID 区间 | 独立端点数 | 横切/系统级/交叉引用项数 | 备注 |
|---|---|---|---|---|
| 九 运营与运维 | F-4015~F-4037 | 18（setup×2 + option×3 + performance×7 + 公开内容×6） | 5（F-4032~F-4036 配置横切附着 §9.2/§1.3；其中 F-4036 i18n 中间件）+ F-4030 合规确认含会话闸门 | F-4015/F-4016=系统初始化（非 usagelog 局部号） |
| 十 部署管理 | F-3039~F-3056 | 18 | 0 | 全 AdminAuth；io.net 企业集成 |
| 十一 公开站点 | F-4039~F-4044 | 2 后端（status + 2 前端路由页复用内容端点） | 3（F-4040/F-4043/F-4044 前端入口无后端端点） | F-4041/F-4042 前端路由复用 §9.4 |
| 十二 增长 | F-1039~F-1054（D2/D3/D4） | 2 新增（checkin POST/GET） | 11（F-1048~F-1050 系统级 + F-1039~F-1045 D3 交叉引用模块一 + F-1051~F-1054 D4 交叉引用模块一） | 邀请/Telegram 端点本体在模块一 |
| 十三 Playground | F-4038 | 1 | 0 | UserAuth，禁 access token |
| 十四 横切 | F-5001~F-5035、F-2029~F-2037 | 0 新增（均横切/复用） | 28（NFR×5 + Security×4 + Observability×3 + Scalability×3 + Compliance×6 + RBAC×6 + 亲和/重试×6=33，其中部分交叉引用前序模块端点） | 全标「横切约束，无独立端点，由中间件/基础设施/部署实现」 |

> **本批合计**：新增独立 REST 端点 **41 个**（九 18 + 十 18 + 十一 1 + 十二 2 + 十三 1，公开站 2 前端路由页不计入新增后端端点；按后端业务端点计 41）；横切/系统级/前端入口/交叉引用登记项 **47 项**。
> **涉及功能 ID 区间**：F-3039~F-3056（部署）、F-4015~F-4044（运营运维+公开站）、F-1039~F-1054（增长 D2/D3/D4）、F-4038（Playground）、F-5001~F-5035（NFR/Security/Observability/Scalability/Compliance/RBAC）、F-2029~F-2037（亲和缓存/跨分组重试系统行为）。
---

# 模块十五 预填分组（AdminAuth，补批）

> 管理员一键填充渠道/令牌配置的预设分组（model/tag/endpoint 三类型）。原型有此页，第一/二批 API 扫漏，覆盖校验抓出后补齐。表：PrefillGroup(Id/Name/Type/Items[]JSON/软删除)。

### 15.1 预填分组创建
- `POST /api/prefill_group` · **AdminAuth** · 功能ID: **F-2012**
- 入参：`{ name:string必填, type:enum(model|tag|endpoint)必填, items:[]string }`
- 出参（AdminView）：`{ id:int, name, type, items:[], created_time }`
- 校验/错误码：name+type 非空（400 `name/type required`）；同 type 下 name 不重复（409 `prefill group name conflict`）；Items 以 JSON 数组持久化
- 幂等键：无（创建以 name+type 唯一约束兜底重复）

### 15.2 预填分组更新（名称冲突校验）
- `PUT /api/prefill_group` · **AdminAuth** · 功能ID: **F-2013**
- 入参：`{ id:int必填, name:string, items:[]string }`
- 出参（AdminView）：更新后的 PrefillGroup 对象
- 错误码：id 不存在（404 `prefill group not found`）；新 name 与同 type 他组冲突（409 `prefill group name conflict`）

### 15.3 预填分组列表（按 type 下拉填充）
- `GET /api/prefill_group?type=model|tag|endpoint` · **AdminAuth** · 功能ID: **F-2014**
- 入参：query `type`（可选，缺省返回全部）
- 出参（AdminView）：`[{ id, name, type, items:[] }]` — 供前端配置渠道/令牌时一键填充
- 错误码：type 非法枚举（400 `invalid type`）

### 15.4 预填分组软删除
- `DELETE /api/prefill_group/:id` · **AdminAuth** · 功能ID: **F-2015**
- 入参：path `id`
- 出参：`{ success:true }`
- 行为：按 id 软删除（gorm.DeletedAt），保留历史不物理移除
- 错误码：id 不存在（404 `prefill group not found`）

---

# 模块十六 用量排行榜快照（公开/UserAuth，补批）

> 公开页或控制台请求模型/用量排行榜快照。第一/二批扫漏，覆盖校验抓出后补齐。数据对象：UsedataRanking（period 维度的用量聚合快照）。

### 16.1 用量排行榜快照
- `GET /api/rankings?period=week|month` · **匿名 / UserAuth** · 功能ID: **F-4010**
- 入参：query `period`（week|month，缺省 week）
- 出参（PublicView，**裁掉成本/利润/上游模型B/供应商**）：`[{ rank:int, public_model:string(A), used_quota_or_count:number, period:string, snapshot_time }]`
- 行为：返回指定 period 的用量排行榜快照（定时任务预聚合，非实时扫日志）
- 错误码：period 非法（400 `invalid period`）
- 视图铁律：公开排行只暴露对外模型 A 名与聚合量，绝不出现真实上游 B / 渠道 / 成本

---

> **覆盖完整性（脚本实测，非自报）**：FUNCTION-LIST 全 **243** 功能（F-1001~F-6012）经第一批（模块一~八）+ 第二批（模块九~十四）+ 补批（模块十五预填分组 F-2012~2015、模块十六排行榜 F-4010）全部登记。主 agent 用 Python 正则提取全文 F-xxxx 与 FUNCTION-LIST.csv 逐一对账，正向 miss=0。文档中 F-3060/F-3061/F-3062 为 prd-relay.md 旧局部编号的对账说明（已统一映射回权威号 F-6010~6012），非孤儿端点。
