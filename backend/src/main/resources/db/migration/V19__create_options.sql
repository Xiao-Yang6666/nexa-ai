-- =============================================================================
-- V19: 创建 options 表 — 运营与运维 W5（F-4015~F-4037 全站系统配置 KV 底座）
-- 对齐 DB-SCHEMA §18 Option 实体 + openapi.yaml /api/option*（RootAuth）。
--
-- 背景：全站选项（OAuth 开关 / 主题 / 限流分组 / 敏感词 / 自动分组 / 法务文案 /
--   控制台设置 / 通知设置 / 支付合规 等）以单一 KV 表承载（key→value 字符串）。
--   启动 InitOptionMap() 装载内存；运行时 PUT /api/option/ 逐键覆盖式写入。
--   本工程为全新库，options 此前未建表，W5 在此显式建表，不改动 V1~V18。
--
-- 设计说明：
--   * key 为主键，且为 PG 保留字（与 DB-SCHEMA §18 一致），列名加双引号转义。
--   * value 用 TEXT：法务文案 / 控制台 JSON / 词表 等可能很长，VARCHAR 长度上限不安全。
--   * 全局 KV 无软删除（配置删除即物理删除该键），不引入 deleted_at。
--   * F-4017 列表查询时由应用层剔除敏感键（*Token/*Secret/*Key/*secret/*api_key），
--     落库仍保留（运行时需读），脱敏只在客户视图 DTO 层做（不在 SQL）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS options (
    "key"   VARCHAR(255)   PRIMARY KEY,                    -- 配置键（保留字转义；唯一主键）
    value   TEXT                                            -- 配置值（字符串/JSON/文案，可空）
);
