-- =============================================================================
-- V31: account 域增强 — group 统一为字符串 + 账号级倍率（供应商账号化重构 / round7 阶段2）
--
-- 【背景】round7 阶段2 让供应商账号(account)与渠道(channel)在「字符串 group」下汇合选渠。
-- 现有 account_groups.group_id 是 BIGINT 悬空外键（指向不存在的 groups 实体表），而
-- channel/user/abilities 的 group 都是 varchar(64) 字符串约定。本迁移把 account 侧也统一
-- 为字符串 group，地基对齐后阶段3/4 才能按同一 group 维度选 account 取凭证。
--
-- 【account_groups 重建】group_id(BIGINT) → group(varchar 64)。因复合主键含该列、且 account
-- 是本轮新功能（设计文档：数据不迁、以本地为主、新库从零跑 Flyway），直接 drop 重建最干净。
--
-- 【accounts 补字段】rate_multiplier：账号级售价倍率（阶段5 计费 sell = A倍率×group倍率×account倍率）。
-- session_window 等 sub2api 字段 nexa 暂无使用场景，不预加（避免过度设计，真有需求再补）。
-- =============================================================================

-- 1) account_groups：group_id(BIGINT) 改为 group(varchar 64)，对齐 channel/user/abilities 字符串约定。
DROP TABLE IF EXISTS account_groups;

CREATE TABLE account_groups (
    account_id               BIGINT          NOT NULL,                    -- 账号 id
    "group"                  VARCHAR(64)     NOT NULL,                    -- 分组（字符串，对齐 channels."group"）
    priority                 INTEGER         NOT NULL DEFAULT 50,         -- 组内优先级
    PRIMARY KEY (account_id, "group")
);

-- 按 group 反查账号的索引（选 account 时按 group 过滤）。
CREATE INDEX IF NOT EXISTS idx_account_groups_group ON account_groups ("group");

-- 2) accounts 补账号级倍率（>=0，默认 1.0 不打折/不加价；阶段5 计费用）。
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS rate_multiplier NUMERIC(10,4) NOT NULL DEFAULT 1.0;

COMMENT ON COLUMN accounts.rate_multiplier IS '账号级售价倍率（>=0，默认 1.0；售价=A倍率×group倍率×account倍率）';
