-- =============================================================================
-- V7: 创建 channels 表 — 渠道域 W2 上游渠道管理（F-2016~F-2028）
-- 对齐 DB-SCHEMA §3 Channel 实体 + openapi.yaml /api/channel* 端点（AdminAuth）。
--
-- 背景：DB-SCHEMA §3 把 channels 标为「复用现网表」，但本工程为全新库（jdbc .../nexa），
--   W2 渠道 CRUD/搜索/批量/测试/余额/按 tag 启停/上游模型探测/Ollama 管理（F-2016~F-2028）
--   需要 channels 表承载。故在此显式建表，列名/类型与 DB-SCHEMA §3 及 JPA @Column 对齐，
--   保证 ddl-auto=validate 通过。不改动 V1~V6。
--
-- 安全：key 为敏感凭证，落库但管理视图 ChannelAdminView 脱敏/不全量下发（接口层 DTO 不带原始 key）。
-- balance 为金额，用 NUMERIC(30,6)（领域 BigDecimal，禁裸 float）；JSON 字段用 JSONB。
-- 注意：name、tag 普通索引（对齐 @Index）；DB-SCHEMA §3 明确不补软删除列。
-- key、group 为 PG 保留字，列名加双引号转义。
-- =============================================================================
CREATE TABLE IF NOT EXISTS channels (
    id                       BIGSERIAL       PRIMARY KEY,                 -- 自增主键
    type                     INTEGER         NOT NULL DEFAULT 0,          -- 渠道类型（0=未知/通用，对齐 ChannelType）
    "key"                    TEXT            NOT NULL DEFAULT '',         -- 上游凭证（敏感，绝不下发；保留字转义）
    status                   INTEGER         NOT NULL DEFAULT 1,          -- 状态：1=启用 2=手动禁用 3=自动禁用
    name                     VARCHAR(255),                                -- 渠道名（索引）
    weight                   INTEGER         NOT NULL DEFAULT 0,          -- 权重（uint 语义，>=0）
    base_url                 TEXT            NOT NULL DEFAULT '',         -- 上游 BaseURL
    models                   TEXT            NOT NULL DEFAULT '',         -- 支持模型（逗号分隔串）
    "group"                  VARCHAR(64)     NOT NULL DEFAULT 'default',  -- 分组（保留字转义）
    priority                 BIGINT          NOT NULL DEFAULT 0,          -- 优先级
    auto_ban                 INTEGER         NOT NULL DEFAULT 1,          -- 是否自动禁用（1=是 0=否）
    balance                  NUMERIC(30,6)   NOT NULL DEFAULT 0,          -- 余额（USD，BigDecimal，禁裸 float）
    used_quota               BIGINT          NOT NULL DEFAULT 0,          -- 已用配额
    response_time            INTEGER,                                     -- 最近测试响应耗时 ms
    test_time                BIGINT,                                      -- 最近测试时间 epoch 秒
    model_mapping            JSONB,                                       -- 模型映射（A→B，JSON）
    status_code_mapping      VARCHAR(1024)   NOT NULL DEFAULT '',         -- 状态码映射（JSON ≤1024）
    tag                      VARCHAR(255),                                -- 标签（索引；按 tag 批量启停）
    setting                  JSONB,                                       -- 渠道附加设置（含 param/header 覆写，JSON）
    channel_info             JSONB,                                       -- 多 Key 信息（is_multi_key/size/mode/polling_index）
    created_time             BIGINT                                       -- 创建时间 epoch 秒
);

-- name、tag 普通索引（对齐 DB-SCHEMA §3 @Index(idx_channels_name / idx_channels_tag)）。
CREATE INDEX IF NOT EXISTS idx_channels_name ON channels (name);
CREATE INDEX IF NOT EXISTS idx_channels_tag  ON channels (tag);
