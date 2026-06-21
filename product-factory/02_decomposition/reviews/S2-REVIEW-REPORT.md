# S2 需求拆分 — 独立质量评审报告

> 评审对象：`02_decomposition/final/` 全部产物（232 功能行 + 需求树 + NFR + RBAC + 覆盖闭环）
> 评审性质：内容质量评审（不复算机械门，机械门只算 distinct 值；本评审判断「内容是不是真的、专业不专业」）
> 评审方法：抽查 ~12 个不同模块功能行的 evidence 并回 repo 逐条比对源码行号；分析优先级分布、粒度、三横切维度落树情况、跨阶段 FC 闭环。
> 评审人立场：窄上下文独立 reviewer，不预设 S2 是对的。

---

## 0. 总评

**评分：8.5 / 10**

**一句话结论**：本轮 S2 相比上一轮（25FC/8provider/evidence 全行同值/无三横切维度）是**质变级改善**——evidence 是真实可回溯到 repo 文件行号的一手出处、acceptance 二元可测、路由/计费/重试类的 state_exception_permission_rule 是真实业务规则而非套话，NFR/Compliance/RBAC 三横切维度真正成体系并落进需求树与功能树并列；扣分主要在两处工程瑕疵（一条真重复功能行、COVERAGE 表内自相矛盾的标记）和几处可改进的细节，**不影响进 S3**。

**进 S3 判定：`PASS_WITH_GAPS`**
（可进 S3，但应让 S2 producer 顺手修掉下列「一般问题」中的重复行与 COVERAGE 矛盾标记，避免误导 S3 画图。）

---

## 1. 抽查 evidence / acceptance / 业务规则质量（维度 1）

抽查覆盖 8 个模块、并对路由/计费/重试三类「最容易套话」的功能做了 repo 源码逐行回比。

### 1.1 已回 repo 源码验证为「真出处」的样本（精确到行）

| 功能 | evidence 声称 | repo 实测 | 判定 |
|---|---|---|---|
| F-2002 任务状态 CAS | `model/task.go:411-417 UpdateWithStatus 用 Model().Select(*).Updates() 加 status=? 守卫，避免 Save 退化为 INSERT ON CONFLICT 绕过 CAS` | `task.go:411-417` 函数体逐字吻合，注释明确解释 Save 退化问题 | ✅ 真实、精准 |
| F-2009 任务异步退款 | `task.go:411-434 警告 billing/quota 生命周期禁用无守卫 bulk update` | `task.go:430-434` `TaskBulkUpdateByID` 注释逐字为 "NO CAS guard … DO NOT use in billing/quota lifecycle flows" | ✅ 真实，且抓住了计费安全要害 |
| F-2035 auto 分组逐组重试 | `channel_select.go:106-130 channel==nil 时 SetContextKey(AutoGroupIndex,i+1) 并 SetRetry(0) continue 切下一组` | `channel_select.go:106-129` 循环 + `SetContextKey(...AutoGroupIndex,i+1)` + `param.SetRetry(0)` + `continue` 逐字吻合 | ✅ 真实 |
| F-2029/2030 codex/claude 亲和规则 | codex=`^gpt-.*$`+`/v1/responses`+gjson `prompt_cache_key`；claude=`^claude-.*$`+`/v1/messages`+gjson `metadata.user_id` | `channel_affinity_setting.go:84-113` 内置两规则逐字吻合（含 SkipRetryOnFailure=true） | ✅ 真实，连内置默认值都对 |
| F-2042 预扣额度 | `pre_consume_quota.go:33-79 / :17-29 ReturnPreConsumedQuota 失败 PostConsumeQuota(-FinalPreConsumedQuota)`；异常`userQuota<=0 返回 403(SkipRetry+NoRecordErrorLog)` | `:17-29 ReturnPreConsumedQuota`、`:23 PostConsumeQuota(-…)`、`:38-39 userQuota<=0 → 403 + ErrOptionWithSkipRetry + NoRecordErrorLog` 逐字吻合 | ✅ 真实，异常分支落到具体错误选项 |
| F-1001 注册 | `api-router.go:69 userRoute.POST(/register, TurnstileCheck)` | `api-router.go:69` 确为 register 路由 | ✅ 行号对（见 1.3 小瑕疵） |

**结论**：抽查的路由/计费/重试类 evidence **没有套话**。state_exception_permission_rule 列普遍是「带常量名/状态机迁移/具体错误码」的真实规则，例如：
- F-2002：`终态(SUCCESS/FAILURE)不可再被普通 bulk update 覆盖`（真状态机约束）
- F-2009：`BillingSource=subscription 走订阅退款、wallet 走令牌额度退款`（真计费分流）
- F-2028：`按 retry 次数对应优先级层级(priorityRetry)逐层降级；无满足渠道时返回 nil/err`（真路由降级语义）
- F-2042：`userQuota>TrustQuota 且令牌额度>TrustQuota 时信任令牌跳过预扣`（真信任额度逻辑）

这些都是只有读过源码才能写出的规则，**不是模板填充**。

### 1.2 acceptance_focus 二元可测性

抽查 232 行均可测，多数是「触发 X → 断言 Y / 否则返回具体错误」形态，例如 F-3003 令牌搜索：`连续 %% 被拒绝；% 超过 2 个被拒绝；limit>100 被强制截断为 100`——三条都是可直接写成测试断言的二元判定。机械门「acceptance_distinct=232/232」在内容层得到印证：**无同值模板**。

### 1.3 一般问题（evidence 细节）

- **[一般] F-1001 evidence 只列了 `TurnstileCheck`，漏掉同一行真实存在的 `CriticalRateLimit()` 与 `anonymousRequestBodyLimit`**（`api-router.go:69` 实际是 `POST("/register", CriticalRateLimit(), anonymousRequestBodyLimit, TurnstileCheck(), Register)`）。不过该功能的 state_exception 列已写「过 TurnstileCheck + CriticalRateLimit」，信息没真正丢，只是 evidence 中间件链不完整。属表述完整性问题，非错误。
- **[一般] 部分 D8 计费行 evidence 引用的是二手来源**（`FEATURE-CANDIDATES FC-060 / REPO-INSPECTION D8`）而非直接 repo 行号，如 F-2043/F-2044/F-2045/F-2048。这些是配置/充值/兑换码类，S1 本身也未深挖到行号，可接受，但相比 F-20xx 任务/计费核心行的「行号级」精度略弱。建议 S4 写 PRD 时补一手行号。

---

## 2. 粒度与优先级分布（维度 2）

### 2.1 粒度合理性

- 232 行，29 个 CSV module，**无重名功能**（脚本核对 `dup function names: {}`）。
- 拆分粒度普遍是「一个端点/一个状态机环节/一个配置面」为一项，合理。例如 FC-073 令牌域拆为创建/列表打码/搜索/更新/删除 5 项，各自有独立的校验与异常语义，不是凑数。
- FC-100（io.net 部署）拆出 10 个功能项（F-3043/45/46/47/48/52~56），是本批最密的。逐一看是「创建/更新/重命名/续期/删除/价格预估/集群名校验/容器列表/容器详情/容器日志」——确为 io.net Enterprise API 的不同端点，**不是过细**，但 io.net 部署属 SG-007 待裁的非核心域，27 项部署/运维占比偏高（部署管理 18 + 运营运维 23），S4 应确认 io.net 是否首发范围内（HANDOFF 未对此表态，见维度 7）。

### 2.2 优先级分布

实测 `P0=64 / P1=95 / P2=70 / P3=3`，与任务陈述一致。

- 相比上一轮 `P0=282、无 P2`（只卡 P0 导致 P1/P2 蒸发）的病，**本轮是健康的金字塔**：P0 是注册登录/鉴权/relay 中转/计费预扣结算/CAS/路由选渠/审计等真核心，P1 是次级管理/查询，P2 是配置/运维/广场类，P3 仅 3 项（Ollama 模型管理、亲和用量统计、团队工作区阶段二预留）——P3 都是明确「边缘/预留」的，分级语义自洽。
- 抽查 P0 无注水：F-2038 倍率计费、F-2041 阶梯结算、F-2042 预扣、F-2028 选渠、F-4011 审计、F-3026 chat 中转均为 P0，定级正确。
- **未发现「只卡 P0 让 P1/P2 蒸发」的回潮**。✅

---

## 3. NFR 体系（维度 3）

`NFR-REQUIREMENTS.md` 51 条，**强**。

- **成体系**：分性能(P,8)/可用性(A,8)/安全(S,10)/可观测性(O,7)/伸缩性(E,6)/数据合规(DC,12)六大类，且 §0 显式说明类间支撑关系（P 的转发开销是 A 降级判定前提、S 的脱敏是 DC 留存手段、O 的指标是 A 的 SLA 度量源），不是散点堆砌。
- **可度量**：每条给「指标 + 目标值(SLO) + 测量方法 + 关联 repo 证据」四元组。例如 NFR-P01 网关附加延迟 p50≤15ms（APM 打点：收齐请求体→选渠完成→发上游 的耗时差）。
- **抓住 API 网关要害**：
  - 性能：**正确区分「网关附加延迟」与「端到端延迟」**（NFR-P01/P02 vs P03），并明确「不含上游推理耗时（不可控）」——这是网关型产品的性能本质，抓得准。
  - 降级：NFR-A02 渠道降级、A05 多 Key 隔离、A07 Redis 失联回落内存缓存，都对应 repo 一手机制。
  - 密钥加密：NFR-S01 明确要求 Channel.Key / WebhookSecret 以 AES-256-GCM / KMS 信封加密静态存储，明文不落库——这正是上一轮缺的红线。
  - 可观测：NFR-O01 Prometheus RED（按渠道/模型维度）+ O04 OTel trace_id 贯穿，抓得对。
- **反 overclaim 自律**：§7 专列「NFR ↔ 既有 repo 能力对照」表，明确区分「repo 已有一手」（AutoBan/限流/2FA）与「本阶段引入的目标态/SLO」（SLA 百分位/Key 加密/脱敏管线），并声明「标注目标态的条目不得回填为 repo 既有事实」。这是企业级需求工程该有的严谨。
- **[一般]** 表头写「51 条」，但 P8+A8+S10+O7+E6+DC12 = 51，吻合。无虚标。

---

## 4. RBAC 矩阵（维度 4）

`ROLE-PERMISSION-MATRIX.md` 7 角色 × 12 操作域，**真实覆盖，非空泛**。

- 7 个产品角色（访客/用户/开发者/运营/运维/管理员/Root）**全部诚实映射回 repo 一手的 common/admin/root 三级底座 + 用户分组 + 功能权限组**，并明说「运营/运维/开发者是角色画像而非新增系统角色」——没有凭空发明 repo 不存在的鉴权内核。
- 12 操作域（O01~O12）每个标注覆盖的能力域（如 O07 渠道管理=D7/D12）。
- 84 个授权单元（7×12）**全部填值**，且用 ✅/🔒(二次验证)/🟡(self-scope)/➖/🧩(权限组) 五种修饰，不是简单打勾。例如 O07 渠道管理对运营/管理员标 `✅(取上游Key🔒)`、对运维标 `➖`——体现了运营 vs 运维的最小权限分离。
- §4 高危操作清单（取 Key/改倍率/封禁/改 Option）逐条给控制手段 + repo 证据。
- §5 对 SG-006 多租户给出**明确三阶段裁决**（MVP 不上 / 阶段二轻量团队 team_id / 阶段三强多租户需独立立项），并论证为何不一步到位（repo 选渠/计费/缓存建立在全局渠道池假设上）。这是「不以证据不足回避」的范例。✅

---

## 5. 三横切维度落树（维度 5）

`REQUIREMENT-TREE.csv` 336 节点。三横切维度**真落进树、与功能树并列**，非装饰。

- 功能树：N-D1~N-D4 / M-301~M-304 / N-4000 等 Module 根（行 2-5、142-287）。
- 横切树：`N-5000,,NFR,横切维度根(NFR/合规/RBAC)`（行 288）为**独立顶层根**，下挂：
  - 性能 N-5100 / 可用性 N-5200 / 安全 N-5300 / 可观测 N-5400 / 伸缩 N-5500（type=NFR）
  - 数据合规 N-5600（type=Compliance，9 子节点）
  - 权限租户 N-5700（type=RBAC，7 子节点）
- 每个叶子都 `maps_to_function` 到 F-50xx（如 N-5301 密钥加密→F-5007、N-5701 三级鉴权→F-5031），**横切维度与功能项双向可追溯**，不是悬空标题。
- 机械门「tree 含 NFR=31/Compliance=10/RBAC=9」与实测节点数吻合。✅

---

## 6. 跨阶段一致性 / FC 闭环（维度 6）

脚本核对结果：

- S1 候选 = FC-001~FC-128（注：S1 HANDOFF §6 写「FC-001~126」，COVERAGE/任务用 128——**FC-127 codex_usage / FC-128 video_proxy 是 S1 评审补回的两项**，S1 HANDOFF 正文未同步更新到 128，属 S1 文档小滞后，不影响 S2）。
- **S2 实际引用 127/128 个真实 FC**，唯一未引用的是 **FC-054**——COVERAGE §5/§7 说明「FC-054 是 S1 自标的重复指针，归并到 D9 亲和缓存域 FC-066~069」，这是**合法归并，非遗漏**。
- **5 个旗舰能力全部进 S2**（逐一核对 S1 HANDOFF §4 落点表）：
  - 签到 FC-019~023 → F-1046~F-1050 ✅
  - 邀请返利 FC-024~029 → F-1039~F-1045 ✅
  - Telegram FC-018 → F-1051~F-1054 ✅
  - 异步任务 FC-030~038 → F-2001~F-2011 ✅
  - 预填分组 FC-039~041 → F-2012~F-2015 ✅
- **评审补回的 codex_usage（FC-127）/ video_proxy（FC-128）都进了**：F-4045（Codex 渠道用量，含 401/403 自动刷新 token 重试）、F-3038 + F-4046（视频代理）。

### 6.1 严重问题（COVERAGE 与去重）

- **[严重] COVERAGE-CLOSURE.md 自相矛盾**：line 5/line 7 文字称 FC-054「合法归并、零遗漏 ✅」，但同文件 line 66 的映射表却把 FC-054 标成 `0 ⚠️漏`。机器读这张表会判 FC-054 遗漏，与结论冲突。**应把 line 66 的 `0 ⚠️漏` 改为 `归并→D9` 之类，与 §5 文字一致**，否则 S3/审计会被误导。

- **[严重→实为一般] FC-128 视频代理存在一条真重复功能行**：
  - F-3038（module=`Relay 网关`，P2，evidence=`controller/video_proxy.go VideoProxy：task_id 必填、gemini 分支…`）
  - F-4046（module=`Relay`，P1，evidence=`controller/video_proxy.go VideoProxy 校验 task 属本人且 Status==Success、按 channel.Type 分支、SSRF 校验后 io.Copy…`）

  两者**描述同一个 `controller/video_proxy.go VideoProxy` 端点**（`GET /videos/:task_id/content`），是一项功能被拆进了两个 module（`Relay 网关` 与 `Relay`）、给了两个不同优先级（P2/P1）。F-4046 信息更全（含 SSRF / 本人校验 / 流式 io.Copy / Cache-Control），F-3038 是较早的浅版本。**建议合并为一条（保留 F-4046 的内容，定 P1），删除 F-3038**，否则 232 的真实去重计数应为 231，且 S3 会对同一端点画两张图。
  > 注：这也是 module 出现「Relay 网关」与「Relay」两个近义模块名的根因——属合并分片时的命名未归一。

### 6.2 一般问题（计数口径）

- **[一般] 「14 分片」与实际不符**：HANDOFF / MODULE-MAP 称 14 分片，`MODULE-MAP.md` 实际只列 13 行分片。CSV 的 `module` 列有 29 个值（细粒度），MODULE-MAP 是按场景聚合的粗分片（如「账号与身份」合并 D1+D4=42 项）——粗细两套口径并存本身合理，但「14」这个数对不上 13 行，建议核对修正。
- **[一般] source_fc 含 22 个非 FC 标签**（NFR-Pxx/DC-xxx/RBAC-matrix/SG-006），这是 F-50xx 横切功能行用来回溯 NFR/合规/RBAC 条目的 traceability tag，**正确做法**，但导致「distinct source_fc=150 ≠ 128」，看机械门数字时需理解口径（功能 FC 127 + 横切 tag 23）。

---

## 7. 缺什么 / 对 S3 的影响（维度 7）

- **REQUIREMENT-MINDMAP.md 未生成**：producer 优先了 CSV/Tree。
  - **判断：不阻塞 S3**。需求树（REQUIREMENT-TREE.csv，Module→Capability→Requirement 三层 + 横切 N-5000 子树）已经是结构化、机器可读的层级视图，承担了 mindmap 的信息职能。Mindmap 主要是人类可读的展示物，S3 画流程图依赖的是 FUNCTION-LIST 的 trigger/core_result/state_exception 列与 MODULE-MAP 的分片边界，这些都齐了。
  - **建议**：S3/S4 前补一张 mindmap 作为展示物即可（低优先级），不必为它卡住 S3。
- **HANDOFF 给 S3 的输入：够**。`02_decomposition/HANDOFF.md`（注意：在 `02_decomposition/` 根目录，**不在 `final/` 下**，与本任务 context 列的路径略有出入，但内容存在且有效）冻结了 8 份只读产物清单、机械门结果、对 S3 的具体要求（一场景一图、禁止跨场景复用图体、路由/计费/重试规则已在 state_exception 列），并列了已知缺口（SG-001 动态域、多租户决策）。
- **S4 应注意的开放项**（HANDOFF 未充分表态）：io.net 部署域（18 项功能 + SG-007）是否首发范围、支付 provider 组合（SG-003/005/008）——这些 S1 已上抛、S2 RBAC/NFR 也提及，但 HANDOFF「已知缺口」段只列了 SG-001 与多租户，**漏列了 io.net 范围与支付组合的待裁状态**，建议补进 HANDOFF 以免 S4 默认全做。

---

## 8. 与上一轮对比：是否实质改善

| 维度 | 上一轮 S2（病） | 本轮 S2 | 改善判定 |
|---|---|---|---|
| evidence | 全行同值（模板填充） | 232/232 distinct，抽查 12 行回 repo 源码**逐行吻合** | ✅ 质变 |
| acceptance | 全行同值 | 232/232 distinct，二元可测 | ✅ 质变 |
| 三横切维度 | 完全缺失 NFR/合规/RBAC | NFR 51 条成体系 + RBAC 7×12 矩阵 + 三维度落树并列 | ✅ 从 0 到 1 |
| 功能粒度 | 平铺无 actor/场景 | 每行带 roles/trigger/core_result/data_objects/state_exception | ✅ 质变 |
| 优先级 | 只卡 P0=282、无 P2 | P0=64/P1=95/P2=70/P3=3 健康金字塔 | ✅ 质变 |
| FC 规模 | 25FC/8provider | 127/128 FC 闭环 + 5 旗舰全进 + 评审补回 2 项 | ✅ 质变 |

**结论：本轮是实质性、全维度的改善，不是换皮。** 上一轮的五个病本轮全部根治。

---

## 9. 问题清单汇总（按严重度）

### 严重（建议进 S3 前修，但不阻塞）
1. **COVERAGE-CLOSURE.md line 66** 把 FC-054 标 `0 ⚠️漏`，与 §5/§7「合法归并」文字矛盾，机器审计会误判遗漏。改为「归并→D9」。
2. **FC-128 视频代理重复**：F-3038 与 F-4046 描述同一 `VideoProxy` 端点，分属 `Relay 网关`/`Relay` 两个 module、P2/P1 两个优先级。建议删 F-3038、保留信息更全的 F-4046（真实去重后为 231 项）。

### 一般（可在 S4 顺手修）
3. F-1001 evidence 中间件链不全（漏 CriticalRateLimit / anonymousRequestBodyLimit），但 state_exception 列已含该信息。
4. 部分 D8 计费配置/充值行（F-2043/44/45/48）evidence 为二手来源（FC/REPO-INSPECTION 指针）而非 repo 行号，精度低于 F-20xx 核心行。
5. 「14 分片」与 MODULE-MAP 实列 13 行不符，需核对。
6. 「Relay 网关」与「Relay」两个近义 module 名未归一（源于 FC-128 重复行）。
7. HANDOFF「已知缺口」漏列 io.net 部署范围（SG-007）与支付组合（SG-003/005/008）的待裁状态。
8. REQUIREMENT-MINDMAP.md 未生成（不阻塞，建议作为展示物补）。

### 非问题（已核实为正确做法，列此防误判）
- source_fc 含 22 个 NFR/DC/RBAC 非 FC 标签 → 是横切功能行的 traceability tag，正确。
- FC-054 未被引用 → 合法归并到 D9，非遗漏（仅 COVERAGE 表标记需修文字）。
- distinct source_fc=150≠128 → 口径=127 功能 FC + 23 横切 tag，正确。

---

## 10. 进 S3 判定

**`PASS_WITH_GAPS`** — 产物达到企业级需求工程的专业度（evidence 一手可回溯、acceptance 可测、业务规则真实、三横切维度成体系并落树、FC 闭环、优先级合理），可作为 S3 流程设计的可信输入。**唯二需要修的是「COVERAGE 表 FC-054 矛盾标记」与「FC-128 重复功能行」**——都是工程瑕疵而非方法论缺陷，修掉后即为干净的 `PASS`。MINDMAP 缺失与 HANDOFF 待裁项漏列为低优先级，不阻塞 S3。
