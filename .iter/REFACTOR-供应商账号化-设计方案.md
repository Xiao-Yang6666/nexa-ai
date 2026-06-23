# 供应商账号化 + 模型映射落盘 重构设计方案

> 状态：**待用户拍板**。参考蓝图：sub2api（已 dump 真实表结构）。目标：把"乱腾腾"的 channel 单表重构成职责清晰的多实体，超管页/模型广场连上真生效的映射。

---

## 一、问题诊断（冷静、基于真实代码，非脑补）

### 1.1 旧 `channels` 单表混了 6 类职责（被 908 处代码引用，是转发中枢）

| 职责 | 字段 | 病 |
|---|---|---|
| 供应商凭证 | `key` | 没有"账号"概念，凭证是渠道属性 |
| 路由 | `weight`/`priority`/`group` | 和凭证混 |
| 模型集 | `models`（逗号串） | 非结构化 |
| **模型映射 A→B** | `model_mapping`（JSON 串） | **僵尸字段，见 1.2** |
| 定价/额度 | `balance`/`usedQuota` | 和路由混 |
| 杂配 | `setting`/`channelInfo`（两 JSON 黑盒） | 啥都塞 |

`Channel` 领域聚合 **859 行**，超载。

### 1.2 真相：模型映射有两套并存，一套在用一套是僵尸

- **机制1（真生效）**：`TwoLayerModelResolver` 两层别名
  - L1 客户层 C→A（`UserModelAlias`，user>group 优先级）
  - L2 超管层 A→B（`PlatformModelMapping`，全局底仓）
  - relay 转发链真正调用的就是这套，设计干净（纯函数+环检测+最大跳数）
- **机制2（僵尸）**：`Channel.modelMapping`（JSON 串）
  - 挂在 channel 聚合，**转发引擎不读它**
- **"配了不生效"根因**：超管页大概率配的是僵尸字段（机制2），所以连不上、不生效。

### 1.3 结论
重构 **不是照抄 sub2api 单层映射**（nexa 的两层别名更细且在生效）。正确做法：
1. 账号化（抄 sub2api 的 accounts）
2. 把已生效的 L1/L2 两层别名结构化落盘 + 接超管页 + 删僵尸 channel.modelMapping
3. 超管/模型广场连到机制1

---

## 二、sub2api 参考（已 dump，真实字段）

### accounts（供应商=账号）
关键字段：`platform` `type` `credentials(jsonb)` `concurrency` `priority` `status` `rate_limited_at` `rate_limit_reset_at` `overload_until` `session_window_*` `expires_at` `auto_pause_on_expired` `rate_multiplier`
→ 账号级并发/优先级/限流恢复/会话窗口/过期暂停，全有。

### account_groups（账号↔组多对多）
`account_id` `group_id` `priority`（组内优先级）

### channel_model_pricing（定价独立成表）
`channel_id` `models(jsonb)` `input_price` `output_price` `cache_*_price` `billing_mode` `per_request_price` `platform`
→ 定价从 channel 拆出来，按模型配价。

---

## 三、新目标架构（nexa 版，DDD）

### 新增域 `account`（供应商账号化）
```
account（聚合根）
  id / name / platform / type
  credentials(jsonb)  ← 凭证（敏感，加密落盘，绝不进视图）
  concurrency / priority / status
  rate_limited_at / rate_limit_reset_at / overload_until   ← 限流恢复
  expires_at / auto_pause_on_expired
  created_at / updated_at
account_group（账号↔组关联）
  account_id / group_id / priority
```

### 映射落盘（结构化 L1/L2，替换僵尸字段）
```
platform_model_mapping（L2 超管层 A→B，已有领域概念，落盘成表）
  source_model(A) / target_model(B) / enabled
user_model_alias（L1 客户层 C→A，已有领域概念，落盘成表）
  scope(user/group) / scope_id / source(C) / target(A)
```
→ 超管页直接 CRUD 这两张表，转发链 `TwoLayerModelResolver` 从表读 lookup。

### channel 瘦身
- 删 `model_mapping`（僵尸）、`setting`/`channelInfo` 黑盒按需拆
- channel 只留：逻辑渠道身份 + 关联 account + 模型集 + 状态

---

## 四、拆除顺序（先建后拆，不一刀 rm —— 否则 908 引用编译雪崩 + 转发瘫痪）

```
阶段0  设计冻结（本文档）
阶段1  新增 account/account_group 域 + 迁移（纯加法，不碰旧 channel）
阶段2  L1/L2 映射落盘成表 + TwoLayerModelResolver 从表读 + 超管页接新表
阶段3  转发/计费切到新结构（channel 关联 account 取凭证）
阶段4  僵尸 channel.modelMapping 腾空 → 删字段
阶段5  channel 黑盒字段（setting/channelInfo）按需拆 → 最终瘦身
```
数据不迁（旧数据乱，以本地为主，新库从零跑 Flyway）。

---

## 五、并行 slice 切分（文件不重叠，3 个 CC 并行）

| Slice | 范围 | 碰的文件 | 不碰 |
|---|---|---|---|
| **A 账号域** | 新建 account/account_group 实体+仓储+用例+超管账号管理页 | `com/nexa/account/**`（全新）+ 新 Flyway V29 + 前端 `account` 页 | 完全不碰 channel/relay |
| **B 映射落盘** | L1/L2 落盘成表 + Resolver 从表读 + 超管映射页 | `relay/domain/service/TwoLayerModelResolver` 读取端口 + 新 mapping 仓储 + 新 Flyway V30 + 前端映射页 | 不碰 account，不碰 channel 写路径 |
| **C 模型广场连通** | ModelSquareUseCase + 超管模型配置页连到机制1 | `model/**` catalog + 前端模型广场/配置页 | 不碰 account/mapping 写端 |

冲突点排查：
- Flyway 版本号预分配（A=V29 B=V30 C=V31），不撞。
- 三者都不写 channel 聚合，避免 Channel.java 并发改。
- 集成验收由主控串行做（合并后真起服务）。

---

## 六、红线（不变）
- 不自动合 main、不推生产、不花钱 —— 这三件停下问用户。
- CC 方案A：自主跑到绿+commit，主控事后亲验抽测。
- 每轮：S8开发 → S11真起服务复验 → S12复盘出下轮scope，才算闭环。
