# PRD — 公开与营销站点（FL-public）

> 分片：公开营销站（公开站点能力域）。对应流程图 `flow/FL-public.md`（6 图）、状态矩阵 `PAGE-STATE-MATRIX.md`。
> 数据对象字段一律复用 `DATA-MODEL.md §15 Option`，公开文本来源 `LegalSetting`（`GetUserAgreement`/`GetPrivacyPolicy`）。
> 跨切面契约见 `OVERALL-FLOW.md §3`：C1 未登录先登录、C2 externalJump、C4 Turnstile。
> 本片覆盖功能 ID：**F-4039 / F-4040 / F-4041 / F-4042 / F-4043 / F-4044**。

---

## PUB-1 营销首页公开状态聚合（按开关条件注入可选区块）

- **功能 ID / 优先级**：F-4039 / P1
- **来源**：FC-121（`controller/misc.go GetStatus`，返回 `system_name/logo/footer_html/register_enabled/checkin_enabled/user_agreement_enabled/theme` 等；WEBSITE-COVERAGE §3 页脚「基于 New API」）
- **角色 / Owner**：访客 Guest；Owner 模块 = 公开站点
- **触发**：访客打开营销首页，前端无鉴权地请求 `GET /api/status`（GetStatus）

### 1. 场景
首页能渲染哪些内容完全由 `GetStatus` 这一个公开聚合接口决定。它返回必备区（系统名/Logo/页脚/登录方式开关），并按 `ApiInfoEnabled / AnnouncementsEnabled / FAQEnabled` 三个开关**条件注入**可选区块——开关关则对应区块不出现在响应里。敏感配置（OAuth Secret、各类 Key）一律不进此响应。

### 2. 前置条件
- 无需登录（匿名公开可读）。
- 后端 `OptionMap` 已 `InitOptionMap()` 装载。
- 各可选开关键（`ApiInfoEnabled/AnnouncementsEnabled/FAQEnabled`）已落库 `Option` KV。

### 3. 主流程（对应 P-1 节点 S0→DONE/DONE2）
1. 访客打开首页，请求 `GetStatus`，进加载骨架态（SK）。
2. `GetStatus` 返回成功（R-是）→ 渲染必备区：系统名/Logo/页脚/登录方式开关（BASE）。
3. 判 `ApiInfoEnabled`（C1）：true 注入 API 信息区块（INJ1）；false 隐藏（SKIP1）。
4. 判 `AnnouncementsEnabled`（C2）：true 注入公告区块（INJ2）；false 隐藏（SKIP2）。
5. 判 `FAQEnabled`（C3）：true 注入 FAQ 区块（INJ3）→ 完整渲染态（DONE，含可选区）；false → 基础渲染态（DONE2，仅必备区）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `GetStatus` 请求失败 | R-否 | 降级为默认壳 + 提供重试 | 配置拉取失败降级态 |
| `ApiInfoEnabled=false` | C1-否 | 响应不含 API 信息字段 | 隐藏 API 信息区 |
| `AnnouncementsEnabled=false` | C2-否 | 响应不含公告字段 | 隐藏公告区 |
| `FAQEnabled=false` | C3-否 | 响应不含 FAQ 字段 | 隐藏 FAQ 区，仅必备区 |

### 5. 数据对象（复用 DATA-MODEL §15 Option）
- **读** `Option` KV：`system_name`、`logo`、`footer_html`、`register_enabled`、`checkin_enabled`、`user_agreement_enabled`、`privacy_policy_enabled`、`theme`、`ApiInfoEnabled`、`AnnouncementsEnabled`、`FAQEnabled`、各 OAuth 启用开关（`GitHubOAuthEnabled` 等）。
- **不返回**：任何以 `Token/Secret/Key/secret/api_key` 结尾的敏感键（与 OP-2 `GetOptions` 同口径）。
- **返回结构**：必备字段恒定返回；可选区块字段仅在对应开关 true 时存在。

### 6. 验收标准
- [ ] 匿名（无 JWT）请求 `GetStatus` 返回 200，且含 `system_name`、`logo`、`footer_html`、`register_enabled` 字段。
- [ ] 响应体中不含任何以 `Token`/`Secret`/`Key` 结尾的键（断言无 OAuth Secret、无渠道 Key）。
- [ ] 置 `ApiInfoEnabled=true` → 响应含 API 信息区字段；置 false → 响应不含该字段。
- [ ] 置 `AnnouncementsEnabled=false` 且 `FAQEnabled=false` → 首页仅渲染必备区（基础渲染态），无公告/FAQ 区块。
- [ ] 三可选开关全 true → 首页同时含 API 信息/公告/FAQ 三区块（完整渲染态）。

### 7. 所触及页面状态（对齐 P-1 首页状态聚合）
加载骨架态 · 配置拉取失败降级态（默认壳+重试）· 基础渲染态（必备区，可选开关全关）· 含 API 信息区态 · 含公告区态 · 含 FAQ 区态 · 完整渲染态（多可选区注入）。

---

## PUB-2 首页对话框 Playground 试用入口（未登录引导登录 + 草稿保留）

- **功能 ID / 优先级**：F-4040 / P2
- **来源**：FC-122（WEBSITE-COVERAGE §4 首页对话输入框 placeholder「问点什么…」；后端落地 `controller/playground.go Playground`，`/pg` 挂 UserAuth）
- **角色 / Owner**：访客/登录用户；Owner 模块 = 公开站点（衔接 Playground）
- **触发**：访客在首页对话框输入文本后点「发送」

### 1. 场景
首页对话框是 Playground 的诱导入口，placeholder 为「问点什么…」。点「发送」要进入 `/pg` Playground，而该路由需 `UserAuth`。因此未登录点发送须先经契约 C1 跳登录；登录成功携带输入进 Playground；放弃登录则返回首页并保留输入草稿（不丢用户已输入内容）。

### 2. 前置条件
- 首页已渲染对话框（依赖 PUB-1 `GetStatus`）。
- 用户在对话框已输入文本（可为空，空文本同样走入口判定）。
- Playground 路由 `/pg` 受 UserAuth 保护（契约 C1）。

### 3. 主流程（对应 P-2 节点 D0→OK）
1. 访客在首页对话框输入（D0），点「发送」（D1）。
2. 判当前是否已登录（L）。
3. 未登录（L-否）→ 复用契约 C1 跳登录（B1），保留输入草稿。
4. 登录成功（L2-是）→ 携输入进入 Playground（PG）。
5. 已登录（L-是）→ 直接携输入进入 Playground（PG）→ Playground 对话试用态（OK）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 未登录点发送 | L-否 | 触发 C1 鉴权闸门，跳登录，暂存输入 | 未登录阻断态（跳登录，草稿保留） |
| 登录页放弃登录 | L2-否 | 返回首页，回填草稿 | 放弃登录返回态（草稿回填首页） |
| 已登录点发送 | L-是 | 携输入直达 Playground | Playground 试用态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option / §1 User）
- **读** `Option`：首页对话框 placeholder 文案及入口可见性来自 `GetStatus`（见 PUB-1）。
- **会话** `User`（鉴权态）：是否持有有效 JWT 决定走 C1 还是直达。
- **前端态**：对话框输入草稿在跳登录/放弃登录间保留（前端会话内暂存，不落库）。

### 6. 验收标准
- [ ] 首页对话框 placeholder 文案为「问点什么…」。
- [ ] 未登录点「发送」→ 跳转登录（C1），不进入 Playground。
- [ ] 跳登录前已输入的文本在登录成功后被携带进 Playground（草稿不丢）。
- [ ] 登录页放弃登录返回首页 → 对话框仍显示原输入草稿。
- [ ] 已登录点「发送」→ 直接进入 Playground 对话视图（不经登录页）。

### 7. 所触及页面状态（对齐 P-2 Playground 入口）
首页对话框默认态（placeholder「问点什么…」）· 未登录阻断态（跳登录，保留草稿，复用 C1）· 放弃登录返回态（草稿回填首页）· Playground 试用态（携输入进入对话）。

---

## PUB-3 用户协议公开页（入口开关 + 正文空判）

- **功能 ID / 优先级**：F-4041 / P1
- **来源**：FC-123（`controller/misc.go GetUserAgreement` 返回 `GetLegalSettings().UserAgreement`；入口由 `GetStatus.user_agreement_enabled` 控制；WEBSITE-COVERAGE §2 page-0002 `/agreement`）
- **角色 / Owner**：访客 Guest；Owner 模块 = 公开站点
- **触发**：访客访问 `/agreement`

### 1. 场景
`/agreement` 公开渲染用户协议正文，数据源是 `GetUserAgreement`（取 `LegalSetting.UserAgreement`）。入口受 `user_agreement_enabled` 单一开关控制：关闭则入口隐藏并 404/重定向首页。正文可能未配置（空字符串），此时显示空内容占位而非报错。

### 2. 前置条件
- 无需登录（匿名公开访问）。
- `Option.user_agreement_enabled` 已落库（默认决定入口可见性）。
- `LegalSetting.UserAgreement` 文本可为空。

### 3. 主流程（对应 P-3 节点 A0→SHOW/EMPTY）
1. 访客访问 `/agreement`（A0）。
2. 判 `user_agreement_enabled`（E）：false → 入口隐藏，404/重定向首页（H）。
3. true → 请求 `GetUserAgreement` 正文（F）。
4. 判正文非空（N）：空 → 协议空内容占位态（EMPTY）；非空 → 协议正文渲染态（SHOW）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `user_agreement_enabled=false` | E-否 | 隐藏入口，404 或重定向首页 | 入口隐藏态 |
| 正文为空字符串 | N-否 | 返回空内容，渲染占位 | 协议空内容占位态 |
| 正文非空 | N-是 | 渲染 `UserAgreement` 文本 | 协议正文渲染态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option + LegalSetting）
- **读开关** `Option.user_agreement_enabled`（bool，控制入口）。
- **读正文** `LegalSetting.UserAgreement`（string，`GetUserAgreement` 返回；未设置为空串）。

### 6. 验收标准
- [ ] `user_agreement_enabled=false` → 访问 `/agreement` 返回 404 或重定向首页，不渲染正文。
- [ ] `user_agreement_enabled=true` 且 `UserAgreement` 非空 → 渲染该正文文本。
- [ ] `user_agreement_enabled=true` 且 `UserAgreement=""` → 返回空内容占位（不报 500、不显示其他页内容）。
- [ ] 匿名（无 JWT）访问 `/agreement` 可正常渲染（无需鉴权）。

### 7. 所触及页面状态（对齐 P-3 用户协议页）
入口隐藏态（user_agreement_enabled=false → 404/回首页）· 协议空内容占位态（正文未配置）· 协议正文渲染态。

---

## PUB-4 隐私政策公开页（独立数据源 + 版本字段判定）

- **功能 ID / 优先级**：F-4042 / P1
- **来源**：FC-124（`controller/misc.go GetPrivacyPolicy` 返回 `GetLegalSettings().PrivacyPolicy`；入口由 `GetStatus.privacy_policy_enabled` 控制；WEBSITE-COVERAGE §2 page-0003 `/privacy`）
- **角色 / Owner**：访客 Guest；Owner 模块 = 公开站点
- **触发**：访客访问 `/privacy`

### 1. 场景
`/privacy` 与协议页同形但**独立数据源**（`PrivacyPolicy` ≠ `UserAgreement`）。入口受 `privacy_policy_enabled` 控制。隐私页特有：除空判外还判**版本字段**——含生效版本/日期则带版本标注渲染，无版本则纯正文渲染。隐私政策版本与合规同意闸门（见 prd-nfr-rbac F-5021）相关，故单独建块而非复用协议页。

### 2. 前置条件
- 无需登录（匿名公开访问）。
- `Option.privacy_policy_enabled` 已落库。
- `LegalSetting.PrivacyPolicy` 数据源可未配置/可含版本字段。

### 3. 主流程（对应 P-4 节点 PV0→VER/PLAIN/EMP）
1. 访客访问 `/privacy`（PV0）。
2. 判 `privacy_policy_enabled`（G）：false → 入口隐藏回首页（HID）。
3. true → 请求 `GetPrivacyPolicy`（REQ）。
4. 判数据源已配置（SRC）：否 → 隐私未配置占位态（EMP）。
5. 已配置 → 判版本字段存在（V）：无 → 隐私正文态·无版本标注（PLAIN）；有 → 隐私正文态·含生效版本/日期（VER）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| `privacy_policy_enabled=false` | G-否 | 隐藏入口，回首页 | 入口隐藏态 |
| 数据源未配置 | SRC-否 | 返回占位 | 隐私未配置占位态 |
| 配置但无版本字段 | V-否 | 渲染正文，不带版本 | 隐私正文态·无版本标注 |
| 配置且含版本字段 | V-是 | 渲染正文 + 生效版本/日期 | 隐私正文态·含生效版本/日期 |

### 5. 数据对象（复用 DATA-MODEL §15 Option + LegalSetting）
- **读开关** `Option.privacy_policy_enabled`（bool）。
- **读正文** `LegalSetting.PrivacyPolicy`（string，`GetPrivacyPolicy` 返回）。
- **版本标注**：隐私政策可携生效版本号/日期（与合规同意 `payment_setting.compliance_terms_version` 同源版本语义，见 prd-nfr-rbac F-5021）。

### 6. 验收标准
- [ ] `privacy_policy_enabled=false` → 访问 `/privacy` 回首页，不渲染正文。
- [ ] `privacy_policy_enabled=true` 且数据源未配置 → 返回隐私未配置占位态（不报 500）。
- [ ] 配置正文但无版本字段 → 渲染纯正文（界面不出现版本/日期标注）。
- [ ] 配置正文且含版本字段 → 渲染正文并显示生效版本号/日期。
- [ ] 隐私页数据源独立于协议页：仅改 `UserAgreement` 不影响 `/privacy` 内容。

### 7. 所触及页面状态（对齐 P-4 隐私政策页）
入口隐藏态（privacy_policy_enabled=false）· 隐私未配置占位态（数据源缺失）· 隐私正文态·无版本标注 · 隐私正文态·含生效版本/日期。

---

## PUB-5 公开主题/语言切换控件（root 约束回退 + i18n 支持集判定）

- **功能 ID / 优先级**：F-4043 / P2
- **来源**：FC-125（WEBSITE-COVERAGE §4 公开 CTA「切换主题」「切换语言」；主题源 `GetStatus.theme`，最终值受 root `theme.frontend` 约束；语言源 `i18n/i18n.go`）
- **角色 / Owner**：访客/登录用户；Owner 模块 = 公开站点
- **触发**：访客点击页面右上「切换主题」或「切换语言」

### 1. 场景
访客无需登录即可切换偏好，但这是带**约束回退**的偏好状态机，两条独立切换链：主题在 `default/classic` 间切，但若 root 已通过 `theme.frontend` 锁定，则切换被忽略并回退到锁定主题（提示已锁定）；语言只在 i18n 受支持集合（zh-CN/zh-TW/en）内切，目标语言不在集合内则保持当前语言。

### 2. 前置条件
- 无需登录（匿名即可切换）。
- 主题当前值来自 `GetStatus.theme`（即 root `theme.frontend`）。
- i18n 受支持语言集合 = `{zh-CN, zh-TW, en}`（`i18n/i18n.go` 加载的 locale）。

### 3. 主流程（对应 P-5 节点 T0→THSAVE/THFIX/LGSAVE）
1. 访客点右上控件（T0），判切主题还是切语言（W）。
2. 切主题 → 读候选主题（TH），判 root `theme.frontend` 是否锁定（THC）：锁定 → 忽略切换回退锁定主题（THLOCK→THFIX）；未锁定 → 在 `default/classic` 间应用（THAPPLY），本地持久（THSAVE）。
3. 切语言 → 读目标语言（LG），判是否在受支持集合内（LGC）：否 → 保持当前语言（LGERR）；是 → 加载对应 i18n 资源（LGAPPLY），界面重渲（LGSAVE）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| root `theme.frontend` 已锁定 | THC-锁定 | 忽略切换，强制回锁定主题 | 主题被锁定回退态（提示已锁定） |
| root 未锁定 | THC-未锁定 | 在 default/classic 间切换并本地持久 | 主题已切换态 |
| 目标语言不在 `{zh-CN,zh-TW,en}` | LGC-否 | 不切换，保持当前语言 | 语言不支持保持态 |
| 目标语言在集合内 | LGC-是 | 加载 i18n 资源，界面重渲 | 语言已切换态 |

### 5. 数据对象（复用 DATA-MODEL §15 Option + i18n）
- **读约束** `Option.theme.frontend`（root 配置，枚举 `default/classic`，约束主题切换最终值，见 OP-2 F-4035）。
- **读语言集合** i18n locale：`zh-CN/zh-TW/en`（`i18n/i18n.go` 加载）。
- **前端态**：用户主题/语言偏好本地持久（不强制落库；最终主题受 root 约束覆盖）。

### 6. 验收标准
- [ ] 匿名（无 JWT）可点击切换主题/语言（无需登录）。
- [ ] root 未锁定时，切主题在 `default`↔`classic` 间生效并本地持久。
- [ ] root 将 `theme.frontend` 锁定为某值时，前端切换被忽略，最终主题回退到锁定值并提示已锁定。
- [ ] 切语言到 `zh-CN/zh-TW/en` 之一 → 界面以对应 i18n 资源重渲。
- [ ] 切语言到集合外的语言（如 `fr`）→ 保持当前语言，界面不变。

### 7. 所触及页面状态（对齐 P-5 主题/语言切换）
控件默认态（显示当前主题/语言）· 主题已切换态（default↔classic 持久化）· 主题被锁定回退态（root 锁定，提示）· 语言不支持保持态 · 语言已切换态（i18n 重渲）。

---

## PUB-6 控制台/模型广场/API Keys 主入口跨端深链（C1 网关判定）

- **功能 ID / 优先级**：F-4044 / P2
- **来源**：FC-126（WEBSITE-COVERAGE §4 导航含控制台/模型广场/API Keys；§7 GAP `app.routifyapi.com` 未访问 SG-001，落地页以 repo 控制台能力为权威）
- **角色 / Owner**：访客/登录用户；Owner 模块 = 公开站点
- **触发**：访客点击导航「控制台」/「模型广场」/「API Keys」

### 1. 场景
www 站三个导航入口指向 app 动态域，须登录后访问。这是 www→app 的跨端深链：三入口共用一个鉴权网关判定（契约 C1），但落地到不同目标路由（控制台首页/模型广场/令牌管理）。未登录则带 returnUrl 跳登录，取消登录则留在公开站。落地页具体形态以 repo 控制台为权威（截图为证据 GAP SG-001）。

### 2. 前置条件
- 三导航入口在公开站可见（导航默认态）。
- app 目标域路由需 UserAuth（契约 C1）。
- 落地页标注为证据 GAP（SG-001，未捕获动态截图）。

### 3. 主流程（对应 P-6 节点 N0→LAND/CANCEL）
1. 访客点导航（N0），判点了哪个入口（WHICH）：控制台→目标 app 控制台首页（TGT1）；模型广场→目标 app 模型广场（TGT2）；API Keys→目标 app 令牌管理（TGT3）。
2. 三入口汇入鉴权网关判定（GATE）：是否已登录会话。
3. 未登录（GATE-否）→ 复用契约 C1 带 returnUrl 跳登录（BLK）；登录成功（AFTER-是）→ 跨端深链跳转（JUMP）；放弃（AFTER-否）→ 留在公开站（CANCEL）。
4. 已登录（GATE-是）→ 直接跨端深链跳转 app 动态域（JUMP）→ 落地对应控制台页态（LAND）。

### 4. 分支与异常
| 触发条件 | 流程图节点 | 系统行为 | 用户可见结果 |
|---|---|---|---|
| 未登录点入口 | GATE-否 | 触发 C1，带 returnUrl 跳登录 | 未登录阻断态 |
| 登录页取消 | AFTER-否 | 返回公开站，不跳转 | 取消登录留站态 |
| 已登录点入口 | GATE-是 | 按入口跨端深链跳转 app 域 | 落地对应控制台页态 |
| 点不同入口 | WHICH 三分支 | 落地不同目标路由 | 落地控制台/模型广场/令牌管理（按入口） |

### 5. 数据对象（复用 DATA-MODEL §1 User 鉴权态）
- **会话** `User`（JWT）：网关判定是否已登录。
- **跳转参数**：returnUrl（未登录跳登录时携带，登录后回跳目标）；目标路由按入口分流（控制台首页/模型广场/令牌管理）。
- **证据边界**：app 域落地页形态以 repo 控制台为权威（SG-001 GAP，本片不固化具体落地页字段）。

### 6. 验收标准
- [ ] 未登录点击任一导航入口（控制台/模型广场/API Keys）→ 跳登录并携带 returnUrl。
- [ ] 登录成功后按原入口跳转到对应 app 目标路由（控制台首页/模型广场/令牌管理三者之一）。
- [ ] 登录页取消 → 留在公开站，不发生跨端跳转。
- [ ] 已登录点击入口 → 直接跨端深链跳转，不经登录页。
- [ ] 三入口分别落地不同目标路由（控制台≠模型广场≠令牌管理）。

### 7. 所触及页面状态（对齐 P-6 控制台入口跳转）
导航默认态（三入口可见）· 未登录阻断态（带 returnUrl 跳登录，复用 C1）· 取消登录留站态 · 跨端深链跳转中态 · 落地控制台首页态/落地模型广场态/落地令牌管理态（按入口）。

---

## 功能 ↔ PRD 覆盖对账

| 功能 ID | PRD 块 |
|---|---|
| F-4039 | PUB-1 |
| F-4040 | PUB-2 |
| F-4041 | PUB-3 |
| F-4042 | PUB-4 |
| F-4043 | PUB-5 |
| F-4044 | PUB-6 |

无 `[BLOCKER]`。
