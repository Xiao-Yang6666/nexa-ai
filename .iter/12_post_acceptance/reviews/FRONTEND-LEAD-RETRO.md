# S12 前端开发组长复盘 — nexa-app/frontend

> 角色：前端开发组长　|　范围：体验 / 组件 / 路由 / 交互 / 前端债务
> 基线：main = cf5e0a4，Next.js 14（App Router），180 tsx/ts，build 过，api-missing 全局清零
> 方法：通读 `frontend/src` 全量 feature + `shared/api` + `shared/ui` + 路由层，并与 `backend` 实际端点交叉核对

---

## 0. 总体判断

前端工程化质量在 SaaS 控制台里属于中上：feature 切片清晰（`api → model（VM/hook）→ components` 三层）、设计 token 化纪律好（几乎无裸 hex）、客户端「零泄露」白名单（`toAccountVM` 等）落得很实、`shared/api/client.ts` 的 `ApiResponse` 解包 + `ApiError` 归一化是干净的单一入口。

但「api-missing 全局清零」是个**会误导人的绿灯**：它只保证「前端调用的端点后端都存在」，不保证「前端该调的端点都调了」。实际盘下来，**有相当一批页面仍是 S6 静态原型直接工程化、没有接任何接口**，只是因为它们压根没发起请求，所以不会被 api-missing 扫到。这是当前最大的前端债务面，下一轮必须正面处理。

三句话结论：
1. **数据接通率不均衡**：核心交易链路（登录/账户/密钥/充值/签到/模型映射/管理 CRUD/利润/系统设置）已接真接口；但**仪表盘图表、任务监控、分销趋势/明细、个人资料/密码/设备**仍是写死数据。
2. **mock 代码可以删了**：`shared/api/mock*.ts` 三个文件（约 800 行）开关已关、后端已就位，留着只会让「假接通」更难被发现，建议按下文清单清理 + 补真。
3. **缺一套共享的 loading/empty/error 态组件**，导致每个页面各写各的骨架/占位，体验不一致、也是重复劳动。

---

## 1. Feature 完整度盘点（骨架 / mock 残留 → 具体到文件）

按「是否真正接后端」分三档。

### A 档：已真实接通（健康）
这些 feature 的 `model` 层有 `useQuery/useMutation` + `api` 层 `http.*`，端点经核对后端存在：

| feature | 页面 | 接的端点 | 备注 |
|---|---|---|---|
| account | LoginForm / RegisterForm / SettingsPage(notify/api) / UsersAdminPage | `/api/user/login`·`/register`·`/self`·`/self/setting` | 登录/注册/通知偏好/用户管理 OK |
| billing | RechargePage / BillingPage / PricingPage / BillingRulesPage | `/api/topup`·`/api/log/self`·`/api/pricing`·`ratio` | 充值下单含 `isError` 兜底，较完整 |
| channel | ChannelsAdminPage | `/api/channel/` CRUD | OK |
| model | ModelsAdminPage / ModelMapPage / ModelsPage | `model-admin`·`model-alias`·`/api/pricing` | C→A 映射 CRUD 已接（见 §2 勘误） |
| modelgroup | ModelGroupsPage | `modelgroup` CRUD | OK |
| redeem | RedeemPage | `redeem` CRUD | OK |
| group | GroupsPage | `prefill` | OK |
| log | LogsAuditPage / UsagePage | `/api/log/*` | OK |
| token | KeysPage | `/api/token/` CRUD | OK |
| profit | ProfitPage | `/api/profit/dashboard` | OK |
| ops | OpsMonitorPage | `/api/status` | 注释诚实说明了「无主机资源端点」，以 status 聚合代之 |
| system | SysSettingsPage | `/api/option` 18 键 + 3 操作端点 | R3/R4 已接通 |
| relay | TasksPage（console 异步任务） | `GET /api/task/self` | OK |
| growth | CheckinPage | `/api/user/checkin` GET/POST | 签到部分 OK |

### B 档：纯静态 / 写死数据，零接口（**优先级最高的债**）

1. **`features/task/components/TasksMonitorPage.tsx`（admin 任务监控）— 全静态**
   - `KPI`、`TREND_DONE/FAIL`、`STATUS_DIST`、`LATENCY_BY_TYPE`、`TASKS`（14 行假任务）全是模块顶层常量；告警条「检测到 28 个超时」、分页「全量 9,360」也是写死文案。
   - 筛选 `select`/搜索 `input` 无 `onChange` 落地到查询，纯摆设。
   - 注意：它和 `features/relay/TasksPage.tsx`（console 端，已接 `/api/task/self`）是**两个 task 概念**（管理监控 vs 用户自助），但命名高度撞车，且 admin 这个完全没数据源。

2. **`features/dashboard`（两个仪表盘的图表区）**
   - `DashboardPage.tsx`（console）：KPI 卡走 `useKpi()`（真），但 `RECENT`（最近调用 8 条）、`TREND_30D`、`MODEL_DIST`、`LATENCY_BY_MODEL` 全静态。`dashboard.model.ts` 注释自认「契约暂无聚合按日/分模型 series 端点，暂用静态」。
   - `AdminDashboardPage.tsx`：趋势/分布/Top 渠道走 `useAdminDashboard()`（拼 `/api/data/`+`/api/profit/dashboard`+`/api/channel/`，真），相对更完整，但延迟分布等仍有静态成分。
   - 结论：console 仪表盘的图表是「真 KPI + 假趋势」的拼装，视觉上完整、数据上半真半假，最容易让验收误判。

3. **`features/growth/components/ReferralPage.tsx`（分销推广）**
   - 邀请码（`useAffCode`）、邀请人数/累计返佣（来自 `useSelf` 的 `aff_count/aff_quota`）是真；但**返佣趋势图、邀请明细列表是演示数据**（文件注释已标 `openapi 无邀请明细端点`）。
   - 「申请结算」「申请提现」按钮无 onClick / 无 mutation。二维码是 `aria-label="邀请二维码占位"` 的占位块。
   - **关键勘误（见 §2）**：后端其实有 `GET /api/user/self/aff_stats`（F-1045 邀请统计三项）和 `POST /api/user/self/aff_transfer`（F-1044 邀请额度划转），前端 `growth.api.ts` **根本没封装这两个**。这不是「后端没端点」，是前端漏接。

4. **`features/account/components/SettingsPage.tsx`（个人设置）— 半接通**
   - `notify` tab：`saveNotify()` → `useSaveSetting()`，真。
   - `profile` tab：昵称/公司输入框 `defaultValue` 来自 self，但「保存更改」按钮**没有 onClick**，是死按钮。
   - `security` tab：「当前密码/新密码/确认」三个输入框 + 「更新密码」按钮**全无 onClick、无修改密码 mutation**；`DEVICES`（登录设备列表）是文件内静态常量，「登出」按钮也是摆设。TOTP Toggle 用 self.setting 的 `totp_enabled` 但切换不回写。
   - 这是个体验陷阱：用户填了密码点按钮没有任何反馈。

5. **`features/ranking/components/RankingPage.tsx`（公开排行榜）**
   - 设计上就是纯展示页（数据来自 `ranking-data.ts` 公开评估常量，无榜单接口）。注释明确「纯展示页：数据来自公开评估常量」。
   - 这个**可以接受**保持静态（产品定位是营销/口碑页），但要在产品层确认：是否要一个 `/api/ranking` 真端点，否则价格/排名会随模型上下架失真。

### C 档：营销/法务/文档静态页（设计上即静态，无债）
`marketing`（HomeHero/PublicShell）、`legal`（隐私/协议，`legal-content.ts`）、`docs`（10 个文档页 + 自研 `highlight.ts` 语法高亮）。这些天然是 CMS/静态内容，不算债。`docs/docs-shell-ref.js` 是个遗留 `.js` 引用文件，建议确认是否还需要（疑似早期参考残留）。

---

## 2. shared/api/mock* 清理与「假接通」核查（任务重点）

三个 mock 文件：`mock.ts`（auth+pricing）、`mock-console.ts`（17KB，控制台 self-scope 全套）、`mock-pricing.ts`（pricing 桩）。开关在 `app/providers.tsx`：仅 `NEXT_PUBLIC_USE_MOCK==='1'` 时 `installMock()`，当前生产关闭。

### 2.1 结论：建议删除，而非保留
- **理由 1**：后端已全量就位（R4-A 全新空库部署全链路 200 验过），mock 的「前后端并行解耦」使命已完成。
- **理由 2**：mock 仍在会掩盖 B 档「假接通」——开发期开 mock 时页面看起来都有数据，关掉后才暴露哪些根本没接。删 mock 是逼出真实接通率的最直接手段。
- **理由 3**：mock 里塞了大量业务语义（`SELF_USER` 的 setting 18 项、`TASKS` 14 条、`aliases` 8 条、`CANDIDATE_MODELS`），和真实 DTO 容易漂移，维护即负债。

### 2.2 删除前必须先核对的「契约勘误」（重要发现）
`mock-console.ts` 与 `model-alias.api.ts` 的注释都写着：
> `GET/POST /api/user/self/model_aliases` …CRUD 主资源路径按 DTO + new-api REST 惯例**推定**，待 S7 补登

但与后端核对：`com.nexa.model.interfaces.api.UserModelAliasController` **真实存在**，`@RequestMapping("/api/user/self/model_aliases")`，GET/POST/PUT{id}/DELETE{id}/candidates **五个端点全在**。
→ **这些不是「推定」，是已实现端点，注释过时了**。删 mock 时顺手把这些「推定/待补登」的注释清掉，否则会误导后人以为接口不存在。

同理 `mock.ts` 顶部「拦截 auth 端点……后端就绪后关掉」整段说明、`mock-console.ts` 第 234-235 行的「contract 仅登记 candidates；CRUD 主资源路径按惯例推定」都应随删除一并清理。

### 2.3 mock 端点 vs 后端真实端点对照（删除评估表）

| mock 端点 | 后端真有？ | 前端真调？ | 处置 |
|---|---|---|---|
| `POST /api/user/login` `/register` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/pricing` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/user/self` `PUT /self/setting` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/user/self/aff` | ✅ | ✅ | 删 mock，已接 |
| `GET/POST /api/user/checkin` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/task/self` | ✅ | ✅(console) | 删 mock，已接 |
| `GET /api/user/self/models` | ✅ | ✅ | 删 mock，已接 |
| `model_aliases` GET/POST/PUT/DELETE/candidates | ✅（注释误标推定） | ✅ | 删 mock，**修注释** |
| `GET /api/token/` `POST /api/token/` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/log/self` `/self/stat` | ✅ | ✅ | 删 mock，已接 |
| `POST /api/topup` | ✅ | ✅ | 删 mock，已接 |
| `GET /api/user/self/aff_stats`（F-1045） | ✅ | ❌**漏接** | 前端补 `growth.api.ts` + ReferralPage 接入 |
| `POST /api/user/self/aff_transfer`（F-1044） | ✅ | ❌**漏接** | 前端补封装 +「申请结算/划转」按钮接上 |

→ 净结论：mock 三件可整体删除；删除动作要**捆绑**两件补真（aff_stats / aff_transfer）和一处注释修正，否则删完 ReferralPage 的趋势/明细会直接空白。

---

## 3. 组件抽公共化建议（具体到文件）

`shared/ui` 现有：Button / Input / Card / Badge / Table / Field —— 基础原子层 OK，但**复合态组件缺位**，导致重复造轮子。

### 3.1 缺一套「数据态」组件（最高 ROI）
全仓没有 `EmptyState / ErrorState / LoadingSkeleton / Spinner`（已确认 `shared/ui` 无此类导出）。现状是每页各写：
- `ModelsAdminPage.tsx:134` 自定义「统一占位行（加载/错误/空）」
- `AdminDashboardPage` 自定义 `chartEmpty`
- `ReferralPage`/`TasksPage` 各写 `styles.skeleton`
- `ChannelsAdminPage.module.css:101`「空/加载/错误占位行」
- `dashboard/AdminDashboardPage.module.css:192`「占位/错误/页脚」

建议抽 `shared/ui/components/DataState.tsx`：统一 `<DataState loading error empty onRetry>`，吃 React Query 的 `isLoading/isError/error/refetch`，配 `ApiError.message`。这能一次性拉平十几个页面的态展示，并消灭「错误了但页面只显示空」的不一致。

### 3.2 SVG 图表组件抽公共化
`TrendChart`（面积折线）在 `DashboardPage`、`AdminDashboardPage`、`TasksMonitorPage` 里**各写了一份几乎一样的实现**（同样的 pad/xs/ys/areaPath 套路）；`DonutChart`/`LatencyBars` 也重复。建议抽 `shared/ui/charts/{AreaLine,Donut,HBar}.tsx`（无依赖、纯 SVG、吃 token 色），既消重又便于将来统一接真实 series 数据。

### 3.3 表单字段一致化
`Field` 已存在，但 SettingsPage / ChannelsAdminPage 等大量直接写 `<label className="field-label"> + <input className="input">` 裸结构，没走 `Field`/`Input` 组件。建议收口到 `shared/ui` 的受控字段组件，顺带统一校验展示（见 §5）。

### 3.4 vendor 图标已抽得很好
`features/model/VendorAvatar.tsx` + `vendors.ts`（simple-icons path / 首字母占位 / token 色）是个正面样板，`RankingPage` 也复用了 `vendorIcon`。保持。

---

## 4. 路由 / 状态 / API client 整理

### 4.1 路由（App Router）—— 结构健康
四个 route group 分层清晰：`(public)` / `(console)` / `(admin)` / `(docs)`，`features/shell` 用单一 `nav-tree.ts` + `minRole` 过滤合并了原 console/admin 两套写死菜单（CR-01/CR-03 已做），`RouteShellLayout` 在鉴权未决时渲染占位防越权闪现 —— 这块是亮点，无需大动。

小问题：
- `nav-tree.ts` 里 `model-map`（模型映射）和 `referral`（分销）都用了 `ic: 'share'` 同图标，侧栏可辨识度差，建议换一个。
- admin `tasks-monitor`（静态页，§1-B1）挂在「运营」组里有完整入口，但点进去是假数据，要么接真要么先降级隐藏，避免验收点到。

### 4.2 状态管理 —— React Query 用法规范
`providers.tsx` 全局 `retry:1 / refetchOnWindowFocus:false / mutations.retry:0`，合理。`useLogout` 在 `onSettled` 里 `qc.removeQueries()` 清全部缓存防串号、`useSelf` 设 5min staleTime 防菜单角色抖动 —— 这两处是经过思考的好实践。无 Redux/zustand 冗余，状态分层干净。

建议：query key 目前散落各 model（`['account','self']`、`['dashboard','kpi']` 等），建议建一个 `shared/api/query-keys.ts` 集中登记，避免 invalidate 时 key 拼错（已经能看到 `useSaveSetting` 手写 `['account','self']` 与 `useSelf` 重复字面量）。

### 4.3 API client —— 单一入口干净，两点可加固
`client.ts` 整体好（统一 baseURL / credentials:'include' / ApiResponse 解包 / ApiError 归一）。两点建议：
1. **401 全局处理缺位**：当前 `!res.ok` 一律抛 `ApiError(status)`，但没有 401→跳登录 的全局拦截。会话过期时各页面只会各自报错。建议在 `request()` 里对 401 统一触发登出/跳 `/login`（或在 QueryClient 层加 onError）。
2. **非包络透传分支**（`return payload as T`，relay 透传用）缺类型约束，目前靠调用方泛型自觉，易埋坑。relay 域接入更多透传端点时要补 runtime 校验。

---

## 5. 错误处理 / loading 态 / 表单校验 一致性

### 5.1 错误处理 —— 不一致
- 好的：`RechargePage` 有 `topup.isError` + `(topup.error as Error)?.message` 兜底文案；`OpsMonitorPage`/`AdminDashboardPage` 有 `isError`+`refetch` 重试。
- 差的：`SettingsPage` profile/security 的死按钮点了**无任何反馈**；`ReferralPage` 的演示数据区没有 error 概念。
- 根因：无 §3.1 的统一 `ErrorState`，每页自由发挥，覆盖率参差。

### 5.2 loading 态 —— 不一致
存在三种风格混用：`styles.skeleton` 骨架块（ReferralPage）、`placeholder={isLoading?'加载中…':...}`（SysSettingsPage 大量）、自定义占位行（ModelsAdminPage）。建议统一到 `DataState`/`Skeleton`。

### 5.3 表单校验 —— 仅登录/注册有，其余基本裸奔
- `LoginForm`/`RegisterForm` 有「空值抖动校验 → loading → 成功/失败」的完整交互（注释可见），是标杆。
- 其余写表单的页面（Channel 新建、ModelGroup 新建、Token 新建、Settings profile、Recharge 自定义金额）**基本只靠 mock 里的服务端校验或 input 原生 min/max**，前端没有统一的 required/格式校验与字段级错误提示。Recharge 自定义金额 `max=10000` 仅靠 HTML 属性，绕过即穿透。
- 建议：随 §3.3 收口字段组件时引入轻量校验（如 zod + react-hook-form，或自研 Field 级校验），至少把「必填 / 数值范围 / 邮箱格式」拉平。

---

## 6. 设计还原 / 响应式

- 设计 token 纪律执行得好：`tokens.css` 单一来源，多处注释强调「不写死 hex，走 var(--token)/color-mix」，DOM 无裸色值（vendor 品牌色也走 `--v-*` token）。这是高质量信号。
- SVG 图表用 `viewBox + width="100%"` 自适应宽度，但**固定 viewBox 高度**，窄屏下文字会挤（TasksMonitorPage 的 `LatencyBars` W=1180 在移动端会强缩）。响应式主要风险点在这些宽图表和宽表格（`tableWrap` 横向滚动是有的，但列多时体验一般）。
- 未见明显的暗色主题问题（`RouteShellLayout` 给 `<html data-scheme='dark'>`）。建议下一轮补一次真机/窄屏走查，重点 admin 的宽表 + 图表区。

---

## 7. 下一轮前端 slice 怎么拆更稳（建议排期）

按「风险×ROI」排序，每个 slice 控制在可独立验收的粒度：

**Slice F1 — 删 mock + 暴露真相（0.5d，必须最先做）**
删 `shared/api/mock.ts`/`mock-console.ts`/`mock-pricing.ts` 及 `providers.tsx` 安装逻辑、`index.ts` 导出；修 `model-alias.api.ts`/相关注释里「推定/待补登」措辞。删完跑一遍各页面，记录所有变空白的页面 → 即 B 档真实清单。这一步是后续所有 slice 的「照妖镜」。

**Slice F2 — 补漏接的真端点（1d）**
- `growth.api.ts` 补 `getAffStats()`（`/api/user/self/aff_stats`）、`transferAff()`（`/api/user/self/aff_transfer`）；ReferralPage 的 KPI/「申请结算」接真。
- SettingsPage：profile「保存更改」接 `useSaveSetting`；接后端修改密码端点（核对是否存在，不存在则提产品/后端）。

**Slice F3 — 共享数据态组件 + 收口（1d）**
落 `shared/ui/DataState`（loading/empty/error/retry），改造 B/A 档约 12 个页面的态展示；顺带抽 `shared/ui/charts`。

**Slice F4 — 静态图表接真（依赖后端 series 端点，1.5d，需协同）**
console DashboardPage 的 `RECENT`（接 `/api/log/self` 最新 N 条，端点已有，纯前端可做）、`TREND_30D`/`MODEL_DIST`（需后端按日/分模型聚合端点 → **提需求给后端**）。admin TasksMonitorPage 接真（需后端任务监控聚合端点 → 后端确认）。
拆分要点：能用现有端点的（RECENT）先做，需后端新端点的（趋势 series）单独成卡、明确阻塞依赖，别和能做的混在一个 slice 里卡住。

**Slice F5 — 表单校验 + 401 全局处理（1d）**
引入字段级校验框架，统一必填/范围/格式；client.ts 加 401→跳登录。

**Slice F6 — 响应式走查（0.5d）**
窄屏 + 宽表/宽图表专项修复。

排期纪律建议：F1 是前提，F2/F3 可并行，F4 看后端端点节奏，避免再出现「页面看着完整、数据是假的」绿灯误判。

---

## 8. 风险与债务清单（速查）

| 等级 | 项 | 文件 | 处置 |
|---|---|---|---|
| 高 | admin 任务监控全静态 | `features/task/TasksMonitorPage.tsx` | 接真或先隐藏入口 |
| 高 | 分销趋势/明细演示数据 + aff_stats/aff_transfer 漏接 | `features/growth/*` | F2 补真 |
| 高 | 设置页 profile/密码/设备 死按钮 | `account/SettingsPage.tsx` | F2 接真 |
| 高 | mock 仍在掩盖假接通 | `shared/api/mock*.ts` | F1 删除 |
| 中 | 仪表盘图表半真半假 | `dashboard/*` | F4 接 series 端点 |
| 中 | 无共享 loading/empty/error 组件 | `shared/ui` | F3 抽 DataState |
| 中 | SVG 图表三处重复实现 | dashboard/task | F3 抽 charts |
| 中 | 无 401 全局处理 | `shared/api/client.ts` | F5 |
| 中 | 表单校验仅 auth 有 | 各写表单页 | F5 统一 |
| 低 | `model-alias` 注释过时（误标端点为推定） | `mock-console.ts`/`model-alias.api.ts` | F1 随删修正 |
| 低 | nav 图标重复（model-map/referral 同 share） | `shell/nav-tree.ts` | 顺手改 |
| 低 | `docs/docs-shell-ref.js` 疑似遗留 | `features/docs/` | 确认可删 |
| 低 | 宽表/宽图表窄屏挤压 | 多处 SVG/table | F6 |

---

*复盘人：S12 前端开发组长　|　依据：cf5e0a4 代码实读 + 前后端端点交叉核对*
