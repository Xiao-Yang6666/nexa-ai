# SOURCE-TYPE-ANALYSIS — 来源判定与权威边界

> S1 重跑产物。本文件声明每个证据来源的角色与权威边界，下游阶段必须据此区分「视觉/体验来源」与「功能/逻辑来源」，不得混淆。
> 旧产物（`_archive/full-rerun-20260619-103254/**`）仅作 P3 参考证据，**不得**作为下游权威。

## 1. 来源角色表

| 来源 | 角色 | 权威级别 | 可用于 | 不得用于 |
|---|---|---|---|---|
| 参考网站 https://routifyapi.com/ | 视觉 / UX / 信息架构 / 营销定位 / 公开转化路径 | P0（公开体验、视觉方向） | 首页结构、导航、CTA、配色/字体 token、产品文案、公开页 IA | 后台/控制台/管理端范围（除非有动态证据）、计费/渠道真实逻辑 |
| 开源仓库 new-api（`repo/new-api/`，QuantumNous/new-api） | 功能 / 领域逻辑 / 数据模型 / API / 架构 | P0（领域逻辑、后台能力是否存在） | 路由、控制器、模型结构、计费/限流/路由算法、管理端能力清单 | 最终视觉风格、品牌、文案 |
| 旧归档运行 `_archive/full-rerun-20260619-103254/**` | 上一轮失败产物 | P3 参考 | 仅作交叉检查参考 | 任何下游权威 |
| 产品综合判断 | 综合 | P0（评审后最终范围） | 决定保留/改造/省略/增强 | 把假设当成已验证的来源事实 |

## 2. 各来源摄取动作

### 2.1 参考网站（routifyapi.com）— 视觉/营销来源
- 性质：营销首页 + 用户协议 + 隐私政策三页（静态/SSR 渲染证据已镜像）。
- 已抓取证据：`01_ingestion/evidence/website-mirror/routifyapi/`（mirror-manifest.json、rendered/pages.jsonl、3 张截图、3 份 DOM、extracted/* 9 份）。
- 动作：提取首页 sections、导航、CTA 文案、`cssVars` 设计 token、产品定位文案。
- 关键事实：营销页**只暴露公开体验**，控制台/价格/API Keys 均跳转到 `app.routifyapi.com`（动态域，未访问）→ 记为非阻塞 GAP。
- 站点页脚自述「基于 New API」，文档外链 `docs.newapi.pro` → 确证「视觉壳=Routify，底层逻辑=New API」。

### 2.2 开源仓库 new-api — 功能/逻辑来源
- 性质：Go（Gin+GORM）AI API 网关，分层 Router→Controller→Service→Model，relay/ 下各 provider adapter，前端 React。
- 摄取动作（本轮穷尽）：系统遍历 `controller/*.go`（69 个）、`model/*.go`（39 个）、`router/api-router.go` + `router/relay-router.go`、`setting/**`、`relay/channel/*`（37 个顶层 adapter）、`pkg/billingexpr/expr.md`、`constant/*`。
- 产出：见 REPO-INSPECTION.md（产品域全景）、FEATURE-CANDIDATES.md（FC 候选）、INTEGRATION-LIST.md（集成点）、GLOSSARY.md（黑话）。

## 3. 上一轮漏域纠正声明（本轮硬要求）

上一轮 S1 在 repo grep 命中 0、被静默漏掉的 5 个旗舰能力，本轮已逐一去源码确认其真实存在并写入证据（落点见各 final 文件，汇总见 HANDOFF.md「旗舰能力落点表」）：

| # | 旗舰能力 | 源码确证文件 | 状态 |
|---|---|---|---|
| 1 | 签到 / 每日奖励 | `controller/checkin.go` + `model/checkin.go` + `setting/operation_setting/checkin_setting.go` | ✅ 确证存在 |
| 2 | 邀请返利 / 分销(aff) | `model/user.go`（aff_code/aff_count/aff_quota/aff_history/inviter_id）+ `controller/user.go`（GetAffCode/TransferAffQuota） | ✅ 确证存在 |
| 3 | Telegram 登录 / Bot | `controller/telegram.go`（TelegramLogin/TelegramBind/checkTelegramAuthorization HMAC） | ✅ 确证存在 |
| 4 | 异步任务中心（MJ/Suno 追踪/列表/进度/重试） | `model/task.go` + `model/midjourney.go` + `controller/task.go` + `controller/midjourney.go` + `relay/channel/task/*` | ✅ 确证存在 |
| 5 | 预填分组 prefill_group | `model/prefill_group.go` + `controller/prefill_group.go` + router `/api/prefill_group` | ✅ 确证存在 |

## 4. 动态证据 GAP（非阻塞，记入 PASS_WITH_GAPS）
- `app.routifyapi.com` 控制台 / 价格 / API Keys 页未做动态访问（跳转外域，需登录）。
- 营销页登录/注册流程仅见 CTA「登录」按钮，未捕获动态表单截图。
- 这些 GAP 不阻塞 S2 需求拆解（后台能力以 repo 为权威来源），但 S5/S6 设计/原型前必须补动态证据或显式标注 UI 假设。
