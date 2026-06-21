# HANDOFF — S1（摄入）→ S2（需求拆解）交接契约

> 角色：S1 摄入阶段完整交付清单 + 权威性声明 + S2 工作指引。S2 **必须**先读本文件。
> 本轮 S1 为重跑，纠正了上一轮静默漏掉 5 个旗舰能力的问题（见 SOURCE-TYPE-ANALYSIS §3）。

## 1. final 产物清单（01_ingestion/final/）

| 文件 | 内容 | 权威级别 | S2 用法 |
|---|---|---|---|
| `REPO-INSPECTION.md` | new-api 产品能力域全景（D1~D17 + 数据对象 + license 记录） | P0 功能/逻辑/后台能力是否存在 | 反向覆盖基准（能力面全集） |
| `FEATURE-CANDIDATES.md` | 候选功能 FC-001~FC-126（覆盖 17 域 + 公开站点） | P0 候选池 | 逐条裁决：保留/改造/省略/增强 |
| `INTEGRATION-LIST.md` | 上游 provider(~52) + OAuth(8) + 支付(6) + 通知(3) + webhook + 基础设施 | P0 集成点全集 | 判定上线集成子集 |
| `GLOSSARY.md` | 领域术语表（计费/路由/缓存/增长/任务/预填等黑话） | P0 统一领域语言 | 全程术语对齐 |
| `SEMANTIC-GAPS.md` | 语义缺口 SG-001~SG-011 | P0 待裁决项 | 显式决策，禁止当事实 |
| `SOURCE-TYPE-ANALYSIS.md` | 来源角色与权威边界 | P0 权威边界 | 区分视觉 vs 逻辑来源 |
| `REFERENCE-TRIAGE.md` | URL 角色分流 | P0 证据去向 | 追溯证据来源 |
| `WEBSITE-COVERAGE.md` | routifyapi.com 视觉/营销证据 + 设计 token | P0 视觉/公开体验（**非**后台权威） | S5 DESIGN 视觉基线 |

> 本交接为以上 8 个 final 文件；本轮新写 5 个（FEATURE-CANDIDATES / INTEGRATION-LIST / GLOSSARY / SEMANTIC-GAPS / HANDOFF），其余 3 个为上一 subagent 已完成（未改动）。

## 2. 权威性声明（S2 必须遵守）

- **后台 / 功能 / 数据模型 / 计费逻辑 / 集成能力是否存在** → 以 **repo new-api 源码**为唯一一手权威（REPO-INSPECTION / FEATURE-CANDIDATES / INTEGRATION-LIST / GLOSSARY）。
- **视觉 / 品牌 / 营销定位 / 公开页 IA / 配色字体** → 以 **routifyapi.com 镜像**为权威（WEBSITE-COVERAGE）。
- **二者不得混淆**：网站只暴露公开体验，**不**作为后台范围权威；repo 只定逻辑，**不**作为最终视觉权威。
- 旧归档 `_archive/full-rerun-20260619-103254/**` 仅 P3 参考，**不得**作为任何下游权威。

## 3. S2 工作指引：以 repo 能力面做反向覆盖基准

> **核心要求**：S2 不得只挑「看起来重要」的功能。必须以 FEATURE-CANDIDATES 的 **FC-001~FC-126 全集** 为基准，**逐条**给出处置裁决，做到反向全覆盖、无静默漏域。

- 每个 FC 必须落一个裁决：**保留 / 改造 / 省略 / 增强**，并附理由。
- 特别盯紧 5 个旗舰能力（上一轮被漏，本轮已确证）——它们是 Routify 增长 / 差异化的核心，**默认保留**，除非有明确理由省略。
- 17 个能力域（D1~D17）每个都要在 S2 输出中有交代，不得整域消失而不说明。
- 所有 SG-xxx 缺口必须在 S2 显式决策（定位 / 商业模式 / 上线范围 / 多租户等），不得跳过。
- 商业模式锚点：营销文案「用多少付多少、没有套路」→ 倾向额度 / 用量计费为主（见 SG-008）。

## 4. 旗舰能力落点表（5/5 已确证，反 overclaim 核对锚点）

| # | 旗舰能力 | 源码确证 | FC 落点 | GLOSSARY | INTEGRATION |
|---|---|---|---|---|---|
| 1 | 签到 / 每日奖励 | controller/checkin.go + model/checkin.go + setting checkin_setting.go | FC-019~FC-023 | §E 签到 | — |
| 2 | 邀请返利 / 分销 | model/user.go(aff*) + controller/user.go | FC-024~FC-029 | §E Affiliate | — |
| 3 | Telegram 登录 | controller/telegram.go(HMAC) | FC-018 | §E Telegram | §3 Telegram |
| 4 | 异步任务中心 | model/task.go + midjourney.go + controller/task.go + relay/channel/task/* | FC-030~FC-038 | §F 任务 | §1.5 任务型 provider |
| 5 | 预填分组 | model/prefill_group.go + controller/prefill_group.go | FC-039~FC-041 | §G 预填分组 | — |

> ✅ 自检通过：5 个旗舰能力在 FEATURE-CANDIDATES 中**全部**有 FC 编号落点。

## 5. 已知缺口（非阻塞，详见 SEMANTIC-GAPS.md）

- 动态 UI 未捕获（SG-001、SG-011）：控制台/价格/API Keys/登录注册动态页。S5/S6 前补证据或标注假设。
- 定价数字未固化（SG-002）：计费机制完整，具体倍率是运营配置。
- 支付组合 / provider 上线范围 / 计费模式 待 S2 决策（SG-003、SG-005、SG-008）。
- 多租户 / 组织模型缺失（SG-006）：repo 为单租户用户/分组/角色模型。
- 部署域(io.net)、通知完整性、前端主题取舍待裁（SG-007、SG-009、SG-010）。
- 品牌 / license 边界（SG-004）：**S1 不下商用法律结论**，作为独立合规决策项上抛。

## 6. 量级核对（防漏域锚点）

- 能力域：**17**（D1~D17）。
- FC 候选：**126**（FC-001~FC-126）。
- 上游 AI provider：渠道类型编到 **57**，已命名约 **52**（超 AGENTS.md 自述 40+）。
- OAuth/登录集成：**8**；支付网关：**6**；通知渠道：**3**。
- 语义缺口：**11**（SG-001~SG-011），均非阻塞。

## 7. S1 状态

- 状态：**PASS_WITH_GAPS**（功能/视觉证据闭环；动态 UI 与定价数字为非阻塞 GAP）。
- 反 overclaim：本轮**未**对 app.routifyapi.com 任何动态页做真实访问/截图；凡涉控制台/价格/密钥 UI 的事实一律记 GAP 或以 repo 替代。
- repo **只读**，本轮**未**改动 repo 任何文件。
