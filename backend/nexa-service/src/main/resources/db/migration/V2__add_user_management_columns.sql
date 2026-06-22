-- =============================================================================
-- V2: users 表补列 — 账号域 W1 用户管理（F-1008~1014）
-- 新增管理端字段：group（分组 F-1013）、remark（备注 F-1014）、setting（个人设置 F-1014）。
-- 对齐 DB-SCHEMA §1 User 与 JPA 实体 UserJpaEntity；保证 ddl-auto=validate 通过。
-- group 为 PG 保留字，列名以双引号转义。
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS "group"  VARCHAR(64)  DEFAULT 'default';  -- 用户分组（F-1013）
ALTER TABLE users ADD COLUMN IF NOT EXISTS remark   VARCHAR(255);                     -- 管理员备注（F-1014，仅管理端可见）
ALTER TABLE users ADD COLUMN IF NOT EXISTS setting  TEXT;                             -- 个人设置 JSON（F-1014）
