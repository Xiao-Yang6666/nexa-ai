# Nexa·AI — 控制台（Console / 用户区）UI 设计文档

> 本文档为 Nexa·AI 四端之「用户控制台端」详细 UI 规范。唯一 token 源为冻结版 `DESIGN-SYSTEM.md` / 可执行 `assets/tokens.css`。
> 全文只引用 `var(--token)`，**禁裸色值、禁 `#000/#fff` 裸用、禁 emoji（功能图标一律内联线性 SVG）**。
> 端身份：深色应用底（`data-scheme="dark"`）+ teal 辉光主题（`data-theme="teal"`，可换肤）。
> 动效强度：**low** —— 仅 Count Up / Fade / Animated List；**禁全屏背景动效、禁干扰数据读取的大动效**。
> 合并架构：控制台与管理台为同一套外壳（角色叠加）。本文档写**用户区视角**；导航预留「角色切换位」，超管 = 用户区 + 管理区叠加渲染。

---

## 1. 端概述与设计读

**定位**：登录后用户自助工作台。用户在此管理自己的 API 密钥、查看用量与账单、充值余额、参与签到/分销、追踪异步任务、调整个人设置。核心使用场景是**高频回看数据 + 偶发配置动作**，因此信息密度高于门面、低于管理台，强调「一眼读数 + 安全操作」。

**三旋钮取值（继承 DESIGN-BRIEF）**：
- **density（密度）= 高**：表格/列表为主力，间距走最紧凑档（§5）；但比管理台留更多呼吸位（用户区非批量作业场）。
- **motion_intensity（动效）= low**：进入页面卡片 Fade-in（`--dur-2`/`--ease-out`），指标数字 Count Up（`--dur-3`），列表项 Animated List 逐项淡入（`--stagger-step`）。**图表区一旦渲染即静止，禁轮播/禁自动重绘动画**，避免干扰数据判读。
- **theme（主题）= teal + dark**：深色应用底为默认；换肤只切主色族，页面零改动。

**视觉 DNA 采纳**：深色玻璃质感工作台（`.glass` 仅用于顶栏/侧栏装饰层，数据卡用实心 `.card`）；teal 辉光仅落在主 CTA（充值、创建密钥）与签到「已签」高亮；巨字、Flowing Threads、aurora 等门面招牌**不进控制台**（动效门 low 约束）。

**Slop 规避**：不堆数字卡冒充看板——仪表盘三类真 ECharts 图表（趋势折线 / 消费分布环 / Top 模型排名柱）为硬门；不用彩色 emoji，状态一律语义徽章 + 线性 SVG 图标。

---

## 2. 信息架构与导航

### 2.1 外壳布局（desktop >1024px）
```
┌───────────────────────────────────────────────────────────────┐
│ TopBar (h56, --color-bg-elevated, 下边框 --color-border)          │
│  [Logo] Nexa·AI   ……   [余额 chip] [角色切换▾] [主题] [通知] [头像▾]│
├──────────┬────────────────────────────────────────────────────┤
│ SideNav  │  Content (--color-bg, padding --space-6, max 1200)   │
│ (w240    │   面包屑 / 页标题 H2                                   │
│  固定)   │   ─────────────────────────────────────              │
│          │   主区（卡片栅格 / 表格 / 图表）                       │
│ 用户区组  │                                                      │
│ ──────── │                                                      │
│ 角色切换位│                                                      │
└──────────┴────────────────────────────────────────────────────┘
```

### 2.2 侧栏导航树（用户区视角）
侧栏分组用 `--text-overline`（大写、`--tracking-wide`、`--color-text-muted`）作组头，每组之间 `--space-3` 间隔。激活项：左侧 3px `--color-primary-500` 竖条 + 底 `color-mix(in oklch, var(--color-primary-500) 12%, transparent)` + 文字 `--color-primary-700`（深色场景为提亮的 `-700`=`#62D2DA`）。

| 分组（overline） | 菜单项 | 图标（线性 SVG） | 路由 |
|---|---|---|---|
| 概览 | 仪表盘 | gauge | `/dashboard` |
| 接入 | API 密钥 | key | `/keys` |
| 接入 | 用量统计 | bar-chart | `/usage` |
| 接入 | 异步任务 | list-checks | `/tasks` |
| 账户 | 账单与计费 | receipt | `/billing` |
| 账户 | 余额充值 | wallet | `/recharge` |
| 增长 | 每日签到 | calendar-check | `/checkin` |
| 增长 | 分销推广 | share-network | `/referral` |
| 设置 | 个人设置 | settings | `/settings` |

### 2.3 角色切换位（合并架构关键）
TopBar 右侧设「角色切换 ▾」下拉，仅当账号叠加了管理角色时**才显示**（普通用户隐藏，留位不留视觉噪声）：
- 选项：`用户视图` / `管理视图`。切到管理视图时，侧栏在「设置」组下方**追加**管理区分组（系统总览/用户管理/渠道管理/…，详见 admin.md），同一外壳不刷新。
- 超管 = 用户区菜单 **+** 管理区菜单同时挂载；用 overline 分隔，管理区组头加 `b-info` 小徽章「管理」以区分作用域。
- 切换状态持久化于本地，切换动作走 Fade（`--dur-1`），不做翻页大动效。

### 2.4 面包屑 / 页标题
内容区顶部：面包屑（`--text-body-sm` `--color-text-muted`，分隔符为线性 chevron SVG）+ 页标题 `--text-h2`。右侧放页级主操作按钮（如「创建密钥」`.btn-primary`、「立即充值」`.btn-primary`）。

---

## 3. 调色板（端级 · 继承 DESIGN-SYSTEM，列实际使用子集）

控制台运行于 `:root[data-scheme="dark"]`，下表为深色覆盖后的**生效值**（来源 tokens.css L98–106），页内仍写 `var(--token)`。

### 3.1 中性骨架（深色生效值）
| token | 深色生效 | 用途 |
|---|---|---|
| `--color-bg` | `#0E1416` | 工作台主底 |
| `--color-bg-subtle` | `#151D20` | 斑马行/次底 |
| `--color-bg-elevated` | `#1B2528` | 卡片/侧栏/顶栏/抽屉面 |
| `--color-surface-sunken` | `#0A1012` | 行 hover / 凹陷输入 |
| `--color-border` | `#2A3539` | 常规分隔 |
| `--color-border-strong` | `#3A474C` | 表头下边框/强分隔 |
| `--color-text` | `#E8EEEF` | 主文字 |
| `--color-text-secondary` | `#B8C2C5` | 次级正文/label |
| `--color-text-muted` | `#8A989C` | 占位/辅助/轴标签 |
| `--color-disabled` / `-bg` | `#5A6669` / `#1E282B` | 禁用 |

### 3.2 主色族（深色提亮）
深色下主色整体提亮以保证对深底对比：`--color-primary-500`=`#2BB7C2`、`-600`=`#46C5CF`、`-700`=`#62D2DA`、`--color-primary-fg`=`#06181A`。链接、主 CTA、激活态、签到高亮、图表 chart-1 均取此族。换肤只改主色块，本端不碰。

### 3.3 语义色（跨主题恒定，三件套）
`success` 已支付/在线/启用 · `warning` 限额接近/待处理 · `danger` 超限/禁用/删除/失败 · `info` 提示/管理作用域标记。徽章用 `b-suc / b-warn / b-dan / b-info / b-neutral`（+ `.dot` 状态点）。深色下 `--color-info` 提亮为 `#7AA2F7`。

### 3.4 图表序列
`--chart-1`（=主色，深色 `#2BB7C2`）/ `--chart-2 #1E6FD9` / `-3 #B5740A` / `-4 #7A4FB5` / `-5 #1F8F5F` / `-6 #C0497F` / `-7 #3D9AA0` / `-8 #6B7780`；网格 `--chart-grid`（深色 `color-mix(--color-text 12%)`）、轴 `--chart-axis`、面积渐变 `--chart-area-stop → --chart-area-stop0`。

---

## 4. 字体字阶（端级 · 裁出控制台实际用到的层级）

| 层级 | token | 用途场景 |
|---|---|---|
| h2 | `--text-h2` | 页面主标题 |
| h3 | `--text-h3` | 卡片/区段标题（图表卡头、表单分组）|
| h4 | `--text-h4` | 子标题 / 小卡标题 |
| h5 | `--text-h5` | Logo / 抽屉标题 |
| h6 | `--text-h6` | 列表组标题 |
| body | `--text-body` | 默认正文 / 输入文字 |
| body-sm | `--text-body-sm` | 表格、label、面包屑、侧栏项 |
| caption | `--text-caption` | hint / 单位 / 时间戳 |
| overline | `--text-overline` | 侧栏分组头、表格列分组（大写 + `--tracking-wide`）|
| code | `--text-code` | 密钥串、Endpoint、模型名 |

**指标数字一律 `--font-mono`**（余额、用量、费用、Token 计数、排名数值），配合 Count Up 动效；`--font-brand`（Space Grotesk）仅用于 Logo 字标，不进数据区。`display`/`h1`/`mega` 在本端**不使用**。

---

## 5. 间距与栅格（端级 · 高密度档）

- 内容区外边距 `--space-6`（32）；卡片内 padding `--space-5`（24）；卡片间距 `--gutter`（24）。
- **表格/列表密集档**：单元格 padding `--space-2 var(--space-3)`（8×12），行高紧凑；密钥列表等多列表格沿用此档。
- 表单纵向节奏：字段组间 `--space-5`，label→控件 `--space-2`，控件→hint `--space-1`。
- 栅格：内容 `--container-max 1200px`；仪表盘卡片栅格 desktop 12 列 → 趋势图占 8 列、分布环占 4 列、排名柱整行 12 列（或 KPI 行 4×3 列）。tablet 2 列、mobile 单列。

---

## 6. 阴影 / 圆角应用（端级表面映射）

| 元素 | 阴影 | 圆角 |
|---|---|---|
| 数据卡 `.card` / 图表卡 | `--shadow-sm` | `--radius-md`（10）|
| 下拉 / popover / tooltip | `--shadow-md` | `--radius-md` |
| 模态 / 右侧抽屉 | `--shadow-lg` | `--radius-lg`（16）|
| 输入 / 小按钮 | `--shadow-xs` | `--radius-sm`（6）|
| 主 CTA 辉光（充值/创建密钥/已签到）| `--shadow-glow` | `--radius-sm` |
| 徽章 / 开关 / 头像 / chip | — | `--radius-full` |
| 玻璃顶栏/侧栏装饰层 `.glass` | — | `--radius-lg` |

深色场景阴影仍走 token（基于 `--color-text` mix），视觉以**边框 + 微抬升**为主，避免深底上重影发灰。

---

## 7. 按钮系统（变体 × 状态，每态明确色值）

高度 sm 32 / md 40 / lg 48；圆角 `--radius-sm`；过渡 `transform --dur-1 --ease-snap` + 色/影 `--dur-2`。focus-visible 一律 `--focus-ring`。

| 变体 | default | hover | active | disabled | loading | 控制台用例 |
|---|---|---|---|---|---|---|
| primary | bg `-500` / fg `-fg` / `--shadow-xs` | bg `-600` | bg `-700` | bg `--color-disabled-bg` / fg `--color-disabled` / 无影 | spinner=`-fg`，禁点 | 创建密钥、立即充值、保存设置 |
| secondary | bg `-elevated` / 边框 `--color-border` | bg `-subtle` / 边框 `-strong` | bg `-sunken` | 同上 | 同上 | 取消、导出 CSV、复制 |
| ghost | 透明 / fg `-secondary` | bg `-subtle` / fg `-text` | bg `-sunken` | fg `--color-disabled` | — | 行内图标动作、筛选切换 |
| danger | bg `--color-danger` / fg `#FFF7F5` | `mix(danger 88%+text)` | 更深 | 同上 | spinner=fg | 删除密钥、禁用密钥（二次确认）|
| link | 透明 / fg `-500` | fg `-600` 下划线 | — | `--color-disabled` | — | 「查看明细」「了解额度规则」 |
| glow | `linear-gradient(-500→-600)` / `--shadow-glow` | `translateY(-1px)` + 更强辉光 | — | — | — | 余额充值页主 CTA、签到「立即签到」 |

**禁用 `.btn-glass`** 入数据操作区（仅深色门面用）。loading 态以 `--font-mono` 不可，loading 用内联 spinner SVG（旋转走 `--dur-5` 线性，reduced-motion 减速）。

---

## 8. 表单元素（每元素 default/focus/filled/error/disabled）

- **input / textarea**：高 40 / 圆角 sm / 边框 `--color-border` / 底 `-elevated`；focus 边框 `-500` + `--focus-ring`；filled 同 default（不变底）；error 底 `--color-danger-bg` + 边框 `--color-danger` + error focus-ring（`0 0 0 3px mix(danger 28%)`）；disabled 底 `--color-disabled-bg` + fg `--color-disabled`。占位符 `--color-text-muted`。
- **label**：`--text-body-sm` semibold `--color-text-secondary`；必填 `.field-req`=danger 星号。
- **hint**：`--text-caption` muted；**error 文案** `--text-caption` `--color-danger-fg` + 前置线性 SVG 警示图标（非 emoji）。
- **switch（开关）**：track `--color-border-strong` → checked `-500`；thumb `--ease-snap` 滑动 16px；focus-ring。用于密钥「启用/禁用」、设置项开关。
- **select / 下拉**：触发器同 input；面板 `--shadow-md` + `--radius-md`，选中项底 `mix(--color-primary-500 12%)`。
- **数字 + 单位输入**（额度上限、充值金额）：右侧贴单位后缀（`--color-text-muted`），值用 `--font-mono`；校验范围（如额度 ≥0、充值最小额）失败走 error 态。
- **复制框**（密钥串）：只读 input + 右侧「复制」`.btn-sm.btn-sec`，点击后图标切「check」线性 SVG + toast，1.5s 复原。

---

## 9. 图表规范（仪表盘硬门：真图表，非数字卡）

图表库冻结 **ECharts 5（CDN）**；**motion_intensity=low → 关闭 ECharts 入场 `animation`（`animation:false` 或 `animationDuration:0`），图表渲染即静止，禁自动轮播/禁实时重绘抖动**。数值 tooltip / 标签一律 `--font-mono`。tooltip：`--color-bg-elevated` 底 + `--shadow-md` + `--radius-md`。

三类必备图（仪表盘）+ 每类三态：

| 图类 | 用途 | 颜色 | 网格/轴 | 空态 | 加载态 | 异常态 |
|---|---|---|---|---|---|---|
| **趋势（折线+面积）** | 近 7/30 天用量（请求数/Token）按时间 | line=`--chart-1`，面积 `--chart-area-stop→--chart-area-stop0` 渐变，点 hover 放大 | grid `--chart-grid` / axis `--chart-axis`，X=日期 Y=量 | 居中线性 SVG（空折线）+「所选区间暂无用量」 | `.skeleton` 矩形占位（图区高度）| 红描边容器 + 线性 alert SVG +「加载失败，点击重试」`.btn-sm.btn-sec` |
| **分布（环形）** | 本月消费按模型/渠道占比 | 分片按 `--chart-1…8` 顺序取色，图例置右（`--text-body-sm`），中心留白显「总消费」`--font-mono` | — | 灰环占位（`--color-border`）+「暂无消费数据」 | `.skeleton` 圆形 | 同趋势异常态 |
| **排名（横向柱）** | Top 5/10 模型用量排名 | 柱 `--chart-1`，hover `-600`，零基线，柱尾数值标签 `--font-mono`，按量降序 | grid `--chart-grid`（仅纵向参考线）| 占位灰柱 + 文案 | `.skeleton` 柱 | 同上 |

三态切换由数据层状态机驱动（idle→loading→success/empty/error），切换走 Fade `--dur-2`，**不做图形 morph 动画**。

---

## 10. 核心组件规范（控制台主力：密集表格 / 抽屉 / 确认 / 状态 / KPI）

### 10.1 KPI 指标卡（仪表盘顶行）
`.card`（`-elevated`+`--shadow-sm`），内含：overline 标签（如「本月消费」）+ 主数值 `--font-mono` `--text-h2`（**Count Up** 入场，`--dur-3`/`--ease-out`）+ 环比变化（↑/↓ 线性箭头 SVG + `b-suc`/`b-dan` 文字）。4 张一行：余额 / 本月消费 / 本月请求数 / 活跃密钥数。

### 10.2 密集表格（API 密钥列表为范本）
表头 sticky（`-elevated` 底 + `-strong` 下边框）；斑马行 `-subtle`；行 hover `-sunken`。列：
| 名称 | Key 前缀（`sk-****1234` 脱敏，`--font-mono`）| 状态（`b-suc`启用/`b-neutral`禁用 + `.dot`）| 额度上限（`--font-mono` + 进度条）| 已用 | 创建时间（`--font-mono`）| 操作 |

- **操作列**：行内 `.btn-ghost.btn-sm` 图标按钮组（复制 / 编辑 / 启用切换 switch / 删除-danger），hover 行才高亮显形。
- **额度进度条**：`--color-surface-sunken` 轨 + `-500` 填充；≥80% 转 `--color-warning`、=100% 转 `--color-danger`（含 `aria-valuenow`）。
- 分页 / 每页条数 / 排序点击表头（chevron SVG 指示）；列表项进场走 Animated List 逐项 Fade（`--stagger-step`，仅首屏，翻页不重播）。

### 10.3 创建/编辑密钥抽屉（右侧 Drawer）
从右滑出（宽 480，`--ease-out` `--dur-3`，遮罩 `color-mix(--color-text 40%)`，`--shadow-lg` `--radius-lg` 左侧圆角）。字段：名称、模型/分组限制（多选 chip）、额度上限（数字+单位 / 无限开关）、有效期。底部贴 `.btn-primary 保存` + `.btn-sec 取消`。**创建成功后**：抽屉内显示完整 Key（**仅此一次**）+ 复制框 + `b-warn`「请妥善保存，关闭后不再展示」提示。

### 10.4 二次确认弹窗（删除/禁用）
居中模态（`--shadow-lg` `--radius-lg`），线性 alert SVG（danger 色）+ 标题 + 影响说明（如「禁用后使用此 Key 的请求将立即失败」）+ `.btn-danger 确认删除` / `.btn-sec 取消`。删除需输入密钥名确认（防误删）。

### 10.5 状态徽章映射（全端一致）
| 场景 | 徽章 |
|---|---|
| 密钥启用 / 已支付 / 任务成功 | `b-suc` + 绿点 |
| 限额接近 / 待支付 / 任务排队 | `b-warn` + 黄点 |
| 密钥禁用 / 超限 / 任务失败 | `b-dan` + 红点 |
| 信息 / 任务进行中 | `b-info` + 蓝点 |
| 默认 / 已归档 | `b-neutral` |

### 10.6 充值组件（余额充值页）
金额预设 chip 行（`--radius-full`，选中 `mix(-500 12%)` 底 + `-500` 边框）+ 自定义金额输入（`--font-mono`）+ 支付方式选择（线性图标 + radio 卡）+ `.btn-glow 立即充值`。下方「充值记录」用 10.2 表格范式（金额 / 方式 / 状态 / 时间）。

### 10.7 签到组件（每日签到）
日历网格（7×N，`--radius-sm` 格子）：已签 = `mix(-500 18%)` 底 + check 线性 SVG（`-500`）；今日未签 = `--color-primary-500` 描边脉冲（**仅 today，单格 Fade-pulse `--dur-3`，不全屏**）；缺签 = `--color-bg-subtle`。主按钮 `.btn-glow 立即签到`（已签转 `.btn-sec disabled「今日已签」`）；右侧连续签到天数 `--font-mono` Count Up + 奖励说明。

### 10.8 分销推广组件
顶部 KPI（累计邀请 / 已结算佣金 / 待结算，`--font-mono` Count Up）+ 专属推广链接复制框（同 10.2 复制框）+ 邀请记录表格（被邀用户脱敏 / 状态 / 佣金 / 时间）+ 佣金提现入口（`.btn-primary`，走二次确认）。

### 10.9 异步任务列表
表格列：任务 ID（`--font-mono`）/ 类型 / 状态徽章（含「进行中」`b-info` + 微旋转 spinner SVG）/ 进度条 / 创建时间 / 操作（查看结果/取消/重试）。进行中任务**轮询刷新仅更新进度数值与徽章（局部更新，不整表重渲、不闪烁）**，符合 low 动效约束。

---

## 11. 响应式断点（端级）

| 断点 | 范围 | 导航 | 布局 |
|---|---|---|---|
| mobile | <640px | 侧栏收为顶部汉堡抽屉；底部不放 tab（操作密度低）；触控目标 ≥44×44 | KPI/图表/表格全单列；密集表格转「卡片化行」（每行折叠为带 label 的卡）|
| tablet | 640–1024px | 侧栏折叠为图标条（hover/点击展开）| KPI 2 列；趋势图整行、分布环+排名上下叠 |
| desktop | >1024px | 固定侧栏 w240 + 顶栏 | 12 列；仪表盘 KPI 4 列、趋势 8 + 分布 4、排名整行 |

抽屉在 mobile 转**全屏**（保留顶部关闭）；模态宽度自适应但留 `--space-4` 安全边距。表格横向溢出时容器内横向滚动 + 首列（名称）sticky。

---

## 12. 关键页高保真说明（用户区核心页，逐页区块 + token + 交互态）

### 12.1 仪表盘 `/dashboard`
- **区块**：页标题「仪表盘」H2 + 时间区间筛选（chip：今日/7天/30天，选中态同 10.6）→ KPI 行（4 卡，10.1）→ 趋势折线（8 列）+ 消费分布环（4 列）→ Top 模型排名柱（整行）→ 「最近任务」精简表（5 行，链至 /tasks）。
- **token**：卡 `.card`/`--shadow-sm`，数值 `--font-mono`，图表见 §9。
- **交互态**：首次进入卡片 Fade-in（`--stagger-step`）+ 数值 Count Up；三图各自独立 idle/loading/empty/error；筛选切换重拉数据走 loading→success Fade。

### 12.2 API 密钥管理 `/keys`
- **区块**：标题 + 右上「创建密钥」`.btn-primary` → 筛选栏（状态下拉 + 搜索 input）→ 密集表格（10.2）→ 分页。
- **交互态**：创建/编辑走右抽屉（10.3）；启用切换为行内 switch（乐观更新 + 失败回滚 + toast）；删除走二次确认（10.4，需输名）；复制 Key 走复制框反馈。
- **三态**：无密钥 → `.empty`（线性 key SVG +「还没有 API 密钥」+「创建密钥」CTA）；加载 → 骨架行 ×5；接口失败 → 行内重试条。

### 12.3 用量统计 `/usage`
- **区块**：区间 + 维度筛选（按密钥/模型/日）→ 趋势折线（可叠加多序列，色取 `--chart-1…8`）→ 明细表（日期/模型/请求数/Token/费用，全 `--font-mono`）→ 「导出 CSV」`.btn-sec`。
- **交互态**：维度切换重绘（静止图，无 morph）；表格可排序；空/加载/异常同范式。

### 12.4 账单与计费 `/billing`
- **区块**：当前账期卡（待支付/已支付徽章）→ 账单列表表格（账期/金额/状态/时间/明细链接）→ 用量分项明细抽屉。
- **交互态**：「查看明细」开右抽屉显示该账期分模型消费环 + 明细表；「下载发票」`.btn-sec`。

### 12.5 余额充值 `/recharge`
- **区块**：余额大数 `--font-mono` Count Up（`--text-h1` 级，本页破例放大单一数值）→ 充值组件（10.6）→ 充值记录表。
- **交互态**：`.btn-glow` 主 CTA；金额校验（最小额、整数/两位小数）error 态；支付跳转前 loading。

### 12.6 每日签到 `/checkin`
- 签到组件（10.7）+ 签到奖励规则说明（`.card`，`--container-prose` 行长）+ 历史日历。今日未签脉冲提示，签到成功后 today 格 Fade 转「已签」+ Count Up 更新连签天数。

### 12.7 分销推广 `/referral`
- 分销组件（10.8）+ 规则说明卡 + 提现历史表。提现走二次确认 + 状态徽章流转。

### 12.8 异步任务列表 `/tasks`
- 筛选（状态/类型）+ 任务表（10.9）+ 结果查看抽屉（成功显结果/产物下载，失败显错误日志 `--font-mono` + 重试）。进行中局部轮询更新。

### 12.9 个人设置 `/settings`
- **区块**：分组卡——基本资料（昵称/邮箱，邮箱只读+「换绑」）/ 安全（改密码、2FA switch、登录设备列表）/ 通知偏好（开关组）/ 危险区（注销账号 danger 卡，红边框 `--color-danger-border`）。
- **交互态**：每组独立保存 `.btn-primary`；改密/换绑/注销走二次确认或验证码；表单 error 态见 §8；保存成功 toast。

---

## 13. 可访问性（A11y）

- **对比度**：深色底所有正文 `--color-text` 对 `--color-bg` ≥7:1；次要 `--color-text-secondary` ≥4.5:1；主色提亮族（`-500…-700`）对深底满足 AA 大字/正文；语义徽章 fg 对其 bg ≥7:1。
- **focus 可见**：所有可聚焦元素 focus-visible 一律 `--focus-ring`（随主题），不靠颜色单独传达——状态附 `.dot` + 文字 + 图标。
- **键盘**：侧栏/表格/抽屉/模态全可 Tab 导航；抽屉/模态 focus-trap + Esc 关闭 + 关闭后焦点归还触发元素；表格操作按钮可键盘触达。
- **语义/ARIA**：表格用 `<table>` 原生语义 + `scope`；进度条 `role=progressbar`+`aria-valuenow/min/max`；开关 `role=switch`+`aria-checked`；图表容器 `role=img`+`aria-label` 文字摘要，并附「查看数据表」可选 fallback（满足不可视读者）；toast `role=status` aria-live=polite，错误 `role=alert`。
- **图标**：**全部内联线性 SVG，禁 emoji**；纯装饰 `aria-hidden`，承载语义者带 `aria-label`/`<title>`。
- **动效降级**：`prefers-reduced-motion` 下 `--motion-speed=.35`，Count Up 直接到终值/极短、Animated List 与脉冲降级为即显、骨架 shimmer 减速至 2.6s——**关键数据不依赖动画完成才可见，立即可读**。
- **触控**：mobile 触控目标 ≥44×44；密集表格转卡片化避免误触。

---

## 附：本端覆盖核对（对 §5 的 13 章节 + 任务清单）

| 维度 | 覆盖 |
|---|---|
| 13 章节 | ①端概述设计读 ②信息架构导航 ③端级调色板 ④字阶 ⑤间距栅格 ⑥阴影圆角 ⑦按钮各态 ⑧表单各态 ⑨图表规范 ⑩核心组件含密集表格 ⑪响应式 ⑫关键页高保真 ⑬可访问性 —— 全覆盖 |
| 功能页 | 仪表盘 / API 密钥管理 / 用量统计 / 账单与计费 / 余额充值 / 每日签到 / 分销推广 / 异步任务列表 / 个人设置 —— 9 页全覆盖 |
| 图表 | 趋势折线（用量）+ 消费分布环 + Top 模型排名柱，三类真 ECharts 图 + 空/加载/异常三态 |
| 动效强度 | low —— Count Up / Fade / Animated List + 局部轮询更新；图表渲染即静止、禁全屏背景动效、禁数据 morph；reduced-motion 减速不冻结 |
| 合并架构 | 用户区视角；导航预留角色切换位，超管 = 用户区 + 管理区叠加挂载 |
| 纪律 | 只引用 `var(--token)`，禁裸色值 / `#000/#fff` 裸用 / emoji；深色 `data-scheme=dark` + teal 可换肤 |
