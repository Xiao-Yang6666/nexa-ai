# PRD — 账号与身份（FL-account）

> 分片：账号与身份 D1（F-1001~F-1038）+ Telegram 登录 Bot D4（F-1051~F-1054）。对应流程图 `flow/FL-account.md`、状态矩阵 `PAGE-STATE-MATRIX.md §B`。
> 数据对象字段一律复用 `DATA-MODEL.md`：用户 §1 User、令牌/会话相关 §2、OAuth 绑定 §11、双因子 §12、Passkey §13、系统开关 §15。
> 跨切面契约见 `../OVERALL-FLOW.md §3`：C1 登录闸门、C2 externalJump（OAuth 外跳）、C3 二次验证、C4 Turnstile 人机校验。本片不重画 C4，仅在节点标注「过 C4」。
> 本片覆盖功能 ID：**F-1001 / F-1002 / F-1003 / F-1004 / F-1005 / F-1006 / F-1007 / F-1010 / F-1016 / F-1018 / F-1019 / F-1020 / F-1021 / F-1022 / F-1025 / F-1026 / F-1027 / F-1028 / F-1029 / F-1031 / F-1033 / F-1036 / F-1051 / F-1052 / F-1053 / F-1054**。

---

## AC-1 邮箱密码注册（验证码 + 邀请归因，多重串行校验链）

- **功能 ID / 优先级**：F-1001、F-1004、F-1005、F-1040 / P0
- **来源**：FC-001/FC-002（`controller/user.go Register`、`api-router.go:69 userRoute.POST(/register, TurnstileCheck)`、`EmailVerificationEnabled` 分支 user.go:224）
- **角色 / Owner**：匿名访客；Owner 模块 = 账号与身份
- **触发**：访客在注册页填用户名/密码/邮箱并提交（过 C4）

### 1. 场景
访客想开通 RoutifyAPI 账号。系统在落库前要逐关挡住注册关闭、验证码错/过期、用户名占用三类拦截；若访客来自他人邀请链接（携 `aff_code`），需把邀请人归因写入新账号；成功后直接进入自动登录态，并给新用户配初始额度与本人邀请码。

### 2. 前置条件
- `RegisterEnabled=true`（系统开关，§15）。
- 通过 C4 人机校验，且过 `CriticalRateLimit`。
- 若 `EmailVerificationEnabled=true`（§15），访客须先经 F-1004 取得有效验证码并随注册请求携带。

### 3. 主流程（对应 AC-1 节点 R0→OK）
1. 访客进入注册页（R0），判 `RegisterEnabled`（R1）：true 放行。
2. 填用户名/密码/邮箱并过 C4（R2）。
3. 判是否需邮箱验证码（R3）：`EmailVerificationEnabled` 开启走 R4，关闭直接到 R8。
4. 请求发送验证码（R4），判验证码限流（R5）：放行则输入验证码（R6）。
5. 校验验证码匹配且未过期（R7）：通过到 R8。
6. 提交注册（R8），判用户名是否已存在（R9）：不存在放行。
7. 判是否携带 `aff_code`（R10）：有效则解析 `InviterId` 归因（R11）；无/无效则 `InviterId=0`（R12）。
8. 创建 `common` 用户 + `Quota=QuotaForNewUser` + 生成 4 位 `aff_code`（R13）→ 进入自动登录态（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `RegisterEnabled=false` | R1-否 | 拒绝进入注册 | 注册已关闭态 |
| 验证码请求被限流 | R5-被限流 | 不下发新验证码 | 发码过频 → 稍后重试 |
| 验证码不匹配或已过期 | R7-否 | 拒绝创建，不落库 | 验证码错误/过期态 |
| 用户名已存在 | R9-是 | 拒绝创建 | 提示 `MsgUserExists` |
| 携带有效 `aff_code` | R10-有效 | 解析并写 `InviterId` | 邀请归因态（提示「来自邀请」） |
| `aff_code` 无/无效 | R10-无/无效 | `InviterId=0` | 正常注册无归因 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **写** `User`：`Username(username)`（`unique`，`validate:max=20`）、`Password(password)`（`validate:min=8,max=20`，存哈希）、`Email(email)`、`Role(role)=RoleCommonUser(common)`、`Status(status)=UserStatusEnabled(1)`、`Quota(quota)=common.QuotaForNewUser`、`AffCode(aff_code)`（4 位 `uniqueIndex`）、`InviterId(inviter_id)`（有效 aff 时解析，否则 0）、`CreatedAt(created_at)`。
- **读校验** `VerificationCode(verification_code)`（`-:all`，仅校验不落库，`EmailVerificationEnabled` 开启时启用）。
- **读** 系统开关（§15）：`RegisterEnabled`、`EmailVerificationEnabled`。

### 6. 验收标准
- [ ] `RegisterEnabled=false` 提交注册 → 拒绝 + 注册已关闭态，不写 `User` 表。
- [ ] `EmailVerificationEnabled=true` 且 `verification_code` 错误/过期 → 拒绝 + 验证码错误态，不落库。
- [ ] 用户名与已有 `Username` 重复 → 拒绝 + `MsgUserExists`。
- [ ] 携带有效 `aff_code` 注册成功 → 新用户 `InviterId` = 邀请人 Id（≠0）。
- [ ] 不携带 `aff_code` 注册成功 → 新用户 `InviterId=0`。
- [ ] 注册成功 → `Role=common`、`Quota=common.QuotaForNewUser`、`AffCode` 为 4 位且全局唯一，进自动登录态。

### 7. 所触及页面状态（对齐 §B「注册表单」）
注册表单默认态（过 C4）· 注册已关闭态（RegisterEnabled=false） · 发码过频限流态 · 验证码错误/过期态 · 用户名重复态（MsgUserExists） · 邀请归因态（带 aff_code） · 注册成功自动登录态。

---

## AC-2 邮箱密码登录（封禁状态闸门 + 2FA 分流）

- **功能 ID / 优先级**：F-1002 / P0
- **来源**：FC-001（`controller/user.go Login`、`setupLogin`、`api-router.go:70 userRoute.POST(/login, TurnstileCheck)`）
- **角色 / Owner**：注册用户；Owner 模块 = 账号与身份
- **触发**：访客在登录页填账号密码并提交（过 C4）

### 1. 场景
已注册用户用账号密码登录。系统不能只看凭证是否正确：被封禁（`Status≠Enabled`）的账号即使密码对也必须拒登；若账号已开 2FA，密码这一关只是第一步，须转到 AC-8 的第二步校验后才建会话。短链路 + 状态闸门 + 分流。

### 2. 前置条件
- 提交的账号在 `User` 表存在。
- 通过 C4 人机校验，且过 `CriticalRateLimit`。

### 3. 主流程（对应 AC-2 节点 L0→OK）
1. 访客进入登录页（L0），输入账号密码并过 C4（L1）。
2. 校验凭证是否正确（L2）：`Password` 哈希匹配则放行。
3. 校验 `Status=UserStatusEnabled`（L3）：是放行。
4. 判该账号是否已开 2FA（L4）：是→转 AC-8 第二步；否→`setupLogin` 建立会话（L6）。
5. 会话建立成功 → 进入控制台（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 凭证不正确 | L2-否 | 不建会话 | 账号或密码错误态 |
| `Status≠UserStatusEnabled`（被封禁） | L3-否 | 拒绝登录，不建会话 | 账号已封禁拒绝态 |
| 账号已开 2FA | L4-是 | 暂不建会话，跳第二步 | 转 2FA 第二步态（见 AC-8） |
| 凭证正确 + 未开 2FA + 已启用 | L4-否 | `setupLogin` 建会话 | 登录成功进控制台态 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **读校验** `User`：`Username(username)` / `Email(email)`（定位账号）、`Password(password)`（哈希比对）、`Status(status)`（须 `=UserStatusEnabled(1)`，禁用 `≠1` 拒登）、`Role(role)`（会话内携带）。
- **写** `User.LastLoginAt(last_login_at)`（成功登录时间戳）。
- **写会话**：`setupLogin` 后续 `/api/user/self` 凭会话鉴权（无独立会话表，session 内携 `Id`/`Role`）。

### 6. 验收标准
- [ ] 密码哈希不匹配 → 账号或密码错误态，不建会话。
- [ ] `Status≠UserStatusEnabled` 且密码正确 → 账号已封禁拒绝态，不建会话。
- [ ] 已开 2FA 账号密码正确 → 不直接建会话，转 2FA 第二步态。
- [ ] 凭证正确 + `Status=1` + 未开 2FA → `setupLogin` 建会话，`/api/user/self` 返回本人资料。

### 7. 所触及页面状态（对齐 §B「登录表单」）
登录表单默认态（过 C4） · 账号或密码错误态 · 账号已封禁拒绝态 · 转 2FA 第二步态 · 登录成功进控制台态。

---

## AC-3 找回密码（两段式：发重置邮件 → 提交新密码）

- **功能 ID / 优先级**：F-1006、F-1007 / P0
- **来源**：FC-003（`controller.SendPasswordResetEmail`、`controller.ResetPassword`、`api-router.go:43 GET /reset_password`、`api-router.go:44 POST /user/reset`）
- **角色 / Owner**：匿名访客；Owner 模块 = 账号与身份
- **触发**：访客在登录页点「忘记密码」，输入邮箱（过 C4）

### 1. 场景
用户忘记密码，靠邮箱自助重置。流程跨「发件」与「重置」两个时刻：发件阶段要防邮箱枚举（对未注册邮箱统一回提示但不发有效令牌）；重置阶段要校验令牌有效且未过期，成功后旧密码立即失效。

### 2. 前置条件
- 发件阶段过 C4 人机校验，且过 `CriticalRateLimit`。
- 重置阶段持有从邮件链接跳转携带的重置令牌。

### 3. 主流程（对应 AC-3 节点 F0→OK）
1. 访客点「忘记密码」（F0），输入邮箱并过 C4（F1）。
2. 判邮箱是否已注册（F2）：已注册→发送重置令牌到邮箱（F4）；未注册→统一提示已发送但不发有效令牌（F3）。
3. 用户点邮件链接（F5），打开重置页填新密码（F6）。
4. 校验令牌有效且未过期（F7）：通过则更新密码、旧密码失效（F8）。
5. 重置成功 → 引导用新密码登录（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 邮箱未注册 | F2-否 | 不发有效令牌，统一回提示 | 未注册邮箱统一提示态（防枚举） |
| 令牌无效/过期 | F7-否 | 拒绝重置，不改密码 | 令牌无效/过期态（重新申请） |
| 令牌有效且未过期 | F7-是 | 更新 `Password`，旧哈希失效 | 重置成功引导登录态 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **读定位** `User.Email(email)`（判是否已注册）、`User.Id(id)`（令牌归属）。
- **写** `User.Password(password)`（重置阶段更新为新哈希，`validate:min=8,max=20`），更新后旧密码哈希失效。
- **令牌**：重置令牌带过期时间（仅向已注册邮箱发有效令牌；提交 reset 时校验有效性与时效）。

### 6. 验收标准
- [ ] 对未注册邮箱发起找回 → 返回统一「已发送」提示，但不下发可用重置令牌。
- [ ] 已注册邮箱发起找回 → 该邮箱收到含有效令牌的重置链接。
- [ ] 用无效/过期令牌提交新密码 → 拒绝 + 令牌无效/过期态，密码未变。
- [ ] 用有效令牌提交新密码 → `Password` 更新为新哈希，旧密码不再能登录。

### 7. 所触及页面状态（对齐 §B「找回密码」）
填邮箱默认态（过 C4） · 未注册邮箱统一提示态（防枚举） · 已发送等待态 · 填新密码态 · 令牌无效/过期态 · 重置成功引导登录态。

---

## AC-4 第三方 OAuth 登录/注册/绑定（多 provider 分发）

- **功能 ID / 优先级**：F-1016、F-1018、F-1019、F-1020、F-1025 / P1~P2
- **来源**：FC-008/FC-009/FC-010/FC-011/FC-014（`controller/oauth.go HandleOAuth`、`findOrCreateOAuthUser`、`handleOAuthBind`、`GenericOAuthProvider` 分支、`api-router.go:46 GET /oauth/state`、`api-router.go:54 GET /oauth/:provider`）
- **角色 / Owner**：匿名访客 / 登录用户；Owner 模块 = 账号与身份
- **触发**：访客或登录用户点击 GitHub/Discord/OIDC/LinuxDO/自定义 provider 登录

### 1. 场景
一套 OAuth 回调处理同时承担登录、注册、绑定三种意图，并覆盖多个 provider。系统先发 state 防 CSRF，回调时校验 state；按当前是否有会话分流到「绑定」或「登录/注册」；登录/注册分支里再判外部 ID 是否已有账号、注册是否开放、LinuxDO 信任级是否达标。复用契约 C2 外跳四态。

### 2. 前置条件
- 先 `GET /oauth/state` 生成 CSRF state（可带 aff），写入会话。
- 目标 provider 已在系统开关启用（如 `GitHubOAuthEnabled` / `LinuxDOOAuthEnabled`，§15）。
- 走「绑定」分支须已有登录会话；走「登录/注册」分支无需会话。

### 3. 主流程（对应 AC-4 节点 O0→OKL/OKB）
1. 点击第三方登录（O0），`GET /oauth/state` 生成 CSRF 并暂存 aff（O1）。
2. 外跳 provider 授权（O2，复用 C2），回调校验 state 匹配（O3）。
3. 校验 provider 已启用（O4）。
4. 判当前是否已登录会话（O5）：
   - 是 → 绑定分支：判该外部 ID 是否已被占用（O6）；未占用则写 `user_oauth_bindings`（O7）→ 绑定成功（OKB）。
   - 否 → 登录/注册分支：判外部 ID 是否已有账号（O8）。
5. 外部 ID 已有账号（O8-是）：若 LinuxDO 则判信任级达标（O11），达标/非 LinuxDO 则登录已有账号（O12）→ 登录成功（OKL）。
6. 外部 ID 无账号（O8-否）：判 `RegisterEnabled`（O9），开放则创建账号+写绑定+按 aff 归因（O10）→ 登录成功（OKL）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 回调 state 不匹配 | O3-否 | 拒绝回调 | state 不匹配拒绝态（403 `MsgOAuthStateInvalid`） |
| provider 未启用 | O4-否 | 拒绝 | provider 未启用态 |
| 已登录且外部 ID 已被占用 | O6-是 | 拒绝绑定 | 已被绑定态（`MsgOAuthAlreadyBound`） |
| LinuxDO 信任级不达标 | O11-不达标 | 拒绝登录 | 信任级过低态（`MsgOAuthTrustLevelLow`） |
| 外部 ID 无账号且 `RegisterEnabled=false` | O9-否 | 不创建新用户 | 注册关闭不创建态 |
| 已登录 + 外部 ID 未占用 | O6-否 | 写 `user_oauth_bindings` | 绑定成功态 |
| 外部 ID 已有账号 + 信任达标/非 LinuxDO | O11/O12 | 登录已有账号 | 登录成功态 |

### 5. 数据对象（复用 DATA-MODEL §1 / §11）
- **写绑定** `UserOAuthBinding`（§11）：`UserId(user_id)`、`ProviderId(provider_id)`、`ProviderUserId(provider_user_id)`（`uniqueIndex:ux_provider_userid`，每 provider 一账号唯一）、`CreatedAt(created_at)`。
- **写/读 内建 provider 标识** `User`：`GitHubId(github_id)`、`DiscordId(discord_id)`、`OidcId(oidc_id)`、`LinuxDOId(linux_do_id)`（均 `index`）。
- **登录/注册创建** `User`：`Role(role)=common`、`Quota(quota)=QuotaForNewUser`、`InviterId(inviter_id)`（按暂存 aff 归因）。
- **读** 系统开关（§15）：`GitHubOAuthEnabled`/`LinuxDOOAuthEnabled` 等 provider 启用键、`RegisterEnabled`。

### 6. 验收标准
- [ ] 回调 state 与会话暂存不一致 → 403 + `MsgOAuthStateInvalid`，不建会话不写绑定。
- [ ] 目标 provider 未启用 → provider 未启用态，拒绝处理。
- [ ] 已登录会话 + 该 `ProviderUserId` 已被他人绑定 → `MsgOAuthAlreadyBound`，不写 `user_oauth_bindings`。
- [ ] LinuxDO 信任级低于阈值 → `MsgOAuthTrustLevelLow`，拒绝登录。
- [ ] 外部 ID 无账号且 `RegisterEnabled=false` → 不创建 `User`，注册关闭不创建态。
- [ ] 已登录会话 + 外部 ID 未占用 → `user_oauth_bindings` 新增一条且后续该 provider 可登录此账号。

### 7. 所触及页面状态（对齐 §B「OAuth 登录/绑定」）
外跳授权前确认态/跳转中态（C2） · state 不匹配拒绝态（403） · provider 未启用态 · 已被绑定态 · 信任级过低态 · 注册关闭不创建态 · 绑定成功态 · 登录成功态（已有/新建+aff）。

---

## AC-5 WeChat 扫码授权登录/绑定（二维码 + 轮询）

- **功能 ID / 优先级**：F-1021、F-1022 / P2
- **来源**：FC-012（`controller.WeChatAuth`、`controller.WeChatBind`、`controller/wechat.go`、`api-router.go:49 GET /oauth/wechat`、`api-router.go:50 POST /oauth/wechat/bind`）
- **角色 / Owner**：匿名访客 / 登录用户；Owner 模块 = 账号与身份
- **触发**：用户点击微信登录

### 1. 场景
微信不走标准 OAuth 重定向，而是先展示二维码、等用户扫码授权，再用授权码完成绑定或登录。与 AC-4 的重定向形态不同：这里有扫码态与轮询特性，二维码会过期。

### 2. 前置条件
- 微信功能已配置（`WeChatAuthEnabled`，§15）。
- 走绑定分支须已有登录会话；走登录分支须 `wechat_id` 已对应某账号。
- 过 `CriticalRateLimit`。

### 3. 主流程（对应 AC-5 节点 W0→WOKB/WOKL）
1. 点击微信登录（W0），判微信功能是否已配置（W1）：是放行。
2. `GET /oauth/wechat` 展示二维码（W2），等待用户扫码授权（W3）。
3. 判扫码授权是否成功（W4）：成功则 `POST /wechat/bind` 携授权码（W5）。
4. 判当前是否已登录（W6）：
   - 是 → 绑定 `wechat_id` 到当前账号（W7）→ 微信绑定成功（WOKB）。
   - 否 → 判 `wechat_id` 是否已对应账号（W8）：是→登录该账号（W9）→ 微信登录成功（WOKL）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 微信功能未配置 | W1-否 | 入口不可用 | 微信未配置不可用态 |
| 扫码超时/取消 | W4-超时/取消 | 二维码失效 | 二维码过期刷新态 |
| 已登录扫码成功 | W6-是 | 写 `wechat_id` 到当前账号 | 微信绑定成功态 |
| 未登录且 `wechat_id` 已对应账号 | W8-是 | 登录该账号 | 微信登录成功态 |
| 未登录且 `wechat_id` 无对应账号 | W8-否 | 不登录 | 无对应账号引导态（引导注册/绑定） |

### 5. 数据对象（复用 DATA-MODEL §1）
- **读/写** `User.WeChatId(wechat_id)`（`index`，第三方绑定标识；绑定写入、登录按其匹配账号）。
- **读** `User.Id(id)`（登录目标定位）、`User.Status(status)`（登录态校验沿用 §1 枚举）。
- **读** 系统开关（§15）：`WeChatAuthEnabled`（未配置则入口不可用）。

### 6. 验收标准
- [ ] `WeChatAuthEnabled` 未配置 → 微信入口不可用，不展示二维码。
- [ ] 扫码超时/取消 → 二维码过期刷新态，需刷新重扫。
- [ ] 已登录会话扫码成功 → 当前账号 `WeChatId` 被写入（绑定成功）。
- [ ] 未登录扫码成功且 `wechat_id` 已对应账号 → 登录该账号。
- [ ] 未登录扫码成功且 `wechat_id` 无对应账号 → 不登录，进无对应账号引导态。

### 7. 所触及页面状态（对齐 §B「微信扫码」）
微信未配置不可用态 · 二维码展示等待扫码态 · 轮询扫码态 · 二维码过期刷新态 · 微信绑定成功态 · 微信登录成功态 · 无对应账号引导态。

---

## AC-6 Telegram Widget 登录（HMAC 防伪）

- **功能 ID / 优先级**：F-1051、F-1053 / P0~P1
- **来源**：FC-018（`controller/telegram.go TelegramLogin` telegram.go:72-99、`checkTelegramAuthorization` telegram.go:101-124、`api-router.go:51 GET /oauth/telegram/login`）
- **角色 / Owner**：匿名访客 / 系统（HMAC 校验）；Owner 模块 = Telegram 登录 Bot
- **触发**：Telegram Login Widget 回传授权参数

### 1. 场景
Telegram 用 Widget 回传一组参数 + 一个 `hash`，没有传统的服务端会话回调。系统必须用 BotToken 派生密钥重算 HMAC，与传入 `hash` 逐字节比对：相等才信任 `telegram_id` 并登录，任一参数被篡改都会导致重算不等而拒绝。安全校验密集型。

### 2. 前置条件
- `TelegramOAuthEnabled=true`（§15）。
- 已配置 `TelegramBotToken`（§15，HMAC 派生密钥 key=`SHA256(BotToken)`）。
- 过 `CriticalRateLimit`。

### 3. 主流程（对应 AC-6 节点 TG0→TGOK）
1. Telegram Widget 回传参数（TG0），判 `TelegramOAuthEnabled`（TG1）：开启放行。
2. 取除 `hash` 外的全部参数，按字典序拼接（TG2）。
3. 派生密钥 key=`SHA256(BotToken)`（TG3），计算 `HMAC-SHA256(data)`（TG4）。
4. 比对计算 hash 是否等于传入 hash（TG5）：相等放行。
5. 判 `telegram_id` 是否已有账号（TG6）：是→登录该 Telegram 用户（TG7）→ 登录成功（TGOK）。
6. `telegram_id` 无账号（TG6-否）：判 `RegisterEnabled`（TG8），开放则创建账号绑定 `telegram_id`（TG9）→ 登录成功（TGOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `TelegramOAuthEnabled=false` | TG1-否 | 入口不可用 | Telegram 登录未开启态 |
| 计算 hash ≠ 传入 hash（被篡改/伪造） | TG5-否 | 拒绝，视为无效请求 | hash 不匹配拒绝态 |
| `telegram_id` 已有账号 | TG6-是 | 登录该用户 | Telegram 登录成功态（已有） |
| `telegram_id` 无账号且 `RegisterEnabled=false` | TG8-否 | 不创建 | 注册关闭不创建态 |
| `telegram_id` 无账号且 `RegisterEnabled=true` | TG8-是 | 创建账号绑定 telegram_id | 新建账号登录成功态 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **读/写** `User.TelegramId(telegram_id)`（`index`，Telegram 绑定唯一标识；登录按其匹配账号，新建时写入）。
- **登录/注册创建** `User`：`Role(role)=common`、`Quota(quota)=QuotaForNewUser`。
- **读** 系统开关（§15）：`TelegramOAuthEnabled`、`TelegramBotToken`（派生 HMAC key=`SHA256(BotToken)`）、`RegisterEnabled`。

### 6. 验收标准
- [ ] `TelegramOAuthEnabled=false` → 返回未开启，不进入 HMAC 校验。
- [ ] 篡改任一回传参数后计算 hash ≠ 传入 hash → 拒绝，hash 不匹配拒绝态，不登录。
- [ ] HMAC 校验通过且 `telegram_id` 已有账号 → 登录该 `TelegramId` 对应用户。
- [ ] HMAC 通过 + `telegram_id` 无账号 + `RegisterEnabled=false` → 不创建账号。
- [ ] HMAC 通过 + `telegram_id` 无账号 + `RegisterEnabled=true` → 创建账号并写入 `TelegramId`，登录成功。

### 7. 所触及页面状态（对齐 §B「Telegram 登录」）
Telegram 登录未开启态 · HMAC 防伪校验 · hash 不匹配拒绝态 · Telegram 登录成功态（已有/新建） · 注册关闭不创建态。

---

## AC-7 Telegram 绑定到现有账号（唯一性闸门）

- **功能 ID / 优先级**：F-1052、F-1054 / P1
- **来源**：FC-018（`controller/telegram.go TelegramBind` telegram.go:18-70、`IsTelegramIdAlreadyTaken` telegram.go:35-41、`api-router.go:52 GET /oauth/telegram/bind`）
- **角色 / Owner**：登录用户 / 系统（唯一性校验）；Owner 模块 = Telegram 登录 Bot
- **触发**：登录用户在个人中心点「绑定 Telegram」

### 1. 场景
已登录用户想把自己的 Telegram 账户绑到现有账号。区别于 AC-6 的登录意图：这里必须先有登录会话，HMAC 校验通过后还要过唯一性闸门——同一 `telegram_id` 不能被多个账号绑定，已被占用直接拒绝；绑定成功后写入 `user.TelegramId` 并 302 回个人中心。

### 2. 前置条件
- 有有效登录会话（无会话复用契约 C1 阻断）。
- HMAC 校验通过（沿用 AC-6 §AC-6 的 `checkTelegramAuthorization` 防伪）。
- 当前用户未注销。

### 3. 主流程（对应 AC-7 节点 B0→OK）
1. 登录用户点「绑定 Telegram」（B0），判是否有登录会话（B1）：有则放行。
2. Widget 回传 + HMAC 校验（B2），判 HMAC 是否通过（B3）：通过放行。
3. 判当前用户是否已注销（B4）：未注销放行。
4. 判 `telegram_id` 是否已被他人占用（B5）：未占用则写入 `user.TelegramId`（B6）。
5. 302 跳转 `/console/personal`（B7）→ 绑定成功回个人中心（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 无登录会话 | B1-否 | 阻断 | 未登录阻断态（复用 C1） |
| HMAC 校验未通过 | B3-否 | 拒绝绑定 | HMAC 校验失败态 |
| 当前用户已注销 | B4-是 | 报错 | 用户已注销错误态 |
| `telegram_id` 已被他人占用 | B5-是 | 拒绝绑定 | Telegram 已被占用态（`IsTelegramIdAlreadyTaken` → 已被绑定） |
| 校验全通过且未占用 | B5-否 | 写 `user.TelegramId` + 302 | 绑定成功回个人中心态 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **写** `User.TelegramId(telegram_id)`（`index`，绑定唯一标识，写入当前会话用户）。
- **读唯一性校验** `User.TelegramId(telegram_id)`：`IsTelegramIdAlreadyTaken` 查是否已被其他 `User.Id` 占用。
- **读** `User.Id(id)`（当前会话用户）、`User.DeletedAt`（判是否已注销/软删除）。

### 6. 验收标准
- [ ] 无登录会话发起绑定 → 未登录阻断态（C1），不写 `TelegramId`。
- [ ] HMAC 校验不通过 → HMAC 校验失败态，拒绝绑定。
- [ ] 当前用户已注销 → 用户已注销错误态，拒绝绑定。
- [ ] 目标 `telegram_id` 已被其他账号绑定 → 拒绝 + 提示「该 Telegram 账户已被绑定」（`IsTelegramIdAlreadyTaken`）。
- [ ] 全部校验通过且 `telegram_id` 未占用 → 当前用户 `TelegramId` 被写入，302 跳 `/console/personal`。

### 7. 所触及页面状态（对齐 §B「Telegram 绑定」）
未登录阻断态（C1） · HMAC 校验失败态 · 用户已注销错误态 · Telegram 已被占用态（唯一性拒绝） · 绑定成功回个人中心态（302）。

---

## AC-8 2FA 启用 + 登录二次校验（配置期/登录期双状态机）

- **功能 ID / 优先级**：F-1033、F-1036 / P0~P2
- **来源**：FC-016（`controller/twofa.go`、`api-router.go:113-114 selfRoute 2fa/setup|enable`、`api-router.go:71 userRoute.POST(/login/2fa)`）
- **角色 / Owner**：登录用户（配置期）/ 匿名访客（登录期）；Owner 模块 = 账号与身份
- **触发**：用户进入安全设置开启 2FA；或已开 2FA 账号密码登录后进入第二步

### 1. 场景
2FA 是一个跨「配置期」与「登录期」的状态机。配置期：`setup` 返回 TOTP 密钥+二维码，用户扫码后输入首个 TOTP，正确才把 2FA 置为开启。登录期：已开 2FA 的账号在密码通过后，须 `POST /login/2fa` 传 TOTP 或备份码，正确才建会话。

### 2. 前置条件
- 配置期：用户已登录（过 C1）。
- 登录期：账号 `TwoFA.IsEnabled=true`，且已通过 AC-2 的密码第一步。
- 登录期过 `CriticalRateLimit`。

### 3. 主流程（对应 AC-8 节点 TF0→TFON / TF5→TFOK）
1. （配置期）用户进入安全设置（TF0），`POST 2fa/setup` 取密钥+二维码（TF1）。
2. 扫码后输入首个 TOTP（TF2），判首个 TOTP 是否正确（TF3）：正确则 2FA 状态置为开启（TF4）→ 2FA 已启用（TFON）。
3. （登录期）下次密码登录通过后（TF5），`POST /login/2fa` 输 TOTP 或备份码（TF6）。
4. 判 TOTP/备份码是否有效（TF7）：有效则建立会话（TF8）→ 2FA 登录成功（TFOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 配置期首个 TOTP 错误 | TF3-否 | 拒绝开启，状态不变 | 首个 TOTP 错误拒绝开启态 |
| 配置期首个 TOTP 正确 | TF3-是 | `IsEnabled` 置 true | 2FA 已启用态 |
| 登录期 TOTP/备份码无效 | TF7-否 | 拒绝，不建会话 | 第二步校验失败态 |
| 登录期 TOTP/备份码有效 | TF7-是 | 建立会话 | 2FA 登录成功态 |

### 5. 数据对象（复用 DATA-MODEL §12）
- **写** `TwoFA`：`UserId(user_id)`（`unique`）、`Secret(-)`（TOTP 密钥，`json:"-"` 不下发，setup 阶段生成）、`IsEnabled(is_enabled)`（首个 TOTP 校验通过置 true）、`FailedAttempts(failed_attempts)`、`LockedUntil(locked_until)`、`LastUsedAt(last_used_at)`。
- **读校验** `TwoFABackupCode`：`UserId(user_id)`、`CodeHash(-)`（备份码哈希，不下发）、`IsUsed(is_used)`（登录期备份码核销）、`UsedAt(used_at)`。
- **写会话**：登录期校验通过后 `setupLogin` 建会话（沿用 AC-2 会话）。

### 6. 验收标准
- [ ] 配置期首个 TOTP 错误 → 拒绝开启，`TwoFA.IsEnabled` 保持 false。
- [ ] 配置期首个 TOTP 正确 → `TwoFA.IsEnabled=true`，进 2FA 已启用态。
- [ ] 已开 2FA 账号登录期输错 TOTP/备份码 → 第二步校验失败态，不建会话。
- [ ] 登录期输入有效 TOTP → 建立会话，进 2FA 登录成功态。
- [ ] 登录期使用未用过的备份码 → 通过且该码 `IsUsed` 置 true 不可复用。

### 7. 所触及页面状态（对齐 §B「2FA 启用+登录」）
setup 展示密钥/二维码态 · 配置期/登录期双状态机 · 首个 TOTP 错误拒绝开启态 · 2FA 已启用态 · 登录第二步输入态（TOTP/备份码） · 第二步校验失败态 · 2FA 登录成功态。

---

## AC-9 Passkey 注册与无密码登录（begin/finish 两段挑战-应答）

- **功能 ID / 优先级**：F-1028、F-1029、F-1031 / P2
- **来源**：FC-015（`controller/passkey.go`、`api-router.go:90-91 selfRoute passkey/register/begin|finish`、`api-router.go:72-73 userRoute passkey/login/begin|finish`、`api-router.go:89/94 GET/DELETE /passkey`）
- **角色 / Owner**：登录用户（注册/删除）/ 匿名访客（登录）；Owner 模块 = 账号与身份
- **触发**：用户在安全设置注册 Passkey；或在登录页用 Passkey 无密码登录

### 1. 场景
Passkey 用 WebAuthn 挑战-应答，形态既不同于密码也不同于 TOTP：注册与登录都分 begin（取挑战）和 finish（提交应答）两段。注册 finish 校验通过才落库凭据；登录 finish 校验签名与已注册凭据匹配才建会话；凭据删除后该用户 passkey 登录立即失效。

### 2. 前置条件
- 注册分支：用户已登录（过 C1）。
- 登录分支：该用户已有落库的 Passkey 凭据，且 login/begin 过 `CriticalRateLimit`。

### 3. 主流程（对应 AC-9 节点 PK0→RGOK / LGOK）
1. 用户操作 Passkey（PK0），判注册还是登录（PKW）。
2. （注册）`register/begin` 取挑战（RG1）→ 浏览器创建凭据（RG2）→ `register/finish` 提交（RG3）。
3. 判凭据验证是否通过（RG4）：通过则落库凭据（RG5）→ Passkey 已注册（RGOK）。
4. （登录）`login/begin` 取挑战并过限流（LG1）→ 本地认证器签名（LG2）→ `login/finish` 校验签名（LG3）。
5. 判签名是否与已注册凭据匹配（LG4）：匹配则建立会话（LG5）→ Passkey 登录成功（LGOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 注册凭据验证不通过 | RG4-否 | 不落库 | 注册失败重试态 |
| 注册凭据验证通过 | RG4-是 | 落库 `PasskeyCredential` | Passkey 已注册态 |
| 登录签名与已注册凭据不匹配 | LG4-否 | 拒绝，不建会话 | 凭据校验失败拒绝态（`ErrFriendlyPasskeyNotFound`） |
| 登录签名匹配 | LG4-是 | 建立会话 | Passkey 登录成功态 |
| 删除凭据（F-1031） | DELETE /passkey | 移除本人凭据 | 删除后 passkey 登录失效 |

### 5. 数据对象（复用 DATA-MODEL §13）
- **写注册** `PasskeyCredential`：`UserID(user_id)`（`uniqueIndex`）、`CredentialID(credential_id)`（`varchar(512);uniqueIndex`，base64）、`PublicKey(public_key)`（`text`，base64）、`AttestationType(attestation_type)`、`AAGUID(aaguid)`、`SignCount(sign_count)`、`Transports(transports)`、`Attachment(attachment)`。
- **读登录校验** `PasskeyCredential.CredentialID(credential_id)` + `PublicKey(public_key)` 验签；不存在抛 `ErrPasskeyNotFound` / `ErrFriendlyPasskeyNotFound`。
- **删除** `PasskeyCredential`（按 `UserID` 移除本人凭据，删除后该用户无凭据）。

### 6. 验收标准
- [ ] register/finish 凭据验证不通过 → 不写 `PasskeyCredential`，进注册失败重试态。
- [ ] register/finish 验证通过 → `PasskeyCredential` 落库，`GET /passkey` 显示已注册凭据。
- [ ] login/finish 签名与已注册 `CredentialID` 不匹配 → 凭据校验失败拒绝态，不建会话。
- [ ] login/finish 签名匹配 → 建立会话，Passkey 登录成功态。
- [ ] 删除本人 Passkey 后再 passkey 登录 → 校验失败（无凭据），passkey 登录失效。

### 7. 所触及页面状态（对齐 §B「Passkey」）
register/begin 挑战态 · 凭据创建中态 · begin/finish 两段挑战-应答 · 注册失败重试态 · Passkey 已注册态 · login/begin 挑战态（过限流） · 凭据校验失败拒绝态 · Passkey 登录成功态。

---

## AC-10 管理端用户管理（ManageUser 角色越权护栏）

- **功能 ID / 优先级**：F-1010 / P1
- **来源**：FC-004（`controller.ManageUser`、`api-router.go:139 adminRoute.POST(/manage)`、AdminAuth、Role 优先级 root>admin>common）
- **角色 / Owner**：管理员 / Root；Owner 模块 = 账号与身份
- **触发**：管理员在用户列表选目标用户并选动作（启用/禁用/提升/删除）

### 1. 场景
管理员可对用户执行启用、禁用、提升、删除。核心风险是越权：admin 不能操作 root，也不能操作平级 admin；提升动作还不能把目标提到不低于操作者自己的角色。角色优先级闸门是该功能的核心判定。

### 2. 前置条件
- 操作者通过 AdminAuth（`adminRoute`）。
- 目标用户存在于 `User` 表。

### 3. 主流程（对应 AC-10 节点 M0→OK）
1. 管理员在用户列表选目标（M0），判是否有 AdminAuth（M1）：有则放行。
2. 选择动作：启用/禁用/提升/删除（M2）。
3. 判目标角色是否 ≥ 操作者角色（M3）：否（不越权）才放行。
4. 按动作类型分发（M4）：
   - 禁用→`Status` 置禁用（M5），目标无法登录。
   - 启用→`Status` 置启用（M6）。
   - 提升→判提升后是否仍不高于操作者（M7）：否则更新 `Role`（M8）。
   - 删除→删除用户（M9）。
5. 操作生效 → 列表刷新（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 无 AdminAuth | M1-否 | 拒绝 | 无权限态（403） |
| 目标角色 ≥ 操作者角色 | M3-是 | 拒绝越权操作 | 越权拒绝态（不可操作同级/更高角色） |
| 提升后角色 ≥ 操作者角色 | M7-是会越权 | 拒绝提升 | 提升越界拒绝态 |
| 禁用且不越权 | M5 | `Status` 置禁用 | 禁用生效态（目标无法登录） |
| 启用/合法提升/删除 | M6/M8/M9 | 更新对应字段 | 启用/提升/删除生效列表刷新态 |

### 5. 数据对象（复用 DATA-MODEL §1）
- **写** `User.Status(status)`：禁用动作置 `≠UserStatusEnabled`（目标不可登录）、启用动作置 `=UserStatusEnabled(1)`。
- **写** `User.Role(role)`：提升动作更新，守卫「提升后角色 < 操作者 `Role`」。
- **写删除** `User.DeletedAt`（删除动作，软删除）。
- **读护栏** 操作者 `User.Role` 与目标 `User.Role` 比较（越权护栏：不可操作 `目标角色 >= 操作者角色`；枚举 root>admin>common，§1 Role 枚举）。

### 6. 验收标准
- [ ] 非 AdminAuth 调用 `/api/user/manage` → 403 无权限态。
- [ ] admin 操作 root（目标角色 > 操作者）→ 越权拒绝态，目标不变。
- [ ] admin 操作平级 admin（目标角色 = 操作者）→ 越权拒绝态。
- [ ] 提升使目标角色 ≥ 操作者角色 → 提升越界拒绝态，`Role` 不变。
- [ ] 对合法目标禁用 → `Status≠1`，该目标登录被拒（对齐 AC-2 封禁闸门）。
- [ ] 对合法目标删除 → `DeletedAt` 写入，列表不再返回该用户。

### 7. 所触及页面状态（对齐 §B「管理端用户管理」）
无 AdminAuth 拒绝态（403） · 动作选择态（启用/禁用/提升/删除） · 角色优先级闸门 · 越权拒绝态 · 提升越界拒绝态 · 禁用生效态（目标无法登录） · 启用/提升/删除生效列表刷新态。

---

## AC-11 OAuth 绑定查看与解绑（本人 + 管理端，防锁死）

- **功能 ID / 优先级**：F-1026、F-1027 / P2
- **来源**：FC-014（`controller.UnbindCustomOAuth`、`api-router.go:124 selfRoute.DELETE(/oauth/bindings/:provider_id)`、`api-router.go:134 GET /:id/oauth/bindings`、`api-router.go:135 DELETE /:id/oauth/bindings/:provider_id`）
- **角色 / Owner**：登录用户（本人）/ 管理员；Owner 模块 = 账号与身份
- **触发**：用户在个人中心查看/解绑本人 OAuth 绑定；或管理员在用户详情解绑指定用户绑定

### 1. 场景
解绑这一个动作有两个权限入口：本人入口只能动自己的绑定；管理端入口需 AdminAuth 才能查看/解绑指定用户。本人解绑还要防锁死——若解绑后没有任何可登录方式了，要先做二次确认。解绑后该 provider 不再能登录此账号。

### 2. 前置条件
- 本人入口：已登录会话；操作目标绑定 `UserId` = 当前用户。
- 管理端入口：通过 AdminAuth。

### 3. 主流程（对应 AC-11 节点 UB0→SBOK / ABOK）
1. 进入 OAuth 绑定管理（UB0），判本人入口还是管理端入口（UB1）。
2. （本人）列出本人全部绑定（SB1）→ 选择要解绑的 provider（SB2）。
3. 判该绑定是否属于本人（SB3）：是则判解绑后是否仍有可登录方式（SB4）。
4. 仅剩此方式（SB4-否）→ 提示将无法登录并二次确认（SBW）→ 确认后到删除（SB5）；仍有其他方式（SB4-是）→ 直接删除绑定记录（SB5）→ 本人解绑成功（SBOK）。
5. （管理端）判是否有 AdminAuth（AB1）：有则查询指定用户绑定列表（AB2）→ 管理员解绑指定 provider（AB3）→ 管理端解绑成功（ABOK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 本人解绑非本人绑定 | SB3-否 | 拒绝 | 越权解绑拒绝态 |
| 解绑后仅剩此一种登录方式 | SB4-否 | 弹二次确认 | 仅剩一种登录方式二次确认态（防锁死） |
| 解绑后仍有其他登录方式 | SB4-是 | 直接删除绑定 | 本人解绑成功态 |
| 管理端无 AdminAuth | AB1-否 | 拒绝 | 管理端无权限态 |
| 管理端有 AdminAuth | AB1-是 | 查询并解绑指定用户 | 管理端解绑成功态 |

### 5. 数据对象（复用 DATA-MODEL §11）
- **读列表** `UserOAuthBinding`：本人按 `UserId(user_id)`=当前用户过滤；管理端按目标 `UserId(user_id)` 查询全部绑定。
- **删除** `UserOAuthBinding`（按 `UserId(user_id)` + `ProviderId(provider_id)`，`uniqueIndex:ux_user_provider`）；删除后该 `ProviderUserId(provider_user_id)` 不再能登录此账号。
- **读防锁死判定** 当前用户的可登录方式集合（`User.Password(password)` 是否设、其余 `UserOAuthBinding` 与内建 provider 标识如 `GitHubId(github_id)` 等是否仍在）。

### 6. 验收标准
- [ ] 本人尝试解绑 `UserId` ≠ 自己的绑定 → 越权解绑拒绝态，绑定不变。
- [ ] 本人解绑且解绑后仍有其他可登录方式 → 直接删除 `UserOAuthBinding`，本人解绑成功态。
- [ ] 本人解绑且解绑后无任何可登录方式 → 先进二次确认态，确认后才删除。
- [ ] 解绑成功后用该 provider 登录 → 不再能登录此账号。
- [ ] 非 AdminAuth 调用管理端解绑 → 管理端无权限态。
- [ ] AdminAuth 解绑指定用户绑定 → 该用户对应 `UserOAuthBinding` 记录消失。

### 7. 所触及页面状态（对齐 §B「OAuth 绑定管理」）
本人绑定列表态 · 越权解绑拒绝态 · 仅剩一种登录方式二次确认态（防锁死） · 本人解绑成功态 · 管理端无权限态 · 管理端用户绑定列表态 · 管理端解绑成功态。

---

## AC-12 用户登出（会话失效，幂等）

- **功能 ID / 优先级**：F-1003 / P1
- **来源**：FC-001（`controller.Logout`、`api-router.go:75 userRoute.GET(/logout)`）
- **角色 / Owner**：登录用户；Owner 模块 = 账号与身份
- **触发**：登录用户点「退出登录」

### 1. 场景
登录用户主动退出。系统清除当前会话，使后续凭会话的鉴权请求失效；无会话时调用也不报错，保持幂等，避免重复点击或并发退出造成异常。

### 2. 前置条件
- 通常有有效登录会话（无会话调用走幂等分支）。

### 3. 主流程
1. 登录用户 `GET /api/user/logout`。
2. 清除当前会话（销毁 session 内携带的 `Id`/`Role`）。
3. 返回成功，后续访问 `/api/user/self` 按未授权处理。

### 4. 分支与异常
| 触发条件 | 系统行为 | 用户可见结果 |
|---|---|---|
| 有有效会话 | 清除会话 | 登出成功，回到未登录态 |
| 无会话调用 | 幂等返回，不报错 | 仍返回成功（幂等） |

### 5. 数据对象（复用 DATA-MODEL §1）
- **清除会话**：销毁 session 内携带的 `User.Id(id)`、`User.Role(role)`（无独立会话表，登出即令该 session 失效）。
- **不写库**：登出不修改 `User` 任何持久字段。

### 6. 验收标准
- [ ] 有会话调用 `/logout` → 会话失效，随后 `/api/user/self` 返回未授权。
- [ ] 无会话调用 `/logout` → 仍返回成功（幂等），不报错。

### 7. 所触及页面状态（对齐 §B 账号区，复用登录态切换）
登录态 → 登出后未登录态（幂等成功，无新增失败态）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-1001 | AC-1 |
| F-1002 | AC-2 |
| F-1003 | AC-12 |
| F-1004 | AC-1 |
| F-1005 | AC-1 |
| F-1006 | AC-3 |
| F-1007 | AC-3 |
| F-1010 | AC-10 |
| F-1016 | AC-4 |
| F-1018 | AC-4 |
| F-1019 | AC-4 |
| F-1020 | AC-4 |
| F-1021 | AC-5 |
| F-1022 | AC-5 |
| F-1025 | AC-4 |
| F-1026 | AC-11 |
| F-1027 | AC-11 |
| F-1028 | AC-9 |
| F-1029 | AC-9 |
| F-1031 | AC-9 |
| F-1033 | AC-8 |
| F-1036 | AC-8 |
| F-1051 | AC-6 |
| F-1052 | AC-7 |
| F-1053 | AC-6 |
| F-1054 | AC-7 |

无 `[BLOCKER]`。
