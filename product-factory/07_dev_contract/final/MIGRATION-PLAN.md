# MIGRATION-PLAN — 迁移顺序与依赖契约（S7，Java/JPA + PostgreSQL 单库修订版）

> 项目：Nexa（**从零自研**，Java 21+ / Spring Boot 3.2+ / JPA(Hibernate 6) / **PostgreSQL 单库**）。
> 配套文件：`DB-SCHEMA.md`（同目录，JPA Entity 版）。上游权威：`03_flow_prd/final/DATA-MODEL.md`。
> **本版为 Java/JPA + PG 单库修订版**（S2.5 技术栈冻结 2026-06-20）。原 GORM AutoMigrate + 三库版已废弃。**迁移逻辑/依赖顺序/缓存初始化语义与原版一致——仅迁移工具从 GORM AutoMigrate 换成 Flyway，目标库收敛为 PostgreSQL 单库。**
> 范围：22 张物理表（本轮新增 4 张），Log +10 列、Token +2 可选列。

---

## 0. 迁移工具选型与原则

- **迁移工具：Flyway**（Spring Boot 集成 `spring-boot-starter`，`flyway-core` + `flyway-database-postgresql`）。版本化 SQL 脚本，可重复、可审计、生产可控。
  - 备选 Liquibase（XML/YAML changelog）；本契约用 Flyway SQL 脚本（更直观、贴近 PG 原生）。
- **不依赖 Hibernate 自动建表**：生产 `spring.jpa.hibernate.ddl-auto=validate`（启动只校验 Entity 与表结构一致，**不让 Hibernate 自动改表**）。表结构由 Flyway 脚本显式管理——这是企业级纪律，避免 ORM 隐式改表的不可控风险。
- 脚本放 `src/main/resources/db/migration/`，命名 `V<版本>__<描述>.sql`（如 `V1__baseline_schema.sql`、`V2__add_compat_billing_tables.sql`）。

---

## 1. 依赖分层（被依赖的基础表先建）

PostgreSQL 单库，可用真实外键约束（也可沿用逻辑外键，见 §1 末注）。按下列层级组织建表脚本，保证被依赖表先建：

| 层 | 表 | 依赖 | 说明 |
|---|---|---|---|
| L0 基础表 | channels、users、options | 无 | 被几乎所有表引用 |
| L0 基础表 | tokens | users | `user_id` 引用 users |
| L1 路由/凭据 | abilities、passkey_credentials、two_fas、two_fa_backup_codes、user_oauth_bindings、redemptions | channels/users | — |
| L1 业务 | top_ups、subscription_plans、subscription_orders、user_subscriptions、tasks、checkins、prefill_groups | users/plans/channels | subscription_orders/user_subscriptions 依赖 subscription_plans |
| **L2 本轮新增**（映射/商品） | **public_models、platform_model_mappings** | — / 同键 public_models.public_name | A 维度 |
| **L2 本轮新增**（别名/成本） | **user_model_aliases、channel_model_costs** | user_model_aliases→users(scope_id) / channel_model_costs→channels | C→A 别名、渠道×B 成本 |
| 日志（单库内同表） | logs | users/channels/tokens + public_models.public_name（逻辑） | PG 单库统一在主库，建议**按时间范围分区** |

要点：
- **users / channels / tokens 必须在关联表之前建**。
- 4 张新表建表顺序建议 public_models → platform_model_mappings → channel_model_costs → user_model_aliases，与缓存初始化顺序一致。
- **外键策略**：核心一致性域（计费/对账，如 subscription_orders→subscription_plans、user_subscriptions→users）建议用**真实外键 + 索引**（PG 支持好，保障引用完整性，这是从 GORM 三库兼容时代「全逻辑外键」改进的地方）；高写入量表（logs、abilities）可保持逻辑外键避免写入开销。具体由 S8 后端按子域定。

---

## 2. 建表脚本组织（Flyway 版本化 SQL）

放弃 GORM AutoMigrate 的运行时建表，改为显式 Flyway 脚本。建议按 baseline + 增量组织：

```text
src/main/resources/db/migration/
  V1__baseline_schema.sql          # 18 张复用表的完整 DDL（users/tokens/channels/abilities/logs/
                                    #   redemptions/top_ups/subscription_*/tasks/checkins/
                                    #   user_oauth_bindings/two_fa*/passkey_credentials/prefill_groups/options）
  V2__compat_billing_tables.sql     # 本轮新增 4 表 + Log 加 10 列 + Token 加 2 列
  V3__logs_partitioning.sql         # （可选）logs 按时间分区 + 索引
```

**V2 脚本要点（本轮核心变更）**：

```sql
-- 4 张新表（PG 原生 DDL，主键 BIGSERIAL，JSON 用 JSONB，金额/倍率 NUMERIC）
CREATE TABLE public_models (
    id              BIGSERIAL PRIMARY KEY,
    public_name     VARCHAR(255) NOT NULL,
    base_price_ratio NUMERIC(20,8),          -- 倍率：NUMERIC 不用 float
    base_price      NUMERIC(20,8),
    -- ... 其余字段对齐 DB-SCHEMA §19
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at      BIGINT,                  -- 软删除时间戳（对齐原 gorm.DeletedAt 语义）
    CONSTRAINT uk_public_name UNIQUE (public_name)
);
-- platform_model_mappings: uk_public_name UNIQUE(public_name)
-- user_model_aliases: uk_scope_alias UNIQUE(scope_type, scope_id, alias)
-- channel_model_costs: uk_channel_model UNIQUE(channel_id, upstream_model), cost_ratio NUMERIC

-- Log 加 10 列（PG ADD COLUMN 带 DEFAULT 避存量 NULL）
ALTER TABLE logs
  ADD COLUMN requested_model       VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN resolved_public_model VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN actual_upstream_model VARCHAR(255) NOT NULL DEFAULT '',
  ADD COLUMN inbound_protocol      VARCHAR(32)  NOT NULL DEFAULT '',
  ADD COLUMN upstream_protocol     VARCHAR(32)  NOT NULL DEFAULT '',
  ADD COLUMN protocol_converted    BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD COLUMN user_agent            VARCHAR(512) NOT NULL DEFAULT '',
  ADD COLUMN quota_sell            BIGINT       NOT NULL DEFAULT 0,   -- 配额额度整数（非金额）
  ADD COLUMN quota_cost            BIGINT       NOT NULL DEFAULT 0,
  ADD COLUMN quota_profit          BIGINT       NOT NULL DEFAULT 0;
CREATE INDEX idx_logs_requested_model       ON logs(requested_model);
CREATE INDEX idx_logs_resolved_public_model ON logs(resolved_public_model);
CREATE INDEX idx_logs_actual_upstream_model ON logs(actual_upstream_model);

-- Token 加 2 列（可选）
ALTER TABLE tokens
  ADD COLUMN endpoint_limits_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN endpoint_limits         JSONB;     -- 原 text 存 JSON，PG 单库改 JSONB
```

注意：
- PG 单库可直接 `ADD COLUMN ... DEFAULT`，存量行自动回填 default，无 SQLite 那套 ADD-COLUMN-only 限制。
- JSON 字段统一 JSONB（user.setting / channel.* / token.model_limits / token.endpoint_limits / task.* / log.other 等），需查询时建 GIN 索引。
- 金额/价格/倍率/成本列用 NUMERIC；配额额度（quota/quota_sell/quota_cost/quota_profit/amount）是整数单位用 BIGINT。
- 启动后 `ddl-auto=validate` 会校验 Entity 与这些表一致，不一致启动失败（早发现 schema 漂移）。

---

## 3. 缓存初始化（对齐 DATA-MODEL 缓存惯例，Java 侧实现）

4 张新表带内存/Redis 缓存。Java 侧用 `@PostConstruct` 或 `ApplicationRunner` 在应用启动、对外服务前装载；写操作（Create/Update/Delete）后失效或重载（可用 Spring Cache + Redis，或领域事件驱动失效）。

| 表 | 初始化（Java） | 缓存 key | 失效时机 |
|---|---|---|---|
| public_models | `publicModelCache.warmup()`（@PostConstruct） | `public_name → PublicModel` | 写时失效（增删改公开模型） |
| platform_model_mappings | `platformModelMappingCache.warmup()` | `public_name → upstreamName(B)` | 写时失效 |
| channel_model_costs | `channelModelCostCache.warmup()` | `[channelId][upstreamModel] → costRatio` | 写时失效（含 enabled 切换） |
| user_model_aliases | 随别名查询路径装载（可加 `userModelAliasCache`） | `(scopeType,scopeId,alias) → target` | 写时失效；优先级 user>group |

装载顺序（建议，紧随配置/选项装载之后，对齐选渠/计费链路读序 L1→L2→结算）：

```
OptionConfig 装载（现网 KV）
... 其余启动装载 ...
publicModelCache.warmup()           // A 商品目录（候选层只返 A 全集）
platformModelMappingCache.warmup()  // L2: A→B
channelModelCostCache.warmup()      // 结算: channel×B → costRatio
// userModelAliasCache: L1 C→A，与上同批装载即可
```

要点：
- `publicModelCache` 装载 = `enabled=true AND deleted_at IS NULL` 全集（对外商品目录唯一权威）。
- `channelModelCostCache` 取每 `(channel_id, upstream_model)` 最新生效且 `enabled=true` 一条（`effective_time` 预留版本化，本期取 enabled 最新）。
- 缓存失效与售价端 `model_ratio`/`model_price` 配置联动：PublicModel 写入时同步刷售价配置，保证 KV 与缓存一致。
- **DDD 落地**：缓存 warmup/失效属基础设施层关注点，领域层通过 repository 接口读，不直接碰缓存实现。

---

## 4. 迁移执行顺序总览

1. **Flyway 自动执行**：应用启动时 Flyway 按版本顺序跑 `V1 → V2 → V3` 脚本（首次空库建全部表，存量库只跑未执行的增量）。
2. **JPA 校验**：`ddl-auto=validate` 校验 Entity 与表一致，不一致启动失败。
3. **缓存装载**：4 表缓存 warmup（@PostConstruct/ApplicationRunner），紧随配置装载。
4. **KV 联动校验**：确认 PublicModel 写路径同步刷售价配置。
5. **回归**：空库全新建 + 存量库增量 两场景各跑一次，确认无 NULL、索引齐、缓存装载成功、Entity 校验通过。

---

## 5. 本轮迁移清单速查

| 变更 | 对象 | 方式 | PG 注意 |
|---|---|---|---|
| 新建表 | public_models | Flyway CREATE TABLE | BIGSERIAL 主键；uk_public_name |
| 新建表 | platform_model_mappings | Flyway CREATE TABLE | uk_public_name；B 不可见 |
| 新建表 | user_model_aliases | Flyway CREATE TABLE | uk_scope_alias 复合唯一 |
| 新建表 | channel_model_costs | Flyway CREATE TABLE | uk_channel_model 复合唯一；cost_ratio NUMERIC |
| 加 10 列 | logs | Flyway ADD COLUMN DEFAULT | 整数额度 BIGINT；3 列建索引 |
| 加 2 列（可选） | tokens | Flyway ADD COLUMN DEFAULT | endpoint_limits 用 JSONB |
| 缓存 | 4 表缓存 warmup | 启动装载 + 写时失效 | 与售价配置联动 |
| 分区（可选） | logs | Flyway 按时间范围分区 | 高写入量表优化 |

**新增表 = 4；扩展列 = Log+10、Token+2（可选）；迁移工具 Flyway，目标 PostgreSQL 单库，JPA ddl-auto=validate。**

---

## 6. 相比原 GORM 三库版的关键变化

| 维度 | 原（GORM 三库） | 现（JPA + PG 单库） |
|---|---|---|
| 迁移工具 | GORM AutoMigrate 运行时建表 | Flyway 版本化 SQL 脚本 + JPA validate |
| 目标库 | SQLite/MySQL/PostgreSQL 兼容 | PostgreSQL 单库 |
| JSON 列 | text + common.Marshal | JSONB（可 GIN 索引） |
| 加列约束 | SQLite 只能 ADD COLUMN + 必带 default | PG ADD COLUMN DEFAULT 自动回填，无限制 |
| 外键 | 全逻辑外键 | 核心一致性域可用真实外键 |
| 金额/倍率 | int / float64 | NUMERIC / BigDecimal（精度安全） |
| 表结构逻辑 | — | **完全不变**（仅表达层变） |

---

## 7. GAP 汇总（待架构师/S8 处理，继承自 DB-SCHEMA）

1. UserModelAlias 缓存命名：原 DATA-MODEL §18 未显式命名 Init，本计划用 `userModelAliasCache` 与其他三表对称。
2. （继承 DB-SCHEMA GAP：Channel 软删除字段、SubscriptionPlan 支付商品号字段独立约束、SubscriptionOrder 枚举/索引、UserSubscription 字段 tag——S8 建表脚本时回查 DATA-MODEL 补全。）

以上均为 DATA-MODEL 表述层面待确认，未自创字段或迁移步骤。
