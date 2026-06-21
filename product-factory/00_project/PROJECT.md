# RoutifyAPI × New API Product

## 项目意图

参考 RoutifyAPI 的公开网站视觉、产品表达与转化体验，参考 New API 开源项目的底层功能、数据模型与 API 网关能力，重新定义一套自己的 AI API 网关 SaaS 产品。

本项目不是复刻 RoutifyAPI，也不是直接换皮 New API：

- RoutifyAPI = 视觉/体验/营销表达参考。
- New API = 功能/技术逻辑/数据对象参考。
- 最终产物 = 我们自己的产品定义、需求树、PRD、UI 与原型。

## 当前执行模式

- 工作流：autonomous-product-factory 轻量六阶段。
- 质量门：product-quality-gates 已启用（含机械门脚本：template_slop_probe / compute_gate_counts / repo_capability_coverage）。
- 审核方式：独立 reviewer subagent 窄上下文审核，不自产自审。
- 当前阶段：从 S1 完整重跑（验证新机械门全链路）。

## 本次重跑背景（关键）

上一轮 S1-S4 全部质量门 PASS，但实测是套模板凑出来的：
- S1 漏抓 5 个 newapi 旗舰能力（签到/邀请返利分销/Telegram 登录/异步任务中心/预填分组）
- S2 CSV 的 evidence/acceptance/granularity 列各只有 1 个 distinct value（模板填充）
- S3 48 场景塌缩成 7 张模板图
- S4 占位句「读取、变更、状态流转或审计」复制 330 次
本次重跑必须跑过新机械门脚本（exit 0）才算各阶段完成。

## 范围原则

- 不使用旧版产物作为权威，只可作为参考证据（旧产物在 _archive/full-rerun-20260619-103254）。
- 不早期裁剪核心闭环。
- 首轮重点覆盖：公开营销页、登录/注册（含 OAuth/Telegram/Passkey）、模型广场/价格、控制台、API Key、Playground、用量日志、钱包/订阅、签到/邀请返利、异步任务中心、管理后台、渠道/模型/计费配置、Relay API。

## 来源

| Source | Role | Authority | Notes |
|---|---|---|---|
| https://routifyapi.com/ | 视觉/营销/公开体验参考 | P0 for public UX | 首页已抓取 |
| https://github.com/Calcium-Ion/new-api.git | 功能/技术/架构参考 | P0 for domain logic | 已克隆到 repo/new-api |

## 红线

- 不复制受保护品牌、logo、文案归属。
- 不把 New API 原 UI 当最终视觉方向。
- 不在 S1/S2 阶段做商用法律结论，只记录 license 风险待后续确认。
