# ROLE-PERMISSION-MATRIX — 角色×操作 RBAC 矩阵 + 多租户决策

> 角色：覆盖上一轮 S2 缺失的第三个横切维度——**权限与租户**。
> 权威边界：repo new-api 一手为**三级角色**：`common`（普通用户）/ `admin`（管理员）/ `root`（超管），见 GLOSSARY §C「角色 admin/common/root」、REPO-INSPECTION D1。S1 还观察到访客（未登录）、运营/开发者/运维等**业务角色语义**散落在能力域中。本文件**不以「证据不足」回避**——而是：① 以 repo 三级角色为**实现底座**，② 把业务语义角色映射为这三级 + 用户分组（User Group）的组合，③ 给出落地的角色×操作矩阵，④ 对多租户给**明确决策建议**。

---

## 1. 角色模型：repo 底座 → 产品角色

repo 的鉴权底座是 **3 个系统角色 + 用户分组（group_ratio / 可用分组）**。本产品在此之上定义 **7 个产品角色**，全部可由「系统角色 + 分组 + 功能开关」组合实现，**无需** repo 之外的新鉴权内核（除多租户，见 §5）。

| 产品角色 | 映射到 repo 底座 | 定位 | 证据 |
|---|---|---|---|
| **访客 Guest** | 未登录（无 JWT） | 仅可访问公开站、价格页、模型广场、Playground 试用、注册/登录 | relay-router `/pg/*`、`/api/pricing`、`/api/rankings` |
| **用户 User** | `common` 角色 | 自助：令牌、额度、充值、签到、邀请、任务、日志自助查询 | model/user.go role=common |
| **开发者 Developer** | `common` + 高配额分组 + 多令牌/IP白名单/模型白名单 | User 的「重度调用」子集（产品权限同 User，差异在分组/配额/令牌能力，不需独立系统角色） | model/token.go AllowIps/ModelLimits、user group |
| **运营 Operator** | `admin` 角色（可加运营功能子集开关） | 渠道/计费倍率/兑换码/订阅/公告/用户管理等运营动作，**不**碰全站系统级 Option | role=admin、AdminAuth 路由 |
| **运维 SRE** | `admin` 角色（运维功能子集：性能/缓存/部署/监控） | 性能统计、缓存清理、强制 GC、部署管理、Uptime 监控，**不**改计费/用户资产 | controller/performance.go、deployment.go、uptime_kuma.go |
| **管理员 Admin** | `admin` 角色（全 admin 能力） | 运营 + 运维的并集（中小部署常合一） | AdminAuth |
| **Root 超管** | `root` 角色 | 全站系统 Option、OAuth provider 配置、setup、限流、敏感词、所有 admin 能力 | role=root、RootAuth、controller/option.go/setup.go/custom_oauth.go |

> 设计取舍：**运营/运维/开发者是「角色画像」而非新增系统角色**。repo 已有 `admin` 不足以细分运营 vs 运维——本产品建议在 admin 之上引入**功能权限组（permission set）开关**（运营集 / 运维集），由 root 分配，从而在不动 JWT 内核的前提下实现「最小权限」。这是 §6 的派生功能项 F-5030。

---

## 2. 操作域分类（列）

把 17 个能力域的操作归并为 **12 类操作域**（矩阵的列）：

| 代码 | 操作域 | 覆盖能力域 |
|---|---|---|
| O01 | 公开浏览/试用 | 公开站、价格页、广场、Playground（D12/D17） |
| O02 | 账号与身份（自身） | 注册/登录/2FA/Passkey/OAuth 绑定（D1） |
| O03 | 令牌/API Key（自身） | 令牌 CRUD、取明文 key、模型/IP 白名单（D11） |
| O04 | 额度/充值/订阅（自身） | 充值、兑换码、订阅、额度查询（D8） |
| O05 | 增长（签到/邀请） | 签到、邀请返利划转（D2/D3） |
| O06 | 异步任务（自身） | MJ/Suno/视频提交与查询（D5） |
| O07 | 渠道管理 | 渠道 CRUD/测试/余额/多Key/上游同步（D7/D12） |
| O08 | 计费配置 | 倍率/阶梯/表达式/兑换码批量/订阅计划（D8） |
| O09 | 用户管理 | 管理端用户 CRUD/分组/2FA重置/封禁（D1） |
| O10 | 日志/审计/用量 | 全量日志、审计、用量统计、排行（D15） |
| O11 | 运维（性能/缓存/部署/监控） | perf/GC/缓存清理/部署/Uptime（D14/D16） |
| O12 | 系统设置（全站） | 全站 Option、OAuth provider、setup、限流、敏感词、隐私政策（D16/D1） |

---

## 3. 角色 × 操作 RBAC 矩阵

> 图例：✅ 允许 ｜ 🔒 允许但需二次验证(SecureVerification/2FA) ｜ 🟡 仅限本人资源(self-scope) ｜ ➖ 不适用/无权限 ｜ 🧩 需功能权限组开关

| 操作域 \ 角色 | 访客 Guest | 用户 User | 开发者 Developer | 运营 Operator | 运维 SRE | 管理员 Admin | Root 超管 |
|---|---|---|---|---|---|---|---|
| **O01 公开浏览/试用** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **O02 账号身份(自身)** | ✅注册/登录 | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ |
| **O03 令牌/Key(自身)** | ➖ | 🟡✅（取明文🔒） | 🟡✅（取明文🔒） | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ |
| **O04 额度/充值/订阅(自身)** | ➖ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ |
| **O05 增长(签到/邀请)** | ➖ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ |
| **O06 异步任务(自身)** | ➖ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ | 🟡✅ |
| **O07 渠道管理** | ➖ | ➖ | ➖ | ✅（取上游Key🔒） | ➖ | ✅（取上游Key🔒） | ✅🔒 |
| **O08 计费配置** | ➖ | ➖ | ➖ | ✅ | ➖ | ✅ | ✅ |
| **O09 用户管理** | ➖ | ➖ | ➖ | ✅（2FA重置🔒） | ➖ | ✅🔒 | ✅🔒 |
| **O10 日志/审计/用量** | ➖ | 🟡✅(自助) | 🟡✅(自助) | ✅全量 | ✅全量 | ✅全量 | ✅全量 |
| **O11 运维(性能/缓存/部署/监控)** | ➖ | ➖ | ➖ | ➖ | 🧩✅ | ✅ | ✅ |
| **O12 系统设置(全站)** | ➖ | ➖ | ➖ | ➖ | ➖ | ➖/🧩部分 | ✅🔒 |

### 矩阵关键规则说明
- **self-scope（🟡）**：User/Developer 的所有数据操作只能命中**本人**资源（令牌、额度、任务、日志），由后端按 user_id 强制过滤，越权访问他人资源返回 403。
- **取明文 Key（🔒）**：无论令牌自身 Key（O03）还是上游渠道 Key（O07），取明文都走 CriticalRateLimit + DisableCache + SecureVerification 受保护路径（NFR-S02/S03）。
- **O12 系统设置仅 Root**：全站 Option、OAuth provider 配置、setup、限流、敏感词、隐私政策属 RootAuth 专属；Admin 默认无权，仅可经功能权限组（🧩）获得**只读**子集。
- **运营 vs 运维分离（🧩）**：默认 Admin = 运营∪运维全集；启用功能权限组后，Operator 仅得 O07/O08/O09，SRE 仅得 O11 + O10 只读，互不越界（最小权限）。
- **访客边界**：访客仅 O01 + O02 的注册/登录入口，绝不触达任何资源型接口。

---

## 4. 高危操作清单（强制二次验证 + 审计）

| 操作 | 角色 | 控制 | 证据 |
|---|---|---|---|
| 取令牌明文 Key | User+ | CriticalRateLimit + SecureVerification | controller/token.go |
| 取上游渠道 Key | Operator/Admin/Root | SecureVerification + 审计 | secure_verification.go |
| 改计费倍率/表达式 | Operator/Admin/Root | 审计 + 变更前后值留痕 | ratio_config.go、billingexpr |
| 封禁/重置用户 2FA | Operator/Admin/Root | SecureVerification + 审计 | user.go、twofa.go |
| 改全站 Option / 限流 / setup | Root | RootAuth + 审计 | option.go、setup.go |
| 配置 OAuth provider | Root | RootAuth + 审计 | custom_oauth.go |
| 强制 GC / 清缓存 / 部署变更 | SRE/Admin/Root | 审计 | performance.go、deployment.go |

> 所有高危操作落 NFR-S09 审计日志（操作人/时间/对象/前后值）。

---

## 5. 多租户决策（SG-006 的明确裁决）

**现状（repo 一手）**：new-api 是**单租户**模型——所有用户共享一套渠道池、一套计费倍率、一套全站 Option；隔离维度只有**用户分组（User Group）**（决定 group_ratio 与可用渠道集合）。无「组织/团队」实体，无组织级管理员、无组织级资源隔离。

### 决策建议：**分两阶段，先「轻量团队」后评估「强多租户」**

| 维度 | 建议 |
|---|---|
| **MVP（首发）** | **不引入**组织/团队多租户。沿用 repo 单租户 + 用户分组模型即可满足「面向开发者的公共 AI 网关 SaaS」定位（营销定位=个人/团队自助充值用量计费）。强行上多租户会拖慢首发且与 repo 架构冲突。 |
| **阶段二（按需）** | 引入**轻量「团队/工作区」概念**：在 User 之上加 `Team` 实体（team_id 外键挂到 User/Token/Log），实现：①团队共享额度池 ②团队级令牌可见性 ③团队 Owner/Member 两级角色 ④团队级用量账单。**复用** group_ratio 做团队折扣，**不**做渠道池隔离（渠道仍全局共享，符合「我们帮你路由」的网关定位）。 |
| **阶段三（企业版，需求驱动才做）** | 仅当出现**私有渠道隔离/独立计费主体/数据驻地隔离**的企业客户时，才升级为**强多租户**：组织级渠道池隔离 + 组织级 Root + 数据分区。此为重架构，需独立立项。 |

### 为什么不一步到位强多租户
1. repo 选渠/计费/缓存全建立在**全局渠道池**假设上（GetRandomSatisfiedChannel、channel_cache、ability 全局），强隔离需重写选渠内核——高风险。
2. 产品定位（用多少付多少、一行接入）天然偏**个人/小团队自助**，多数客户不需要组织隔离。
3. 轻量团队（阶段二）以「加 team_id 外键 + 团队角色」即可覆盖 80% 团队协作诉求，改动可控、不动核心链路。

### 多租户落地的角色扩展（阶段二预留）
| 团队角色 | 权限 | 实现 |
|---|---|---|
| Team Owner | 管理本团队成员、令牌、额度池、查本团队用量 | common 角色 + team scope + owner 标志 |
| Team Member | 用本团队额度调用、管自己的令牌、查自己用量 | common 角色 + team scope |

> 平台级 Root/Admin/Operator/SRE 角色**不变**，跨团队管理；团队角色是 User 层内部的二级权限，**不**与平台系统角色冲突。

---

## 6. 派生功能项（落 CSV part-5 的 RBAC 段）

| 功能 | function_id | 说明 |
|---|---|---|
| 三级系统角色鉴权（common/admin/root） | F-5031 | AdminAuth/RootAuth 中间件落地 |
| 功能权限组（运营集/运维集）分配 | F-5030 | root 给 admin 细分最小权限 |
| self-scope 资源越权防护 | F-5032 | 按 user_id 强制过滤 |
| 高危操作二次验证闸门 | F-5033 | SecureVerification 统一接入 |
| 角色×操作权限矩阵配置化 | F-5034 | 矩阵可后台查看/审计 |
| 团队/工作区实体（阶段二预留） | F-5035 | team_id 模型 + Owner/Member |

---

## 7. 自检小结
- **角色覆盖**：访客/用户/开发者/运营/运维/管理员/Root **共 7 个产品角色**，全部映射到 repo 的 common/admin/root 底座 + 用户分组 + 功能权限组。
- **操作覆盖**：**12 类操作域**（O01~O12），覆盖 17 个能力域。
- **矩阵规模**：7 角色 × 12 操作域 = 84 个授权单元，全部给出明确判定（含 🔒/🟡/🧩 修饰）。
- **多租户**：给出**三阶段明确建议**——MVP 不上、阶段二轻量团队、阶段三强多租户（需求驱动），并说明为何不一步到位。
- **未回避**：S1 提到的所有角色语义均落到矩阵，无「证据不足」搪塞。
