-- =============================================================================
-- V4: 创建 custom_oauth_providers 表 — 账号域 W1 自定义 OAuth provider 配置（F-1023/1024）
-- 对齐 openapi.yaml CustomOAuthProviderView + /api/custom-oauth-provider* 端点（RootAuth）。
--
-- 背景：DB-SCHEMA §13 把 CustomOAuthProvider 标为「现网表，非本批 PRD 范围」，但本切片
--   F-1023/1024 要求实现自定义 provider 的 discovery 获取与 CRUD，且 user_oauth_bindings.provider_id
--   逻辑外键指向本表。故在此显式建表承载该配置（root 端管理的第三方 OIDC/OAuth2 接入点 + 凭证）。
--
-- 安全：client_secret 为敏感凭证，落库但客户/管理视图均不回显（CustomOAuthProviderView 不含该字段）。
-- 列名 snake_case；类型与 JPA @Column 声明对齐，保证 ddl-auto=validate 通过。不改动 V1~V3。
-- =============================================================================
CREATE TABLE IF NOT EXISTS custom_oauth_providers (
    id                       BIGSERIAL    PRIMARY KEY,          -- 自增主键（provider_id，绑定表逻辑外键引用）
    name                     VARCHAR(64)  NOT NULL,             -- provider 展示名/路由标识（唯一，回调路径段 /api/oauth/{name}）
    client_id                VARCHAR(256) NOT NULL,             -- OAuth client id
    client_secret            VARCHAR(512) NOT NULL,             -- OAuth client secret（敏感，绝不下发）
    authorization_endpoint   VARCHAR(512) NOT NULL,             -- 授权端点
    token_endpoint           VARCHAR(512) NOT NULL,             -- token 端点
    userinfo_endpoint        VARCHAR(512) NOT NULL,             -- userinfo 端点
    scopes                   TEXT,                              -- 申请 scope（空格分隔串，可空）
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,-- 是否启用（停用后不再接受该 provider 登录）
    created_at               BIGINT,                            -- 创建时间 epoch 秒
    updated_at               BIGINT                             -- 最近更新时间 epoch 秒
);

-- provider name 全局唯一（作为回调路径段路由 key，不能重名）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_custom_oauth_provider_name
    ON custom_oauth_providers (name);
