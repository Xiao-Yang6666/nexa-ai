# Nexa·AI — 管理台（admin 端）详细 UI 设计

> 端标识：`admin`（管理区视角）。本端**不另起炉灶**：严格引用冻结的 `../DESIGN-SYSTEM.md` + `../assets/tokens.css`，只写管理区特异化的部分；所有色值/字号/间距/阴影/动效一律用 `var(--token)`，禁裸色值、禁 emoji、禁 `#000/#fff` 裸用。
> 重要前提：**管理台与控制台合并成一套 SaaS 工作台**——超管 = 用户区全部能力 + 管理区能力叠加。本文档写【管理区视角】，导航 = 用户区菜单 + 管理区菜单叠加（见 §2）。本端只覆盖**管理区**的页面与组件；与用户区重叠的 token/组件规范引用 console 端，不重复发明。
> 上游依据：`FUNCTION-LIST.csv`（筛 admin/root 功能）、`REQUIREMENT-MINDMAP.md`、`PAGE-STATE-MATRIX.md`（§A~N，权威状态来源）、`DATA-MODEL.md`。
> 设计读 / 三旋钮（来自 `../DESIGN-BRIEF.md §1.3`）：**面向运营/管理员的全局管控台**，气质=权威、严谨、强状态可恢复，倾向工业/brutalist 数据仪表盘 + impeccable product register。

---

## 1. 端概述与设计读

### 1.1 本端是什么 · 受众 · 设备
- **是什么**：Nexa·AI（AI API 网关 SaaS）的**全局管控台**。管理员/超管在此管理渠道、上游路由、用户、计费规则、兑换码、模型/供应商元数据、预填分组、异步任务监控、日志审计、io.net 部署、系统设置与运维。
- **受众**：`admin`（运营 + 运维细分权限组）与 `root`（超管，全量配置 + 合规确认）。角色优先级 `root > admin > common`（F-1012 / F-5031）。
- **设备/视口**：桌面优先（运营在大屏多列表多筛选作业）。`>1024px` 固定侧栏工作台；`640–1024px` 折叠侧栏；`<640px` 抽屉导航 + 表格降级为卡片列表（应急查看，非主作业场景）。

### 1.2 三旋钮（端级，引用 DESIGN-BRIEF §1.3）
| 旋钮 | 值 | 落地表现 |
|---|---|---|
| DESIGN_VARIANCE | **systematic-restrained** | 与 console 同源 DESIGN-SYSTEM，密度更高、强调强状态与批量操作；视觉零花哨 |
| MOTION_INTENSITY | **low（趋近 none）** | 仅指标 Count Up；表格/操作区**零装饰动效**（无 hover 位移、无 cursor 特效、无全屏背景动效） |
| VISUAL_DENSITY | **high（最密）** | 信息优先：紧凑间距档（表格行内边距走 `--space-2`）、16px 紧凑栅格、可一屏容纳尽量多行 |

### 1.3 整体调性一句话
> 深色重数据后台：信息密度最高、强状态（自动禁用/超时退款/合规闸门/越权拦截）一眼可辨且可恢复，操作可批量、可审计、可二次确认；视觉安静到「让数据自己说话」，绝不与内容争注意力。

### 1.4 配色场景说明（关键）
管理台**默认走深色应用底**（`:root[data-scheme="dark"]`，见 tokens.css L98–106），与门面页深色 hero 不同源——前者是**工作台暗色底**（`--color-bg #0E1416`），后者是品牌深色场景（`--hd-bg #06100F`）。两者正交于 `data-theme` 换肤开关。管理台亦支持切浅色（去掉 `data-scheme`），但默认暗色以降低长时间作业视疲劳。本文档色值在**暗色底**语境下给出，token 自动随 `data-scheme` 解析（如 `--color-primary-500` 暗色下解析为 `#2BB7C2`）。

---

## 2. 信息架构与导航

### 2.1 合并工作台导航模型（用户区 + 管理区叠加）
单一应用外壳（app shell）+ 固定左侧栏 + 顶栏。侧栏菜单按角色门控可见：`common` 只见用户区分组；`admin` 叠加管理区分组；`root` 再叠加超管专属项（系统设置/合规/运维/自定义 OAuth）。**菜单项可见性由角色裁定**（F-5030 RBAC），不可见项不渲染（非禁用置灰）。

```
┌─ 顶栏 TopBar ───────────────────────────────────────────────────────────┐
│ [≡] Nexa·AI logo   ……(面包屑)……      [主题切换] [scheme切换] [告警铃] [管理员头像▾] │
├──────────────┬──────────────────────────────────────────────────────────┤
│ 侧栏 SideNav  │  主内容区 Main                                              │
│              │                                                            │
│ ◇ 用户区(继承) │   ┌── 页头 PageHeader：标题 + 描述 + 右侧主操作/批量栏 ──┐    │
│  · 概览       │   │                                                  │    │
│  · Playground │   ├── 筛选区 FilterBar（多维过滤 + 搜索 + 时间区间） ──┤    │
│  · 令牌       │   │                                                  │    │
│  · 用量日志    │   ├── 数据区：表格 / 看板图表 / 表单 / 详情抽屉 ──────┤    │
│  · 充值/订阅   │   │                                                  │    │
│  · 模型广场    │   └── 分页器 Pagination ───────────────────────────┘    │
│  · 个人中心    │                                                            │
│ ──────────── │                                                            │
│ ◆ 管理区(admin)│                                                            │
│  · 全局概览★   │  ★ = 看板/概览页（真图表，见 §9）                          │
│  · 渠道管理    │                                                            │
│  · 用户管理    │                                                            │
│  · 任务监控★   │                                                            │
│  · 计费规则    │                                                            │
│  · 兑换码      │                                                            │
│  · 模型/供应商  │                                                            │
│  · 预填分组    │                                                            │
│  · 日志审计    │                                                            │
│  · 部署(io.net)│                                                            │
│ ──────────── │                                                            │
│ ▲ 超管区(root) │                                                            │
│  · 系统设置    │                                                            │
│  · 运维监控★   │                                                            │
│  · 合规/法务    │                                                            │
│  · 权限矩阵    │                                                            │
└──────────────┴──────────────────────────────────────────────────────────┘
```

### 2.2 管理区页面层级（IA 树，标 FID）
```
管理区 admin
├─ 全局概览★        汇总：今日请求量/费用/活跃渠道/用户数/任务量 + 趋势/分布/排名 (F-4007/F-4004/F-5003)
├─ 渠道管理
│  ├─ 渠道列表/搜索/批量      (F-2016/F-2019)  列表密集页
│  ├─ 渠道创建/编辑（多 Key/映射/覆写/亲和）(F-2016/F-2020/F-2021/F-2022/F-2025)
│  ├─ 连通性测试 + 余额更新     (F-2017/F-2018)
│  ├─ 自动禁用/恢复状态        (F-2023/F-2024)
│  ├─ 上游模型探测同步         (F-2026/F-2027)
│  ├─ Codex 用量查询          (F-4045)
│  └─ 亲和缓存策略/清缓存/用量   (F-2031/F-2032/F-2033/F-4014)
├─ 用户管理
│  ├─ 用户列表/搜索           (F-1008)
│  ├─ 创建/编辑/状态管理/分组/备注 (F-1009/F-1010/F-1011/F-1013/F-1014)
│  ├─ OAuth 绑定管理          (F-1027)
│  └─ 重置 Passkey / 2FA 统计与禁用 (F-1032/F-1037)
├─ 任务监控★       全量任务看板 + 超时扫描 (F-2004/F-2011)
├─ 计费规则
│  ├─ 倍率配置/同步（model/group/cache/补全）(F-2043/F-2038/F-2039)
│  ├─ 表达式/阶梯计费配置       (F-2040)
│  └─ 价格暴露（expose_ratio）  (F-2048)
├─ 兑换码         单个/批量生成/过期 (F-2045)
├─ 模型/供应商
│  ├─ 模型元数据列表/搜索/CRUD  (F-3013/F-3014/F-3015/F-3016/F-3017)
│  ├─ 供应商元数据 CRUD        (F-3018)
│  ├─ 上游模型同步（预览/执行）  (F-3019/F-3020)
│  └─ 缺失模型检测            (F-3021)
├─ 预填分组       model/tag/endpoint 三类 CRUD/软删 (F-2012~F-2015)
├─ 日志审计
│  ├─ 全量日志查询（八维过滤）   (F-4001)
│  ├─ 日志统计（quota/rpm/tpm） (F-4004)
│  ├─ 按日配额看板★            (F-4007/F-4008)
│  ├─ 审计日志（管理/安全/登录）  (F-4011/F-4012/F-4013)
│  └─ 历史日志清理            (F-4006)
├─ 部署 io.net    集成开关/连接测试/部署生命周期/容器日志 (F-3039~F-3056)
└─ 超管区 root
   ├─ 系统初始化向导          (F-4015/F-4016)
   ├─ 全站选项读写            (F-4017/F-4018/F-4032/F-4033/F-4034/F-4035)
   ├─ 运维监控★（性能/磁盘/GC/日志文件）(F-4019~F-4023)
   ├─ 合规确认 + 控制台迁移     (F-4030/F-4031)
   └─ 权限矩阵查看/审计         (F-5034/F-5030)
```

### 2.3 关键路径（引用流程 / 状态矩阵）
- **渠道故障处置**：全局概览 → 健康度看板发现高错误率渠道 → 渠道列表过滤「自动禁用」→ 进详情看错误日志 → 测试连通性 → 修复后启用（PAGE-STATE-MATRIX §G CH-3）。
- **用户越权封禁**：用户管理 → 搜索定位 → 管理动作（禁用/提升/删除）→ 角色优先级闸门校验 → 列表刷新（§B AC-10）。
- **倍率调整（合规闸门）**：计费规则 → 改 group_ratio/邀请额度正值 → 命中「需先确认支付合规」闸门 → 跳合规确认（仅会话鉴权）→ 回填保存 → 写 option.update 审计（§L OP-2/OP-5）。
- **超时任务退款核查**：任务监控 → 过滤超时/FAILURE → 看 CAS 退款态 → 关联日志审计（§F AT-3）。

---

## 3. 调色板（端级 · 继承 DESIGN-SYSTEM，列实际使用子集）

> 管理台默认 `data-scheme="dark"`。下表给出本端实际用到的 token 子集与用途；hex/OKLCH 双写均取自 `../DESIGN-SYSTEM.md §2`，**不重新发明**。括号内为暗色底下 token 的解析值（tokens.css L98–106 覆盖）。

### 3.1 应用底与表面（暗色工作台）
| token | 暗色解析 hex | OKLCH（近似） | 用途 |
|---|---|---|---|
| `--color-bg` | `#0E1416` | `oklch(0.205 0.008 207)` | 工作台主底 |
| `--color-bg-subtle` | `#151D20` | `oklch(0.252 0.009 210)` | 斑马行/次底/筛选区底 |
| `--color-bg-elevated` | `#1B2528` | `oklch(0.296 0.010 209)` | 卡片/表头/抽屉/模态面 |
| `--color-surface-sunken` | `#0A1012` | `oklch(0.166 0.007 215)` | 行 hover / 凹陷 |
| `--color-border` | `#2A3539` | `oklch(0.346 0.011 213)` | 常规边框/行分隔 |
| `--color-border-strong` | `#3A474C` | `oklch(0.420 0.012 213)` | 表头下边框/强分隔 |

### 3.2 文字（暗色）
| token | 暗色解析 hex | 用途 |
|---|---|---|
| `--color-text` | `#E8EEEF` | 主文字/表格单元主值 |
| `--color-text-secondary` | `#B8C2C5` | 次级正文/label |
| `--color-text-muted` | `#8A989C` | 次要/占位/列头辅助/分页信息 |
| `--color-disabled` | `#5A6669` | 禁用文字 |

### 3.3 主色族（暗色下提亮，换肤随 data-theme）
| token | 暗色解析 hex | 用途 |
|---|---|---|
| `--color-primary-500` | `#2BB7C2` | 主按钮/链接/选中/图表 chart-1/开关 checked |
| `--color-primary-600` | `#46C5CF` | hover |
| `--color-primary-700` | `#62D2DA` | active |
| `--color-primary-fg` | `#06181A` | 主色上前景文字（暗色下为深字） |
| `--focus-ring` | `0 0 0 3px color-mix(in oklch, var(--color-primary-500) 35%, transparent)` | 所有可聚焦元素 |

### 3.4 语义色（跨主题恒定 · 管理台强状态主力）
| 角色 | base | bg | fg | border | 管理台用途 |
|---|---|---|---|---|---|
| success `--color-success` | `#1F8F5F` | `#E7F5EE` | `#0C3D29` | `#A9DCC4` | 渠道在线/已启用/任务 SUCCESS/已支付/兑换有效 |
| warning `--color-warning` | `#B5740A` | `#FBF1DF` | `#5A3A04` | `#EBCF95` | 限额接近/排队 QUEUED/手动禁用/合规未确认提醒 |
| danger `--color-danger` | `#D14A33` | `#FCEDE9` | `#5E1B0E` | `#F0BCAE` | 自动禁用/任务 FAILURE/越权拦截/删除/超限/超时 |
| info `--color-info` | `#1E6FD9`（暗 `#7AA2F7`） | `#E8F0FC` | `#0E2F5E` | `#AECCF2` | 信息提示/IN_PROGRESS/可重试/预览态 |

> 语义色在暗色底上用法：徽章统一用 `.badge .b-suc/.b-warn/.b-dan/.b-info`（tokens.css L165–169，bg/fg 自带）。强状态行可附 `.dot` 状态点（7px 圆，取语义 base 色）。

### 3.5 图表离散序列（用 chart-1…8，色盲可分）
`--chart-1`（主题主色，暗色 `#2BB7C2`）/ `--chart-2 #1E6FD9` / `--chart-3 #B5740A` / `--chart-4 #7A4FB5` / `--chart-5 #1F8F5F` / `--chart-6 #C0497F` / `--chart-7 #3D9AA0` / `--chart-8 #6B7780`。辅助：`--chart-grid`（暗色 12% text）、`--chart-axis`（=text-muted）、趋势面积渐变 `--chart-area-stop`→`--chart-area-stop0`。

---

## 4. 字体字阶（端级 · 裁出后台实际用到的层级）

> 字体栈引用 DESIGN-SYSTEM §3：正文 `--font-sans`（Inter/Noto Sans SC），指标/ID/key/数值一律 `--font-mono`（JetBrains Mono），管理台**不出现 `--font-brand` 巨字与 `--text-mega`**（无品牌门面）。

| 层级 | token | 字号 | 字重 | 行高 | 管理台用途 |
|---|---|---|---|---|---|
| h2 | `--text-h2` | 1.875rem | 600 | snug 1.3 | 页面主标题（如「渠道管理」）少用，多数页用 h3 |
| h3 | `--text-h3` | 1.5rem | 600 | snug 1.3 | 页头标题 / 看板区段标题 |
| h4 | `--text-h4` | 1.25rem | 600 | snug 1.3 | 卡片/抽屉标题 / 表单分组标题 |
| h5 | `--text-h5` | 1.0625rem | 600 | normal | 小标题 / 侧栏 logo / 模态标题 |
| h6 | `--text-h6` | .9375rem | 600 | normal | 表单字段组标题 / 列表组标题 |
| body | `--text-body` | .9375rem | 400 | normal 1.55 | 默认正文 / 表单输入值 |
| body-sm | `--text-body-sm` | .8125rem | 400 | normal | **表格主力字号** / 辅助说明 / 徽章 |
| caption | `--text-caption` | .75rem | 400 | normal | hint / error 文案 / 分页信息 / 时间戳 |
| overline | `--text-overline` | .6875rem | 600 | normal（wide .08em，大写） | 筛选区分组头 / 看板卡片标签 |
| code | `--text-code` | .844rem | 400 | normal | 渠道 key 打码 / 模型名 / request_id / IP / JSON 配置 |

数值（指标卡数字、quota、token 数、耗时、余额、占比）一律 `--font-mono` + 右对齐于表格数值列；相邻字阶比 ≥1.2（达标，引用 DESIGN-SYSTEM §3）。

---

## 5. 间距与栅格（端级 · 最高密度档）

- **间距阶**引用 DESIGN-SYSTEM §4：`--space-1 4 / -2 8 / -3 12 / -4 16 / -5 24 / -6 32 / -8 48`。
- **管理台密度规则**：
  - 侧栏宽 240px（折叠 64px 仅图标）；顶栏高 56px。
  - 主内容区左右内边距 `--space-6`（32px，桌面）；区块纵向间距 `--space-5`（24px）。
  - **表格紧凑档**：单元格内边距 `--space-2 var(--space-3)`（8px×12px，tokens.css L176–177 已定）；行高约 40px。
  - 筛选区控件间距 `--space-3`（12px）；卡片内边距 `--space-5`（24px，`.card` 默认）。
- **栅格**：管理区表格区为单列全宽流式（`--container-max` 不强约束，表格随视口铺满）；看板/详情用 12 列栅格，gutter 取紧凑 16px（非门面 24px）。指标卡区桌面 4–6 列等分，平板 2 列，移动 1 列。

---

## 6. 阴影 / 圆角应用（端级表面映射）

> 引用 DESIGN-SYSTEM §5/§6，指定本端各表面用哪一级。

| 表面 | 阴影 | 圆角 | 说明 |
|---|---|---|---|
| 卡片 `.card` / 指标卡 / 看板卡 | `--shadow-sm` | `--radius-md` (10px) | 抬升基线 |
| 表格容器 | `--shadow-xs`（极轻）或无 + `--color-border` | `--radius-md` | 密集后台减弱阴影，靠边框分面 |
| 下拉 / popover / 筛选浮层 | `--shadow-md` | `--radius-md` | 弹层级 |
| 模态 Modal / 抽屉 Drawer | `--shadow-lg` | 模态 `--radius-lg`(16px) / 抽屉左缘直角右侧 `--radius-lg` | 最高层 |
| 输入 `.input` / 小按钮 | `--shadow-xs` | `--radius-sm` (6px) | |
| 徽章 `.badge` / 状态点 / 开关 | 无 | `--radius-full` | |
| 标签 / chip（如多 Key 列表项、模型多选 tag） | 无 | `--radius-xs` (4px) | |
| focus（所有可聚焦） | `--focus-ring` | 跟随元素 | 键盘可达硬要求 |

> 管理台为降视疲劳与突出数据，**整体压低阴影强度**：表格/列表区主要靠 `--color-border` 与斑马行分面，仅卡片/弹层/模态用真阴影梯级。

---

## 7. 按钮系统（变体 × 状态，每态明确色值）

> 引用 tokens.css `.btn-*`（L117–138）。色值随 `data-scheme=dark` 解析。高度：sm 32 / md 40 / lg 48；圆角 `--radius-sm`；过渡 `transform --dur-1 --ease-snap` + 色/影 `--dur-2`。管理台**禁 glow/glass 门面按钮**（仅门面页用）。

### 7.1 primary（主操作：保存渠道/创建用户/确认同步）
| 状态 | 背景 | 文字 | 边框 | 阴影 | 说明 |
|---|---|---|---|---|---|
| default | `--color-primary-500`(#2BB7C2) | `--color-primary-fg`(#06181A) | 无 | `--shadow-xs` | |
| hover | `--color-primary-600`(#46C5CF) | `-fg` | 无 | `--shadow-xs` | |
| active | `--color-primary-700`(#62D2DA) | `-fg` | 无 | inset 略压 | |
| focus | default 底 | `-fg` | 无 | `--focus-ring` | focus-visible |
| disabled | `--color-disabled-bg`(#1E282B) | `--color-disabled`(#5A6669) | `--color-border` | none | `cursor:not-allowed` |
| loading | default 底 + 内联 spinner（`--font` 无关，stroke=`--color-primary-fg`） | `-fg` | 无 | `--shadow-xs` | **禁点**，文案旁转圈 |

### 7.2 secondary（次操作：取消/测试连通性/更新余额/导出）
| 状态 | 背景 | 文字 | 边框 | 阴影 |
|---|---|---|---|---|
| default | `--color-bg-elevated`(#1B2528) | `--color-text`(#E8EEEF) | `--color-border`(#2A3539) | `--shadow-xs` |
| hover | `--color-bg-subtle`(#151D20) | `-text` | `--color-border-strong`(#3A474C) | `--shadow-xs` |
| active | `--color-surface-sunken`(#0A1012) | `-text` | `-strong` | inset |
| focus | default 底 | `-text` | `-border` | `--focus-ring` |
| disabled | `--color-disabled-bg` | `--color-disabled` | `--color-border` | none |
| loading | default 底 + spinner（stroke=`--color-text`） | `-text` | `-border` | 禁点 |

### 7.3 ghost（行内/工具栏弱操作：编辑/查看/筛选清除/列设置）
| 状态 | 背景 | 文字 | 边框 | 阴影 |
|---|---|---|---|---|
| default | 透明 | `--color-text-secondary`(#B8C2C5) | 无 | 无 |
| hover | `--color-bg-subtle` | `--color-text` | 无 | 无 |
| active | `--color-surface-sunken` | `-text` | 无 | 无 |
| focus | 透明 | `-secondary` | 无 | `--focus-ring` |
| disabled | 透明 | `--color-disabled` | 无 | none |
| loading | 透明 + spinner（stroke=`-secondary`） | `-secondary` | 无 | 禁点 |

### 7.4 danger（高危：删除渠道/用户/兑换码/禁用/清空缓存/清理日志）
| 状态 | 背景 | 文字 | 边框 | 阴影 |
|---|---|---|---|---|
| default | `--color-danger`(#D14A33) | `#FFF7F5` | 无 | `--shadow-xs` |
| hover | `color-mix(in oklch, var(--color-danger) 88%, var(--color-text))`（加深） | `#FFF7F5` | 无 | `--shadow-xs` |
| active | 更深（再混 12% text） | `#FFF7F5` | 无 | inset |
| focus | default 底 | `#FFF7F5` | 无 | `0 0 0 3px color-mix(in oklch, var(--color-danger) 35%, transparent)`（danger focus-ring） |
| disabled | `--color-disabled-bg` | `--color-disabled` | `--color-border` | none |
| loading | default 底 + spinner（stroke=`#FFF7F5`） | `#FFF7F5` | 无 | 禁点 |

> **高危按钮强制配二次确认**：删除/禁用/清空/清理类 danger 操作点击后弹确认模态（见 §10.6），破坏性大的（删用户/清全部亲和缓存/清理历史日志）要求输入对象名或勾选确认框才激活确认按钮。改倍率/取 key/重置 2FA 等接入 SecureVerification 二次验证（F-5033）。

### 7.5 link（行内跳转：进详情/查看日志/关联渠道）
| 状态 | 背景 | 文字 | 下划线 |
|---|---|---|---|
| default | 透明 | `--color-primary-500` | 无 |
| hover | 透明 | `--color-primary-600` | 下划线 |
| active | 透明 | `--color-primary-700` | 下划线 |
| focus | 透明 | `-500` | `--focus-ring` |
| disabled | 透明 | `--color-disabled` | 无 |

### 7.6 尺寸规格
| 尺寸 | 高 | 内边距 | 字号 | 圆角 | 图标间距 | 管理台用途 |
|---|---|---|---|---|---|---|
| sm `.btn-sm` | 32px | `0 var(--space-3)` | `--text-body-sm` | `--radius-sm` | gap `--space-2` | 表格行内操作 / 工具栏 / 筛选 |
| md `.btn`（默认） | 40px | `0 var(--space-4)` | `--text-body` | `--radius-sm` | gap `--space-2` | 页头主操作 / 表单提交 |
| lg `.btn-lg` | 48px | `0 var(--space-5)` | `--text-body-lg` | `--radius-sm` | gap `--space-2` | 少用（初始化向导/合规确认大按钮） |

图标按钮（仅图标，如行尾「⋯」更多）：32×32 方形，ghost 底，内联线性 SVG（stroke + currentColor，禁 emoji）。

---

## 8. 表单元素（每元素 default/focus/filled/error/disabled）

> 引用 tokens.css `.input/.field-*/.switch`（L144–162）。管理台表单密集（渠道编辑、用户编辑、倍率配置、系统设置），label 顶置。

### 8.1 文本输入 / 文本域（input/textarea）
| 状态 | 底 | 边框 | 文字 | 说明 |
|---|---|---|---|---|
| default | `--color-bg-elevated` | `--color-border` | `--color-text` | 高 40；textarea 多行 min-height 96 |
| focus | `-elevated` | `--color-primary-500` | `-text` | + `--focus-ring` |
| filled | `-elevated` | `--color-border` | `-text` | 同 default，值用 `--font-sans`；key/JSON 用 `--font-mono` |
| error | `--color-danger-bg` | `--color-danger` | `-text` | focus 时 `0 0 0 3px color-mix(--color-danger 28%)`；下方 `.field-err` |
| disabled | `--color-disabled-bg` | `--color-border` | `--color-disabled` | `cursor:not-allowed` |
- 占位符 `--color-text-muted`；label `.field-label`（body-sm semibold secondary）；必填 `.field-req`(danger 星号)。
- hint `.field-hint`（caption muted）；error `.field-err`（caption danger-fg + 前置线性 SVG 警示图标）。

### 8.2 下拉选择 Select（渠道类型/分组/供应商/计费类型/角色）
- 触发器同 input 样式；展开浮层 `--shadow-md` + `--radius-md` + `-elevated` 底；选项 hover `--color-surface-sunken`，选中项左侧主色竖条 + 文字 `--color-primary-500`。
- 可搜索下拉（模型多选、分组）：顶部内嵌搜索框，下方多选列表带 checkbox；选中项以 chip 显示在触发器内（`--radius-xs`，可单删）。
- error/disabled 同 §8.1。

### 8.3 多选 / 标签输入（渠道 Models、令牌模型白名单、IP 白名单）
- chip 列表：每 chip `--radius-xs`，底 `--color-bg-subtle`，文字 `--color-text-secondary`，右侧线性 × 删除（hover 转 danger）。
- 支持**预填分组一键填充**（F-2014）：下拉选 model/tag/endpoint 类型分组 → 批量注入 chip。
- IP 白名单：多行 textarea，按换行解析（F-3010）；hint 提示「每行一个 IP，留空=不限制」。

### 8.4 单选 Radio / 复选 Checkbox
- radio/checkbox：18×18，未选边框 `--color-border-strong`，选中底 `--color-primary-500` + `-fg` 勾/点；focus `--focus-ring`；disabled `--color-disabled-bg`。
- 用于：通知方式单选（email/webhook/bark, F-4037）、MultiKeyMode 随机/轮询单选、status_only 切换等。

### 8.5 开关 Switch（启用/禁用/AutoBan/各功能开关）
- 引用 `.switch`：track 默认 `--color-border-strong` → checked `--color-primary-500`；thumb 18px `--ease-snap` 滑动；focus-ring。
- 标准用法：渠道启用、AutoBan、签到 Enabled、RegisterEnabled、各 OAuth provider enabled、prompt 留存开关、亲和缓存 Enabled/SwitchOnSuccess。
- 危险开关（如全局自动禁用、合规相关）切换前弹确认。

### 8.6 数值 / 区间输入（倍率、Quota、Priority、Weight、阈值、TTL、限流 count/duration）
- 数值输入右对齐 + `--font-mono`；带步进器（ghost 上下箭头，sm）。
- 区间 `[count, duration]`、`[MinQuota, MaxQuota]`：并排两输入 + 中间分隔符；校验 Min≤Max（违例 error 态，文案「最小值不能大于最大值」）。
- 倍率输入支持小数；表达式计费用代码区（`--font-mono` textarea + 语法高亮位）。

### 8.7 日期 / 时间区间选择（日志/任务/配额过滤）
- 触发器同 input + 前置日历线性 SVG；弹层日历 `--shadow-md`，今日/选中用主色，范围底 `--color-primary-500` 18% 透明。
- 快捷区间：今日/近7天/近30天 chip（自助配额跨度上限 1 月，超限 error，F-4009）。

### 8.8 校验提示位置约定
- 行内字段错误：字段下方 `.field-err`。
- 表单级错误（如保存失败/JSON 解析失败）：表单顶部 info/danger 内联条（见 §10.4 toast/alert）。
- 异步校验（集群名可用性 F-3046、模型名重名 F-3015）：失焦实时校验，输入框右侧加载→成功(success 勾)/失败(danger 文案)。

---

## 9. 图表规范（看板/概览/数据页硬门：真图表，非数字卡）

> 图表库冻结 **ECharts 5（CDN）**（DESIGN-SYSTEM §7.3）；离散色 `--chart-1…8`；数值 tooltip 一律 `--font-mono`。管理台看板页：**全局概览**、**任务监控**、**按日配额看板**、**运维监控**、**日志统计**、**渠道健康度**。每类图必含空态/加载态/异常态。

### 9.1 趋势类（折线 + 面积）——请求量/费用/任务量按时间
| 项 | 规范 |
|---|---|
| 线色 | 主序列 `--chart-1`；多序列按 `--chart-1…8` 顺序 |
| 面积 | `--chart-area-stop`(chart-1 18%) → `--chart-area-stop0`(0%) 纵向渐变 |
| 网格/轴 | grid `--chart-grid`（暗色 12% text）；axis 文字 `--chart-axis`(text-muted) |
| tooltip | `--color-bg-elevated` 底 + `--shadow-md` + `--radius-md`；数值 `--font-mono` |
| 空态 | 居中线性 SVG 折线占位图标 + 「所选区间内暂无数据」 |
| 加载态 | 图区骨架条 `.skeleton`（暗色 shimmer，reduced-motion 减速 2.6s） |
| 异常态 | 红描边图区 + 「加载失败，重试」+ 重试 ghost 按钮（查询出错/超时，§E UL-2） |
- 用途：全局概览近 24h/7d 请求量与费用双轴折线；按日配额看板 quota 折线/堆叠（F-4007）。

### 9.2 分布类（环 / 饼）——模型占比/渠道占比/任务平台占比/状态计数
| 项 | 规范 |
|---|---|
| 分片色 | 按 `--chart-1…8` 顺序，超 8 类归「其他」灰 `--chart-8` |
| 图例 | 右侧竖排，每项色块 + 名称 + 占比（`--font-mono`） |
| 中心（环） | 显示总量（如总请求数/总费用），`--font-mono` 大字 + caption 标签 |
| 空态 | 灰环占位（`--color-border` 描边）+ 「暂无分布数据」 |
| 加载态 | 骨架圆 |
| 异常态 | 同趋势异常 |
- 用途：全局概览模型/渠道占比环；任务监控按 platform/status 分布（F-2004 status_counts）；部署 status_counts（F-3041）。

### 9.3 排名 / 对比类（横 / 纵柱）——Top 模型/Top 用户/Top 渠道
| 项 | 规范 |
|---|---|
| 柱色 | `--chart-1`；hover `--color-primary-600`；零基线明确 |
| 数值标签 | 柱尾 `--font-mono`；横向条形图利于长名称 |
| 排序 | 降序；超 10 条分页或滚动 |
| 空态 | 占位柱（虚线描边）+ 「暂无排行数据」 |
| 加载态 | 骨架柱 |
| 异常态 | 同上；排行非法 period 显「无效的统计周期」（F-4010） |
- 用途：全局概览 Top 模型/Top 用户消耗排行（F-4008 按用户聚合）；渠道错误率排行（健康度看板，F-5003）。

### 9.4 指标卡（KPI）——配合图表，非替代
看板顶部 KPI 卡区（4–6 张）：每卡 = overline 标签 + `--font-mono` 大数（Count Up 动效，唯一允许动效）+ 同比/环比小箭头（success 升 / danger 降，纯色无 emoji）。**KPI 卡必须与下方真图表并存**，不得只堆数字卡（§6.2 硬门）。
- 全局概览 KPI：今日请求量 / 今日费用(quota) / 活跃渠道数 / 在线用户数 / 进行中任务数 / 平均网关延迟(p50)（F-5001）。
- 运维监控 KPI：goroutine 数 / NumGC / 内存 Alloc / 磁盘 used_percent（F-4019，超阈值卡转 warning/danger 描边）。

### 9.5 健康度 / 状态特化可视化
- **渠道健康度看板**（F-5003）：表格 + 内嵌迷你趋势（sparkline，`--chart-1`）；错误率列用进度条（success<1% / warning 1–5% / danger>5%）；AutoBan 状态徽章。
- **Uptime 状态**（F-4026）：各监控组可用率横条（success 绿满 / danger 缺口）+ 心跳点阵（近 N 个心跳，绿=up 红=down）；未配置空数组态。

> 所有图表在 `data-scheme=dark` 下 grid 用加强 `--chart-grid`（12% text，tokens.css L105 已覆盖），保证暗底可读。

---

## 10. 核心组件规范（管理台主力：表格/批量/抽屉/确认/状态）

### 10.1 数据表格 Table（管理台第一公民）

> 引用 tokens.css 表格基线（L174–179）：表头 sticky + `-strong` 下边框；斑马行 `--color-bg-subtle`；行 hover `--color-surface-sunken`。管理台密集列表（渠道/用户/订单/任务/日志/兑换码/模型）统一规范如下。

**结构与样式**
| 部位 | 规范 |
|---|---|
| 容器 | `--color-bg-elevated` + `--color-border` 1px + `--radius-md`；横向溢出可滚（保表头/操作列固定） |
| 表头 th | sticky top；`--color-bg-elevated` 底；`--text-body-sm` semibold `--color-text-secondary`；下边框 `--color-border-strong` |
| 行 td | 内边距 `--space-2 var(--space-3)`（紧凑档）；`--text-body-sm`；下边框 `--color-border` |
| 斑马 | 偶数行 `--color-bg-subtle`（暗色 `#151D20`） |
| 行 hover | `--color-surface-sunken`（暗色 `#0A1012`）；**仅背景变化，无位移/无阴影**（low motion） |
| 数值列 | 右对齐 + `--font-mono`（quota/token/耗时/余额/占比/数量） |
| ID/key/IP/模型名列 | `--font-mono` + `--text-code`；过长省略号 + hover tooltip 全值 |
| 选择列 | 首列 checkbox（行选 + 表头全选/半选态） |
| 操作列 | 末列固定右侧；ghost sm 按钮组（查看/编辑/更多⋯）；高危操作放「更多」下拉避免误触 |

**列定义示例（各列表实际列）**
- **渠道列表**（F-2016）：☑ | 名称 | 类型(徽章) | 状态(徽章：启用/手动禁用/自动禁用) | 分组 | 模型数 | 优先级 | 权重 | 多Key(N) | 余额 | 测试耗时/时间 | 标签 tag | 操作。
- **用户列表**（F-1008）：☑ | ID | 用户名 | 角色(徽章 root/admin/common) | 状态(启用/封禁) | 分组 | 额度/已用 | 邀请数 | 注册时间 | 操作(编辑/管理/绑定/重置)。
- **任务监控**（F-2004）：☑ | TaskID | 平台(mj/suno/video 徽章) | 动作 | 状态(NOT_START/QUEUED/IN_PROGRESS/SUCCESS/FAILURE/UNKNOWN 徽章) | 进度(进度条) | 用户 | 渠道ID | 提交时间 | 完成时间 | 失败原因 | quota | 操作。
- **全量日志**（F-4001）：时间 | 类型(consume/manage/login/error 徽章) | 用户名 | 令牌名 | 模型 | 渠道 | 分组 | quota | token(prompt/completion) | request_id | 操作。
- **审计日志**（F-4011）：时间 | action | content(英文渲染) | 操作者(admin_username+role 徽章) | auth_method | client IP | 详情。
- **兑换码**（F-2045）：☑ | 码(打码) | 额度 | 状态(未用/已用/过期 徽章) | 创建/过期时间 | 使用者 | 操作。
- **模型元数据**（F-3013）：☑ | 模型名 | 供应商 | 端点 | 计费类型 | 状态(上架/下架) | 渠道引用数 | 操作。

**排序 Sort**
- 可排序列头右侧排序图标（线性 SVG 双箭头；激活时单向箭头 + `--color-primary-500`）。
- 单列排序：升/降/无三态循环；服务端排序（如 task 按 id desc，F-2003）默认降序。
- 多数列表默认按时间/ID 降序。

**分页 Pagination**
- 底部右侧：每页条数下拉（10/20/50/100）+ 上/下页 ghost 按钮 + 页码 + 「共 N 条」caption。
- 当前页码主色实底；服务端分页用 total/page_size（F-2004 TaskCountAllTasks 提供总数）。
- 搜索限流（SearchRateLimit）触发时分页区上方显限流提示（warning 内联条，F-3003）。

**行内状态表达**
- 强状态用徽章 + 可选 `.dot`：启用=success / 手动禁用=warning / 自动禁用=danger（附「已通知 root」hint）/ 排队=warning / 进行中=info / 成功=success / 失败=danger（hover 显 FailReason）。
- 进度列：细进度条（高 6px，`--radius-full`，填充 `--color-primary-500`，底 `--color-surface-sunken`）+ 右侧 `--font-mono` 百分比。

**表格三态**
| 态 | 表现 |
|---|---|
| 空态 | 表体居中 `.empty`：线性 SVG 插画位 + 主文案（如「暂无渠道，点击右上『新建渠道』开始」）+ 主操作按钮 |
| 加载态 | 行级骨架 `.skeleton`（5–8 行占位条，保留表头）；筛选切换时仅数据区骨架 |
| 异常态 | 表体居中 danger 图标 + 「加载失败」+ 重试按钮（查询出错/超时） |
| 权限拦截态 | 非 AdminAuth 访问 → 整页 403 占位（盾牌线性 SVG + 「无权访问该模块」+ 返回概览，§F AT-3 / §M PF-1） |

### 10.2 筛选区 FilterBar
- 位于页头与表格之间，`--color-bg-subtle` 底 + `--radius-md` + 内边距 `--space-4`。
- 控件横向排列（sm 尺寸）：关键词搜索框（前置放大镜线性 SVG）+ 维度下拉（类型/状态/分组/平台/角色）+ 时间区间 + 「筛选」primary sm + 「重置」ghost sm。
- **日志八维过滤**（F-4001）：type/时间区间/username/token_name/model_name/channel/group/request_id 折叠为「更多筛选」展开区（≥4 维时折叠，避免一行过挤）。
- 已应用筛选以可删 chip 显示在 FilterBar 下方；「清除全部」link。
- 自助维度受限（用户区日志无 username/channel）在合并工作台中按角色隐藏对应维度（§E UL-1）。

### 10.3 批量操作 BatchBar
- 勾选 ≥1 行后，表格上方滑入**批量操作条**（`--color-bg-elevated` + `--shadow-sm`，无大位移动效，仅高度展开）：
  - 左：「已选 N 项」+「清除选择」link。
  - 右：批量操作按钮组（按上下文）：批量启用/禁用（渠道 by tag, F-2019）、批量删除（danger, F-2016/F-3007）、批量导出 key（≤100, F-3005）、批量更新余额、批量生成兑换码。
- 批量删除/禁用 → 二次确认模态（显影响条数）。
- 批量超上限（如导出 key >100）→ danger 内联提示「单次最多 100 项」（F-3005 MsgBatchTooMany）。
- 批量部分失败 → 结果模态列出失败项（如清理日志 failed_files，F-4023）。

### 10.4 提示 / 反馈（Toast / Alert / 内联条）
| 类型 | 用途 | 样式 |
|---|---|---|
| Toast（瞬时） | 操作成功/失败轻反馈（保存成功、已禁用、已复制） | 右上角浮出，`--color-bg-elevated` + `--shadow-md` + 左侧语义色竖条 + 线性图标；3s 自动消失；reduced-motion 直接显示不滑入 |
| Alert 内联条 | 表单级/页级持续提示（合规未确认闸门、限流、降级、配置拉取失败） | 整宽条，语义 bg + fg + border + 线性图标 + 可选操作 link |
| 闸门提示 | 改倍率/邀请额度需先确认合规（F-4018/F-4030） | warning Alert + 「去确认合规」primary link，点后跳合规确认页 |

### 10.5 抽屉 Drawer（详情 / 编辑，管理台主力交互）
- 右侧滑入抽屉（`--shadow-lg`，宽 480–640px，复杂表单可 720px）；遮罩 `color-mix(in oklch, var(--color-text) 45%, transparent)`（暗色场景遮罩用 token 派生，非裸 oklch）。
- 头：标题（h4）+ 关闭 ghost；体：滚动区（表单/详情）；脚：sticky 操作栏（取消 ghost + 保存 primary）。
- 用途：渠道编辑（多 Key/映射/覆写/亲和多 Tab）、用户编辑、任务详情（含 events 时间线）、容器日志详情、部署详情、模型/供应商编辑。
- 动效：抽屉滑入用 `--ease-out --dur-2`；reduced-motion 减速不冻结。**抽屉是管理台编辑首选**（保留列表上下文，不跳页）。
- 复杂编辑（渠道）抽屉内用 Tab 分段：基础 / 模型映射 / 多 Key / 参数覆写 / 亲和规则 / 状态码映射。

### 10.6 模态 Modal（确认 / 向导 / 二次验证）
- 居中模态（`--radius-lg` + `--shadow-lg`，宽 400–560px）；遮罩同抽屉。
- **确认模态**：标题（h5）+ 描述（高危说明影响）+ 取消 ghost + 确认（danger/primary）。破坏性操作要求输入对象名/勾选确认框激活确认。
- **向导模态**：系统初始化（F-4016，步进：账号→模式→完成）、上游模型同步预览（F-3020，差异列表勾选→执行）。
- **二次验证模态**：SecureVerification（取 key/改倍率/重置 2FA，F-5033）——密码/TOTP/Passkey 验证步，验证通过才放行。
- 表达式计费配置模态：`--font-mono` 代码编辑 + 校验结果区（成功/语法错误 danger）。

### 10.7 徽章 / 状态标签 Badge
- 引用 `.badge`（L165–169）+ 语义类。管理台高频：角色(root/admin/common)、渠道状态(启用/手动禁用/自动禁用)、任务状态(6 态)、日志类型(consume/manage/login/error)、兑换码状态、模型上下架、合规已确认/未确认、数据驻地(境内/境外, F-5019)。
- 中性徽章 `.b-neutral`（分组名、平台名、计费类型）。
- 带状态点 `.dot`（在线/离线/排队的实时性表达）。

### 10.8 其他组件
- **面包屑 Breadcrumb**：顶栏内，管理区 > 模块 > 子页/详情；分隔符线性 SVG `/`；末级非链接 `--color-text`。
- **Tab 选项卡**：用于详情抽屉分段、系统设置分组、日志/审计切换；激活下划线 `--color-primary-500` 2px + 文字主色。
- **进度条 / sparkline**：进度条见 §10.1；sparkline 见 §9.5。
- **头像 Avatar**：用户管理列表 + 顶栏管理员；`--radius-full`，无图用首字母 + 主色底。
- **代码块**：渠道 JSON 配置（ModelMapping/ParamOverride/HeaderOverride/StatusCodeMapping）、表达式计费、容器日志——`--font-mono` + `--color-bg-subtle` 底 + `--radius-md`；容器日志按级别着色（error=danger / warn=warning / info=text-muted, F-3056）。
- **空状态插画位**：统一线性 SVG（stroke + currentColor `--color-text-muted`），每模块一个语义图形（渠道/用户/任务/日志/兑换码），禁 emoji、禁位图。
- **状态机可视化**（任务/订阅/部署）：详情抽屉内横向步骤条，已完成 success、当前 info、未达 muted、失败 danger（NOT_START→…→SUCCESS/FAILURE，F-2001；订阅 active/expired/cancelled，F-2046；部署 创建→运行→终止，F-3041）。

---

## 11. 响应式断点（端级）

> 引用 DESIGN-SYSTEM §8。管理台桌面优先；移动为应急查看，非主作业。

| 断点 | 范围 | 导航 | 表格 | 筛选 | 看板 |
|---|---|---|---|---|---|
| desktop | >1024px | 固定侧栏 240px（可折叠 64px 图标态）；顶栏 56px | 全列显示，横向可滚，操作列固定右 | FilterBar 横向一行 + 「更多筛选」展开 | 指标卡 4–6 列；图表 2 列 |
| tablet | 640–1024px | 侧栏默认折叠为图标（hover/点展开浮层） | 隐藏次要列（权重/标签/部分时间列），保留关键列 + 操作 | FilterBar 折行；维度下拉收进「筛选」面板 | 指标卡 2 列；图表 1 列 |
| mobile | <640px | 顶栏汉堡 → 抽屉导航；底部无 tab（后台不用底 tab） | **表格降级为卡片列表**：每行变一张卡（主字段 + 状态徽章 + 展开看详情）；触控目标 ≥44×44 | 筛选收进全屏 Sheet | 指标卡 1 列；图表横向可滚 |

补充移动规则：
- 触控目标 ≥44×44（行内操作按钮在移动端放大至 sm 下限 44 高）。
- 抽屉在移动端变为全屏 Sheet（自底/全覆盖），脚部操作栏 sticky 底，避让安全区。
- 移动端不依赖 hover 表达状态（hover 仅桌面增强）：状态全部用徽章常显，不靠悬停揭示。
- 批量操作在移动端：长按/勾选进入选择模式，BatchBar 固定底部。

---

## 12. 关键页高保真说明（管理区核心页，逐页区块 + token + 交互态）

> 选 6 个管理区核心页，逐页描述布局区块、用到的组件与 token、交互态（引用 PAGE-STATE-MATRIX）。

### 12.1 全局概览 Dashboard（看板，真图表）
- **布局**：页头（h3「全局概览」+ 时间区间切换 chip）→ KPI 卡区（6 卡，§9.4）→ 趋势区（请求量/费用双轴折线，全宽）→ 双列（左：模型/渠道占比环 §9.2；右：Top 模型/Top 用户排行柱 §9.3）→ 渠道健康度看板（表格 + sparkline §9.5）。
- **token**：卡 `.card`(`--shadow-sm`/`--radius-md`)；KPI 数 `--font-mono`；图表 `--chart-1…8`。
- **态**：加载（KPI 骨架卡 + 图区骨架）/ 空（区间无数据，图占位）/ 异常（图区重试）/ Count Up 动效（唯一动效，KPI 数字滚动入场，reduced-motion 直显终值）。来源 §A + §E UL-2。

### 12.2 渠道管理列表 + 编辑抽屉
- **布局**：页头（h3「渠道管理」+「新建渠道」primary + 「全量测试」「全量更新余额」secondary）→ FilterBar（类型/状态/分组/标签/关键词）→ 渠道表格（§10.1 列）→ 分页。
- **编辑抽屉**：720px，Tab（基础/模型映射/多Key/参数覆写/亲和/状态码映射）；多 Key 区为可增删行列表（每行 key 打码 + 状态徽章 + 轮询索引）；JSON 配置区 `--font-mono`。
- **态**：表单编辑/单Key/多Key随机轮询；保存成功(默认 Status=1)；必填校验失败；JSON 解析失败 danger；自动禁用行 danger 徽章+「已通知 root」；连通性测试 MJ/Suno 等不支持态（按钮 disabled + hint）。来源 §G CH-1/CH-3 + F-2017/F-2020。

### 12.3 用户管理列表 + 管理动作
- **布局**：页头（h3 +「创建用户」primary）→ FilterBar（角色/状态/分组/关键词搜索）→ 用户表格 → 分页。
- **行操作**：编辑(抽屉)/管理动作(下拉：启用/禁用/提升/删除)/OAuth 绑定(抽屉列表)/重置 Passkey(确认模态)。
- **态**：动作选择；禁用生效(状态徽章转 danger，列表刷新)；越权/提升越界拒绝(danger toast「不可操作同级或更高角色」)；无 AdminAuth 403 占位；仅剩一种登录方式解绑二次确认(防锁死)。来源 §B AC-10/AC-11。

### 12.4 任务监控看板（真图表 + 全量表格）
- **布局**：页头（h3「任务监控」+「超时扫描」secondary）→ KPI（总任务/进行中/成功率/失败数）→ 状态分布环 + 平台分布环 → 全量任务表格（§10.1，channel_id 列仅 admin 可见）→ 分页。
- **任务详情抽屉**：状态机步骤条 + 进度 + 产物(ImageUrl/VideoUrl 缩略，F-2010) + events + FailReason + 计费上下文(quota/BillingSource)。
- **态**：提交成功(NOT_START 0%)/排队进行中(进度递增)/成功终态/失败终态(FailReason)/超时退款态(CAS 成功标记)；越权 403。来源 §F AT-1/AT-3/AT-4。

### 12.5 计费规则（倍率配置 + 合规闸门）
- **布局**：Tab（模型倍率/分组倍率/缓存倍率/补全倍率/表达式计费/价格暴露）→ 各 Tab 为可编辑表格或代码区 →「保存」「从远端同步」secondary（同步前预览 F-2043）。
- **合规闸门**：改 group_ratio 或邀请额度正值时，顶部 warning Alert「需先确认支付合规」+「去确认」link → 跳合规确认（仅会话鉴权，access_token 拒绝 403，F-4030）。
- **态**：配置回显；保存成功(写 option.update 审计，仅记 key 不记 value)；JSON/表达式校验失败 danger；合规未确认拦截。来源 §L OP-2/OP-5 + §H BL-5。

### 12.6 运维监控（root，真图表）
- **布局**：KPI（goroutine/NumGC/内存 Alloc/磁盘 used_percent，超阈值卡 warning/danger 描边）→ 内存/GC 趋势折线 → 磁盘空间环 + 缓存命中率 → 运维动作区（清理磁盘缓存/强制 GC/重置统计，均 secondary + 确认）→ 日志文件管理（oneapi-*.log 列表 + 按数量/天数清理 danger）→ Uptime 状态(§9.5)。
- **态**：实时统计；清理/GC/重置成功 toast；日志清理 mode/value 非法 danger；部分失败结果模态；LogDir 未配置空态。来源 §L OP-3/OP-4。

---

## 13. 可访问性（A11y）

- **对比度**：暗色底主文字 `--color-text #E8EEEF` 对 `--color-bg #0E1416` ≈ 13:1（远超 AA 7:1）；次要文字 `--color-text-muted #8A989C` 对底 ≈ 4.8:1（达 AA 正文）；主色 `--color-primary-500 #2BB7C2` 对暗底 ≈ 6.5:1。语义徽章 fg 对自身 bg ≥7:1（DESIGN-SYSTEM §2.3）。**状态不只靠颜色**：徽章带文字标签 + 可选状态点，色盲可辨；图表用 `--chart-1…8` 色盲可分序列 + 图例文字。
- **焦点可见**：所有可聚焦元素 focus-visible 显 `--focus-ring`（主色 35% 3px 环）；danger 操作用 danger focus-ring；键盘 Tab 顺序 = 视觉顺序。
- **键盘可达**：表格行操作、批量勾选、抽屉/模态（Esc 关、焦点锁在层内、关闭后焦点返回触发元素）、下拉/日期选择全键盘可操作；表格支持方向键移动 + 空格选行。
- **触控目标**：移动端 ≥44×44；桌面行内 sm 按钮 ≥32 高（密集后台合理下限，移动放大）。
- **动效降级**：`prefers-reduced-motion` 下 `--motion-speed` 降 .35；KPI Count Up 直显终值；骨架 shimmer 减速 2.6s；Toast/抽屉滑入降级为直显。**业务关键信息不依赖动画完成才可见**（数据立即可读）。
- **语义与读屏**：表格用 `<th scope>` + caption；图表配 `aria-label` + 数据表备选（screen-reader 可读数值）；状态徽章 `aria-label` 含状态文字；表单 label 关联 + error 用 `aria-describedby`；高危确认模态 `role=alertdialog`。
- **图标**：一律内联线性 SVG（stroke + currentColor），带 `aria-hidden` 或 `aria-label`；**禁 emoji 功能图标**（DESIGN-SYSTEM 硬纪律）。

---

## 附：本端覆盖核对（对 §5 的 13 章节 + 出口门）

| 章节 | 状态 |
|---|---|
| 1 端概述与设计读 | ✓（含合并工作台 + 暗色场景说明） |
| 2 信息架构与导航 | ✓（用户区+管理区+超管区叠加 IA 树标 FID + 关键路径） |
| 3 调色板（端级，hex+OKLCH） | ✓（暗色解析子集，引用 DESIGN-SYSTEM 不重发明） |
| 4 字体字阶（端级） | ✓（裁出后台层级，无巨字/mega） |
| 5 间距与栅格 | ✓（最高密度档 + 表格紧凑档） |
| 6 阴影/圆角应用 | ✓（表面→级别映射，压低阴影） |
| 7 按钮系统（每变体每态色值） | ✓（primary/secondary/ghost/danger/link 全态 + 尺寸） |
| 8 表单元素（每态色值） | ✓（input/select/多选/radio/switch/数值区间/日期/校验） |
| 9 图表规范（真图表三类+三态） | ✓（趋势/分布/排名 + KPI + 健康度/Uptime；ECharts5） |
| 10 核心组件 | ✓（表格列/排序/分页/hover/空态/批量/抽屉/模态/toast/徽章/状态机） |
| 11 响应式断点 | ✓（desktop/tablet/mobile + 表格降级卡片 + 触控目标） |
| 12 关键页高保真 | ✓（6 页：概览/渠道/用户/任务/计费/运维，逐页区块+token+态） |
| 13 可访问性 | ✓（对比度/焦点/键盘/触控/动效降级/读屏/禁 emoji） |

- 看板/概览/数据页真图表门：✓（全局概览/任务监控/按日配额/运维监控均真图表，非数字卡墙）。
- 表格密集页门：✓（渠道/用户/任务/日志/订单列表均写清列/分页/排序/行 hover/空态/批量操作）。
- token 纪律：✓（全程引用 `var(--token)`，无裸色值、无 `#000/#fff` 裸用、无 emoji）。
- motion_intensity：✓（low 趋 none，仅 KPI Count Up；表格/操作区零装饰动效）。



