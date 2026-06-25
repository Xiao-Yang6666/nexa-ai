# 重构方案：渠道/账号 ↔ 模型商品 两页重切 + 渠道级 A→B（删全局表，一次交付）

## 决策（已确认）
1. A→B 彻底改成**渠道级**：转发链路废弃全局 `platform_model_mappings`，改读选中渠道的 `modelMapping`。
2. 全局表**直接删除**（含两套 BC 的全部代码 + DB 表）。
3. **一次交付**（单批次，前后端一起，全量测试回归）。
4. 成本倍率粒度：**渠道×B 为主 + 批量填充**（不改计费查成本逻辑，只加批量 upsert 入口）。
5. 分组**售价**倍率已接 `ModelGroupPricingPort`，不动。

## 关键架构洞见（决定改动面）

当前链路：`C →(L1用户别名)→ A →(L2全局表)→ B → 用B查abilities选渠 → 成本(channelId,B)`
- 选渠 `selectChannel(group, model, tried)` 的 model 传的是 **B**；`abilities.model` 列存的是 `channel.models` 逗号项（即 B）。
- `channel.modelMapping` 字段后端**只存取、转发零引用**（relay 包内无任何读取）。

目标链路：`C →(L1)→ A → 用A查abilities选渠 → 选中渠道用其modelMapping做A→B → 用B请求上游 & 成本(channelId,B)`

### 简化点
选渠口径从 B 改 A，**只需把 `channel.models` 的语义从"上游真实名"改成"对外名 A 列表"**——abilities fan-out 逻辑一行不用改（仍按 models 逗号 fan-out），改的是"往 models 里填什么"（填 A）。选渠 `selectChannel` 入参从 `upstreamModel` 换成 `resolvedPublic`。

## 后端改动

### A. 转发内核 `RelayForwardUseCase`（forward + forwardStream 对称）
- `resolveModel`：删 L2 lookup，`TwoLayerModelResolver.resolve` 的 l2Lookup 传 `null`（保留单层 C→A）。`ModelResolution.upstream` 此时 = A（恒等）。
- 选渠：`selectChannel(group, resolution.resolvedPublic(), tried)` —— 用 A。
- 选中渠道后新增一步 `resolveUpstreamModel(channel, A)`：解析 `channel.modelMapping`（JSON map {A:B}）得真实 B；未命中则 B=A。
- `buildUpstreamBody(...)` 与 `resolveCostRatio(channel.id(), B)` 用这个 B。
- 删除 `PlatformModelMappingRepository l2Repo` 注入。

### B. 删除全局映射（27 文件中的全局映射部分）
**整删**（model BC + relay BC 两套）：
- model BC：`PlatformModelMapping`(domain) / `PlatformModelMappingRepository`(+impl) / `PlatformModelMappingJpaEntity` / `SpringDataPlatformModelMappingJpaRepository` / `ManagePlatformModelMappingUseCase` / `PlatformModelMappingController` / 4个DTO(`...CreateRequest/UpdateRequest/AdminView/ListView`) / `PlatformModelMappingNotFoundException`
- relay BC：`PlatformModelMapping`(domain) / `PlatformModelMappingRepository`(+impl) / `PlatformModelMappingJpaEntity` / `SpringDataPlatformModelMappingRepository` / `PlatformMappingView` / `ManageMappingUseCase`(里 L2 部分；L1 别名部分若被复用则保留拆分)
- DB：新增 `V__drop_platform_model_mappings.sql`（DROP TABLE）。
**改**：
- `ModelExceptionHandler`：去掉 `PlatformModelMappingNotFoundException` 分支。
- `ListPublicModelsUseCase`：注释提到 B 来源，确认不实际依赖（只读 PublicModelRepository），无代码改动。
- `TwoLayerModelResolver`：保留（L1 仍用），仅调用方 l2Lookup 传 null；或保留双层能力但生产不传 L2。

### C. 成本批量 upsert
- `ChannelModelCostController` + UseCase 加 `POST /api/channel_model_costs/batch`：按 channelId 列表 / 或 (channel, model) 列表批量写 cost_ratio。

### D. 测试回归
- `TwoLayerModelResolverTest`：L2 用例删除/改为单层。
- `RelayForwardUseCaseRetryTest` / `StreamTest`：去 mock l2Repo，改为渠道 modelMapping 驱动 B。
- 全量 `JAVA_HOME=corretto-21.0.5 mvn test`（493 基线）。

## 前端改动

### 页面①「渠道/账号」`ChannelsAdminPage`
- 列表加**分组列 + 分组筛选**；批量栏加**批量设成本倍率**。
- 编辑抽屉分区化：基本信息 / **支持模型 A**（textarea→可加每模型成本倍率行）/ **模型重定向 A→B 可视化表格**（替换裸 JSON/=分隔 textarea，存回 model_mapping JSON）。
- 新增 api：`getChannelModelCosts`(挪用)、`batchUpsertChannelCosts`。

### 页面②「模型商品」`ModelsAdminPage`
- 删"供应商成本"Tab（挪进渠道页）。
- "供应商元数据"Tab 改名"模型厂牌"。
- 删对外模型抽屉里"A→B在平台映射单独维护"提示。

### shared/api
- 重新生成 `schema.ts`（删 `/api/platform_model_mappings` 两个端点，加 batch cost 端点）。
- `types.ts`/`index.ts`：删 PlatformModelMapping 类型导出；model_mapping 字段保留。
- 删 `model-admin.api.ts` 的 `getModelMappings`；`ModelMapPage`（客户自助 C→A）不动。

## 交付顺序（单批次内）
1. 后端转发内核改 + 渠道 modelMapping 解析（先让转发自洽）。
2. 后端删全局映射全套 + DROP 迁移 + 成本 batch 端点。
3. 后端全量测试绿。
4. 前端 schema 重生成 + 两页重组 + 类型清理。
5. 前端构建 + 冒烟。

## 风险
- 选渠口径 B→A 是语义切换：**现存渠道的 `models` 字段若填的是上游真实名，迁移后选渠会错配**。需在 DROP 迁移同批，把现有 channels.models 视为 A（多数场景 A=B 名称相同，无影响；命名不同的渠道需人工核对 modelMapping）。这点交付时需提示运营核对。
- abilities 是 fan-out 派生表，渠道 save 时自动重建，无需手动迁移。
