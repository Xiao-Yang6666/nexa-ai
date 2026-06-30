package com.nexa.interfaces.api.task.dto;

import java.util.List;

/**
 * 任务列表分页响应体（接口层出参，对齐 openapi 任务列表 {@code data: {items,total,page,page_size}}）。
 *
 * @param <T>      列表项视图类型（{@link TaskUserVO} 或 {@link TaskAdminVO}）
 * @param items    当前页任务视图列表
 * @param total    满足条件的总条数
 * @param page     当前页码
 * @param pageSize 每页条数
 */
public record TaskListData<T>(List<T> items, long total, int page, int pageSize) {
}
