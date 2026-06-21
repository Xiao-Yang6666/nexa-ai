-- =============================================================================
-- V10: 创建 model_metas / vendor_metas 表 — 模型元数据域 W2（F-3013~F-3021）
-- 对齐 DB-SCHEMA 模块四「模型与供应商元数据」+ openapi.yaml /api/models* /api/vendors* 端点（adminAuth）。
--
-- 背景：模块四 §4.1 模型元数据 CRUD（F-3013~F-3017）、§4.2 供应商元数据 CRUD（F-3018）、
--   模型同步（F-3019~F-3021）需要两张承载表：
--     1) vendor_metas  供应商元数据（name 唯一；模型按 vendor_id 归属）
--     2) model_metas   模型元数据（model_name 唯一；含端点/命名规则/同步标记/状态）
--   列名/类型与 JPA @Column 对齐，保证 ddl-auto=validate 通过。不改动 V1~V9。
--
-- 幂等键（PRD ML-1/ML-2）：
--   - model_metas.model_name 业务唯一（软删除前提下，仅未删行唯一 → 用部分唯一索引 uk_model_name_alive）。
--   - vendor_metas.name      业务唯一（同理 uk_vendor_name_alive）。
--   软删除（deleted_at）下「唯一」只约束存活行，删除后可同名重建（对齐 new-api uk_model_name_delete_at 语义）。
--
-- sync_official（PRD ML-2）：1=由上游官方同步维护，0=本地自建模型。同步执行（F-3019）覆盖时
--   跳过 sync_official=0 的本地模型，避免覆盖手工配置（计入 skipped_models）。
--
-- 时间戳统一 epoch 秒（BIGINT），与 tokens/channels 等表一致。
-- =============================================================================

CREATE TABLE IF NOT EXISTS vendor_metas (
    id            BIGSERIAL    PRIMARY KEY,                   -- 自增主键
    name          VARCHAR(255) NOT NULL,                      -- 供应商名（业务唯一，存活行）
    icon          TEXT,                                       -- 图标 URL / data URI（可空）
    status        INTEGER      NOT NULL DEFAULT 1,            -- 状态：1=启用 2=禁用
    created_time  BIGINT,                                     -- 创建时间 epoch 秒
    updated_time  BIGINT,                                     -- 更新时间 epoch 秒
    deleted_at    BIGINT                                      -- 软删除时间戳（配合 @SQLRestriction）
);

CREATE TABLE IF NOT EXISTS model_metas (
    id            BIGSERIAL    PRIMARY KEY,                   -- 自增主键
    model_name    VARCHAR(255) NOT NULL,                      -- 模型名（业务唯一，存活行；幂等键）
    status        INTEGER      NOT NULL DEFAULT 1,            -- 状态：1=启用 2=禁用
    description   TEXT,                                       -- 描述（可空）
    icon          TEXT,                                       -- 图标 URL / data URI（可空）
    tags          TEXT,                                       -- 标签（逗号分隔串，可空）
    vendor_id     BIGINT,                                     -- 归属供应商（→vendor_metas.id，可空）
    endpoints     TEXT,                                       -- 支持端点（逗号分隔串，可空）
    name_rule     TEXT,                                       -- 命名匹配规则（可空）
    sync_official INTEGER      NOT NULL DEFAULT 0,            -- 1=官方同步维护 0=本地自建（同步覆盖跳过本地）
    created_time  BIGINT,                                     -- 创建时间 epoch 秒
    updated_time  BIGINT,                                     -- 更新时间 epoch 秒
    deleted_at    BIGINT                                      -- 软删除时间戳（配合 @SQLRestriction）
);

-- 业务唯一索引（仅约束存活行；软删除后可同名重建，对齐 new-api uk_*_delete_at 语义）。
CREATE UNIQUE INDEX IF NOT EXISTS uk_vendor_name_alive
    ON vendor_metas (name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_model_name_alive
    ON model_metas (model_name) WHERE deleted_at IS NULL;

-- 普通索引：供应商过滤（F-3014）、软删除过滤、供应商名搜索（F-3018）。
CREATE INDEX IF NOT EXISTS idx_model_metas_vendor_id  ON model_metas (vendor_id);
CREATE INDEX IF NOT EXISTS idx_model_metas_deleted_at ON model_metas (deleted_at);
CREATE INDEX IF NOT EXISTS idx_vendor_metas_deleted_at ON vendor_metas (deleted_at);
