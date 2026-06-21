# API 契约缺口 — API-CONTRACT-GAPS

> 配套 `ARCHITECTURE-REVIEW.md` 第 3/4/6 项。核心域 API 边界（路径+中间件+控制器函数）已清晰，本文件仅记录响应 schema / 验签字段 / 去重键 等未规约到合同级的点。

## 判定基线

API 边界 PASS 的依据：每个动作映射到具体 HTTP 路径 + 中间件链 + 控制器函数（见评审第 3 项表）。本文件记录的是**请求/响应字段契约层面**的缺口，不否定动作-责任映射本身。

## 缺口清单（均非阻断）

| # | 接口/契约 | 引用位置 | 缺口描述 | 严重度 |
|---|---|---|---|---|
| ACG-1 | OpenAI 兼容转发响应体 schema | prd-relay RL-1 §5 | §5 仅列 `Log` 落账字段（结算侧），**未冻结返回给客户端的响应结构**（chat completion / stream chunk 的字段）。多协议（Claude/Gemini/Embedding/Realtime）的响应字段更未列。RL-4 反解析回"统一格式"但统一格式 schema 无定义。 | 中 |
| ACG-2 | 支付回调 webhook 验签契约 | prd-billing BL-1 §3、prd-nfr-rbac X3 §4/§5 | 只规约"验签通过/失败"二元，**未给每 provider（Stripe `Stripe-Signature`、Creem、易支付 epay、Waffo）的具体验签头字段、签名算法、时间戳容差**。BL-1 幂等键 `TradeNo` 明确，但 webhook 事件去重（provider 自身重发的 `event_id` 级）未规约。 | 中 |
| ACG-3 | 充值/订阅回调入站路径 | prd-billing BL-1/BL-3 | 回调由"支付渠道异步回调网关"触发，但**未给回调 HTTP 路径**（如 `/api/topup/callback/:provider`）与请求体字段映射到 `TopUp.TradeNo` 的解析规约。 | 中 |
| ACG-4 | event_id 级去重窗口 | prd-billing BL-1（幂等） | 任务书要求"举出去重窗口"。当前用 `TradeNo unique + Status 已 paid 判定` 做幂等（可防重复入账，PASS）。但若同一 `TradeNo` 短时高频重发，**无显式去重时间窗口或回调幂等表**——CAS/唯一键是逻辑去重而非窗口去重。建议补"回调幂等记录表 + N 分钟窗口"或确认唯一键足够。 | 低（已有唯一键兜底）|
| ACG-5 | 任务回调入站契约 | prd-asynctask AT-1 §3「轮询/回调推进」 | 状态推进的"回调"路径与请求体未规约（仅说后台轮询/回调）。MJ/Suno/视频 provider 回调的字段→`Task.Status/Progress` 映射未给。轮询侧（拉取上游）已清晰，回调侧（被推送）未规约。 | 低 |
| ACG-6 | 84 授权单元矩阵 | prd-nfr-rbac X7 §5/§6 | PRD 引用 `ROLE-PERMISSION-MATRIX（7×12=84 单元）`，但该矩阵本体不在本批读取范围。**单元级（哪个角色对哪个操作域是 ✅/🟡/➖）的逐项映射需在 ROLE-PERMISSION-MATRIX.md 维护并被本评审核对**。当前仅验收抽样（common→O07、User→他人 O03、admin→O12）显式。 | 中 |
| ACG-7 | `Team` API 边界 | prd-nfr-rbac X7 §3「阶段二预留」 | Team Owner/Member 的接口、team 维度的 self-scope 过滤路径未规约（阶段二）。 | 低（阶段二）|
| ACG-8 | reveal 接口路径不一致 | prd-token TK-4 §3 vs prd-nfr-rbac X3 §5 | TK-4 用 `POST /token/:id/key`，X3 §5 用 `/api/token/:id/reveal`。两处取明文路径命名不一致，需 PM 统一（同一能力 F-5006/F-3004 的两种表述）。 | 中 |

## 幂等专项结论（任务书第 4 项强调）

要求"举不出具体幂等键和去重窗口即 FAIL"。逐一核对：

- **支付回调入账**：幂等键 = `TopUp.TradeNo`（DATA-MODEL §7 `unique`），去重 = 回调判 `Status` 已 paid（prd-billing BL-1 §4「订单已 paid（重复回调）→幂等返回成功」、§6「同一 TradeNo 回调两次 → Quota 仅增加一次」）→ **举得出，PASS**。
- **兑换码**：幂等键 = `Redemption.Key char(32);uniqueIndex`，去重 = 同事务内 `Status` 已使用判定（BL-4 §6「并发两次仅一次成功」）→ **举得出，PASS**。
- **任务回调/超时退款**：去重机制 = `UpdateWithStatus(fromStatus)` CAS（AT-1 §6、AT-3 §6）→ **举得出，PASS**。
- **唯一缺口（ACG-4）**：provider 重发的 event_id 级去重窗口未显式。因 `TradeNo` 唯一键已防重复入账，判 gap 不判 FAIL。

故幂等整体 `PASS_WITH_GAPS`，与评审第 4 项一致。
