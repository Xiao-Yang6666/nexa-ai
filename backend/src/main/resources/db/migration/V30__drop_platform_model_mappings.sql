-- =============================================================================
-- V30: 删除 platform_model_mappings 表 — 全局底仓映射 A→B 废弃（渠道级映射重构 / round7）
--
-- A→B 映射下沉为渠道级（channels.model_mapping，JSON {A:B}，V7 已建列），由
-- RelayForwardUseCase 选渠后用选中渠道的 model_mapping 解析真实上游名 B。
-- 全局唯一 A→B 约束（V12 的 uk_public_name）随之取消：同一对外名 A 可被不同上游
-- 渠道映射到各自的真实名 B（多渠道供货，对齐 sub2api 蓝图）。
--
-- 对应代码：删除 com.nexa.model / com.nexa.relay 两套 PlatformModelMapping 全套
-- （聚合/仓储/Controller/DTO/UseCase），TwoLayerModelResolver 的 L2 lookup 改恒等 null。
-- =============================================================================

DROP TABLE IF EXISTS platform_model_mappings;
