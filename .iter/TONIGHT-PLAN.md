# Nexa-AI 今晚作战文档

> 出文档时间：2026-06-22 晚
> 项目：nexa-ai（AI 中转站 / API relay SaaS），代码 `/root/nexa-app`，Java21 + Spring Boot DDD + 前端
> 文档目的：把"今晚要做什么"建立在项目真实记录（审计报告 + 迭代状态 + 实测代码）之上，今晚据此开干
> 编排方式：autonomous-product-iteration（已交付代码的迭代/bugfix，代码基线为事实源，不重跑产品工厂）

---

## 0. 一句话现状

P0 命脉链路（转发主干 + 双价计费 + 鉴权选渠落库）已经端到端跑通，单测已涨到 536 个。
**但代码库当前处于"拆了一半 + 一堆新功能全部未提交"的危险中间态——这是今晚上不了生产的头号物理原因，必须先处理。**

---

## 1. 今晚的头号风险：1083 项改动全部未提交（P0，最先做）

实测 `git status`：
- **1081 个文件显示"已删除"（D）**——其实是后端从单模块拆成 `nexa-common`(40类) + `nexa-service`(1012类) 两个 Maven 模块，旧 `backend/src/` 的文件被移走了
- **新模块目录 `nexa-common/` `nexa-service/` 未跟踪（??）**——拆分成果 + 新增的 `compliance` 合规子域（数据分级/留存/跨境 F-5016~5018）全在里面，**都没 commit**
- 好消息：这个拆分后结构**能干净编译通过**（已用 JDK21 实测 `mvn clean compile` EXIT=0）

**风险**：这种状态既不可复现、也不安全——任何人误一个 `git checkout/reset` 整轮成果蒸发；生产部署要的是干净可复现的已提交基线，现在不具备。

**今晚动作**：
1. 把拆分 + compliance 子域 + 其余改动，整理成清晰的 commit（建议按"Maven 拆分"/"compliance 子域"/"其余功能"拆几个语义 commit，不要一坨）
2. commit 前跑全量 `mvn clean test`（JDK21）确认 536 测试全绿、无回归
3. 推到 feature 分支，**不自动合 main**（合 main 等业务验收通过 + 用户明确指示）

> 负责人建议：后端开发组长 + 主控编译验证

---

## 2. 文档与实际不同步（P0，影响所有后续判断）

发现三套记录互相对不上：
- `.iter/STATE.md` 说 P0(REQ-01~06) + P1(REQ-09/10/12) 完成，单测 475
- `.iter/12_post_acceptance/S12-REPORT.md` 只覆盖 Maven 拆分这一窄轮
- **实际代码**：单测已 536（+61），还多出 `compliance` 合规子域、`ClaudeStreamCodec`/`OpenAiStreamCodec` 流式编解码——这些在任何状态文档里都没记

**风险**：状态文档失真 → 没人说得清"到底做完了什么、还差什么" → 无法判断能不能上生产。

**今晚动作**：
1. 以**当前代码**为事实源，重建一份准确的"已完成/进行中/未做" REQ 对照表（见第 4 节）
2. 更新 `.iter/STATE.md` 到与代码一致

> 负责人建议：主控 + 架构师核对

---

## 3. 上生产前的功能完成度核对（P1）

按审计报告 17 条 REQ（P0×6 / P1×6 / P2×5），结合实测代码状态：

### 已跑通（实测有据）
- **P0 命脉 REQ-01~06 + API-KEY-AUTH**：HTTP 基建 / 转发主干 / 选渠 / OpenAI 协议 / 双价计费落 Log / key 校验 —— 端到端 curl 200 + DB 落库实证
- **P1 REQ-09 重试容灾 / REQ-10 /v1/models / REQ-12 利润看板** —— 端到端验证过

### 进行中 / 部分（今晚要确认到底什么程度）
- **REQ-07 Claude 协议**：`ClaudeProtocolAdapter` 仍有 4 处 "not yet implemented" 抛错 → **未完成**
- **REQ-08 流式 SSE**：已有 `ClaudeStreamCodec`/`OpenAiStreamCodec`/`StreamCodecTest`，但 codec 里仍有 `return List.of()` 兜底 → **需确认是否真打通**

### 未做
- **REQ-11 视频代理 VideoProxy**
- **P2 全部**：REQ-13 Group 注册中心 / REQ-14 其余端点(embeddings等) / REQ-15 Gemini 协议位 / REQ-16 stub 甄别 / REQ-17 IP白名单限流

### 额外冒出（不在原 17 条，需归位）
- **compliance 合规子域**（F-5016 数据分级 / F-5017 留存策略 / F-5018 跨境路由）——有人新做了一整块，但未登记、未提交，今晚要确认它的完成度并纳入管理

---

## 3.5 用户实测反馈 CR（2026-06-22 晚补充，今晚必做）

用户实际点测后提的 6 个问题，已按代码定位转成 CR。前端框架 = Next.js 14 + React 18。

### CR-01【P0 严重】root 登录后点几个菜单就退化成普通用户菜单
- **现象**：登 root 账号，点左侧菜单跳几次后，菜单莫名变回普通用户的
- **根因定位（实测）**：前端有**两套割裂的 Shell**——`src/features/admin/components/AdminShell.tsx`(管理端菜单) 与 `src/features/console/components/ConsoleShell.tsx`(普通用户菜单)，分属 `(admin)` 和 `(console)` 两个 Next.js layout group。跨 layout group 跳转时整个 shell 重挂载，root 的权限/菜单状态没被持有住 → 掉回 console 默认
- **用户判断（成立）**：菜单不是按角色动态生成的动态路由，疑似静态表 + layout 割裂
- **类型**：B3 前端架构 bug（权限状态 + 路由架构）
- **方向**：菜单改成**按当前用户角色动态生成的单一权威菜单树**；root 菜单 = 普通用户菜单全集 + 管理项叠加（见 CR-03），不再靠两套 shell 切换
- **完成判据**：root 登录后任意点跳菜单，菜单始终是 root 全量菜单，不退化；刷新后保持

### CR-02【P1】右上角账号信息点开没有"退出登录"按钮
- **现象**：点右上角账号区，没有退出登录入口（目前"登出"只孤立藏在 SettingsPage 设备列表里 `SettingsPage.tsx:335`）
- **类型**：B1 前端缺功能
- **方向**：右上角账号下拉补"退出登录"，调用现有登出逻辑，清 token + 跳登录页
- **完成判据**：点右上角账号 → 下拉出现"退出登录" → 点击真正登出

### CR-03【P1，与 CR-01 同根】root 动态路由应包含普通用户全部菜单，取消"右上角切换视图"
- **现象**：现在 root 要在右上角切换视图才能看到普通用户的页面，割裂
- **类型**：B5 路由/权限模型调整（与 CR-01 一起改最省）
- **方向**：root 菜单 = 普通用户菜单 ∪ 管理菜单（超集），一棵树里全展示，去掉视图切换器；权限按角色在同一 shell 内叠加，不再 admin/console 两套 shell
- **完成判据**：root 登录后一棵菜单树里既有普通用户全部入口、也有管理入口，无需切换视图
- **注**：CR-01 + CR-03 本质是同一次重构（统一 shell + 动态菜单），建议合并一个 worker 做

### CR-04【P1】视频生成任务相关页面与功能先隐藏
- **现象**：视频生成相关页面/功能暂不上线，先藏掉
- **类型**：B0 范围裁剪（隐藏，不删代码）
- **方向**：菜单入口 + 路由守卫层面隐藏视频相关页（feature flag 关掉即可，保留代码待后续）。后端 REQ-11 视频代理同步不进本轮生产范围
- **完成判据**：前端任何角色都看不到视频生成入口；直接访问路由也被挡

### CR-05【P1】Redis 没用上，要接入
- **现象**：项目部署了 Redis 但后端没利用
- **类型**：B4/B5 后端基础设施增强
- **方向**：先让架构师定**用 Redis 缓存什么**（候选：token/鉴权校验缓存、group_ratio/模型映射等热配置缓存、/v1/models 列表缓存、限流计数器 REQ-17）。先接 1-2 个高频只读缓存见效，不一上来全套
- **完成判据**：至少一条真实链路走 Redis 缓存命中（有缓存命中日志/监控证据），且缓存失效策略明确
- **注**：需架构师先出一页"Redis 用途清单"再开干，别盲接

### CR-06【P1】超管页面仍有大量 mock 假数据，改成真实数据
- **现象**：超管(admin/root)页面很多写死的 demo/mock 数据
- **类型**：B2/B3 前后端对接（mock 残留）
- **方向**：逐个超管页排查写死数据 → 对接真实后端接口（缺接口的回后端补）→ 真实 Network 请求验证。这正是 iteration skill 基础冒烟第 7 条"Network 真请求"
- **完成判据**：超管各页数据来自真实 API（Network 可见真请求），无写死 demo；空态/错误态正常

---

## 4. 今晚任务分派（建议）

| 序号 | 任务 | 类型 | 负责 | 完成判据 |
|---|---|---|---|---|
| T1 | 全量 `mvn clean test`(JDK21) 跑通，确认 536 测试基线 | 验证 | 主控 | 全绿，无 fail |
| T2 | 把未提交的拆分+compliance+改动整理成语义 commit，推 feature 分支 | 工程 | 后端组长 | git 树干净，编译+测试过，已 push（不合 main） |
| T3 | 以代码为准重建 REQ 完成度对照表，更新 STATE.md | 梳理 | 主控+架构师 | STATE.md 与代码一致 |
| T4 | 核对 REQ-07 Claude 协议：补完 4 处未实现 + 单测往返 | 后端 | CC worker | `/v1/messages` 真转发，协议往返单测过 |
| T5 | 核对 REQ-08 流式 SSE 真实打通度（codec 是否真用上主干） | 后端 | CC worker | `stream:true` curl 收到真实 SSE 增量 + 末尾计费落 Log |
| T6 | 给 compliance 子域补登记（它做了什么、完成度、是否进生产范围） | 梳理 | 架构师 | 一页说明 + 纳入 REQ 表 |
| **T7** | **CR-01+CR-03 合并：统一 shell + 角色动态菜单树（root=普通菜单超集，去掉视图切换）** | **前端重构** | **前端组长** | **root 任意点跳菜单不退化；一棵树含全部入口；刷新保持** |
| T8 | CR-02：右上角账号下拉补"退出登录" | 前端 | 前端组长/CC | 点右上角 → 出现退出登录 → 真登出 |
| T9 | CR-04：隐藏视频生成相关页面/功能（feature flag，保留代码） | 前端 | 前端组长/CC | 任何角色看不到视频入口，直访路由被挡 |
| T10 | CR-06：超管页面 mock 假数据逐页对接真实接口 | 前后端 | 前端组长（缺接口回后端） | 超管各页 Network 真请求，无写死 demo |
| T11 | CR-05 前置：架构师出"Redis 用途清单"（缓存什么/失效策略） | 设计 | 架构师 | 一页清单，1-2 个高频缓存点定下来 |
| T12 | CR-05 实施：按清单接 1-2 个 Redis 缓存点 | 后端 | 后端组长/CC | 真实链路缓存命中有证据 |
| **T13** | **核心全链路 E2E 测试：建供应商→配模型/模型组→用模型调用（上游用 mock）** | **集成验证** | **集成联调组长** | **真 curl 走通：建 channel→配 ability/模型组→token 调用→上游 mock 返回→双价计费落 Log，全链路 DB 实证** |

> CR-01/03 已诊断完成（前端组长，见第 7 节诊断结论）——T7 可直接进入实施。

> 优先级：
> - **最先（让代码库安全）**：T1 → T2
> - **最严重的 bug（用户卡点）**：T7（CR-01/03 root 菜单退化 + 割裂视图）——**已诊断完，可直接改**
> - **核心信心验证**：T13 全链路 E2E（证明命脉真能跑通，是"能不能上生产"的硬证据）

> - **紧随**：T3 状态对齐
> - **并行铺开**：T8/T9/T10（前端 CR）、T11→T12（Redis）、T4/T5/T6（后端命脉 + compliance）
> 红线：不动生产、不自动合 main、改完必须真验证（编译+测试+真 curl/真 Network），不只看"代码写了"。
> 依赖提示：T7 是 T8/T10 的地基（shell 统一后再补退出登录、再接真数据最省返工），建议 T7 先动。

---

## 4.5 全局开发约束：开发第一守则（今晚所有 worker 必守）

**反过度工程是今晚（及后续）所有前后端开发的第一守则，优先级高于一切其他纪律。** 已写进 `karpathy-coding-discipline` 第 0 条，并注入 backend-dev-lead / frontend-dev-lead 的 CC prompt 模板——开发组长派 CC worker 时必须显式交代：

1. **优先用项目现有依赖/能力解决**——能一行调用解决绝不手写、绝不引新库
2. **确需新能力优先用成熟依赖不造轮子**——但**能用现有依赖替代的绝不引新依赖**，引新依赖必须说明理由
3. **实在没现成才自己写最小代码**——50 行够绝不写 200 行
4. **不该抽象/拆分时坚决不拆（YAGNI）**——只有当前真有重复/第二调用方才抽公共层

> CC 派发实测可用：`cat prompt.md | claude -p --max-turns N`（v2.1.128，走 ningmeng.chat）。**坑：`/usr/bin/cc` 是 GCC 编译器，派 worker 必须用 `claude` 不能用 `cc`。**

---

## 5. 今晚不做（避免范围蔓延）

- P2 增强（REQ-13~17）今晚不碰，留下一轮
- 不重跑产品工厂 S1-S6、不回写旧 PRD/原型（除非 T4/T5 暴露结构性契约变更才回 change-backpropagation）
- 不直接上生产——今晚目标是"回到干净可发布基线 + 补完命脉相邻的 REQ-07/08 + 摸清 compliance"，真正部署等基线干净 + 业务验收过

---

## 6. 今晚成功长什么样

1. 代码库：一个干净的、已提交、能编译+全测试绿的 feature 分支（不再是 1083 项游离改动）
2. 认知：一张和代码一致的 REQ 完成度表，说得清"还差哪几条才能上生产"
3. 进展：REQ-07/08 补完或明确卡点，compliance 子域归位登记
4. CR-01~06 用户实测问题修完并真验证（尤其 root 菜单不再退化）
5. 核心全链路 E2E（建供应商→配模型组→调用→计费落库）真 curl 跑通，有 DB 实证
6. 全程进度发微信汇报

---

## 7. CR-01/03 诊断结论（前端组长，已完成）

**一句话根因**：不是权限态丢失，是菜单架构错了——两套写死的静态 Shell（`AdminShell`/`ConsoleShell`）分挂两个路由组，而 admin 菜单里埋了指向普通用户路由 `/dashboard` 的链接 + "用户视图"切换链接，root 一点到就跨组把外壳换成只渲染普通用户静态菜单的 ConsoleShell → 菜单"退化"。

**证据（文件:行）**：
- 主因：`AdminShell.tsx:83` admin 菜单含 `href:'/dashboard'`（console 路由）+ `:209-212` 顶栏"用户视图"链接 → 点了跨组换壳到 `ConsoleShell`
- `ConsoleShell.tsx:61-87` 普通用户菜单是写死静态表，**完全不读 role 生成菜单**
- 次因：`AdminShell.tsx:166` role 兜底 `?? ROLE.ADMIN`——refetch 瞬间 self.data 为 undefined 时 root 闪退成 admin，root 专属项消失
- 放大因子：mock 数据 `mock.ts:30` / `mock-console.ts:55` root 账号 role 写死成 1（普通用户）
- 角色态存 React Query 缓存（`account.model.ts:106`），无持久层，但 QueryClient 在根 layout，跨组缓存其实没丢——丢的是"ConsoleShell 不读 role"的视觉表现

**修复方案（T7 实施）**：
1. 抽单一菜单数据源 `nav-tree.ts`：合并两张表为一棵带 `minRole` 的树（普通项 COMMON / 管理项 ADMIN / root 项 ROOT），root 天然是普通用户菜单超集
2. 合并成单一 `AppShell`（基于 AdminShell 改）：`nav.filter(currentRole >= minRole)`；role 兜底从 `?? ADMIN` 改安全处理（pending 显式 loading 或兜底 COMMON，不闪退也不越权 flash）
3. 删视图切换：`AdminShell.tsx:187-214` 角色切换下拉 + `ConsoleShell.tsx:151-155` 进入管理后台入口
4. 收敛 route group：`(admin)` 与 `(console)` 共用同一 layout + AppShell + 守卫（建议保留 `/admin/*` 前缀避免大规模改 href）
5. React Query：self 设 staleTime（如 5min）减少跨页 refetch 闪烁；登出 removeQueries 再跳登录

**风险点**：① 合并路由组注意 `/admin/*` 与 console flat 路径不冲突；② 守卫放宽后 admin/root 专属 **page 仍须独立 role 守卫**（前端体验层 + 后端 @RequireRole 才是真防线），防普通用户敲 URL 直进管理框架；③ 菜单 `minRole` 必须与后端各端点 @RequireRole 对齐，别露出会 403 的入口；④ 深浅色 scheme 合并后别回归。
