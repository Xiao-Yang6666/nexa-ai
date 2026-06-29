package com.nexa.application.task;

import com.nexa.domain.task.model.Task;

import java.util.List;

/**
 * 任务分页结果（应用层读模型，承载当前页任务 + 总数 + 分页元数据，F-2003/F-2004）。
 *
 * <p>由列表用例返回，接口层据此组装 {@code { items, total, page, page_size }} 响应（对齐 openapi
 * 的任务列表出参）。持有领域聚合列表（接口层再裁剪为 TaskUserView/TaskAdminView，privateData
 * 不下发）。</p>
 *
 * @param items    当前页任务聚合列表
 * @param total    满足条件的总条数
 * @param page     当前页码（从 1 起）
 * @param pageSize 每页条数
 */
public record TaskPage(List<Task> items, long total, int page, int pageSize) {
}
