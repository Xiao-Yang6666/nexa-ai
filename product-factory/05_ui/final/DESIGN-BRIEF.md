# Nexa·AI — DESIGN-BRIEF（设计依据 · S5 第一步冻结版）

> 本文件记录「为什么这样设计」的证据，防止凭空发挥。它先于 DESIGN-SYSTEM 冻结，
> 沉淀：每端设计读 + 三旋钮、三页深色 DNA 的采纳/补强映射、各端 motion_intensity（按 react-bits 矩阵）、impeccable Slop Test 结论。
> 产品：**Nexa·AI** —— AI API 网关 SaaS（聚合多家上游模型、统一直连、按量计费/路由/分组重试）。
> 参考基准：已确认满意的三页深色门面 `home.html / login.html / register.html` + `_reference-facade/DESIGN-DNA.md`，以及已扩到 191 行的 `assets/tokens.css`。

---

## 0. 设计技能门：已跑记录

写 DESIGN-SYSTEM 前已 `skill_view` 并应用：

| skill | 用途 | 应用结论 |
|---|---|---|
| `designer-s5-s6` | 角色连续性 / 记忆 / 质量判断 | 本步只冻结 DESIGN-SYSTEM + BRIEF + tokens.css + PREVIEW，不写各端详细 UI |
| `multi-surface-ui-design` | S5 执行规范（§3 必含 / §3.7 动效门 / §4 设计技能门） | DESIGN-SYSTEM 按 §3 全字段冻结；动效按 §3.7 落到各端 motion_intensity |
| `react-bits-motion-design` | 动效选型矩阵 | 各端 motion_intensity + hero_effect + 降级方案见 §3 |
| `impeccable` | Shared Design Laws / 6 条 Absolute Bans / AI Slop Test | OKLCH + tinted neutrals + 配色策略；Slop Test 见 §4 |
| `design-taste-frontend` | Read the Room 设计读 + 三旋钮 + 反 LLM 默认审美 | 每端设计读 + 三旋钮见 §1 |

参考基准来源：三页深色门面已由用户确认满意，其 `DESIGN-DNA.md` 即「参考站 DNA」等价物——本步以它为硬约束，**在其上补强专业度，非逐字照抄**（见 §2）。

---

## 1. 每端设计读（Read the Room）+ 三旋钮

四个交付端（取自 FUNCTION-LIST 231 行的角色/模块划分 + 任务给定范围）：

### 1.1 web-public（公开站：首页 / 登录 / 注册 / 价格 / 模型市场）
> 把 **web-public** 读作：**面向开发者与采购决策者的产品门面**，气质=冷静精密有科技纵深的「网关/路由」工程美，倾向 **Linear / Vercel / cursor.com 的克制 + 一个惊艳主视觉**家族。

| 旋钮 | 值 | 理由 |
|---|---|---|
| DESIGN_VARIANCE | **committed-distinctive** | 一个招牌主效果（Flowing Threads 光束网络）撑场，其余克制；不走全 full-palette 喧闹 |
| MOTION_INTENSITY | **medium-high** | 门面页强制：招牌主效果 + 巨字逐字 + stagger 揭示 + 微交互（按 react-bits 矩阵 §3.7） |
| VISUAL_DENSITY | **low**（首屏≈一屏） | 反卡片墙；对齐竞品克制度 |

### 1.2 console（用户控制台：用量看板 / 令牌 / 渠道 / 账单 / 日志）
> 把 **console** 读作：**面向已登录开发者的高频数据工作台**，气质=高效、可信、数据密度大但不杂乱，倾向 **Linear 后台 + Stripe Dashboard** 的产品 register。

| 旋钮 | 值 | 理由 |
|---|---|---|
| DESIGN_VARIANCE | **systematic-restrained** | 后台不另起炉灶，复用同一 DESIGN-SYSTEM，只提密度 |
| MOTION_INTENSITY | **low** | 仅 Count Up / Fade / Animated List；禁全屏背景动效干扰数据阅读 |
| VISUAL_DENSITY | **high** | 表格/图表密集，紧凑间距档 |

### 1.3 admin（管理台：用户 / 渠道全局 / 兑换码 / 系统设置 / 审计）
> 把 **admin** 读作：**面向运营/管理员的全局管控台**，气质=权威、严谨、强状态可恢复，倾向工业/brutalist 数据仪表盘 + impeccable product register。

| 旋钮 | 值 | 理由 |
|---|---|---|
| DESIGN_VARIANCE | **systematic-restrained** | 与 console 同源，密度更高、强调强状态与批量操作 |
| MOTION_INTENSITY | **low**（趋近 none） | 仅指标 Count Up；表格/操作区零装饰动效 |
| VISUAL_DENSITY | **high** | 最密；信息优先 |

### 1.4 api-docs（API 文档 / 开发者文档）
> 把 **api-docs** 读作：**面向接入工程师的可读性优先文档站**，气质=清爽、可扫读、代码友好，倾向 Stripe Docs / Mintlify 家族。

| 旋钮 | 值 | 理由 |
|---|---|---|
| DESIGN_VARIANCE | **restrained** | 内容为王，视觉退后 |
| MOTION_INTENSITY | **low** | 仅 Scroll Reveal 段落渐显 + 代码块 Text Type（克制）；禁 glitch/fuzzy 伤可读性 |
| VISUAL_DENSITY | **medium** | 长文 + 代码块 + 侧边目录 |

> **全局风格基调由 web-public 主导**（最体现品牌）；console/admin/api-docs 在同一 DESIGN-SYSTEM 上提 `VISUAL_DENSITY`、降 `MOTION_INTENSITY`，复用同一套色板/字阶，不另起炉灶。

---

## 2. 三页深色 DNA 采纳 / 补强映射

参考基准 = `home.html / login.html / register.html` + `DESIGN-DNA.md`。下表逐项标注 **采纳（照搬）** / **补强（在其上提升专业度）** / **刻意不照搬**。

| DNA 维度 | 参考基准做法 | 本次决策 | token / 落地 |
|---|---|---|---|
| 深色场景主底 | `--hd-bg #06100F` 色族 + `--hd-bg2` 顶部径向次底 | **采纳** | `--hd-bg / --hd-bg2 / --hd-text / --hd-muted` 保留 |
| 品牌主色 | teal `#0E7C86`（浅）/ 深色提亮 `--hd-cyan #2BB7C2` | **采纳 + 补强**：补全 50→800 完整阶 + OKLCH 双写 | `--color-primary-50…800` + `--color-primary-fg` |
| 巨字品牌字 | Space Grotesk 几何 sans 巨字 + Inter 正文 | **采纳**：`--font-brand` = Space Grotesk，`--font-sans` = Inter | `--font-brand / --font-sans / --font-mono` |
| 巨字字阶 | `--text-mega: clamp(3.2rem,12vw,9rem)` + `--tracking-tight -.04em` | **采纳** | `--text-mega / --tracking-tight` |
| 流动光束 / aurora 辉光 | Flowing Threads canvas + aurora 三团径向辉光配方 | **采纳**：辉光配方 promote 成 `--glow-a/b/c`；PREVIEW 用静态 aurora 招牌样例呈现 | `--glow-a/b/c` |
| 玻璃质感 | `--hd-glass / --hd-line` + `backdrop-filter:blur()` | **采纳** | `--hd-glass / --hd-glass-strong / --hd-line` + `.glass` |
| 强 ease-out 缓动 | `cubic-bezier(.16,1,.3,1)` 主力出场 + snap 回弹 + in-out 呼吸 | **采纳**：三档缓动 + 五档时长 + stagger 全 token 化 | `--ease-out / --ease-snap / --ease-in-out / --dur-1…5 / --stagger-step` |
| 滑出抽屉范式 | 登录走页内右侧滑出抽屉、内容左移让位 | **采纳为方法**：作为门面页招牌交互记入 motion 语言（本步不实现页面） | 记入 §3 web-public |
| 渐变文字 | `linear-gradient(--color-primary-300 → --hd-mint → --hd-lav)` clip | **采纳** | `--hd-mint / --hd-lav` |
| 配色策略 | restrained，渐变只在巨字与主行动收敛 | **采纳** | 见 §4 |
| 图标 | 一律内联线性 SVG（stroke + currentColor），禁 emoji | **采纳**（硬纪律） | PREVIEW 全线性 SVG |
| **补强 1：换肤架构** | DNA 仅 teal 单主题 | **补强**：teal 主 + 预留 indigo / violet / emerald，`:root[data-theme]` 只切主色块 | tokens.css §主题块 |
| **补强 2：深色应用底** | DNA 只有门面深色场景 | **补强**：console/admin 需深色工作台底，新增 `:root[data-scheme=dark]` 与 `data-theme` 正交 | tokens.css §深色应用底 |
| **补强 3：图表规范** | DNA 无图表（门面无看板） | **补强**：冻结 chart-1…8 离散序列（色盲可分）+ 趋势/分布/排名/空态规范 | `--chart-1…8 / --chart-grid / --chart-axis` |
| **补强 4：完整中性阶 + 语义色** | DNA 只有深色 hero 色族 | **补强**：tinted slate 中性阶（禁纯黑白）+ success/warning/danger/info 四语义（fg/bg/border） | tokens.css §中性 / §语义 |
| **补强 5：完整组件基线** | DNA 只覆盖门面按钮/玻璃 | **补强**：按钮各态 / 表单各态 / 徽章 / 表格 / 空态 / 骨架 全局基线 | tokens.css 通用类 |
| 配色（参考站若有外部站） | — | **不照搬**：保留自有 teal 品牌色，不抄任何第三方配色 | — |

> 取舍方向无歧义：DNA 已是用户确认满意的自有资产，补强项均为「向专业生产级 DESIGN-SYSTEM 补全」，不改变已冻结的视觉性格。

---

## 3. 各端动效（按 react-bits 选型矩阵）

| 端 | motion_intensity | hero_effect（至多一个） | 入场/滚动 | 微交互 | reduced-motion 降级 | 禁用 |
|---|---|---|---|---|---|---|
| **web-public** | medium-high | Flowing Threads 光束网络（canvas，body 级 fixed 独立层） | 巨字 Split Text 逐字 + 区块 stagger 滚动揭示 + 指标 Count Up | 磁吸辉光 CTA / Spotlight 玻璃卡 / chip snap / 登录右侧滑出抽屉 | hero 背景**减速不冻结**（`--motion-speed` 因子，canvas reduced 时画一帧定格网络）；内容全显 | heavy cursor trails / glitch spam |
| **console** | low | 无全屏背景 | Fade Content 区块渐显 | 指标 Count Up / Animated List 行入场 / Spotlight Card | 关装饰动效，数据立即可见 | full-screen 动效背景 / 光标特效 |
| **admin** | low（趋 none） | 无 | 无（信息立即呈现） | 仅指标 Count Up | 全关 | 表格 hover/cursor 装饰 |
| **api-docs** | low | 无 | Scroll Reveal 段落渐显 | 代码块 Text Type（克制） | 段落全显，代码静态 | glitch / fuzzy text 伤可读性 |

**全局动效 token**（all surfaces 复用）：`--ease-out`（主力出场）/ `--ease-snap`（回弹微交互）/ `--ease-in-out`（呼吸/aurora 漂移）/ `--dur-1…5` / `--stagger-step .06s` / `--motion-speed`（reduced 降为 .35，减速不冻结）。

招牌主效果按产品调性选定 = **Flowing Threads**：语义最贴「网关把多家上游连成一张网、请求在网里被路由」，单一效果撑满一屏且有交互记忆点，符合「一个主效果就够」的克制原则（候选 Beams/Particles/Aurora 中择优）。

---

## 4. impeccable AI Slop Test 自检结论

| 检查项（impeccable / 6 Absolute Bans + Slop Test） | 结论 | 证据 |
|---|---|---|
| 禁 LLM 默认审美（紫渐变 + 居中 hero over 暗 mesh + 三等分卡 + Inter+slate-900） | **通过** | 主色 teal 非紫；招牌是有语义的 Flowing Threads 非通用 mesh；首屏一屏非三等分卡墙 |
| 禁裸 `#000`/`#fff`，中性色向品牌色相微调 | **通过** | 中性阶为 tinted slate（色相微调 200），`--color-bg #FCFDFD` / `--color-text #161C1E`，无纯黑白 |
| OKLCH + tinted neutrals | **通过** | 所有遮罩/叠层用 `color-mix(in oklch, var(--token) x%, transparent)`；DESIGN-SYSTEM 调色板 hex+OKLCH 双写 |
| 配色策略明确（restrained/committed/full/drenched 四选一） | **通过** | 选 **restrained-committed**：单主色族 + 中性骨架，渐变只在巨字与主 CTA 收敛 |
| 字阶相邻级比 ≥1.2、非扁平 | **通过** | display 3.5→h1 2.5→h2 1.875→h3 1.5… 比例达标；巨字另用 `--text-mega` |
| elevation/阴影阶非扁平（xs/sm/md/lg + focus-ring + glow） | **通过** | 五档阴影 + focus-ring + glow，指定卡片=sm/弹层=md/模态抽屉=lg |
| 动效门（门面页非静态平铺） | **通过** | web-public 有招牌 Flowing Threads + 逐字 + stagger + 微交互，非静态卡墙 |
| emoji 功能图标 | **通过（0）** | 一律内联线性 SVG（stroke + currentColor） |
| 真图表（非数字卡） | **通过** | CHART-SPECS 冻结趋势/分布/排名 + 空态；PREVIEW 用 ECharts 真渲染验证 |

**综合自评：8.8/10**（沿用并复核 DNA 自评），达标（≥8）。可继续打磨：巨字可上真·可变字体、Threads 可加极淡景深分层——留待 S6。

---

## 5. 输出顺序与本步范围

本步（S5 第一步）只冻结：**DESIGN-BRIEF.md + DESIGN-SYSTEM.md + tokens.css + DESIGN-SYSTEM-PREVIEW.html**，并做 CDP 截图 + 探针自验。
不写各端详细 `<surface>.md` / PAGE-INVENTORY / STATE-MATRIX 等 S6 执行文件（属后续步骤）。
