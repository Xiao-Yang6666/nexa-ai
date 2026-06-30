package com.nexa.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * MyBatis-Plus 全局配置（基础设施层共享构件）。
 *
 * <p>持久化层从 Spring Data JPA / Hibernate 滚动迁移到 MyBatis-Plus 的阶段1产物：引入但不切换。
 * 该配置注册分页插件并集中扫描各域 Mapper，迁移期与 JPA starter 共存、不改变现有行为。</p>
 *
 * <p>Mapper 扫描：各 bounded context 的 Mapper 统一落在 {@code com.nexa.infrastructure.<域>.persistence.mapper}
 * 子包下（与 {@code RepositoryImpl} 分离，仅供其内部依赖注入），故以
 * {@code com.nexa.infrastructure.**.persistence.mapper} 通配集中扫描。{@code markerInterface = BaseMapper.class}
 * 再限定只注册继承 {@code BaseMapper} 的 MyBatis-Plus Mapper（双保险）。</p>
 *
 * <p>分页：{@link PaginationInnerInterceptor} 以 {@link DbType#POSTGRE_SQL} 生成 PostgreSQL 方言
 * {@code LIMIT/OFFSET} 分页 SQL，与迁移前 Spring Data 分页语义对齐（Req8.3）。</p>
 *
 * <p>连接池：MyBatis-Plus 复用 Spring Boot 自动装配的同一 {@code DataSource}（HikariCP），
 * 不新建第二个连接池（Req11.3）。</p>
 *
 * <p>Bean 命名：默认 generator 取「decapitalize 后的简单类名」当 bean name，跨 bounded context
 * 的同名 Mapper（如 {@code relay} 与 {@code model} 各自的 {@code UserModelAliasMapper}）会撞名导致
 * {@code ConflictingBeanDefinitionException}。改用 {@link FullyQualifiedAnnotationBeanNameGenerator}
 * 以全限定类名作 bean name，彻底消除跨包重名冲突；各 {@code RepositoryImpl} 均按类型注入 Mapper，
 * 不依赖其 bean name，故无副作用。</p>
 */
@Configuration
@MapperScan(
        basePackages = "com.nexa.infrastructure.**.persistence.mapper",
        markerInterface = BaseMapper.class,
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
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
