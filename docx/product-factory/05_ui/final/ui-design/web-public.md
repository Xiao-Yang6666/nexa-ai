# Nexa·AI — web-public（公开站）详细 UI 设计

> 端：**web-public**（公开站 / 门面）。受众：开发者 + 采购决策者。
> 唯一 token 源：`../DESIGN-SYSTEM.md` + `../assets/tokens.css`（页面 `@import` 它，页内一律 `var(--token)`，**禁裸色值 / 禁 #000·#fff / 禁 emoji**）。
> 本文件只**引用** token，不另发明颜色/字号。所有遮罩/叠层用 `color-mix(in oklch, var(--token) x%, transparent)`。
> 设计依据：`../DESIGN-BRIEF.md`（设计读 / 三旋钮 / DNA 采纳映射 / Slop Test）+ `../_reference-facade/{home,login,register}.html` + `DESIGN-DNA.md`（已确认满意的视觉基准）。
> 上游：`02_decomposition/final/FUNCTION-LIST.csv`（公开站功能）、`03_flow_prd/final/PAGE-STATE-MATRIX.md` §A 公开站点（P-1…P-6 + ML-4/ML-5）、§B 注册/登录（AC-1/AC-2）。

---

## 1. 端概述与设计读

### 1.1 本端是什么

Nexa·AI 公开站，是 AI API 网关 SaaS 的**产品门面**：对未登录访客讲清「满血直连多家上游、统一计费、智能路由」的价值，并把访客转化为注册/登录用户。它承载 **8 个公开页面**（见 §2 信息架构），最终把行动引向「进控制台 / 开始试用」。

### 1.2 受众与设备

| 维度 | 取向 |
|---|---|
| 主受众 | 接入工程师（看技术可信度、价格透明度、模型覆盖度）、采购/技术决策者（看稳定性、合规、对标竞品） |
| 设备 | 桌面优先（>1024px 决策场景为主），移动端完整可用（响应式降级，触控目标 ≥44×44） |
| 进入心智 | 「这家网关靠不靠谱 / 支持哪些模型 / 多少钱 / 怎么接」——首屏要在一屏内立住信任 + 给出试用入口 |

### 1.3 三旋钮（取自 DESIGN-BRIEF §1.1）

| 旋钮 | 值 | 落地 |
|---|---|---|
| DESIGN_VARIANCE | **committed-distinctive** | 一个招牌主效果（Flowing Threads 光束网络）撑场，其余克制；不走 full-palette 喧闹 |
| MOTION_INTENSITY | **medium-high** | 门面强制：招牌主效果 + 巨字逐字 Split Text + 区块 stagger 滚动揭示 + Count Up + 磁吸 CTA + 登录右侧滑出抽屉 |
| VISUAL_DENSITY | **low**（首屏≈一屏） | 反卡片墙；对齐 Linear / Vercel / cursor.com 克制度 |

### 1.4 整体调性一句话

> **深色场景 + teal 辉光，巨字品牌逐字浮现，背景一张会呼吸、会流光、随鼠标聚拢的「流动光束网络」——极简到只剩一屏，但那张网络就是记忆点；价格/模型/排行页在同一深色 DNA 下转为可扫读的浅色内容版式，信息密度上升、动效退到滚动揭示。**

---

## 2. 信息架构与导航

### 2.1 页面清单（取自 PAGE-STATE-MATRIX §A + 任务范围）

| Page ID | 页面 | 路由 | 关联 FID / 来源 | 调性 |
|---|---|---|---|---|
| WP-01 | 营销首页（Hero + Playground 入口对话框） | `/` | FL-public P-1/P-2、FL-playground PG-1（F-1001 入口） | 深色 hero，medium-high |
| WP-02 | 登录页 / 右侧滑出抽屉 | `/login`（+ 站内抽屉） | FL-account AC-2（F-1002） | 深色品牌区 + 玻璃表单 |
| WP-03 | 注册页 / 抽屉 register 段 | `/register` | FL-account AC-1（F-1001/F-1004/F-1005） | 同上 |
| WP-04 | 公开价格页 | `/pricing` | FL-model ML-4 | 浅色内容版式 |
| WP-05 | 模型广场（模型市场） | `/models` | FL-model ML-3/ML-4（公开可见模型 + 定价） | 浅色内容版式 |
| WP-06 | 模型排行榜 | `/rankings` | FL-model ML-5、FL-usagelog UL-6 | 浅色 + 真图表 |
| WP-07 | 用户协议页 | `/agreement` | FL-public P-3 | 浅色长文 |
| WP-08 | 隐私政策页 | `/privacy` | FL-public P-4 | 浅色长文 |

> 找回密码 / OAuth / 微信 / Telegram / 2FA / Passkey（AC-3…AC-9）作为登录/注册抽屉内的**次级流程入口与态**在 §12.2 说明，不单列独立门面页。

### 2.2 导航模型

- **顶栏（sticky，desktop）**：左 = 品牌 logo `Nexa·AI`（h5 字号，中点 `·` 主色高光）；中 = 主导航「模型广场 / 价格 / 排行榜 / 文档↗」；右 = 主题/语言切换控件（P-5）+ `登录`（ghost）+ `开始试用`（glow CTA）。
  - 深色页（首页/登录/注册）顶栏走深色玻璃：`background: color-mix(in oklch, var(--hd-bg) 70%, transparent)` + `backdrop-filter: blur(12px)` + 下边框 `--hd-line`。
  - 浅色内容页（价格/模型/排行/协议）顶栏走浅色玻璃：`background: color-mix(in oklch, var(--color-bg) 78%, transparent)` + blur12 + 下边框 `--color-border`。
- **页脚（轻量）**：品牌行 + 版权 + 协议/隐私链接 + 文档/状态页链接。门面克制原则下不堆长 footer，最多两列。
- **关键路径**（引用流程图）：
  - 转化主路径：`首页 Hero 对话框（PG-1）→ 未登录阻断 C1 → 滑出登录抽屉 → 登录成功 → 控制台`（保留输入草稿回流，见 PAGE-STATE-MATRIX P-2）。
  - 信息决策路径：`首页 → 价格 / 模型广场 / 排行榜 → 开始试用 → 注册`。
- **移动端导航**：顶栏收为 logo + 汉堡 + 主 CTA；点汉堡从右侧滑出全屏抽屉（导航项 + 主题/语言 + 登录/试用），触控目标 ≥44×44。

### 2.3 入口隐藏 / 降级（来自矩阵）

- 价格页 `pricing` 关 → 模块隐藏、导航项不渲染（ML-4 模块不可见隐藏态）。
- 排行榜 `rankings` 关 → 入口隐藏（ML-5/UL-6 排行入口隐藏态）。
- 协议/隐私 `enabled=false` → 404 或回首页（P-3/P-4 入口隐藏态）。
- 注册关闭 `RegisterEnabled=false` → 注册抽屉/页显「注册已关闭」态（AC-1）。

---

## 3. 调色板（端级 · 引用 DESIGN-SYSTEM）

本端实际使用的 token 子集（**不重复发明色值**，hex/OKLCH 全见 DESIGN-SYSTEM §2）。

### 3.1 深色场景（首页 hero / 登录 / 注册品牌区）

| token | hex | 用途 |
|---|---|---|
| `--hd-bg` | `#06100F` | 深色主底 |
| `--hd-bg2` | `#0A1A1B` | 顶部径向次底 |
| `--hd-text` | `#F4FBFB` | 深色主文字 |
| `--hd-muted` | `#8AA3A6` | 深色次要文字 |
| `--hd-cyan` / `--hd-blue` | `#2BB7C2` / `#1E6FD9` | Threads 节点 / 光束 / 辉光 |
| `--hd-mint` / `--hd-lav` | `#5FD4DE` / `#7AA2F7` | 巨字渐变高光 / 收尾 |
| `--hd-line` / `--hd-glass` / `--hd-glass-strong` | `mix(--hd-text 12/6/10%)` | 网格线·描边 / 玻璃面 / hover 玻璃 |
| `--glow-a/b/c` | `mix(primary-500 60% / hd-cyan 48% / hd-blue 32%)` | aurora 三团辉光 |
| `--color-primary-300` | `#5FB9C2` | 深底提亮文字/图标（AA 大字 ≥3:1 对 `--hd-bg`） |

### 3.2 浅色内容场景（价格 / 模型广场 / 排行 / 协议）

| token | hex | 用途 |
|---|---|---|
| `--color-bg` / `--color-bg-subtle` / `--color-bg-elevated` | `#FCFDFD` / `#F4F6F7` / `#FFFFFE` | 页面底 / 次底·斑马 / 卡片面 |
| `--color-surface-sunken` | `#EEF1F2` | 凹陷 / 行 hover |
| `--color-border` / `--color-border-strong` | `#DEE3E5` / `#C5CCCF` | 常规边框 / 表头·强边框 |
| `--color-text` / `--color-text-secondary` / `--color-text-muted` | `#161C1E` / `#3D484C` / `#5E6A6E` | 主文字 / 次正文 / 次要·占位 |
| `--color-primary-500/600/700` | `#0E7C86` / `#0A6770` / `#075058` | 浅色按钮·链接 / hover / active |

### 3.3 语义色（徽章 / 价格状态 / 表单校验，跨主题恒定）

| 角色 | base | bg | fg | border | 端内用途 |
|---|---|---|---|---|---|
| success | `--color-success #1F8F5F` | `--color-success-bg` | `--color-success-fg` | `--color-success-border` | 「可用 / 在线 / 已支持」徽章、排行涨幅 |
| warning | `--color-warning #B5740A` | `…-bg` | `…-fg` | `…-border` | 「beta / 限额接近 / 即将下线」 |
| danger | `--color-danger #D14A33` | `…-bg` | `…-fg` | `…-border` | 表单校验错误、「已下线 / 不可用」 |
| info | `--color-info #1E6FD9` | `…-bg` | `…-fg` | `…-border` | 公告条、提示、「新模型」标 |

### 3.4 图表离散序列（排行榜 / 价格对比用）

`--chart-1`（teal 主题主色）… `--chart-8`，辅助 `--chart-grid` / `--chart-axis` / `--chart-area-stop[0]`。色盲可分，见 DESIGN-SYSTEM §2.5。

---

## 4. 字体字阶（端级 · 引用 DESIGN-SYSTEM §3）

字体栈：`--font-brand`（Space Grotesk，巨字/标题）/ `--font-sans`（Inter+Noto Sans SC，正文）/ `--font-mono`（JetBrains Mono，价格数字/模型 id/代码/排行数值）。

| 层级 | token | 字号 | 字重 | 行高 | 端内用途 |
|---|---|---|---|---|---|
| mega | `--text-mega` | clamp(3.2rem,12vw,9rem) | 700 | tight | **首页巨字品牌 `Nexa·AI`** |
| display | `--text-display` | 3.5rem | 700 | 1.15 | 价格/模型/排行页区块大标题 |
| h1 | `--text-h1` | 2.5rem | 700 | 1.15 | 内容页主标题（协议/隐私标题） |
| h2 | `--text-h2` | 1.875rem | 600 | 1.3 | 模块标题（价格档位组 / 模型分类） |
| h3 | `--text-h3` | 1.5rem | 600 | 1.3 | 卡片标题（套餐卡 / 模型卡） |
| h4 | `--text-h4` | 1.25rem | 600 | 1.3 | 子标题 |
| h5 | `--text-h5` | 1.0625rem | 600 | 1.55 | 顶栏品牌 logo 文字 |
| body-lg | `--text-body-lg` | 1.0625rem | 400 | 1.7 | hero slogan / 引导正文 / 协议正文 |
| body | `--text-body` | .9375rem | 400 | 1.55 | 默认正文 / 导航 |
| body-sm | `--text-body-sm` | .8125rem | 400 | 1.55 | 表格 / 模型卡元信息 / 辅助 |
| caption | `--text-caption` | .75rem | 400 | — | 价格单位说明 / hint / 脚注 |
| overline | `--text-overline` | .6875rem | 600 | — | 分组头大写标签（`POPULAR` / `MODELS`）字距 .08em |
| code | `--text-code` | .844rem | 400 | — | 模型 id / 接入示例代码块 |

正文行长 `--container-prose: 68ch`（协议/隐私长文用），相邻级比 ≥1.2（达标）。

---

## 5. 间距与栅格（端级）

- 间距阶：`--space-1…12`（4/8/12/16/24/32/40/48/64/96px）。门面区段垂直节奏多用 `--space-10`(64)/`--space-12`(96) 拉开留白；内容卡内边距 `--space-5`(24)；表格紧凑档 16px。
- 栅格：`--container-max 1200px` 居中，左右 `--gutter 24px` 外边距；门面 12 列。
  - 首页 hero：单列居中（巨字 + slogan + chat 输入 + 信任行），留白主导。
  - 价格页：套餐卡 12 列 → desktop 3–4 卡一行；模型对比表满宽。
  - 模型广场：模型卡网格 12 列 → desktop 3 列 / tablet 2 列 / mobile 1 列。
  - 协议/隐私：内容列 `--container-prose 68ch` 居中 + 右侧锚点目录（desktop）。

---

## 6. 阴影 / 圆角应用（端级 · 引用 DESIGN-SYSTEM §5/§6）

| 表面 | elevation | radius |
|---|---|---|
| 输入框 / 小按钮 | `--shadow-xs` | `--radius-sm` 6px |
| 内容卡（套餐卡 / 模型卡 / 排行卡） | `--shadow-sm` | `--radius-md` 10px |
| 下拉 / popover / 顶栏语言菜单 / tooltip | `--shadow-md` | `--radius-md` |
| 登录/注册滑出抽屉 / 模态 | `--shadow-lg` | `--radius-lg` 16px |
| 深色玻璃面板（hero 卡 / 抽屉表单） | `.glass`（`--hd-glass`+`--hd-line`+blur12） | `--radius-lg` |
| hero 容器 / 大视觉卡 | — | `--radius-xl` 24px |
| 主 CTA `.btn-glow` | `--shadow-glow`（teal 辉光） | `--radius-sm` |
| 徽章 / chip / 头像 / 开关 | — | `--radius-full` |
| 所有可聚焦元素 focus-visible | `--focus-ring`（随主题切换） | — |

---

## 7. 按钮系统（每变体 × 每状态明确色值 · 引用 tokens.css `.btn-*`）

高度：sm 32 / md 40 / lg 48；圆角 `--radius-sm`；过渡 `transform --dur-1 --ease-snap` + 色/影 `--dur-2`。所有变体 focus 态统一 `--focus-ring`（danger 用 danger focus-ring）。

### 7.1 primary（浅色内容页主按钮）

| 状态 | 背景 | 文字 | 边框 | 阴影 | 说明 |
|---|---|---|---|---|---|
| default | `--color-primary-500` | `--color-primary-fg` | 无 | `--shadow-xs` | |
| hover | `--color-primary-600` | `--color-primary-fg` | 无 | `--shadow-xs` | |
| active | `--color-primary-700` | `--color-primary-fg` | 无 | inset 轻压 | |
| focus | `--color-primary-500` | `--color-primary-fg` | 无 | `--focus-ring` | focus-visible |
| disabled | `--color-disabled-bg` | `--color-disabled` | `--color-border` | none | 不可点 |
| loading | `--color-primary-500` | `--color-primary-fg` | 无 | `--shadow-xs` | spinner=`--color-primary-fg`，禁点 |

### 7.2 secondary（浅色次级 / 表格行操作）

| 状态 | 背景 | 文字 | 边框 | 阴影 |
|---|---|---|---|---|
| default | `--color-bg-elevated` | `--color-text` | `--color-border` | `--shadow-xs` |
| hover | `--color-bg-subtle` | `--color-text` | `--color-border-strong` | `--shadow-xs` |
| active | `--color-surface-sunken` | `--color-text` | `--color-border-strong` | inset |
| focus | `--color-bg-elevated` | `--color-text` | `--color-border` | `--focus-ring` |
| disabled | `--color-disabled-bg` | `--color-disabled` | `--color-border` | none |
| loading | `--color-bg-elevated` | `--color-text` | `--color-border` | spinner=`--color-text`，禁点 |

### 7.3 ghost（顶栏「登录」/ 文本动作）

| 状态 | 背景 | 文字 | 边框 |
|---|---|---|---|
| default | 透明 | `--color-text-secondary` | 无 |
| hover | `--color-bg-subtle` | `--color-text` | 无 |
| active | `--color-surface-sunken` | `--color-text` | 无 |
| focus | 透明 | `--color-text-secondary` | `--focus-ring` |
| disabled | 透明 | `--color-disabled` | 无 |

> 深色顶栏的 ghost：default fg `--hd-muted`，hover bg `--hd-glass` + fg `--hd-text`。

### 7.4 danger（删除/危险确认，门面用得少：注销账号确认等）

| 状态 | 背景 | 文字 | 阴影 |
|---|---|---|---|
| default | `--color-danger` | `#FFF7F5`（语义 fg 配色，非裸白） | `--shadow-xs` |
| hover | `color-mix(in oklch, var(--color-danger) 88%, var(--color-text))` | `#FFF7F5` | `--shadow-xs` |
| active | 更深（再 mix `--color-text`） | `#FFF7F5` | inset |
| focus | `--color-danger` | `#FFF7F5` | danger focus-ring `0 0 0 3px color-mix(in oklch, var(--color-danger) 28%, transparent)` |
| disabled | `--color-disabled-bg` | `--color-disabled` | none |
| loading | `--color-danger` | `#FFF7F5` | spinner=fg，禁点 |

### 7.5 link（行内文字链接 / 「忘记密码」）

| 状态 | 文字 | 装饰 |
|---|---|---|
| default | `--color-primary-500` | 无 |
| hover | `--color-primary-600` | underline |
| focus | `--color-primary-500` | `--focus-ring` |
| disabled | `--color-disabled` | 无 |

### 7.6 glow（门面招牌主 CTA：「开始试用 / 进入控制台」）

| 状态 | 背景 | 文字 | 阴影 |
|---|---|---|---|
| default | `linear-gradient(135deg, var(--color-primary-500), var(--color-primary-600))` | `--color-primary-fg` | `--shadow-glow` |
| hover | 同上 + `transform: translateY(-1px)` | `--color-primary-fg` | `0 12px 30px -8px var(--color-primary-500)`（更强辉光） |
| active | 同上 `translateY(0)` | `--color-primary-fg` | `--shadow-glow` |
| focus | 同 default | `--color-primary-fg` | `--focus-ring` |
| disabled | `--color-disabled-bg` | `--color-disabled` | none |
| loading | 同 default | `--color-primary-fg` | spinner=fg，禁点 |

> 磁吸微交互：鼠标接近 `glow` CTA 时按 `--ease-snap` 轻微朝光标偏移（≤4px），松开回弹；reduced-motion 关闭磁吸。

### 7.7 glass（深色门面次级：hero「查看文档」/ 抽屉次级）

| 状态 | 背景 | 文字 | 边框 |
|---|---|---|---|
| default | `--hd-glass` + blur8 | `--hd-text` | `--hd-line` |
| hover | `--hd-glass-strong` | `--hd-text` | `color-mix(in oklch, var(--hd-text) 24%, transparent)` |
| active | `--hd-glass-strong`（轻压） | `--hd-text` | 同 hover |
| focus | `--hd-glass` | `--hd-text` | `--focus-ring` |

### 7.8 尺寸规格

| 尺寸 | 高度 | 内边距 | 字号 | 图标间距 |
|---|---|---|---|---|
| sm | 32px | `0 --space-3` | `--text-body-sm` | `--space-1` 4 |
| md（默认） | 40px | `0 --space-4` | `--text-body` | `--space-2` 8 |
| lg（hero CTA） | 48px | `0 --space-5` | `--text-body-lg` | `--space-2` 8 |

---

## 8. 表单元素（登录/注册抽屉 + 价格/找回流程 · 引用 tokens.css）

每元素覆盖 default/focus/filled/error/disabled。深色抽屉内表单底走玻璃，控件保持同结构、色随场景。

### 8.1 input / textarea / 密码框

| 状态 | 底 | 边框 | 文字 | 阴影 | 说明 |
|---|---|---|---|---|---|
| default | `--color-bg-elevated`（深色抽屉：`--hd-glass`） | `--color-border`（深色：`--hd-line`） | `--color-text`（深色：`--hd-text`） | — | 高 40 / 圆角 sm |
| focus | 同 default | `--color-primary-500` | 同上 | `--focus-ring` | |
| filled | 同 default | 同 default | `--color-text` | — | 占位符 `--color-text-muted` |
| error | `--color-danger-bg` | `--color-danger` | `--color-text` | error focus-ring（focus 时） | |
| disabled | `--color-disabled-bg` | `--color-border` | `--color-disabled` | — | 禁输入 |

- 密码框右侧「显示/隐藏」眼睛 = 内联线性 SVG（stroke + currentColor）按钮，hover bg `--color-bg-subtle`。
- 密码强度条：track `--color-border` → 弱 `--color-danger` / 中 `--color-warning` / 强 `--color-success`，宽度过渡 `--dur-2 --ease-out`。

### 8.2 label / 必填 / hint / error 文案

- label：`--text-body-sm` semibold `--color-text-secondary`（深色：`--hd-muted`）；位置在控件上方。
- 必填标记 `.field-req` = `--color-danger`（`*`）。
- hint：`--text-caption` `--color-text-muted`，控件下方 `--space-1` 间距。
- error 文案：`--text-caption` `--color-danger-fg` + 前置线性 SVG 警示图标（三角/感叹），控件下方；校验失败时输入框 `.input.err` 同步 + 抽屉整体 shake（`--ease-snap` 微抖一次，reduced-motion 关闭）。

### 8.3 下拉 select / 多选 / 单选 / 开关 / 验证码

- **下拉 select**：触发器同 input 结构；展开面板 `--shadow-md` + `--radius-md`，选项 hover `--color-bg-subtle`、选中 `--color-primary-50` + 主色对勾。深色场景面板走 `.glass`。
- **单选 radio / 多选 checkbox**：未选边框 `--color-border-strong`；选中底 `--color-primary-500` + 勾/点 `--color-primary-fg`；focus `--focus-ring`；disabled `--color-disabled-bg`。
- **开关 switch**：track `--color-border-strong` → checked `--color-primary-500`；thumb `--color-bg-elevated`，`--ease-snap` 滑动；focus-ring。用于登录「记住我」、协议同意之外的偏好。
- **图标验证码 / Turnstile（C4，AC-1/AC-2）**：嵌入式人机校验区块，加载态骨架占位，校验失败回退态显 `--color-danger-fg` 文案 + 重试按钮。
- **邮箱验证码（AC-1，F-1004）**：输入框 + 右侧「发送验证码」按钮（secondary，倒计时禁用态显 `60s`，btn disabled 配色），频率限流态显 warning hint。
- **2FA / OTP 第二步（AC-2/AC-8）**：6 位分格输入，每格 input 结构，自动跳格；错误态整组 error 边框 + shake。

---

## 9. 图表规范（排行榜 / 价格对比 · 引用 DESIGN-SYSTEM §7.3）

图表库冻结 **ECharts 5（CDN）**；离散色 `--chart-1…8`；数值一律 `--font-mono`；tooltip = `--color-bg-elevated` + `--shadow-md` + `--radius-md`。门面以浅色场景渲染（排行榜在 `--color-bg` 内容版式）。

### 9.1 趋势类（折线 + 面积）— 排行榜「用量随时间」/ 模型调用趋势

| 项 | 规范 |
|---|---|
| 线色 | `--chart-1`（多序列按 `--chart-1…8` 顺序） |
| 面积渐变 | `--chart-area-stop` → `--chart-area-stop0`（顶部到收口透明） |
| 网格 / 轴 | grid `--chart-grid`，axis `--chart-axis`，刻度 `--text-caption` |
| tooltip | 同上规范，多序列带色点图例 |
| **空态** | 居中线性 SVG（折线图标）+「暂无数据」`--color-text-muted`（UL-6 排行空态） |
| **加载态** | 骨架条 `.skeleton`（取快照加载，ML-5 默认 week） |
| **异常态** | 红描边占位 + 「加载失败，重试」（非法 period 400 → 显错误 message，ML-5） |

### 9.2 分布类（环 / 饼）— 价格页「按模型/渠道占比」/ 排行「分类占比」

| 项 | 规范 |
|---|---|
| 分片色 | `--chart-1…8` 顺序，图例置右侧 |
| 标签 | 中心总量用 `--font-mono`；分片标签 `--text-body-sm` |
| **空态** | 灰环占位（`--color-border` 描边环）+ 文案 |
| **加载态** | 骨架圆 |
| **异常态** | 同 §9.1 异常 |

### 9.3 排名 / 对比类（横/纵柱）— 模型排行榜主图

| 项 | 规范 |
|---|---|
| 柱色 | `--chart-1`，hover `--color-primary-600`；零基线；数值标签 `--font-mono` |
| 网格 | `--chart-grid`（仅基准轴方向） |
| period 维度 | day / week / month 切换 chip（snap 微交互），默认 week（ML-5/UL-6） |
| **空态** | 占位柱（`--color-bg-subtle`）+「暂无排行数据」（UL-6 排行空态） |
| **加载态** | 骨架柱（取快照加载态） |
| **异常态** | 非法 period 态：保留上次数据 + 顶部 danger 提示条「period 非法，已回退 week」 |

---

## 10. 核心组件规范（端级）

| 组件 | 规范（色值引用 token） |
|---|---|
| **卡片 `.card`** | `--color-bg-elevated` + `--color-border` + `--shadow-sm` + `--radius-md`，内边距 `--space-5`；hover（可点卡）升 `--shadow-md` + `translateY(-2px)`（`--ease-out`） |
| **玻璃面板 `.glass`** | `--hd-glass` + `--hd-line` + blur12 + `--radius-lg`（深色 hero 卡 / 抽屉） |
| **Spotlight 玻璃卡** | 鼠标位置径向高光：`radial-gradient(circle at cursor, color-mix(in oklch, var(--hd-cyan) 14%, transparent), transparent)`；reduced-motion 关闭 |
| **套餐 / 价格卡** | 卡片基线 + 顶部档位名（h3）+ 价格数字（`--font-mono` display 字号）+ 单位（caption muted）+ 特性列表（线性 SVG 勾）+ 底部 CTA；「推荐」卡描边 `--color-primary-500` + 角标 `POPULAR`（overline，主色徽章） |
| **模型卡** | 卡片基线 + 模型图标位 + 模型名（h4）+ id（`--font-mono` body-sm muted）+ 标签 chip（context 窗口 / 多模态 / beta）+ 单价（mono）+ 「在 Playground 试用」link |
| **表格（价格对比 / 模型列表）** | 表头 sticky + `--color-border-strong` 下边框 + `--color-text-secondary`；斑马行 `--color-bg-subtle`；行 hover `--color-surface-sunken`；价格列右对齐 `--font-mono`；分页器（见下）；列排序（表头点击 + 线性箭头 SVG） |
| **分页器** | 当前页 `--color-primary-500` 底 + fg；其余 ghost；省略号 muted；上/下页线性箭头 |
| **标签 / 徽章 `.badge`** | full 圆角，四语义 `b-suc/b-warn/b-dan/b-info` + `b-neutral`，可带状态点 `.dot`（如模型「在线」success dot） |
| **chip（示例问句 / 筛选）** | `--radius-full` + `--color-bg-subtle` 底 + body-sm；hover `--color-surface-sunken`；选中 `--color-primary-50` + 主色文字 + 主色描边；snap 微交互 |
| **toast / 公告条** | 顶部 info 条（P-1 公告区）：`--color-info-bg` + `--color-info-fg` + 左侧线性图标 + 关闭按钮；toast `--shadow-md` + `--radius-md`，自动消失，四语义底色 |
| **模态 / 抽屉** | 抽屉 `--shadow-lg` + `--radius-lg`（详见 §12.2）；遮罩 `color-mix(in oklch, var(--hd-bg) 45%, transparent)`（深色场景）/ `color-mix(in oklch, var(--color-text) 38%, transparent)`（浅色） |
| **面包屑** | 内容页（模型详情 / 协议）顶部：link 段 + 线性分隔斜杠 + 当前页 muted |
| **头像** | `--radius-full`，未登录顶栏不显；登录后显（跳控制台） |
| **空状态 `.empty`** | 居中线性 SVG 插画位 + 标题 + 说明 muted + 可选 CTA（如价格空「可用分组为空」、模型空「无可用模型」、排行空「暂无快照」） |
| **加载骨架 `.skeleton`** | shimmer 渐变（reduced-motion 减速到 2.6s） |
| **锚点目录（协议/隐私）** | 右侧 sticky 目录，当前段高亮 `--color-primary-500` 左边框 + 主色文字；滚动联动 |

---

## 11. 响应式断点（引用 DESIGN-SYSTEM §8）

| 断点 | 范围 | 导航 | 栅格 / 关键变化 |
|---|---|---|---|
| mobile | <640px | logo + 汉堡 + 主 CTA；右侧滑出全屏抽屉；触控目标 ≥44×44 | 单列；巨字 `--text-mega` clamp 自动收缩；套餐卡/模型卡纵向堆叠；表格转「卡片化」行（标签:值）；排行图横向滚动；底部安全区 `env(safe-area-inset-bottom)` |
| tablet | 640–1024px | 折叠侧导 / 顶栏精简 | 2 列；套餐卡 2/行；模型卡 2/行 |
| desktop | >1024px | 顶栏全展开（门面无固定侧栏） | 12 列，`--container-max 1200`；套餐卡 3–4/行；模型卡 3/行；协议页带右侧锚点目录 |

- 移动端关闭磁吸 CTA、Spotlight 等鼠标向微交互（无 hover 设备）；保留滚动揭示与 Count Up。
- 巨字用 `--text-mega` clamp 自适应，无需断点切字号。
- 登录/注册：desktop 走右侧滑出抽屉（站内）；mobile 抽屉转全屏覆盖层（从右滑入占满）。

---

## 12. 关键页高保真说明

### 12.1 WP-01 营销首页（深色 hero + Playground 入口）

**布局区块（单列居中，首屏≈一屏）：**
1. **背景层（body 级 fixed 独立层，`z-index:-1`）**：Flowing Threads 光束网络 canvas（节点数随面积自适应 `W*H/42000` 封顶 40，DPR≤2）+ aurora 三团辉光（`--glow-a/b/c` 径向）+ 细网格 mask。画笔色从 token 读取（`--color-primary-500/--hd-cyan/--hd-mint/--hd-lav`）合成 rgba。**置于 body 级 fixed，绝不放进会 transform 的容器**（抽屉推屏时背景不被拖动，对应 designer 坑 16）。
2. **顶栏**：深色玻璃 sticky（见 §2.2）。
3. **Hero pill 标签**：`--hd-glass` 小胶囊 + 主色 dot + 「多上游聚合 · 智能路由」overline 文案；入场 `+.1s`。
4. **巨字品牌 `Nexa·AI`**：`--text-mega` `--font-brand`，中点 `·` 走主色高光（accent 尺寸 ≈.13em，标点级，非焦点球——对应 designer 坑 19），`AI` 段 `linear-gradient(--color-primary-300 → --hd-mint → --hd-lav)` clip；**逐字 Split Text 入场**（每字 `delay=.2+i*.06s`，`translateY(.42em) rotate(3deg) scale(.94)→0`，`--ease-out`）。
5. **Slogan**：`满血直连，只付零头`（body-lg，「只付零头」走渐变 accent）；`+.62s` fade-up。
6. **Playground 入口对话框（PG-1 / P-2）**：玻璃输入框 + 只读打字机轮播 placeholder「问点什么…」（光标 `caret` 1.05s steps 闪烁）+ 内嵌「发送 / 试用」glow 按钮；下挂 3 个示例 chip；`+.78s`。点击/输入触发 → 未登录阻断 C1 → 滑出登录抽屉（保留草稿，登录后回流 Playground）。
7. **信任行**：`+.95s`，一行小字 + logo 墙（线性 SVG / 字标，克制）：支持模型家族 / 兼容 OpenAI 协议 / 计费透明。
8. **（向下滚动 · stagger 滚动揭示）轻量价值区**：3 条克制特性行（不是三栏卡墙）——智能路由 / 按量计费 / 多协议兼容，每条线性 SVG + 标题 + 一句话，区块 stagger 错位揭示（`--stagger-step`，非整块淡入）。指标 Count Up（如「N+ 模型 / N 家上游 / 99.9% 可用」）。
9. **页脚**：轻量两列。

**状态（PAGE-STATE-MATRIX P-1/P-2）**：基础渲染态（仅必备区）/ 加载骨架态（GetStatus 进行中）/ 完整渲染态（公告·FAQ·API 信息可选区按开关注入）/ 配置拉取失败降级态（默认壳 + 重试）/ 对话框未登录阻断态（跳抽屉保留草稿）/ 登录回流态。

**动效**：见 §12.3。reduced-motion：Threads 背景**减速不冻结**（读 `--motion-speed`，进一步省电时画一帧定格网络，绝不 `animation:none`），逐字/stagger 转即时显示，内容全可读。

### 12.2 WP-02/WP-03 登录 / 注册（右侧滑出抽屉 + 独立页兜底）

**招牌交互**：站内点「登录 / 开始试用」→ 玻璃抽屉从**右侧滑入**，首页内容**左移 + 轻微 scale**让位（背景 Threads 继续流动、不模糊内容）。用 body class + 非对称 `visibility` 时序驱动（开 `visibility 0s`、关 `visibility 0s linear var(--dur)`），不用 `hidden`/`display`（对应 designer 坑 17/21）。`/login`、`/register` 同时保留为直链兜底独立页（同款深色品牌区 + 玻璃表单）。

**抽屉布局**：
- 顶部：tab 切换「登录 / 注册」（chip snap）。
- 表单（玻璃 `.glass` + `--shadow-lg`）：
  - **登录（AC-2）**：邮箱/用户名 + 密码（眼睛切换）+「记住我」开关 +「忘记密码」link + Turnstile（C4）+ glow「登录」CTA。次级登录方式：OAuth / 微信扫码 / Telegram / Passkey 按钮（glass，线性 SVG 图标），分隔线「或」。
  - **注册（AC-1）**：用户名 + 邮箱 + 邮箱验证码（发送倒计时）+ 密码（强度条）+ 确认密码 + 协议同意 checkbox（link 到协议/隐私）+ Turnstile + glow「注册」CTA；带 `aff_code` 时显邀请归因提示条（info）。
- Esc / 点遮罩 / 关闭按钮关闭；打开时锁 body 滚动。

**状态**：
- 登录（AC-2）：默认 / 成功进控制台 / 账号密码错误（error + shake）/ 账号已封禁拒绝（danger 提示）/ 转 2FA 第二步（OTP 分格）。
- 注册（AC-1）：默认 / 注册成功自动登录 / 验证码错误·过期 / 用户名重复 / 发码过频限流（warning）/ 注册已关闭（RegisterEnabled=false：表单替换为「注册已关闭」空态）/ 邀请归因。
- 找回密码（AC-3）：填邮箱 → 已发送等待 → 填新密码 → 重置成功引导登录 / 令牌无效·过期 / 未注册邮箱统一提示（防枚举）。
- OAuth/微信/Telegram/2FA/Passkey（AC-4…AC-9）：外跳确认/跳转中、二维码等待/过期刷新、轮询扫码、HMAC 校验失败、provider 未启用等态，以抽屉内子视图 + toast 呈现。

### 12.3 WP-04 公开价格页（ML-4）

**布局**：浅色内容版式。顶部区块标题（display）+ 副说明 + 分组切换（匿名取公开分组 / 登录取用户分组，all 分组常显）。主体 = 套餐/分组定价卡网格（3–4/行）+ 模型单价对比表（满宽，按 endpoint/auto_groups/版本分组，价格列 `--font-mono` 右对齐，支持搜索/筛选）。可选分布环图（按模型占比）。
**状态（ML-4）**：匿名取公开分组态 / 登录取用户分组态 / 空定价列表态（可用分组为空 → empty）/ 价格页渲染态（含 endpoint/auto_groups/版本）/ 模块不可见隐藏态（pricing 关 → 整页 404 或导航隐藏）/ all 分组常显态 / 定价过滤态。
**动效**：滚动揭示卡片 stagger；价格数字 Count Up；推荐卡轻辉光。

### 12.4 WP-05 模型广场（ML-3/ML-4）

**布局**：浅色。顶部搜索框 + 分类/能力筛选 chip（多模态 / 推理 / 代码 / 视觉 / 音频）+ 排序下拉。主体 = 模型卡网格（3 列 desktop）。每卡：模型名 + id（mono）+ 上游/家族标 + 能力 chip + context 窗口 + 单价 + 「Playground 试用」link + 「可用」success dot。
**状态（ML-3）**：模型查询加载态（骨架卡）/ 可用模型去重列表态 / 无可用分组空态（empty「无可用模型」）/ 用户不存在错误态（登录态异常）/ 分组扇出去重合并态。
**动效**：卡片网格 stagger 揭示；Spotlight hover；筛选 chip snap。

### 12.5 WP-06 模型排行榜（ML-5 / UL-6）

**布局**：浅色 + **真图表**。顶部 period 切换 chip（day/week/month，默认 week）。主体 = 排名横柱图（Top 模型 by 用量，§9.3）+ 趋势折线（用量随时间，§9.1）+ 排行表（名次 / 模型 / 调用量 mono / 占比 / 环比涨跌徽章 success·danger）。
**状态（ML-5/UL-6）**：取快照加载态（骨架）/ 排行空态（暂无数据 → empty）/ 排行榜渲染态（period 维度）/ 非法 period 态（400 + 顶部 danger 提示「已回退 week」）/ 排行入口隐藏态（rankings 关 → 导航隐藏 + 直链 404）。

### 12.6 WP-07/WP-08 协议 / 隐私页（P-3/P-4）

**布局**：浅色长文，内容列 `--container-prose 68ch` 居中 + 右侧 sticky 锚点目录（desktop，滚动联动高亮）。标题 h1 + 章节 h2/h3 + 正文 body-lg；隐私页可显生效版本/日期（caption muted）。
**状态**：协议正文渲染态 / 协议空内容占位态（empty）/ 隐私正文·无版本 / 隐私正文·含生效版本日期 / 入口隐藏态（enabled=false → 404 或回首页）。
**动效**：仅段落 Scroll Reveal 渐显（克制，不伤可读性）。

---

## 13. 可访问性

- **对比度**：深色场景正文 `--hd-text` 对 `--hd-bg` 远超 AA；`--color-primary-300` 作深底提亮文字仅用于大字/图标（≥3:1）。浅色场景 `--color-text`/`--color-primary-500` 对应底均 ≥4.5:1（正文 AA），语义徽章 fg 对 bg ≥7:1。
- **焦点可见**：所有可聚焦元素 `:focus-visible` 显 `--focus-ring`（随主题切换）；抽屉打开焦点移入、关闭归还触发元素；焦点不逃逸抽屉（focus trap）。
- **键盘可达**：顶栏导航 / 抽屉表单 / tab 切换 / chip / 分页 / 表格排序全键盘可操作；Esc 关抽屉/弹层；下拉 Arrow 导航。
- **触控目标**：移动端按钮/链接/chip ≥44×44；底部安全区避让。
- **动效降级**：`prefers-reduced-motion` 下 `--motion-speed .35`（减速不冻结），Threads 背景减速、装饰动效延长、磁吸/Spotlight 关闭、逐字/stagger 即时显示；**业务关键信息（价格/模型/协议/校验提示）不依赖动画完成即可读**。
- **语义与替代**：图标全内联线性 SVG（`stroke:currentColor;fill:none`）带 `aria-label`；装饰性 canvas/aurora `aria-hidden="true"`；表单 label 关联 `for`/`aria-describedby`（hint/error）；图表提供数据表替代（排行表即为图的可读替代）。
- **无 emoji**（功能图标 0）。

---

## 14. 引用的 S6 执行文件

本端文档与下列执行文件配合驱动 S6 原型（位于 `../`）：
- `../PAGE-INVENTORY.md`：WP-01…WP-08 页面清单 + 关联 FID + 状态 + 原型优先级。
- `../STATE-MATRIX.md`：登录/注册/找回/价格/排行强状态（外跳、阻断、限流、隐藏、降级、二次确认）。
- `../COMPONENT-SPECS.md`：按钮使用矩阵、表单字段模式、滑出抽屉/模态模式、移动 sticky action。
- `../RESPONSIVE-SPECS.md`：本端断点/导航/表格卡片化/筛选/CTA 移动降级。
- `../CHART-SPECS.md`：排行榜趋势/分布/排名图指标口径、维度、空态/异常态、period 交互。

> 上游状态权威：`03_flow_prd/final/PAGE-STATE-MATRIX.md` §A（P-1…P-6、ML-4/ML-5、UL-6）+ §B（AC-1…AC-9）。原型每页须覆盖此处列出的全部状态。
