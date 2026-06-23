# T6 · compliance 子域登记说明

> 出文档：2026-06-22 夜（编排总控复核代笔，基于 baseline d8f2bf0 实测代码）
> 目的：把"有人新做了一整块 compliance 合规子域但未登记"这件事归位——说清它做了什么、完成度、是否进本轮生产范围、纳入 REQ 表。

## 1. 它是什么 / 在哪

`backend/nexa-service/src/main/java/com/nexa/compliance/` —— 一个独立的 DDD 子域（domain/application/infrastructure/interfaces 四层齐全），共 **21 个 main 文件 + 21 个单测**（实测 `@Test` 计数 21，全部在 baseline 中已编译通过）。

对应审计需求编号区间 **F-5016 ~ F-5021**（数据分级 / 留存 / 跨境路由 / 账号注销 / 同意闸门），来源 `API-ENDPOINTS §14.5`。注意：这一段**不在原审计 17 条 REQ（REQ-01~17）里**，是额外冒出的合规块，本文档负责把它登记归位。

## 2. 做了什么（按 F 编号）

| F 编号 | 主题 | 代码载体 | 完成度 |
|---|---|---|---|
| F-5016 | 数据分级 | `domain/vo/DataClassification` + `DataClassificationRegistry` + test | 领域模型完成（分级值对象 + 注册表），**未接入转发主干** |
| F-5017 | prompt/响应正文留存策略 | `domain/vo/PromptRetentionPolicy`（默认关，保留期默认≤30天，硬上限）+ `InvalidRetentionPolicyException`(→400) + `ComplianceOptionKeys`(Option 键) + test | 领域规则 + 配置键 + 异常翻译完成，**留存写入链路未接入** |
| F-5018 | 跨境/数据驻地路由 | `domain/vo/DataResidency` + `domain/service/ComplianceGroupPolicy`（合规分组仅命中境内驻地渠道）+ `CrossBorderRoutingDeniedException`(→403) + test | 领域策略完成，**未接入 relay 选渠/路由主干** |
| F-5020 | 账号注销级联删除 | `interfaces/api/AccountDeactivationController`(@RestController, DELETE /api/user/self) + `application/DeactivateAccountUseCase` + `infrastructure/cascade/CascadingAccountDeactivationAdapter`(级联清 token 等) + View | **唯一有 live REST 端点的 F**，链路完整（接口→用例→级联适配器） |
| F-5021 | 同意闸门 | `domain/vo/Consent` + `domain/service/ConsentGate` + `ConsentRequiredException`(→403) + `ComplianceOptionKeys.termsVersion` | 领域闸门 + 条款版本配置完成，**调用前置校验未接入转发主干** |

外加 `interfaces/api/ComplianceExceptionHandler`（@RestControllerAdvice，把上述领域异常翻译成 403/400/404 + 错误信封）。

## 3. 完成度结论（一句话）

**"自治子域已建成、但与命脉转发链路尚未缝合"。**
- 已成：完整的合规领域模型 + 21 单测全绿 + 异常→HTTP 翻译；账号注销(F-5020)有 live 端点、链路闭环。
- 未成（关键缺口）：F-5017 留存 / F-5018 跨境驻地 / F-5021 同意闸门 这三条的领域逻辑**还没被 relay 转发主干调用**——实测 `grep ComplianceGroupPolicy|ConsentGate|CrossBorderRouting` 在 `relay/` 下**零命中**。也就是说"分级/留存/拦境外/同意才放行"目前只是单元可测的领域能力，真实 API 调用并不会触发它们。

## 4. 是否进本轮（今晚/明早）生产范围

**建议：不纳入本轮生产硬验收，登记为"已建成待缝合"，留下一轮按需接入。** 理由：
1. 今晚作战目标是"回到干净可发布基线 + 补完命脉相邻 REQ-07/08 + 摸清 compliance"——本文档即"摸清"的交付物，缝合属于增量。
2. F-5020 账号注销已有 live 端点，可单独验收；但 F-5017/18/21 接入转发主干属于改命脉路径的中风险动作，应走正式 slice（带 E2E 验证），不今晚盲缝。
3. 不接入也不影响现有命脉链路（它当前对转发零侵入，纯增量子域）。

## 5. 纳入 REQ 表（补登）

在 REQ-01~17 之外，新增登记段 **REQ-CMPL（合规子域 F-5016~5021）**：
- 状态：DONE(领域层+F-5020端点) / PARTIAL(F-5017/18/21 未接入主干)
- 下一轮 slice 候选：将 ConsentGate / ComplianceGroupPolicy 接入 relay 调用前置校验 + 选渠过滤，补 E2E（同意未签→403、命中境外渠道→403、留存开关落库）。
