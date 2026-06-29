package com.nexa.interfaces.log.api.dto;

import com.nexa.application.log.LogPage;
import com.nexa.domain.log.model.LogEntry;

import java.util.List;

/**
 * 日志列表分页响应数据 DTO（接口层，F-4001 管理端 / F-4002 自助）。
 *
 * <p>对齐 openapi {@code data: { items[], total }} 结构（额外回带 page/page_size 便于前端分页器）。
 * 提供两个工厂分别裁剪为管理视图（{@link AdminLogVO}）/ 用户视图（{@link UserLogVO}），
 * 由控制器按角色选择——可见性裁剪的最终落点（同一 {@link LogPage} 两种序列化口径）。</p>
 *
 * @param <V>      列表项视图类型（AdminLogVO 或 UserLogVO）
 * @param items    当前页日志视图
 * @param total    满足过滤的总条数
 * @param page     当前页号
 * @param pageSize 每页条数
 */
public record LogListVO<V>(List<V> items, long total, int page, int pageSize) {

    /**
     * 裁剪为管理视图列表（F-4001，全字段含 B/成本/利润）。
     *
     * @param page 用例分页结果
     * @return 管理视图列表响应
     */
    public static LogListVO<AdminLogVO> admin(LogPage page) {
        List<AdminLogVO> items = page.items().stream().map(AdminLogVO::from).toList();
        return new LogListVO<>(items, page.total(), page.pagination().page(), page.pagination().pageSize());
    }

    /**
     * 裁剪为用户视图列表（F-4002，B/成本/利润/渠道丢弃）。
     *
     * @param page 用例分页结果
     * @return 用户视图列表响应
     */
    public static LogListVO<UserLogVO> user(LogPage page) {
        List<UserLogVO> items = page.items().stream().map(UserLogVO::from).toList();
        return new LogListVO<>(items, page.total(), page.pagination().page(), page.pagination().pageSize());
    }

    /**
     * 裁剪一组领域日志为用户视图数组（F-4003 按令牌，无分页 total，直接数组）。
     *
     * @param logs 领域日志列表
     * @return 用户视图列表
     */
    public static List<UserLogVO> userArray(List<LogEntry> logs) {
        return logs.stream().map(UserLogVO::from).toList();
    }
}
