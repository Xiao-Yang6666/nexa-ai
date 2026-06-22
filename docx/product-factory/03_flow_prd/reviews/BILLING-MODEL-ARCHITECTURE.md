# BILLING-MODEL-ARCHITECTURE — 成本/售价分离计费 + 品质分级 + 分组折扣 + 模型全开（增量主架构）

> 角色：S3/S4 架构（只设计架构，不写代码、不画 UI）。
> 范围：在 **COMPAT-LAYER-ARCHITECTURE.md**（协议兼容层 + 两层模型映射 C→A→B）已落定的基础上，**增量补全计费/分级/分组**。本轮不推翻兼容层，只在它的链路里插入「售价/成本分离 + 分组折扣 + key 级减法约束」。
> 最高权威：`COMPAT-BILLING-DECISIONS.md`（用户拍板业务规则）。本文件落 DECISIONS 第 3/4/5/6/7/8/9/10 节。
> 设计铁律：**基于现有 new-api 计费内核**，明确「复用什么 + 新增什么」，每个决策可落到字段/主键/公式/执行顺序。
> 锚定证据（已 inspect 现网代码）：
> - `relay/helper/price.go::ModelPriceHelper`：售价端定价内核，读 `info.OriginModelName`（= 模型名）→ `GetModelPrice`/`GetModelRatio`，乘 `groupRatioInfo.GroupRatio`，产出 `info.PriceData`。**在 Distribute 选渠之后调用**（`controller/relay.go:153`）。
> - `setting/ratio_setting/group_ratio.go::GetGroupRatio(name)`：分组倍率内核，KV `group_ratio_setting`，默认 `default/vip/svip` 各 1.0，可后台配。**这正是 DECISIONS §6 折扣系数的现成承载**。
> - `setting/ratio_setting/model_ratio.go::GetModelRatio/GetModelPrice`：模型级倍率/价格表（全局，按模型名）。
> - `service/quota.go::PostAudioConsumeQuota / PostWssConsumeQuota → SettleBilling + model.RecordConsumeLog`：结算 + 落 Log 终点。
> - `types/price_data.go::PriceData{ModelRatio, ModelPrice, GroupRatioInfo, ...}`：每笔请求计费快照，挂 `RelayInfo.PriceData`。
> - `model/channel.go` Channel（DATA-MODEL §3）：有 `Type/Models/ModelMapping/Setting(text)`，**无任何「按模型的成本倍率」承载字段**。

---

## 0. 一句话定位与四个增量

DECISIONS §0：「对内成本/售价分离、每笔利润算得清、按等级折扣售卖」。落到架构 = 四个增量，全部挂在兼容层 C→A→B 链路上：

| 增量 | DECISIONS | 落点（复用/新增） |
|---|---|---|
| **售价挂对外模型 A** | §3 §4 §6 | 复用 `GetModelPrice/GetModelRatio`（按 A 取价）；新增 `PublicModel` 表统一承载「对外名 A + 品质标签 + 基准售价倍率 + 上下架」 |
| **成本挂渠道×真实模型 B** | §4 | Channel **无承载字段** → **新增表 `ChannelModelCost`**（渠道id+模型B+成本倍率+生效） |
| **分组退化为纯折扣系数** | §6 §5 | **复用** `GetGroupRatio`（`group_ratio` KV），语义改为纯折扣；砍掉「分组圈模型」 |
| **模型全开 + key 级减法约束** | §5 §7 | 模型全开 = 不再用分组过滤模型；保留 **复用** `Token.ModelLimits` 作「客户自我约束、默认全开」减法校验 |

外加：**Log 利润字段扩展**（§8，在兼容层 Log 扩展基础上再加 售价/成本/利润）+ **利润看板数据来源**（§9）。

---

## 1. PublicModel（对外模型表）与 PlatformModelMapping 的关系（落 DECISIONS §3）

### 1.1 为什么需要 PublicModel，它跟上轮 PlatformModelMapping 是什么关系

- **PlatformModelMapping（上轮已设计，A→B）**：解决「A 这个公开名最终调哪个上游 B」——是**路由/映射**语义，客户不可见 B。
- **PublicModel（本轮新增，挂在 A 上）**：解决「A 这个公开名**对客户长什么样、卖多少钱、什么品质、上不上架**」——是**经营/商品**语义，客户可见。
- 二者**同以 A（平台公开名）为业务键**，是**一对一互补**关系，不重复造：
  - `PublicModel.PublicName` == `PlatformModelMapping.PublicName` == A。
  - PlatformModelMapping 回答「A→哪个 B」；PublicModel 回答「A 的牌价/品质/上架」。
  - **A 的全集 = PublicModel 上架记录的并集**（取代上轮「公开名全集 = PlatformModelMapping.public_name ∪ PrefillGroup ∪ 直通」的临时口径——本轮起以 PublicModel 为对外商品目录的**唯一权威**）。

### 1.2 品质分级落地（DECISIONS §3 关键决策）

- 品质/体验不同的，**拆成不同的 A 分开卖**：`opus-4.8`（满血）、`opus-4.8-max`、`opus-4.8-air`（经济版）= **PublicModel 的三条独立记录**，三个 `PublicName`、三个 `BasePriceRatio`、各自 `QualityTier` 标签。
- 每条 A 在 PlatformModelMapping 里各自映射到**对应品质的真实模型 B**（如 `opus-4.8→claude-opus-4.8`、`opus-4.8-air→某经济上游`）。
- **红线落表（DECISIONS §3）**：品质不同的渠道**绝不**进同一个 A 的兜底池。约束点 = **Ability 路由维 `model=B`**：同一 A→B 下挂的多渠道必须是「同品质、仅进货价不同」的供应商。**品质差异通过「拆成不同 A→不同 B」实现，而不是通过「同一 A 下混不同品质渠道」**。这条红线在数据层由「A→B 一对一 + 同 B 下多渠道才兜底」结构性保证（见 §2.3）。

### 1.3 PublicModel 字段级（详见 BILLING-DATA-OBJECTS.md §1）

`PublicName(A,uniqueIndex)` / `QualityTier`（品质标签枚举 `full`/`max`/`air`/自定义，纯展示+分类）/ `BasePriceRatio`（基准售价倍率，对客户恒定）/ `Enabled`（上下架）/ `DisplayName`/`SortOrder`/`Description`。

### 1.4 售价倍率与现有 ratio 内核的衔接（复用，不另起）

- **不新建定价计算引擎**。`PublicModel.BasePriceRatio` 的取值口径 = 现有 `ratio_setting.GetModelRatio(A)`（或按价 `GetModelPrice(A)`）的「模型倍率」语义。
- 落地两选一（记 ADR-BILL-01，二选一不影响链路）：
  - **(a) 视图聚合**：`PublicModel.BasePriceRatio` 作为「对外商品目录的展示与上下架权威」，实际计费时仍由 `ModelPriceHelper` 按 A 读 `GetModelRatio(A)`；后台「成本/售价配置页」写 `BasePriceRatio` 时同步刷 `model_ratio` KV。**推荐**（链路零改动，复用现网计费）。
  - (b) 直读：`ModelPriceHelper` 改为优先读 `PublicModel.BasePriceRatio`，回落 `GetModelRatio`。改动面大，本期不取。
- **本期采用 (a)**：PublicModel 是「商品/上架/品质」权威 + 售价倍率的**编辑入口**，计费仍走 `GetModelRatio(A)*GroupRatio`，二者由后台保存动作保持一致。

---

## 2. 供应商成本倍率：挂「渠道 × 真实模型 B」（落 DECISIONS §4）

### 2.1 现有 Channel 有无承载字段？——无，必须新增表

- 已查 `model/channel.go`（DATA-MODEL §3）：Channel 有 `Type/Models/ModelMapping/Setting(text)/Balance/UsedQuota`，**没有任何「按模型的成本倍率」结构化字段**。`Setting(text)` 是非结构化 JSON，不适合做「渠道×模型」二维成本表的查询/聚合主键。
- 结论：**新增表 `ChannelModelCost`**（不塞进 Channel.Setting）。理由：(1) 成本是「渠道×模型 B」二维，需要复合主键唯一约束；(2) 利润看板要按「供应商」「模型」聚合，需可索引列；(3) 同一 A 下多供应商各记各的成本，天然多行。

### 2.2 ChannelModelCost 表（字段级详见 BILLING-DATA-OBJECTS.md §2）

| 维度 | 决策 |
|---|---|
| 表名 | `channel_model_costs` |
| 业务唯一键 | 复合 `uniqueIndex(channel_id, upstream_model)` —— 一个渠道对一个真实模型 B 只有一条生效成本 |
| 核心字段 | `ChannelId`（= 供应商渠道）、`UpstreamModel`（= **真实模型 B**，对齐 Ability.Model 维）、`CostRatio`（成本倍率，超管手填，DECISIONS §4「A 起步手填」）、`CompletionCostRatio`（成本补全倍率，可选；缺省回落 CostRatio 口径）、`Enabled`、`EffectiveTime`（生效时间，未来可做版本，本期取最新生效一条） |
| 录入方式 | DECISIONS §4：**超管后台手填**每个供应商每个模型的成本倍率（不做自动折算，留扩展位 `SourceUnitPrice`） |

### 2.3 同一对外模型 A 下多供应商成本不同，怎么落表 + 一笔请求怎么取到「实际走的那个渠道的成本」

**落表（多供应商各记各的）**：
```
A = opus-4.8  ──(PlatformModelMapping)──▶  B = claude-opus-4.8
                                            │
            Ability(group × B) 下挂多个同品质渠道：
                 Channel#7 (供应商 X)   Channel#12 (供应商 Y)
                                            │
            ChannelModelCost 各一行：
              (channel_id=7,  upstream_model="claude-opus-4.8", cost_ratio=0.40)
              (channel_id=12, upstream_model="claude-opus-4.8", cost_ratio=0.55)
```
- **售价同一（挂 A，恒定）**：`opus-4.8` 卖一个价，不管走 X 还是 Y。
- **成本分行（挂 channel_id × B）**：X 进货便宜（0.40）、Y 贵（0.55），各记各的。

**一笔请求取「实际走的那个渠道的成本」（关键时序，见 §6）**：
1. Distribute 选中渠道 → `RelayInfo.ChannelId` 已确定（如选中 Channel#12）。
2. L2/L3 后 `RelayInfo.UpstreamModelName = B`（= `claude-opus-4.8`，**统计 B 取 L2 后值**，见兼容层 ADR-COMPAT-03）。
3. **结算阶段**用 `(ChannelId=12, UpstreamModel=B)` 主键查 `ChannelModelCost` → 得 `cost_ratio=0.55` → 这才是「实际走的那个渠道的成本」。
4. 兜底切换（X 挂了切 Y）时 `ChannelId` 变 → 自动查到 Y 的成本行；**售价不变（挂 A）、成本跟实际渠道走**（DECISIONS §4 「兜底切供应商时售价恒定，成本跟实际供应商走」）。

> **成本缺失兜底**：`(ChannelId, B)` 在 ChannelModelCost 无行 → 成本记 0 + 利润标记「成本缺失」告警，**不阻断计费**（售价照扣）。详见 COMPAT-LAYER-API-STATE.md 追加的「成本缺失」边界规则。

---

## 3. 分组折扣：分组退化为纯折扣系数（落 DECISIONS §6 §5）

### 3.1 现有 User.Group / GroupRatio 怎么改造（复用，语义收窄）

- **复用** `setting/ratio_setting/group_ratio.go` 的 `group_ratio` KV + `GetGroupRatio(name)`。它现成承载 `default/vip/svip → 折扣系数`，正是 DECISIONS §6 所需。
- **改造 = 语义收窄，不动结构**：
  - 折扣值改为 DECISIONS §6 示例 `free=1.0 / vip=0.85 / svip=0.7`（后台可配，落 `group_ratio` KV）。
  - **砍掉「分组圈模型」**（DECISIONS §5 模型全开）：现网 `GroupSpecialUsableGroup`（分组可用渠道组）的「圈定模型可用性」用途**停用**（不再用分组决定能用哪些模型）。`GroupGroupRatio`（用户组×使用组特价）可保留作高级折扣覆盖，本期非必需。
  - `User.Group`（DATA-MODEL §1，`varchar(64);default:'default'`）字段**不动**，语义从「权限+定价分组」收窄为「**纯折扣等级**」。升级方式 = DECISIONS §6 按充值额/付费包自动升档（复用现有 Subscription `UpgradeGroup/DowngradeGroup`，DATA-MODEL §8）。

### 3.2 扣费公式 + 落在链路哪一步

**公式（DECISIONS §6 §4）**：
```
售价金额 quota_sell = 对外模型 A 的基准售价 × 分组折扣系数
                    = BasePriceRatio(A) × GetGroupRatio(UsingGroup) × tokens 计费
```
对应现网 `ModelPriceHelper`：`ratio := modelRatio(A) * groupRatioInfo.GroupRatio`（`price.go:114`）——**已经是这个公式**，无需改计算，只需保证：
- `modelRatio` 读的是 **A**（= `info.OriginModelName`，兼容层保证未映射前 OriginModelName=C，但定价口径取 A；见 §6 时序「定价键 = A」决策）。
- `groupRatioInfo.GroupRatio` 读的是 `UsingGroup` 的折扣系数。

**落点（链路第 ⑥ 步 扣客户售价）**：发生在 `ModelPriceHelper`（预扣，选渠后）+ `PostXxxConsumeQuota`（结算，响应后）。见 §6 完整时序。

---

## 4. 模型全开 + key 级自我约束（落 DECISIONS §5 §7）

### 4.1 模型全开

- DECISIONS §5：所有对外模型 A 对所有客户可见可用，**不再用分组圈模型**。
- 落地 = **取消「分组→可用模型」的过滤步**（停用 GroupSpecialUsableGroup 的模型圈定用途）。Ability 路由仍按 `(UsingGroup × B)` 选渠，但「客户能不能用 A」**不再由分组决定**——只要 A 上架（`PublicModel.Enabled=true`）即可用。
- 因此**不存在「客户自我授权越权」**：模型本就全开，客户配 C→A 映射、配 key 范围都是减法，安全（DECISIONS §5）。

### 4.2 key 级自我约束（保留 Token.ModelLimits 作减法校验）

- DECISIONS §7：客户建 key 时**可选**限定「只能用某些模型/某些端点」，纯自我保险，默认全开。
- **复用** `Token.ModelLimits`（DATA-MODEL §2，`model_limits_enabled bool` + `model_limits text` JSON）+ `Token.GetModelLimitsMap()`：
  - `ModelLimitsEnabled=false`（默认）→ 全开，不校验。
  - `ModelLimitsEnabled=true` → 仅放行 `model_limits` 列出的模型。
- **校验对象 = A（公开名）**，与兼容层 ADR-COMPAT-08 一致（白名单作用在 A 层，B 客户不可见不进白名单）。
- **校验在链路哪步**：兼容层时序第 5 步「白名单校验」，即 **L1(C→A) 之后、L2(A→B) 之前**。本轮把它明确为「key 级减法校验」语义（非权限闸门）：
  - 命中 → 放行；未命中且 `ModelLimitsEnabled=true` → 拒绝（沿用现有 ModelLimits 拒绝错误）。
  - **端点同理**：key 可选限定能用哪些端点协议（默认全开）。落地为 Token 新增 **可选** `EndpointLimits`（减法，详见 BILLING-DATA-OBJECTS.md §4），校验在入站协议识别（第 1 步）后、L1 前；默认全开则跳过。

> **强调**：因模型全开（§4.1），ModelLimits 从「加法授权」彻底变为「减法约束」——客户只能在「全开集合」里**减**，无法**加**。这与 DECISIONS §7「做减法的自我约束，不是做加法的自我授权」字面一致。

---

## 5. Log 利润字段扩展 + 利润看板数据来源（落 DECISIONS §8 §9）

### 5.1 在兼容层 Log 扩展基础上再加售价/成本/利润

兼容层已扩展 Log：`requested_model(C)/resolved_public_model(A)/actual_upstream_model(B)/inbound_protocol/upstream_protocol/protocol_converted`（COMPAT-LAYER-DATA-OBJECTS §3）。本轮**再加三个金额字段**（字段级详见 BILLING-DATA-OBJECTS.md §3）：

| 新增字段 | 含义 |
|---|---|
| `quota_sell` | 本笔**售价金额**（= 现有 `Quota` 字段口径，即实扣客户额度；为聚合显式化单列一份） |
| `quota_cost` | 本笔**成本金额**（= 同样 token 量 × 该渠道该 B 的 `cost_ratio` × 分组折扣是否计入？见 §5.2 口径决策） |
| `quota_profit` | 本笔**利润** = `quota_sell − quota_cost`（落库冗余，便于看板直接聚合，避免每次重算） |

- **复用现有 `Quota`**：现网 `Log.Quota` = 实扣额度 = 售价。`quota_sell` 与 `Quota` 同口径（冗余显式列，便于 admin 视图与现有 user 视图解耦）。
- 成本/利润字段**仅 admin/root 可见**（对齐 B 不可见三道闸的序列化层：`AdminLogView` 含、`UserLogView` 不含）。

### 5.2 成本金额口径决策（ADR-BILL-02）

- **成本只看真实进货，不打分组折扣**：`quota_cost = tokens 计费 × CostRatio(channel,B) × (completion 用 CompletionCostRatio)`，**不乘 GroupRatio**。理由：分组折扣是「卖给客户的让利」，与「我方进货成本」无关。利润 = 客户实付（含折扣）− 我方真实成本。
- 售价金额 `quota_sell` **乘 GroupRatio**（= 客户实付）。
- → **利润随分组折扣变化**：svip(0.7) 客户利润更薄，这正是看板要暴露的经营信号（DECISIONS §9「哪个对外模型定价偏低」）。

### 5.3 利润看板数据来源建议（DECISIONS §9）

- **数据源 = Log 表聚合**（落字段后直接 GROUP BY）。不另建数仓（本期）。
- 推荐聚合维度（均为 Log 现有/新增可索引列）：
  - 按**模型**：`GROUP BY resolved_public_model(A)` → 看哪个对外模型利润薄/定价偏低。
  - 按**供应商**：`GROUP BY channel(channel_id)` + join channel_name → 看哪个供应商在亏、该给谁导流量。
  - 按**分组**：`GROUP BY group` → 看哪个等级折扣过狠。
  - 度量：`SUM(quota_sell)`、`SUM(quota_cost)`、`SUM(quota_profit)`、利润率 `SUM(profit)/SUM(sell)`。
- **索引建议**：`resolved_public_model`、`channel`、`group`、`created_at` 已建/补建 index（DATA-MODEL §5 多数已 index），保证看板聚合查询走索引。
- **三库兼容**：聚合用 GORM `Select(...).Group(...)`，避免 MySQL-only 函数（AGENTS.md Rule 2）。

---

## 6. 完整请求链路时序（含计费）

> 把 DECISIONS §10 链路落成精确步骤：每步**读哪张表 / 写什么 ctx / 算什么**，特别标注「扣客户售价」「记成本」分别在哪步、用哪个供应商的值。中间件次序复用兼容层 COMPAT-LAYER-API-STATE §2，本表在其上叠加计费动作（标 ¥）。

```
步 | 动作                | 读表/源                          | 写 ctx/RelayInfo                | 算什么 / 计费动作
---+---------------------+----------------------------------+---------------------------------+----------------------------------------
 1 | 入站识别            | router + GenRelayInfoXxx          | InboundFormat=RelayFormat        | inFmt = openai|claude
   |                     |                                   | OriginModelName=C; RequestedModel=C
 2 | TokenAuth 鉴权      | Token(key) / User                 | TokenId, UserId, UsingGroup,     | 取 Token{Group,ModelLimits,EndpointLimits}
   |   [复用]            |                                   |  UserGroup, Token.ModelLimits    |  + User{Group=折扣等级}
 2'| ¥ key端点减法校验   | Token.EndpointLimits(可选)        | —                                | EndpointLimitsEnabled? inFmt 在允许集? 否→拒
   |   [新增,默认全开跳过]|                                   |                                  |  (DECISIONS §7 端点自我约束)
 3 | L1 客户映射 C→A     | UserModelAlias(user>group)        | ResolvedPublicModel=A            | C→A (未命中 A=C)
 4 | ¥ key模型减法校验   | Token.ModelLimits(可选)           | —                                | ModelLimitsEnabled? A 在允许集? 否→拒
   |   [复用,校验对象=A]  |                                   |                                  |  (DECISIONS §7 / ADR-COMPAT-08)
 5 | L2 超管映射 A→B     | PlatformModelMapping(public_name=A)| UpstreamModelName=B (统计基准)   | A→B (未命中 B=A); 客户不可见 B
 6 | 环检测/跳数         | (visited)                         | —                                | L1+L2 跳数≤MaxHops; 检环→报错
 7 | Distribute 选渠     | Ability(UsingGroup × B)           | ChannelId, Channel.Type,         | 负载/容灾选 1 个渠道(如 Channel#12=供应商Y)
   |   [复用]            |                                   |  UsingGroup(可能 auto 改写)      |  挂了兜底切下一个→ChannelId 随之变
 8 | ¥ 售价定价(预扣基准)| ★PublicModel/GetModelRatio(A)     | RelayInfo.PriceData{ModelRatio,  | 售价口径=A: modelRatio=GetModelRatio(A)
   |   [复用ModelPriceHelper]| GetGroupRatio(UsingGroup)     |  GroupRatioInfo, ModelPrice}     |  groupRatio=GetGroupRatio(UsingGroup=折扣)
   |                     |                                   |                                  |  preConsumedQuota = tokens×modelRatio×groupRatio
 9 | ¥ 预扣额度          | Token/User Quota                  | FinalPreConsumedQuota            | PreConsumeTokenQuota(预扣售价侧)
10 | 头尾决策            | Channel.Type                      | TargetProtocol, Passthrough      | targetProto=protocolOf(Type); pass=(inFmt==target)
11 | L3 渠道映射 B→B'    | Channel.ModelMapping (model_mapped)| UpstreamModelName=B'(终值,调用用)| 渠道内重定向; B'仅诊断,统计B取第5步值
12 | Outbound            | compat.Get(inFmt/target)          | —                                | pass?透传(换model=B'):IR 转换→调上游
13 | 上游响应            | upstream                          | usage(真实 token)                | resp(targetProto)
14 | 响应回转            | compat                            | —                                | pass?透传:target→IR→inFmt 序列化(流式见§5)
15 | ¥ 结算-取真实token  | IR.Usage(D5归一)/token_counter(B) | usage.Prompt/Completion          | 计费 token 以上游真实 usage 为准
16 | ¥ 算售价金额        | PriceData(第8步快照)              | quota_sell                       | quota_sell = tokens×modelRatio(A)×groupRatio
   |                     |                                   |                                  |  (= 现网 calculateAudioQuota 结果 = Log.Quota)
17 | ¥ 取实际渠道成本    | ★ChannelModelCost(ChannelId, B)   | costRatio, completionCostRatio   | 主键(第7步ChannelId, 第5步B)→cost_ratio
   |   [新增]            |                                   |                                  |  缺行→costRatio=0+告警(不阻断)
18 | ¥ 算成本/利润金额   | (第15步tokens + 第17步costRatio)  | quota_cost, quota_profit         | quota_cost=tokens×costRatio(不乘GroupRatio,ADR-BILL-02)
   |   [新增]            |                                   |                                  |  quota_profit=quota_sell−quota_cost
19 | ¥ 多退少补+落账     | Token/User Quota                  | —                                | SettleBilling: 按 quota_sell 实扣(退预扣差额)
20 | ¥ 落 Log            | model.RecordConsumeLog            | —                                | 写 C/A/B + 协议字段 + Quota(=quota_sell)
   |   [扩展]            |                                   |                                  |  + quota_sell/quota_cost/quota_profit
   |                     |                                   |                                  |  + channel_id=Y + Other.channel_redirect=B'
```

**关键计费落点小结（直接回答 DECISIONS §10 ⑥⑦）**：
- **「扣客户售价」**：分两段——预扣在**第 8–9 步**（选渠后 `ModelPriceHelper` 算 + `PreConsumeTokenQuota` 预扣），实扣在**第 16/19 步**（结算 `quota_sell = BasePriceRatio(A)×GroupRatio(UsingGroup)×tokens`，多退少补）。**用的值 = 对外模型 A 的基准售价 × 分组折扣**，与实际走哪个渠道无关（售价恒定）。
- **「记成本」**：在**第 17–18 步**（结算阶段），用 **`(实际选中的 ChannelId, 真实模型 B)`** 主键查 `ChannelModelCost.cost_ratio`。**用的值 = 实际走的那个渠道（如 Y=Channel#12）对 B 的成本倍率**。兜底切换后 ChannelId 变 → 自动取到新渠道成本。
- **利润**：第 18 步 `quota_profit = quota_sell − quota_cost`，落 Log 冗余列，看板直接聚合。

---

## 7. 数据模型总览（新增哪几张表/字段）

| 对象 | 类型 | 复用/新增 | 承载语义 | 业务键/公式 |
|---|---|---|---|---|
| **PublicModel** | DB 表（新增） | **新增** `model/public_model.go` 表 `public_models` | 对外模型商品目录：A + 品质标签 + 基准售价倍率 + 上下架 | `uniqueIndex(public_name=A)`；`opus-4.8/-max/-air` 三条记录 |
| **ChannelModelCost** | DB 表（新增） | **新增** `model/channel_model_cost.go` 表 `channel_model_costs` | 供应商成本：渠道×真实模型 B 的成本倍率 | `uniqueIndex(channel_id, upstream_model=B)`；超管手填 `cost_ratio` |
| **Log（+3 金额字段）** | DB 列（扩展） | **扩展** 现有 logs（在兼容层 6 字段之上再加 3 个） | 逐笔售价/成本/利润 | `quota_sell` / `quota_cost` / `quota_profit`（admin 可见） |
| **Token（+EndpointLimits）** | DB 列（扩展，可选） | **扩展** 现有 tokens | key 级端点减法约束 | `endpoint_limits_enabled bool` + `endpoint_limits text`（默认全开） |
| `group_ratio` KV（GetGroupRatio） | 配置（复用，语义收窄） | **复用** `setting/ratio_setting/group_ratio.go` | 分组纯折扣系数 | `free=1.0/vip=0.85/svip=0.7`（后台可配） |
| `model_ratio`/`model_price`（GetModelRatio） | 配置（复用） | **复用** | A 的基准售价倍率计算口径 | `BasePriceRatio(A)` 编辑入口写回（ADR-BILL-01a） |
| `Token.ModelLimits` | DB 列（复用，语义改） | **复用不动结构** | key 级模型减法约束（语义从加法授权→减法约束） | 校验对象=A，默认全开 |
| `User.Group` / `Token.Group` | DB 列（复用，语义收窄） | **复用不动结构** | 纯折扣等级（砍掉圈模型） | 升级走 Subscription UpgradeGroup |
| `RelayInfo.PriceData` | 内存（复用，加字段） | **复用** + 加 `CostRatio/CostQuota/ProfitQuota` 运行期字段 | 逐笔计费快照含成本 | 第 8/17/18 步填充 |
| `PlatformModelMapping` / `UserModelAlias` / `Ability` / `Channel` | — | **复用不动**（上轮已定/现网） | A→B 映射 / C→A 映射 / 路由 / 渠道 | — |

**新增表数 = 2（PublicModel、ChannelModelCost）；扩展列 = Log +3、Token +2（可选）；复用并收窄语义 = group_ratio KV、ModelLimits、User.Group。** 全部 GORM AutoMigrate，三库兼容（JSON 用 TEXT，主键交 GORM，SQLite 用 ADD COLUMN）。

---

## 8. 关键设计决策清单（ADR 摘要，本轮新增）

- **ADR-BILL-01**：PublicModel 是「商品/上架/品质 + 售价倍率编辑入口」权威；计费仍走现网 `GetModelRatio(A)*GroupRatio`，保存时同步刷 `model_ratio` KV（视图聚合方案 a），链路零改动。
- **ADR-BILL-02**：`quota_cost` = tokens×CostRatio(channel,B)，**不乘 GroupRatio**；`quota_sell` 乘 GroupRatio。利润随折扣变化，是经营信号。
- **ADR-BILL-03**：成本挂「渠道×B」必须新增 `ChannelModelCost` 表（Channel 无承载字段；需复合主键+可聚合索引）。
- **ADR-BILL-04**：成本取值在**结算阶段**用「实际选中 ChannelId × L2 后 B」主键查，兜底切换后自动跟新渠道；售价用 A 恒定。
- **ADR-BILL-05**：品质分级 = 拆成不同 A→不同 B（PublicModel 三条记录）；同一 A 下不混不同品质渠道（红线由 A→B 一对一 + 同 B 多渠道才兜底结构性保证）。
- **ADR-BILL-06**：分组退化为纯折扣（复用 group_ratio KV，停用 GroupSpecialUsableGroup 圈模型用途）；ModelLimits 从加法授权变减法约束（模型全开）。
- **ADR-BILL-07**：利润看板数据源 = Log 表聚合（落 quota_sell/cost/profit 冗余列后 GROUP BY 模型/供应商/分组），不另建数仓。
