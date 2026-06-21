-- =============================================================================
-- V25: 创建 abilities 表 — 选渠路由子系统 (group × model → channel)
-- 对齐 DB-SCHEMA §4 Ability 实体 + CH-2 优先级+权重选渠。
--
-- 背景：channels.models 以逗号分隔字符串挂在 channel 行上，CH-2 选渠需要按
--   (group, model, enabled=true) 快速筛渠道并按 priority 分层 + weight 抽签。
--   abilities 表是 channels 的「group×model → channel」反向索引：一个渠道
--   (支持 N 个模型、归 1 个组) 展开为 N 行 abilities，选渠时直接走复合索引。
--   渠道 CRUD 时由 ChannelRepositoryImpl 维护（fan-out / fan-in）。
--
-- 安全：ability 仅路由使用，不暴露给客户端；不存敏感信息。
-- =============================================================================
CREATE TABLE IF NOT EXISTS abilities (
    "group"     VARCHAR(64)     NOT NULL,                   -- 分组名（保留字转义）
    model       VARCHAR(255)    NOT NULL,                   -- 模型名（真实上游模型 B，或 L2 直通的 A）
    channel_id  BIGINT          NOT NULL,                   -- 渠道 id → channels.id
    enabled     BOOLEAN         NOT NULL DEFAULT TRUE,      -- 是否启用（渠道禁用/auto_ban 时置 false）
    priority    BIGINT          NOT NULL DEFAULT 0,         -- 优先级（数值越大越优先）
    weight      INTEGER         NOT NULL DEFAULT 0,         -- 权重（同优先级层内加权随机）
    tag         VARCHAR(255),                               -- 渠道 tag（冗余，便于按 tag 批量启停）
    PRIMARY KEY ("group", model, channel_id)
);

-- 选渠主查询索引：按 (group, model, enabled) 过滤；priority/weight 用于分层+抽签内存计算即可。
CREATE INDEX IF NOT EXISTS idx_abilities_group_model ON abilities ("group", model, enabled);
CREATE INDEX IF NOT EXISTS idx_abilities_channel     ON abilities (channel_id);
CREATE INDEX IF NOT EXISTS idx_abilities_tag         ON abilities (tag);

-- V7 channels 已有 channels(id, "group", models, priority, weight, tag, status, auto_ban)。
-- abilities 初始化为空（渠道 CRUD 时 fan-out 填充；存量渠道可通过重新保存/重建刷新）。
-- 对现有 channels 做一次回填（按逗号分隔 models 展开）。
INSERT INTO abilities ("group", model, channel_id, enabled, priority, weight, tag)
SELECT
    c."group",
    TRIM(regexp_split_to_table) AS model,
    c.id                      AS channel_id,
    (c.status = 1)            AS enabled,
    c.priority,
    c.weight,
    c.tag
FROM channels c,
     LATERAL regexp_split_to_table(c.models, ',') AS m(regexp_split_to_table)
WHERE c.models IS NOT NULL
  AND c.models <> ''
  AND TRIM(m.regexp_split_to_table) <> ''
ON CONFLICT ("group", model, channel_id) DO NOTHING;
