-- =============================================================================
-- V15: 渠道亲和缓存与规则配置 — 选渠中间件 W2（F-2029~F-2037 持久化部分）
-- 对齐 PRD CH-4 / API-ENDPOINTS 5.4 / FUNCTION-LIST F-2029~F-2034。
--
-- 版本号说明：原 V9 与 token 域 V9__create_tokens.sql 撞号（Flyway 版本必须唯一，
-- 双 V9 会让应用启动直接崩）。本迁移属选渠中间件 W2 片，推到 V15（当前最大 V14 之后），
-- 保持迁移历史单调递增、版本唯一。表名/列名不变，JPA 实体映射不受影响。
--
-- 本迁移落地三张表：
--   1) affinity_rules        亲和规则配置（F-2031；含内置 codex/claude 规则种子）
--   2) affinity_settings     亲和缓存全局策略（F-2031；单行配置，id=1 单例）
--   3) affinity_cache        会话键→渠道粘连缓存（F-2029/F-2032/F-2033）
--
-- 规则与策略持久化原因：与 com.nexa.channel/com.nexa.account 同构（运行时动态 CRUD，不重启），
-- 比现网内存单例 + YAML 更可观测、可审计。
--
-- 缓存表持久化的取舍：方便 F-2032 全部/按规则清空、F-2033 用量统计的实现；
-- 性能上 hit_count 写入与每次命中续期都走 DB（W2 阶段先正确，后续可叠加 Caffeine 一级缓存）。
--
-- key 列保留字考虑：本批无 PG 保留字列名冲突（rule_name/key_fingerprint/using_group/channel_id 均非保留字）。
-- 不改动 V1~V8。
-- =============================================================================

-- 1) 亲和规则配置 ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS affinity_rules (
    id                       BIGSERIAL       PRIMARY KEY,                 -- 自增主键
    name                     VARCHAR(64)     NOT NULL UNIQUE,              -- 规则名（唯一键，CH-4 配置维度）
    enabled                  BOOLEAN         NOT NULL DEFAULT TRUE,        -- 是否启用
    model_regex              TEXT            NOT NULL DEFAULT '',          -- model 命中正则
    path_regex               TEXT            NOT NULL DEFAULT '',          -- path 命中正则
    key_sources              JSONB           NOT NULL DEFAULT '[]'::jsonb, -- 会话键来源数组：[{"type":"gjson","path":"prompt_cache_key"}]
    pass_headers             JSONB           NOT NULL DEFAULT '{}'::jsonb, -- F-2030 header 透传模板
    skip_retry_on_failure    BOOLEAN         NOT NULL DEFAULT FALSE,       -- F-2034 命中失败是否跳重试
    ttl_seconds              BIGINT          NOT NULL DEFAULT 0,           -- 缓存 TTL 秒（0=用 settings 默认）
    built_in                 BOOLEAN         NOT NULL DEFAULT FALSE,       -- 是否内置规则（不可删，仅可禁用）
    created_time             BIGINT,                                       -- 创建时间 epoch 秒
    updated_time             BIGINT                                        -- 更新时间 epoch 秒
);

CREATE INDEX IF NOT EXISTS idx_affinity_rules_enabled ON affinity_rules (enabled);

-- 内置规则种子（FC-068 codex/claude 默认配置；存在则不重复插入）。
INSERT INTO affinity_rules (name, enabled, model_regex, path_regex, key_sources, pass_headers,
                            skip_retry_on_failure, ttl_seconds, built_in, created_time, updated_time)
VALUES
('codex', TRUE, '^gpt-.*', '^/v1/responses$',
 '[{"type":"gjson","path":"prompt_cache_key"}]'::jsonb,
 '{"OpenAI-Beta":"responses=experimental","X-Stainless-Helper-Method":"stream"}'::jsonb,
 TRUE, 0, TRUE,
 EXTRACT(EPOCH FROM NOW())::BIGINT, EXTRACT(EPOCH FROM NOW())::BIGINT),
('claude', TRUE, '^claude-.*', '^/v1/messages$',
 '[{"type":"gjson","path":"metadata.user_id"}]'::jsonb,
 '{"anthropic-version":"2023-06-01","anthropic-beta":"","x-app":"cli"}'::jsonb,
 TRUE, 0, TRUE,
 EXTRACT(EPOCH FROM NOW())::BIGINT, EXTRACT(EPOCH FROM NOW())::BIGINT)
ON CONFLICT (name) DO NOTHING;

-- 2) 亲和缓存全局策略（单例） -------------------------------------------------
CREATE TABLE IF NOT EXISTS affinity_settings (
    id                       INTEGER         PRIMARY KEY DEFAULT 1,        -- 强制单例（CHECK id=1）
    enabled                  BOOLEAN         NOT NULL DEFAULT TRUE,        -- 总开关
    switch_on_success        BOOLEAN         NOT NULL DEFAULT TRUE,        -- 仅成功才回写
    max_entries              INTEGER         NOT NULL DEFAULT 100000,      -- 缓存最大条目数（>=1）
    default_ttl_seconds      BIGINT          NOT NULL DEFAULT 3600,        -- 默认 TTL 秒（>=1）
    updated_time             BIGINT,                                       -- 更新时间 epoch 秒
    CONSTRAINT chk_affinity_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_affinity_settings_max_entries CHECK (max_entries >= 1),
    CONSTRAINT chk_affinity_settings_ttl CHECK (default_ttl_seconds >= 1)
);

INSERT INTO affinity_settings (id, enabled, switch_on_success, max_entries, default_ttl_seconds, updated_time)
VALUES (1, TRUE, TRUE, 100000, 3600, EXTRACT(EPOCH FROM NOW())::BIGINT)
ON CONFLICT (id) DO NOTHING;

-- 3) 亲和缓存（会话键→渠道映射） --------------------------------------------
CREATE TABLE IF NOT EXISTS affinity_cache (
    id                       BIGSERIAL       PRIMARY KEY,                 -- 自增主键
    rule_name                VARCHAR(64)     NOT NULL,                    -- 命中规则名（与 affinity_rules.name 对齐，不强外键避免规则删除时缓存清理顺序耦合）
    key_fingerprint          VARCHAR(64)     NOT NULL,                    -- 原始会话键的 SHA-256 前 16 字节 hex（32 字符）；不存明文避免 PII 落库
    using_group              VARCHAR(64),                                 -- 当前 token 使用分组（可空——auto 不同分组各自维护）
    channel_id               BIGINT          NOT NULL,                    -- 粘连渠道 id
    hit_count                BIGINT          NOT NULL DEFAULT 0,          -- 命中次数（F-2033 用量统计）
    last_hit_at              BIGINT          NOT NULL,                    -- 最近命中时刻 epoch 秒
    expires_at               BIGINT          NOT NULL                     -- 过期时刻 epoch 秒（now>=expires_at 即过期）
);

-- 复合唯一键：(rule_name, key_fingerprint, using_group)。using_group 可空，PG 唯一索引对 NULL 不去重，
-- 故用表达式索引 + COALESCE 兼容空值。
CREATE UNIQUE INDEX IF NOT EXISTS uk_affinity_cache_triplet
    ON affinity_cache (rule_name, key_fingerprint, COALESCE(using_group, ''));

CREATE INDEX IF NOT EXISTS idx_affinity_cache_rule       ON affinity_cache (rule_name);
CREATE INDEX IF NOT EXISTS idx_affinity_cache_expires_at ON affinity_cache (expires_at);
