-- =============================================================================
-- V21: 创建 channel_model_costs 表 — 供应商成本倍率（渠道×真实模型 B，超管层，客户绝不可见）
-- 对齐 DB-SCHEMA §22 ChannelModelCost 实体 + ChannelModelCostJpaEntity
-- 业务出处：F-6006 供应商成本配置，COMPAT-BILLING-DECISIONS（结算阶段链路第 17 步取值）
-- B 不可见三道闸之一：成本与上游模型 B 仅存于此表，无客户读接口、客户视图 DTO 不含本表字段。
-- 规则：一渠道对一 B 只一条生效成本；多供应商同一 A→B 下每渠道各一行；
--       取值时机=结算阶段，主键(实际选中 channel_id, L2 后 B) 精确取一行。
-- =============================================================================

CREATE TABLE channel_model_costs (
    id                    BIGSERIAL PRIMARY KEY,
    channel_id            INTEGER,
    upstream_model        VARCHAR(255) NOT NULL,
    cost_ratio            NUMERIC DEFAULT 0,
    completion_cost_ratio NUMERIC DEFAULT 0,
    enabled               BOOLEAN DEFAULT TRUE,
    effective_time        BIGINT DEFAULT 0,
    source_unit_price     NUMERIC DEFAULT 0,
    remark                VARCHAR(255) DEFAULT '',
    created_time          BIGINT,
    updated_time          BIGINT,
    deleted_at            BIGINT
);

-- 复合唯一：一渠道×一 B 只一条生效成本（软删除行不占用，与 JPA uk_channel_model 对齐）
CREATE UNIQUE INDEX uk_channel_model ON channel_model_costs (channel_id, upstream_model) WHERE deleted_at IS NULL;
-- 选渠后按 channel_id 取成本行
CREATE INDEX idx_channel_model_costs_channel_id ON channel_model_costs (channel_id);
CREATE INDEX idx_channel_model_costs_deleted_at ON channel_model_costs (deleted_at);

COMMENT ON TABLE channel_model_costs IS '供应商成本倍率（渠道×真实模型 B，超管层，客户绝不可见）';
COMMENT ON COLUMN channel_model_costs.channel_id IS '=Channel.id，逻辑外键';
COMMENT ON COLUMN channel_model_costs.upstream_model IS 'B 真实上游模型名（客户绝不可见）';
COMMENT ON COLUMN channel_model_costs.cost_ratio IS '成本倍率(输入token)，口径同 model_ratio，BigDecimal';
COMMENT ON COLUMN channel_model_costs.completion_cost_ratio IS '成本补全倍率(输出token)，0=回落 cost_ratio×现网 CompletionRatio';
COMMENT ON COLUMN channel_model_costs.enabled IS 'false=视为成本缺失（记 0 + 告警）';
COMMENT ON COLUMN channel_model_costs.effective_time IS '生效 epoch 时间戳，取最新生效且 enabled 一条';
COMMENT ON COLUMN channel_model_costs.source_unit_price IS '扩展位：进货单价，本期不参与计算';
COMMENT ON COLUMN channel_model_costs.deleted_at IS '软删除 epoch 时间戳';
