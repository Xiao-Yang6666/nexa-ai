package com.nexa.task.application;

/**
 * 任务提交命令（应用层入参，F-2001/F-2005/F-2007/F-2008）。
 *
 * <p>由 relay 提交链路填充：上游回报的 task_id、平台/动作、归属用户、选定渠道，以及计费上下文
 * （预扣额度、是否按次计费、计费来源）。无业务逻辑，纯数据载体。</p>
 *
 * @param taskId           上游任务 ID
 * @param platform         任务平台（wire 格式，如 midjourney/suno/kling）
 * @param userId           归属用户 id
 * @param group            用户分组（可空）
 * @param channelId        选定渠道 id（可空）
 * @param action           任务动作（如 IMAGINE/MUSIC/video）
 * @param preConsumedQuota 预扣配额（整数额度单位，退款依据）
 * @param perCallBilling   是否按次计费（true=终态跳过差额结算）
 * @param billingSource    计费来源（subscription/wallet）
 */
public record SubmitTaskCommand(
        String taskId,
        String platform,
        Integer userId,
        String group,
        Integer channelId,
        String action,
        long preConsumedQuota,
        boolean perCallBilling,
        String billingSource
) {
}
