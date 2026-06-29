-- =============================================================================
-- V12: 创建 platform_model_mappings 表 — 超管底仓映射 A→B（全局，客户不可见）
-- 对齐 DB-SCHEMA §20 PlatformModelMapping 实体 + COMPAT-LAYER-ARCHITECTURE §4.1
-- =============================================================================

CREATE TABLE platform_model_mappings (
    id BIGSERIAL PRIMARY KEY,
    public_name VARCHAR(255) NOT NULL,
    upstream_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    remark VARCHAR(255),
    created_time BIGINT NOT NULL,
    updated_time BIGINT NOT NULL,
    deleted_at BIGINT
);

CREATE UNIQUE INDEX uk_public_name ON platform_model_mappings (public_name) WHERE deleted_at IS NULL;
CREATE INDEX idx_platform_model_mappings_deleted_at ON platform_model_mappings (deleted_at);

COMMENT ON TABLE platform_model_mappings IS '超管底仓映射 A→B（全局，客户不可见，L2 层）';
COMMENT ON COLUMN platform_model_mappings.public_name IS 'A 平台公开名，唯一键（1对1）';
COMMENT ON COLUMN platform_model_mappings.upstream_name IS 'B 真实上游模型名（客户绝不可见）';
COMMENT ON COLUMN platform_model_mappings.enabled IS 'false=该映射停用（A 回落直通或 404）';
COMMENT ON COLUMN platform_model_mappings.deleted_at IS '软删除 epoch 时间戳';
