-- =============================================================================
-- V16: 创建 prefill_groups 表 — 预填分组 W2（PRD 模块十五 §14，F-2012~F-2015）
-- 注：原为 V10，与 model_metas/vendor_metas 迁移撞版本号（并行 wave 双 subagent 各自取了 V10），
--   按 Flyway 版本唯一性约束改为 V16（V11~V15 已被 logs/映射/别名/任务/亲和占用）。
-- 对齐 DB-SCHEMA §17 PrefillGroup 实体 + JPA 实体 PrefillGroupJpaEntity + openapi
--   /api/prefill_group*（AdminAuth：创建/更新/列表/软删除）。
--
-- 背景：DB-SCHEMA §17 把 prefill_groups 标为「复用现网表」，但本工程为全新库（jdbc .../nexa），
--   预填分组的创建/更新/下拉填充/软删除需要此表承载。故显式建表，列名/类型与 JPA @Column 对齐，
--   保证 ddl-auto=validate 通过。递增版本号 V16，不改动 V1~V15。
--
-- 关键约束：
--   name varchar(64) NOT NULL，唯一索引 uk_prefill_name(name)（软删除条件唯一——已软删行
--     被 @SQLRestriction 排除，但 DB 唯一索引为全局；业务名称冲突按 type 维度在应用层显式校验，
--     DB 唯一索引为兜底）。
--   type varchar(32) NOT NULL，普通索引；枚举 model/tag/endpoint（落库小写字面量）。
--   items 存 JSON 字符串数组（如 ["gpt-4o","gpt-3.5-turbo"]），用 JSONB（DB-SCHEMA §17）。
--   软删除 deleted_at（epoch 秒），配合 @SQLRestriction("deleted_at IS NULL")。
-- =============================================================================
CREATE TABLE IF NOT EXISTS prefill_groups (
    id            BIGSERIAL    PRIMARY KEY,                  -- 自增主键
    name          VARCHAR(64)  NOT NULL,                     -- 分组名称（唯一索引 uk_prefill_name）
    type          VARCHAR(32)  NOT NULL,                     -- 类型：model/tag/endpoint（索引）
    items         JSONB,                                     -- 条目 JSON 字符串数组（["gpt-4o",...]）
    description   VARCHAR(255),                              -- 描述（可空）
    created_time  BIGINT,                                    -- 创建时间 epoch 秒
    updated_time  BIGINT,                                    -- 更新时间 epoch 秒
    deleted_at    BIGINT                                     -- 软删除时间戳（配合 @SQLRestriction）
);

-- name 唯一索引（对齐 @Index uk_prefill_name unique）；名称冲突的 DB 兜底。
CREATE UNIQUE INDEX IF NOT EXISTS uk_prefill_name ON prefill_groups (name);
-- type 普通索引（对齐 @Index idx_prefill_groups_type）；按类型下拉填充查询提速。
CREATE INDEX IF NOT EXISTS idx_prefill_groups_type ON prefill_groups (type);
-- 软删除索引（对齐 @Index idx_prefill_groups_deleted_at）。
CREATE INDEX IF NOT EXISTS idx_prefill_groups_deleted_at ON prefill_groups (deleted_at);
