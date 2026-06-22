-- =============================================================================
-- V17: 创建 telegram_bindings 表 — Telegram 登录 Bot W4（F-1051/F-1052/F-1054）
-- 承载 telegram_id ↔ 本站 user_id 绑定，对齐 JPA 实体 TelegramBindingJpaEntity。
--
-- 设计说明：Telegram 登录走独立 HMAC 校验路径（非标准 OAuth 授权码流程，见 OAuthProvider 注释），
--   故不复用 user_oauth_bindings 表，用独立绑定表承载。DB-SCHEMA §1 在 users 上有 telegram_id 索引列，
--   本切片以独立绑定表实现反查与唯一性兜底（与 OAuthBinding 独立建模同理）。
--   - ux_telegram_id：一个 Telegram 账号至多绑一个本站账号（F-1054）。
--   - ux_telegram_user_id：一个本站账号至多绑一个 Telegram（与 users.telegram_id 单列语义一致）。
--
-- 列名 snake_case；类型与 @Column 声明对齐，保证 JPA ddl-auto=validate 通过。
-- 递增版本号 V17（当前最大为 V16），不改动 V1~V16。
-- =============================================================================
CREATE TABLE IF NOT EXISTS telegram_bindings (
    id          BIGSERIAL    PRIMARY KEY,      -- 自增主键（GenerationType.IDENTITY）
    user_id     BIGINT       NOT NULL,         -- 绑定归属用户 id（逻辑外键 → users.id）
    telegram_id VARCHAR(64)  NOT NULL,         -- 绑定的 Telegram 账号 id（数字串）
    created_at  BIGINT                         -- 绑定建立时间 epoch 秒
);

-- 一个 Telegram 账号至多绑一个本站账号（F-1054 唯一性兜底）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_telegram_id
    ON telegram_bindings (telegram_id);
-- 一个本站账号至多绑一个 Telegram 账号。
CREATE UNIQUE INDEX IF NOT EXISTS ux_telegram_user_id
    ON telegram_bindings (user_id);
-- 按用户聚合查绑定（本人查询/解绑定位）。
CREATE INDEX IF NOT EXISTS idx_telegram_bindings_user_id
    ON telegram_bindings (user_id);
