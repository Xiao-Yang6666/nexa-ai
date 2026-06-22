# SEMANTIC-GAPS — 语义缺口（SG-xxx）

> 角色：记录 S1 摄入中**证据不足 / 待确认 / 存在歧义**的项，交给 S2 显式裁决，防止把假设当事实。
> 编号 SG-001..SG-NNN。每项含：缺口、当前证据、影响范围、建议处置阶段。
> 原则：repo 是后台能力面权威；routifyapi.com 是视觉 / 公开体验权威；二者覆盖不到的为缺口。

## SG-001 — 控制台 / 价格 / API Keys 动态 UI 未捕获
- **缺口**：`app.routifyapi.com` 的 console / pricing / API Keys 为跨域动态页（需登录），本轮未访问 / 未截图。
- **当前证据**：仅确认子域与入口存在（REFERENCE-TRIAGE）；后台能力以 repo 源码替代。
- **影响**：FC-126 标 GAP；S5/S6 设计控制台 UI 时无参考站视觉一手证据。
- **建议处置**：S2 以 repo 能力面定范围；S5/S6 前补动态证据 **或** 显式标注「UI 为假设，基于 repo 能力 + Routify 视觉 token 推导」。

## SG-002 — 计费倍率 / 定价具体数字未固化
- **缺口**：model_ratio / group_ratio / completion_ratio / 阶梯档位 / 表达式的**具体数值**在 repo 中是运营期配置（setting 默认值 ≠ 上线定价），不是产品定义。
- **当前证据**：计费**机制**完整（pkg/billingexpr、ratio_setting），但 Routify「远低官方价格」只是营销文案，无具体价格表。
- **影响**：S2 需求 / S5 价格页无法直接落数字。
- **建议处置**：S2 把「定价策略 / 默认倍率」列为产品决策项；具体数字标为运营配置，不在产品文档硬编码。

## SG-003 — 支付网关最终启用组合未定
- **缺口**：repo 支持 6 个支付网关（Stripe/Creem/ePay/Waffo/WaffoPancake/通用），但 Routify SaaS 实际启用哪些、面向哪些地区货币未知。
- **当前证据**：repo controller/topup_*.go 全部存在；动态域支付页未访问。
- **影响**：INTEGRATION-LIST §4 列全集；S2 需裁决上线子集。
- **建议处置**：S2 决策「目标市场 → 支付网关组合」；境内/境外合规差异留意 payment_compliance。

## SG-004 — 品牌 / 归属边界（New API vs Routify）
- **缺口**：repo AGENTS.md Rule5 声明 new-api / QuantumNous 品牌为受保护标识；Routify 页脚自述「基于 New API」。本产品「重新定义」的品牌替换边界、license 合规未下结论。
- **当前证据**：REPO-INSPECTION §License 仅记录，未下法律结论；PROJECT.md 红线待查。
- **影响**：影响品牌文案、页脚、about 页、代码归属声明。
- **建议处置**：**S1 不下商用法律结论**。S2 明确「保留底层逻辑、自建 Routify 品牌」边界，license 合规作为独立决策项上抛。

## SG-005 — 上游 provider 上线范围与营销展示不一致
- **缺口**：repo 能力面约 52 个 provider；routifyapi.com 首页仅展示 **Anthropic / OpenAI** 两家。
- **当前证据**：constant/channel.go（52 provider）vs WEBSITE-COVERAGE §3（展示 2 家）。
- **影响**：S2 需裁决「能力全集 vs 实际上线 vs 营销展示」三层差异。
- **建议处置**：S2 区分「网关支持能力」（保留全集）与「Routify 上线 / 展示模型」（聚焦旗舰几家 + 渐进开放）。

## SG-006 — 多租户 / 组织模型缺失
- **缺口**：repo 是「用户 + 分组 + 角色（admin/common/root）」单租户管理模型，**无组织 / 团队 / 多租户隔离**概念（无 Org/Team/Workspace entity）。
- **当前证据**：model/user.go 仅 user/group/role；数据对象清单（REPO-INSPECTION §数据对象）无组织实体。
- **影响**：若 Routify 定位 B2B / 团队协作，存在能力缺口。
- **建议处置**：S2 明确产品定位（B2C 个人开发者 vs B2B 团队）；若需多租户，记为「超出 repo 能力面的增强项」。

## SG-007 — 部署管理（io.net）是否为产品范围未定
- **缺口**：controller/deployment.go + pkg/ionet 提供 GPU 部署集群管理，疑似对接 io.net，但是否属于 Routify 产品形态未知。
- **当前证据**：路由 / 控制器存在；与「AI API 网关 SaaS」主线关系待判。
- **影响**：FC-098~FC-100 范围待裁。
- **建议处置**：S2 判定 deployment 域「保留 / 省略」；多数 API 网关 SaaS 不暴露此能力，倾向省略但需确认。

## SG-008 — 订阅 vs 充值 vs 兑换 三种计费并存的产品取舍
- **缺口**：repo 同时支持充值额度制（TopUp/Quota）、订阅制（Subscription）、兑换码（Redemption）。三者在 Routify 的主次关系未定。
- **当前证据**：三套 model 均存在；营销文案「用多少付多少」偏向**额度 / 用量计费**。
- **影响**：S2 商业模式核心决策。
- **建议处置**：S2 以营销文案「用多少付多少、没有套路」为锚，倾向额度 / 用量制为主，订阅 / 兑换为辅，需明确裁决。

## SG-009 — 通知渠道完整性
- **缺口**：前端确认 email / webhook / bark 三种额度预警通知；是否还有其它系统通知（如任务完成、签到提醒）未穷尽。
- **当前证据**：NotificationSettings.jsx warningType=email/webhook/bark。
- **影响**：通知能力面可能不止额度预警。
- **建议处置**：S2 如需通知体系，补查后端 service 层通知派发逻辑。

## SG-010 — Classic vs Default 双前端主题的取舍
- **缺口**：repo 含 default（React19+Rsbuild+BaseUI）与 classic（React18+Vite+Semi）两套前端；本轮证据多来自 classic（如 NotificationSettings.jsx）。Routify 视觉应基于哪套未定。
- **当前证据**：repo web/default + web/classic 并存；Routify 视觉 token 偏极简开发者工具风（WEBSITE-COVERAGE §5）。
- **影响**：S5 设计基线选择。
- **建议处置**：S5 以 Routify 视觉 token 为准重做 UI，**不**直接沿用任一 repo 主题；功能参照 default 主题能力面。

## SG-011 — 登录 / 注册动态流程未捕获
- **缺口**：营销页仅见「登录」CTA，未捕获注册 / 登录动态表单与字段。
- **当前证据**：repo 身份能力完整（D1），但 Routify 实际登录页 UI / 启用的登录方式未知。
- **影响**：S5/S6 登录页设计无参考站一手证据。
- **建议处置**：S2 以 repo 身份能力定可选登录方式；S5 标注 UI 假设或补动态证据。

---

## 缺口处置矩阵

| SG | 类别 | 阻塞 S2? | 主处置阶段 |
|---|---|---|---|
| SG-001 | UI 动态证据 | 否 | S5/S6 |
| SG-002 | 定价数字 | 否 | S2 决策 |
| SG-003 | 支付组合 | 否 | S2 决策 |
| SG-004 | 品牌/license | 否（S1 不下法律结论） | S2 + 独立合规 |
| SG-005 | provider 上线范围 | 否 | S2 决策 |
| SG-006 | 多租户/组织 | 否 | S2 定位决策 |
| SG-007 | 部署域范围 | 否 | S2 决策 |
| SG-008 | 计费模式取舍 | 否 | S2 商业模式 |
| SG-009 | 通知完整性 | 否 | S2/补查 |
| SG-010 | 前端主题取舍 | 否 | S5 |
| SG-011 | 登录流程证据 | 否 | S5/S6 |

> 全部缺口**均不阻塞 S2 需求拆解**（后台能力以 repo 为权威）。S5/S6 设计 / 原型前必须补动态证据或显式标注 UI 假设。
