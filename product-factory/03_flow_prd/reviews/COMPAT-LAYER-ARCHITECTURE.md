# COMPAT-LAYER-ARCHITECTURE — 协议兼容层 + 两层模型映射（主架构设计）

> 角色：S3/S4 架构（只设计架构，不写代码、不画 UI）。
> 范围：在现有 new-api（Go + GORM）relay 链路上，新增 **(A) 协议兼容层（OpenAI ⇄ Anthropic 双向互转，可插拔扩展）** + **(B) 两层模型映射（C→A→B 链式叠加，1对1）**。
> 设计铁律：**基于现有 Channel / Ability / Token / User / Log 结构**，明确「复用什么 + 新增什么」，不从零建系统。
> 锚定证据：`relay/channel/adapter.go`（Adaptor 接口已含 `ConvertOpenAIRequest`/`ConvertClaudeRequest`/`ConvertGeminiRequest`）、`types/relay_format.go`（`RelayFormat` 入站协议枚举）、`relay/helper/model_mapped.go`（已有链式映射 + 环检测）、`relay/common/relay_info.go`（`RelayInfo.OriginModelName/UpstreamModelName/IsModelMapped`）、`DATA-MODEL.md` §3 Channel.ModelMapping / §4 Ability / §2 Token / §5 Log。

---

## 0. 现状基线（必须基于它设计，不要另起炉灶）

| 现有能力 | 出处 | 在本方案中的角色 |
|---|---|---|
| **入站协议已被识别为 `RelayFormat`** | `types/relay_format.go`：`openai`/`claude`/`gemini`/`openai_responses`/`embedding`/... | 复用：作为「头」= 入站端点协议。`GenRelayInfoClaude` 已把 `/v1/messages` 标为 `RelayFormatClaude`。 |
| **每个渠道适配器已实现「入站格式 → 渠道原生」转换** | `relay/channel/adapter.go` 的 `ConvertOpenAIRequest` / `ConvertClaudeRequest` / `ConvertGeminiRequest` | 复用：这是「尾」= 目标供应商协议的事实承载。Anthropic 渠道适配器的 `ConvertOpenAIRequest` 已是「OpenAI 入站 → Anthropic 出站」。 |
| **渠道级模型映射（链式 + 环检测）** | `relay/helper/model_mapped.go`，读 `c.GetString("model_mapping")`（来自 `Channel.ModelMapping`，DATA-MODEL §3） | 复用为 **B 层的"超管底仓映射 A→B"的执行内核**，但映射的「来源/作用域/可见性」要新增表来承载（见 §4）。 |
| **路由能力表 Ability（group×model→channel）** | DATA-MODEL §4，复合主键 `group+model+channel_id` | 复用：负载/容灾仍走 Ability，**映射不掺路由**（铁律）。映射产出的最终模型名 B 作为 Ability 的 `model` 维查询键。 |
| **用量日志 Log** | DATA-MODEL §5：`ModelName/ChannelName/Group/PromptTokens/...` | 扩展：新增三段式落点字段（见 COMPAT-LAYER-DATA-OBJECTS.md）。 |

**关键结论：协议转换"只看头和尾"的需求，与现有 `Adaptor.ConvertXxxRequest(入站格式) by 渠道(出站供应商)` 模型天然吻合。** 本方案不替换该机制，而是：(1) 把它抽象为显式的「协议适配器注册表 + IR 中间表示」以支持可插拔扩展与响应回转；(2) 在它**之前**插入两层模型映射，确保「确定最终目标模型 B」后再做唯一一次协议转换决策。

---

## 1. 整体管线（端到端，文字 + ASCII）

```
                         ┌──────────────────────────────────────────────────────────────┐
 客户请求               │                     N E X A   G A T E W A Y                     │
 (任意入站协议)         │                                                                │
   POST /v1/chat/...    │  ┌────────────┐   ┌──────────────────────┐   ┌──────────────┐ │
   或 POST /v1/messages │  │ ① 入站识别 │   │ ② 两层模型映射         │   │ ③ 头尾对比   │ │
   ─────────────────────┼─▶│ InboundProto│──▶│  C → A → B (链式叠加)  │──▶│ 协议转换决策 │ │
   model = C            │  │ = RelayFmt  │   │  环检测 + 最大跳数     │   │ inFmt vs    │ │
   (客户别名)            │  └────────────┘   └──────────────────────┘   │ targetProto │ │
                         │       │ inFmt          │  C,A,B 三态落 ctx    └──────┬───────┘ │
                         │       │                │                              │         │
                         │       │                ▼                              ▼         │
                         │       │         ┌─────────────┐              ┌────────────────┐ │
                         │       │         │ Ability 路由│              │ inFmt==target? │ │
                         │       │         │ group×B→chan│              │  ├ 是: 直通     │ │
                         │       │         │ (负载/容灾) │              │  └ 否: IR 转换  │ │
                         │       │         └──────┬──────┘              └───────┬────────┘ │
                         │       │                │ 选中 Channel               │           │
                         │       │                │ targetProto=providerProto  │           │
                         │       │                ▼                            ▼           │
                         │       │         ┌────────────────────────────────────────────┐ │
                         │       │         │ ④ Outbound: IR → targetProto 序列化 → 调上游 │ │
                         │       │         │    （复用现有 Adaptor.ConvertXxx / DoRequest）│ │
                         │       │         └───────────────────┬──────────────────────────┘ │
                         │       │                             │ upstream resp (targetProto)│
                         │       ▼                             ▼                            │
                         │  ┌────────────────────────────────────────────────────────────┐│
                         │  │ ⑤ 响应回转: targetProto → IR → inFmt 序列化（流式/非流式）   ││
                         │  │    inFmt==target 则直通                                       ││
                         │  └───────────────────────────────┬────────────────────────────┘│
                         │                                   │                              │
                         │  ┌────────────────────────────────▼───────────────────────────┐│
                         │  │ ⑥ 计费 + 落 Log：requested_model=C / resolved_public=A /      ││
                         │  │    actual_upstream=B / inbound_protocol / upstream_protocol  ││
                         │  └──────────────────────────────────────────────────────────────┘│
                         └──────────────────────────────────────────────────────────────────┘
                                   响应（客户入站协议 inFmt）回客户
```

### 管线分步语义

1. **① 入站识别**：沿用现有 router（`router/relay-router.go`）+ `GenRelayInfoXxx`。`/v1/chat/completions`→`RelayFormatOpenAI`；`/v1/messages`→`RelayFormatClaude`。记 `inFmt = info.RelayFormat`，记 `OriginModelName = C`（客户报文里的 model 字段，未映射原值）。
2. **② 两层模型映射（核心新增）**：在 **选渠道之前** 完成 `C → A → B` 解析（见 §3 执行次序、§5 防循环）。产出 `B = 真实上游模型名`，同时把 `C / A / B` 三态写入 ctx（供 ⑥ 统计）。
3. **③ 头尾对比**：选中 Channel（按 `group × B` 查 Ability）后，得 `targetProto = providerProtocolOf(Channel.Type)`。比较 `inFmt` 与 `targetProto`：相等→标记 `passthrough=true`；不等→标记需转换。**协议转换只在此处决策一次**（铁律「只看头和尾」）。
4. **④ Outbound**：若 passthrough，直接转发原报文（仅替换 model 字段为 B）；否则把请求 IR 用目标协议适配器序列化。**复用现有 `Adaptor.ConvertOpenAIRequest/ConvertClaudeRequest`**，新增的「协议适配器抽象」是它们的统一壳（见 §2），不重写已能跑的 convert 逻辑。
5. **⑤ 响应回转**：上游响应按 `targetProto` 解析进 IR，再以 `inFmt` 序列化回客户。流式（SSE）需逐 chunk 转（见 COMPAT-LAYER-API-STATE.md §5）。passthrough 时直接透传上游响应流。
6. **⑥ 计费 + 落 Log**：计费用 B 的定价（Ability/ratio 已按 B）；Log 扩展字段记 `C/A/B + inbound_protocol + upstream_protocol`（见数据对象文档）。

> **铁律落点「协议转换只做一次」**：映射跳几层（C→A→B）只影响"最终目标模型是谁"，进而只影响"目标供应商协议是什么"。协议转换发生在 ③④⑤，输入只依赖 `inFmt`（头）和 `targetProto`（尾），与中间映射跳数完全解耦。

---

## 2. 协议适配器抽象（功能 A，可插拔）

### 2.1 设计目标
- 这次只实现 **OpenAI** 与 **Anthropic（/v1/messages）** 两个协议适配器。
- 为后期 Gemini / embedding / 图像 / 语音留扩展位：**注册表模式 + 协议能力声明**。
- 双向：inbound 解析（任意协议报文 → 内部统一 IR）+ outbound 序列化（IR → 目标协议）。

### 2.2 内部统一中间表示 IR（要点，非完整 schema）

IR 是「协议无关的对话/请求语义模型」，新增包 `relay/compat/ir`。请求侧 `ChatIR`、响应侧 `ChatRespIR`（含流式增量 `ChatDeltaIR`）。IR 字段要点（**覆盖两协议差异点**，详细字段级见 COMPAT-LAYER-DATA-OBJECTS.md §3）：

- `Model string`（恒为 B，序列化时由 outbound 写回目标 model 字段）
- `System []ContentBlock`（**统一承载 system**：OpenAI 来自 `messages[role=system]`，Anthropic 来自顶层 `system` 字段 — 见差异点 D1）
- `Messages []Message`，`Message{ Role(user/assistant/tool), Content []ContentBlock }`
- `ContentBlock{ Type(text/image/tool_use/tool_result), ... }`（统一 OpenAI `content` 字符串/数组 与 Anthropic content blocks — D2）
- `Tools []Tool` / `ToolChoice`（统一 OpenAI `tools[].function` 与 Anthropic `tools[]` — D3）
- `Stream bool`、`MaxTokens *int`、`Temperature *float64`、`StopSequences []string`、`Metadata`
- 响应侧：`StopReason`（IR 枚举，双向映射 OpenAI `finish_reason` ⇄ Anthropic `stop_reason` — D4）、`Usage{ PromptTokens, CompletionTokens }`（统一 OpenAI `usage.prompt/completion_tokens` 与 Anthropic `usage.input/output_tokens` — D5）

> **可选项**：IR 可以做"瘦壳"——不强求把所有字段语义都建模，对两协议都不认识的私有参数走 `PassthroughExtras map[string]json.RawMessage` 旁路保留，降低首版工作量。决策记为 ADR-COMPAT-01：首版 IR 只强建模 D1–D5 五个差异点，其余字段旁路透传。

### 2.3 协议适配器接口（签名级）

新增包 `relay/compat`，定义 `ProtocolAdapter`：

```
type ProtocolAdapter interface {
    Format() types.RelayFormat                 // 该适配器代表的协议（openai / claude）
    Capabilities() ProtocolCapabilities         // 协议能力声明（见 2.4）

    // Inbound：客户报文(原始 body) → IR
    ParseRequest(raw []byte, mode relayconstant.RelayMode) (*ir.ChatIR, error)
    // Outbound：IR → 目标协议报文（用于调上游）
    SerializeRequest(in *ir.ChatIR) ([]byte, error)

    // 响应回转
    ParseResponse(raw []byte) (*ir.ChatRespIR, error)        // 上游(目标协议) → IR
    SerializeResponse(in *ir.ChatRespIR) ([]byte, error)     // IR → 客户(入站协议)

    // 流式（SSE）回转：逐 chunk
    ParseStreamChunk(raw []byte, st *StreamState) ([]*ir.ChatDeltaIR, error)
    SerializeStreamChunk(d *ir.ChatDeltaIR, st *StreamState) ([][]byte, error) // 可能 1→N 个 SSE event
}
```

- **不重复造轮子**：`Anthropic.SerializeRequest` 内部可直接复用现有 `relay/channel/claude` 的 convert 实现；本接口是"统一入口壳"，把分散在各 channel adaptor 里的 `ConvertOpenAIRequest/ConvertClaudeRequest` 收敛到协议维度。迁移策略见 §6。

### 2.4 协议能力声明（注册 + 扩展机制）

```
type ProtocolCapabilities struct {
    Streaming   bool   // 支持 SSE
    Tools       bool   // 支持 tool/function calling
    Vision      bool   // 支持图像 content block
    Embedding   bool
    Audio       bool
    Image       bool
}
```

注册表（包级单例，进程启动注册）：

```
var registry = map[types.RelayFormat]ProtocolAdapter{}
func Register(a ProtocolAdapter)                 // openai/claude 在 init() 注册
func Get(f types.RelayFormat) (ProtocolAdapter, bool)
```

- **可插拔扩展位**：加新协议 = 实现一个 `ProtocolAdapter` + 在 `init()` 调 `Register`。无需改管线。
- **能力裁决**：转换前用 `inAdapter.Capabilities()` ∩ `targetAdapter.Capabilities()` 做兼容性预检，能力缺口（如入站用 tools 但目标协议 `Tools=false`）按 COMPAT-LAYER-API-STATE.md §「能力降级/拒绝」规则处理。
- **首版注册**：仅 `openai`、`claude` 两个。Gemini/embedding/image/audio 注册位预留，未实现时 `Get` 返回 false，管线回落到"现有 per-channel adaptor 直转"路径（不阻断现网）。

---

## 3. 两层模型映射（功能 B）的执行次序与三者关系

### 3.1 三段式管线 C → A → B
- **C = 客户别名**（客户请求里的 model 值，= `info.OriginModelName`）。
- **A = 平台公开名**（在公开模型列表里、客户可见）。
- **B = 真实上游模型**（超管底仓的实际调用名，**客户永不可见**）。

### 3.2 两层映射的执行顺序（铁律）

```
第 1 步【客户层】 C → A   (UserModelAlias，分组级或用户级，基于平台公开名)
第 2 步【超管层】 A → B   (PlatformModelMapping，全局底仓映射，客户不可见)
最终调用 B
```

- 先客户层再超管层。两层都是 **1对1 纯字符串替换**。
- 任一层未命中则该层为恒等（C 未配则 A=C；A 未配底仓则 B=A）。
- **负载/容灾不掺进映射**：映射只产出"模型名"，多渠道选择/重试仍由 Ability + 现有路由完成（见 §3.3）。

### 3.3 与现有 Channel.ModelMapping / Ability 的执行次序（必须讲清）

**总执行链（含现有渠道级映射）**：

```
C ──[客户层 UserModelAlias]──▶ A ──[超管层 PlatformModelMapping]──▶ B
                                                                    │
                                          按 (Token.Group/User.Group) × B 查 Ability 选 Channel
                                                                    │
                                  B ──[渠道级 Channel.ModelMapping(现有)]──▶ B'(渠道内重定向)
                                                                    │
                                                           以 B'(= info.UpstreamModelName) 调上游
```

| 层 | 映射 | 作用域 | 选渠之前/之后 | 实现 |
|---|---|---|---|---|
| L1 客户层 | C→A | 用户/分组 | **之前** | 新增 `UserModelAlias` 表（§4.2），新增解析步 |
| L2 超管层 | A→B | 全局 | **之前** | 新增 `PlatformModelMapping` 表（§4.1），新增解析步 |
| L3 渠道级 | B→B' | 单渠道 | **之后**（选定 Channel 才知道） | **复用现有** `Channel.ModelMapping` + `relay/helper/model_mapped.go` 的链式内核 |

**冲突 / 谁先谁后裁决（写死的次序）**：
1. L1、L2 在 `Distribute` 中间件**之前**（或其早段）执行：因为 Ability 的路由维 `model` 必须是 B，不是 C/A。→ 顺序 **L1 → L2 → 选渠 → L3**。
2. L3（渠道级）是渠道内的二次重定向，定位/语义不变（new-api 现有行为），保留以兼容已配置的渠道。L2 与 L3 都可能改写模型名，但 **L2 决定"调哪个上游模型/算哪个价/记哪个 B"，L3 只决定"该渠道内部用什么名字请求"**。统计落点 B 取 **L2 之后的值**（= 计费与公开语义的"真实上游模型"），L3 的 B' 仅作为 `Other` JSON 里的诊断信息记录，不覆盖 B。
3. **协议转换的 targetProto 由"选中的 Channel.Type"决定**，与 L3 无关（L3 只改名不改协议）。

> 落地点：在现有 `relay/helper/model_mapped.go` **之前**新增 `relay/compat/modelmap` 的两层解析（产出 B 写 `info.UpstreamModelName` 初值与 ctx 三态），现有 model_mapped.go 保持原状处理 L3。两者通过 `RelayInfo` 衔接，互不重写。

---

## 4. 两层映射数据模型（新增表 / 字段级，归属·主键·作用域·生效顺序）

> 完整字段级建议见 COMPAT-LAYER-DATA-OBJECTS.md。此处给结构与归属决策。

### 4.1 超管层底仓映射 `PlatformModelMapping`（全局，客户不可见）— 新增表

| 维度 | 决策 |
|---|---|
| 表名 | `platform_model_mappings` |
| 主键 | `Id int`（自增，交 GORM 生成，跨 DB 安全）；业务唯一键 `PublicName` 唯一索引（一个公开名 A 只能映射到一个 B，保证 1对1） |
| 核心字段 | `PublicName(public_name) varchar(255);uniqueIndex`（= A）、`UpstreamName(upstream_name) varchar(255)`（= B）、`Enabled(enabled) bool default:true`、`Remark` |
| 作用域 | **全局**（无 group/user 维），所有请求都过 |
| 生效顺序 | L2（A→B），在选渠之前 |
| 可见性保证 | 该表**只通过 root/admin 路由读写**；客户侧任何 API（公开模型列表、映射候选、用量）**绝不 join / 返回 `upstream_name`**。见 §6 权限边界。 |
| 公开模型列表来源 | A 的全集 = `PlatformModelMapping.public_name` ∪ 现有 PrefillGroup(type=model) ∪ 直通模型。**客户能选的 A 来自这个全集，B 不在其中。** |

### 4.2 客户层自助映射 `UserModelAlias`（分组级 / 用户级，基于平台公开名）— 新增表

| 维度 | 决策 |
|---|---|
| 表名 | `user_model_aliases` |
| 主键 | `Id int` 自增；业务唯一键复合 `uniqueIndex(scope_type, scope_id, alias)`（同作用域内别名 C 唯一，保证 1对1） |
| 核心字段 | `ScopeType(scope_type) varchar(16)`（枚举 `user`/`group`）、`ScopeId(scope_id) varchar(64)`（user→`user_id` 字符串化；group→分组名，对齐 `User.Group`/`Token.Group` 的 `varchar(64)`）、`Alias(alias) varchar(255)`（= C）、`Target(target) varchar(255)`（= A，平台公开名）、`Enabled bool default:true` |
| 作用域 | 分组级或用户级；**只在该 scope 的请求生效**，不进公开列表、不影响别人 |
| 生效优先级 | **user 级 > group 级**（同一 C 同时存在 user 与 group 映射时取 user）。落地：解析时先查 `scope_type=user AND scope_id=当前userId`，未命中再查 `scope_type=group AND scope_id=当前group`。 |
| 输入约束 | `Target` **不强制白名单**（铁律）：客户硬输入平台没有的名字也允许存。候选提示来自公开模型列表（前端联想），但写入不拦。调用时若 A 在 L2 查不到底仓且不是直通模型 → 自然 404（"那是客户的事"）。 |

### 4.3 与现有表的关系小结
- **复用**：`Channel.ModelMapping`（L3）、`Ability`（路由维 model=B）、`Token.Group`/`User.Group`（作用域键）、`Token.ModelLimits`（白名单——注意：白名单校验对象是 **C（客户输入名）** 还是 A？决策见 COMPAT-LAYER-API-STATE.md §「白名单与映射的次序」）。
- **新增**：`PlatformModelMapping`、`UserModelAlias` 两张表 + `Log` 三段式字段扩展。

---

## 5. 配置作用域与权限边界

| 能力 | root/admin | 普通用户(common) | 保证机制 |
|---|---|---|---|
| 配 L2 底仓映射 A→B | ✅ 读写（`AdminAuth`/`RootAuth` 路由组） | ❌ | 路由层中间件拦截；controller 不暴露写入口给 user 路由 |
| 看 B（upstream_name） | ✅ 完整 C→A→B | ❌ 永不可见 | (1) 公开模型列表只返 A；(2) 映射候选只返 A 全集；(3) 用量列表 user 视图字段级裁剪掉 `actual_upstream_model`（见数据对象文档）；(4) `PlatformModelMapping` 无任何 user 路由读接口 |
| 配 L1 别名 C→A | ✅（可代配）| ✅（自助，仅自己 scope） | user 路由写 `UserModelAlias` 时强制 `scope_id` 注入为本人 user_id / 本人所属 group，禁止跨 scope 写（对齐现有 self-scope `where user_id=:caller` 护栏，ARCHITECTURE-REVIEW §6） |
| 看 L1 别名 | ✅ 全量 | ✅ 仅本 scope | model 层强制 scope 过滤 |
| 用量统计可见链 | C→A→B（全链） | **仅 C→A** | Log 查询接口按角色裁字段 |

**「客户绝对看不到 B」的三道闸**（纵深防御）：
1. **数据层**：`PlatformModelMapping`、`Log.actual_upstream_model` 不出现在任何 `UserAuth` 路由的查询/序列化路径。
2. **序列化层**：用量/日志 DTO 分 `UserLogView`（无 B）与 `AdminLogView`（有 B），按调用者角色选 DTO（不靠前端隐藏）。
3. **映射输入层**：客户配 C→A 的候选来源是"公开名全集"，该全集生成时**不包含** B。

---

## 6. 可插拔扩展：加第三个协议（Gemini）要做什么 + 改动面

| 改动 | 是否必须 | 工作量 |
|---|---|---|
| 新建 `relay/compat/gemini_adapter.go`，实现 `ProtocolAdapter`（6 个方法，可大量复用现有 `relay/channel/gemini` convert） | 必须 | 中（差异点 D1–D5 的 Gemini 侧映射） |
| 在 `init()` 调 `Register(geminiAdapter)` | 必须 | 极小（1 行） |
| `types/relay_format.go` 已有 `RelayFormatGemini` | 已存在 | 0 |
| IR 是否需扩字段 | 仅当 Gemini 有 OpenAI/Claude 都没有的语义（如 `safetySettings`）→ 走 `PassthroughExtras` 旁路 | 小 |
| 管线 ①–⑥ | **零改动**（注册表驱动） | 0 |
| 入站路由 `/v1beta/...` 识别 → `GenRelayInfoGemini` 设 `RelayFormat=gemini` | 若要支持 Gemini 入站端点 | 小 |

> **结论**：加协议的改动面 = 1 个新文件（适配器）+ 1 行注册 +（可选）IR 旁路扩展。管线与权限边界不动。这正是"协议转换只看头尾 + 注册表"带来的扩展性。

### 现有 per-channel convert 的迁移策略（不破坏现网）
- **首版不强制迁移**：协议适配器壳优先用于"入站协议 ≠ 目标协议"的跨协议场景（OpenAI⇄Anthropic）。同协议直通仍走现有 `Adaptor.ConvertXxxRequest` 路径。
- `relay/compat.ProtocolAdapter` 的 OpenAI/Claude 实现内部**调用现有 channel convert 逻辑**，避免重写已验证代码。后续可逐步把分散逻辑收敛进 compat 包（记为 ADR-COMPAT-02，非本期阻断项）。

---

## 7. 关键设计决策清单（ADR 摘要）

- **ADR-COMPAT-01**：首版 IR 只强建模 5 个差异点（system 位置 / content 结构 / tools / stop_reason / usage），其余字段 `PassthroughExtras` 旁路透传。
- **ADR-COMPAT-02**：协议适配器壳首版复用现有 per-channel convert，不重写；逐步收敛。
- **ADR-COMPAT-03**：模型映射执行次序固定为 **L1(C→A, 客户层) → L2(A→B, 超管层) → 选渠 → L3(B→B', 渠道级现有)**；统计 B 取 L2 之后值。
- **ADR-COMPAT-04**：协议转换只在"确定 B + 选中 Channel"后做一次，输入仅 `inFmt` 与 `targetProto`，与映射跳数解耦。
- **ADR-COMPAT-05**：B 不可见 = 数据层 + 序列化层 + 输入候选层三道闸，不靠前端隐藏。
- **ADR-COMPAT-06**：客户层 Target(A) 不强制白名单，未命中调用期自然 404。
