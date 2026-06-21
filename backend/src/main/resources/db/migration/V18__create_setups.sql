-- =============================================================================
-- V18: 创建 setups 表 — 系统初始化标记（F-4015 状态查询 / F-4016 初始化提交）
-- 对齐 API-ENDPOINTS §9.1：POST /api/setup 成功后 Create(model.Setup) 落一行标记，
--   GET /api/setup 据 constant.Setup（= setups 表存在记录）判断是否已初始化。
--
-- 背景：现网 Go 版 model.Setup 是「系统已初始化」的单行哨兵记录。本工程用一张
--   单行表承载该哨兵 + 初始化元数据（版本/初始化时间），用于：
--     * 幂等护栏：已存在记录 → POST /api/setup 拒绝重复初始化（「系统已经初始化完成」）；
--     * 状态探测：GET /api/setup 据是否存在记录返回 status=true/false。
--   不改动 V1~V17。
--
-- 设计说明：
--   * 单行语义用固定主键 id（恒为 1）+ 应用层 INSERT ON CONFLICT 幂等保证，
--     避免并发双提交建出两行（DB 层 PK 唯一兜底）。
--   * initialized_at 记初始化完成时间（epoch 秒），便于审计「何时完成首次部署引导」。
--   * version 记初始化时的引导脚本/数据模型版本，预留升级迁移识别。
-- =============================================================================
CREATE TABLE IF NOT EXISTS setups (
    id               INTEGER       PRIMARY KEY,             -- 单行哨兵主键（恒为 1）
    version          VARCHAR(64),                           -- 初始化时的引导版本标记
    initialized_at   BIGINT        NOT NULL                 -- 初始化完成时间 epoch 秒
);
