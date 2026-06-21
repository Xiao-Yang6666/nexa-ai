-- =============================================================================
-- V3: 创建 user_oauth_bindings 表 — 账号域 W1 OAuth 登录/绑定（F-1016~1020）
-- 对齐 DB-SCHEMA §11/§13 UserOAuthBinding 与 JPA 实体 UserOAuthBindingJpaEntity。
--
-- 对 DB-SCHEMA §13 的合理偏离（已在 OAuthProvider / 实体注释中说明）：
--   §13 provider_id 为整数外键 → 现网 CustomOAuthProvider 表（自定义 provider，非本批范围）。
--   本切片绑定 4 个内建 provider（github/discord/oidc/linuxdo），无 CustomOAuthProvider 行可引用，
--   故 provider 列用字符串标识（OAuthProvider.code()）落库，零外键依赖。复合唯一索引语义等价 §13。
--
-- 列名 snake_case；类型与 @Column 声明对齐，保证 JPA ddl-auto=validate 通过。
-- 不改动 V1/V2。
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_oauth_bindings (
    id               BIGSERIAL      PRIMARY KEY,            -- 自增主键（GenerationType.IDENTITY）
    user_id          BIGINT         NOT NULL,               -- 绑定归属用户 id（逻辑外键 → users.id）
    provider         VARCHAR(32)    NOT NULL,               -- provider 标识串（github/discord/oidc/linuxdo）
    provider_user_id VARCHAR(256)   NOT NULL,               -- 第三方账号在该 provider 下的唯一 id
    created_at       BIGINT                                 -- 绑定建立时间 epoch 秒
);

-- 每用户每 provider 至多一条绑定（DB-SCHEMA §13 ux_user_provider 的字符串 provider 等价）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_provider
    ON user_oauth_bindings (user_id, provider);
-- 每 provider 一第三方账号唯一（DB-SCHEMA §13 ux_provider_userid 的字符串 provider 等价）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_provider_userid
    ON user_oauth_bindings (provider, provider_user_id);
-- 按用户聚合查绑定（解绑/列出本人绑定）。
CREATE INDEX IF NOT EXISTS idx_oauth_bindings_user_id
    ON user_oauth_bindings (user_id);
