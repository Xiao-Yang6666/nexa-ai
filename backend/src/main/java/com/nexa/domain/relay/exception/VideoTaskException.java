package com.nexa.domain.relay.exception;

import com.nexa.domain.kernel.HttpAwareDomainException;

/**
 * 视频任务相关异常（RL-5 视频代理）。
 *
 * <p>承载 F-4046 视频内容代理的归属/终态/SSRF 三类拒绝场景，对应 openapi
 * /v1/videos/{task_id}/content 的 403/404 响应。</p>
 */
public class VideoTaskException extends HttpAwareDomainException {

    public VideoTaskException(String code, int httpStatus, String message) {
        super(code, httpStatus, message);
    }

    /** task 不存在 / 非本人 → 404（不区分两者，避免泄露任务存在性，PRD RL-5 §6 验收）。 */
    public static VideoTaskException notFoundOrNotOwner(String taskId) {
        return new VideoTaskException("VIDEO_TASK_NOT_FOUND", 404,
                "video task not found or not owned: " + taskId);
    }

    /** task 未完成（Status≠SUCCESS）→ 404（含 IN_PROGRESS/FAILURE 等）。 */
    public static VideoTaskException notSuccess(String taskId, String currentStatus) {
        return new VideoTaskException("VIDEO_TASK_NOT_FINISHED", 404,
                "video task not finished, current status: " + currentStatus + ", task_id: " + taskId);
    }

    /** SSRF 校验未过 → 403。 */
    public static VideoTaskException ssrfBlocked() {
        return new VideoTaskException("VIDEO_URL_BLOCKED_BY_SSRF", 403,
                "upstream video url blocked by ssrf policy");
    }

    /** 渠道类型不支持视频代理。 */
    public static VideoTaskException unsupportedChannelType(int channelType) {
        return new VideoTaskException("VIDEO_CHANNEL_UNSUPPORTED", 400,
                "channel type does not support video proxy: " + channelType);
    }
}
