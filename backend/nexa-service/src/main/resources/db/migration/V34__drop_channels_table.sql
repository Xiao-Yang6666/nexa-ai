-- =============================================================================
-- V34: 删除 channels 表 — 渠道域彻底下线（channel→account 体系迁移收尾）
--
-- 背景：转发引擎已 100% 迁移到供应商账号（accounts）体系，旧 channels 表 + Channel 聚合
--   /ChannelController/选渠死链已从代码删除（保留 channel_model_costs 成本表，其 channel_id
--   列语义为 account_id，本迁移不动）。本脚本仅 DROP channels 表及其索引，回收存储。
--
-- 保守策略：不重命名 channel_model_costs.channel_id（避免牵连 JPA 实体/Repository 映射），
--   该列语义澄清留作独立任务。channel_model_costs 表本身不受影响。
--
-- 幂等：IF EXISTS 保证可重复执行；channels 无被其它表 FK 引用（V7 注释确认），直接 DROP。
-- =============================================================================
DROP INDEX IF EXISTS idx_channels_name;
DROP INDEX IF EXISTS idx_channels_tag;
DROP TABLE IF EXISTS channels CASCADE;
