-- =============================================================================
-- V5: user_oauth_bindings 增列 provider_ref_id — 支撑自定义 OAuth provider 绑定（F-1025/1026/1027）
--
-- 背景：V3 的 user_oauth_bindings.provider 用字符串标识内建 provider（github/discord/oidc/linuxdo/wechat）。
--   自定义 provider（custom_oauth_providers）需要按其整数主键 provider_id 关联绑定，且 openapi 的
--   OAuthBindingView.provider_id / 解绑端点 {provider_id} 均以整数表达 provider。
--
-- 方案（对 V3 的向后兼容扩展，非破坏性）：
--   新增可空列 provider_ref_id —— 自定义 provider 绑定时存 custom_oauth_providers.id；内建 provider 绑定为 NULL。
--   provider 列继续承载内建 provider 标识串；自定义 provider 的 provider 列存固定前缀 'custom:<id>'（路由/落库可还原）。
--   既有内建绑定行不受影响（provider_ref_id 默认 NULL）。复合唯一约束沿用 V3（仍按 provider+provider_user_id / user_id+provider 去重，
--   自定义 provider 的 provider 串含其 id 故天然区分不同自定义 provider）。
--
-- 不改动 V1~V4。
-- =============================================================================
ALTER TABLE user_oauth_bindings
    ADD COLUMN IF NOT EXISTS provider_ref_id BIGINT;

-- 按自定义 provider 整数 id 聚合查绑定（管理端/本人按 provider_id 解绑定位）。
CREATE INDEX IF NOT EXISTS idx_oauth_bindings_provider_ref_id
    ON user_oauth_bindings (provider_ref_id);
