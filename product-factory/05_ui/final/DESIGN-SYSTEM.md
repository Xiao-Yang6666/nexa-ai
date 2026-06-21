# Nexa·AI — DESIGN-SYSTEM（冻结版 · 唯一 token 源）

> **这是全四端共享的唯一设计系统。各端文档只允许 `引用` token，不允许各自发明颜色/字号。**
> 可执行版本：`assets/tokens.css`（页面 `@import` 它，页内一律 `var(--token)`，禁裸色值）。
> 遮罩/叠层一律 `color-mix(in oklch, var(--token) x%, transparent)`，不写裸 `oklch()` / `hex`。
> 设计依据见 `DESIGN-BRIEF.md`（设计读 / 三旋钮 / DNA 采纳映射 / Slop Test 结论）。
> 视觉性格：深色场景 + teal 辉光 + Space Grotesk 巨字 + Inter 正文 + 流动光束/aurora + 玻璃质感 + 强 ease-out 缓动 + 滑出抽屉范式。

---

## 1. 配色策略

**restrained-committed**：单一主色族（teal）+ tinted-slate 中性骨架；渐变只在「巨字品牌」与「主 CTA」两处收敛使用，其余一律安静。语义色（success/warning/danger/info）跨主题恒定。禁纯黑白，所有中性向品牌色相（H≈195，青绿）微调 chroma 0.005–0.01。

---

## 2. 调色板（hex + OKLCH 双写）

### 2.1 品牌主色族（teal，换肤唯一开关 · 默认主题）
| token | hex | OKLCH | 用途 | 对比说明 |
|---|---|---|---|---|
| `--color-primary-50` | `#E6F4F5` | `oklch(0.962 0.018 195)` | 主色极浅底 | — |
| `--color-primary-100` | `#C2E5E8` | `oklch(0.901 0.039 195)` | 浅底/选中底 | — |
| `--color-primary-200` | `#9AD4DA` | `oklch(0.829 0.060 196)` | 边框/分隔 | — |
| `--color-primary-300` | `#5FB9C2` | `oklch(0.722 0.085 197)` | 深色场景提亮文字/图标（AA 大字）| 对 `--hd-bg` ≥3:1 |
| `--color-primary-400` | `#2E9AA3` | `oklch(0.620 0.088 197)` | 次强调 | — |
| `--color-primary-500` | `#0E7C86` | `oklch(0.520 0.083 199)` | **品牌主色**（浅色场景按钮/链接）| 对 `#FFF` ≈4.6:1 (AA 正文) |
| `--color-primary-600` | `#0A6770` | `oklch(0.452 0.071 199)` | hover | — |
| `--color-primary-700` | `#075058` | `oklch(0.378 0.058 200)` | active/pressed | — |
| `--color-primary-800` | `#053A40` | `oklch(0.300 0.045 201)` | 深底强调 | — |
| `--color-primary-fg` | `#FBFEFE` | `oklch(0.992 0.002 195)` | 主色上前景文字 | 对 `-500` ≥4.5:1 |

预留换肤主题（只切主色块，结构同上）：**indigo** `#4F46E5` / **violet** `#7C3AED` / **emerald** `#059669`。切换走 `:root[data-theme="indigo|violet|emerald"]`，页面零改动。

### 2.2 中性灰阶（tinted slate，禁纯黑白）
| token | hex | OKLCH | 用途 |
|---|---|---|---|
| `--color-bg` | `#FCFDFD` | `oklch(0.992 0.002 197)` | 页面主底 |
| `--color-bg-subtle` | `#F4F6F7` | `oklch(0.967 0.003 207)` | 次底/斑马行 |
| `--color-bg-elevated` | `#FFFFFE` | `oklch(1.000 0.001 110)` | 卡片/抬升面 |
| `--color-surface-sunken` | `#EEF1F2` | `oklch(0.945 0.004 207)` | 凹陷面/hover |
| `--color-border` | `#DEE3E5` | `oklch(0.903 0.005 207)` | 常规边框 |
| `--color-border-strong` | `#C5CCCF` | `oklch(0.831 0.007 211)` | 强边框/表头 |
| `--color-text-muted` | `#5E6A6E` | `oklch(0.499 0.013 216)` | 次要文字/占位 |
| `--color-text-secondary` | `#3D484C` | `oklch(0.376 0.012 218)` | 次级正文 |
| `--color-text` | `#161C1E` | `oklch(0.224 0.009 216)` | 主文字 |
| `--color-text-inverse` | `#F7FAFA` | `oklch(0.980 0.003 197)` | 深底上文字 |
| `--color-disabled` | `#8A9498` | `oklch(0.645 0.009 213)` | 禁用文字 |
| `--color-disabled-bg` | `#EBEEEF` | `oklch(0.937 0.003 211)` | 禁用底 |

### 2.3 语义色（跨主题恒定，含 fg/bg/border 三件套）
| 角色 | base hex / OKLCH | bg | fg | border | 用途 |
|---|---|---|---|---|---|
| success | `#1F8F5F` / `oklch(0.585 0.117 158)` | `#E7F5EE` | `#0C3D29` | `#A9DCC4` | 成功/在线/已支付 |
| warning | `#B5740A` / `oklch(0.628 0.130 71)` | `#FBF1DF` | `#5A3A04` | `#EBCF95` | 警告/限额接近 |
| danger | `#D14A33` / `oklch(0.591 0.169 33)` | `#FCEDE9` | `#5E1B0E` | `#F0BCAE` | 错误/超限/删除 |
| info | `#1E6FD9` / `oklch(0.566 0.166 257)` | `#E8F0FC` | `#0E2F5E` | `#AECCF2` | 信息/提示 |

所有 base 对其 bg ≥4.5:1（正文 AA）；fg 对 bg ≥7:1（徽章文字加强）。

### 2.4 深色 Hero / 品牌区色族（门面页 + PREVIEW 复用）
| token | hex / 配方 | 用途 |
|---|---|---|
| `--hd-bg` | `#06100F` | 深色场景主底 |
| `--hd-bg2` | `#0A1A1B` | 顶部径向次底 |
| `--hd-text` | `#F4FBFB` | 深色主文字 |
| `--hd-muted` | `#8AA3A6` | 深色次要文字 |
| `--hd-cyan` | `#2BB7C2` | 辉光/光束 青 |
| `--hd-blue` | `#1E6FD9` | 辉光/光束 蓝（纵深） |
| `--hd-mint` | `#5FD4DE` | 巨字渐变高光 薄荷 |
| `--hd-lav` | `#7AA2F7` | 巨字渐变收尾 薰衣草 |
| `--hd-line` | `color-mix(--hd-text 12%)` | 网格线/玻璃描边 |
| `--hd-glass` | `color-mix(--hd-text 6%)` | 玻璃面板底 |
| `--hd-glass-strong` | `color-mix(--hd-text 10%)` | 玻璃 hover 底 |
| `--glow-a` | `color-mix(--color-primary-500 60%)` | aurora 左上 teal 主辉 |
| `--glow-b` | `color-mix(--hd-cyan 48%)` | aurora 右上 青补光 |
| `--glow-c` | `color-mix(--hd-blue 32%)` | aurora 底中 蓝纵深 |

### 2.5 图表离散序列（chart-1 由主题给，2–8 跨主题稳定，色盲可分）
`--chart-1` = 主题主色（teal `#0E7C86`）；`--chart-2 #1E6FD9` / `--chart-3 #B5740A` / `--chart-4 #7A4FB5` / `--chart-5 #1F8F5F` / `--chart-6 #C0497F` / `--chart-7 #3D9AA0` / `--chart-8 #6B7780`。
辅助：`--chart-grid` `color-mix(--color-text 8%)`、`--chart-axis` `var(--color-text-muted)`、`--chart-area-stop` / `--chart-area-stop0`（趋势面积渐变收口）。

---

## 3. 字体 · 字阶（每层级独立给值，相邻级比 ≥1.2）

字体栈：
- `--font-sans` = `'Inter','Noto Sans SC',-apple-system,'Segoe UI',Roboto,sans-serif`（正文）
- `--font-brand` = `'Space Grotesk','Inter Tight',var(--font-sans)`（品牌巨字/标题）
- `--font-mono` = `'JetBrains Mono','SFMono-Regular',Menlo,Consolas,ui-monospace,monospace`（指标/代码）

| 层级 | token | 字号 | 字重 | 行高 | 字间距 | 用途 |
|---|---|---|---|---|---|---|
| display | `--text-display` | 3.5rem | 700 | tight 1.15 | tight -.04em | 区块大标题 |
| h1 | `--text-h1` | 2.5rem | 700 | tight 1.15 | snug -.01em | 页面主标题 |
| h2 | `--text-h2` | 1.875rem | 600 | snug 1.3 | snug | 模块标题 |
| h3 | `--text-h3` | 1.5rem | 600 | snug 1.3 | — | 卡片/区段标题 |
| h4 | `--text-h4` | 1.25rem | 600 | snug 1.3 | — | 子标题 |
| h5 | `--text-h5` | 1.0625rem | 600 | normal 1.55 | — | 小标题/品牌 logo |
| h6 | `--text-h6` | .9375rem | 600 | normal | — | 列表组标题 |
| body-lg | `--text-body-lg` | 1.0625rem | 400 | relaxed 1.7 | — | 引导正文 |
| body | `--text-body` | .9375rem | 400 | normal 1.55 | — | 默认正文 |
| body-sm | `--text-body-sm` | .8125rem | 400 | normal | — | 表格/辅助 |
| caption | `--text-caption` | .75rem | 400 | normal | — | 说明/hint |
| overline | `--text-overline` | .6875rem | 600 | normal | wide .08em | 标签/分组头（大写）|
| code | `--text-code` | .844rem | 400 | normal | — | 行内/块代码 |
| **mega** | `--text-mega` | `clamp(3.2rem,12vw,9rem)` | 700 | tight | tight -.04em | **巨字品牌专用** |

字重 token：`--fw-regular 400 / --fw-medium 500 / --fw-semibold 600 / --fw-bold 700`。
行高 token：`--lh-tight 1.15 / --lh-snug 1.3 / --lh-normal 1.55 / --lh-relaxed 1.7`。
正文行长 `--container-prose: 68ch`（65–75ch 区间）。

---

## 4. 间距尺 + 栅格

间距阶（4 基数）：`--space-1 4 / -2 8 / -3 12 / -4 16 / -5 24 / -6 32 / -7 40 / -8 48 / -10 64 / -12 96`（px）。
栅格：`--container-max 1200px`、`--container-prose 68ch`、`--gutter 24px`。门面 12 列；console/admin 表格区 16px 紧凑间距档。

---

## 5. 圆角（radius scale）

| token | 值 | 用途 |
|---|---|---|
| `--radius-xs` | 4px | 标签/小 chip |
| `--radius-sm` | 6px | 按钮 / 输入框 |
| `--radius-md` | 10px | 卡片 / 弹层 |
| `--radius-lg` | 16px | 玻璃面板 / 模态 |
| `--radius-xl` | 24px | 大容器 / hero 卡 |
| `--radius-full` | 9999px | 徽章 / 开关 / 头像 |

---

## 6. 阴影 / Elevation 阶（含 focus-ring + glow）

| token | 值（色相微调 200，非纯黑） | 表面映射 |
|---|---|---|
| `--shadow-xs` | `0 1px 2px color-mix(--color-text 6%)` | 输入/小按钮 |
| `--shadow-sm` | 双层柔影 | **卡片** |
| `--shadow-md` | 中层 | **弹层 / popover / 下拉** |
| `--shadow-lg` | 深层 | **模态 / 抽屉** |
| `--shadow-glow` | `0 8px 24px -8px var(--color-primary-500)` | 主 CTA 辉光 / 门面 |
| `--focus-ring` | `0 0 0 3px color-mix(--color-primary-500 35%)` | 所有可聚焦元素 focus-visible（随主题切换）|

---

## 7. 组件基线（全局默认，各端只做特异化覆盖）

### 7.1 按钮（变体 × 状态，每态明确色值，见 tokens.css `.btn-*`）
高度 sm 32 / md 40 / lg 48；圆角 `--radius-sm`；过渡 `transform --dur-1 --ease-snap` + 色/影 `--dur-2`。

| 变体 | default | hover | active | focus | disabled | loading |
|---|---|---|---|---|---|---|
| primary | bg `-500` / fg `-fg` / `--shadow-xs` | bg `-600` | bg `-700` | `--focus-ring` | bg `--color-disabled-bg` / fg `--color-disabled` / 无影 | spinner=`-fg`，禁点 |
| secondary | bg `-elevated` / 边框 `--color-border` | bg `-subtle` / 边框 `-strong` | bg `-sunken` | `--focus-ring` | 同上 | 同上 |
| ghost | 透明 / fg `-secondary` | bg `-subtle` / fg `-text` | bg `-sunken` | `--focus-ring` | fg `--color-disabled` | — |
| danger | bg `--color-danger` / fg `#FFF7F5` | `mix(danger 88% + text)` | 更深 | danger focus-ring | 同上 | spinner=fg |
| link | 透明 / fg `-500` | fg `-600` 下划线 | — | `--focus-ring` | `--color-disabled` | — |
| **glow**（门面 CTA） | `linear-gradient(-500→-600)` / `--shadow-glow` | `translateY(-1px)` + 更强辉光 | — | `--focus-ring` | — | — |
| **glass**（深色门面次级） | `--hd-glass` / 边框 `--hd-line` / blur8 | `--hd-glass-strong` / 边框提亮 | — | `--focus-ring` | — | — |

### 7.2 表单（每元素 default/focus/filled/error/disabled）
- input/textarea：高 40 / 圆角 sm / 边框 `--color-border` / 底 `-elevated`；focus 边框 `-500` + `--focus-ring`；error 底 `--color-danger-bg` + 边框 danger + error focus-ring；disabled 底 `--color-disabled-bg`。占位符 `--color-text-muted`。
- label：`--text-body-sm` semibold `-secondary`，必填标记 `.field-req`=danger。
- hint：`--text-caption` muted；error 文案 `--text-caption` `--color-danger-fg` + 前置线性 SVG 警示图标。
- 开关 switch：track `--color-border-strong`→checked `-500`；thumb `--ease-snap` 滑动；focus-ring。

### 7.3 图表规范（看板/概览页硬门：真图表，非数字卡）
图表库冻结为 **ECharts 5（CDN）**；离散色用 `--chart-1…8`。三类必备 + 三态：

| 图类 | 用途 | 线/片/柱色 | 网格/轴 | 空态 | 加载态 | 异常态 |
|---|---|---|---|---|---|---|
| 趋势（折线+面积） | 请求量/费用按时间 | line=`--chart-1`，面积 `--chart-area-stop`→`--chart-area-stop0` 渐变 | grid `--chart-grid` / axis `--chart-axis` | 居中线性 SVG + "暂无数据" | 骨架条 `.skeleton` | 红描边 + "加载失败，重试" |
| 分布（环/饼） | 模型/渠道占比 | 分片按 `--chart-1…8` 顺序，图例右侧 | — | 灰环占位 + 文案 | 骨架圆 | 同上 |
| 排名（横/纵柱） | Top 模型/Top 用户 | 柱 `--chart-1`，hover `-600`，零基线，数值标签 `--font-mono` | grid `--chart-grid` | 占位柱 + 文案 | 骨架柱 | 同上 |

tooltip：`--color-bg-elevated` 底 + `--shadow-md` + `--radius-md`；数值一律 `--font-mono`。

### 7.4 其他组件
- 卡片 `.card`：`-elevated` + `--color-border` + `--shadow-sm` + `--radius-md`。
- 玻璃 `.glass`：`--hd-glass` + `--hd-line` + blur12 + `--radius-lg`（深色场景）。
- 徽章 `.badge`：full 圆角，四语义 `b-suc/b-warn/b-dan/b-info` + `b-neutral`，可带状态点 `.dot`。
- 表格：表头 sticky + `-strong` 下边框；斑马行 `-subtle`；行 hover `-sunken`。
- 空态 `.empty` / 加载骨架 `.skeleton`（shimmer，reduced-motion 减速到 2.6s）。

---

## 8. 响应式断点

| 断点 | 范围 | 导航 | 栅格 |
|---|---|---|---|
| mobile | <640px | 抽屉/底 tab；触控目标 ≥44×44 | 单列 |
| tablet | 640–1024px | 折叠侧栏/顶栏 | 2 列 |
| desktop | >1024px | 顶栏（门面）/ 固定侧栏（console/admin）| 12 列，`--container-max 1200` |

巨字用 `--text-mega` clamp 自适应视口，移动端自动收缩，无需断点切字号。

---

## 9. 动效系统（§3.7 动效门）

缓动：`--ease-out cubic-bezier(.16,1,.3,1)`（主力出场，弹性收尾）/ `--ease-in-out cubic-bezier(.65,0,.35,1)`（往复/呼吸/aurora）/ `--ease-snap cubic-bezier(.34,1.56,.64,1)`（按钮/图标/chip 轻回弹）。
时长：`--dur-1 .15 / -2 .3 / -3 .55 / -4 .75 / -5 1.1s`。错位：`--stagger-step .06s`。

各端 motion_intensity（见 DESIGN-BRIEF §3）：web-public medium-high（招牌 Flowing Threads + 巨字 Split Text 逐字 + 区块 stagger 揭示 + Count Up + 磁吸 CTA + 滑出抽屉）；console/admin low（仅 Count Up / Fade / Animated List，禁全屏背景动效）；api-docs low（Scroll Reveal + 代码 Text Type）。

**reduced-motion 降级（硬规则）**：`@media (prefers-reduced-motion: reduce)` 下 `--motion-speed` 降为 `.35` —— **减速不冻结**。品牌 hero 背景（Flowing Threads canvas）通过 JS 读 `--motion-speed` 减速；reduced 时若需进一步省电则画一帧定格网络，**绝不 `animation:none` 整死**。装饰类动效（骨架 shimmer）延长时长；业务关键信息不依赖动画完成才可见，全部立即可读。emoji 功能图标一律禁用，换内联线性 SVG。

---

## 10. 换肤架构

两个正交开关：
- `data-theme="teal|indigo|violet|emerald"`（默认 teal）：只切**主色族 + focus-ring + chart-1**，页面零改动。
- `data-scheme="dark"`：切换**深色应用底**（console/admin 工作台暗色），与 `data-theme` 正交。门面页深色场景直接用 `--hd-*` 色族，不依赖 `data-scheme`。

新增主题 = 在 `:root[data-theme="x"]` 复制主色块结构改 10 个值即可，不碰任何页面 CSS。

---

## 11. 冻结声明

本 DESIGN-SYSTEM 为 S5 唯一 token 源，已落地为可执行 `assets/tokens.css`。各端文档与 S6 原型只引用 token，禁裸色值、禁 emoji、禁 `#000/#fff` 裸用。任何后续变更须同步更新 `tokens.css` 与 `DESIGN-MEMORY.md` 并记录理由。
