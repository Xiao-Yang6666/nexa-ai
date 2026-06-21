# DESIGN-MEMORY — RoutifyAPI 设计记忆（designer 角色 S5-S6 持久层）

> 角色：designer-s5-s6（autonomous-product-factory 第三角色，owns S5-S6 设计连续性）。
> 用途：人可审的设计交接层，与 DB fact 双写。任何 durable 设计决策先写此处再落 DB（`project:routifyapi-newapi-product / role:designer / stage:S5`）。
> 上游冻结（只读）：PRD + PAGE-STATE-MATRIX（63 页 290+ 状态 14 分区 A~N）+ DATA-MODEL + FUNCTION-LIST（231）+ PROJECT.md。设计师**消费**之，不发明产品范围 / 不改功能名 / 不重定义业务规则。

---

## 1. 品牌 & 视觉意图

- **产品个性**：developer-tool-clean / 专业可信 / 克制（理性、数据导向、给开发者「快、准、不骗钱」的安全感）。不走花哨营销腔，不走霓虹/玻璃拟态/暗 mesh 的 AI-SaaS 套路。
- **register**：公开站 = brand register（设计即产品）；控制台/后台/API 文档 = product register（设计服务于产品）。共享同一 token 源。
- **物理场景句**（impeccable Theme 门）：「后端工程师/独立开发者，白天在 13–16 寸笔记本接 API、深夜盯用量曲线和余额排查计费，明亮工位或居家暖光，心态要快要准别骗我钱。」→ 默认 light、完整 dark 映射、主题可切持久化。
- **配色策略**：Restrained（tinted neutrals + 单一 teal 主色作 ≤10% 功能强调）；营销 hero 局部可触 Committed 边缘。
- **主色为何 teal 不蓝**：规避「API 网关→深蓝」一阶反射；teal（H≈195）有路由/连接语义联想且辨识度高。蓝色退守为 info/链接（呼应 RoutifyApi 但不当主色）。

## 2. 采纳 routifyapi 哪些视觉线索 vs 刻意不抄（含红线）

证据来源：`website-evidence/routifyapi/assets/app-BTq1Mpg7.css`（实测 CSS 变量）。

| RoutifyApi 事实 | 决策 | 理由 |
|---|---|---|
| `--ui-brand:#0072f5`（蓝功能色） | **不当主色**；保留为 dark 链接参考 | 蓝是品类反射色 |
| `--ui-bg:#FFFDF7`（暖奶油纸底） | **刻意不抄** | editorial 暖纸感不符数据可信工具 |
| `--ui-text:#080808`（近黑非纯黑） | **采纳思路**（tinted near-black `#161C1E`） | 合 impeccable 禁纯黑 |
| `--ui-danger:#ec5e41`（暖红） | **采纳思路**（暖危险色 `#D14A33`） | 暖红克制可信 |
| JetBrains Mono 等宽代码 | **采纳** | 开发者熟悉，key/数值/endpoint 用 mono |
| Noto Sans SC + Gilroy | **部分采纳**：Inter+Noto Sans SC，display 用 Inter 700 | 需中文兜底，display 不引专有字 |
| dark `--ui-brand:#7aa2f7` | **采纳为 dark 链接色** | 暗底蓝链接合理 |
| 品牌名 / logo / footer「基于 New API」/ 具体文案 | **🔴红线：不复制** | PROJECT.md 红线，仅借语言不借资产 |

**红线清单（不可越）**：不复制受保护品牌/logo/文案归属；不把 New API 原 UI 当最终视觉方向；只借视觉「语言」，自定品牌名/标识/文案。

## 3. 各端布局决策骨架（详见 05_ui/final/SURFACE-MAP.md）

| 端 | 外壳 | 导航模型 | 信息密度 | 对应 PAGE-STATE-MATRIX 分区 |
|---|---|---|---|---|
| web-public（公开站） | 顶栏 + 长滚动落地 + 页脚 | 顶部水平导航 + 锚点 | 低（营销留白节奏 48/64/96） | A（+ I 公开价格/排行、N Playground 入口） |
| console（用户控制台） | 左侧栏 + 顶栏 + 内容区 | 左侧分组导航（持久/可折） | 中–高（紧凑 8/12/16/24） | C/D/E/F/H/M/N（+A 入口、I 用户视角） |
| admin（管理后台） | 左侧栏 + 顶栏 + 内容区（同 console 外壳，密度更高） | 左侧分组导航（管理域） | 高（表格密集 + 过滤面板） | B(管理) /C(配置) /E(全量日志/审计) /F(全量看板) /G(渠道) /H(计费配置) /I(模型元数据/同步) /K(部署) /L(运维) /M(分组) |
| api-docs（API 文档，按需轻量端） | 三栏：左目录 + 中正文 + 右代码示例 | 左侧目录树 + 右侧 anchor | 中（文档密度，mono 代码块） | J(Relay 外部 API 契约) /D(令牌外部 API) /E(按 key 日志外部 API) |

> 共享：console 与 admin **同一外壳同一 token**，admin 仅提密度、降动效、加批量过滤，不另起色系。

## 4. 组件 & 图表约定

- **按钮**：primary/secondary/ghost/danger/link 五变体 × default/hover/active/focus/disabled/loading 六态，色值已在 DESIGN-SYSTEM §6 冻结。primary 仅一处主操作/区。
- **表单**：input/textarea/select/checkbox/radio/switch/date，每态色值冻结（§7）。label 顶置、必填 `*` 红、error inline 不阻塞。
- **表格**：斑马用 `--bg-subtle`，行 hover `--primary-50`，表头 `--text-h6`，分页/排序/空态/批量(indeterminate)规范在端文档落。
- **选中表达禁左彩条**（impeccable ban）：用 check 图标 / 背景 tint / 序号，不用 `border-left` 彩条。
- **图表（硬约定）**：dashboard/概览/用量/排行/按日配额页**必有真图表**（趋势线 + 分布环 + 排名条三类按需），**数字卡仅作图表补充 KPI**，不替代图表。图表库冻结 = **ECharts 5 CDN**（迷你 sparkline 可 inline SVG）。每类图必带空/加载/异常三态（§8）。离散配色 `--chart-1..8` 色盲可分。
- **状态可视**：成功/失败/警示/info 各 base+bg+fg；状态点用 `--radius-full` 小圆 + 文字双编码。

## 5. 无障碍决策

- 对比度：正文 ≥4.5:1（§1 已逐色标注），图形/大字 ≥3:1；图表不靠纯色相，配明度阶 + 图例文字。
- 焦点态：统一 `--focus-ring`（主色 35% 3px 外环），禁 `outline:none` 无替代。
- 键盘：全流程 Tab 可达；模态/抽屉聚焦陷阱 + Esc；下拉方向键。
- 触控：移动端 ≥44×44px。
- 双编码：状态不靠颜色单独传达（图标+文字）；图标按钮带 `aria-label`；表单 `aria-invalid`/`aria-describedby`。
- 动效：`prefers-reduced-motion` 全降即时；关键信息不依赖动画可见。

## 6. 冻结决策表

| 日期 | 决策 | 理由 | 影响 |
|---|---|---|---|
| 2026-06-19 | 主色取 deep teal `#0E7C86`（非蓝） | 规避网关品类一阶 AI-slop 反射 + 路由语义 | 全端 CTA/链接/选中/图表序列1基色 |
| 2026-06-19 | 默认 light + 完整 dark 映射，可切持久化 | 物理场景句逼出（白天接 API / 深夜排查） | 双主题 token，对齐 P-5 主题切换 |
| 2026-06-19 | 配色策略 = Restrained | 开发者工具可信感来自克制与可读 | 主色 ≤10% 用量，中性为主体 |
| 2026-06-19 | 图表库 = ECharts 5 CDN（inline SVG 兜底迷你图） | 统一防 S6 换库视觉漂移 | dashboard/排行/用量页强制真图表 |
| 2026-06-19 | 不抄 RoutifyApi 暖奶油纸底，改近白冷微暖 | editorial 纸感不符数据工具气质 | bg 系列 token 重定义 |
| 2026-06-19 | console 与 admin 共享外壳/token，admin 仅调密度旋钮 | 防后台另起炉灶（multi-surface Pitfall 11） | 两端不另立色系 |
| 2026-06-19 | 字体 Inter + Noto Sans SC + JetBrains Mono | 中文兜底 + 拉丁清晰 + 开发者等宽代码 | 全端字栈统一 |
| 2026-06-19 | 设计系统层 Nielsen 自评 33/40（Good），冻结放行 | ≥32 门槛达标 | 缺口入开放缺口表，端/S6 补 |

## 7. 开放设计缺口

| ID | 缺口 | 阻塞阶段 | 当前处理 |
|---|---|---|---|
| DG-1 | 各端具体页文案 / 错误文案 / 空态文案未定 | S5 第二步（端文档） | 设计系统给规范位，端文档逐页填，影响 Nielsen #2/#9 评分 |
| DG-2 | 快捷键 / power-user 加速体系未定 | S5 第二步 | 控制台/后台端文档定义批量与快捷键，影响 #7 |
| DG-3 | undo / 草稿恢复的全局策略未定 | S5 第二步 | 端文档结合 PRD 二次确认/草稿保留场景（如 PUB-2、签到、令牌）补，影响 #3/#5 |
| DG-4 | 设计参考图 / 配图资产未生成 | S5 第二步 | 第二步按 image-generation→免版权→本地 SVG 降级链生成到 assets/ |
| DG-5 | PAGE-INVENTORY / STATE-MATRIX / COMPONENT-SPECS / RESPONSIVE-SPECS / CHART-SPECS 五份 S6 执行文件未出 | S5 第二步（S6 可执行性门） | 第二步强制产出，本步只冻结全局 token + 端盘点 |
| DG-6 | 上下文帮助 / API 文档信息架构细节未定 | S5 第二步 | api-docs 端文档定义目录树 + 内联帮助，影响 #10 |

## 8. 阶段交接备注（S5→S6）

- **S5 第一步已冻结给 S6**：完整 token 体系（色/字/间距/圆角/阴影/焦点环/动效）、按钮全态、表单全态、图表三类规范 + 库选型(ECharts 5)、响应式断点、无障碍基线、4 个交付端盘点。
- **S6 必须镜像 S5**：同 palette / 同字阶 / 同间距 / 同组件状态 / 同图表库。任何偏差须回此表登记 + 理由，不得静默漂移。
- **S5 第二步待补**（同 §7 缺口）：四端逐页 UI 文档 + 五份 S6 执行文件 + 参考图资产 + 各端关键页 Nielsen 评分 + 甲方规范映射（本项目无甲方规范文档，以 PROJECT.md 意图 + RoutifyApi 证据为依据，已记录）。
