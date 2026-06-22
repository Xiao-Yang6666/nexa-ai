-- =============================================================================
-- V13: 创建 user_model_aliases 表 — 客户层自助映射 C→A（分组/用户级）
-- 对齐 DB-SCHEMA §21 UserModelAlias 实体 + COMPAT-LAYER-ARCHITECTURE §4.2
-- =============================================================================

CREATE TABLE user_model_aliases (
    id BIGSERIAL PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    scope_id VARCHAR(64) NOT NULL,
    alias VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_time BIGINT NOT NULL,
    updated_time BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE UNIQUE INDEX uk_scope_alias ON user_model_aliases (scope_type, scope_id, alias) WHERE deleted_at IS NULL;
CREATE INDEX idx_user_model_aliases_deleted_at ON user_model_aliases (deleted_at);

COMMENT ON TABLE user_model_aliases IS '客户层自助映射 C→A（分组/用户级，L1 层）';
COMMENT ON COLUMN user_model_aliases.scope_type IS '枚举 user/group';
COMMENT ON COLUMN user_model_aliases.scope_id IS 'user→user_id 字符串化；group→分组名';
COMMENT ON COLUMN user_model_aliases.alias IS 'C 客户别名';
COMMENT ON COLUMN user_model_aliases.target IS 'A 目标平台公开名（不强制白名单）';
COMMENT ON COLUMN user_model_aliases.deleted_at IS '软删除 epoch 时间戳';
