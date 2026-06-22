# FL-account — 账号与身份（D1 + D4 Telegram登录）流程图

> 分片：账号与身份 D1（F-1001~F-1038）+ Telegram 登录 Bot D4（F-1051~F-1054）。
> 角色：访客 / 注册用户 / 登录用户 / 管理员 / Root / 系统。
> 跨切面契约见 `../OVERALL-FLOW.md §3`：C2 externalJump（OAuth 外跳）、C3 二次验证、C4 Turnstile（注册/登录前置）。本文件不重画 C4，仅在节点标注「过 C4」。

---

## 场景 AC-1 · 邮箱密码注册（含验证码 + 邀请归因）（F-1001/F-1004/F-1005/F-1040）

> 业务规则：`RegisterEnabled=false` 拒绝；用户名重复 `MsgUserExists`；`EmailVerificationEnabled` 开启时验证码错/过期拒绝；携带有效 `aff_code` 则写 `InviterId`；成功后 `Role=common`、`Quota=QuotaForNewUser`、生成 4 位 `aff_code`。多重串行校验链。

```mermaid
flowchart TD
  R0([访客进入注册页]) --> R1{RegisterEnabled?}
  R1 -->|否| RE0[注册已关闭态]:::err
  R1 -->|是| R2[填用户名/密码/邮箱 · 过C4]
  R2 --> R3{需邮箱验证码?}
  R3 -->|EmailVerificationEnabled| R4[请求发送验证码]
  R4 --> R5{验证码限流?}
  R5 -->|被限流| RE1[发码过频 → 稍后重试]:::err
  R5 -->|放行| R6[输入验证码]
  R6 --> R7{验证码匹配且未过期?}
  R7 -->|否| RE2[验证码错误/过期态]:::err
  R7 -->|是| R8
  R3 -->|关闭| R8[提交注册]
  R8 --> R9{用户名已存在?}
  R9 -->|是| RE3[用户名重复 MsgUserExists]:::err
  R9 -->|否| R10{携带 aff_code?}
  R10 -->|有效| R11[解析 InviterId 并归因]
  R10 -->|无/无效| R12[InviterId=0]
  R11 --> R13[创建 common 用户 + QuotaForNewUser + 生成aff_code]
  R12 --> R13
  R13 --> OK([注册成功 → 自动登录态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-1 邮箱注册）：
- 注册已关闭态（RegisterEnabled=false） ← 异常终态
- 注册表单默认态（过 C4 人机校验）
- 发码过频限流态 ← 异常
- 验证码错误/过期态 ← 异常
- 用户名重复态（MsgUserExists） ← 异常
- 邀请归因态（带 aff_code，提示「来自邀请」）
- 注册成功自动登录态 ← 终态

---

## 场景 AC-2 · 邮箱密码登录（封禁拦截）（F-1002）

> 业务规则：凭证正确**且** `Status=UserStatusEnabled` 才建会话；被封禁用户登录被拒；过 C4 + CriticalRateLimit。短链 + 状态闸门。

```mermaid
flowchart TD
  L0([访客进入登录页]) --> L1[输入账号密码 · 过C4]
  L1 --> L2{凭证正确?}
  L2 -->|否| LE1[账号或密码错误态]:::err
  L2 -->|是| L3{Status=Enabled?}
  L3 -->|否 被封禁| LE2[账号已封禁 → 拒绝登录]:::err
  L3 -->|是| L4{该账号已开 2FA?}
  L4 -->|是| L5[/转 2FA 第二步 见 AC-8/]
  L4 -->|否| L6[setupLogin 建立会话]
  L6 --> OK([登录成功 → 进入控制台]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-2 邮箱登录）：
- 登录表单默认态（过 C4）
- 账号或密码错误态 ← 异常
- 账号已封禁拒绝态 ← 异常
- 转 2FA 第二步态（已开 2FA，跳 AC-8）
- 登录成功进控制台态 ← 终态

---

## 场景 AC-3 · 找回密码（发重置邮件→提交新密码）（F-1006/F-1007）

> 业务规则：向**已注册**邮箱发重置令牌（非注册邮箱不发有效令牌）；提交 reset 时令牌无效/过期拒绝；两段式，跨「发件」与「重置」两个时刻。

```mermaid
flowchart TD
  F0([访客点「忘记密码」]) --> F1[输入邮箱 · 过C4]
  F1 --> F2{邮箱已注册?}
  F2 -->|否| F3[统一提示已发送 · 实际不发有效令牌]:::err
  F2 -->|是| F4[发送重置令牌到邮箱]
  F4 --> F5([等待用户点邮件链接])
  F5 --> F6[打开重置页填新密码]
  F6 --> F7{令牌有效且未过期?}
  F7 -->|否| FE[令牌无效/过期 → 重新申请]:::err
  F7 -->|是| F8[更新密码 · 旧密码失效]
  F8 --> OK([重置成功 → 引导用新密码登录]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-3 找回密码）：
- 填邮箱默认态（过 C4）
- 未注册邮箱统一提示态（防枚举，不发有效令牌） ← 异常处理
- 已发送等待态（邮件已寄出）
- 填新密码态（令牌跳转后）
- 令牌无效/过期态（重新申请） ← 异常
- 重置成功引导登录态 ← 终态

---

## 场景 AC-4 · 第三方 OAuth 登录/注册/绑定（多 provider 分发）（F-1016/F-1018/F-1019/F-1020/F-1025）

> 业务规则：先 `GET /oauth/state` 生成 CSRF state（可带 aff），回调校验 state；provider 未启用拒绝；`RegisterEnabled=false` 时不创建新用户；已登录会话走绑定分支；LinuxDO 额外校验信任级。复用契约 C2（externalJump 四态）。

```mermaid
flowchart TD
  O0([点击第三方登录]) --> O1[GET /oauth/state 生成CSRF·暂存aff]
  O1 --> O2[/外跳 provider 授权 · 复用C2/]
  O2 --> O3{回调 state 匹配?}
  O3 -->|否| OE1[state 不匹配 403 MsgOAuthStateInvalid]:::err
  O3 -->|是| O4{provider 已启用?}
  O4 -->|否| OE2[provider 未启用拒绝]:::err
  O4 -->|是| O5{当前已登录会话?}
  O5 -->|是 走绑定| O6{该外部ID已被占用?}
  O6 -->|是| OE3[已被绑定 MsgOAuthAlreadyBound]:::err
  O6 -->|否| O7[写 user_oauth_bindings 绑定]
  O7 --> OKB([绑定成功态]):::term
  O5 -->|否 走登录/注册| O8{外部ID已有账号?}
  O8 -->|是| O11{LinuxDO 且信任级达标?}
  O11 -->|不达标| OE4[信任级过低 MsgOAuthTrustLevelLow]:::err
  O11 -->|达标/非LinuxDO| O12[登录已有账号]
  O8 -->|否| O9{RegisterEnabled?}
  O9 -->|否| OE5[注册关闭不创建新用户]:::err
  O9 -->|是| O10[创建账号+写绑定·按aff归因]
  O10 --> OKL([登录成功态]):::term
  O12 --> OKL
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-4 OAuth 登录/绑定）：
- 外跳授权前确认态/跳转中态（复用 C2）
- state 不匹配拒绝态（403） ← 异常
- provider 未启用态 ← 异常
- 已被绑定态（MsgOAuthAlreadyBound） ← 异常
- 绑定成功态 ← 终态
- 信任级过低态（LinuxDO） ← 异常
- 注册关闭不创建态 ← 异常
- 登录成功态（已有账号 / 新建账号 + aff 归因） ← 终态

---

## 场景 AC-5 · WeChat 扫码授权登录/绑定（F-1021/F-1022）

> 业务规则：先 `GET /oauth/wechat` 发起授权获取 `wechat_id`，再 `POST /oauth/wechat/bind` 完成；已登录则绑定，未登录且 wechat_id 存在则登录对应用户；微信未配置不可用。扫码态 + 轮询特性，区别于标准 OAuth 重定向。

```mermaid
flowchart TD
  W0([点击微信登录]) --> W1{微信功能已配置?}
  W1 -->|否| WE0[未配置不可用态]:::err
  W1 -->|是| W2[GET /oauth/wechat 展示二维码]
  W2 --> W3([等待用户扫码授权])
  W3 --> W4{扫码授权成功?}
  W4 -->|超时/取消| WE1[二维码过期 → 刷新重扫]:::err
  W4 -->|是| W5[POST /wechat/bind 携授权码]
  W5 --> W6{当前已登录?}
  W6 -->|是| W7[绑定 wechat_id 到当前账号]
  W7 --> WOKB([微信绑定成功态]):::term
  W6 -->|否| W8{wechat_id 已对应账号?}
  W8 -->|是| W9[登录该账号]
  W9 --> WOKL([微信登录成功态]):::term
  W8 -->|否| WE2[无对应账号 → 引导注册/绑定]:::err
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-5 微信扫码）：
- 微信未配置不可用态 ← 异常终态
- 二维码展示等待扫码态
- 二维码过期刷新态 ← 异常
- 微信绑定成功态（已登录绑定） ← 终态
- 微信登录成功态（未登录且有对应账号） ← 终态
- 无对应账号引导态 ← 异常

---

## 场景 AC-6 · Telegram Widget 登录（HMAC 防伪）（F-1051/F-1053）

> 业务规则：`TelegramOAuthEnabled=false` 返回未开启；用 `key=SHA256(BotToken)` 对**按字典序拼接的非 hash 参数**做 HMAC-SHA256，与传入 `hash` 一致才登录对应 `telegram_id`；篡改任一参数 → hash 不等 → 拒绝。安全校验密集型。

```mermaid
flowchart TD
  TG0([Telegram Widget 回传参数]) --> TG1{TelegramOAuthEnabled?}
  TG1 -->|否| TGE0[Telegram 登录未开启]:::err
  TG1 -->|是| TG2[取非hash参数按字典序拼接]
  TG2 --> TG3[key=SHA256BotToken 派生]
  TG3 --> TG4[计算 HMAC-SHA256data]
  TG4 --> TG5{计算hash == 传入hash?}
  TG5 -->|否 被篡改/伪造| TGE1[hash不匹配 → 无效请求拒绝]:::err
  TG5 -->|是| TG6{telegram_id 已有账号?}
  TG6 -->|是| TG7[登录该 telegram 用户]
  TG7 --> TGOK([Telegram 登录成功态]):::term
  TG6 -->|否| TG8{RegisterEnabled?}
  TG8 -->|否| TGE2[注册关闭不创建]:::err
  TG8 -->|是| TG9[创建账号绑定 telegram_id]
  TG9 --> TGOK
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-6 Telegram 登录）：
- Telegram 登录未开启态 ← 异常终态
- hash 不匹配拒绝态（防伪失败） ← 异常
- Telegram 登录成功态（已有账号） ← 终态
- 注册关闭不创建态 ← 异常
- 新建账号登录成功态 ← 终态

---

## 场景 AC-7 · Telegram 绑定到现有账号（唯一性校验）（F-1052/F-1054）

> 业务规则：登录用户 `GET /oauth/telegram/bind`，HMAC 校验后将 `telegram_id` 写入当前用户；该 telegram 已被绑定 → `IsTelegramIdAlreadyTaken` 返回「已被绑定」；用户已注销报错；成功后 302 跳 `/console/personal`。绑定唯一性闸门是核心判定。

```mermaid
flowchart TD
  B0([登录用户点「绑定 Telegram」]) --> B1{有登录会话?}
  B1 -->|否| BE0[未登录 → 复用契约C1]:::err
  B1 -->|是| B2[Widget 回传 + HMAC 校验]
  B2 --> B3{HMAC 通过?}
  B3 -->|否| BE1[校验失败拒绝绑定]:::err
  B3 -->|是| B4{当前用户已注销?}
  B4 -->|是| BE2[用户已注销错误]:::err
  B4 -->|否| B5{telegram_id 已被他人占用?}
  B5 -->|是| BE3[该Telegram已被绑定 → 拒绝]:::err
  B5 -->|否| B6[写入 user.TelegramId]
  B6 --> B7[302 跳转 /console/personal]
  B7 --> OK([绑定成功 · 回个人中心态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-7 Telegram 绑定）：
- 未登录阻断态（复用 C1） ← 异常
- HMAC 校验失败态 ← 异常
- 用户已注销错误态 ← 异常
- Telegram 已被占用态（唯一性拒绝） ← 异常
- 绑定成功回个人中心态（302） ← 终态

---

## 场景 AC-8 · 2FA 启用 + 登录二次校验（F-1033/F-1036）

> 业务规则：`setup` 返回 TOTP 密钥，`enable` 校验首个 TOTP 通过才开启；登录时已开 2FA 的账号须 `POST /login/2fa` 传 TOTP/备份码，错误拒绝。一个状态机跨「配置期」与「登录期」。

```mermaid
flowchart TD
  TF0([用户进入安全设置]) --> TF1[POST 2fa/setup 取密钥+二维码]
  TF1 --> TF2[扫码后输入首个TOTP]
  TF2 --> TF3{首个TOTP正确?}
  TF3 -->|否| TFE1[TOTP错误 → 拒绝开启]:::err
  TF3 -->|是| TF4[2FA 状态置为开启]
  TF4 --> TFON([2FA 已启用态]):::term
  TFON -.下次登录.-> TF5([密码登录通过后])
  TF5 --> TF6[POST /login/2fa 输TOTP或备份码]
  TF6 --> TF7{TOTP/备份码有效?}
  TF7 -->|否| TFE2[第二步校验失败拒绝]:::err
  TF7 -->|是| TF8[建立会话]
  TF8 --> TFOK([2FA登录成功态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-8 2FA 启用+登录）：
- setup 展示密钥/二维码态
- 首个 TOTP 错误拒绝开启态 ← 异常
- 2FA 已启用态 ← 终态（配置期）
- 登录第二步输入态（TOTP/备份码）
- 第二步校验失败态 ← 异常
- 2FA 登录成功态 ← 终态（登录期）

---

## 场景 AC-9 · Passkey 注册与无密码登录（F-1028/F-1029/F-1031）

> 业务规则：注册 `register/begin→finish` WebAuthn 凭据；登录 `login/begin→finish` 无密码校验通过即建会话；删除后 passkey 登录失效。begin/finish 两段挑战-应答，与密码/2FA 形态不同。

```mermaid
flowchart TD
  PK0([用户操作 Passkey]) --> PKW{注册 or 登录?}
  PKW -->|注册| RG1[register/begin 取挑战]
  RG1 --> RG2[浏览器创建凭据]
  RG2 --> RG3[register/finish 提交]
  RG3 --> RG4{凭据验证通过?}
  RG4 -->|否| RGE[注册失败 → 重试]:::err
  RG4 -->|是| RG5[落库凭据]
  RG5 --> RGOK([Passkey已注册态]):::term
  PKW -->|登录| LG1[login/begin 取挑战 · 过限流]
  LG1 --> LG2[本地认证器签名]
  LG2 --> LG3[login/finish 校验签名]
  LG3 --> LG4{签名与已注册凭据匹配?}
  LG4 -->|否| LGE[凭据校验失败拒绝]:::err
  LG4 -->|是| LG5[建立会话]
  LG5 --> LGOK([Passkey登录成功态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-9 Passkey）：
- register/begin 挑战态
- 凭据创建中态
- 注册失败重试态 ← 异常
- Passkey 已注册态 ← 终态
- login/begin 挑战态（过限流）
- 凭据校验失败拒绝态 ← 异常
- Passkey 登录成功态 ← 终态

---

## 场景 AC-10 · 管理端用户管理（ManageUser 越权护栏）（F-1010）

> 业务规则：`POST /api/user/manage` 启用/禁用/提升/删除；需 AdminAuth；**不可对同级或更高角色越权**（admin 不能操作 root，不能操作平级 admin）。角色优先级闸门是核心。

```mermaid
flowchart TD
  M0([管理员在用户列表选目标]) --> M1{有 AdminAuth?}
  M1 -->|否| ME0[无权限 403]:::err
  M1 -->|是| M2[选择动作 启用/禁用/提升/删除]
  M2 --> M3{目标角色 >= 操作者角色?}
  M3 -->|是 越权| ME1[不可操作同级/更高角色拒绝]:::err
  M3 -->|否| M4{动作类型?}
  M4 -->|禁用| M5[Status置禁用 → 目标无法登录]
  M4 -->|启用| M6[Status置启用]
  M4 -->|提升| M7{提升后不高于操作者?}
  M7 -->|是会越权| ME2[提升越界拒绝]:::err
  M7 -->|否| M8[更新角色]
  M4 -->|删除| M9[删除用户]
  M5 --> OK([操作生效 · 列表刷新态]):::term
  M6 --> OK
  M8 --> OK
  M9 --> OK
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-10 管理用户）：
- 无 AdminAuth 拒绝态（403） ← 异常
- 动作选择态（启用/禁用/提升/删除）
- 越权拒绝态（操作同级/更高角色） ← 异常
- 提升越界拒绝态 ← 异常
- 禁用生效态（目标无法登录） ← 终态
- 启用/提升/删除生效列表刷新态 ← 终态

---

## 场景 AC-11 · OAuth 绑定查看与解绑（本人 + 管理端）（F-1026/F-1027）

> 业务规则：本人 `DELETE /self/oauth/bindings/:provider_id` 仅能解绑本人；管理端 `GET/DELETE /user/:id/oauth/bindings` 需 AdminAuth。解绑后该 provider 不再能登录此账号。一个动作两个权限入口 + 末路保护。

```mermaid
flowchart TD
  UB0([进入 OAuth 绑定管理]) --> UB1{本人入口 or 管理端入口?}
  UB1 -->|本人 self| SB1[列出本人全部绑定]
  SB1 --> SB2[选择要解绑的 provider]
  SB2 --> SB3{该绑定属于本人?}
  SB3 -->|否| SBE[越权 → 拒绝]:::err
  SB3 -->|是| SB4{解绑后仍有可登录方式?}
  SB4 -->|否 仅剩此方式| SBW[提示将无法登录 → 二次确认]
  SBW --> SB5
  SB4 -->|是| SB5[删除绑定记录]
  SB5 --> SBOK([本人解绑成功态]):::term
  UB1 -->|管理端 admin| AB1{有 AdminAuth?}
  AB1 -->|否| ABE[无权限拒绝]:::err
  AB1 -->|是| AB2[查询指定用户绑定列表]
  AB2 --> AB3[管理员解绑指定 provider]
  AB3 --> ABOK([管理端解绑成功态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（AC-11 OAuth 绑定管理）：
- 本人绑定列表态
- 越权解绑拒绝态 ← 异常
- 仅剩一种登录方式二次确认态（防锁死）
- 本人解绑成功态 ← 终态
- 管理端无权限态 ← 异常
- 管理端用户绑定列表态
- 管理端解绑成功态 ← 终态
