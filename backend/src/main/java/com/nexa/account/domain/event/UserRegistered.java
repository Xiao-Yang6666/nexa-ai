package com.nexa.account.domain.event;

import java.time.Instant;

/**
 * 领域事件：用户已注册（UserRegistered）。
 *
 * <p>当 {@link com.nexa.account.domain.model.User} 聚合根成功完成注册不变量校验、
 * 生成新用户后发布。用于解耦注册的副作用（PRD AC-1 R13：发放初始额度后续动作、
 * 邀请人 aff_count++/aff_quota 归因 F-1042/F-1043、欢迎通知等），本切片仅承载事件本身，
 * 副作用订阅在后续 wave 接入。</p>
 *
 * <p>不可变记录（record），零框架依赖——纯领域对象。</p>
 *
 * @param userId     新注册用户 ID（注册时聚合根尚未持久化则为 {@code null}，落库后回填语义由应用层处理）
 * @param username   用户名
 * @param inviterId  邀请人 ID（无有效 aff_code 时为 0，对齐 PRD AC-1 R12）
 * @param occurredAt 事件发生时刻
 */
public record UserRegistered(
        Long userId,
        String username,
        long inviterId,
        Instant occurredAt) {

    /**
     * 创建一个发生在当前时刻的注册事件。
     *
     * @param userId    新用户 ID（可为 null）
     * @param username  用户名
     * @param inviterId 邀请人 ID（无则 0）
     * @return 注册领域事件
     */
    public static UserRegistered now(Long userId, String username, long inviterId) {
        return new UserRegistered(userId, username, inviterId, Instant.now());
    }
}
