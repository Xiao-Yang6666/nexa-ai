# DECISION-LOG — Slice C (r6c-square)

## 模型广场与超管模型配置端到端连通

### 现状摸查结论
排查「超管配置 → 模型广场展示」三条读路径，发现 **只有一条脱节**：

| 读路径 | 端点 | 数据源 | 与超管 PublicModel 同源? |
|---|---|---|---|
| C 端模型广场 `(public)/models` | `GET /api/pricing` → `QueryPublicPricingUseCase` | `PublicModelRepository.findAllEnabled()` | ✅ 同源 |
| relay 模型列表 | `GET /v1/models` → `ListPublicModelsUseCase` | `PublicModelRepository.findEnabledNames()` | ✅ 同源 |
| 用户可见模型 | `GET /api/user/self/models` → `ModelSquareUseCase.visibleModels` | `ChannelModelCatalog.visibleModelsForGroup()`（读 channel.models 串） | ❌ **脱节** |

超管经 `ManagePublicModelUseCase`（`/api/public_models`）写 `public_models` 表；前端 `ModelsAdminPage`
四 Tab、`(public)/models` 均已真实接线（非 mock，mock 仅在 `NEXT_PUBLIC_USE_MOCK=1` 时拦截）。
即「超管写 / C 端广场读」本就同源——用户反馈的脱节落在 `visibleModels` 读 channel 这一条。

### 决策：visibleModels 改读 PublicModelRepository（单一数据源）
- 依据领域既有决策 **F-6004 / COMPAT §5**（见 `PublicModel.java` 类注释）：「上架即全员可用，
  不再用分组圈定可见模型」，可见性唯一裁决 = `PublicModel.enabled=true`。
- 故 `ModelSquareUseCase.visibleModels` 改为返回 `publicModelRepository.findEnabledNames()`，
  与超管写入、C 端 `/api/pricing` 同源。`UserGroupQuery` 保留仅用于校验用户存在（会话契约，
  不信任入参 user_id），不再据分组过滤。
- 连带清理：`ChannelModelCatalog.visibleModelsForGroup` 及 adapter 实现、`channelServesGroup`
  辅助方法已无调用方（正是引发脱节的死方法），一并删除，防止未来再被误接。
- `channelToModels()`（`GET /api/models/dashboard`，F-3024 运维诊断「渠道→模型」视图）保留读
  channel——渠道是其天然真源，与商品目录可见性无关，不动。

### 边界遵守
- 只动 `com.nexa.model.**`（application + port + infrastructure/catalog）+ 验证前端 model 页。
- 未碰 PlatformModelMapping/UserModelAlias（Slice B）、account 域、channel 写路径、relay 域。
- 无需 Flyway 迁移（复用现有 `public_models` 表，`V31__model_square.sql` 未使用）。

### 验收
- 新增 `ModelSquareClosedLoopTest`（5 用例，纯内存桩）：超管上架→可见+对外全集查得到；
  下架/软删→查不到；可见列表为上架全集去重保序；未知用户→异常。
- 后端全量 `mvn test`：542 通过，0 失败。
- 前端 `npm ci && npx next build`：通过（`/models` 路由正常产出）。
