package com.nexa.routing.domain.vo;

/**
 * auto 分组跨组重试上下文值对象（不可变，F-2035/F-2036/F-2037，PRD CH-5 调度状态量）。
 *
 * <p>领域规则来源：FC-072 {@code CacheGetRandomSatisfiedChannel} 的状态量：
 * <ul>
 *   <li>{@code autoGroupIndex} —— 当前组索引（对应现网 {@code SetContextKey(AutoGroupIndex,i+1)} 中的 i）。</li>
 *   <li>{@code priorityRetry} —— 当前组内优先级重试层级（组耗尽切组时归零 {@code SetRetry(0)}）。</li>
 *   <li>{@code retryTimes} —— 全局重试次数上限（F-2037 {@code common.RetryTimes}）。</li>
 *   <li>{@code crossGroupRetry} —— 令牌级跨组重试开关（F-2036 {@code Token.CrossGroupRetry}）。</li>
 *   <li>{@code model} —— 当前请求模型名（候选查询参数）。</li>
 * </ul>
 * 不可变——每次推进返回新实例（重试循环中各步不互相污染）。</p>
 *
 * @param autoGroupIndex   当前组索引（>=0）
 * @param priorityRetry    当前组内优先级重试层级（>=0）
 * @param retryTimes       全局重试次数上限（>=0）
 * @param crossGroupRetry  令牌级跨组重试开关
 * @param model            请求模型名（非空白）
 */
public record AutoGroupRetryContext(int autoGroupIndex, int priorityRetry, int retryTimes,
                                    boolean crossGroupRetry, String model) {

    /** 紧凑构造器：归一下限。 */
    public AutoGroupRetryContext {
        if (autoGroupIndex < 0) autoGroupIndex = 0;
        if (priorityRetry < 0) priorityRetry = 0;
        if (retryTimes < 0) retryTimes = 0;
    }

    /**
     * 创建首次请求上下文（组索引=0，优先级重试=0）。
     *
     * @param retryTimes      全局重试次数上限（F-2037）
     * @param crossGroupRetry 令牌级跨组开关（F-2036）
     * @param model           请求模型名
     * @return 初始上下文
     */
    public static AutoGroupRetryContext initial(int retryTimes, boolean crossGroupRetry, String model) {
        return new AutoGroupRetryContext(0, 0, retryTimes, crossGroupRetry, model);
    }
}
