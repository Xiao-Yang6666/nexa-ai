# compliance 合规子域 — 登记说明

> 登记人：backend-dev-lead（后端开发组长）
> 分支：feat/tonight-baseline
> 路径：`backend/nexa-service/src/main/java/com/nexa/compliance/`
> 状态：此前游离未登记，现补登记。**结论：领域积木齐全且有单测，但除 F-5020 外均未接入主干。**

---

## 1. 子域做了什么

compliance 子域承载 5 个合规功能点的领域规则，分两类形态：

- **已贯通业务链路的**：F-5020 账号注销级联删除（interface → application → infrastructure 全链路接通）。
- **纯领域积木（VO + 领域服务），等待主干调用方接入的**：F-5016 数据分级、F-5017 留存策略、F-5018 跨境路由（合规分组选渠）、F-5021 同意闸门。

子域结构遵循 DDD 分层：`domain/`（vo + service + exception）、`application/`（用例 + port）、`infrastructure/`（adapter）、`interfaces/`（controller + dto + 异常处理）。

---

## 2. 文件清单（共 18 个 Java 类 / 4 个对应单测）

### domain/vo（值对象）
| 文件 | 功能点 | 说明 |
|---|---|---|
| `DataClassification.java` | F-5016 | 四级分级枚举(CREDENTIAL/PII/CONTENT/METERING) + 充血方法(加密/下发/注销处置决策)。**有单测** |
| `DataClassificationRegistry.java` | F-5016 | 核心字段分级登记清单(13 条 FieldEntry，固化数据字典) |
| `PromptRetentionPolicy.java` | F-5017 | 正文留存开关 + 保留期 VO(默认关、≤30 天、硬上限 180)。**有单测** |
| `DataResidency.java` | F-5018/F-5019 | 数据驻地 VO(境内/境外判定) |
| `Consent.java` | F-5021 | 用户同意记录 VO(条款版本 + 时刻，版本失效判定) |
| `ComplianceOptionKeys.java` | F-5017/F-5021 | Option 配置键常量 |

### domain/service（纯领域服务，零框架依赖）
| 文件 | 功能点 | 说明 |
|---|---|---|
| `ComplianceGroupPolicy.java` | F-5018 | 合规分组选渠：识别合规分组、过滤境外渠道、二次护栏 assertNotCrossBorder。**有单测** |
| `ConsentGate.java` | F-5021 | 同意闸门：requireConsent 断言用户已同意当前条款。**有单测** |

### domain/exception
`CrossBorderRoutingDeniedException` / `ConsentRequiredException` / `InvalidRetentionPolicyException`

### application
`DeactivateAccountUseCase.java`（F-5020 注销用例，@Service @Transactional）、`DeactivateAccountCommand.java`、`port/AccountDeactivationCascade.java`（级联端口接口）

### infrastructure
`cascade/CascadingAccountDeactivationAdapter.java`（@Component，实现级联端口，拼装 token/oauth/passkey/log 各 BC 仓储）

### interfaces
`api/AccountDeactivationController.java`（@RestController，DELETE /api/user/self）、`api/ComplianceExceptionHandler.java`、`api/dto/AccountDeactivationView.java`

---

## 3. 完成度评估

| 功能点 | 完成度 | 判定依据 |
|---|---|---|
| **F-5020 账号注销** | ✅ **已实现（链路接通）** | Controller→UseCase→Adapter 全链路 + Spring 注解齐全 + 异常翻译。**唯一已接主干的功能。** 缺口：2FA 级联因 twofa BC 暂无持久化仓储，硬编码计 0 并留 TODO（adapter 第 63-64 行） |
| **F-5016 数据分级** | 🟡 **半成品（登记态）** | 分级枚举 + 充血决策 + 字段登记清单完整，有单测。本就无独立端点（数据字典性质）。但加密/脱敏/注销处置主干**未引用**这些决策方法 |
| **F-5017 留存策略** | 🟡 **半成品（未接线）** | VO + 校验 + 超期判定完整，有单测。但日志写入层/归档层**没有任何代码调用** PromptRetentionPolicy（nfr 的 LogArchivalPolicy 仅 javadoc 提到"协同"，无实际调用） |
| **F-5018 跨境路由** | 🟡 **半成品（未接线）** | 选渠过滤 + 护栏完整，有单测。但 routing 选渠层**没有调用** filterAllowed / assertNotCrossBorder。DataResidency 也无实际数据源填充 |
| **F-5021 同意闸门** | 🟡 **半成品（未接线）** | ConsentGate + Consent 完整，有单测。但**没有中间件/拦截器调用** requireConsent，调用前置闸门未落地 |

**接主干验证方法**：全局搜索 `ComplianceGroupPolicy / ConsentGate / PromptRetentionPolicy / DataResidency / DataClassificationRegistry` 在 main/java 下的引用 —— 除 compliance 包自身 + nfr 一处 javadoc 注释外，**无任何 routing/log/relay 调用方**。

**单测覆盖**：4 个领域单测（ConsentGateTest、PromptRetentionPolicyTest、DataClassificationTest、ComplianceGroupPolicyTest）覆盖了纯领域规则，逻辑验证真实有效。F-5020 链路无端到端集成测试。

---

## 4. 是否进今晚生产范围 — 建议

**建议：仅 F-5020 注销链路进今晚生产范围；F-5016/17/18/21 暂不进（标记为"已就绪待接线"）。**

理由（对齐开发第一守则·反过度工程）：

1. **F-5020 可进**：全链路接通、有异常翻译、self-scope 鉴权从根保证。唯一缺口是 2FA 级联（已显式 TODO，非静默失败），twofa 仓储落地后补接即可，不阻塞注销主功能。
2. **F-5016/17/18/21 不进**：领域积木质量高、有单测，但**全部未接入主干**——上线它们等于上线一堆"无人调用的死代码"。今晚强行接线会引入未经集成测试的横切改动（改 routing 选渠 / 日志写入 / 全局同意拦截），风险与收益不匹配。
3. **下一步（非今晚）**：按"一个真实调用方"原则逐个接线 —— 选渠层接 F-5018、日志层接 F-5017、调用前置中间件接 F-5021、注销/加密处置接 F-5016 决策方法。每接一个补一条端到端集成测试。**禁止为"未来可能"提前抽象更多空壳。**
