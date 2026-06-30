package com.nexa.interfaces.api.task.dto;

import com.nexa.domain.task.model.Task;

/**
 * 任务管理视图 DTO（接口层出参，对齐 openapi TaskAdminVO = TaskUserVO + channel_id/user_id，F-2004）。
 *
 * <p>管理端（AdminAuth）专用，相比 {@link TaskUserVO} 额外含 {@code channelId}/{@code userId}
 * （管理需按渠道/用户排障）。<b>仍不含 privateData</b>（上游 Key 即便管理端也不经列表 API 明文下发，
 * Key 仅内部流程使用）。</p>
 *
 * @param user        客户视图基础字段（复用，避免重复定义）
 * @param channelId   渠道 id（管理可见）
 * @param userId      归属用户 id（管理可见）
 */
public record TaskAdminVO(
        TaskUserVO user,
        Integer channelId,
        Integer userId) {

    /**
     * 从领域聚合映射为管理视图。
     *
     * @param t 任务聚合
     * @return 管理视图 DTO
     */
    public static TaskAdminVO from(Task t) {
        // 管理视图复用客户视图字段，但管理端可见全量产物（不限 SUCCESS）；故单独取 data。
        TaskUserVO base = new TaskUserVO(
                t.id(),
                t.taskId(),
                t.platform() == null ? null : t.platform().toWire(),
                t.action(),
                t.status() == null ? null : t.status().toWire(),
                t.failReason(),
                t.submitTime(),
                t.startTime(),
                t.finishTime(),
                t.progress(),
                t.properties(),
                t.data(),
                t.createdAt(),
                t.updatedAt());
        return new TaskAdminVO(base, t.channelId(), t.userId());
    }
}
