# S12 验收后复盘 · 产品经理视角下一轮迭代见解

> 角色：产品经理（PM）
> 范围：nexa-app（AI 模型中继/计费 SaaS）
> 基线：main = cf5e0a4（R1+R2+R3+R4），后端 537 测试绿，前端 build 过
> 立场：从"要上线卖钱的 AI 中继 SaaS"的真实用户/商业视角，逐域逐页核实功能完整度，判断离可售卖还差什么。

---

## 0. 一句话结论

**技术地基扎实，但"卖钱闭环"是断的。** 中继转发（relay）真链路已通、计费会落账、密钥管理体验完整——这是产品的硬核能力，做得好。但**用户无法真正给我们付钱**：充值下单接口后端没有实现，支付网关是占位桩，回调入账端点只在安全白名单里挂了名却没有控制器。同时，决定转化率的两个体验（**站内试用 Playground、新用户首屏仪表盘**）一个没有前端、一个全是假数据。这意味着当前形态只能"演示"，不能"开张"。下一轮必须以"打通付费闭环 + 补齐转化体验"为唯一主线，其余一切让路。

---

## 1. 最影响商业转化的功能 —— 必须进下一轮

### 1.1 【P0 阻断上线】充值/支付链路是死路

这是最严重的问题，直接决定能不能收钱。我核实了完整链路：

- 前端 `frontend/src/features/billing/components/RechargePage.tsx` 提供了完整的充值 UI（档位卡、自定义金额、支付宝/微信/对公转账、订单摘要、赠送计算），`billing.api.ts` 的 `createTopUp()` 会 `POST /api/topup`。
- **但后端没有任何控制器处理 `POST /api/topup`。** 我全量搜过 `@PostMapping("/api/topup")` / `RequestMapping topup`，零命中。前端这个"确认充值"按钮点下去就是 404。
- 应用层 `billing/application/CreateTopUpOrderUseCase.java` 写好了下单逻辑，**但没有任何控制器注入它**（全局搜 `CreateTopUpOrderUseCase` 只在它自己文件里出现），是一个孤儿用例。
- 支付网关 `billing/infrastructure/payment/BaselinePaymentGatewayAdapter.java` 是明确标注的占位桩：`createSession()` 返回拼接的本地假 URL，`verifyCallback()` 只要 `trade_no` 非空就**无条件放行**。
- `SecurityConfig.java:101` 把 `/api/topup/callback/*` 加进了 `security:[]` 白名单（注释写"F-2044 支付回调入账"），**但同样没有控制器实现这个回调端点**。即使下单接口补上、用户跳到真实收银台付了钱，钱也回不来——没有入账路径。

**影响**：整条"真金白银充值 → 余额到账"的链路完全不存在。当前唯一能给账户加余额的路径是**兑换码**（`RedeemController` 的 `/api/user/topup`，已实现），适合内测/赠送，但不是一个能规模化卖钱的 SaaS 的收款方式。

**下一轮必做**：
1. 实现 `POST /api/topup` 控制器，接通已有的 `CreateTopUpOrderUseCase`。
2. 实现 `POST /api/topup/callback/{provider}` 回调入账控制器（验签 → 幂等 → `UserQuotaAccount.credit` + 置 TopUp 为 success），需要 `HandlePaymentCallbackUseCase`（`CreateTopUpOrderUseCase` 注释里引用了它，但类不存在）。
3. 至少接通一个真实支付渠道（Stripe / 易支付二选一），替换 `BaselinePaymentGatewayAdapter`。验签桩"无条件放行"是**伪造回调即可白嫖余额**的安全洞，上线前必须堵死。
4. 前端 `RechargePage` 的 `payment_method` 映射目前把"支付宝→stripe、微信→creem"硬写死（第 61 行），需要和真实渠道对齐。

### 1.2 【P0 转化体验缺失】Playground 站内试用：有后端、无前端

- 后端 `playground/interfaces/api/PlaygroundController.java` 完整实现了 `POST /pg/chat/completions`：UserAuth、禁 access token、按真实额度计费、走 relay 转发。这是个高质量的"先试后买"能力。
- **前端完全没有对应页面**。我搜了 `frontend/src` 下的 `playground`，只在 `schema.ts` 里有类型，没有任何 `.tsx` 页面，`nav-tree.ts` 里也没有入口。
- 对一个 AI 中继 SaaS 来说，"注册后能立刻在网页里聊两句看看效果"是**核心转化漏斗的第一站**。现在新用户注册完，只能去 keys 页面拿 key、自己写 curl——对非开发者/评估期用户极不友好，白白浪费了已经做好的后端能力。

**下一轮必做**：补一个 `/playground` 前端对话页（模型选择 + 流式输出 + 余额提示），接 `/pg/chat/completions`。这是投入产出比最高的转化提升项，因为后端零成本。

### 1.3 【P1 信任感缺失】仪表盘首屏几乎全是假数据

`frontend/src/features/dashboard/`：

- KPI 顶行（本月调用量/消费/余额/累计请求）是**真实**的（`useKpi` 聚合 `/api/user/self` + `/api/log/self/stat`）。✓
- 但 **30 天趋势折线图、模型分布环形图、P50/P95 延迟柱状图全是写死的静态数组**（`dashboard.model.ts` 的 `TREND_30D` / `MODEL_DIST` / `LATENCY_BY_MODEL`），`DashboardPage.tsx` 顶部的"最近调用记录"表格也是 8 行硬编码假数据（`opus-4.8 $0.0412` 之类）。
- 环形图中心还硬写着"1.28M 本月调用"。

**影响**：付费用户登录看到的第一屏，数字和自己实际用量对不上——这是直接摧毁信任的体验。一个卖 API 额度的平台，用户最在意"我花了多少、调了多少"，这里却给假数据。

**下一轮必做**：后端补按日/按模型聚合的 series 端点（`dashboard.model.ts` 注释也承认"契约暂无聚合端点，暂用静态演示数据"），前端切真实数据；"最近调用记录"直接复用已有的 `/api/log/self` 取最新 N 条即可，成本很低。

---

## 2. 仍不顺的流程

### 2.1 【P1】自助注册被邮件桩卡住

- `account/infrastructure/messaging/LoggingEmailSender.java` 是日志桩，验证码/重置令牌只 `log.info` 打印，**不真发信**（类注释明确标 TODO 生产换真实实现）。
- 注册流程 `RegisterForm.tsx` 和找回密码依赖 `/api/verification`、`/api/reset_password`。如果注册强制邮箱验证码，**没有真实 SMTP 用户就收不到码、注册不了**——自助获客漏斗在第一步就断。
- 这条在 S12 输入里已列为遗留（"真邮件 SMTP：需用户提供凭证"），但从 PM 角度它是**上线硬门槛**，不是"nice to have"：要么接真实邮件服务，要么明确产品决策"首版关闭邮箱验证、用其他注册方式"。

**下一轮决策点**：接 SMTP/SES 真实邮件 vs. 首版降级（关闭强制验证 / 仅 OAuth 登录）。需要 PM 拍板，不能继续悬空。

### 2.2 【P1】余额无下限保护，存在白嫖/欠费风险

- `billing/infrastructure/account/JdbcUserQuotaAccount.java` 的 `debit()` 注释明说"**本期最小结算不做余额下限保护（允许欠费）**"，SQL 是无条件 `quota = quota - ?`。
- `relay/application/RelayForwardUseCase.java` 的 `settle()`（第 727 行）是**响应后一次性扣费**，转发前**没有任何余额闸门/预扣**（注释 TODO REQ-05 承认缺"选渠后预扣 + 多退少补"）。
- **影响**：余额为 0 甚至负数的用户照样能持续调用昂贵模型，扣成负余额也不拦。对一个按量付费的中继来说，这是直接的收入泄漏 + 被刷成本的风险。流式场景尤其危险（一次长对话成本可观）。

**下一轮必做**：转发前加余额校验闸门（余额 ≤ 0 或低于阈值直接 402/403），中期补预扣-结算的两段式记账。

### 2.3 【P2】日志的 IP / User-Agent 永远是空

- `RelayForwardUseCase.recordConsumeLog()`（第 753-761 行）明确 TODO：forward 链路没透传 `HttpServletRequest`，IP/UA 落库写空串。
- 但前端 `UsagePage.tsx` 有 IP 和 User-Agent 两列在展示——用户看到的永远是空白。安全审计、异常排查、风控都缺这个维度。

**下一轮**：从 `RelayController` 经 `RelayAuthContext` 透传真实 `remoteAddr`/UA。

---

## 3. 需要补强的角色/场景

- **评估期用户（非开发者）**：目前从注册到"看到效果"必须经过"拿 key → 写代码"。缺 Playground 前端（见 1.2）是最大短板。这类用户决定了大量潜在转化。
- **付费决策者/财务**：充值开不了票、对公转账流程在 UI 上写了"1-2 工作日到账"但后端没有对应的人工确认/审核流。To B 客户的付款场景没闭环。
- **被中继的成本归因（运营/老板视角）**：`RelayForwardUseCase` 里 `resolveCostRatio` 缺行时 `cost_missing=true` 不阻断、利润算作等于售价。这意味着只要管理员忘记给某渠道×模型配成本行，**利润分析就会虚高**。利润分析（`/admin/profit`）是经营决策依据，需要一个"成本未配置渠道"的醒目告警/巡检，而不是静默标记。

---

## 4. 应砍掉或延后的需求

- **Passkey/WebAuthn（passkey 域）**：S12 输入已记"stub 状态，生产化工作量大"。对一个还没打通收款的产品，无密码登录是过度投入。**延后**，首版用密码 + OAuth 足够。
- **合规驻地标注（compliance residency）**：渠道表无 residency 字段，需要产品决策怎么标境内外。除非首版就要面向有数据驻留合规要求的 B 端客户，否则**延后**，别让它阻塞上线。
- **多支付渠道齐全**：1.1 里列了支付宝/微信/对公转账三种。首版**只需打通一个真实渠道**就能开张，其余延后。别为了 UI 上三个按钮都亮而拖慢上线。
- **仪表盘的炫酷图表**：延迟分布柱状图这类"锦上添花"的图，可以晚于"真实用量趋势 + 真实最近调用"上线。先保证核心数字真实，再做花样。

---

## 5. 应进入下一轮的验收 Gap（优先级排序）

| 优先级 | Gap | 域/文件 | 影响 |
|---|---|---|---|
| P0 | `POST /api/topup` 无控制器，下单孤儿用例 | `billing` / `CreateTopUpOrderUseCase`（无人注入） | 无法收钱 |
| P0 | `POST /api/topup/callback/*` 白名单挂名但无实现 | `SecurityConfig:101` / 缺 `HandlePaymentCallbackUseCase` | 付了钱不到账 |
| P0 | 支付网关是放行桩 | `BaselinePaymentGatewayAdapter` | 伪造回调白嫖 + 无真实收银台 |
| P0 | Playground 无前端 | `frontend` 缺 `/playground` 页 | 转化漏斗第一站缺失 |
| P1 | 仪表盘图表/最近调用全假数据 | `dashboard.model.ts` 静态数组 + `DashboardPage` RECENT | 首屏摧毁信任 |
| P1 | 邮件 stub，注册/找回不真发信 | `LoggingEmailSender` | 自助注册可能断 |
| P1 | 转发前无余额闸门，允许欠费 | `RelayForwardUseCase.settle` / `JdbcUserQuotaAccount.debit` | 收入泄漏/被刷 |
| P2 | 日志 IP/UA 落空串 | `RelayForwardUseCase.recordConsumeLog` | 审计/风控缺数据 |
| P2 | 成本行缺失静默致利润虚高 | `RelayForwardUseCase.resolveCostRatio` | 经营决策误判 |

---

## 6. 下一轮成功标准（验收口径）

下一轮的目标是**从"能演示"到"能开张"**。成功 = 一个真实用户能完整走完付费闭环且数据真实：

1. **付费闭环可用**：真实用户能通过至少一个真实支付渠道充值，钱到账、余额更新、`/admin/profit` 利润数据准确。端到端验收：下单 → 跳收银台 → 支付 → 回调验签 → 入账 → 余额可见 → 调用扣费。
2. **试用闭环可用**：注册用户能在 `/playground` 网页里直接选模型、发消息、看到流式回复，并正确扣费。
3. **首屏数据真实**：仪表盘的趋势、最近调用记录来自真实 `/api/log/self`，KPI 与用量页对得上，无任何硬编码业务数字。
4. **不漏钱**：余额 ≤ 0 的用户被转发闸门拦截（402/403），无法继续白嫖调用；伪造支付回调被验签拒绝。
5. **注册可自助**：邮箱验证码/找回密码真实送达（或产品明确决策首版登录方式并落地）。

**量化建议**：下一轮验收时，用一个全新空库 + 全新注册账号，由非工程人员完成"注册 → Playground 试用 → 充值 $10 → 用 key 调一次 API → 在仪表盘看到这笔消费"全流程，全程不碰数据库、不看日志。能走通即达标。

---

## 7. 给工程的取舍提醒

- **不要再加新域/新页面**，下一轮把已经做了一半的能力（充值用例、Playground 后端）接通即可，投入产出比最高。
- 付费闭环涉及钱，**安全 > 速度**：验签、幂等、余额闸门三件事不能用桩糊弄上线。
- 邮件、合规驻地、Passkey 这类"需要外部凭证/重投入"的项，PM 会在下一轮启动前给明确决策（接 or 砍），工程不要因为它们悬空而阻塞主线。
