-- =============================================================================
-- V1: 创建 users 表（账号域垂直切片）
-- 对齐 DB-SCHEMA §1 User 与 JPA 实体 UserJpaEntity 的列子集（注册/登录所需 + 关键索引）。
-- 列名 snake_case；字段类型与 @Column 声明对齐，保证 JPA ddl-auto=validate 通过。
-- 后续账号 wave（OAuth 绑定 github_id 等、setting JSONB）按需追加迁移脚本。
-- =============================================================================
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL      PRIMARY KEY,                 -- 自增主键（GenerationType.IDENTITY）
    username        VARCHAR(20),                                -- 用户名（唯一索引见下）
    password        VARCHAR(100)   NOT NULL,                    -- 密码哈希（BCrypt 串，放宽至 100）
    display_name    VARCHAR(20),                                -- 展示名（本切片可空）
    role            INTEGER        DEFAULT 1,                   -- 1=common,10=admin,100=root
    status          INTEGER        DEFAULT 1,                   -- 1=启用，其它=禁用
    email           VARCHAR(50),                                -- 邮箱（可空）
    quota           BIGINT         DEFAULT 0,                   -- 当前额度
    used_quota      BIGINT         DEFAULT 0,                   -- 已用额度
    request_count   INTEGER        DEFAULT 0,                   -- 请求计数
    aff_code        VARCHAR(32),                                -- 个人邀请码（唯一索引见下）
    aff_count       INTEGER        DEFAULT 0,                   -- 邀请人数
    aff_quota       BIGINT         DEFAULT 0,                   -- 邀请额度
    aff_history     BIGINT         DEFAULT 0,                   -- 历史累计邀请额度
    inviter_id      BIGINT,                                     -- 邀请人 id（无则 0）
    created_at      BIGINT,                                     -- 创建时间 epoch 秒
    last_login_at   BIGINT         DEFAULT 0,                   -- 最近登录时间 epoch 秒
    deleted_at      BIGINT                                      -- 软删除时间戳（NULL=未删，配合 @SQLRestriction）
);

-- 用户名唯一（注册查重 + 并发兜底，PRD AC-1 R9）。
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username  ON users (username);
-- 邀请码全局唯一（DB-SCHEMA §1 aff_code uniqueIndex）。
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_aff_code  ON users (aff_code);
-- 邮箱查询索引（找回/发码等场景）。
CREATE INDEX IF NOT EXISTS idx_users_email       ON users (email);
-- 邀请人聚合查询索引。
CREATE INDEX IF NOT EXISTS idx_users_inviter_id  ON users (inviter_id);
-- 软删除过滤索引。
CREATE INDEX IF NOT EXISTS idx_users_deleted_at  ON users (deleted_at);
