-- =============================================================================
-- V29: 创建 accounts + account_groups 表 — 供应商账号化（Slice A / r6a-account）
-- 把「供应商」建成结构化「账号」聚合（DDD 充血模型）。
--
-- 字段对齐 com.nexa.account.provider.domain.model.Account 与 sub2api accounts 权威参考表，
-- 时间字段统一 epoch 秒 BIGINT（对齐 channel 现网习惯，区别于参考表的 timestamptz）；
-- status 以 varchar 字符串码持久化（active/disabled/rate_limited，对齐参考表 varchar(20)）。
--
-- 安全：credentials 为敏感凭证 JSON，落库（jsonb）但管理视图 AccountView 绝不下发。
-- 索引：platform / status / priority（列表过滤与调度筛选）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS accounts (
    id                       BIGSERIAL       PRIMARY KEY,                 -- 自增主键
    name                     VARCHAR(100)    NOT NULL,                    -- 账号名
    platform                 VARCHAR(50)     NOT NULL,                    -- 供应商平台（openai/anthropic 等）
    type                     VARCHAR(20)     NOT NULL,                    -- 账号类型（api_key/oauth 等）
    credentials              JSONB           NOT NULL DEFAULT '{}',       -- 凭证 JSON（敏感，绝不下发视图）
    concurrency              INTEGER         NOT NULL DEFAULT 3,          -- 并发度（>=1）
    priority                 INTEGER         NOT NULL DEFAULT 50,         -- 优先级（>=0）
    status                   VARCHAR(20)     NOT NULL DEFAULT 'active',   -- 状态：active/disabled/rate_limited
    rate_limited_at          BIGINT,                                      -- 进入限流时刻 epoch 秒
    rate_limit_reset_at      BIGINT,                                      -- 限流恢复时刻 epoch 秒
    overload_until           BIGINT,                                      -- 过载冷却截止 epoch 秒
    expires_at               BIGINT,                                      -- 账号过期时刻 epoch 秒
    auto_pause_on_expired    BOOLEAN         NOT NULL DEFAULT TRUE,       -- 过期是否自动暂停
    created_at               BIGINT,                                      -- 创建时间 epoch 秒
    updated_at               BIGINT                                       -- 更新时间 epoch 秒
);

-- platform/status/priority 索引（对齐 @Index：列表过滤 + 调度筛选）。
CREATE INDEX IF NOT EXISTS idx_accounts_platform ON accounts (platform);
CREATE INDEX IF NOT EXISTS idx_accounts_status   ON accounts (status);
CREATE INDEX IF NOT EXISTS idx_accounts_priority ON accounts (priority);

-- account_groups：账号-分组关联（复合主键 account_id+group_id，组内优先级 priority）。
CREATE TABLE IF NOT EXISTS account_groups (
    account_id               BIGINT          NOT NULL,                    -- 账号 id
    group_id                 BIGINT          NOT NULL,                    -- 分组 id
    priority                 INTEGER         NOT NULL DEFAULT 50,         -- 组内优先级
    PRIMARY KEY (account_id, group_id)
);

-- 按 group_id 反查账号的索引。
CREATE INDEX IF NOT EXISTS idx_account_groups_group ON account_groups (group_id);
