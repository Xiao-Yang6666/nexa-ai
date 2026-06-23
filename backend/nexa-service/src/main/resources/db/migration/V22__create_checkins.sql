-- =============================================================================
-- V22: 创建签到模块表 — checkins（签到流水）+ checkin_settings（签到奖励配置单例）
-- 对齐 CheckinJpaEntity / CheckinSettingJpaEntity（com.nexa.growth）
-- 业务出处：签到/每日奖励（check-in），昨晚 S8 w-checkin 切片实体落库但漏建表，补齐覆盖缺口。
-- =============================================================================

-- 签到流水：每个用户每天最多一条（user_id + checkin_date 唯一）
CREATE TABLE checkins (
    id             BIGSERIAL PRIMARY KEY,
    user_id        INTEGER NOT NULL,
    checkin_date   VARCHAR(10) NOT NULL,
    quota_awarded  BIGINT NOT NULL,
    created_at     BIGINT
);

-- 复合唯一：一用户一天只一条签到（与 JPA idx_user_checkin_date 对齐）
CREATE UNIQUE INDEX idx_user_checkin_date ON checkins (user_id, checkin_date);
CREATE INDEX idx_checkins_user_id ON checkins (user_id);

COMMENT ON TABLE checkins IS '签到流水：每用户每天最多一条';
COMMENT ON COLUMN checkins.user_id IS '=User.id，逻辑外键';
COMMENT ON COLUMN checkins.checkin_date IS '签到日期 YYYY-MM-DD';
COMMENT ON COLUMN checkins.quota_awarded IS '本次签到发放的额度（随机区间内）';
COMMENT ON COLUMN checkins.created_at IS '创建 epoch 时间戳';

-- 签到奖励配置：全局单例行（固定主键 id=1）
CREATE TABLE checkin_settings (
    id         BIGINT PRIMARY KEY,
    enabled    BOOLEAN NOT NULL,
    min_quota  BIGINT NOT NULL,
    max_quota  BIGINT NOT NULL
);

COMMENT ON TABLE checkin_settings IS '签到奖励配置（全局单例行，id=1）';
COMMENT ON COLUMN checkin_settings.enabled IS '签到功能开关';
COMMENT ON COLUMN checkin_settings.min_quota IS '单次签到最小发放额度';
COMMENT ON COLUMN checkin_settings.max_quota IS '单次签到最大发放额度';

-- 初始化单例配置行（默认关闭，待管理端启用并设区间）
INSERT INTO checkin_settings (id, enabled, min_quota, max_quota)
VALUES (1, FALSE, 0, 0)
ON CONFLICT (id) DO NOTHING;
