# Nexa·AI — api-docs（API 文档端）详细 UI 设计

> 端：**api-docs**（开发者 API 文档 / 公开端）。受众：接入工程师、技术评估者。
> 唯一 token 源：`../DESIGN-SYSTEM.md` + `../assets/tokens.css`（页面 `@import` 它，页内一律 `var(--token)`，**禁裸色值 / 禁 #000·#fff / 禁 emoji（功能图标一律内联线性 SVG）**）。
> 本文件只**引用** token，不另发明颜色 / 字号。所有遮罩 / 叠层用 `color-mix(in oklch, var(--token) x%, transparent)`。
> 场景定位：**深色公开端**，直接采用 `--hd-*` 深色场景色族（不依赖 `data-scheme=dark`），延续门面深色 DNA。
> 三旋钮：DESIGN_VARIANCE = restrained（文档以可读为先，零招牌动效）；**MOTION_INTENSITY = low-medium**（代码块入场 + 锚点平滑滚动 + 复制反馈，禁全屏背景动效）；VISUAL_DENSITY = medium（信息密集但留白克制）。

---

## 1. 端概述与设计读

### 1.1 本端是什么

Nexa·AI 的**开发者 API 文档站**：面向已决定接入或正在评估接入的工程师，提供从「拿到 Key → 发出第一个请求 → 跑通各能力接口 → 排错 → 装 SDK」的完整自助通道。文档本身是产品可信度的一部分——结构清晰、示例可复制、错误码可查，决定开发者多快能接入成功。

它承载 **10 个文档页面**（见 §2）：快速开始、认证说明、聊天补全 API、模型列表 API、嵌入 API、图像 API、错误码参考、限流说明、SDK 下载、changelog。

### 1.2 受众与设备

| 维度 | 取向 |
|---|---|
| 主受众 | 接入工程师（边读边复制代码、对照字段、查错误码）；技术评估者（扫接口覆盖度、鉴权方式、限流策略） |
| 设备 | 桌面优先（>1024px，双栏：导航树 + 正文 + 代码并排阅读）；平板折叠导航；移动端单列、导航转抽屉、代码块横向滚动 |
| 进入心智 | 「鉴权怎么传 / 这个接口要什么参数 / 返回长什么样 / 报这个码是什么意思 / 有没有我语言的 SDK」——目标是**最短路径找到可复制的答案** |

### 1.3 整体调性一句话

> **深色阅读场景 + teal 强调，左侧三级导航树锁定全局位置，右侧 68ch 正文保证可读行长，正文与代码区在底色、字体、行高三个维度强对比；代码块是第一公民——语法高亮、一键复制、curl/Python/Node/Go 四语言 tab 同步切换，动效只剩滚动揭示与复制反馈。**

---

## 2. 信息架构与导航

### 2.1 三栏布局骨架（desktop >1024px）

```
┌─────────────────────────────────────────────────────────────┐
│  顶栏 TopBar（sticky，--hd-bg + --hd-line 下边框，h=56）       │
│  [logo Nexa·AI 文档]   [全局搜索 ⌘K]      [主题切换] [进控制台]│
├──────────────┬──────────────────────────────┬───────────────┤
│ 左侧导航树     │  正文区 Article               │  页内目录 ToC  │
│ NavTree       │  （--container-prose 68ch）   │  （当前页 h2/  │
│ w=260 sticky  │  标题 + 正文 + 代码块 + 表格   │  h3 锚点）     │
│ 可滚动        │                              │  w=200 sticky │
│               │                              │               │
└──────────────┴──────────────────────────────┴───────────────┘
```

- 左栏 **NavTree**（w=260）、中栏 **Article**（max 768，内部正文限 `--container-prose 68ch`）、右栏 **ToC**（w=200）。三栏总宽对齐 `--container-max 1200`，居中。
- 顶栏、左栏、右栏 `position:sticky`，仅中栏正文随页面滚动。

### 2.2 导航树层级（左栏 NavTree 内容）

三级结构：**分组（overline 大写）→ 页面（可点链接）→ 当前页 h2 锚点（展开时内嵌）**。

| 分组（overline） | 页面（链接项） | 路由 |
|---|---|---|
| 入门 GETTING STARTED | 快速开始 | `/docs/quickstart` |
| | 认证说明 | `/docs/authentication` |
| API 参考 API REFERENCE | 聊天补全 API | `/docs/api/chat-completions` |
| | 模型列表 API | `/docs/api/models` |
| | 嵌入 API | `/docs/api/embeddings` |
| | 图像 API | `/docs/api/images` |
| 运行约定 OPERATIONS | 错误码参考 | `/docs/errors` |
| | 限流说明 | `/docs/rate-limits` |
| 资源 RESOURCES | SDK 下载 | `/docs/sdks` |
| | 更新日志 changelog | `/docs/changelog` |

- 当前激活页：左侧 3px teal 竖条（`--color-primary-300`，深色场景提亮色）+ 项底 `--hd-glass`，文字 `--hd-text`。
- 激活页**内嵌展开**该页 h2 锚点（次级缩进 + 当前可视锚点高亮），随滚动联动右栏 ToC。
- 顶部全局搜索 `⌘K` 唤起命令面板式搜索（覆盖所有页标题 / 接口名 / 错误码）。

### 2.3 关键路径

1. **首次接入**：快速开始 → 认证说明 → 聊天补全 API（复制示例跑通）。
2. **能力扩展**：API 参考分组内任一接口页 → 复制对应语言 tab → 查参数表。
3. **排错**：错误码参考（搜码）或 限流说明 → 回到具体接口页对照。
4. **工程化**：SDK 下载 → 选语言 → changelog 看破坏性变更。

---

## 3. 调色板（端级，深色场景子集）

本端为深色公开端，主用 `--hd-*` 色族，强调 / 链接用主色族在深底上的提亮档（`-300`），语义色用于行内提示块与错误码徽章。**全部继承自 DESIGN-SYSTEM，不发明新值。**

| token | hex / 配方 | 本端用途 |
|---|---|---|
| `--hd-bg` | `#06100F` | 页面主底（顶栏 / 左右栏 / 正文区统一深底） |
| `--hd-bg2` | `#0A1A1B` | 代码块底（与正文底拉开层次） |
| `--hd-text` | `#F4FBFB` | 正文主文字、激活导航项 |
| `--hd-muted` | `#8AA3A6` | 次要文字、未激活导航项、面包屑、ToC 默认项 |
| `--hd-line` | `color-mix(--hd-text 12%)` | 栏分隔线、表格边框、代码块描边 |
| `--hd-glass` | `color-mix(--hd-text 6%)` | 导航激活底、行内 code 底、tab 容器底 |
| `--hd-glass-strong` | `color-mix(--hd-text 10%)` | 导航 hover 底、复制按钮 hover |
| `--color-primary-300` | `#5FB9C2` | 深底上链接 / 强调文字 / 激活竖条（对 `--hd-bg` ≥3:1） |
| `--color-primary-400` | `#2E9AA3` | 链接 hover、tab 激活下划线 |
| `--hd-cyan` | `#2BB7C2` | 代码语法高亮：关键字 / 方法名 |
| `--hd-mint` | `#5FD4DE` | 代码语法高亮：字符串 |
| `--hd-lav` | `#7AA2F7` | 代码语法高亮：数字 / 常量 |
| `--color-success` 三件套 | base `#1F8F5F` / bg / fg / border | `GET` 方法徽章、`2xx` 行内提示块 |
| `--color-info` 三件套 | base `#1E6FD9` | `info` 提示块、`POST` 方法标注 |
| `--color-warning` 三件套 | base `#B5740A` | 弃用 / 限流警示块、`429` 标注 |
| `--color-danger` 三件套 | base `#D14A33` | `4xx/5xx` 错误码徽章、破坏性变更标记 |

> 深色场景中语义提示块（callout）：底用 `color-mix(in oklch, var(--color-{role}) 16%, transparent)`，左竖条用 base 色，文字用 `--hd-text`，确保深底上≥4.5:1。

---

## 4. 字体字阶（端级）

文档端**不用 `--text-display` / `--text-mega`**（无品牌巨字场景）。正文可读性是第一优先，正文与代码用不同字体族强区分。

| 层级 | token | 用途 | 本端备注 |
|---|---|---|---|
| h1 | `--text-h1` 2.5rem / 700 | 页面主标题（每页顶部一个） | `--font-brand`，`--tracking-snug` |
| h2 | `--text-h2` 1.875rem / 600 | 接口分组 / 主区段（进 ToC + 锚点） | 顶部 `--space-8` 间距，前置 `#` 锚点链接 |
| h3 | `--text-h3` 1.5rem / 600 | 子区段（请求参数 / 响应 / 示例） | 进 ToC 二级 |
| h4 | `--text-h4` 1.25rem / 600 | 字段组 / 小节 | |
| body-lg | `--text-body-lg` 1.0625rem / 1.7 | 页面引导段（每页首段） | `--lh-relaxed` 提升导读舒适度 |
| body | `--text-body` .9375rem / 1.55 | 默认正文 | 限宽 `--container-prose 68ch` |
| body-sm | `--text-body-sm` .8125rem | 参数表、表格 | |
| caption | `--text-caption` .75rem | 字段说明、图注、版本日期 | `--hd-muted` |
| overline | `--text-overline` .6875rem / 600 / `--tracking-wide` 大写 | 导航分组头、方法 / 类型标签 | |
| code | `--text-code` .844rem | 行内 code + 代码块 | `--font-mono`（JetBrains Mono） |

**可读性硬规则：**
- 正文行长锁 `--container-prose 68ch`（65–75ch 区间），代码块**不限宽**（横向滚动）。
- 正文 `--font-sans` + `--lh-normal 1.55`；代码 `--font-mono` + 行高 1.6。**字体族差异 + 底色差异（`--hd-bg` vs `--hd-bg2`）+ 行高差异**三重区分正文与代码。
- 行内 `code`：`--font-mono` + `--hd-glass` 底 + `--radius-xs` + 左右 `--space-1` padding，与周围正文明确隔开。

---

## 5. 间距与栅格（端级）

间距全取 `--space-*` 阶。

| 项 | 值 | 说明 |
|---|---|---|
| 三栏总容器 | `--container-max 1200px` 居中 | 左 260 + 中 flex + 右 200 |
| 左栏 NavTree | w=260，内边距 `--space-5` | sticky，独立滚动 |
| 中栏正文 | 内部限 `--container-prose 68ch`，内边距上下 `--space-8` / 左右 `--space-6` | |
| 右栏 ToC | w=200，内边距 `--space-5` | sticky |
| 正文段落间距 | `--space-4` | |
| h2 上间距 | `--space-8` | 区段呼吸 |
| 代码块外间距 | 上下 `--space-5` | |
| 顶栏 TopBar 高 | 56px，左右 padding `--space-6` | sticky top:0 |

栅格：desktop 三栏；tablet（640–1024）左栏折叠为可唤起抽屉，正文 + ToC 两栏并存或 ToC 隐藏；mobile（<640）单列，导航与 ToC 均转抽屉。

---

## 6. 阴影 / 圆角应用

深色场景以**边框 + 底色层次**替代重阴影，阴影仅用于浮层。

| 表面 | radius | elevation |
|---|---|---|
| 代码块容器 | `--radius-md` | 无阴影，`--hd-line` 1px 描边 + `--hd-bg2` 底 |
| 行内 code | `--radius-xs` | 无 |
| 方法 / 类型徽章 | `--radius-full` | 无 |
| callout 提示块 | `--radius-md` | 无，左竖条 3px |
| 命令面板搜索（⌘K 浮层） | `--radius-lg` | `--shadow-lg` |
| ToC / 复制按钮 tooltip | `--radius-sm` | `--shadow-md` |
| 语言 tab 容器 | `--radius-sm`（顶部） | 无 |

---

## 7. 按钮系统 · 每状态色值

文档端按钮少（顶栏「进控制台」、SDK 页「下载」、复制按钮），变体取 DESIGN-SYSTEM `.btn-*` 基线，深色场景按需用 `glass`。**复制按钮见 §10 代码块组件，单列规范。**

尺寸：sm h=32 / 内边距 `--space-3` / `--text-body-sm`；md h=40 / 内边距 `--space-4` / `--text-body`；lg h=48 / 内边距 `--space-5` / `--text-body-lg`。圆角统一 `--radius-sm`，图标与文字间距 `--space-2`。过渡 `transform --dur-1 --ease-snap` + 色/影 `--dur-2`。

### 7.1 primary（顶栏「进控制台」/ SDK「下载」）

| 状态 | 背景 | 文字 | 边框 | 阴影 | 说明 |
|---|---|---|---|---|---|
| default | `--color-primary-500` | `--color-primary-fg` | 无 | `--shadow-xs` | |
| hover | `--color-primary-600` | `--color-primary-fg` | 无 | `--shadow-xs` | |
| active | `--color-primary-700` | `--color-primary-fg` | 无 | 无 | |
| focus | `--color-primary-500` | `--color-primary-fg` | 无 | `--focus-ring` | focus-visible |
| disabled | `--color-disabled-bg` | `--color-disabled` | `--color-border` | none | |
| loading | `--color-primary-500` | spinner=`--color-primary-fg` | 无 | `--shadow-xs` | 禁点 |

### 7.2 glass（深色次级，如「查看全部 SDK」）

| 状态 | 背景 | 文字 | 边框 | 阴影 | 说明 |
|---|---|---|---|---|---|
| default | `--hd-glass` + blur8 | `--hd-text` | `--hd-line` | 无 | |
| hover | `--hd-glass-strong` | `--hd-text` | `color-mix(--hd-text 24%)` | 无 | |
| active | `--hd-glass-strong` | `--hd-text` | `color-mix(--hd-text 24%)` | 无 | |
| focus | `--hd-glass` | `--hd-text` | `--hd-line` | `--focus-ring` | |
| disabled | `--hd-glass` | `--hd-muted` | `--hd-line` | none | |

### 7.3 ghost / link（正文行内链接、ToC 项）

| 变体 · 状态 | 背景 | 文字 | 说明 |
|---|---|---|---|
| ghost default | 透明 | `--hd-muted` | 导航 / ToC 未激活项 |
| ghost hover | `--hd-glass` | `--hd-text` | |
| ghost active(选中) | `--hd-glass` + 左 3px `--color-primary-300` | `--hd-text` | 当前页 / 当前锚点 |
| link default | 透明 | `--color-primary-300` | 正文内链接 |
| link hover | 透明 | `--color-primary-400` + 下划线 | |
| link focus | 透明 | `--color-primary-300` | `--focus-ring` |

---

## 8. 表单元素

文档端表单极少，仅 **全局搜索框** 与 **SDK 页语言下拉筛选**。沿用 `.input` 基线，深色场景覆盖底色为 `--hd-glass`。

| 元素 · 状态 | 背景 | 文字 / 占位 | 边框 | 说明 |
|---|---|---|---|---|
| 搜索框 default | `--hd-glass` | 占位 `--hd-muted` | `--hd-line` | 前置线性 SVG 放大镜图标 + 右侧 `⌘K` kbd 提示 |
| 搜索框 focus | `--hd-glass-strong` | 文字 `--hd-text` | `--color-primary-300` | `--focus-ring` |
| 搜索框 filled | `--hd-glass-strong` | 文字 `--hd-text` | `--hd-line` | 右侧出现清除（线性 SVG ×） |
| 搜索框 disabled | `--hd-glass` | `--hd-muted` | `--hd-line` | cursor:not-allowed |
| SDK 语言下拉 default | `--hd-glass` | `--hd-text` | `--hd-line` | 右侧线性 SVG chevron |
| SDK 语言下拉 focus | `--hd-glass-strong` | `--hd-text` | `--color-primary-300` | `--focus-ring` |

- label：`--text-body-sm` semibold `--hd-muted`，位于元素上方。
- 命令面板搜索结果列表：每项 hover `--hd-glass`，键盘 ↑↓ 选中态加 `--hd-glass-strong` + 左竖条，回车跳转。无结果走空态（§10）。
- error 态（如搜索服务不可用）：提示文案 `--text-caption` + 前置线性 SVG 警示图标，色 `color-mix(--color-danger 80%, --hd-text)`（深底可读）。

---

## 9. 图表规范

> 本端为**纯文档端，无看板 / 概览 / 数据统计页**，§6.2「数据页必须有真图表」门禁**不适用**。文档内若需表达流程 / 调用时序，用**线性 SVG 流程图**（描边 `--hd-line`，节点底 `--hd-glass`，箭头 / 强调 `--color-primary-300`），不引入图表库，不堆数字卡。changelog 的版本时间线为 SVG 竖线节点列表（节点点 `--color-primary-300`，连接线 `--hd-line`），非图表组件。

如后续新增「API 调用量 / 状态码分布」类开发者控制台数据，归属 console 端，不在本文档端范围。

---

## 10. 核心组件规范

### 10.1 代码块组件 CodeBlock（本端第一公民，重点规范）

文档端最核心组件。结构：**头部栏（语言 tab + 复制按钮）+ 代码体（行号 + 语法高亮）**。

```
┌────────────────────────────────────────────────┐
│ [curl] [Python] [Node] [Go]          [⧉ 复制]   │  ← 头部 --hd-glass 底
├────────────────────────────────────────────────┤
│ 1  curl https://api.nexa.ai/v1/chat/completions│  ← 代码体 --hd-bg2 底
│ 2    -H "Authorization: Bearer $NEXA_KEY"      │     行号列 --hd-muted
│ 3    -d '{ "model": "gpt-4o", ... }'           │     横向滚动不限宽
└────────────────────────────────────────────────┘
```

**容器规范：**

| 部件 | 规范 |
|---|---|
| 外容器 | `--hd-bg2` 底 + `--hd-line` 1px 描边 + `--radius-md`，外间距上下 `--space-5`，无阴影 |
| 头部栏 | h=40，`--hd-glass` 底，下边框 `--hd-line`，左侧语言 tab、右侧复制按钮，左右 padding `--space-3` |
| 代码体 | `--font-mono` + `--text-code` + 行高 1.6，内边距 `--space-4`，`overflow-x:auto`（**不限宽，横向滚动**），底 `--hd-bg2` |
| 行号列 | 可选，`--hd-muted` + 右对齐 + 右 `--space-3` 间距 + 不可选中（`user-select:none`）；与代码间 `--hd-line` 细分隔 |

**语法高亮配色（深底 token tint）：**

| 语法元素 | token | 备注 |
|---|---|---|
| 普通文本 / 标点 | `--hd-text` | |
| 关键字 / HTTP 方法 / 函数名 | `--hd-cyan` | |
| 字符串 / URL | `--hd-mint` | |
| 数字 / 布尔 / null / 常量 | `--hd-lav` | |
| 注释 | `--hd-muted` | italic |
| 变量 / 占位（如 `$NEXA_KEY`） | `--color-primary-300` | 强调用户需替换处 |
| 行高亮（diff/重点行） | 行底 `color-mix(--color-primary-500 14%, transparent)` + 左 2px `--color-primary-300` | |

> 语法高亮库冻结为 **Shiki / Prism（CDN）**，主题用上述 token 自定义映射；不直接引第三方深色主题以免裸色值。

**多语言 tab 切换（curl / Python / Node / Go）：**

| tab 状态 | 文字 | 底 | 下划线 | 说明 |
|---|---|---|---|---|
| default | `--hd-muted` | 透明 | 无 | |
| hover | `--hd-text` | `--hd-glass-strong` | 无 | |
| active | `--hd-text` | `--hd-glass-strong` | 2px `--color-primary-400`（底部） | |
| focus | `--hd-text` | — | — | `--focus-ring` |

- tab 切换：**全页同步**——切到 Python 后，本页所有代码块同步切 Python（记忆到 localStorage），减少重复切换。
- tab 切换动效：内容 fade + 轻 translateY（`--dur-2 --ease-out`），`low-medium` 强度；reduced-motion 下走 `--motion-speed .35` 减速，**不冻结**，内容立即可读。

**复制按钮 CopyButton（单列规范）：**

| 状态 | 图标 | 文字 / 反馈 | 底 | 说明 |
|---|---|---|---|---|
| default | 线性 SVG 双页（复制） | 「复制」（hover 才显文字，移动端常显） | 透明 | 文字 `--hd-muted` |
| hover | 同上 | 「复制」 | `--hd-glass-strong` | 文字 `--hd-text` |
| active(点击瞬间) | — | — | `--hd-glass-strong` | scale 0.96 `--ease-snap` 回弹 |
| copied(成功) | 线性 SVG 对勾 | 「已复制」 | 透明 | 图标 / 文字转 `--color-success` 提亮档（`color-mix(--color-success 70%, --hd-text)`），1.6s 后回 default |
| focus | 复制图标 | | 透明 | `--focus-ring` |

- 复制动效：图标「复制→对勾」切换走交叉淡入（`--dur-1`），成功反馈是文档端**唯一的微交互动效**，符合 low-medium。

### 10.2 参数表 ParamTable（接口页核心）

请求参数 / 响应字段统一表格。沿用全局表格基线，深色覆盖：表头底 `--hd-bg2`、行边框 `--hd-line`、斑马行 `color-mix(--hd-text 3%, transparent)`、行 hover `--hd-glass`。

列：**字段名（`--font-mono` + `--hd-text`）| 类型（type 徽章）| 必填（线性 SVG 红点 or 空）| 说明（`--hd-muted`，支持行内 code）**。

| 类型徽章 | 底 / 文字 | 类型 |
|---|---|---|
| `string` | `--hd-glass` / `--hd-mint` | |
| `integer` / `number` | `--hd-glass` / `--hd-lav` | |
| `boolean` | `--hd-glass` / `--hd-cyan` | |
| `object` / `array` | `--hd-glass` / `--color-primary-300` | 可展开嵌套字段（行内折叠） |

### 10.3 HTTP 方法徽章 MethodBadge

接口标题前缀，`--radius-full`，`--text-overline` 大写，`--font-mono`：

| 方法 | 底 | 文字 |
|---|---|---|
| `GET` | `color-mix(--color-success 18%, transparent)` | `color-mix(--color-success 75%, --hd-text)` |
| `POST` | `color-mix(--color-info 18%, transparent)` | `color-mix(--color-info 75%, --hd-text)` |
| `DELETE` | `color-mix(--color-danger 18%, transparent)` | `color-mix(--color-danger 75%, --hd-text)` |

### 10.4 提示块 Callout

行内提示，`--radius-md` + 左 3px 竖条 + 底 `color-mix(--color-{role} 16%, transparent)` + 前置线性 SVG 图标：

| 类型 | 竖条 / 图标 | 用途 |
|---|---|---|
| info | `--color-info` | 补充说明、最佳实践 |
| success | `--color-success` | 成功响应示例提示 |
| warning | `--color-warning` | 限流 / 弃用 / 注意事项 |
| danger | `--color-danger` | 破坏性变更、安全警告（如「勿在客户端暴露 Key」） |

### 10.5 错误码表 ErrorTable（错误码参考页）

列：**HTTP 状态 | 错误码（`--font-mono`）| 含义 | 处理建议**。状态徽章按 2xx=success / 4xx=warning / 5xx=danger 着色（同 §10.3 配方）。支持顶部锚点跳转 + `⌘K` 搜码定位（命中行高亮 `color-mix(--color-primary-500 14%, transparent)` 1.6s）。

### 10.6 面包屑 Breadcrumb

正文顶部，`--text-body-sm` + `--hd-muted`，分隔用线性 SVG chevron；末项（当前页）`--hd-text`。结构：`文档 / {分组} / {页面}`。

### 10.7 ToC（右栏页内目录）

当前页 h2 / h3 锚点列表，`--text-body-sm`。默认 `--hd-muted`，当前可视区段（scrollspy 高亮）`--color-primary-300` + 左 2px 竖条。点击平滑滚动到锚点（`scroll-behavior:smooth`，reduced-motion 下 auto）。

### 10.8 状态：空态 / 加载 / 异常

| 场景 | 表现 |
|---|---|
| 搜索无结果 | `.empty`：居中线性 SVG（放大镜+斜线）+「未找到匹配的页面 / 接口 / 错误码」+ `--hd-muted` |
| 代码块渲染中 | `.skeleton` 行条（深底版：`linear-gradient(--hd-glass→--hd-glass-strong→--hd-glass)` shimmer），reduced-motion 降速 2.6s |
| changelog 加载中 | 时间线骨架节点 |
| 接口示例加载失败 | callout danger：「示例加载失败」+ 重试 link |
| 页面 404 | 居中线性 SVG + 「文档页面不存在」+「返回快速开始」link |

---

## 11. 响应式断点

| 断点 | 范围 | 导航 | 布局 |
|---|---|---|---|
| mobile | <640px | 顶栏汉堡 → 左侧导航树滑出**抽屉**（`--shadow-lg`）；ToC 收进正文顶部可折叠「本页目录」；触控目标 ≥44×44 | 单列正文；代码块横向滚动（保留行号），复制按钮常显文字 |
| tablet | 640–1024px | 左栏可折叠（图标态 / 展开态），ToC 默认隐藏（可顶部 toggle） | 正文双栏退单栏，正文限宽放宽到 76ch |
| desktop | >1024px | 三栏全显（NavTree 260 + 正文 + ToC 200），均 sticky | 正文锁 `--container-prose 68ch` |

移动特异：抽屉从左滑入（`--ease-out --dur-3`），遮罩 `color-mix(--hd-bg 60%, transparent)`；底部安全区留白；代码块在窄屏不缩字号，靠横向滚动保证可读。

---

## 12. 关键页高保真说明

### 12.1 快速开始 `/docs/quickstart`

- 区块：面包屑 → h1「快速开始」→ body-lg 引导段 → 「三步跑通」编号区段（① 获取 API Key ② 设置环境变量 ③ 发出第一个请求）。
- 每步含一个 **CodeBlock**（默认 curl tab），第 ③ 步代码块高亮 `$NEXA_KEY` 占位（`--color-primary-300`）。
- 末尾 callout info：「下一步 → 认证说明 / 聊天补全 API」link。
- 动效：区段滚动揭示（fade + translateY，`--ease-out --dur-3`，`--stagger-step` 错位）。

### 12.2 认证说明 `/docs/authentication`

- h1 + 引导段 → h2「Bearer Token 认证」（CodeBlock 展示 header 写法）→ h2「获取与轮换 Key」→ **callout danger**：「切勿在前端 / 客户端代码暴露 API Key」→ 错误鉴权示例（CodeBlock，401 响应，`error` 字段 `--hd-mint`）。
- ParamTable 列出 `Authorization` header 规范。

### 12.3 聊天补全 API `/docs/api/chat-completions`（接口页模板）

接口页统一模板，区块顺序：
1. **MethodBadge `POST` + 接口标题** + endpoint（行内 code）。
2. 引导段（一句话说明）。
3. h3「请求参数」→ **ParamTable**（`model` / `messages` / `temperature` / `stream` …，必填红点、type 徽章、嵌套展开）。
4. h3「请求示例」→ **CodeBlock**（curl/Python/Node/Go 四 tab 同步）。
5. h3「响应」→ ParamTable（响应字段）+ CodeBlock（200 JSON 示例，含 success callout）。
6. h3「流式响应」→ 说明 `stream:true` 的 SSE 格式 + CodeBlock。
7. h3「错误」→ 链接到错误码参考的相关锚点。

### 12.4 错误码参考 `/docs/errors`

- h1 + 引导段 → 顶部「按状态码快速跳转」chip 行（`2xx/4xx/429/5xx` 锚点 chip）→ 按状态分组的 **ErrorTable**（每组 h2，进 ToC）。
- 支持 `⌘K` 搜码定位 + 命中高亮。

### 12.5 限流说明 `/docs/rate-limits`

- h1 + 引导段 → h2「限流维度」（按 Key / 按模型 / 按租户）ParamTable → h2「限流响应头」（`X-RateLimit-*` 字段表）→ **callout warning**「触发 429 后的退避策略」+ CodeBlock（指数退避重试示例）→ SVG 简易时序图（请求→429→退避→重试）。

### 12.6 SDK 下载 `/docs/sdks`

- h1 + 引导段 → 语言下拉筛选 → 四个 SDK 卡片（Python / Node / Go / 其他），每卡：语言图标（线性 SVG）+ 版本徽章 + 安装命令 CodeBlock（`pip install` / `npm i` / `go get`）+ primary「下载 / 查看仓库」按钮 + changelog link。

### 12.7 changelog `/docs/changelog`

- h1 + 引导段 → 版本时间线（SVG 竖线 + 节点）。每版本节点：版本号（`--font-mono` + `--hd-text`）+ 日期（caption `--hd-muted`）+ 变更列表，破坏性变更前置 **danger 徽章「BREAKING」**，新增 success、修复 info。

> 模型列表 API / 嵌入 API / 图像 API 三页**复用 §12.3 接口页模板**（仅参数 / 方法 / 示例不同：模型列表为 `GET`，嵌入 / 图像为 `POST`），不另出布局描述。

---

## 13. 可访问性

- **对比度**：正文 `--hd-text` 对 `--hd-bg` ≈ 18:1（远超 AAA）；`--hd-muted` 对 `--hd-bg` ≥ 4.5:1（次要文字 AA）；链接 `--color-primary-300` 对 `--hd-bg` ≥ 3:1（大字 / UI 组件 AA），正文内链接同时加下划线 hover 不靠纯色区分；语义 callout 文字均经 `color-mix` 提亮至深底上 ≥ 4.5:1。
- **代码可读**：语法高亮各 token 对 `--hd-bg2` 均 ≥ 4.5:1；高亮不作为唯一信息载体（diff 行另加左竖条）。
- **焦点可见**：所有可聚焦元素（导航项 / tab / 复制按钮 / 搜索 / 链接）`focus-visible` 显示 `--focus-ring`，深底上清晰。
- **键盘可达**：`⌘K` 唤起搜索；导航树 / tab / ToC 全 Tab 可达，命令面板 ↑↓ + 回车操作；代码块复制按钮可 Tab 聚焦 + 回车触发。
- **语义结构**：每页单一 `h1`，h2/h3 严格层级供屏幕阅读器 + ToC scrollspy；代码块 `<pre><code>` 标注 `lang` 属性；复制成功通过 `aria-live="polite"` 播报「已复制」。
- **触控目标**：移动端导航项 / tab / 复制按钮 ≥ 44×44。
- **动效降级**：`prefers-reduced-motion` 下 `--motion-speed .35`——tab 切换 / 滚动揭示 / 复制反馈减速不冻结，锚点滚动转 `auto`，骨架 shimmer 延至 2.6s；所有文档内容**不依赖动画完成即可见**。
- **无 emoji**：所有功能图标（搜索 / 复制 / 对勾 / chevron / 警示 / 语言图标）一律内联线性 SVG。
