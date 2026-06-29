package com.nexa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Nexa 后端启动类（账号域垂直切片）。
 *
 * <p>Spring Boot 应用入口。组件扫描根为 {@code com.nexa}，覆盖 account 域四层
 * （interfaces/application/infrastructure；domain 为纯 POJO 被引用但不被 Spring 管理）。
 * {@code @ConfigurationPropertiesScan} 启用 {@code @ConfigurationProperties} Bean
 * （如 {@code ConfigAccountSettings}）的扫描绑定。</p>
 *
 * <p>运行约定（application.yml）：连真 PostgreSQL、Flyway 启动建表、JPA ddl-auto=validate、
 * 虚拟线程开启。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NexaApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NexaApplication.class, args);
    }
}
