-- =============================================================================
-- V8: 创建 redemptions 表 — 计费与钱包 W2 兑换码（prd-billing BL-4，F-2045）
-- 对齐 DB-SCHEMA §6 Redemption 实体 + JPA 实体 RedemptionJpaEntity + openapi
--   /api/redemption*（AdminAuth 生成/列表）、/api/user/topup（用户兑换）。
--
-- 背景：DB-SCHEMA §6 把 redemptions 标为「复用现网表」，但本工程为全新库（jdbc .../nexa），
--   兑换码生成/兑换/列表需要此表承载。故显式建表，列名/类型与 JPA @Column 对齐，
--   保证 ddl-auto=validate 通过。不改动 V1~V7。
--
-- 关键约束：key 为 char(32) 明文唯一索引（一次性兑换的并发守卫依赖唯一键）；
--   quota 为整数面额（DATA-MODEL §6 default 100）；软删除 deleted_at 配合 @SQLRestriction。
-- key 为 PG 保留字，列名加双引号转义。金额此处为内部配额单位（整数），非真实货币，故用 integer。
-- =============================================================================
CREATE TABLE IF NOT EXISTS redemptions (
    id            BIGSERIAL    PRIMARY KEY,                  -- 自增主键
    user_id       INTEGER,                                   -- 创建者用户 id（→users）
    "key"         CHAR(32),                                  -- 兑换码明文（保留字转义；唯一索引）
    status        INTEGER      NOT NULL DEFAULT 1,           -- 1=未使用 2=已使用 3=已禁用
    name          VARCHAR(255),                              -- 名称/批次标识（索引）
    quota         INTEGER      NOT NULL DEFAULT 100,         -- 面额（内部配额单位，default 100）
    created_time  BIGINT,                                    -- 创建时间 epoch 秒
    redeemed_time BIGINT,                                    -- 核销时间 epoch 秒（未核销为 NULL）
    used_user_id  INTEGER,                                   -- 核销人用户 id（→users，未核销为 NULL）
    expired_time  BIGINT,                                    -- 过期时间 epoch 秒（0=不过期）
    deleted_at    BIGINT                                     -- 软删除时间戳（配合 @SQLRestriction）
);

-- key 唯一索引（对齐 @Index idx_redemptions_key unique）；一次性兑换的并发兜底。
CREATE UNIQUE INDEX IF NOT EXISTS idx_redemptions_key ON redemptions ("key");
-- name 普通索引（对齐 @Index idx_redemptions_name）。
CREATE INDEX IF NOT EXISTS idx_redemptions_name ON redemptions (name);
-- 软删除索引（对齐 @Index idx_redemptions_deleted_at）。
CREATE INDEX IF NOT EXISTS idx_redemptions_deleted_at ON redemptions (deleted_at);
