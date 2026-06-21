-- =============================================================================
-- V23: 创建 top_ups 表 + 修正 redemptions.key 列类型（CHAR(32) → VARCHAR(32)）
-- 对齐 DATA-MODEL §7 TopUp 实体 + TopUpJpaEntity（@Table top_ups）
-- 业务出处：prd-billing BL-1「在线充值入账（支付下单 → 回调 → 幂等入账）」
--
-- 背景：W2-billing 切片漏建 top_ups 表（实体/仓储已就绪，迁移缺失），补建于此。
--   列名/类型与 TopUpJpaEntity @Column 对齐，保证 ddl-auto=validate 通过。
--   trade_no 为商户订单号（幂等键），唯一索引保证回调幂等（BL-1 pay_idem）。
--
-- 可见性：本表为后台资金流水，无客户读路径。money（真实货币支付金额）为内部财务字段，
--   仅 admin/root 管理端可见，不出现在面向客户的 DTO。
--
-- 另：redemptions.key 在 V8 建为 CHAR(32)（定长右补空格），与实体 columnDefinition
--   varchar(32) 不一致，会触发 ddl-auto=validate 报错。此处 ALTER TYPE 对齐为 VARCHAR(32)。
--   key 为 PG 保留字，列名加双引号转义。不改动已 applied 的 V8（Flyway checksum）。
-- =============================================================================

CREATE TABLE top_ups (
    id               BIGSERIAL      PRIMARY KEY,                          -- 自增主键
    user_id          INTEGER,                                            -- 充值用户 id（→users）
    amount           BIGINT         DEFAULT 0,                           -- 充值额度（内部配额单位，入账目标）
    money            NUMERIC(20,6)  DEFAULT 0,                           -- 支付金额（真实货币，内部财务字段，客户不可见）
    trade_no         VARCHAR(255),                                       -- 商户订单号（幂等键，唯一索引）
    payment_method   VARCHAR(64),                                        -- 支付方式（stripe/creem/...）
    payment_provider VARCHAR(64),                                        -- 支付渠道（epay/stripe/...）
    status           VARCHAR(32)    DEFAULT 'pending',                   -- 状态（pending/success）
    create_time      BIGINT,                                             -- 创建时间 epoch 秒
    complete_time    BIGINT                                              -- 完成时间 epoch 秒（未完成为 NULL）
);

-- trade_no 唯一索引（对齐 @Index idx_top_ups_trade_no unique）；回调幂等定位的并发兜底。
CREATE UNIQUE INDEX idx_top_ups_trade_no ON top_ups (trade_no);
-- user_id 普通索引（对齐 @Index idx_top_ups_user_id），按用户查流水。
CREATE INDEX idx_top_ups_user_id ON top_ups (user_id);
-- status 普通索引（对齐 @Index idx_top_ups_status），按状态筛选 pending/success。
CREATE INDEX idx_top_ups_status ON top_ups (status);

COMMENT ON TABLE top_ups IS '充值订单流水（后台资金流，客户绝不可见 money 字段）';
COMMENT ON COLUMN top_ups.amount IS '充值额度（内部配额单位，入账目标）';
COMMENT ON COLUMN top_ups.money IS '支付金额（真实货币，内部财务字段，仅 admin/root 可见）';
COMMENT ON COLUMN top_ups.trade_no IS '商户订单号（幂等键，唯一索引）';
COMMENT ON COLUMN top_ups.status IS 'pending=待支付 success=已支付入账';

-- redemptions.key: CHAR(32) → VARCHAR(32)，对齐 RedemptionJpaEntity columnDefinition。
ALTER TABLE redemptions ALTER COLUMN "key" TYPE VARCHAR(32);
