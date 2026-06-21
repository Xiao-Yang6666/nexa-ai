-- =============================================================================
-- V11: 创建 logs 表 — Relay 用量/操作日志（F-3026~F-3037 + F-4046）
-- 对齐 DB-SCHEMA §5 Log 实体 + 10 新列（requested_model/resolved_public_model/actual_upstream_model/
-- inbound_protocol/upstream_protocol/protocol_converted/user_agent/quota_sell/quota_cost/quota_profit）
-- =============================================================================

CREATE TABLE logs (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER,
    created_at BIGINT NOT NULL,
    type INTEGER NOT NULL,
    content TEXT,
    username VARCHAR(255) DEFAULT '' NOT NULL,
    token_name VARCHAR(255) DEFAULT '' NOT NULL,
    model_name VARCHAR(255) DEFAULT '' NOT NULL,
    quota INTEGER DEFAULT 0 NOT NULL,
    prompt_tokens INTEGER DEFAULT 0 NOT NULL,
    completion_tokens INTEGER DEFAULT 0 NOT NULL,
    use_time INTEGER DEFAULT 0 NOT NULL,
    is_stream BOOLEAN,
    channel INTEGER,
    channel_name VARCHAR(255),
    token_id INTEGER DEFAULT 0 NOT NULL,
    "group" VARCHAR(64),
    ip VARCHAR(255) DEFAULT '' NOT NULL,
    request_id VARCHAR(64),
    upstream_request_id VARCHAR(128),
    other JSONB,
    -- 本轮新增 10 列（三段模型 + 协议 + UA + 双价记账）
    requested_model VARCHAR(255) DEFAULT '' NOT NULL,
    resolved_public_model VARCHAR(255) DEFAULT '' NOT NULL,
    actual_upstream_model VARCHAR(255) DEFAULT '' NOT NULL,
    inbound_protocol VARCHAR(32) DEFAULT '' NOT NULL,
    upstream_protocol VARCHAR(32) DEFAULT '' NOT NULL,
    protocol_converted BOOLEAN DEFAULT FALSE NOT NULL,
    user_agent VARCHAR(512) DEFAULT '' NOT NULL,
    quota_sell INTEGER DEFAULT 0 NOT NULL,
    quota_cost INTEGER DEFAULT 0 NOT NULL,
    quota_profit INTEGER DEFAULT 0 NOT NULL
);

-- 复合索引
CREATE INDEX idx_created_at_id ON logs (created_at, id);
CREATE INDEX idx_user_id_id ON logs (user_id, id);

-- 普通索引
CREATE INDEX idx_logs_username ON logs (username);
CREATE INDEX idx_logs_token_name ON logs (token_name);
CREATE INDEX idx_logs_model_name ON logs (model_name);
CREATE INDEX idx_logs_channel ON logs (channel);
CREATE INDEX idx_logs_token_id ON logs (token_id);
CREATE INDEX idx_logs_group ON logs ("group");
CREATE INDEX idx_logs_ip ON logs (ip);
CREATE INDEX idx_logs_requested_model ON logs (requested_model);
CREATE INDEX idx_logs_resolved_public_model ON logs (resolved_public_model);
CREATE INDEX idx_logs_actual_upstream_model ON logs (actual_upstream_model);

COMMENT ON TABLE logs IS 'Relay 用量/操作日志（三段模型 C→A→B + 协议转换标记 + 双价记账）';
COMMENT ON COLUMN logs.model_name IS '= requested_model(C), 保留现网报表语义';
COMMENT ON COLUMN logs.requested_model IS 'C 客户输入名（客户可见）';
COMMENT ON COLUMN logs.resolved_public_model IS 'A 平台公开名（客户可见）';
COMMENT ON COLUMN logs.actual_upstream_model IS 'B 真实上游名（仅 admin/root）';
COMMENT ON COLUMN logs.quota_sell IS '本笔售价（客户可见），= BasePriceRatio(A)×GroupRatio×tokens';
COMMENT ON COLUMN logs.quota_cost IS '本笔成本（仅 admin/root），= CostRatio(channel,B)×tokens 不乘折扣';
COMMENT ON COLUMN logs.quota_profit IS '本笔利润=sell-cost（仅 admin/root），可为负=亏损告警';
