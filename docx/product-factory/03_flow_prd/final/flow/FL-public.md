# FL-public — 公开与营销（公开站点）流程图

> 分片：公开站点（F-4039 ~ F-4044）。角色：访客 Guest / 用户 User。
> 跨切面契约见 `../OVERALL-FLOW.md §3`（C1 未登录先登录 / C2 externalJump / C4 Turnstile）。

---

## 场景 P-1 · 营销首页公开状态聚合（F-4039）

> 业务规则：`GetStatus` 返回公开配置，按 `ApiInfoEnabled / AnnouncementsEnabled / FAQEnabled / user_agreement_enabled` 等开关**条件注入**可选区块；敏感配置不暴露。这是首页能渲染什么的唯一数据来源。

```mermaid
flowchart TD
  S0([访客打开营销首页]) --> Q[请求 GetStatus 公开配置]
  Q --> SK[加载骨架态]
  SK --> R{GetStatus 返回成功?}
  R -->|否| ERR[配置拉取失败 → 降级默认壳+重试]:::err
  R -->|是| BASE[渲染系统名/Logo/页脚/登录方式开关]
  BASE --> C1{ApiInfoEnabled?}
  C1 -->|是| INJ1[注入 API 信息区块]
  C1 -->|否| SKIP1[隐藏 API 信息区]
  INJ1 --> C2{AnnouncementsEnabled?}
  SKIP1 --> C2
  C2 -->|是| INJ2[注入公告区块]
  C2 -->|否| SKIP2[隐藏公告区]
  INJ2 --> C3{FAQEnabled?}
  SKIP2 --> C3
  C3 -->|是| INJ3[注入 FAQ 区块]
  C3 -->|否| SKIP3[隐藏 FAQ 区]
  INJ3 --> DONE([首页完整渲染态 · 含可选区]):::term
  SKIP3 --> DONE2([首页基础渲染态 · 仅必备区]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-1 首页状态聚合）：
- 加载骨架态（GetStatus 进行中）
- 配置拉取失败降级态（默认壳 + 重试） ← 异常
- 基础渲染态（系统名/Logo/页脚/登录方式开关，所有可选开关均关） ← 终态
- 含 API 信息区态（ApiInfoEnabled=true）
- 含公告区态（AnnouncementsEnabled=true）
- 含 FAQ 区态（FAQEnabled=true）
- 完整渲染态（多可选区同时注入） ← 终态

---

## 场景 P-2 · 首页对话框 Playground 试用入口（F-4040）

> 业务规则：首页对话框 placeholder「问点什么…」，点发送导向 Playground；Playground 需 UserAuth，未登录点发送须引导登录。复用契约 C1（未登录先登录）。

```mermaid
flowchart LR
  D0([访客在首页对话框输入]) --> D1[点击「发送」]
  D1 --> L{当前已登录?}
  L -->|否| B1[未登录阻断态 → 复用契约C1 跳登录]:::err
  B1 --> L2{登录成功?}
  L2 -->|否| B2[放弃登录 → 返回首页保留草稿]:::err
  L2 -->|是| PG
  L -->|是| PG[携输入进入 Playground 试用]
  PG --> OK([Playground 对话试用态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-2 Playground 入口）：
- 首页对话框默认态（placeholder「问点什么…」）
- 未登录阻断态（跳登录，保留输入草稿） ← 异常（复用 C1）
- 放弃登录返回态（草稿回填首页） ← 异常终态
- Playground 试用态（携输入进入对话） ← 终态

---

## 场景 P-3 · 用户协议公开页（F-4041）

> 业务规则：`/agreement` 渲染 `GetUserAgreement`；入口由 `GetStatus.user_agreement_enabled` 控制，关闭时入口隐藏。单一条件分支 + 内容空判。

```mermaid
flowchart TD
  A0([访客访问 /agreement]) --> E{user_agreement_enabled?}
  E -->|否| H[入口隐藏 → 404/重定向首页]:::err
  E -->|是| F[请求 GetUserAgreement 正文]
  F --> N{正文非空?}
  N -->|否| EMPTY([协议空内容占位态]):::term
  N -->|是| SHOW([协议正文渲染态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-3 用户协议页）：
- 入口隐藏态（user_agreement_enabled=false → 404/回首页） ← 异常
- 协议空内容占位态（正文未配置） ← 终态
- 协议正文渲染态 ← 终态

---

## 场景 P-4 · 隐私政策公开页（F-4042）

> 业务规则：`/privacy` 渲染 `GetPrivacyPolicy`；入口由 `privacy_policy_enabled` 控制。与协议页同形但独立数据源——刻意用不同节点构成（含「来源未配置」与「合规版本」分支）以反映隐私页特有的版本校验。

```mermaid
flowchart TD
  PV0([访客访问 /privacy]) --> G{privacy_policy_enabled?}
  G -->|否| HID[入口隐藏 → 回首页]:::err
  G -->|是| REQ[请求 GetPrivacyPolicy]
  REQ --> SRC{数据源已配置?}
  SRC -->|否| EMP([隐私未配置占位态]):::term
  SRC -->|是| V{版本字段存在?}
  V -->|否| PLAIN([隐私正文态 · 无版本标注]):::term
  V -->|是| VER([隐私正文态 · 含生效版本/日期]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-4 隐私政策页）：
- 入口隐藏态（privacy_policy_enabled=false） ← 异常
- 隐私未配置占位态（数据源缺失） ← 终态
- 隐私正文态·无版本标注 ← 终态
- 隐私正文态·含生效版本/日期 ← 终态

---

## 场景 P-5 · 公开主题/语言切换控件（F-4043）

> 业务规则：访客无需登录即可切换；主题在 default/classic 间切换但**最终值受 root 配置 `theme.frontend` 约束**；语言在 i18n 受支持集合内切换。这是一个偏好状态机，两条独立切换链 + 约束回退。

```mermaid
flowchart TD
  T0([访客点击右上控件]) --> W{切主题 or 切语言?}
  W -->|切主题| TH[读取候选主题]
  TH --> THC{root theme.frontend 是否锁定?}
  THC -->|锁定指定主题| THLOCK[忽略切换 → 回退到锁定主题]:::err
  THC -->|未锁定| THAPPLY[在 default/classic 间应用]
  THAPPLY --> THSAVE([主题已切换态 · 本地持久]):::term
  THLOCK --> THFIX([主题被强制态 · 提示已锁定]):::term
  W -->|切语言| LG[读取目标语言]
  LG --> LGC{在受支持语言集合内?}
  LGC -->|否| LGERR[不支持 → 保持当前语言]:::err
  LGC -->|是| LGAPPLY[加载对应 i18n 资源]
  LGAPPLY --> LGSAVE([语言已切换态 · 界面重渲]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-5 主题/语言切换）：
- 控件默认态（显示当前主题/语言）
- 主题已切换态（default↔classic 持久化） ← 终态
- 主题被锁定回退态（root 锁定，提示） ← 异常终态
- 语言不支持保持态（目标语言不在集合） ← 异常
- 语言已切换态（i18n 重渲） ← 终态

---

## 场景 P-6 · 控制台/模型广场/API Keys 主入口跳转（F-4044）

> 业务规则：导航入口指向 app 动态域；需登录后访问（证据 GAP SG-001，具体页以 repo 控制台为权威）。这是 www→app 的跨端深链，复用契约 C1。三入口共用一个网关判定但落地到不同目标路由。

```mermaid
flowchart TD
  N0([访客点击导航]) --> WHICH{点了哪个入口?}
  WHICH -->|控制台| TGT1[目标=app 控制台首页]
  WHICH -->|模型广场| TGT2[目标=app 模型广场]
  WHICH -->|API Keys| TGT3[目标=app 令牌管理]
  TGT1 --> GATE{已登录会话?}
  TGT2 --> GATE
  TGT3 --> GATE
  GATE -->|否| BLK[未登录阻断 → 复用契约C1 带returnUrl跳登录]:::err
  BLK --> AFTER{登录成功?}
  AFTER -->|否| CANCEL([取消登录 → 留在公开站]):::term
  AFTER -->|是| JUMP
  GATE -->|是| JUMP[跨端深链跳转 app 动态域]
  JUMP --> LAND([落地对应控制台页态]):::term
  classDef term fill:#e6ffed,stroke:#2da44e
  classDef err fill:#fff5f5,stroke:#cf222e
```

屏幕状态清单（P-6 控制台入口跳转）：
- 导航默认态（三入口可见）
- 未登录阻断态（带 returnUrl 跳登录，复用 C1） ← 异常
- 取消登录留站态 ← 终态
- 跨端深链跳转中态
- 落地控制台首页态 / 落地模型广场态 / 落地令牌管理态（按入口） ← 终态
