# AUDIT-REPORT — Nexa AI 平台 设计↔代码一致性审计（relay 经营链路）

> 审计对象：`/root/nexa-app/backend`（分支 `feat/close-mgmt-loops-and-routing`，编译已通过）。
> 设计基准：`product-factory/07_dev_contract/final/openapi.yaml`（API 契约最硬裁判）、`03_flow_prd/final/prd/prd-relay.md`(RL-1~RL-8)、`prd-model.md`(ML-6~ML-8)、`prd-billing.md`(BL-7~BL-9)、`reviews/BILLING-MODEL-ARCHITECTURE.md`、`DATA-MODEL.md §16-§19`。
> 方法：design-code-conformance-audit（设计为基准，代码实证，结论带文件:行）。
> 审计日期：2026-06-22。

---

## 0. 一句话结论

设计把「一笔请求」规约成一条完整经营链路（协议识别 → C→A→B 两层映射 → key 减法校验 → 按 group×B 选渠容灾 → 头尾协议转换 → 双价记账 → 落 Log），**链路上每一个独立环节代码都已存在且多数为真实逻辑（带单测）**，但**编排主干 `RelayForwardUseCase` 没有把这些环节接起来**——它只注入了 3 个 repo，转发入口 `forwardRelay()` 一律返回 501。结果是：真组件成了一座座孤岛，整条经营链路对外是空的。**这是用户痛点「apikey/模型组/模型 不搭嘎」的物理根因——不是设计缺失，是主干消费链未实现。**

---

## 1. 「apikey→模型组→模型」不搭嘎 — 根因精确定性

### 1.1 设计本来要求 group 是什么、怎么关联

对照 `BILLING-MODEL-ARCHITECTURE.md §3` + `prd-model.md ML-8` + `prd-billing.md BL-7/BL-8` + architect skill 的 api-gateway 建模铁律：

设计**有意**把 group 拆成两个正交职责，**刻意不把它做成主表/实体**：

| group 的职责 | 设计落点 | 关联方式 |
|---|---|---|
| ① 折扣等级（定价维） | `group_ratio` KV（复用 `GetGroupRatio`） | `Token.group/User.group` 字符串作查表键 → 折扣系数 |
| ② 选渠范围（路由维） | `Ability(group × model=B → channel_id)` 复合主键 | group 字符串 + B 查 Ability 路由表选渠 |
| ③ 模型可用性 | **不归 group 管**（ML-8 模型全开） | A 上架即可用，group 不再圈模型 |

→ **设计结论：group 就该是裸字符串约定，没有 group 主表是正确的**（符合「分组退化为折扣系数 + 权限范围，不再是定价/主数据单位」的建模铁律）。`token.group / channels.group / abilities.group 全是 varchar` 这一审计事实，**与设计一致，不是偏差**。

### 1.2 那为什么「不搭嘎」？—— 根因定性

> **根因 = 代码没实现（DIVERGED/MISSING），不是设计缺失。**

设计要求 group 通过「`Ability(group×B)` 选渠 + `group_ratio` 折扣」在**转发主干里被消费**，把 apikey→group→可用渠道→计费串起来。但：

- `RelayForwardUseCase`（`relay/application/RelayForwardUseCase.java:27-33`）构造器只注入 `l2Repo/l1Repo/logRepo` 三个 repo，**不注入选渠用例、不注入计费、不注入 HTTP client**。它只有 `resolveDispatch()`（路径分发）和 `resolveModel()`（C→A→B 映射）两个真方法，**没有任何"用 group 选渠 / 用 group_ratio 计费"的方法**。
- `RelayController.forwardRelay()`（`relay/interfaces/api/RelayController.java:154-162`）9 个对外端点全部走它，统统 `return 501 not_implemented`。
- 真正会「消费 group」的两个组件是孤岛：
  - `routing/application/ResolveChannelRouteUseCase`（`@Service`，CH-4 亲和 + CH-5 重试，有单测 `ResolveChannelRouteUseCaseTest`）——`grep` 全 `src/main` **无任何生产调用方**（仅 javadoc 提及 + 测试引用）。
  - `relay/domain/service/DualPriceBilling`（双价计算真实）——`grep` 全 `src/main` **零调用方**（连 javadoc 都没引）。

所以 group 这个字符串**在转发链路里压根没人读**（审计事实 5 已指出 group/model_limits/remain_quota/allow_ips 解析在转发链路均无消费代码）。客户配了 key、配了 group、配了 Ability，但请求打进来走到 `forwardRelay()` 就 501 了，永远走不到「按 group 选渠 + 按 group_ratio 折扣」那一步 → 表现为「apikey/组/模型 各配各的、连不起来、不搭嘎」。

### 1.3 次要观察（可选 DESIGN-FIX，非命脉）

设计依赖 group 为「自由字符串、admin 在 channel/token/ability 上各自手填」，**无中央注册/校验**：无法枚举有效 group、无法校验 token.group 引用了真实存在的 group、无 group 生命周期管理。这是一个真实的**轻量建模缺口**（设计有意省略），值得补一个可选的 Group 注册中心（见 REQ-13，P2），但它**不是「不搭嘎」的命脉根因**——命脉是 §1.2 的主干未接线。

---

## 2. 三类偏差汇总

### 2.1 MISSING（设计要求，代码空壳/未做）

| 设计项 | 代码现状 | 文件:行 |
|---|---|---|
| RL-1/RL-7 转发主干（鉴权→映射→选渠→转发→计费→Log 端到端） | `forwardRelay()` 返 501 | `RelayController.java:154-162` |
| 整条链路上游调用（HTTP client） | relay/channel/billing/routing **零 WebClient/RestClient** | 全域无（仅 oauth/ops 有 HTTP client） |
| RL-3 上游错误处理 + 按状态码重试/禁用渠道 | 无实现 | 无 |
| RL-5 视频内容代理（VideoProxy） | 端点返 501 TODO | `RelayController.java:144-149` |
| RL-6/RL-8 Claude 协议适配 | 4 方法全 `throw not implemented` | `ClaudeProtocolAdapter.java:56-77` |
| RL-8 流式 SSE 双向（OpenAI+Claude） | `parseStreamChunk/serializeStreamChunk` 返 `List.of()` | `OpenAiProtocolAdapter.java:196-206`、`ClaudeProtocolAdapter.java:80-89` |
| F-3034 /v1/models 返回 A 全集 | 返空 list TODO | `RelayController.java:81-84` |

### 2.2 DIVERGED（代码做了，但与设计脱节 — 主要是「孤岛未接线」）

| 设计要求 | 代码现状（真实但孤立） | 文件:行 |
|---|---|---|
| 选渠（CH-4 亲和/CH-5 重试）接入转发主干 | `ResolveChannelRouteUseCase` 真实有单测，**无生产调用方** | `routing/application/ResolveChannelRouteUseCase.java:38` |
| 选渠 Ability 查询（group×B 加权随机） | `AbilityBackedChannelSelectionAdapter` 真实，但只被孤立用例引 | `routing/infrastructure/selection/AbilityBackedChannelSelectionAdapter.java` |
| 双价计费接入结算 | `DualPriceBilling` 真实计算，**零调用方** | `relay/domain/service/DualPriceBilling.java` |
| OpenAI 非流式协议转换接入主干 | `OpenAiProtocolAdapter` 非流式真实，但主干不调 | `OpenAiProtocolAdapter.java`（非流式真，流式空） |
| group 在转发链被消费（§1.2） | `RelayForwardUseCase` 不读 group | `RelayForwardUseCase.java:27-64` |
| token `remain_quota`/`model_limits`/`allow_ips` 在转发链消费 | 字段都在，转发链无人读 | `token/domain/model/Token.java`（字段全在） |

### 2.3 EXTRA / CLEANUP（代码有、需甄别）

| 项 | 现状 | 处置 |
|---|---|---|
| `StubChannelProbeClient` | 渠道探活桩 | 测渠道功能时需真实化，否则保留为测试桩并标注 |
| `StubChannelSelectionAdapter` | 选渠桩（与真实 `AbilityBacked` 并存） | 接线时确认生产用 Ability 版、桩退测试用 |

> 未发现明显的 agent 幻觉超纲表/字段——DB 层（PublicModel/PlatformModelMapping/UserModelAlias/ChannelModelCost）与 DATA-MODEL §16-§19 对齐。本轮 EXTRA 仅限 stub 甄别，无须删表。

---

## 3. 链路断点定位（LINK-TRACE 精简）

设计链路（`prd-relay.md RL-7` / `BILLING-MODEL-ARCHITECTURE.md §6` 20 步）逐环节通断：

```
环节                              代码状态        断点
─────────────────────────────────────────────────────────
① 协议识别 inFmt (RL-2)           ✅ RelayPathResolver 真实
② C→A→B 两层映射 (RL-7②)          ✅ TwoLayerModelResolver 真实(单测)
③ key 减法校验 (ModelLimits)       ❌ 转发链无人调用            ← 断
④ group×B 选渠 (Ability/CH-4/5)    🟡 组件真实但无生产调用方     ← 断（孤岛）
⑤ 头尾协议转换 (RL-6)              🟡 OpenAI非流式真/Claude空/流式空 ← 断
   ↑ 调上游 HTTP                   ❌ 全域零 HTTP client        ← 致命断点
⑥ 扣客户售价 (BasePriceRatio×group_ratio) ❌ 转发链无计费调用     ← 断
⑦ 记成本 (ChannelModelCost(ch,B))  🟡 DualPriceBilling真但零调用 ← 断（孤岛）
⑧ 响应回转                         ❌ 依赖⑤                     ← 断
⑨ 落 Log (C/A/B+协议+双价)         🟡 RelayLog 模型真，无写入路径 ← 断
─────────────────────────────────────────────────────────
编排主干 RelayForwardUseCase       ❌ 只注入3个repo，forwardRelay 返501  ← 总断点
```

**唯一总断点 = 编排主干没接线 + 缺 HTTP client。** ②③④⑤⑥⑦⑨ 的真实组件全部悬空在 `RelayForwardUseCase` 之外。打通主干（注入这些组件 + 建 HTTP client + 串成 RL-7 20 步）即可让 80% 的「不搭嘎」消失。

---

## 4. 哪些要回写设计（DESIGN-FIX）

- **REQ-13（可选 P2）**：group 缺中央注册/校验。设计有意把 group 做成自由字符串，但缺一个轻量 Group 注册（枚举有效 group + 校验 token/ability/channel 引用 + 折扣系数与 group 绑定的管理入口）。属于设计可补强项，非必须，落地前先评估是否值得引入。
- 其余偏差**全部是「设计已规约清楚、代码未实现/未接线」**，不需要回写设计——照现有 PRD/openapi/DATA-MODEL 直接开发即可。

---

## 5. 交付物

- 本报告：`/root/nexa-app/audit/AUDIT-REPORT.md`
- 新一轮需求清单：`/root/nexa-app/audit/REQUIREMENT-LIST-v2.md`（17 条，P0×6 / P1×6 / P2×5，带依赖与并行/串行标注）
