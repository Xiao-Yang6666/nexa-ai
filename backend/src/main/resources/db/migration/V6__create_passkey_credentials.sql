-- =============================================================================
-- V6: 创建 passkey_credentials 表 — 账号域 W1 Passkey/WebAuthn（F-1028~1032）
-- 对齐 DB-SCHEMA §16 PasskeyCredential 与 JPA 实体 PasskeyCredentialJpaEntity。
--
-- 单 passkey 语义：user_id 唯一索引 → 每用户至多一条 passkey（重新注册即替换）。
-- credential_id 全局唯一（登录断言据此定位用户公钥）。
-- public_key / transports 用 text（base64，DB-SCHEMA §16 PG 注意事项）。
--
-- 列名 snake_case；类型与 @Column 声明对齐，保证 JPA ddl-auto=validate 通过。
-- 递增版本号 V6，不改动 V1~V5。
-- =============================================================================
CREATE TABLE IF NOT EXISTS passkey_credentials (
    id               BIGSERIAL      PRIMARY KEY,            -- 自增主键（GenerationType.IDENTITY）
    user_id          BIGINT         NOT NULL,               -- 凭据归属用户 id（逻辑外键 → users.id，唯一）
    credential_id    VARCHAR(512)   NOT NULL,               -- WebAuthn credential id（base64url，唯一）
    public_key       TEXT           NOT NULL,               -- authenticator 公钥（base64，敏感，绝不下发视图）
    attestation_type VARCHAR(255),                          -- attestation 类型（none/packed/...，可空）
    aaguid           VARCHAR(512),                           -- authenticator 型号标识（base64，可空）
    sign_count       BIGINT         DEFAULT 0,               -- 签名计数器（单调递增，克隆检测；原 uint32）
    clone_warning    BOOLEAN,                                -- 克隆告警标记（计数器回退时置真）
    user_present     BOOLEAN,                                -- authenticator flag: 用户在场（UP）
    user_verified    BOOLEAN,                                -- authenticator flag: 用户已验证（UV）
    backup_eligible  BOOLEAN,                                -- authenticator flag: 可备份资格（BE）
    backup_state     BOOLEAN,                                -- authenticator flag: 已备份状态（BS）
    transports       TEXT,                                   -- transports（base64 串，可空）
    attachment       VARCHAR(32)                             -- 连接形态（platform/cross-platform，可空）
);

-- 每用户至多一条 passkey（DB-SCHEMA §16 idx_passkey_credentials_user_id unique）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_passkey_user_id
    ON passkey_credentials (user_id);
-- credential_id 全局唯一（DB-SCHEMA §16 idx_passkey_credentials_credential_id unique）。
CREATE UNIQUE INDEX IF NOT EXISTS ux_passkey_credential_id
    ON passkey_credentials (credential_id);
