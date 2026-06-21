# S1-REVIEW-REPORT — S1 摄入阶段独立质量评审

> 评审者：独立 reviewer（窄上下文，不预设 S1 正确）。
> 评审对象：`01_ingestion/final/` 全部产物 + 机械门 `repo_capability_coverage.py` 的 repo_domain_blind=33 发现。
> 证据均一手核对 `repo/new-api/` 源码。本报告**不修改**任何 producer 产物，仅做裁决 + 评审 + 写 CAPABILITY-DROP-LEDGER.md。
> 日期：2026-06-19。

---

## 0. 结论速览

- **S1 评分：8 / 10** — 能力面摄入扎实、证据一手、旗舰全中、交接契约清晰；扣分点为机械门暴露的 2 个真漏功能面（codex_usage / video_proxy）未进 FC 候选池。
- **一句话结论**：本轮 S1 相比上一轮（25 FC / 8 provider / 漏 5 旗舰）是**实质性、量级级别的改善**，质量已达可进 S2 标准，仅需 producer 补 2 个被漏的非旗舰功能面 FC 即可闭环。
- **进 S2 判定：PASS_WITH_GAPS** — 可进 S2，但需先回填 2 个 BLOCKER FC（codex_usage→FC-127、video_proxy→FC-128），否则反向覆盖基准不完整。

---

## 1. 机械门 repo_domain_blind=33 处置结果

机械门判 `REWORK_REQUIRED`（repo_flagship_blind=0 但 repo_domain_blind=33）。逐个裁决见 `final/CAPABILITY-DROP-LEDGER.md`（33 行可机械核对）。

### 33 域裁决汇总

| 裁决 | 数量 | 说明 |
|---|---|---|
| **drop — 测试代码** | 10 | *_test.go 单元测试，非产品能力 |
| **drop — 内部基础设施** | 9 | 缓存实现/错误常量/DB 工具/定价刷新等，无独立产品功能面 |
| **已覆盖**（FC/INTEGRATION 已含，仅文件名没对上） | 12 | 支付 provider 适配 + webhook 可用性 + 回跳路径 + swagger video + gemini 视频分支 |
| **补candidate — 真漏功能面（BLOCKER）** | 2 | codex_usage、video_proxy |
| **合计** | **33** | 10+9+12+2=33 ✅ |

### drop 明细（19 个，可审计）
- 测试（10）：channel_test_internal_test, channel_upstream_update_test, model_list_test, model_owned_by_test, model_owner_test, payment_method_guard_test, payment_webhook_availability_test, task_cas_test, token_test, topup_waffo_pancake_test
- 内部基础设施（9）：channel_cache, channel_satisfy, db_time, errors, model_extra, pricing_default, pricing_refresh, token_cache, user_cache

### 已覆盖明细（12 个）
- 支付 topup（4）：topup_creem/stripe/waffo/waffo_pancake → FC-061 + INTEGRATION §4/§6
- 支付订阅（4）：subscription_payment_creem/epay/stripe/waffo_pancake → FC-063 + INTEGRATION §4
- 支付辅助（2）：payment_webhook_availability（INTEGRATION §6）、return_path（FC-061/063 内部工具）
- 视频（2）：swag_video → FC-036；video_proxy_gemini → 随 video_proxy（FC-128）涵盖

### BLOCKER — 真漏功能面（2 个，须 producer 补 FC）

1. **codex_usage** → 建议补 **FC-127**（D7 渠道管理）
   - `GET /channel/:id/codex/usage` 查询 Codex 渠道上游用量/配额。
   - 证据：controller/codex_usage.go:20 `GetCodexChannelUsage`；router/api-router.go:253。
   - FC-067 仅覆盖 codex 亲和缓存 header 模板，**不含**此用量查询端点。

2. **video_proxy** → 建议补 **FC-128**（D5 任务 / D13 Relay）
   - `GET /videos/:task_id/content` 经网关代理/下发生成视频内容（含 gemini 分支）。
   - 证据：controller/video_proxy.go:33 `VideoProxy`；video_proxy_gemini.go:15；router/video-router.go:16。
   - FC-036/FC-038 覆盖视频「生成」与「产物展示」，**不含**「内容代理下发」服务端点。

> 两者均为非旗舰、niche，但属真实独立功能面。按机械门规则不可静默 drop。本 reviewer 不改 FC 产物，BLOCKER 上抛 producer。

---

## 2. S1 整体质量评审

### 2.1 126 个 FC 是否真覆盖 newapi 产品能力面（有无凑数/重复）

**判定：覆盖扎实，无明显凑数/重复，但有 2 处可挑剔。**

- 抽查 FC 证据文件全部存在：controller/deployment.go、pkg/ionet/*、controller/uptime_kuma.go、controller/prefill_group.go、controller/checkin.go、pkg/billingexpr/expr.md、controller/playground.go 均一手确证（repo 实有 controller 62 个、model 36 个非测试文件）。
- D1~D17 全 17 域均有 FC 落点；旗舰 5 个在 FC 中全部有编号（自检表 FEATURE-CANDIDATES.md:221-229 锚定）。
- **轻微结构瑕疵（非阻塞）**：FC-018 Telegram 归在 D1 表内，但 REPO-INSPECTION.md:35 把 Telegram 列为独立 D4；FEATURE-CANDIDATES 跳过 D4 编号（D3→D5），存在域编号与 REPO-INSPECTION 不一一对应。能力本身已覆盖（HANDOFF §4 旗舰表确证），仅编号体系不齐，建议 S2 知悉。
- **覆盖缺口（即 §1 的 2 个 BLOCKER）**：codex_usage / video_proxy 两个真功能面未进 126 池——这正是机械门的价值所在，被本评审定位。

### 2.2 INTEGRATION-LIST 52 provider 是否真实（抽查 constant/channel.go）

**判定：真实，量级声明保守准确。**

- 一手核对 constant/channel.go：`ChannelType*` 常量 53 个（含 Unknown=0），加 `ChannelCloudflare=39`（命名不带 Type 前缀），实际命名 provider **≈54 个**（编到 ChannelTypeCodex=57，ID 28/29/30/32 空缺）。
- INTEGRATION-LIST.md:5/153 声明「已命名约 52 个」属**保守且准确**（未 overclaim）。ChannelTypeNames map 共 54 条（constant/channel.go:123 起）。
- §4 支付（6 网关）、§3 OAuth（8 类）均与 §1 处置一致并一手可溯。

### 2.3 GLOSSARY 是否覆盖核心黑话

**判定：核心黑话全覆盖，质量高。**

- **表达式计费（billingexpr）**：GLOSSARY.md:34 完整列出变量 p/c/cr/cc/cc1h/img/ai/ao/len + 函数 tier/param/header/has/hour + 版本前缀 v1: + p/c 自动排除规则，证据 pkg/billingexpr/expr.md。✅
- **亲和缓存（Affinity Cache）**：GLOSSARY.md:59 含会话键来源（Codex prompt_cache_key / Claude metadata.user_id）+ SwitchOnSuccess/TTL/MaxEntries/SkipRetryOnFailure。✅
- **跨分组重试（Cross-Group Retry）**：GLOSSARY.md:58 含「仅 auto 分组有效 + 优先级耗尽切下一组 + 令牌级开关」。✅
- 另含预扣/BillingSnapshot、优先级+权重路由、Ability、ModelMapping、Multi-Key、阶梯计费等全部核心词，共 9 大类。

### 2.4 与上一轮对比（25 FC / 8 provider / 漏 5 旗舰）

**判定：量级级别的实质改善。**

| 维度 | 上一轮 | 本轮 | 改善 |
|---|---|---|---|
| FC 候选 | 25 | 126 | +404%，能力面穷尽度大幅提升 |
| provider | 8 | ~52（一手核对≈54） | 覆盖国际+国内+自部署+聚合+任务型全集 |
| 旗舰能力 | 漏 5 | 0 漏（flagship_blind=0 机械门确证） | 关键缺陷修复 |
| 交接契约 | — | HANDOFF 含反向覆盖基准 + 旗舰锚点表 | 新增 |

- 本轮 SOURCE-TYPE-ANALYSIS / HANDOFF 明确写明「纠正上一轮静默漏 5 旗舰」（HANDOFF.md:4），且机械门独立验证 repo_flagship_blind=0，改善可信非自述。

### 2.5 HANDOFF 是否给 S2 足够的反向覆盖基准

**判定：充分，是本轮最强项之一。**

- HANDOFF.md:30-35 明确要求 S2 以 FC-001~126 全集为基准**逐条**裁决（保留/改造/省略/增强），禁止只挑「看起来重要」的。
- §4 旗舰落点表（5/5）给出反 overclaim 核对锚点；§6 量级核对（17 域 / 126 FC / 52 provider / 8 OAuth / 6 支付 / 3 通知 / 11 SG）作为防漏域硬锚。
- §2 权威性声明清晰隔离「repo=后台逻辑权威」vs「routifyapi.com=视觉权威」，杜绝 S2 混淆。
- 唯一待补：HANDOFF §6 量级核对写「FC 候选 126」，待补 FC-127/128 后应同步更新为 128。

---

## 3. 进 S2 判定

**PASS_WITH_GAPS**

- **可进 S2 的理由**：能力面摄入穷尽、证据一手、旗舰 0 漏、provider 量级真实、GLOSSARY 核心黑话齐、HANDOFF 反向覆盖基准充分，11 个 SG 缺口均明确标为非阻塞且指派了处置阶段。
- **GAPS（须 producer 在进 S2 前回填，非 reviewer 职责）**：
  1. 补 **FC-127 codex_usage**（D7）。
  2. 补 **FC-128 video_proxy**（含 gemini 分支，D5/D13）。
  3. 同步 HANDOFF §6 / FEATURE-CANDIDATES 末行 FC 总数 126→128。
  4. （可选）对齐 FC 域编号与 REPO-INSPECTION（Telegram D4 缺号）。
- 回填上述 2 个 FC 后，机械门 repo_domain_blind 将清零（其余 31 域已在 DROP-LEDGER 可审计裁决），可升级为 PASS。

---

## 4. 关键证据索引（文件名:行号）

- 33 域全列：脚本 `repo_capability_coverage.py --json` 输出 `domain_blind[]`。
- 旗舰 0 漏：脚本 `counts.repo_flagship_blind=0`。
- codex_usage 漏：controller/codex_usage.go:20 + router/api-router.go:253 vs FEATURE-CANDIDATES.md:113(FC-067 仅 header 模板)。
- video_proxy 漏：controller/video_proxy.go:33 + router/video-router.go:16 vs FEATURE-CANDIDATES.md:62/64(FC-036/038 仅生成+展示)。
- provider 量级：constant/channel.go:4-57（53 ChannelType*）+ :39(ChannelCloudflare) vs INTEGRATION-LIST.md:5/153(约 52 保守)。
- 表达式计费黑话：GLOSSARY.md:34 vs pkg/billingexpr/expr.md。
- 亲和缓存：GLOSSARY.md:59 / FEATURE-CANDIDATES.md:108-115(D9)。
- 跨分组重试：GLOSSARY.md:58 / FEATURE-CANDIDATES.md:117-123(D10)。
- 支付全覆盖：INTEGRATION-LIST.md:110-139(§4/§6) 覆盖 12 个支付域文件。
- 反向覆盖基准：HANDOFF.md:28-35。
- 旗舰自述修复：HANDOFF.md:4 + SOURCE-TYPE-ANALYSIS §3。

---

## 5. 评分明细（8/10）

| 维度 | 分 | 说明 |
|---|---|---|
| 能力面穷尽度 | 1.7/2 | 126 FC 覆盖 17 域，但漏 codex_usage/video_proxy 2 功能面（-0.3） |
| 证据一手性 | 2/2 | 抽查 FC/provider/黑话证据全部源码可溯，无 overclaim |
| 旗舰覆盖 | 2/2 | flagship_blind=0 机械门独立确证，修复上一轮缺陷 |
| 文档质量/交接 | 1.5/2 | GLOSSARY/HANDOFF/SEMANTIC-GAPS 高质量；FC 域编号与 REPO-INSPECTION 略不齐 + FC 总数待回填同步（-0.5） |
| 缺口诚实度 | 0.8/1 | 11 SG 显式标注非阻塞；但 2 个真漏功能面靠机械门才暴露，S1 自检未捕获（-0.2） |
| **合计** | **8.0/10** | |
