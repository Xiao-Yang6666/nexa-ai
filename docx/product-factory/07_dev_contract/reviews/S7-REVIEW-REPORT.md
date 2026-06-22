# S7 开发契约 独立评审报告

> 评审对象：`07_dev_contract/final/`（DB-SCHEMA.md / MIGRATION-PLAN.md / API-ENDPOINTS.md / openapi.yaml / BACKLOG.csv / WAVE-PLAN.md / API-COVERAGE.csv）
> 评审方式：独立 reviewer，不采信生产者自报；全部结论基于 Python `csv`/`re`/`yaml.safe_load` 复验 + 逐文件抽读。
> 上游基准（只读对照）：`02_decomposition/final/FUNCTION-LIST.csv`（243 功能）、`03_flow_prd/final/DATA-MODEL.md`。

---

## 总评：**PASS_WITH_GAPS**

覆盖真 miss=0、客户视图 DTO 无泄露、DB Schema 完全对齐、BACKLOG 1:1、WAVE 依赖方向正确、openapi 可解析——六条硬指标全部通过。**唯一实质 gap：openapi.yaml 只覆盖了人读契约约一半的端点面，整块管理端 CRUD（渠道 / 供应商 / A→B 映射 / 成本配置 / 模型元数据）在 openapi.yaml 中缺失**，且 BACKLOG.csv 的 `depends_on` 列 243 行全空。两者均为「机读产物不完整」而非「设计错误或漏功能」，故定 PASS_WITH_GAPS 而非 REWORK。三类硬伤（漏功能 / 契约含糊 / 客户视图泄露 B 与成本）**均未发现**。

---

## 维度逐条结论

### 1. 覆盖真 miss=0 吗 —— ✅ PASS（脚本实测）

- `FUNCTION-LIST.csv` 共 **243** 个 `function_id`，唯一、无重复（`Counter` 验证）。
- 正则提取 `API-ENDPOINTS.md` 全文 `F-\d{4}`，与 243 ID 对账：**正向 miss = 0**（`set(fids) - set(found) == []`）。
- `API-COVERAGE.csv` 243 行、`BACKLOG.csv` 243 行，均 1:1 覆盖全部 243 功能，无漏、无多。
- 唯一「多出」的 `F-3060 / F-3061 / F-3062` 经核查为 `prd-relay.md` 旧局部编号，文档第 1103 行明确标注已映射回权威号 **F-6010/F-6011/F-6012**，属对账说明而非孤儿端点。
- 旁注：FUNCTION-LIST 自身编号缺 `F-3038`（242→243 连续性由实际 ID 集决定，不影响覆盖）。

**判定：覆盖完整性真实达标，非自报。**

---

### 2. API 契约到可实现级吗 —— ✅ PASS（抽查 8 端点 + ref 解析）

抽查 relay / 账号 / 计费 / 日志 / 渠道 / 利润各域，openapi 对应 operation 均具备 requestBody（如适用）/ responses / 错误码 / 鉴权：

| 端点 | reqBody | responses | security |
|---|---|---|---|
| `POST /v1/chat/completions` | ✅ `OpenAIChatCompletionRequest` | 200/401/403/429（含 SSE `text/event-stream`） | bearerAuth |
| `POST /v1/messages` | ✅ | 200/401/403 | bearerAuth |
| `POST /api/user/register` | ✅ | 200/400 | （公开，符合预期） |
| `POST /api/user/login` | ✅ | 200/400 | 公开 |
| `GET/POST/PUT /api/token/` | ✅ | 200/400 | sessionAuth |
| `GET /api/log/self` | — | 200/401 | sessionAuth |
| `GET /api/profit/dashboard` | — | 200 | **adminAuth** |

- 全 121 operation **100% 有 responses、100% 有 security**；49 个有 requestBody（与读端点不需 body 一致）。
- `$ref` 全量解析：59 个 distinct `$ref`，**0 个 broken**；错误响应通过 `components/responses`（UnauthorizedError/ForbiddenError/BadRequestError）复用，错误码带语义描述。
- 已覆盖的端点确实到「入参 schema + 出参 schema + 错误码 + 鉴权」可实现级。

**判定：已落 openapi 的端点为可实现级契约，非仅端点名。**（覆盖范围问题见 Gap-1。）

---

### 3. 客户/管理视图 DTO 真分开吗 —— ✅ PASS（铁律未破，零泄露）

对 openapi 全部 schema 做敏感字段扫描（`cost/profit/upstream/supplier/channel_id/channel_name/upstream_model/upstream_name/cost_ratio/actual_upstream_model`）：

- **所有客户视图 schema 全部 clean**：`UserView` / `UserLogView` / `TokenUserView` / `TopUpUserView` / `SubscriptionPlanUserView` / `UserSubscriptionView` / `PublicModelUserView` / `UserModelAliasUserView` / `TaskUserView` —— 均**不含** `quota_cost` / `quota_profit` / `actual_upstream_model(B)` / `upstream_name(B)` / `cost_ratio` / `channel_id` / `channel_name`。
- 敏感字段被严格关进管理视图：`AdminLogView`（含 actual_upstream_model/quota_cost/quota_profit/channel_name/upstream_request_id）、`ProfitDashboardItem`（sum_quota_cost/sum_quota_profit/profit_rate）、`ChannelModelCostAdminView`、`PlatformModelMappingAdminView`、`ChannelPoolMember`。
- `GET /api/profit/dashboard` 强制 `adminAuth`；API-ENDPOINTS.md 第 601/610/618/853/1090/1318 行多处用「铁律 / 结构级剔除 / 绝不下发客户」明文钉死 B 不可见三道闸。
- **`UserLogView.upstream_protocol` 非泄露**：经核（API-ENDPOINTS 第 1124/1317/1318 行）`upstream_protocol` 是「上游线协议名（openai/anthropic）」即对外标准协议格式，被设计**有意保留**；真正敏感的 `actual_upstream_model(B)` / `quota_cost` / `quota_profit` / `channel` 被列入 UserLogView「结构级剔除」清单，确实不在客户视图中。判定为正确区分，非泄露。

**判定：客户/管理 DTO 真分开，成本/利润/真实模型 B/供应商零泄露。产品铁律守住。**

---

### 4. DB Schema 字段对齐 DATA-MODEL 吗 —— ✅ PASS（4 张新表逐字段比对）

逐字段比对 4 张新表 DB-SCHEMA.md ↔ DATA-MODEL.md：

- **PublicModel**（`public_models`）：id/public_name(uk)/quality_tier/base_price_ratio/use_price/base_price/enabled/display_name/sort_order/description/时间戳/软删 —— **逐字段对齐，无自创**。
- **PlatformModelMapping**（`platform_model_mappings`）：id/public_name(uk,1对1)/upstream_name(B,not null)/enabled/remark/时间戳/软删 —— **对齐**。
- **UserModelAlias**（`user_model_aliases`）：id/scope_type/scope_id/alias + 复合唯一索引 `uk_scope_alias(scope_type,scope_id,alias)`/target(不校验白名单)/enabled/时间戳/软删 —— **对齐**，越权护栏（强制 scope_type=user）已注记。
- **ChannelModelCost**（`channel_model_costs`）：id/channel_id/upstream_model(B) + `uk_channel_model`/cost_ratio/completion_cost_ratio/enabled/effective_time/source_unit_price(扩展位)/remark/时间戳/软删 —— **对齐**。
- 自增主键统一交 GORM、新列均带 `default`、三库（MySQL/PG/SQLite）注意事项齐备。
- 生产者自附 GAP 汇总（Channel 软删除、SubscriptionPlan/Order/UserSubscription 的 json tag 推导）已核：均为「DATA-MODEL 表述不完整」而非字段缺失，**未自创新字段**，留待架构师确认——属诚实标注，可接受。

**判定：DB Schema 与 DATA-MODEL 权威对齐，无自创字段。**

---

### 5. BACKLOG / WAVE 依赖合理吗 —— ⚠️ PASS_WITH_GAPS

- **BACKLOG 1:1**：243 task 对 243 功能，无漏无多，`function_id` 全部可解析回 FUNCTION-LIST。✅
- **WAVE 方向正确（核心关切已守住）**：W1 基础设施+鉴权+账号（66）→ W2 数据层核心域 渠道/模型/映射/计费/令牌（72）→ W3 业务转发 Relay/日志/Playground（46）→ W4 增长+对外页（22）→ W5 NFR/合规/运维（37）。WAVE-PLAN.md 明文「relay 依赖 W2 渠道+模型映射+计费就绪」「必须先有渠道和模型目录 relay 才有东西可转」——**任务书最担心的「relay 倒置于渠道/映射之前」没有发生，方向正确**。
- 波间依赖在 WAVE-PLAN.md「前置依赖」列 + 各波「关键依赖链」散文中**完整刻画**（W2 依赖 W1，W3 依赖 W2，W4 依赖 W2+W3）。
- 跨波依赖方向 0 违例（脚本：无任一 task 的 depends_on 指向更晚 wave）。

**Gap-2（机读依赖缺失）**：`BACKLOG.csv` 有 `depends_on` 列，但 **243 行全为空**（脚本实测 empty=243 / withdep=0）。任务级依赖边完全未填，排序完全靠 5 波分组隐式保证。相应地，relay 任务（T-128/T-138/T-242 等）的 `depends_on` 也为空，未在数据层显式声明「依赖渠道 CRUD / A→B 映射 / 计费倍率」任务。逻辑依赖在 WAVE-PLAN 散文中存在且正确，但机读派活/拓扑校验工具拿不到边。**非倒置、非漏依赖，属机读产物不完整。**

---

### 6. openapi.yaml 真能解析吗 —— ✅ PASS（解析）/ ⚠️ 范围不全（见 Gap-1）

- `yaml.safe_load` **解析成功**：openapi 3.0.3，`len(paths)=104`、operations=121、schemas=79、securitySchemes 5 个（bearerAuth/tokenReadAuth/sessionAuth/adminAuth/rootAuth）。
- 全部 `$ref` 解析无 broken。

**判定：可解析、结构健康。但覆盖范围不足，见 Gap-1。**

---

## 真实 Gap 清单

### 🔴 Gap-1（重要）：openapi.yaml 缺失整块管理端 CRUD，机读契约仅覆盖人读契约约一半

脚本对比 `API-ENDPOINTS.md` 端点标题（`### METHOD /path`，195 条 / 归一化后 162 个 distinct path）与 `openapi.yaml`（104 path）：**约 79 个归一化路径在人读契约里有、openapi.yaml 里没有**。其中大量是本项目核心管理面：

- **渠道 CRUD**：`/api/channel` GET/POST/PUT/DELETE、`/api/channel/search`、`/api/channel/batch`、`/api/channel/test`、`/api/channel/update_balance`、`/api/channel/{id}/upstream/apply`、`/api/channel/{id}/ollama/*` —— openapi 里只有 `channel_affinity_cache/clear` 两个辅助端点，**渠道增删改查主体完全缺失**。
- **供应商（Vendors）CRUD**：`/api/vendors` 全套 —— openapi 中 `vendor` 命中 **0**。
- **PlatformModelMapping（A→B 映射）CRUD**：`/api/platform_model_mappings` 全套 —— openapi 中命中 **0**。这是本期核心新功能。
- **ChannelModelCost（成本配置）CRUD**：`/api/channel_model_costs` 全套 —— openapi 中命中 **0**。这是利润引擎的输入面。
- **模型元数据 / 同步**：`/api/models`、`/api/models/search`、`/api/models/sync(/preview)`、`/api/models/missing`、`/api/models/dashboard` —— openapi 中缺失。
- **UserModelAlias 写操作**：`/api/user/self/model_aliases` POST/PUT/DELETE —— openapi 中仅有 `/candidates`，**写端缺失**。
- 另含 deployments / performance / setup / about / notice 等运维与对外页端点。

注：上述差集已剔除中文标点合并标题（如 `/v1/chat/completions、post`）等格式假阳性；核心管理 CRUD 的缺失为真实缺口（`yaml` 直查 `channel/vendor/platform_model/channel_model_cost/deployment` 子串确认 openapi 中无对应 path）。

**衍生问题**：`API-COVERAGE.csv` 将 F-2016「渠道 CRUD/搜索/批量」标为 `in_openapi=N / endpoint_type=横切·系统内部·交叉引用`——这是**错误归类**：渠道 CRUD 是主力管理 REST 面（API-ENDPOINTS.md 第 855+ 行有完整 GET/POST/PUT/DELETE 规格），不是系统内部行为。该类标注可能掩盖了「这些端点本该进 openapi 但没进」的事实。

**影响**：人读契约（API-ENDPOINTS.md）完整且可实现；但若团队拿 openapi.yaml 做 mock/codegen/前后端契约校验，管理端写侧（含本期新表的全部配置入口）将无机读契约可依，需回退人读 md 手工实现，丧失「契约即代码」收益。

**建议（不阻塞，但应补齐再进 S8 前端 mock）**：把 API-ENDPOINTS.md 已规格化的管理 CRUD（channel / vendors / platform_model_mappings / channel_model_costs / models+sync / model_aliases 写端）补进 openapi.yaml，并修正 API-COVERAGE.csv 中 F-2016 等的 `in_openapi/endpoint_type` 误标。

### 🟡 Gap-2（次要）：BACKLOG.csv `depends_on` 列 243 行全空

任务级依赖边未填，拓扑排序仅靠 5 波分组隐式承载。波间依赖逻辑在 WAVE-PLAN.md 中完整且方向正确（relay 在渠道/映射之后），故不阻塞人工派活；但机读派活/并行度计算/关键路径分析工具无法使用。**建议**：至少为跨波关键依赖（relay→渠道+映射+计费、令牌→User+计费）回填任务级 `depends_on`。

### 🟢 旁注（不计 gap）
- FUNCTION-LIST 编号缺 `F-3038`（不影响 243 集合完整性）。
- 生产者 DB-SCHEMA GAP 汇总（Channel 软删除等 4 条）属诚实标注 DATA-MODEL 表述不完整，未自创字段，留待架构师确认——保留即可。

---

## 三类硬伤核查结论

| 硬伤类型 | 结论 | 证据 |
|---|---|---|
| 漏功能 | **未发现** | 243→API-ENDPOINTS 正向 miss=0；BACKLOG/COVERAGE 均 1:1 |
| 契约含糊不可实现 | **未发现** | 抽查端点均有 入参/出参/错误码/鉴权；$ref 0 broken（已落 openapi 的端点） |
| 客户视图泄露成本/B/供应商 | **未发现** | 全部 *UserView schema clean；B/cost/profit 仅在 Admin 视图；profit 端点 adminAuth |

---

## 验证脚本要点（可复现）

```python
# 覆盖：FUNCTION-LIST 243 ID  vs  API-ENDPOINTS.md 正则 F-\d{4}  → 正向 miss=0
# DTO ：yaml.safe_load(openapi) → 扫描 *UserView schema 敏感字段 → 0 命中
# openapi：yaml.safe_load → paths=104 ops=121 schemas=79 ; $ref 全解析 0 broken
# WAVE：BACKLOG depends_on 跨波方向 0 违例 ; 但 243 行 depends_on 全空
# 范围：API-ENDPOINTS 端点标题(162 path) - openapi(104 path) = 79 缺口（核心管理 CRUD）
```

---

**评审人**：独立 reviewer（subagent）
**最终判定**：**PASS_WITH_GAPS** —— 设计正确、铁律守住、覆盖与对齐达标；补齐 openapi.yaml 管理端 CRUD（Gap-1）与回填关键任务依赖（Gap-2）后即达 PASS。
