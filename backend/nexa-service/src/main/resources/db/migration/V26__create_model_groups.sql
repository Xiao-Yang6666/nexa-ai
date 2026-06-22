-- =============================================================================
-- V26: 创建 model_groups 表 — 灵活模型组管理（供应商模型 → 模型组 → 倍率/可用模型/访问策略）
--
-- 背景：原系统模型组与用户等级强绑定（User.group 硬编码映射倍率），无法灵活售卖。本表使模型组成为
--   独立可售卖单元：管理员把任意供应商模型纳入一个模型组，设模型组级倍率与可用模型集，并通过访问
--   策略（公开/私有/按等级自动）灵活控制可见性，不再写死在账号等级上。
--
-- 列名/类型与 JPA 实体 ModelGroupJpaEntity 的 @Column 对齐，保证 ddl-auto=validate 通过。
--
-- 关键约束：
--   code varchar(64) NOT NULL，唯一索引 uk_model_group_code(code)（全局唯一业务标识，中继按 code 选组；
--     仅 [a-z0-9_-]，在领域层校验，DB 唯一索引为兜底）。
--   base_price_ratio numeric(18,6) NOT NULL，模型组基础倍率（≥0，复用计费域 Ratio 值对象语义）。
--   models 存 JSON 字符串数组（如 ["gpt-4o","claude-3-opus"]），用 JSONB。
--   access_policy varchar(20) NOT NULL，枚举 PUBLIC/PRIVATE/AUTO_LEVEL（落库大写字面量，索引）。
--   status int NOT NULL（1=启用 2=禁用，与现网渠道/令牌状态整数语义兼容）。
--   软删除 deleted_at（epoch 秒），配合 @SQLRestriction("deleted_at IS NULL")。
-- =============================================================================
CREATE TABLE IF NOT EXISTS model_groups (
    id                BIGSERIAL      PRIMARY KEY,                      -- 自增主键
    name              VARCHAR(64)    NOT NULL,                         -- 展示名
    code              VARCHAR(64)    NOT NULL,                         -- 唯一编码（中继按此选组）
    base_price_ratio  NUMERIC(18, 6) NOT NULL DEFAULT 1.0,            -- 模型组基础倍率（≥0）
    models            JSONB,                                          -- 可用模型 JSON 字符串数组
    access_policy     VARCHAR(20)    NOT NULL DEFAULT 'PUBLIC',       -- PUBLIC/PRIVATE/AUTO_LEVEL
    status            INT            NOT NULL DEFAULT 1,              -- 1=启用 2=禁用
    description       VARCHAR(255),                                   -- 描述（可空）
    created_time      BIGINT,                                         -- 创建时间 epoch 秒
    updated_time      BIGINT,                                         -- 更新时间 epoch 秒
    deleted_at        BIGINT                                          -- 软删除时间戳
);

-- code 唯一索引（对齐 @Index uk_model_group_code unique）；编码冲突的 DB 兜底。
CREATE UNIQUE INDEX IF NOT EXISTS uk_model_group_code ON model_groups (code);
-- status 索引（对齐 @Index idx_model_groups_status）；按启用态筛选提速。
CREATE INDEX IF NOT EXISTS idx_model_groups_status ON model_groups (status);
-- access_policy 索引（对齐 @Index idx_model_groups_access_policy）；按策略查公开组提速。
CREATE INDEX IF NOT EXISTS idx_model_groups_access_policy ON model_groups (access_policy);
-- 软删除索引（对齐 @Index idx_model_groups_deleted_at）。
CREATE INDEX IF NOT EXISTS idx_model_groups_deleted_at ON model_groups (deleted_at);
