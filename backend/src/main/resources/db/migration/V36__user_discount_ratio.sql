-- V36: 用户专属折扣（售价侧，在分组倍率之后再乘）
-- 售价 = A基准 × 分组倍率 × discount_ratio × tokens；只作用于售价，不进成本。
-- 缺省 1.0=不打折；须 >= 0（0 合法=免费）。
ALTER TABLE users ADD COLUMN IF NOT EXISTS discount_ratio numeric(10,4) NOT NULL DEFAULT 1.0;
