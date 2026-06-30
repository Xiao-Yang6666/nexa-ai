package com.nexa.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置（基础设施层共享构件）。
 *
 * <p>持久化层从 Spring Data JPA / Hibernate 滚动迁移到 MyBatis-Plus 的阶段1产物：引入但不切换。
 * 该配置注册分页插件并集中扫描各域 Mapper，迁移期与 JPA starter 共存、不改变现有行为。</p>
 *
 * <p>Mapper 扫描：各 bounded context 的 Mapper 统一落在 {@code com.nexa.infrastructure.<域>.persistence}
 * 包下，故以 {@code com.nexa.infrastructure.**.persistence} 通配集中扫描。<b>关键</b>：该包下并存期同时
 * 住着 26 个 {@code SpringData*JpaRepository}（Spring Data JPA 接口），若不加限定，MyBatis 会把它们也
 * 注册成 {@code MapperFactoryBean}，与 Spring Data 同名 bean 冲突致启动失败。故以
 * {@code markerInterface = BaseMapper.class} 限定只扫描继承 {@code BaseMapper} 的 MyBatis-Plus Mapper，
 * JPA 接口不受影响。阶段1尚无任何 Mapper，空匹配不影响启动。</p>
 *
 * <p>分页：{@link PaginationInnerInterceptor} 以 {@link DbType#POSTGRE_SQL} 生成 PostgreSQL 方言
 * {@code LIMIT/OFFSET} 分页 SQL，与迁移前 Spring Data 分页语义对齐（Req8.3）。</p>
 *
 * <p>连接池：MyBatis-Plus 复用 Spring Boot 自动装配的同一 {@code DataSource}（HikariCP），
 * 不新建第二个连接池（Req11.3）。</p>
 */
@Configuration
@MapperScan(basePackages = "com.nexa.infrastructure.**.persistence", markerInterface = BaseMapper.class)
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器链，加入 PostgreSQL 方言分页内部拦截器。
     *
     * @return 配置完成的 {@link MybatisPlusInterceptor}
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
