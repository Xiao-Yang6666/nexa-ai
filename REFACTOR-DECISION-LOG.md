# Nexa 后端系统性重构 — DECISION LOG

> 本轮重构目标：消除\"长得像 DDD 但缺横向抽象层\"的坏味道，让代码可持续迭代。
> 基线：main `852b9b3`，584 测试全绿，可构建。

## 阶段划分（严格串行）

- **阶段 A（主控亲做）**：打地基公共抽象层。所有域都依赖它，必须先冻结才能并行，否则多 worker 同改公共件必冲突。
- **阶段 B（并行 CC，地基冻结后）**：按域接入地基、消除各域重复样板。每 CC 一个 git worktree 隔离。

---

## 已确认坏味道清单（量化）

| # | 坏味道 | 规模 | 处置阶段 |
|---|--------|------|---------|
| 1 | `DomainException` 各域副本 | 8 份（7 带 httpStatus 同构 + 1 telegram 简单型） | A ✅ |
| 2 | 配置项散落各域 infra/config | 10 个自建 config 包 | A（计划） |
| 3 | 裸 `@Value` 注入 | 8 文件 | A（计划） |
| 4 | RepositoryImpl 手写映射 | 28 个 / 4039 行 | B |
| 5 | `.stream().map(toDomain)` 样板 | 15 文件 | B |
| 6 | `PageRequest.of(page-1,size)` 样板 | 12 文件 | B（接 PageQueries） |
| 7 | 手写 JSON try-catch 编解码 | 5 持久化 + 4 特殊 | B（接 JsonbCodec） |
| 8 | `Pagination` 值对象同构副本 | account/channel/deployment 各 1 | B（评估收敛） |

---

## D001 — DomainException 去重（阶段 A，已完成）

**决策**：7 份带 `httpStatus` 语义的同构 `DomainException` 副本（growth/log/observability/ops/playground/relay/task）删除，统一继承新建的 `com.nexa.shared.kernel.HttpAwareDomainException`（code + httpStatus + message[+cause]）。

**不动**：`telegram` 的 `DomainException` 签名是 `(message)` / `(message, cause)`，无 code/httpStatus，与 shared/kernel 既有的简单型 `DomainException`（code+message）签名也不兼容；它只有 1 份（非重复大头），强行合并会污染契约或改子类语义，故保留独立。

**结果**：改动 36 文件，584 测试全绿。

## D002 — 公共持久化工具（阶段 A，已完成）

**决策**：在 `nexa-service` 新建 `com.nexa.shared.persistence` 包（nexa-common 不含 spring-data，持久化工具不能放公共内核模块）：

- `JsonbCodec`（`@Component`）：统一 JSONB 字段序列化/反序列化，失败 wrap 成 `JsonbCodecException`。收敛 5 份持久化层手写 try-catch。
- `PageQueries`（静态工具）：`of(page1Based, pageSize[, sort])` → Spring Data `Pageable`，统一 1-based→0-based 减一转换。收敛 12 份 `PageRequest.of(page-1,size)` 样板。

**样板示范**：`channel/infrastructure/persistence/ChannelRepositoryImpl` 已接入两者（阶段 B 的 worker 照此范例改各自域）。

**语义净化（顺带）**：channel_info JSON 解析失败原抛 `ChannelUpstreamException`（→502，上游故障语义），改为 `JsonbCodecException`（→全局兜底 500）。JSON 列损坏是本地数据问题非上游故障，500 比 502 语义更准确。这是有意的行为修正，非回归。

---

## 待办（阶段 A 剩余）

- D003：配置项收敛 — 评估把散落的裸 `@Value` 收进各域已有的 `@ConfigurationProperties`，公共配置约定下沉。
- D004：补 nexa-common/shared 缺失的纯函数工具（按层放置，不堆 util 包）。
