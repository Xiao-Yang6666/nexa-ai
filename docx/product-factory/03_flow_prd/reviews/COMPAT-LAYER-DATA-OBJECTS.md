# COMPAT-LAYER-DATA-OBJECTS — 新增/变更数据对象字段级建议

> 配套：COMPAT-LAYER-ARCHITECTURE.md（主架构）、COMPAT-LAYER-API-STATE.md（API/状态）。
> 风格对齐 `DATA-MODEL.md`：`Go字段(json_tag): 类型 [约束]`，来源标注。
> 设计铁律：复用现有 `Channel/Ability/Token/User/Log`，**只新增 2 张表 + Log 字段扩展 + IR 内存结构**。所有 DDL 必须三库（SQLite/MySQL/PostgreSQL）兼容（AGENTS.md Rule 2：JSON 用 `TEXT`，主键交 GORM，SQLite 用 `ADD COLUMN`）。

---

## 1. 新增表：PlatformModelMapping（超管底仓映射 A→B，全局，客户不可见）

文件建议：`model/platform_model_mapping.go`，表名 `platform_model_mappings`。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增（跨 DB 安全，禁手写 AUTO_INCREMENT/SERIAL） |
| PublicName (public_name) | string | `varchar(255);uniqueIndex:uk_public_name` —— **A，平台公开名**。唯一索引保证 1对1（一个 A 只能配一个 B） |
| UpstreamName (upstream_name) | string | `varchar(255);not null` —— **B，真实上游模型名。客户绝不可见**（无 user 路由读接口） |
| Enabled (enabled) | bool | `default:true`，false=该映射停用（A 回落为直通模型或 404） |
| Remark (remark) | string | `varchar(255)`，超管备注（如"降本：claude-3.5→自研模型"） |
| CreatedTime (created_time) | int64 | `bigint;autoCreateTime` |
| UpdatedTime (updated_time) | int64 | `bigint;autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index`，软删除（对齐现有表惯例） |

**作用域**：全局，无 group/user 维。
**生效顺序**：L2（A→B），选渠之前。
**缓存**：高频读，建议进内存缓存（对齐现有 `model_mapping`/Option 内存装载惯例），key=`public_name`，启动 `InitPlatformModelMappingMap()` 装载，写时失效。

---

## 2. 新增表：UserModelAlias（客户层自助映射 C→A，分组/用户级）

文件建议：`model/user_model_alias.go`，表名 `user_model_aliases`。

| 字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| Id (id) | int | 主键，GORM 自增 |
| ScopeType (scope_type) | string | `varchar(16);not null`；枚举 `user`/`group`（复合唯一键 1） |
| ScopeId (scope_id) | string | `varchar(64);not null`；user→`user_id` 字符串化，group→分组名（对齐 `User.Group/Token.Group varchar(64)`）（复合唯一键 2） |
| Alias (alias) | string | `varchar(255);not null` —— **C，客户别名**（复合唯一键 3） |
| Target (target) | string | `varchar(255);not null` —— **A，目标平台公开名。不强制白名单**（可硬输入平台没有的名字） |
| Enabled (enabled) | bool | `default:true` |
| CreatedTime (created_time) | int64 | `bigint;autoCreateTime` |
| UpdatedTime (updated_time) | int64 | `bigint;autoUpdateTime` |
| DeletedAt | gorm.DeletedAt | `index` |

**复合唯一索引**：`uniqueIndex:uk_scope_alias (scope_type, scope_id, alias)` —— 同作用域内别名 C 唯一，保证 1对1。
**作用域**：只在该 scope 请求生效；不进公开列表、不影响别人。
**优先级**：同一 C 命中多 scope 时 **user > group**（解析顺序：先查 user 级，未命中再查 group 级）。
**输入约束**：`Target` 写入**不校验白名单**（铁律）；候选提示由前端从公开模型全集联想，但落库不拦。
**越权护栏**：user 路由写入时强制 `scope_type=user AND scope_id=:caller_user_id`（或本人所属 group），禁跨 scope 写（对齐 ARCHITECTURE-REVIEW §6 self-scope）。

---

## 3. 变更：Log 三段式统计字段扩展（用户明确要求）

现有 `Log`（DATA-MODEL §5）已有 `ModelName(model_name)`。**保留 `model_name` 语义不变**（为兼容现网图表，置为"客户实际输入名 C"），新增三段式显式字段，便于按链路检索与角色裁剪：

| 新增字段 (json) | 类型 | 约束 / 说明 |
|---|---|---|
| RequestedModel (requested_model) | string | `varchar(255);index;default:''` —— **C，用户实际输入的模型名**（= 现 `OriginModelName`）。**客户可见** |
| ResolvedPublicModel (resolved_public_model) | string | `varchar(255);index;default:''` —— **A，平台公开名**（L1 后、L2 前）。**客户可见** |
| ActualUpstreamModel (actual_upstream_model) | string | `varchar(255);index;default:''` —— **B，最终真正调用的上游模型名**（L2 后）。**客户不可见，仅 admin/root** |
| InboundProtocol (inbound_protocol) | string | `varchar(32);default:''` —— 入站协议（`openai`/`claude`/...，= `RelayFormat`）。可观测 |
| UpstreamProtocol (upstream_protocol) | string | `varchar(32);default:''` —— 目标供应商协议。可观测 |
| ProtocolConverted (protocol_converted) | bool | `default:false` —— 是否发生头尾协议转换（false=直通），便于排障/统计转换占比 |

**迁移**：SQLite 用 `ALTER TABLE logs ADD COLUMN ...`（AGENTS.md Rule 2），三库均走 GORM AutoMigrate 加列，给 default 避免存量行 NULL。
**`model_name` 取值口径**：为不破坏现有报表，`model_name` = `requested_model`(C)。若现网历史口径是上游名，则改为在迁移说明中标注口径变更（记 ADR）。本设计默认 `model_name=C`。
**渠道级 L3 重定向名 B'**：不单设列，写入 `Other(other) string`（现有 JSON 字段）的 `{"channel_redirect":"B'"}`，仅诊断用，不参与计费/公开语义。

### 3.1 Log 视图 DTO（序列化层裁剪，保证 B 不可见）
| DTO | 字段 | 用于 |
|---|---|---|
| `UserLogView` | `requested_model(C)`, `resolved_public_model(A)`，**无** `actual_upstream_model` | `UserAuth` 用量/日志接口 |
| `AdminLogView` | 全字段含 `actual_upstream_model(B)` + `inbound/upstream_protocol` + `protocol_converted` | `AdminAuth`/`RootAuth` |

---

## 4. IR 中间表示（内存结构，非 DB；功能 A）

包 `relay/compat/ir`。仅运行期存在，不落库。覆盖 OpenAI⇄Anthropic 五大差异点（D1–D5）。

### 4.1 ChatIR（请求）
| 字段 | 类型 | 说明 / 差异点 |
|---|---|---|
| Model | string | 恒为 B；序列化时写回目标协议 model 字段 |
| System | []ContentBlock | **D1 system 位置统一**：OpenAI 取 `messages[role=system]`；Anthropic 取顶层 `system`。序列化反向还原 |
| Messages | []Message | 见 4.2 |
| Tools | []Tool | **D3**：统一 OpenAI `tools[].function{name,parameters}` 与 Anthropic `tools[]{name,input_schema}` |
| ToolChoice | *ToolChoice | `auto`/`none`/`required`/具名；双向映射 |
| Stream | bool | |
| MaxTokens | *int | OpenAI `max_tokens`/`max_completion_tokens` ⇄ Anthropic `max_tokens`(必填) — 见 API-STATE §token |
| Temperature/TopP | *float64 | 指针 + omitempty（AGENTS.md Rule 6 保留显式零值） |
| StopSequences | []string | OpenAI `stop` ⇄ Anthropic `stop_sequences` |
| Metadata | map | |
| PassthroughExtras | map[string]json.RawMessage | ADR-COMPAT-01：未强建模字段旁路透传 |

### 4.2 Message / ContentBlock
- `Message{ Role string(user/assistant/tool); Content []ContentBlock }`
- `ContentBlock{ Type string(text/image/tool_use/tool_result); Text string; ImageSource *ImageSource; ToolUse *ToolUseBlock; ToolResult *ToolResultBlock }`
- **D2 content 结构统一**：OpenAI `content` 可为字符串或数组；Anthropic 恒为 block 数组。IR 统一为 block 数组，OpenAI 序列化时退化为字符串（纯单 text 时）。
- **tool 往返**：OpenAI assistant `tool_calls` / `role=tool` 消息 ⇄ Anthropic `tool_use` / `tool_result` content block。

### 4.3 ChatRespIR / ChatDeltaIR（响应 + 流式增量）
| 字段 | 类型 | 说明 / 差异点 |
|---|---|---|
| Id / Model | string | |
| Role | string | assistant |
| Content | []ContentBlock | 增量时为局部 |
| StopReason | string(IR 枚举) | **D4**：IR 枚举 `end_turn/max_tokens/stop_sequence/tool_use`；双向映射 OpenAI `finish_reason(stop/length/tool_calls)` ⇄ Anthropic `stop_reason(end_turn/max_tokens/stop_sequence/tool_use)`（映射表见 API-STATE §错误/停止映射） |
| Usage | UsageIR{ PromptTokens, CompletionTokens, TotalTokens } | **D5**：OpenAI `usage.prompt_tokens/completion_tokens` ⇄ Anthropic `usage.input_tokens/output_tokens` |

`StreamState`（流式上下文，见 API-STATE §5）：保存 message 开闭状态、content block index、累计 usage，用于把 Anthropic 多 event（`message_start`/`content_block_delta`/`message_delta`/`message_stop`）与 OpenAI `data: {choices:[{delta}]}` + `[DONE]` 互转（**SerializeStreamChunk 可能 1→N**）。

---

## 5. ctx / RelayInfo 衔接字段（运行期）

复用现有 `RelayInfo`（`relay/common/relay_info.go`），新增字段：
| 字段 | 类型 | 说明 |
|---|---|---|
| RequestedModel (C) | string | = 现有 `OriginModelName` 初值，映射前快照 |
| ResolvedPublicModel (A) | string | L1 后值 |
| (B) UpstreamModelName | string | **复用现有字段**，L2 后写入（L3 现有逻辑可再改写为 B'，但统计 B 取 L2 后快照） |
| InboundFormat | types.RelayFormat | **复用现有 `RelayFormat`** |
| TargetProtocol | types.RelayFormat | 选渠后由 `Channel.Type→protocol` 推导 |
| Passthrough | bool | inFmt==target |

> ctx key 建议沿用现有 `c.Set/GetString` 惯例新增 `requested_model`/`resolved_public_model`/`actual_upstream_model`，供计费与落 Log 读取。

---

## 6. 新增/变更汇总

| 对象 | 类型 | 复用/新增 |
|---|---|---|
| PlatformModelMapping | DB 表 | **新增** |
| UserModelAlias | DB 表 | **新增** |
| Log（6 字段） | DB 列 | **扩展现有** |
| UserLogView / AdminLogView | DTO | **新增**（序列化裁剪） |
| ChatIR/ChatRespIR/ChatDeltaIR/StreamState | 内存结构 | **新增**（`relay/compat/ir`） |
| RelayInfo（6 字段，2 复用） | 内存结构 | **扩展现有** |
| Channel.ModelMapping / Ability / Token.Group / User.Group | — | **复用不动** |
