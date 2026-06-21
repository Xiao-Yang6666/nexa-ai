package com.nexa.account.infrastructure.event;

import com.nexa.account.application.port.DomainEventPublisher;
import com.nexa.account.domain.event.UserRegistered;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布端口 {@link DomainEventPublisher} 的 Spring 适配实现（基础设施层）。
 *
 * <p>把领域事件转投到 Spring 的 {@link ApplicationEventPublisher}，使进程内订阅者
 * （{@code @EventListener} / {@code @TransactionalEventListener}）可消费注册副作用
 * （额度发放、邀请归因 F-1042/F-1043 等，后续 wave 接入）。应用层只依赖端口接口，
 * 不感知 Spring 事件机制（DDD §2.3）。</p>
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    /**
     * @param delegate Spring 容器提供的应用事件发布器
     */
    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>直接把领域事件作为应用事件发布。Spring 4.2+ 支持任意 POJO 事件，
     * 无需包成 {@code ApplicationEvent} 子类，领域事件因此保持零框架依赖。</p>
     */
    @Override
    public void publish(UserRegistered event) {
        delegate.publishEvent(event);
    }
}
