-- V33: 渠道管理整合到供应商账号（Phase 2 - Schema Migration）
-- 目标：account 表添加路由字段，重建 abilities 表为账号路由索引

-- 1. 为 accounts 表添加渠道路由字段
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS model_mapping JSONB;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS weight INTEGER DEFAULT 0 CHECK (weight >= 0);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS tag VARCHAR(255);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS auto_ban BOOLEAN DEFAULT false;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS response_time INTEGER;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS test_time BIGINT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS balance NUMERIC(30,6) DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS used_quota NUMERIC(30,6) DEFAULT 0;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS models TEXT;

COMMENT ON COLUMN accounts.model_mapping IS '模型映射 JSON (A→B)，如 {"gpt-4":"gpt-4-turbo"}';
COMMENT ON COLUMN accounts.weight IS '路由权重 (>=0)';
COMMENT ON COLUMN accounts.tag IS '标签（批量操作用）';
COMMENT ON COLUMN accounts.auto_ban IS '自动封禁标志';
COMMENT ON COLUMN accounts.response_time IS '上次测试响应时间（毫秒）';
COMMENT ON COLUMN accounts.test_time IS '上次测试时间 epoch 秒';
COMMENT ON COLUMN accounts.balance IS '账户余额 USD';
COMMENT ON COLUMN accounts.used_quota IS '已用配额';
COMMENT ON COLUMN accounts.models IS '支持的模型列表（逗号分隔）';

-- 2. 为 tag 字段创建索引（批量操作优化）
CREATE INDEX IF NOT EXISTS idx_accounts_tag ON accounts(tag) WHERE tag IS NOT NULL;

-- 3. 重建 abilities 表为账号路由索引（删除旧的 channel-based abilities）
DROP TABLE IF EXISTS abilities CASCADE;

CREATE TABLE abilities (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    "group" VARCHAR(255) NOT NULL,
    models TEXT,
    tag VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

COMMENT ON TABLE abilities IS '账号路由能力索引（account_id × group × models）';
COMMENT ON COLUMN abilities.account_id IS '逻辑外键 accounts.id';
COMMENT ON COLUMN abilities."group" IS '分组名';
COMMENT ON COLUMN abilities.models IS '支持的模型列表（逗号分隔）';
COMMENT ON COLUMN abilities.tag IS '账号标签';
COMMENT ON COLUMN abilities.status IS '账号状态（ACTIVE/DISABLED/RATE_LIMITED）';

-- 4. 创建 abilities 路由查询索引
CREATE INDEX idx_abilities_group_models ON abilities("group", models);
CREATE INDEX idx_abilities_account_status ON abilities(account_id, status);
CREATE INDEX idx_abilities_tag ON abilities(tag) WHERE tag IS NOT NULL;
