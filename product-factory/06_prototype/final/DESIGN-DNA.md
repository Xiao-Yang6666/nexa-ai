# Nexa·AI — 视觉 DNA v2（冻结版）

> 路线：**极简骨架 + 一个克制但惊艳的主视觉**。对标 Linear / Vercel / cursor.com 的克制度与惊艳度。
> 本文件冻结一套可复用视觉语言，供 `home.html` / `login.html` / `register.html` 等公开站页面统一引用。
> 所有 token 已落地到 `assets/tokens.css`，页面只引用 `var(--token)`，禁止页内写死色值。

---

## 一句话概括

**深色场景 + teal 辉光，巨字品牌逐字浮现，背景一张「流动光束网络」(Flowing Threads) canvas —— 极简到只剩一屏，但那张会呼吸、会流光、会随鼠标聚拢的网络，就是记忆点。**

---

## 1. 品牌视觉性格

| 维度 | 取向 |
|---|---|
| 气质 | 冷静、精密、有科技纵深感（"网关 / 路由 / 直连"的工程美） |
| 克制度 | 首屏≈一屏：导航 + 巨字 + 一句话 + chat 输入 + 留白。**不堆**卡片墙 / FAQ / 代码块 / 长 footer |
| 记忆点 | 唯一一个主效果（Flowing Threads 光束网络），其余一律安静 |
| 反 AI-slop | 无 emoji、无渐变滥用、无"三栏图标卡"套路；图标全用内联线性 SVG（stroke + currentColor） |

---

## 2. 配色

### 2.1 品牌主色（沿用，跨主题可换肤）
- `--color-primary-500: #0E7C86`（teal，浅色场景）
- 深色场景下 teal 提亮：`--color-primary-300 / --hd-cyan / --hd-mint`

### 2.2 深色 Hero / 品牌区色族（v2 新增，promote 成全局 token）
集中声明在 `:root`，供所有深色门面区复用：

| token | 值 | 用途 |
|---|---|---|
| `--hd-bg` | `#06100F` | 深色场景主底（比旧版更暗，辉光对比更强） |
| `--hd-bg2` | `#0A1A1B` | 顶部径向渐变次底 |
| `--hd-text` | `#F4FBFB` | 深色场景主文字 |
| `--hd-muted` | `#8AA3A6` | 深色场景次要文字 |
| `--hd-cyan` | `#2BB7C2` | 辉光 / 光束 青 |
| `--hd-blue` | `#1E6FD9` | 辉光 / 光束 蓝（纵深） |
| `--hd-mint` | `#5FD4DE` | 巨字渐变高光 薄荷 |
| `--hd-lav` | `#7AA2F7` | 巨字渐变收尾 薰衣草 |
| `--hd-line` | `color-mix(--hd-text 12%)` | 网格线 / 玻璃描边 |
| `--hd-glass` | `color-mix(--hd-text 6%)` | 玻璃面板底 |
| `--hd-glass-strong` | `color-mix(--hd-text 10%)` | 玻璃 hover 底 |

### 2.3 辉光配方（aurora 三团）
深色 hero `::before` 三团径向光晕，全部用 `color-mix(in oklch, var(--token) x%, transparent)`：
- 左上：`--color-primary-500` 60% → teal 主辉
- 右上：`--hd-cyan` 50% → 青色补光
- 底中：`--hd-blue` 34% → 蓝色纵深

### 2.4 色值纪律
- 页面 CSS **只用 `var(--token)`**；遮罩 / 半透明叠层用 `color-mix(in oklch, var(--token) x%, transparent)`。
- canvas 主视觉的绘制色 **从 token 读取** (`getComputedStyle` → `--color-primary-500/--hd-cyan/--hd-mint/--hd-lav`)，再合成 `rgba()` 作画笔；JS 内的 hex 仅作 token 缺失时的兜底 fallback。
- CSS `<style>` 块裸色值 = **0**（已探针验证）。

---

## 3. 字体

| 用途 | 栈 |
|---|---|
| 巨字品牌 | `'Gilroy','Inter Tight', var(--font-sans)` — 几何 sans，回退到 Inter/Noto Sans SC |
| 正文 | `var(--font-sans)` = Inter / Noto Sans SC |
| 等宽（指标 / 代码） | `var(--font-mono)` = JetBrains Mono |

新增字阶：
- `--text-mega: clamp(3.2rem, 12vw, 9rem)` — 巨字品牌专用，视口自适应
- `--tracking-tight: -.04em` — 巨字负字距，收紧成"一坨光"

---

## 4. 主视觉效果：FLOWING THREADS（光束网络）

**是什么**：整屏深色 canvas 上，14–40 个发光节点用细线连成网络（每节点连最近 2–3 个，不全连，保持克制）；每条连线上有一颗**沿线流动的光点**持续游走；节点缓慢漂移、轻微呼吸（柔光晕脉动）；鼠标靠近时，附近节点被**轻微吸引**、连线**提亮**。叠加在 aurora 辉光 + 细网格之上，制造"光从屏后透出 + 数据在网络里流动"的隐喻（正契合 AI 网关 / 路由产品）。

**怎么实现**：
- 纯 `canvas 2D`，**零外部依赖**，单文件内联。
- DPR 自适应（cap 在 2）保证清晰；节点数随画布面积自适应（`W*H/42000`，封顶 40）控制 **motion budget**。
- 画笔色全部从 CSS token 读取后合成 `rgba()`。
- 流动光点 = `requestAnimationFrame` 推进每条线的参数 `t∈[0,1]`，配 `createRadialGradient` 做柔光头。
- **省电与降级**：标签页 `visibilitychange` 隐藏即 `cancelAnimationFrame` 暂停；`prefers-reduced-motion` 时只画一帧**定格网络**（静态），绝不动。
- resize 节流（200ms）重建。

**为什么是它（选型）**：react-bits 动效词库里 Beams / Particles / Threads / Aurora 都候选。Particles 易显碎、Beams 偏装饰；**Threads（节点网络 + 流动光点）语义最贴"网关把多家上游连成一张网、请求在网里被路由"**，且单一效果就能撑满一屏、有交互记忆点，符合"一个主效果就够"的克制原则。

---

## 5. 动效语言（缓动 / 时长 / stagger）—— 全站复用 token

| token | 值 | 语义 |
|---|---|---|
| `--ease-out` | `cubic-bezier(.16,1,.3,1)` | **主力出场曲线**（弹性收尾），所有入场/滚动揭示用它 |
| `--ease-in-out` | `cubic-bezier(.65,0,.35,1)` | 往复 / 呼吸 / aurora 漂移 |
| `--ease-snap` | `cubic-bezier(.34,1.56,.64,1)` | 轻回弹（按钮 hover、图标、chip） |
| `--dur-1` … `--dur-5` | `.15 / .3 / .55 / .75 / 1.1s` | 标准时长档位 |
| `--stagger-step` | `.06s` | 逐元素/逐字错位步长 |

**入场编排（首屏一次性 reveal，不靠滚动）**：
1. pill 标签 `+.1s`
2. 巨字 **逐字浮现**（Split Text）：每字 `delay = .2 + i*0.06s`，`translateY(.42em) rotate(3deg) scale(.94) → 0`
3. slogan `+.62s`
4. chat 输入 `+.78s`
5. 信任行 `+.95s`

**微动**：在线点 `breath`（2.4s scale 脉动）、chat 光标 `caret`（1.05s steps 闪烁）、aurora 辉光 `18s` 缓慢漂移。

---

## 6. 文案规范

- 巨字品牌：**`Nexa·AI`**（带中点，呼应竞品 `rout·AI` 玩法；中点 `·` 走主色高光，`AI` 段走 teal→mint→lavender 渐变）。
- Slogan：**短促有力**，`满血直连，只付零头`（≤6 字 + 6 字，"只付零头"走渐变 accent）。禁用又长又土的说明句。
- chat placeholder：`问点什么…`（像问 LLM），下挂 3 个示例 chip。

---

## 7. 给登录 / 注册页复用的规范

> 目标：登录/注册页与首页**同源同质**。沿用以下，无需重新发明。

1. **深色品牌区**直接用 `--hd-*` 全局色族 + aurora 三团辉光配方（§2.3）+ 细网格 mask。
2. （可选升级）品牌区背景换成 **同款 Flowing Threads canvas**，与首页形成视觉连续；或保留静态 aurora（轻量）。
3. **玻璃组件**：`background:var(--hd-glass); border:1px solid var(--hd-line); backdrop-filter:blur(8–14px)`；hover 升到 `--hd-glass-strong`。
4. **缓动/时长**统一用 `--ease-out` / `--dur-*` / `--stagger-step`；入场用 `fade-up` + 逐元素 stagger。
5. **巨字/渐变文字**用 `--text-mega` / `--tracking-tight` + `linear-gradient(--color-primary-300 → --hd-mint → --hd-lav)` clip。
6. **按钮**：主行动用 `.btn-glow`（teal 渐变 + 主色辉光阴影 + `--ease-snap` 回弹）；次级用 `.btn-glass`。
7. **图标**：一律内联线性 SVG（`stroke:currentColor; fill:none`），禁 emoji。
8. **prefers-reduced-motion**：所有页面统一兜底——关动效、内容全显、canvas 走静态定格帧。

---

## 8. 自评分（按 impeccable / design-taste 质量门）

| 维度 | 分 | 说明 |
|---|---|---|
| 克制 / 信息密度 | 9 | 一屏即一屏，无卡片墙/FAQ/长 footer，对齐竞品克制度 |
| 主视觉惊艳度 | 9 | Flowing Threads 有语义、有交互、会呼吸；单一效果不喧宾夺主 |
| craft / 细节 | 8.5 | 巨字逐字、中点高光、玻璃 focus 辉光、光标闪烁、DPR/节流/可见性暂停都到位 |
| 反 AI-slop | 9 | 无 emoji、无套路三栏、渐变只在巨字与主行动收敛使用 |
| token 纪律 | 9 | CSS 裸色值 0；canvas 画笔从 token 读取；动效全 token 化 |
| 可访问性 | 8.5 | reduced-motion 完整降级、aria-label、canvas aria-hidden |
| **综合** | **8.8** | 达标（≥8）。可继续打磨项：巨字可选真·可变字体文件、Threads 可加极淡景深分层 |

---

## 9. 验证记录

- JS 语法探针（`new Function` 解析每个 `<script>`）：**1 script / 0 bad**。
- CSS `<style>` 块裸色值（排除 `--hd-*` 声明、`color-mix(...var...)`、mask）：**0**。
- 巨字 / reduced-motion / emoji / token 使用：全部通过（emoji 0，reduced-motion guard 命中，134 处 `var()`）。
- 既有 token 名全部保留（其他页依赖未破坏），新增 DNA token 全部就位。
