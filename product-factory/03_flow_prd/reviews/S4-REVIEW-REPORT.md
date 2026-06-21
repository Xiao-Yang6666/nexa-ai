# S4 PRD 独立业务质量评审报告

> 评审对象：`03_flow_prd/final/`（15 分片 / 87 个 PRD 块、DATA-MODEL.md、FLOW-PRD-TRACEABILITY.csv 196/231）
> 评审视角：独立 reviewer，窄上下文，不假设 S4 对。项目 = 基于 newapi 的 AI API 网关 SaaS。
> 机械门已过（slop ratio 0.0 / 占位句 0）。本轮重点：七段是否真实质、能否驱动 S5 UI / S6 原型、35 未覆盖是真漏还是合法归并。
> 评审方式：抽查 8 个分片逐块看七段；35 未覆盖逐个粗判；2 处验收去 repo 核对。**只评审，未修改任何产物。**

---

## 一、总评分与结论

**S4 评分：8.5 / 10**

**进 S5 判定：PASS_WITH_GAPS**

S4 相比上一轮是**实质性、断崖式的改善**，不是修补。抽查的每一个 PRD 块七段都达到「可驱动 UI/原型」的质量线：场景具体、主流程节点与流程图一一对应、分支严格对应判定菱形（无脑补）、数据对象逐字段引用 DATA-MODEL 真实字段（含 GORM tag / validate / index）、验收二元可测（去 repo 核对的 2 条均与真实代码吻合）、页面状态对齐矩阵。旗舰 5 功能 PRD 完整且深度足够。

不给满分、不给纯 PASS 的原因：**35 个 `prd_covered=no` 的 F-id 中，约 18 个是真漏**（其中含 1 个 P0、4 个 P1），其余约 17 个是合法归并或可接受的边缘省略。这些漏项是「管理端 CRUD / 配置类功能缺独立 PRD 块」，不阻断核心链路进 S5，但需在 S5 前补块或显式登记降级，否则对应 UI 页面将无 PRD 依据。

---

## 二、七段质量抽查（8 分片逐块）

抽查分片：prd-account、prd-billing、prd-channel、prd-relay(头)、prd-token、prd-model、prd-nfr-rbac、prd-public(头)。

| 维度 | 评定 | 证据 |
|---|---|---|
| 场景具体 | ✅ 优 | 每块场景段是「业务为什么这样设计」的散文，非模板。如 `prd-account.md:71` AC-2「不能只看凭证是否正确：被封禁账号即使密码对也必须拒登」；`prd-billing.md:66` BL-2「冻结额度→真实消费→差额回补的资金生命周期」。各不相同。 |
| 主流程从流程图节点来 | ✅ 优 | 主流程逐步标注流程图节点号，与 `flow/FL-*.md` 一一对应。如 `prd-account.md:26-33`（R0→OK）对齐 `FL-account.md:14-34` 的 R0/R1/R3/R9/R10/R13；`prd-channel.md:74-77`（sc_req→sc_sel）对齐 FL-channel CH-2 节点。 |
| 分支对应判定菱形（不脑补） | ✅ 优 | 分支表「流程图节点」列直接引用菱形分叉。如 `prd-account.md:38-43` 六条分支对应 R1/R5/R7/R9/R10；`prd-channel.md:131-137` CH-3 七条对应状态机迁移节点。未见脑补分支。 |
| 数据对象字段级 | ✅ 优 | 见第四节。逐字段带真实 GORM tag。 |
| 验收二元可测 | ✅ 优 | 见第五节 + repo 核对。 |
| 页面状态对齐矩阵 | ✅ 良 | 每块第 7 段标注「对齐 §X」并列具名状态。如 `prd-account.md:58`「对齐 §B 注册表单」、`prd-token.md:197`「对齐 §D 令牌密钥访问」。未逐一回查 PAGE-STATE-MATRIX 全量字段，抽样标注一致。 |

**七段整体结论：真实质，可驱动 S5 UI 与 S6 原型。** 上一轮「5 段里 4 段逐块字节复制」的问题在抽查范围内**完全不复现**。

---

## 三、35 个未覆盖 F-id 逐个粗判（真漏 vs 合法归并）

> 重要前置发现：`FLOW-COVERAGE.csv` 显示这 35 个 F-id **全部 `covered_by_flow=yes` 且 `has_screen_states=yes`**——即它们在 S3 流程层都有落点与屏幕状态。`FLOW-PRD-TRACEABILITY.csv` 标 `prd_covered=no` 的真实含义是：**S4 PRD 的某个块的 `本片覆盖功能 ID` 清单里没有列这个编号**。因此判定标准为：该 F-id 的业务行为是否被某个现有 PRD 块的正文实际描述（合法归并）还是无任何块描述（真漏）。

### D1 账号（15 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-1012 角色分级(root/admin/common) P0 | **合法归并** | `prd-nfr-rbac.md:297-344` X7 RBAC 以三级系统角色为底座，越权 403 可测，完整含住。 |
| F-1015 OAuth state(CSRF) P0 | **合法归并** | `prd-account.md:168` AC-4 主流程 O1「GET /oauth/state 生成 CSRF 暂存 aff」+ `:180/:195` state 不匹配 403 验收，已含住。 |
| F-1008 管理端查询/搜索用户 P1 | **真漏（一般）** | AC-10（`:446`）只覆盖 ManageUser 启用/禁用/提升/删除，无 list/search。`SearchUsers` 未在任何块出现。 |
| F-1009 管理端创建用户 P1 | **真漏（一般）** | `CreateUser` 未在任何块出现。 |
| F-1013 用户分组绑定 P1 | **真漏（一般）** | 管理端为用户设 group。token TK-5/pricing 用到 group 字段，但「管理端绑定分组」动作本身无块描述。 |
| F-1011 更新用户资料(管理端) P2 | **真漏（一般）** | `UpdateUser` 未出现。 |
| F-1014 用户备注与个人设置 P2 | **真漏（一般）** | Remark 隐藏 / UserSetting 未出现。 |
| F-1017 GitHub legacy_id 迁移 P2 | **真漏（轻微）** | AC-4 未含 legacy_id→数字 ID 迁移容错。边缘项。 |
| F-1023 自定义 OAuth discovery P2 | **真漏（一般）** | FetchCustomOAuthDiscovery 未出现。 |
| F-1024 自定义 OAuth provider CRUD P2 | **真漏（一般）** | CustomOAuthProvider CRUD 未出现（AC-4 只覆盖内建 + 已配置 provider 的登录绑定）。 |
| F-1030 Passkey 二次验证(verify) P2 | **真漏（一般）** | AC-9 覆盖 register/login/delete，无 verify/begin\|finish 敏感动作二次校验。 |
| F-1032 管理端重置用户 Passkey P2 | **真漏（一般）** | AdminResetPasskey 未出现（X7 高危清单列「重置 2FA」非 passkey）。 |
| F-1034 2FA 关闭 P2 | **真漏（一般）** | AC-8 覆盖启用 + 登录二次校验，无 Disable2FA。 |
| F-1035 2FA 备份码生成 P2 | **部分归并/轻微漏** | AC-8 验收（`:392`）提到备份码核销，但 RegenerateBackupCodes（重新生成、旧失效）无独立描述。 |
| F-1037 管理端 2FA 统计与禁用 P2 | **部分归并/轻微漏** | X7 高危列「重置/封禁用户 2FA」含 AdminDisable2FA；但 GET 2fa/stats 统计无块描述。 |

D1 真漏小计：**约 11 个**（无 P0；P1 3 个：F-1008/F-1009/F-1013；其余 P2 边缘）。

### D7 渠道（7 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-2022 状态码映射 P2 | **部分归并** | CH-3（`:142`）将 StatusCodeMapping 作为自动禁用触发源描述，字段已现；但「配置 524→500 映射」的管理动作本身无独立验收。 |
| F-2017 渠道连通性测试 P1 | **真漏（一般）** | channel-test / unsupportedTestChannelTypes 未出现。 |
| F-2018 上游余额查询更新 P2 | **真漏（一般）** | UpdateChannelBalance 未出现。 |
| F-2019 按 tag 批量启停 P1 | **真漏（一般）** | EnableChannelByTag/DisableChannelByTag 未出现（CH-3 仅提手动禁用不自动恢复，无 tag 批量入口）。 |
| F-2025 参数/请求头覆写 P2 | **真漏（一般）** | ParamOverride/HeaderOverride 配置未出现。 |
| F-2026 上游模型探测与同步 P2 | **真漏（一般）** | fetch_models / detect & apply 未出现（注意：ML-2 是「上游官方库同步」非「渠道侧探测」，二者不同）。 |
| F-2027 Ollama 模型管理 P3 | **真漏（轻微）** | Ollama pull/delete/version 未出现。P3 边缘。 |

D7 真漏小计：**约 6 个**（P1 2 个：F-2017/F-2019）。

### 亲和缓存（4 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-2030 codex/claude header 透传模板 P1 | **合法归并** | CH-4（`:160/:192`）描述内置 codex/claude 规则 + pass_headers + SkipRetryOnFailure=true，含住机制。 |
| F-2031 亲和缓存策略配置 P1 | **合法归并** | CH-4 数据对象段（`:193`）逐字段列 Enabled/SwitchOnSuccess/MaxEntries/DefaultTTLSeconds/TTLSeconds，策略已描述。 |
| F-2032 清空亲和缓存 P2 | **真漏（一般）** | ClearChannelAffinityCache(all/rule_name) 管理动作无块描述。 |
| F-2033 亲和缓存用量统计 P3 | **真漏（轻微）** | GetChannelAffinityUsageCacheStats 无块描述。P3 边缘。 |

亲和真漏小计：**约 2 个**（无 P0/P1）。

### D8 计费（4 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-2039 缓存倍率计费(cache_ratio) P1 | **合法归并** | BL-5（`:248`）验收明确 cr 缓存读 token 从 p 自动扣除防重复计价，子类排除已含。 |
| F-2048 公开价格页/价格暴露 P2 | **合法归并** | `prd-model.md:158` ML-4 公开价格页（GetPricing/expose 逻辑），跨片含住。 |
| **F-2038 倍率计费(model×group×completion) P0** | **真漏（严重）** | 基础倍率计费公式 = 模型倍率×分组倍率×补全倍率。BL-5 仅在「`BillingMode!=tiered_expr` 走旧倍率结算」一句引用旧倍率路径（`:250`），BL-2 覆盖预扣/结算资金流，但**没有任何块把「基础倍率三因子相乘 + completion_ratio 单独加权」作为主体功能给出可测验收**。这是 P0 功能，被两个块的边缘引用「夹住」但无正面 PRD。**最该补的一块。** |
| F-2043 倍率配置管理与同步 P2 | **真漏（一般）** | ratio_config/ratio_sync（远端同步、预览覆盖）无块描述。 |

D8 真漏小计：**2 个**（含 **P0 F-2038**）。

### D9 模型/供应商（5 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-3013 模型元数据列表(分页+供应商计数) P1 | **真漏（一般）** | ML-1 只覆盖 create/update，GetAllModelsMeta/enrichModels/vendor_counts 无块。 |
| F-3014 模型元数据搜索 P1 | **真漏（一般）** | SearchModelsMeta 无块。 |
| F-3017 模型元数据删除(刷新定价) P2 | **真漏（一般）** | DeleteModelMeta 无块（ML-1 末段 RefreshPricing 是 create/update 触发，非 delete）。 |
| F-3018 供应商元数据 CRUD(名称唯一) P1 | **真漏（一般）** | ML-2 同步时按需创建 Vendor，但 GetAllVendors/Create/Update/Delete + IsVendorNameDuplicated 的管理 CRUD 无独立块。 |
| F-3022 模型广场(period 快照) | （已覆盖，列于 ML-5，CSV 标 yes） | — |

D9 真漏小计：**4 个**（P1 3 个：F-3013/F-3014/F-3018）。

### 运维（1 个未覆盖）

| F-id | 功能 | 判定 | 依据 |
|---|---|---|---|
| F-4037 额度预警通知配置(email/webhook/bark+阈值正数校验) P1 | **部分归并/真漏（一般）** | X4（`prd-nfr-rbac.md:181`）有平台侧 `alert.quota_warn_threshold` 告警编排；但**用户侧**个人设置选通知方式 + warningThreshold 正数校验 + webhookSecret Bearer 的配置块缺失。用户 UI 无 PRD。 |

运维真漏小计：**1 个**（P1）。

### 35 未覆盖判定汇总

| 类别 | 合法归并 | 真漏 |
|---|---|---|
| D1 账号 15 | 2（F-1012/F-1015） | ~11（其中部分 P2 边缘）|
| 渠道 7 | 0（F-2022 部分） | ~6 |
| 亲和 4 | 2（F-2030/F-2031） | 2 |
| 计费 4 | 2（F-2039/F-2048） | 2（含 P0 F-2038）|
| 模型 4 | 0 | 4 |
| 供应商 1 | 0 | （并入模型 F-3018）|
| 运维 1 | 0 | 1（F-4037）|
| **合计 35** | **约 8** | **约 18** |

**真漏定级：**
- **严重（1）**：F-2038 P0 基础倍率计费——核心计费主路径无正面 PRD 块，必须补。
- **一般（约 13）**：管理端 CRUD/配置类——F-1008/F-1009/F-1013（账号 P1）、F-2017/F-2019（渠道 P1）、F-3013/F-3014/F-3018（模型 P1）、F-4037（运维 P1）及若干 P2。对应 S5 会有管理页但无 PRD 依据。
- **轻微（约 4）**：F-1017/F-2027/F-2033 等 P2/P3 边缘项，可登记降级。

---

## 四、数据对象段字段级核验（对比上轮 330 占位句）

**结论：彻底改善，0 占位，全部字段级，引用 DATA-MODEL 真实字段。**

证据（抽样）：
- `prd-account.md:46` AC-1 写 User：`Username(username)`(unique,validate:max=20)、`Password(password)`(validate:min=8,max=20,存哈希)、`AffCode(aff_code)`(4 位 uniqueIndex)、`InviterId(inviter_id)`——带 GORM tag + validate。
- `prd-channel.md:42` CH-1 写 Channel：`Priority(priority) *int64`(default:0)、`Weight(weight) *uint`(default:0)、`ModelMapping *string text`——真实指针类型与默认值。
- `prd-billing.md:43` BL-1 TopUp：`TradeNo(trade_no)`(unique 幂等键)、`PaymentMethod`(stripe/creem/waffo/...)枚举值具体。
- `prd-model.md:42` ML-1 Model：`ModelName`(gorm:size:128;not null;uniqueIndex:uk_model_name_delete_at,priority:1)——完整复合唯一索引名。
- `prd-token.md:233` TK-5 Token：`AllowIps(allow_ips) *string`(按 \n 切分)、`CrossGroupRetry`、`ModelLimits`(text JSON) + 解析方法 `GetModelLimitsMap()`。

上一轮「数据对象 330 处同一句占位」问题**完全消除**，每块数据对象都是该块独有的字段子集 + 写/读/校验区分。

---

## 五、验收标准可测性核验（含 repo 核对）

**结论：二元可测，非「功能正常」式空话。** 抽样验收均为「给定输入 → 期望可观测输出」，且去 repo 核对的 2 条与真实代码吻合。

repo 核对 1 —— BL-2 预扣验收（`prd-billing.md:97`「User.Quota<=0 → 403 SkipRetry」）：
- repo `service/pre_consume_quota.go:38-39` 确证 `if userQuota <= 0 { ... http.StatusForbidden, ErrOptionWithSkipRetry(), ErrOptionWithNoRecordErrorLog() }`，`:45` `GetTrustQuota()` 旁路。**完全吻合。**

repo 核对 2 —— CH-2 选渠 + CH-3 channel test（`prd-channel.md:61` 引用 `GetRandomSatisfiedChannel(group,model,retry)`；F-2017 引用 unsupportedTestChannelTypes）：
- repo `model/channel_cache.go:97` 确证函数签名 `GetRandomSatisfiedChannel(group string, model string, retry int)`。
- repo `controller/channel-test.go:79-91` 确证 unsupportedTestChannelTypes = Midjourney/MidjourneyPlus/SunoAPI/Kling/Jimeng/Vidu，返回 `"%s channel test is not supported"`。**完全吻合。**

其他可测样例：`prd-token.md:194` TK-4「批量 ids 长度=101 → MsgBatchTooMany{Max:100}」、`prd-token.md:238` TK-5「ModelLimits=[gpt-4o] → 调 gpt-3.5 被拒、gpt-4o 放行」、`prd-account.md:54`「携有效 aff_code → InviterId=邀请人 Id(≠0)」——均带具体值，可写成自动化用例。

上一轮「验收不可测」问题**已解决**。

---

## 六、跨阶段连续性

- **S3 77 场景 → S4 PRD 落点**：S3 flow 共 77 个 `## 场景`（14 个 FL 文件），S4 PRD 共 87 个验收块。逐片对应：每个 FL 场景在对应 prd 文件有同节点号的 PRD 块；account 多出 AC-12 登出块（11 flow→12 PRD）。**77 场景全部有 PRD 落点。**
- **5 旗舰功能 PRD 完整性**：
  1. 阶梯/表达式计费 → `prd-billing.md:207` BL-5（billingexpr 变量/版本/快照/子类排除/负值归零，6 条验收）✅ 完整
  2. 令牌密钥掩码+受控取明文 → `prd-token.md:157` TK-4 + `prd-nfr-rbac.md:101` X3（reveal/CriticalRateLimit/DisableCache）✅ 完整
  3. 亲和缓存渠道粘连 → `prd-channel.md:157` CH-4（命中复用/SwitchOnSuccess/SkipRetryOnFailure）✅ 完整
  4. 跨分组重试 → `prd-channel.md:208` CH-5（priorityRetry/RetryTimes/AutoGroupIndex 嵌套循环）✅ 完整
  5. RBAC 7×12 矩阵 → `prd-nfr-rbac.md:297` X7（self-scope/功能权限组/二次验证/矩阵审计）✅ 完整

旗舰功能 PRD **均完整且深度足够驱动原型**。

---

## 七、与上一轮对比（是否实质改善）

| 维度 | 上一轮 | 本轮 | 改善 |
|---|---|---|---|
| slop ratio | 75% | 0.0 | 实质 |
| 占位句 | 330 | 0 | 实质 |
| 数据对象 | 空/330 处同句占位 | 全字段级 + GORM tag | 实质 |
| 验收可测 | 不可测 | 二元可测，repo 核对吻合 | 实质 |
| 七段独立性 | 5 段中 4 段字节复制 | 抽查无复制，块块独有 | 实质 |

**本轮是实质改善，非表面修补。** 唯一遗留是覆盖广度（管理端 CRUD/配置块缺失），属增量补块问题，不是质量倒退。

---

## 八、进 S5 前建议（按优先级）

1. **[严重·必补]** F-2038 基础倍率计费（model_ratio×group_ratio×completion_ratio）补一个独立 PRD 块——P0 核心计费主路径目前无正面 PRD。
2. **[一般·建议补]** 管理端 CRUD 块：账号（搜索/创建/更新/分组绑定 F-1008/09/11/13）、渠道（连通测试/tag 批量/余额 F-2017/19/18）、模型（列表/搜索/删除/供应商 CRUD F-3013/14/17/18）、用户额度预警配置（F-4037）。这些 P1 项对应 S5 会出页面但无 PRD 依据。
3. **[轻微·可登记降级]** F-1017/F-2027/F-2032/F-2033/F-1023/F-1024/F-1030/F-1032/F-1034/F-1035/F-1037/F-2025/F-2043——P2/P3 边缘，可在 traceability 显式标注「S5 不实现」或排期延后，避免「静默漏」。
4. **[文档一致性]** FLOW-PRD-TRACEABILITY.csv 的 `prd_covered=no` 与各 PRD 块 `本片覆盖功能 ID` 清单口径应统一：建议对「合法归并」项（如 F-1012→X7、F-2048→ML-4、F-2039→BL-5）在 csv 补 `prd_file` + 备注归并块，以免误判为漏。

---

## 评审签字

S4 七段为真实质，旗舰功能完整，验收经 repo 核对可信，可进 S5。35 未覆盖中约 18 个为真漏（1 严重 P0 / 约 13 一般 / 约 4 轻微），均为「缺独立块」而非「质量崩坏」，不阻断核心链路。**判定 PASS_WITH_GAPS：补 F-2038 与 P1 级管理端块后即可全量进 S5。**
