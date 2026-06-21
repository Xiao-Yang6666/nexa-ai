-- =============================================================================
-- V9: 创建 tokens 表 — 令牌域 W2 API 令牌管理（F-3001~F-3012）
-- 对齐 DB-SCHEMA §2 Token 实体 + openapi.yaml /api/token* 端点（sessionAuth/tokenReadAuth）。
--
-- 背景：DB-SCHEMA §2 把 tokens 标为「复用现网表（+2 可选新列）」，但本工程为全新库
--   （jdbc .../nexa），W2 令牌 CRUD/status_only/批量删/搜索/取明文 key/用量/模型限制/
--   IP 白名单/分组/跨组重试/端点级减法约束（F-3001~F-3012）需要 tokens 表承载。故在此
--   显式建表，列名/类型与 DB-SCHEMA §2 及 JPA @Column 对齐，保证 ddl-auto=validate 通过。
--   不改动 V1~V8。
--
-- 安全：key 为令牌明文凭证，落库但客户视图 TokenUserView 默认脱敏（MaskTokenKey）；仅受控
--   端点（/api/token/{id}/key、/api/token/keys/batch，限本人令牌）才下发明文。
-- 配额（remain_quota/used_quota）为整数额度单位，用 INTEGER（DB-SCHEMA §2 明确配额为整数）。
-- model_limits/endpoint_limits 存 JSON，用 JSONB；allow_ips 为按换行切分的文本。
-- key、group 为 PG 保留字，列名加双引号转义。
-- =============================================================================
CREATE TABLE IF NOT EXISTS tokens (
    id                       BIGSERIAL       PRIMARY KEY,                      -- 自增主键
    user_id                  INTEGER,                                          -- 归属用户（索引，→users）
    "key"                    VARCHAR(128)    UNIQUE,                            -- 令牌明文凭证（敏感；唯一索引；保留字转义）
    status                   INTEGER         NOT NULL DEFAULT 1,                -- 状态：1=启用 2=禁用
    name                     VARCHAR(255),                                     -- 令牌名（索引，≤50）
    created_time             BIGINT,                                           -- 创建时间 epoch 秒
    accessed_time            BIGINT,                                           -- 最近访问时间 epoch 秒
    expired_time             BIGINT          NOT NULL DEFAULT -1,              -- 过期时间 epoch 秒，-1=永不过期
    remain_quota             INTEGER         NOT NULL DEFAULT 0,               -- 剩余配额（整数额度单位）
    unlimited_quota          BOOLEAN         NOT NULL DEFAULT FALSE,           -- 是否无限额度
    model_limits_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,           -- 是否启用模型限制（减法约束开关）
    model_limits             JSONB,                                            -- 允许模型列表（JSON 串；减法约束）
    allow_ips                TEXT            NOT NULL DEFAULT '',              -- IP 白名单（按换行切分，空=不限）
    used_quota               INTEGER         NOT NULL DEFAULT 0,               -- 已用配额（整数额度单位）
    "group"                  VARCHAR(255)    NOT NULL DEFAULT '',              -- 调用分组（保留字转义）
    cross_group_retry        BOOLEAN         NOT NULL DEFAULT FALSE,           -- 跨组重试开关（group=auto 时生效）
    endpoint_limits_enabled  BOOLEAN         NOT NULL DEFAULT FALSE,           -- 是否启用端点级减法约束（本轮新增）
    endpoint_limits          JSONB,                                            -- 端点级减法约束（JSON 串；入站协议集，本轮新增）
    deleted_at               BIGINT                                            -- 软删除时间戳（配合 @SQLRestriction）
);

-- 索引：唯一 key 已由列约束建立；user_id/name/deleted_at 普通索引（对齐 DB-SCHEMA §2 @Index）。
CREATE INDEX IF NOT EXISTS idx_tokens_user_id    ON tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_name       ON tokens (name);
CREATE INDEX IF NOT EXISTS idx_tokens_deleted_at ON tokens (deleted_at);
