package com.nexa.log.domain.vo;

/**
 * 日志列表/统计查询条件值对象（不可变；F-4001 管理端 / F-4002 用户自助）。
 *
 * <p>充血查询条件：把「管理端 vs 自助」的可见过滤维度差异固化在工厂方法里，而非散落到接口/SQL——
 * <ul>
 *   <li>{@link #forAdmin}：管理端可按全部维度过滤（username/channel/request_id 等），不绑定 user_id；</li>
 *   <li>{@link #forSelf}：自助强制按 {@code userId} 过滤，且<b>结构上不接受</b> username/channel/request_id
 *       维度（F-4002 契约「无 username/channel 过滤维度，自助接口不暴露」）——这些字段在 forSelf 构造时永远为 null，
 *       杜绝越权按他人 username 查询。</li>
 * </ul>
 * </p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4001/F-4002；ROLE-PERMISSION-MATRIX §3「self-scope 强制 user_id 过滤」。
 * {@code type==0}（或 {@code typeFilter==null}）表示「全部类型不过滤」（契约 type:0=全部）。</p>
 *
 * @param userId         归属用户 id 过滤（自助必填；管理端为 null=全站）
 * @param typeFilter     日志类型过滤（null=全部类型）
 * @param startTimestamp 起始时间（epoch 秒，包含；null=不限）
 * @param endTimestamp   结束时间（epoch 秒，包含；null=不限）
 * @param username       用户名过滤（仅管理端；自助恒为 null）
 * @param tokenName      令牌名过滤（null=不限）
 * @param modelName      模型名过滤（按 model_name=C 口径；null=不限）
 * @param channelId      渠道 id 过滤（仅管理端；自助恒为 null）
 * @param group          分组过滤（null=不限）
 * @param requestId      请求 id 过滤（仅管理端；自助恒为 null）
 * @param upstreamRequestId 上游请求 id 过滤（仅管理端；自助恒为 null）
 */
public record LogQuery(
        Long userId,
        LogType typeFilter,
        Long startTimestamp,
        Long endTimestamp,
        String username,
        String tokenName,
        String modelName,
        Long channelId,
        String group,
        String requestId,
        String upstreamRequestId
) {

    /**
     * 构造管理端全量查询条件（F-4001，可按全部维度过滤，不绑定 user_id）。
     *
     * @param type            原始 type 参数（0/null=全部）
     * @param start           起始时间（epoch 秒，可空）
     * @param end             结束时间（epoch 秒，可空）
     * @param username        用户名过滤（可空）
     * @param tokenName       令牌名过滤（可空）
     * @param modelName       模型名过滤（可空）
     * @param channel         渠道 id（可空字符串，非数字/空白→不过滤）
     * @param group           分组过滤（可空）
     * @param requestId       请求 id 过滤（可空）
     * @param upstreamRequestId 上游请求 id 过滤（可空）
     * @return 管理端查询条件
     */
    public static LogQuery forAdmin(Integer type, Long start, Long end,
                                    String username, String tokenName, String modelName,
                                    String channel, String group,
                                    String requestId, String upstreamRequestId) {
        return new LogQuery(
                null,
                typeOf(type),
                start, end,
                blankToNull(username),
                blankToNull(tokenName),
                blankToNull(modelName),
                parseChannel(channel),
                blankToNull(group),
                blankToNull(requestId),
                blankToNull(upstreamRequestId));
    }

    /**
     * 构造用户自助查询条件（F-4002，强制 user_id，无 username/channel/request_id 维度）。
     *
     * <p>结构上把 username/channel/requestId/upstreamRequestId 钉死为 null：自助接口即便前端传了
     * 也不会进入查询，从源头杜绝按他人维度越权（不靠接口层记得「别传」）。</p>
     *
     * @param userId    当前认证用户 id（来自上下文，防伪造）
     * @param type      原始 type（0/null=全部）
     * @param start     起始时间（可空）
     * @param end       结束时间（可空）
     * @param tokenName 令牌名过滤（可空）
     * @param modelName 模型名过滤（可空）
     * @param group     分组过滤（可空）
     * @return 自助查询条件
     */
    public static LogQuery forSelf(long userId, Integer type, Long start, Long end,
                                   String tokenName, String modelName, String group) {
        return new LogQuery(
                userId,
                typeOf(type),
                start, end,
                null,                 // username 维度自助不暴露
                blankToNull(tokenName),
                blankToNull(modelName),
                null,                 // channel 维度自助不暴露
                blankToNull(group),
                null,                 // request_id 维度自助不暴露
                null);
    }

    /**
     * 派生「仅统计消费类」的查询条件（F-4004/F-4005：quota/rpm/tpm 仅统计 Type=2 Consume）。
     *
     * <p>无论原查询 typeFilter 为何，统计版强制把类型钉为 CONSUME（契约口径），其余过滤维度沿用。
     * 返回新实例（不可变值对象）。</p>
     *
     * @return 类型固定为 CONSUME 的查询条件
     */
    public LogQuery asConsumeOnly() {
        return new LogQuery(userId, LogType.CONSUME, startTimestamp, endTimestamp,
                username, tokenName, modelName, channelId, group, requestId, upstreamRequestId);
    }

    private static LogType typeOf(Integer type) {
        // type=0 或缺省 = 全部类型（契约 type:0=全部），返回 null 让仓储不加类型条件。
        if (type == null || type == 0) {
            return null;
        }
        return LogType.fromCode(type);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Long parseChannel(String channel) {
        // channel 在契约里是 string；非法/空白当作「不按渠道过滤」（宽容读侧），避免 400 干扰列表查询。
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            long v = Long.parseLong(channel.trim());
            return v <= 0 ? null : v;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
