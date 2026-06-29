-- =============================================================================
-- V37: 创建 balance_transactions 表（账变流水）
-- 对齐 BalanceTransactionJpaEntity（@Table balance_transactions）+ BalanceTransactionType。
-- 业务出处：user-balance-admin-recharge —— 管理员手动充值/扣费 + 账变流水居中弹窗。
--
-- 本表只记「管理操作类 + 充值到账类」账变（ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP），
-- 不含 API 消费扣减（量极大，已在 logs 表）。后台资金审计流水，无客户读路径。
--
-- 列名/类型与实体 @Column 严格对齐，保证 ddl-auto=validate 通过。
--   amount 带正负（quota 单位）；balance_after 为变动后余额快照；
--   operator_id 为执行管理员 id（自助/兑换到账可空）；created_time 为 epoch 毫秒/秒（实体 bigint）。
-- =============================================================================

CREATE TABLE balance_transactions (
    id            BIGSERIAL    PRIMARY KEY,              -- 自增主键
    user_id       BIGINT,                                -- 账变所属用户 id（→users）
    type          VARCHAR(32),                           -- 账变类型字面量（ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP）
    amount        BIGINT       DEFAULT 0,                -- 变动额（带正负，quota 单位）
    balance_after BIGINT       DEFAULT 0,                -- 变动后余额快照（quota）
    operator_id   BIGINT,                                -- 执行管理员 id（自助/兑换到账可空）
    remark        VARCHAR(512),                          -- 备注
    created_time  BIGINT                                 -- 创建时间 epoch
);

-- user_id 普通索引（对齐 @Index idx_balance_tx_user_id），按用户查账变流水。
CREATE INDEX idx_balance_tx_user_id ON balance_transactions (user_id);
-- created_time 普通索引（对齐 @Index idx_balance_tx_created），按时间倒序分页。
CREATE INDEX idx_balance_tx_created ON balance_transactions (created_time);

COMMENT ON TABLE balance_transactions IS '账变流水（后台资金审计，无客户读路径）';
COMMENT ON COLUMN balance_transactions.type IS '账变类型：ADMIN_CREDIT/ADMIN_DEBIT/REDEEM/TOPUP';
COMMENT ON COLUMN balance_transactions.amount IS '变动额（带正负，quota 单位）';
COMMENT ON COLUMN balance_transactions.balance_after IS '变动后余额快照（quota）';
COMMENT ON COLUMN balance_transactions.operator_id IS '执行管理员 id（自助/兑换到账可空）';
