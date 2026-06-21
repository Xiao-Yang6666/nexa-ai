-- =============================================================================
-- V14: 创建 tasks 表 — 异步任务中心（视频/音乐/MJ，F-2001~F-2011）
-- 对齐 DB-SCHEMA §9 Task 实体 + FL-asynctask（状态机/CAS/退款/超时）
-- =============================================================================

CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(191),
    platform VARCHAR(30),
    user_id INTEGER,
    "group" VARCHAR(50),
    channel_id INTEGER,
    quota BIGINT,
    action VARCHAR(40),
    status VARCHAR(20),
    fail_reason TEXT,
    submit_time BIGINT,
    start_time BIGINT,
    finish_time BIGINT,
    progress VARCHAR(20),
    properties JSONB,
    data JSONB,
    private_data JSONB,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX idx_tasks_task_id ON tasks (task_id);
CREATE INDEX idx_tasks_platform ON tasks (platform);
CREATE INDEX idx_tasks_user_id ON tasks (user_id);
CREATE INDEX idx_tasks_channel_id ON tasks (channel_id);
CREATE INDEX idx_tasks_action ON tasks (action);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_submit_time ON tasks (submit_time);
CREATE INDEX idx_tasks_start_time ON tasks (start_time);
CREATE INDEX idx_tasks_finish_time ON tasks (finish_time);

COMMENT ON TABLE tasks IS '异步任务中心（视频/音乐/MJ）— 状态机/CAS/退款/超时（DB-SCHEMA §9）';
COMMENT ON COLUMN tasks.task_id IS '上游任务 ID';
COMMENT ON COLUMN tasks.platform IS 'TaskPlatform 枚举：midjourney/suno/kling/jimeng/vidu/hailuo/doubao_video';
COMMENT ON COLUMN tasks.status IS 'TaskStatus 枚举：NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN';
COMMENT ON COLUMN tasks.progress IS '进度，如 0%/100%（超时扫描判定）';
COMMENT ON COLUMN tasks.properties IS '公开元信息 JSONB（客户可见）';
COMMENT ON COLUMN tasks.data IS '产物 JSONB（客户可见，已脱敏 ResultURL/ImageUrl 等）';
COMMENT ON COLUMN tasks.private_data IS '隐私 JSONB（含上游 Key + billing_context，禁止下发用户）';
