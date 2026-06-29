package com.nexa.infrastructure.growth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 增长子域基础设施 Bean 装配（基础设施层）。
 *
 * <p>提供签到日级状态机所需的 {@link Clock}（DailyCheckinUseCase / QueryCheckinStatusUseCase 注入，
 * 用于「今日 / 本月」判定）。用 {@link ConditionalOnMissingBean} 守护：若其它模块/全局配置已声明
 * {@code Clock} Bean，则让位以保持全局唯一一份时钟，避免重复 Bean 冲突；否则提供系统默认时区时钟。
 * 注入 {@code Clock} 而非直接 {@code LocalDate.now()} 是为让领域时间可在单测中固定（可测性）。</p>
 */
@Configuration
public class GrowthConfig {

    /**
     * 系统默认时区时钟（缺省装配）。
     *
     * @return 系统时区 {@link Clock}
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
