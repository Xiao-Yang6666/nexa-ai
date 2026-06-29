-- =============================================================================
-- V24__fix_column_types.sql
-- 目的：消除 Hibernate ddl-auto=validate 下的列类型不匹配，使 schema 与 JPA 实体映射对齐。
--
-- 修复原则：以「实体期望类型」为准（实体是代码契约），用 ALTER COLUMN TYPE 把 DB 列改成实体期望类型。
-- 不改动 V1~V23（已 applied）；不重构、不加列、不改语义，仅对齐类型。
--
-- 全量 diff（DB information_schema.columns vs 所有 JPA @Column）后，共发现 4 处类型不匹配：
--   1) tokens.remain_quota           int4 -> int8     （实体 long）
--   2) tokens.used_quota             int4 -> int8     （实体 long）
--   3) custom_oauth_providers.scopes text -> varchar(255)（实体 String 无 columnDefinition/length，Hibernate6 默认 varchar(255)）
--   4) tasks.fail_reason             text -> varchar(255)（同上）
--
-- 其余 text 列（channels.base_url/models、affinity_rules.*_regex、logs.content、
-- model_metas.icon 等）实体均显式 columnDefinition="text"，与 DB 一致，无需改。
-- 全部目标表当前为空（0 行），ALTER 无数据转换风险；仍带 USING 子句以防万一。
-- PG 保留字列名（无）— 本批次涉及列名均非保留字，无需双引号。
-- =============================================================================

-- 1) tokens.remain_quota : int4 -> int8（实体 long，配额可能超 int 上限，应为 bigint）
ALTER TABLE tokens   ALTER COLUMN remain_quota TYPE bigint USING remain_quota::bigint;

-- 2) tokens.used_quota   : int4 -> int8
ALTER TABLE tokens   ALTER COLUMN used_quota   TYPE bigint USING used_quota::bigint;

-- 3) custom_oauth_providers.scopes : text -> varchar(255)
ALTER TABLE custom_oauth_providers ALTER COLUMN scopes TYPE varchar(255) USING scopes::varchar(255);

-- 4) tasks.fail_reason : text -> varchar(255)
ALTER TABLE tasks    ALTER COLUMN fail_reason  TYPE varchar(255) USING fail_reason::varchar(255);
