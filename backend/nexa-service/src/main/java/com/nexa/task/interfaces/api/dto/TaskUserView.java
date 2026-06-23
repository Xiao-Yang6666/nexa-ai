package com.nexa.task.interfaces.api.dto;

import com.nexa.task.domain.model.Task;

/**
 * 任务客户视图 DTO（接口层出参，对齐 openapi TaskUserView，F-2003/F-2007）。
 *
 * <p><b>客户视图铁律</b>（产品铁律 + PRD AT-2 C5）：<b>Omit channel_id</b>、<b>不含 privateData</b>
 * （上游 Key 等隐私）、不含成本/利润/供应商。仅下发客户可见字段：状态/进度/产物 data/公开 properties。
 * privateData 在领域聚合中保留但本视图<b>不映射</b>该字段（杜绝泄露）。</p>
 *
 * @param id         任务主键
 * @param taskId     上游任务 ID
 * @param platform   平台（wire 格式）
 * @param action     动作
 * @param status     状态（wire 格式）
 * @param failReason 失败原因（可空）
 * @param submitTime 提交时间 epoch 秒（可空）
 * @param startTime  开始时间 epoch 秒（可空）
 * @param finishTime 完成时间 epoch 秒（可空）
 * @param progress   进度
 * @param properties 公开元信息 JSON（可空，客户可见）
 * @param data       产物 JSON（可空，客户可见，已脱敏 ResultURL/ImageUrl）
 * @param createdAt  创建时间 epoch 秒
 * @param updatedAt  更新时间 epoch 秒
 */
public record TaskUserView(
        Long id,
        String taskId,
        String platform,
        String action,
        String status,
        String failReason,
        Long submitTime,
        Long startTime,
        Long finishTime,
        String progress,
        String properties,
        String data,
        Long createdAt,
        Long updatedAt) {

    /**
     * 从领域聚合映射为客户视图（剔除 channel_id / user_id / privateData，安全脱敏）。
     *
     * @param t 任务聚合
     * @return 客户视图 DTO
     */
    public static TaskUserView from(Task t) {
        return new TaskUserView(
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
                // 仅 SUCCESS 暴露产物（PRD AT-4 GetResultURL：非 SUCCESS 不展示）；privateData 绝不映射。
                t.status() != null && t.status().isSuccess() ? t.data() : null,
                t.createdAt(),
                t.updatedAt());
    }
}
