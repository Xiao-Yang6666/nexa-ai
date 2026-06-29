package com.nexa.application.account.port;

import com.nexa.domain.account.event.UserRegistered;

/**
 * 领域事件发布端口（应用层依赖，基础设施层实现）。
 *
 * <p>用例在事务边界内发布领域事件（如 {@link UserRegistered}），由实现转投
 * Spring {@code ApplicationEventPublisher} / 消息队列。定义为端口以便单测注入捕获桩。</p>
 */
public interface DomainEventPublisher {

    /**
     * 发布用户注册事件。
     *
     * @param event 注册领域事件
     */
    void publish(UserRegistered event);
}
