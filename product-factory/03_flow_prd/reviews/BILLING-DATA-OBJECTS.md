# BILLING-DATA-OBJECTS — 计费相关新增/变更数据对象字段级建议

> 配套：BILLING-MODEL-ARCHITECTURE.md（计费主架构）、COMPAT-LAYER-DATA-OBJECTS.md（兼容层数据对象，本文件在其上增量）。
> 风格对齐 `DATA-MODEL.md`：`Go字段(json_tag): 类型 [约束]`，来源标注。
> 设计铁律：复用现有 `Channel/Token/User/Log` + `group_ratio`/`model_ratio` 配置内核，**只新增 2 张表（PublicModel、ChannelModelCost）+ Log 3 列 + Token 2 列（可选）+ PriceData/RelayInfo 运行期字段**。所有 DDL 三库（SQLite/MySQL/PostgreSQL）兼容（AGENTS.md Rule 2：JSON 用 TEXT、主键交 GORM、SQLite 用 ADD COLUMN、布尔用 commonTrueVal/commonFalseVal）。

---

## 1. 新增表：PublicModel（对外模型商品目录，落 DECISIONS §3）

文件建议：`model/public_model.go`，表名 `public_models`。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增（跨 DB 安全，禁手写 AUTO_INCREMENT/SERIAL） |
| PublicName (public_name) | string | `varchar(255);uniqueIndex:uk_public_name` —— **A，平台公开名**。唯一索引：一个对外模型一条记录。与 `PlatformModelMapping.PublicName` 同键互补 |
| QualityTier (quality_tier) | string | `varchar(32);index;default:'full'` —— **品质标签**，枚举 `full`/`max`/`air`/自定义（DECISIONS §3 满血/max/经济版）。纯展示+分类，不参与计费数值 |
| BasePriceRatio (base_price_ratio) | float64 | `default:0` —— **基准售价倍率**（对客户恒定，不随供应商变）。口径 = 现网 `model_ratio` 模型倍率；保存时同步刷 `model_ratio` KV（ADR-BILL-01a） |
| UsePrice (use_price) | bool | `default:false` —— true=按次/按量固定价（对齐现网 `GetModelPrice`），false=按倍率。与现网 UsePrice 语义一致 |
| BasePrice (base_price) | float64 | `default:0` —— UsePrice=true 时的固定单价（对齐 `model_price` KV）；保存时同步刷 |
| Enabled (enabled) | bool | `default:true` —— **上下架**。false=下架（不进客户可见目录；存量 key 调用行为见 API-STATE「下架但有存量 key」边界） |
| DisplayName (display_name) | string | `varchar(255);default:''` —— 公开站展示名 |
| SortOrder (sort_order) | int | `default:0` —— 公开站排序 |
| Description (description) | string | `varchar(1024);default:''` —— 公开站描述 |
| CreatedTime (created_time) | int64 | `bigint;autoCreateTime` |
| UpdatedTime (updated_time) | int64 | `bigint;autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index`，软删除 |

**业务规则落表**：
- `opus-4.8`、`opus-4.8-max`、`opus-4.8-air` = **三条独立记录**（三个 `public_name`、三个 `base_price_ratio`、`quality_tier` 各为 `full/max/air`）。
- A 的对外全集 = `WHERE enabled=true AND deleted_at IS NULL` 的 `public_name` 集（取代上轮临时口径，成为对外商品目录唯一权威）。
- 售价对客户恒定：计费时 `ModelPriceHelper` 读 `GetModelRatio(A)`（与本表 `base_price_ratio` 由后台保存动作保持一致），与实际渠道无关。
- **缓存**：高频读（客户目录 + 候选联想），建议内存缓存 key=`public_name`，启动 `InitPublicModelMap()` 装载，写时失效（对齐现网 model_ratio/Option 内存惯例）。

---

## 2. 新增表：ChannelModelCost（供应商成本倍率，挂渠道×真实模型 B，落 DECISIONS §4）

文件建议：`model/channel_model_cost.go`，表名 `channel_model_costs`。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增 |
| ChannelId (channel_id) | int | `index;uniqueIndex:uk_channel_model` —— 供应商渠道（= `Channel.Id`）（复合唯一键 1） |
| UpstreamModel (upstream_model) | string | `varchar(255);uniqueIndex:uk_channel_model` —— **真实模型 B**（对齐 `Ability.Model` 维 / L2 后 `UpstreamModelName`）（复合唯一键 2）。**客户不可见** |
| CostRatio (cost_ratio) | float64 | `default:0` —— **成本倍率**（超管手填，DECISIONS §4 A 起步手填）。口径同 model_ratio：输入 token 计费基准 |
| CompletionCostRatio (completion_cost_ratio) | float64 | `default:0` —— 成本补全倍率（输出 token）。0=回落用 CostRatio×现网 CompletionRatio 口径（ADR-BILL-02 补全口径） |
| Enabled (enabled) | bool | `default:true` —— false=该成本行停用（视为缺失，记 0+告警） |
| EffectiveTime (effective_time) | int64 | `bigint;default:0` —— 生效时间。本期取「最新生效且 enabled」一条；预留成本版本化扩展 |
| SourceUnitPrice (source_unit_price) | float64 | `default:0` —— **扩展位**：进货单价（DECISIONS §4「以后再考虑填进货单价自动折算」），本期不参与计算 |
| Remark (remark) | string | `varchar(255);default:''` —— 超管备注 |
| CreatedTime (created_time) | int64 | `bigint;autoCreateTime` |
| UpdatedTime (updated_time) | int64 | `bigint;autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index` |

**复合唯一索引**：`uniqueIndex:uk_channel_model (channel_id, upstream_model)` —— 一个渠道对一个真实模型 B 只有一条生效成本。
**多供应商落表**：同一 A→B 下，每个挂在 Ability 的渠道各一行（如 `(7,B,0.40)`、`(12,B,0.55)`），售价同一（挂 A），成本分行（挂 channel×B）。
**取值时机**：**结算阶段**（链路第 17 步），主键 `(实际选中 ChannelId, L2 后 B)` 精确取一行 → 「实际走的那个渠道的成本」。兜底切换后 ChannelId 变 → 自动取新渠道行。
**缓存**：可内存缓存 map `[channelId][upstreamModel]→CostRatio`，启动 `InitChannelModelCostMap()`，写时失效。

---

## 3. 变更：Log 利润字段扩展（在兼容层 6 字段之上再加 3 个，落 DECISIONS §8）

现有 `Log`（DATA-MODEL §5）已有 `Quota(quota) int`（= 实扣额度 = 售价口径）。兼容层已加 `requested_model/resolved_public_model/actual_upstream_model/inbound_protocol/upstream_protocol/protocol_converted`（COMPAT-LAYER-DATA-OBJECTS §3）。**本轮再加 3 个金额字段**：

| 新增字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| QuotaSell (quota_sell) | int | `default:0` —— **本笔售价金额**（= `BasePriceRatio(A)×GroupRatio(UsingGroup)×tokens`，与现有 `Quota` 同口径，显式单列便于聚合）。**客户可见**（= 现有 Quota） |
| QuotaCost (quota_cost) | int | `default:0` —— **本笔成本金额**（= `tokens×CostRatio(channel,B)`，**不乘 GroupRatio**，ADR-BILL-02）。**仅 admin/root 可见** |
| QuotaProfit (quota_profit) | int | `default:0` —— **本笔利润** = `quota_sell − quota_cost`（落库冗余，看板直接聚合）。**仅 admin/root 可见**。可为负（成本>售价，亏本告警） |

**迁移**：SQLite `ALTER TABLE logs ADD COLUMN ...`（AGENTS.md Rule 2），三库 GORM AutoMigrate 加列给 default 避存量 NULL。
**口径**：`quota_sell` 与现有 `Quota` 一致（不破坏现网报表）；`quota_cost/quota_profit` 为新增经营字段。
**成本缺失**：`(ChannelId,B)` 无 ChannelModelCost 行 → `quota_cost=0`、`quota_profit=quota_sell`，并在 `Other(other)` JSON 写 `{"cost_missing":true,"channel_id":X,"upstream_model":"B"}` 供看板筛「成本缺失」告警（详见 API-STATE 边界）。

### 3.1 Log 视图 DTO（序列化层裁剪，B 与成本/利润均不可见）

| DTO | 字段 | 用于 |
|---|---|---|
| `UserLogView` | `requested_model(C)`, `resolved_public_model(A)`, `quota(=quota_sell)`，**无** `actual_upstream_model(B)`、**无** `quota_cost`、**无** `quota_profit` | `UserAuth` 用量/日志接口 |
| `AdminLogView` | 全字段：C→A→B + 协议字段 + `quota_sell/quota_cost/quota_profit` + `channel_id` | `AdminAuth`/`RootAuth` + 利润看板 |

### 3.2 利润看板聚合查询（DECISIONS §9，落字段后）

数据源 = `logs` 表，`WHERE type=2(Consume)`。三库兼容用 GORM `Select/Group`：
- 按模型：`Group("resolved_public_model")`，度量 `SUM(quota_sell)/SUM(quota_cost)/SUM(quota_profit)` + 利润率。
- 按供应商：`Group("channel")` join channel_name。
- 按分组：`Group("group")`。
- 时间维：`WHERE created_at BETWEEN`（已 index）。

---

## 4. 变更：Token 端点级减法约束（可选，落 DECISIONS §7 端点同理）

现有 `Token`（DATA-MODEL §2）已有 `ModelLimitsEnabled/ModelLimits`（模型减法约束，**复用不动**，校验对象=A）。本轮为「端点同理」**新增 2 列（可选）**：

| 新增字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| EndpointLimitsEnabled (endpoint_limits_enabled) | bool | `default:false` —— false=端点全开（默认）；true=启用端点减法约束 |
| EndpointLimits (endpoint_limits) | string | `text;default:''` —— JSON，允许的入站协议集（如 `["openai","claude"]`，对齐 `RelayFormat`）。`GetEndpointLimitsMap()` 解析 |

**校验**：链路第 2' 步（TokenAuth 后、L1 前），`EndpointLimitsEnabled=true` 时校验 `inFmt ∈ EndpointLimits`，否则拒；默认全开则跳过。**纯减法自我约束**（DECISIONS §7），非权限闸门。
**迁移**：SQLite ADD COLUMN，给 default 全开（不破坏存量 key 行为）。

> `Token.ModelLimits`（模型减法）**复用现有字段不新增**，仅语义从「加法授权」明确为「减法约束」（模型全开背景下）。

---

## 5. 变更：PriceData / RelayInfo 运行期字段（内存，承载成本/利润，复用扩展）

复用现有 `types.PriceData`（`types/price_data.go`）+ `relaycommon.RelayInfo`，新增运行期字段（不落库，逐笔计费快照）：

### 5.1 PriceData（`types/price_data.go`）新增
| 字段 | 类型 | 说明 |
|---|---|---|
| CostRatio | float64 | 第 17 步从 ChannelModelCost 取的成本倍率（该笔实际渠道×B） |
| CompletionCostRatio | float64 | 成本补全倍率（缺省回落） |
| CostMissing | bool | 成本行缺失标记（true→quota_cost=0+告警） |

> 现有 `ModelRatio/ModelPrice/GroupRatioInfo/UsePrice` **复用承载售价端**，无需改。

### 5.2 RelayInfo（`relay/common/relay_info.go`）新增/复用
| 字段 | 类型 | 复用/新增 | 说明 |
|---|---|---|---|
| ChannelId | int | **复用现有** | 第 7 步 Distribute 写，第 17 步取成本用 |
| UpstreamModelName (B) | string | **复用现有** | L2 后 B，第 17 步取成本主键 |
| UsingGroup | string | **复用现有** | 折扣系数 key（第 8 步 GetGroupRatio） |
| ResolvedPublicModel (A) | string | 兼容层已加 | 售价定价键 + Log resolved_public_model |
| QuotaSell / QuotaCost / QuotaProfit | int | **新增** | 第 16/18 步算出，第 20 步落 Log |

---

## 6. 新增/变更汇总

| 对象 | 类型 | 复用/新增 | 主键/唯一键 |
|---|---|---|---|
| PublicModel | DB 表 | **新增** | `uniqueIndex(public_name=A)` |
| ChannelModelCost | DB 表 | **新增** | `uniqueIndex(channel_id, upstream_model=B)` |
| Log（quota_sell/cost/profit） | DB 列 ×3 | **扩展** | — |
| Token（endpoint_limits×2） | DB 列 ×2 | **扩展（可选）** | — |
| UserLogView / AdminLogView | DTO | **扩展**（裁成本/利润字段） | — |
| PriceData（CostRatio 等 ×3） | 内存 | **扩展** | — |
| RelayInfo（QuotaSell 等 ×3 + 复用 4） | 内存 | **扩展** | — |
| `group_ratio` KV / GetGroupRatio | 配置 | **复用，语义收窄为纯折扣** | — |
| `model_ratio`/`model_price` KV | 配置 | **复用**（A 售价倍率口径，PublicModel 编辑入口写回） | — |
| Token.ModelLimits | DB 列 | **复用不动**（语义→减法约束，校验对象=A） | — |
| User.Group / Token.Group | DB 列 | **复用不动**（语义→纯折扣等级） | — |
| PlatformModelMapping / UserModelAlias / Ability / Channel | — | **复用不动** | — |

**新增表 = 2；扩展列 = Log+3、Token+2（可选）；复用收窄 = group_ratio/ModelLimits/User.Group。** 全部 GORM AutoMigrate，三库兼容。
