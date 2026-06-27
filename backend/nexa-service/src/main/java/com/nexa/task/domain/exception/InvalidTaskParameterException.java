package com.nexa.task.domain.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 任务参数非法异常（F-2001/F-2005/F-2007/F-2008 参数校验失败 → 400）。
 *
 * <p>任务创建/更新时参数格式非法（task_id/platform/action 缺失、状态机转换非法）时抛出。</p>
 */
public class InvalidTaskParameterException extends HttpAwareDomainException {

    public InvalidTaskParameterException(String message) {
        super("INVALID_TASK_PARAMETER", 400, message);
    }

    /**
     * 必填参数缺失。
     *
     * @param paramName 参数名
     * @return 异常
     */
    public static InvalidTaskParameterException required(String paramName) {
        return new InvalidTaskParameterException(paramName + " is required");
    }

    /**
     * 状态机非法转换（from → to 不合法，PRD AT-1 §状态机规则）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 异常
     */
    public static InvalidTaskParameterException illegalTransition(String from, String to) {
        return new InvalidTaskParameterException(
                "illegal task status transition from " + from + " to " + to);
    }
}
