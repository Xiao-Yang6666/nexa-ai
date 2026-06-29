package com.nexa.domain.relay.vo;

/**
 * 视频任务归属与终态校验结果（RL-5 vp_own + vp_done 产出，不可变值对象）。
 *
 * <p>由应用层查 task 后填充，携带 task_id/user_id/status/channel_type/content_url。
 * 领域服务 {@link com.nexa.domain.relay.service.VideoProxyPolicy} 判定后产出本 VO。</p>
 *
 * @param taskId      任务 ID
 * @param userId      任务归属 user_id
 * @param status      任务状态
 * @param channelType 渠道类型
 * @param contentUrl  视频内容 URL（可能是 data: / http(s)）
 */
public record VideoTaskOwnership(
        String taskId,
        long userId,
        VideoTaskStatus status,
        int channelType,
        String contentUrl
) {

    public boolean isSuccess() {
        return status != null && status.isSuccess();
    }
}
