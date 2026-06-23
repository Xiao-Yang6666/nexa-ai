-- =============================================================================
-- V27: 创建 model_group_access 表 — 私有模型组的访问授权关系
--
-- 背景：access_policy=PRIVATE 的模型组只对显式授权的主体可见可用。本表承载授权记录。授权粒度由
--   subject_type 区分：USER（覆盖该用户名下所有令牌）/ TOKEN（仅授权指定令牌，便于按 key 售卖）。
--   中继链路解析「某令牌可访问哪些私有组」时，按 令牌级 + 其归属用户级 两个维度查授权并合并。
--
-- 列名/类型与 JPA 实体 ModelGroupAccessJpaEntity 的 @Column 对齐。授权关系无软删除（撤销即物理删除）。
--
-- 关键约束：
--   (model_group_id, subject_type, subject_id) 三元唯一约束 uk_mga_subject，兜底重复授权。
--   idx_mga_subject(subject_type, subject_id) 加速「按主体查可访问组」（中继热路径）。
--   idx_mga_group(model_group_id) 加速「查某组的授权清单」（管理端）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS model_group_access (
    id              BIGSERIAL    PRIMARY KEY,                  -- 自增主键
    model_group_id  BIGINT       NOT NULL,                     -- 被授权访问的模型组主键
    subject_type    VARCHAR(10)  NOT NULL,                     -- USER/TOKEN
    subject_id      BIGINT       NOT NULL,                     -- 主体主键（userId 或 tokenId）
    created_time    BIGINT,                                    -- 创建时间 epoch 秒
    CONSTRAINT uk_mga_subject UNIQUE (model_group_id, subject_type, subject_id)
);

-- 按主体查可访问组（中继热路径，对齐 @Index idx_mga_subject）。
CREATE INDEX IF NOT EXISTS idx_mga_subject ON model_group_access (subject_type, subject_id);
-- 按模型组查授权清单（管理端，对齐 @Index idx_mga_group）。
CREATE INDEX IF NOT EXISTS idx_mga_group ON model_group_access (model_group_id);
