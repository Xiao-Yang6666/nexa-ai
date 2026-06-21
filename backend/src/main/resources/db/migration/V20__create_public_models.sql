-- =============================================================================
-- V20: 创建 public_models 表 — 对外模型商品目录（A 分级目录，客户可见，L0/商品层）
-- 对齐 DB-SCHEMA §19 PublicModel 实体 + PublicModelJpaEntity
-- 业务出处：F-6001 对外模型A分级目录（旗舰/增强/经济三档），COMPAT-BILLING-DECISIONS
-- 规则：一个公开名 A 一条记录；品质档拆独立记录分别定价（quality_tier=full/max/air）；
--       对外全集 = enabled=true AND deleted_at IS NULL 的 public_name 集；售价对客户恒定。
-- =============================================================================

CREATE TABLE public_models (
    id              BIGSERIAL PRIMARY KEY,
    public_name     VARCHAR(255) NOT NULL,
    quality_tier    VARCHAR(32) DEFAULT 'full',
    base_price_ratio NUMERIC DEFAULT 0,
    use_price       BOOLEAN DEFAULT FALSE,
    base_price      NUMERIC DEFAULT 0,
    enabled         BOOLEAN DEFAULT TRUE,
    display_name    VARCHAR(255) DEFAULT '',
    sort_order      INTEGER DEFAULT 0,
    description     VARCHAR(1024) DEFAULT '',
    created_time    BIGINT,
    updated_time    BIGINT,
    deleted_at      BIGINT
);

-- 唯一约束：公开名 A 唯一（软删除行不占用，与 JPA uk_public_name 对齐）
CREATE UNIQUE INDEX uk_public_models_public_name ON public_models (public_name) WHERE deleted_at IS NULL;
-- 品质档索引（前端按档位分组展示）
CREATE INDEX idx_public_models_quality_tier ON public_models (quality_tier);
CREATE INDEX idx_public_models_deleted_at ON public_models (deleted_at);

COMMENT ON TABLE public_models IS '对外模型商品目录（A 分级目录，客户可见，商品层唯一权威）';
COMMENT ON COLUMN public_models.public_name IS 'A 平台公开名，唯一键（客户可见）';
COMMENT ON COLUMN public_models.quality_tier IS '品质档 full/max/air/自定义，纯展示分组';
COMMENT ON COLUMN public_models.base_price_ratio IS '基准售价倍率(=model_ratio)，BigDecimal 精度安全';
COMMENT ON COLUMN public_models.use_price IS 'true=按次固定价（取 base_price）';
COMMENT ON COLUMN public_models.base_price IS 'use_price=true 时的固定单价';
COMMENT ON COLUMN public_models.enabled IS '上下架开关；false=客户不可见';
COMMENT ON COLUMN public_models.deleted_at IS '软删除 epoch 时间戳';
