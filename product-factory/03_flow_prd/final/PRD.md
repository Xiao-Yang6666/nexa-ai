# PRD（详细产品需求文档）索引

> 共 15 个分片 PRD，101 个功能块，覆盖 231 功能项。
> 机器权威=各 prd/*.md + FLOW-PRD-TRACEABILITY.csv；本文件为索引。

## 全局概览

- 产品：基于 newapi 的 AI API 网关 SaaS（routifyapi 视觉参考）
- 用户：开发者(自助用 Key 调用)、运营、运维、管理员、Root
- 范围：公开站→注册登录→建Key→Playground→Relay调用→计费→用量；管理侧渠道/模型/计费配置+监控
- 非目标(MVP)：组织级多租户(见 ROLE-PERMISSION-MATRIX 三阶段决策)

## 分片索引

| 分片 PRD | 功能块 | 覆盖域 |
|---|---|---|
| prd-account.md | 13 | PRD — 账号与身份（FL-account） |
| prd-asynctask.md | 6 | PRD — 异步任务中心（FL-asynctask） |
| prd-billing.md | 6 | PRD — 计费与钱包（FL-billing） |
| prd-channel.md | 6 | PRD — 渠道管理与上游路由（FL-channel） |
| prd-deploy.md | 6 | PRD — 部署管理（io.net 集成，FL-deploy |
| prd-growth.md | 6 | PRD — 增长：签到 + 邀请返利分销（FL-growth |
| prd-model.md | 6 | PRD — 模型广场与元数据（FL-model） |
| prd-nfr-rbac.md | 7 | PRD — 横切非功能需求 + 数据合规 + RBAC（FL |
| prd-ops.md | 8 | PRD — 运营 / 系统设置 / 运维（FL-ops） |
| prd-playground.md | 4 | PRD — Playground 在线试用（FL-playg |
| prd-prefill.md | 4 | PRD — 预填分组（FL-prefill） |
| prd-public.md | 7 | PRD — 公开与营销站点（FL-public） |
| prd-relay.md | 6 | PRD — Relay 网关多协议中转（FL-relay） |
| prd-token.md | 7 | PRD — 令牌管理（FL-token） |
| prd-usagelog.md | 9 | PRD — 日志与用量（FL-usagelog） |

## 横切契约

- DATA-MODEL.md：字段级数据契约（PRD 数据对象段权威来源）
- NFR-REQUIREMENTS.md / ROLE-PERMISSION-MATRIX.md：非功能与权限
- PAGE-STATE-MATRIX.md：页面状态（S6 原型状态权威来源）
- FLOW-PRD-TRACEABILITY.csv：功能↔PRD 覆盖对账