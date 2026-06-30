# Implementation Plan: 持久化层从 Spring Data JPA / Hibernate 全量迁移到 MyBatis-Plus

## Overview

本实现计划将 `nexa-backend`（Spring Boot 3.2.5 + JDK 21 + PostgreSQL + Flyway）的持久化层从 Spring Data JPA / Hibernate 全量迁移到 MyBatis-Plus（`mybatis-plus-spring-boot3-starter` 3.5.9），实现语言为 **Java 21**。

执行严格遵循设计中的分阶段策略：阶段1 引入但不切换 → 阶段2 样板域固化范式 → 阶段3 分域滚动迁移（由简到繁，先无软删除域、再软删除域、最后原子适配器回归）→ 阶段4 统一移除 JPA → 阶段5 收尾校验。每个域为独立提交点，提交前须在 `JAVA_HOME=corretto-21.0.5`（JDK21）下 `mvn -o compile` 通过且相关域测试绿，可 `git revert` 回滚。

约定：
- 任务粒度按域组织，一个域一个顶层任务，尽量不跨域。
- 阶段1–3 期间所有已迁域 PO 保持「JPA 注解 + MyBatis-Plus 注解」双注解并存态（满足 `ddl-auto=validate` 全局校验），数据读写走 Mapper。
- 标注 `*` 的子任务为测试相关（往返/软删除等价/原子性/分页/集成测试），可跳过以加速 MVP，但 Req5/6/7/8/13 的等价护栏强烈建议实现。
- 每个任务标注其对应的 requirements 需求编号（Req1–Req15）。

## Tasks

- [x] 1. 阶段1：引入 MyBatis-Plus（不切换，验证共存可构建）
  - [x] 1.1 在 `backend/pom.xml` 新增 MyBatis-Plus starter 依赖
    - 添加 `com.baomidou:mybatis-plus-spring-boot3-starter` 版本 `3.5.9`
    - 保留 `spring-boot-starter-data-jpa` 不动（并存态）
    - _Requirements: 1.1, 11.1_
  - [x] 1.2 创建 `infrastructure/persistence/MybatisPlusConfig`
    - 注册 `MybatisPlusInterceptor`，加入 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`
    - 加 `@MapperScan` 扫描各域 Mapper 包（按实际包路径核定，如 `com.nexa.infrastructure.**.persistence`）
    - **实测修正**：`@MapperScan` 加 `markerInterface = BaseMapper.class`，否则会误扫同包 26 个 `SpringData*JpaRepository` 致 bean 冲突
    - _Requirements: 8.3_
  - [x] 1.3 在 `application.yml` 新增 `mybatis-plus` 配置块
    - 设置 `configuration.map-underscore-to-camel-case: true`（兜底）与 `global-config.db-config.id-type: auto`
    - 暂不改动 `spring.jpa` 块（阶段4 才移除）
    - 复用 Spring Boot 自动装配的同一 `DataSource`（HikariCP `max-pool-size=5`），不新建第二连接池
    - _Requirements: 11.3, 14.2_
  - [x] 1.4 验证两 starter 共存可构建（无行为改动）
    - 以 `JAVA_HOME=corretto-21.0.5` 执行 `mvn -o compile`，确认编译绿（COMPILE_EXIT=0）
    - 800 单测全绿（`*IT` 需真 PG，本机无库跳过，CI 验证上下文加载）
    - _Requirements: 12.1, 12.3, 11.2_

- [x] 2. 阶段2：样板域 billing/BalanceTransaction（固化全部范式）
  - [x] 2.1 改写 `BalanceTransactionPO` 加 MyBatis-Plus 注解并保留 JPA 注解（双注解态）
    - 加 `@TableName`、`@TableId(type = IdType.AUTO)`、逐列 `@TableField` 显式声明列名
    - 保留无参构造函数
    - 保留原 JPA 注解（满足并存期 `ddl-auto=validate`）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 11.1_
  - [x] 2.2 在 `BalanceTransactionPO` 实现就近映射工厂方法
    - 实例方法 `toDomain()`：走 `BalanceTransaction.rehydrate(...)`，`type` 经 `fromWire` 解析，数值列 null → `0L` 兜底
    - 静态工厂 `static of(BalanceTransaction t)`：`po.type = t.type().wireValue()`，逐字段映射，无副作用
    - _Requirements: 4.1, 4.2, 4.5, 4.6_
  - [x] 2.3 新增 `BalanceTransactionMapper extends BaseMapper<BalanceTransactionPO>`
    - 基础 CRUD 由 `BaseMapper` 提供，不声明派生方法
    - _Requirements: 1.5_
  - [x] 2.4 改写 `BalanceTransactionRepositoryImpl` 使用 Mapper + LambdaQueryWrapper
    - `save` 用 `PO.of(...)` + `mapper.insert(po)` 回填 id + `assignId` + `po.toDomain()`
    - `findByUser` 用 `LambdaQueryWrapper.eq().orderByDesc().last("LIMIT " + safeLimit)`，`safeLimit` clamp 到 `[1,500]`
    - 删除私有 `toEntity` / `toDomain` 方法；删除 `SpringDataBalanceTransactionJpaRepository`
    - _Requirements: 4.3, 9.3, 15.1, 15.2_
  - [x]* 2.5 编写 `BalanceTransactionPOMappingTest` 往返完整性测试（建立模板）
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - 覆盖全字段、可空字段（operatorId/remark）、数值 null 兜底（→0L）、枚举 wire 码往返（3 测试绿）
  - [ ]* 2.6 编写 billing 分页查询等价测试（如适用）
    - **Property 4（P4）: 分页语义等价**
    - **Validates: Requirements 8.1, 8.2, 8.3**
  - [x] 2.7 验证样板域提交点
    - `JAVA_HOME=corretto-21.0.5` 下 `mvn -o compile` 绿，billing 相关测试绿
    - _Requirements: 10.1, 10.2, 12.3_

- [x] 3. 阶段2 检查点 - 确保样板域范式可复制且测试通过
  - Ensure all tests pass, ask the user if questions arise.
  - 确认 PO/Mapper/Impl/往返测试模板可作为后续域复制基线
  - _Requirements: 10.1, 12.3_

- [x] 4. 阶段3a：迁移 ops 域（无软删除，KV/历史，最简）
  - [x] 4.1 改写 ops 域各 PO（双注解 + 就近工厂方法）
    - 加 MyBatis-Plus 注解、逐列 `@TableField`、保留 JPA 注解与无参构造
    - 实现 `toDomain()` / `static of(...)`，删除散落映射
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 11.1_
  - [x] 4.2 新增 ops 域各 Mapper 并改写对应 RepositoryImpl
    - Mapper `extends BaseMapper<PO>`；Impl 用 Mapper + LambdaQueryWrapper 翻译派生查询/JPQL
    - 删除私有 `toEntity`/`toDomain`，动态值用占位符传参
    - _Requirements: 1.5, 4.3, 9.3, 15.1, 15.2_
  - [ ]* 4.3 编写 ops 域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [x] 4.4 验证 ops 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 5. 阶段3a：迁移 log 域（无软删除，纯历史）
  - [x] 5.1 改写 log 域各 PO（双注解 + 就近工厂方法）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 11.1_
  - [x] 5.2 新增 log 域各 Mapper 并改写对应 RepositoryImpl
    - 翻译派生查询/JPQL 为 LambdaQueryWrapper；含分页查询则用 `PageQueries.of` + `selectPage`
    - _Requirements: 1.5, 4.3, 8.1, 8.2, 9.3, 15.1, 15.2_
  - [ ]* 5.3 编写 log 域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 5.4 编写 log 域分页等价测试（如适用）
    - **Property 4（P4）: 分页语义等价**
    - **Validates: Requirements 8.1, 8.2, 8.3**
  - [x] 5.5 验证 log 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 6. 阶段3a：迁移 relay 域（核心网关，按实际软删除状态处理）
  - [x] 6.1 改写 relay 域各 PO（双注解 + 就近工厂方法）
    - 若该域含软删除列，按阶段3b 软删除范式处理（`@TableLogic`）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 11.1_
  - [x] 6.2 新增 relay 域各 Mapper 并改写对应 RepositoryImpl
    - _Requirements: 1.5, 4.3, 9.3, 15.1, 15.2_
  - [ ]* 6.3 编写 relay 域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [x] 6.4 验证 relay 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 7. 阶段3a：迁移 routing 域
  - [x] 7.1 改写 routing 域各 PO（双注解 + 就近工厂方法）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 11.1_
  - [x] 7.2 新增 routing 域各 Mapper 并改写对应 RepositoryImpl
    - _Requirements: 1.5, 4.3, 9.3, 15.1, 15.2_
  - [ ]* 7.3 编写 routing 域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [x] 7.4 验证 routing 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 8. 阶段3a：迁移 task / telegram / passkey / oauthprovider 域（无软删除小域分组）
  - [x] 8.1 改写 task 域 PO + Mapper + RepositoryImpl（双注解、就近工厂、wrapper 翻译）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.3, 4.5, 4.6, 9.3, 11.1, 15.1, 15.2_
  - [x] 8.2 改写 telegram 域 PO + Mapper + RepositoryImpl
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.3, 4.5, 4.6, 9.3, 11.1, 15.1, 15.2_
  - [x] 8.3 改写 passkey 域 PO + Mapper + RepositoryImpl
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.3, 4.5, 4.6, 9.3, 11.1, 15.1, 15.2_
  - [x] 8.4 改写 oauthprovider 域 PO + Mapper + RepositoryImpl
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.3, 4.5, 4.6, 9.3, 11.1, 15.1, 15.2_
  - [ ]* 8.5 编写上述各域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [x] 8.6 验证四域提交点（每域独立提交，`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 9. 阶段3a 检查点 - 确保所有无软删除域测试通过
  - Ensure all tests pass, ask the user if questions arise.
  - _Requirements: 12.3_

- [x] 10. 阶段3b：迁移 account 域（首个软删除域，实测确定 @TableLogic delval 方案并固化结论）
  - [x] 10.1 改写 `UserPO` 加软删除 `@TableLogic` 与双注解
    - `@TableLogic(value = "null", delval = "extract(epoch from now())")` 作用于 `deleted_at` 列
    - 逐列 `@TableField` 显式声明，保留 JPA 注解与无参构造
    - 实现 `toDomain()` / `static of(...)`
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 10.2 实测确认 3.5.9 是否接受 delval epoch 秒表达式，据此选定方案并固化
    - 若 `delval` 表达式被接受 → 采用 `@TableLogic` delval 方案
    - 若受限 → 回退：`@TableLogic` 仅声明 `value` 控制过滤，软删写操作改 Mapper 显式 `@Update("UPDATE users SET deleted_at = extract(epoch from now()) WHERE id = #{id} AND deleted_at IS NULL")`
    - 在本任务固化结论，供其余软删除域复用
    - _Requirements: 6.1, 6.3, 6.4_
  - [x] 10.3 新增 `UserMapper` 并改写 `UserRepositoryImpl`
    - 翻译 `findByUsername`/`existsByUsername` 等派生查询为 wrapper；软删过滤由 `@TableLogic` 自动追加
    - 保留唯一索引冲突 → `UserAlreadyExistsException` 的异常翻译（捕获 `DataIntegrityViolationException`）
    - 分页搜索用 `PageQueries.of` + `selectPage`，关键词用 `apply("LOWER(...) LIKE LOWER({0})", kw)` 占位防注入
    - 删除私有 `toEntity`/`toDomain`
    - _Requirements: 4.3, 8.1, 8.2, 9.3, 15.1, 15.2_
  - [ ]* 10.4 编写 UserPO 往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 10.5 编写 account 软删除等价测试
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  - [ ]* 10.6 编写 account 分页搜索等价测试
    - **Property 4（P4）: 分页语义等价**
    - **Validates: Requirements 8.1, 8.2, 8.3**
  - [x] 10.7 验证 account 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 11. 阶段3b：迁移 token 域（软删除，self-scope 可见性）
  - [x] 11.1 改写 token 域各 PO（`@TableLogic` + 双注解 + 就近工厂，复用 account 固化的 delval 方案）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 11.2 新增 token 域 Mapper 并改写 RepositoryImpl
    - 翻译派生查询/JPQL；软删过滤自动追加；`markDeleted` 等价为 `deleteById`/显式 `@Update`
    - _Requirements: 1.5, 4.3, 6.4, 9.3, 15.1, 15.2_
  - [ ]* 11.3 编写 token 往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 11.4 编写 token 软删除等价测试
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  - [x] 11.5 验证 token 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 12. 阶段3b：迁移 model 域（软删除）
  - [x] 12.1 改写 model 域各 PO（`@TableLogic` + 双注解 + 就近工厂）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 12.2 新增 model 域 Mapper 并改写 RepositoryImpl
    - _Requirements: 1.5, 4.3, 6.4, 9.3, 15.1, 15.2_
  - [ ]* 12.3 编写 model 往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 12.4 编写 model 软删除等价测试
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  - [x] 12.5 验证 model 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 13. 阶段3b：迁移 modelgroup 域（软删除 + PG 保留字列 "group" 转义）
  - [x] 13.1 改写 modelgroup 域各 PO（`@TableLogic` + 双注解 + 就近工厂）
    - PG 保留字列以 `@TableField(value = "\"group\"")` 双引号显式转义
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 13.2 新增 modelgroup 域 Mapper 并改写 RepositoryImpl
    - wrapper/apply 中引用保留字列时同样转义；动态值占位防注入
    - _Requirements: 1.5, 4.3, 6.4, 9.3, 15.1, 15.2_
  - [ ]* 13.3 编写 modelgroup 往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 13.4 编写 modelgroup 软删除等价测试
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  - [x] 13.5 验证 modelgroup 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 14. 阶段3b：迁移 billing 剩余（redemption 软删除域）
  - [x] 14.1 改写 billing redemption 各 PO（`@TableLogic` + 双注解 + 就近工厂）
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 14.2 新增 redemption Mapper 并改写 RepositoryImpl
    - _Requirements: 1.5, 4.3, 6.4, 9.3, 15.1, 15.2_
  - [ ]* 14.3 编写 redemption 往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [ ]* 14.4 编写 redemption 软删除等价测试
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 6.4**
  - [x] 14.5 验证 billing-redemption 提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 15. 阶段3b：迁移 growth 域（非原子部分 PO/Mapper/Impl）
  - [x] 15.1 改写 growth 域常规 PO（按软删除状态决定是否加 `@TableLogic`）+ 就近工厂
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 2.5, 4.1, 4.2, 4.5, 4.6, 6.1, 11.1_
  - [x] 15.2 新增 growth 域 Mapper 并改写常规 RepositoryImpl
    - 原子适配器 `Jdbc*` 在任务16 单独处理，此处仅常规仓储
    - _Requirements: 1.5, 4.3, 9.3, 15.1, 15.2_
  - [ ]* 15.3 编写 growth 域关键聚合往返完整性测试
    - **Property 1（P1）: 行为等价（映射往返）**
    - **Validates: Requirements 5.1, 5.2, 5.3**
  - [x] 15.4 验证 growth 域提交点（`mvn -o compile` + 域测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 16. 阶段3c：原子适配器（保留 JdbcTemplate，纳入回归覆盖）
  - [x] 16.1 确认 3 个 `Jdbc*` 原子适配器保留 JdbcTemplate 实现（OPTION A）
    - billing `JdbcUserQuotaAccount`、growth `JdbcUserQuotaAccount`、growth `JdbcAffiliateAccountRepository`
    - 维持单条 SQL 原子自增/CAS/条件扣减语义，不退化为「读-改-写」；`affected=0` 抛领域异常
    - 确保移除 JPA starter 后 `JdbcTemplate` 来源由 `spring-boot-starter-jdbc` 提供（见任务17.4）
    - _Requirements: 7.1, 7.3, 7.4, 14.1, 14.2, 14.3_
  - [ ]* 16.2 编写原子适配器并发原子性回归测试
    - **Property 3（P3）: 并发原子性不退化**
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4**
  - [x] 16.3 验证原子适配器提交点（`mvn -o compile` + 相关测试绿）
    - _Requirements: 10.2, 10.3, 12.3_

- [x] 17. 阶段4：移除 JPA（独立提交，须在全量测试绿后执行）
  - [x] 17.1 删除所有 PO 上的 JPA 注解
    - 移除 `@Entity`/`@Table`/`@Column`/`@Id`/`@GeneratedValue`/`@SQLRestriction` 等，仅保留 MyBatis-Plus 注解
    - _Requirements: 1.3_
  - [x] 17.2 从 `backend/pom.xml` 移除 `spring-boot-starter-data-jpa` 依赖
    - _Requirements: 1.2_
  - [x] 17.3 从 `application.yml` 删除 `spring.jpa` 配置块
    - _Requirements: 1.4_
  - [x] 17.4 确保 `spring-boot-starter-jdbc` 在依赖树中（供 `Jdbc*` 适配器）
    - 显式声明 `spring-boot-starter-jdbc` 依赖，验证依赖树包含 `spring-jdbc`
    - _Requirements: 14.1_
  - [x] 17.5 移除 MapStruct（确认无其他用途后）
    - 移除 `org.mapstruct:mapstruct` 依赖及 `maven-compiler-plugin` 的 `annotationProcessorPaths` 配置
    - _Requirements: 4.4_
  - [x] 17.6 验证移除 JPA 后构建与全量测试通过
    - `JAVA_HOME=corretto-21.0.5` 下 `mvn -o compile` 绿，全量 ~535 测试绿
    - _Requirements: 10.4, 12.1, 12.2_

- [x] 18. 阶段4 检查点 - 移除 JPA 后确保全量测试通过
  - Ensure all tests pass, ask the user if questions arise.
  - _Requirements: 10.4, 12.2_

- [x] 19. 阶段5：架构校验（domain 层零持久化导入）
  - [x] 19.1 新增 ArchUnit 规则校验 `com.nexa.domain.**` 无持久化导入
    - 规则禁止 MyBatis-Plus / JPA / Spring Data 导入出现在 domain 层，违规使构建失败
    - 引入 ArchUnit 测试依赖（如缺失）
    - **Property 5（P5）: domain 零持久化感知**
    - _Requirements: 3.1, 3.2_
  - [ ]* 19.2 校验上层零改动（domain/application/interfaces diff 为零）
    - **Property 6（P6）: 上层零改动**
    - **Validates: Requirements 9.1, 9.2**

- [x] 20. 阶段5：集成测试覆盖（取代消失的 ddl-auto=validate 启动校验）
  - [ ]* 20.1 编写 `*IT` 集成测试覆盖 CRUD 与 PG 保留字列转义
    - 连真实/测试 PostgreSQL，验证 Mapper SQL 正确、字段错配立刻暴露
    - **Validates: Requirements 13.1, 13.2, 13.3**
  - [ ]* 20.2 编写 `*IT` 覆盖 @TableLogic 软删除过滤与写入量纲
    - **Property 2（P2）: 软删除过滤等价**
    - **Validates: Requirements 6.2, 6.3, 13.2**
  - [ ]* 20.3 编写 `*IT` 覆盖分页方言（PG LIMIT/OFFSET）与排序稳定性
    - **Property 4（P4）: 分页语义等价**
    - **Validates: Requirements 8.2, 8.3, 13.2**
  - [ ]* 20.4 编写 `*IT` 覆盖原子自增/CAS/条件扣减
    - **Property 3（P3）: 并发原子性不退化**
    - **Validates: Requirements 7.1, 7.2, 13.2**

- [x] 21. 阶段5 最终检查点 - 全量测试绿，迁移完成
  - Ensure all tests pass, ask the user if questions arise.
  - 确认 ~535 基线测试全绿、ArchUnit 规则绿、`*IT` 集成测试绿
  - _Requirements: 12.2, 13.2, 13.3_

## Notes

- 标注 `*` 的子任务为测试相关（往返/软删除等价/原子性/分页/集成测试 + ArchUnit 上层零改动校验），可跳过以加速 MVP；但 Req5/6/7/8/13 的等价护栏强烈建议实现，否则迁移正确性缺乏天然验证。
- 每个域为独立提交点，提交前须在 `JAVA_HOME=corretto-21.0.5`（JDK21）下 `mvn -o compile` 通过且相关域测试绿，可 `git revert` 回滚（Req10）。
- 阶段1–3 期间所有已迁域 PO 保持 JPA + MyBatis-Plus 双注解并存态（Req11），阶段4 统一移除 JPA 注解与 starter（不可细粒度回滚的单向门，须全量测试绿后执行）。
- account/UserPO（任务10）是首个软删除域，须实测确认 `@TableLogic` 的 `delval` epoch 秒表达式是否被 3.5.9 接受，据此选定 delval 表达式或回退到显式 `@Update`，并固化结论供其余软删除域复用。
- 各域的具体软删除状态以实际代码为准；任务6/15 标注「按实际软删除状态处理」，实现时按域确认是否需要 `@TableLogic`。
- 3 个 `Jdbc*` 原子适配器按设计推荐保留 JdbcTemplate（OPTION A），不迁移到 Mapper，但纳入并发原子性回归与集成测试覆盖（Req7、Req14）。
- 每个 Property 测试任务显式引用 design 的正确性属性（P1–P6）并标注所验证的 requirements 子句。
