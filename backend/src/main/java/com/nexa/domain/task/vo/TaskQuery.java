package com.nexa.domain.task.vo;

/**
 * 任务查询条件值对象（F-2003 用户列表 / F-2004 管理端列表，不可变）。
 *
 * <p>聚合「任务列表查询」的全部过滤/分页条件。用户侧强制 user_id 自身隔离（PRD AT-2
 * self-scope）；管理侧允许跨用户查询。</p>
 *
 * @param taskId     任务 ID 精确匹配（可空）
 * @param userId     用户 id 过滤（可空；用户侧必填，管理侧可空）
 * @param channelId  渠道 id 过滤（可空，管理侧用）
 * @param platform   平台过滤（可空）
 * @param action     动作过滤（可空）
 * @param status     状态过滤（可空）
 * @param startTime  提交时间起（epoch 秒，可空）
 * @param endTime    提交时间止（epoch 秒，可空）
 * @param page       页码（从 1 起）
 * @param pageSize   每页条数（1..100）
 */
public record TaskQuery(
        String taskId,
        Integer userId,
        Integer channelId,
        String platform,
        String action,
        String status,
        Long startTime,
        Long endTime,
        int page,
        int pageSize
) {

    /** 默认页面大小（对齐 openapi PageSizeParam 缺省）。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大页面大小（防止超大页）。 */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 归一构造，page/pageSize 边界处理。
     *
     * @param taskId    任务 ID（可空）
     * @param userId    用户 id（可空）
     * @param channelId 渠道 id（可空）
     * @param platform  平台（可空）
     * @param action    动作（可空）
     * @param status    状态（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @param page      页码（null/非正→1）
     * @param pageSize  每页条数（null/非正→DEFAULT；>MAX→MAX）
     * @return 归一查询条件
     */
    public static TaskQuery of(String taskId, Integer userId, Integer channelId,
                               String platform, String action, String status,
                               Long startTime, Long endTime, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new TaskQuery(blankToNull(taskId), userId, channelId,
                blankToNull(platform), blankToNull(action), blankToNull(status),
                startTime, endTime, p, ps);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
