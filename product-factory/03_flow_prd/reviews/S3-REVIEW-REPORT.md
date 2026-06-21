# S3 流程设计独立质量评审报告

> 评审对象：`03_flow_prd/final/`（14 个 FL-*.md / 78 张图、OVERALL-FLOW、BUSINESS-MAIN-FLOW、EXCEPTION-FLOW、PAGE-STATE-MATRIX、FLOW-COVERAGE.csv）
> 评审方式：独立 reviewer，窄上下文，不假设 S3 正确。机械门已过（diversity=1.0，77/78 骨架不同），本轮验证「图是否画对、够不够」——判定菱形是否对应真实业务规则、是否覆盖分支+异常+终态、屏幕状态清单能否驱动 S4/S6。
> repo 核对：`repo/new-api/`（只读）

---

## 一、总评

**评分：9 / 10**

**一句话结论**：S3 已从上一轮「48 场景塌缩成 7 模板、diversity 15%、happy-path-only」彻底翻盘——判定菱形对应真实代码规则（多处可逐行核对且行号精确命中）、分支/异常/终态完整、屏幕状态清单机械可消费，是高质量、可直接驱动 S4/S6 的流程产物。扣 1 分仅因若干横切功能（如 Key 二次验证闸门、数据驻地标注）在矩阵中以「态」形式被吸收而无独立交互图，存在被 S4 漏接的轻微风险。

**进 S4 结论：PASS**

---

## 二、与上一轮对比（核心结论）

| 维度 | 上一轮致命病 | 本轮实测 |
|---|---|---|
| 骨架多样性 | 7 模板复用、diversity 15% | 78 图，sequence/state/flowchart(TD/LR/TB) 按语义选型，无模板复用 |
| 业务真实性 | 只改 start 标签、菱形空洞 | 判定菱形对应真实代码常量/函数，多处行号精确命中 |
| 分支/异常 | happy-path-only | 每图含异常分支 + 终态，error/term/norm 三类节点分色 |
| 屏幕状态 | 无或凑数 | 7 列矩阵（默认/loading/空/成功/失败/拦截/特殊），63 页面可溯源 |

**本轮是实质改善，不是凑骨架差异掩盖业务空洞。** 这是判定其与上一轮根本不同的最关键证据：抽查的菱形条件在 repo 中被逐行验证为真。

---

## 三、维度逐项核查（带证据）

### 维度 1：核心域流程图判定菱形 vs 真实业务规则 —— 通过（强证据）

**抽查 6 张核心域图 + repo 逐行核对：**

- **FL-billing BL-2（预扣→结算）** — 菱形与代码精确一致：
  - 图 `pc_q0{userQuota <= 0?} → 403 SkipRetry+NoRecordErrorLog`
    ⇔ `service/pre_consume_quota.go:38` `if userQuota <= 0` → `NewErrorWithStatusCode(..., http.StatusForbidden, ErrOptionWithSkipRetry(), ErrOptionWithNoRecordErrorLog())`【精确命中，含 NoRecordErrorLog 细节】
  - 图 `pc_q1{userQuota - preConsumed < 0?} → 403 预扣费额度失败`
    ⇔ `pre_consume_quota.go:41-42` `if userQuota-preConsumedQuota < 0` → "预扣费额度失败"【精确命中含中文错误文案】
  - 图 `pc_trust{信任令牌? ...} → preConsumedQuota=0`
    ⇔ `pre_consume_quota.go:53-55` `if tokenQuota > trustQuota { preConsumedQuota = 0 }`【命中】

- **FL-billing BL-3（订阅生命周期 stateDiagram）** — 钱包兜底分支精确命中：
  - 图 note `阻止用钱包兜底 (subscription.go:844)`
    ⇔ `model/subscription.go:847 UserActiveSubscriptionsAllowWalletOverflow`，:854 `Where(... allow_wallet_overflow = false)`，:859 `return strictCount == 0`【行号 844 指向该函数注释块起点，精确】
  - `service/billing_session.go:427-431` 证实「ErrorCodeInsufficientUserQuota 时仅当 allowOverflow 才回退钱包」分支真实存在。

- **FL-relay RL-2（Path2RelayMode 前缀顺序）** — 关键「顺序敏感」菱形命中：
  - 图强调 `/v1/responses/compact 必须先于 /v1/responses 匹配`
    ⇔ `relay/constant/relay_mode.go:75` `/v1/responses/compact` 判断在 :77 `/v1/responses` **之前**【顺序精确命中——这是只有真读代码才能画对的细节】
  - 图 `/v1/edits 独立 RelayModeEdits` ⇔ relay_mode.go:73-74【命中】

- **FL-channel CH-3（自动禁用 stateDiagram）** — `ChannelStatusAutoDisabled + NotifyRootUser`、「手动禁用不自动恢复」分支
  ⇔ `service/channel.go:28 UpdateChannelStatus(..., ChannelStatusAutoDisabled, ...)`；恢复仅对 AutoDisabled 状态【命中，service/codex_credential_refresh_task.go:32 印证仅 Enabled/AutoDisabled 参与自动状态流转】

- **FL-asynctask AT-1（状态机轮询 CAS）** — 中心机制「UpdateWithStatus CAS / RowsAffected>0 才赢」
  ⇔ `model/task.go:411-412` `UpdateWithStatus(fromStatus)` = `DB.Model(t).Where("status = ?", fromStatus).Select("*").Updates(t)`；:408-410 代码注释甚至印证图所述「禁用无守卫 bulk update」的缘由（Save 会 fallback 到 INSERT ON CONFLICT 绕过 CAS）【精确命中含设计意图】
  - 终态判定（SUCCESS/FAILURE/UNKNOWN 不可被普通 bulk 覆盖）真实。

- **FL-relay RL-5（视频代理 SSRF）** — `ValidateURLWithFetchSetting`、本人校验、Status==Success、data: 直出、404/403 分支——节点用真实函数名，结构合理（未逐行核 video_proxy.go 但函数名与 dto 一致，归一般可信）。

**结论：判定菱形绝大多数对应真实业务规则，关键且易错的细节（前缀顺序、CAS 守卫缘由、NoRecordErrorLog、行号 844）均精确命中，排除「凑骨架差异但业务空洞」的可能。**

### 维度 2：屏幕状态清单可消费性 —— 通过

每张图后均配「屏幕状态清单」，逐条标注 `← 异常 / ← 终态 / ← 内部`。抽查：
- BL-1 充值：含 待支付/合规拒绝/超时/验签失败/幂等成功/入账成功（默认+异常+终态俱全）
- GR-1 签到：今日未签到（默认）/写入中（loading）/签到成功/已签到（置灰）/人机校验失败/未启用 —— 默认+loading+成功+失败+拦截齐全
- AT-1 任务：NOT_START（默认）/进行中（loading 递增）/SUCCESS/FAILURE/UNKNOWN/CAS 回退（内部）

PAGE-STATE-MATRIX 把 81 个 FL 场景 + F-4037 汇成 63 行 × 7 列矩阵，列定义机械（默认/loading/空/成功/失败/权限拦截/特殊），每行带来源场景号（如 `FL-account AC-1`）。**可直接驱动 S4 页面清单与 S6 状态分镜。**

### 维度 3：OVERALL-FLOW 跨端跨模块 + 角色切换 —— 通过

- subgraph 分端：PUB（访客）/AUTH（访客→用户）/CONSOLE（用户/开发者）/RELAY（系统）/ADMIN（运营/运维/Root）
- 角色切换边真实存在：`P4/P5 -->|未登录先登录| G1`、`G7 --> U1`、`CALL --> R1`、`R4 --> U6`、`A3 -.影响.-> R1`、`A5 -.开关控制.-> G2`
- 4 条主干路径（MP-1~MP-4）+ 6 条主异常路径（EX-1~EX-6）+ 5 个跨切面契约（C1~C5）
- **非 happy-path-only，无断边。** 一般观察：RELAY 子图压缩为 R1-R4（细节下沉 FL-relay，合理）。

### 维度 4：EXCEPTION-FLOW 8 类异常 vs API 网关要害 —— 通过（强）

精确命中任务点名的 4 个要害：
- **渠道全挂**：EX-C 覆盖候选集空 / 优先级逐层降级到底 / auto 分组全组耗尽 / 亲和 SkipRetryOnFailure（CH-2+CH-5+RL-1）
- **支付回调**：EX-D 两段式「回调验签后才入账」+ 订单号幂等 + 伪造回调丢弃 + pending 可重发
- **上游限流**：EX-F `ShouldRetryByStatusCode` 重试判定 + AutoBan DisableChannel + MaskSensitiveError 脱敏 + 按 RelayFormat 分别构造错误
- **额度**：EX-B 复用已验证的 `userQuota<=0` / `userQuota-preConsumed<0` 403 SkipRetry + 失败全额返还 + 多退少不补

每类含触发条件、跨场景出现位置（带 FL 场景号）、Mermaid 处理图、文字策略。**抓住了 API 网关产品的要害，不是泛化模板。**

### 维度 5：PAGE-STATE-MATRIX 63 页面 290 状态真实性 —— 通过

抽查 GR-1/BL-1/AT-1/RL-1 行，状态均差异化、可溯源，非凑数。汇总统计诚实可信（如 loading 态仅 18/63，并注明「纯校验链/状态机页面无 loading」；空态 16/63 集中于列表/看板）——**这种带约束的诚实分布而非"每行都满 7 列"的造假，反证矩阵是真遍历产出。**

### 维度 6：FLOW-COVERAGE 204/231，未覆盖 27 个 —— 通过

未覆盖的 27 个**全部为 F-5xxx**，逐行核对 S2 `FUNCTION-LIST.csv:206-232`，类别确为：
- NFR（F-5001~5005：延迟埋点/压测/健康看板/SLA/容灾）
- Security（F-5006~5009：Key 掩码/加密存储/日志脱敏/审计）
- Observability（F-5010~5012：Prometheus/告警/trace）
- Scalability（F-5013~5015：无状态横扩/缓存命中/日志归档）
- Compliance（F-5016~5021：数据分级/留存/合规分组/驻地/注销/同意闸门）
- RBAC（F-5030~5035：权限组/三级鉴权/self-scope/二次验证/矩阵/团队预留）

均为横切关注点，多数无独立交互图。**判定合理。**

### 维度 7：跨阶段 5 旗舰功能流程图齐备 —— 通过

| 旗舰功能 | 流程图 |
|---|---|
| 签到 | FL-growth GR-1（状态机）/GR-2（统计）/GR-3（配置） |
| 分销/邀请 | FL-growth GR-4（返利全链）/GR-5（额度划转） |
| Telegram | FL-account AC-6（HMAC 登录）/AC-7（绑定唯一性） |
| 异步任务 | FL-asynctask AT-1~AT-5（状态机/列表/超时扫描/退款/MJ 路由） |
| 预填 | FL-prefill PF-1~PF-3（创建/更新/填充软删） |

**5 旗舰全部有流程图。**

---

## 四、问题清单

### 严重问题
- **无。** 未发现「凑骨架差异但业务空洞」「漏分支/异常」「菱形与代码矛盾」的严重缺陷。

### 一般问题（不阻塞进 S4，建议 S4 关注）

1. **横切功能在矩阵中以"态"吸收，存在 S4 漏接风险（一般）**
   F-5006（Key 掩码与受控取明文）实际有明确用户交互，仅作为 `PAGE-STATE-MATRIX D 区·令牌密钥访问 TK-4` 的「单个明文一次性展示态（DisableCache）」+「受控明文安全升级」存在；F-5033（二次验证闸门）作为契约 C3 散落各处。这些在 FLOW-COVERAGE 中标记为 `no/未覆盖`，但又确实在矩阵里有状态痕迹。建议 S4 显式确认这些受控明文/二次验证交互页不被漏接（它们不是纯后端 NFR）。

2. **F-5019（数据出境告知与驻地标注）为 P0 且"公开可见 / 价格页与控制台可见"，含用户可见 UI（一般）**
   被归入 27 个未覆盖横切，但其本质是公开页/价格页的标注展示，带可见性需求。EXCEPTION-FLOW EX-H 与 PAGE-STATE-MATRIX 公开价格页行未显式列出"驻地标注态"。建议 S4 在价格页/控制台信息架构中补该展示要素。

3. **F-5021（隐私同意闸门，P0，公开闸门）未在流程中显式成图（一般）**
   "未接受含出境与留存条款的协议不可调用"是一个真实的前置拦截闸门，理论上应在 OVERALL-FLOW 或 EX-H 出现一个判定。当前仅 EX-D 提到 C7 支付合规闸门，隐私同意闸门未单列。建议 S4/S6 补一个首次调用前的同意拦截态。

4. **OVERALL-FLOW 自述为"S3 第一批主干"（一般/说明性）**
   该大图聚焦公开/账号/增长/令牌/用量主链路，Relay/计费/渠道运营链路压缩。这是有意的分层（细节下沉 FL），不算缺陷，但 S4 做整体信息架构时需同时读 OVERALL-FLOW + 各 FL，不能只看大图。

---

## 五、最终裁定

- **评分：9/10**
- **进 S4：PASS**
- **理由**：判定菱形对应真实业务规则（多处 repo 行号精确命中，含只有真读代码才画得对的顺序/守卫细节）；分支+异常+终态完整无 happy-path-only；屏幕状态清单 7 列机械可消费，可直接驱动 S4 页面状态与 S6 原型；8 类异常抓住 API 网关要害；27 个未覆盖确为横切 NFR/合规/RBAC；5 旗舰功能全部成图。相比上一轮（diversity 15%/套模板/happy-path）为实质性、可验证的翻盘。3~4 个横切型 P0（Key 取明文/驻地标注/隐私同意闸门）建议 S4 显式接住，但不构成进入 S4 的阻塞项。
