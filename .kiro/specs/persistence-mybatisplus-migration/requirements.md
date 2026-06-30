# Requirements Document

## Introduction

本需求文档由已确认的设计文档（`design.md`）反向派生而来，描述 `nexa-backend`（Spring Boot 3.2.5 + JDK 21 + PostgreSQL + Flyway）持久化层从 **Spring Data JPA / Hibernate** 全量替换为 **MyBatis-Plus**（`mybatis-plus-spring-boot3-starter` 3.5.9）这一**纯技术重构**所必须满足的、可验证的条件。

需求聚焦「重构后系统必须满足什么」，而非实现步骤。核心命题是**行为严格等价**：对外 REST 契约、软删除语义、并发原子性、分页语义保持不变；四层 DDD 目录结构与 PO 隔离保持不变；`domain` / `application` / `interfaces` 三层代码与测试零改动。迁移规模为 29 个 PO、29 个数据访问接口（→ Mapper）、26 个 RepositoryImpl，并保留 3 个跨上下文 `Jdbc*` 原子适配器，横跨 account / billing / growth / model / modelgroup / relay / routing / log / ops / passkey / task / telegram / token / oauthprovider 等域。样板域为 `billing/BalanceTransaction`。

## Glossary

- **Persistence_Layer（持久化层）**：`infrastructure/<域>/persistence/` 下负责数据库读写的组件集合，是本次重构的唯一改动面（外加 3 个 `Jdbc*` 适配器）。
- **PO（Persistent_Object，持久化对象）**：留在 `infrastructure/<域>/persistence/po/` 的数据库行映射对象，承载持久化框架注解与就近映射工厂方法。
- **Mapper**：继承 `BaseMapper<PO>` 的 MyBatis-Plus 数据访问接口，取代原 Spring Data JPA 接口。
- **Repository_Adapter（仓储适配器）**：实现 `domain` 层 Repository 接口的 `XxxRepositoryImpl`，用 Mapper 与 PO 工厂方法完成领域对象与 PO 转换。
- **Domain_Layer（领域层）**：`com.nexa.domain.**` 包，承载充血聚合与值对象，零框架/零持久化感知。
- **Upper_Layers（上层）**：`domain`、`application`、`interfaces` 三层的总称。
- **Mapping_Factory（就近映射工厂）**：PO 上的实例方法 `toDomain()` 与静态工厂 `static of(domainObj)`，承载 PO ↔ domain 映射逻辑。
- **Soft_Delete（软删除）**：以 `deleted_at`（可空 `Long`，epoch 秒时间戳）标记删除、普通查询自动过滤已删行的机制，迁移后由 MyBatis-Plus `@TableLogic` 承载，等价原 JPA `@SQLRestriction("deleted_at IS NULL")`。
- **Atomic_Adapter（原子适配器）**：3 个跨上下文 `Jdbc*` 适配器（billing `JdbcUserQuotaAccount`、growth `JdbcUserQuotaAccount`、growth `JdbcAffiliateAccountRepository`），以单条 SQL 表达原子自增/CAS/条件扣减语义。
- **Pagination_Support（分页支持）**：`infrastructure/persistence/PageQueries` 等共享构件与 MyBatis-Plus `PaginationInnerInterceptor`，承载 1-based 页号分页。
- **Build_System（构建系统）**：以 `JAVA_HOME=corretto-21.0.5`（JDK21）执行的 Maven 构建与测试流程。
- **Architecture_Check（架构校验）**：ArchUnit 规则，校验 `domain` 层无持久化导入。
- **Coexistence_State（并存中间态）**：迁移期 JPA starter 与 MyBatis-Plus starter 同时在依赖中、已迁域 PO 双注解的过渡状态。
- **Migration_Process（迁移过程）**：分阶段、分域滚动的重构执行流程。

## Requirements

### Requirement 1: 持久化框架技术栈替换

**User Story:** 作为后端维护者，我希望持久化层由 MyBatis-Plus 实现，以便统一数据访问技术栈并移除对 Spring Data JPA / Hibernate 的依赖。

#### Acceptance Criteria

1. THE Persistence_Layer SHALL 使用 `com.baomidou:mybatis-plus-spring-boot3-starter` 版本 `3.5.9` 作为数据访问框架。
2. WHEN 迁移全部完成，THE Build_System SHALL 在依赖树中不包含 `spring-boot-starter-data-jpa`。
3. WHEN 迁移全部完成，THE Persistence_Layer SHALL 在所有 PO 上不保留任何 JPA 注解（`@Entity`、`@Table`、`@Column`、`@Id`、`@SQLRestriction` 等）。
4. WHEN 迁移全部完成，THE Build_System SHALL 在 `application.yml` 中不包含 `spring.jpa` 配置块。
5. THE Persistence_Layer SHALL 为每个原 Spring Data 数据访问接口提供一个继承 `BaseMapper<PO>` 的 Mapper 等价物。

### Requirement 2: PO 隔离与持久化注解收敛

**User Story:** 作为架构维护者，我希望持久化框架注解仅出现在 PO 上，以便保持领域模型与基础设施的边界清晰。

#### Acceptance Criteria

1. THE PO SHALL 位于 `infrastructure/<域>/persistence/po/` 包内。
2. THE Persistence_Layer SHALL 仅在 PO 上声明 MyBatis-Plus 注解（`@TableName`、`@TableId`、`@TableField`、`@TableLogic` 等）。
3. THE PO SHALL 为每个数据库列用 `@TableField` 显式声明列名。
4. WHERE 数据库列名为 PostgreSQL 保留字（如 `group`、`key`），THE PO SHALL 在 `@TableField` 中以双引号显式转义该列名。
5. THE PO SHALL 提供无参构造函数以供 MyBatis-Plus 实例化。

### Requirement 3: 领域层零持久化感知

**User Story:** 作为架构维护者，我希望领域层不感知任何持久化框架，以便领域模型独立于技术实现演进。

#### Acceptance Criteria

1. WHEN 迁移全部完成，THE Domain_Layer SHALL 不包含任何 MyBatis-Plus、JPA 或 Spring Data 的导入语句。
2. THE Architecture_Check SHALL 校验 `com.nexa.domain.**` 不包含上述持久化导入并在违规时使构建失败。

### Requirement 4: PO ↔ domain 就近工厂映射

**User Story:** 作为后端维护者，我希望映射逻辑收敛在 PO 的就近工厂方法上，以便消除散落在各 RepositoryImpl 的私有映射并避免引入 MapStruct。

#### Acceptance Criteria

1. THE PO SHALL 提供实例方法 `toDomain()`，将自身转换为对应领域聚合。
2. THE PO SHALL 提供静态工厂方法 `of(domainObj)`，将领域聚合转换为 PO。
3. THE Repository_Adapter SHALL 通过 `PO.of(...)` 与 `po.toDomain()` 完成映射，且不包含私有 `toEntity` / `toDomain` 方法。
4. WHEN 迁移全部完成且确认 MapStruct 无其他用途，THE Build_System SHALL 不包含 `org.mapstruct:mapstruct` 依赖及编译器插件的 `annotationProcessorPaths` 配置。
5. WHEN 领域聚合的枚举类型映射为数据库列，THE Mapping_Factory SHALL 在 `of` 方向写入枚举 wire 码、在 `toDomain` 方向以 `fromWire` 解析。
6. IF 数据库数值列读取为 null，THEN THE Mapping_Factory SHALL 按既有契约以 `0L` 兜底重建对应领域字段。

### Requirement 5: 映射往返完整性护栏

**User Story:** 作为后端维护者，我希望关键聚合具备 domain→PO→domain 往返完整性测试，以便手写映射漏字段时在测试期立即暴露。

#### Acceptance Criteria

1. THE Persistence_Layer SHALL 为每个关键聚合提供一个 `domain → PO → domain` 往返完整性测试。
2. WHEN 对任一领域聚合执行 `PO.of(d).toDomain()`，THE Mapping_Factory SHALL 在所有持久化字段（id、值对象、枚举 wire 码、数值 null 兜底、可空字段）上产出与原聚合 `d` 语义等价的结果。
3. THE 往返完整性测试 SHALL 覆盖全字段、可空字段与 null 兜底以及枚举 wire 码往返。

### Requirement 6: 软删除语义等价

**User Story:** 作为后端维护者，我希望软删除语义在迁移后保持不变，以便已删数据继续对普通查询不可见且删除时间量纲一致。

#### Acceptance Criteria

1. THE Soft_Delete SHALL 以 MyBatis-Plus `@TableLogic` 作用于 `deleted_at` 列，等价原 JPA `@SQLRestriction("deleted_at IS NULL")`。
2. WHEN 普通查询（`selectList` / `selectById` / `selectCount`）在软删除域执行，THE Persistence_Layer SHALL 不返回 `deleted_at` 非空的行。
3. WHEN 对软删除域记录执行删除操作，THE Persistence_Layer SHALL 将 `deleted_at` 写入为 epoch 秒时间戳，与迁移前同量纲，而非 `0` / `1`。
4. WHEN 删除操作命中已软删除的行，THE Persistence_Layer SHALL 保持原 `markDeleted` 仅在 `deleted_at IS NULL` 时更新的幂等语义。

### Requirement 7: 并发原子性不退化

**User Story:** 作为账务系统维护者，我希望跨上下文原子自增/扣减在迁移后仍以单条 SQL 表达，以便并发场景下不出现丢更新或超额扣减。

#### Acceptance Criteria

1. THE Atomic_Adapter SHALL 以单条 SQL 完成自增/扣减/CAS，而不退化为「读-改-写」序列。
2. WHEN 任意并发 credit / debit / CAS 序列执行，THE Atomic_Adapter SHALL 产出与单条原子 SQL 串行执行相同的终态（无丢更新、无超额扣减）。
3. WHEN 原子更新影响行数为 0（用户不存在或已软删），THE Atomic_Adapter SHALL 抛出对应领域异常而不静默吞错。
4. THE Atomic_Adapter SHALL 在对软删除用户（`deleted_at` 非空）执行时不修改其数据。

### Requirement 8: 分页语义等价

**User Story:** 作为接口使用者，我希望分页查询在迁移后返回一致的页与总数，以便分页行为对调用方透明不变。

#### Acceptance Criteria

1. THE Pagination_Support SHALL 接受 1-based 页号并直接映射为 MyBatis-Plus `Page`，不做页号减一。
2. WHEN 以相同 `(page1Based, pageSize)` 查询，THE Pagination_Support SHALL 返回与迁移前 Spring Data 一致的页内容、总数与排序顺序。
3. THE Pagination_Support SHALL 通过 `PaginationInnerInterceptor`（`DbType.POSTGRE_SQL`）生成 PostgreSQL 方言分页 SQL。

### Requirement 9: 上层零改动与对外契约等价

**User Story:** 作为系统维护者，我希望迁移不触及上层代码与对外 REST 契约，以便迁移正确性可由上层既有测试天然验证。

#### Acceptance Criteria

1. WHEN 迁移完成，THE Upper_Layers SHALL 在 `domain` / `application` / `interfaces` 三层的代码与测试上产生零 diff。
2. THE Persistence_Layer SHALL 保持对外 REST 契约不变。
3. WHEN 唯一索引冲突发生，THE Repository_Adapter SHALL 将 Spring `DataIntegrityViolationException` 翻译为与迁移前一致的领域异常（如 `UserAlreadyExistsException`）。

### Requirement 10: 分阶段滚动迁移与可回滚

**User Story:** 作为迁移执行者，我希望按域滚动迁移且每个提交点可回滚，以便控制风险并能定位与撤销回归。

#### Acceptance Criteria

1. THE Migration_Process SHALL 以样板域 `billing/BalanceTransaction` 先行建立可复制的 PO/Mapper/Impl/往返测试模板。
2. WHEN 每个域迁移在提交点完成，THE Build_System SHALL 使 `mvn -o compile` 与该域相关测试通过。
3. IF 某域迁移引入回归，THEN THE Migration_Process SHALL 支持通过 `git revert` 该域提交回滚而不影响其他已迁域。
4. THE Migration_Process SHALL 将移除 JPA starter（阶段 4）作为在所有域测试全绿后才执行的独立提交。

### Requirement 11: 并存中间态可启动

**User Story:** 作为迁移执行者，我希望迁移中间态两套框架可共存并能启动，以便在 `ddl-auto=validate` 全局校验下逐域迁移。

#### Acceptance Criteria

1. WHILE 处于 Coexistence_State，THE Persistence_Layer SHALL 在已迁域 PO 上同时保留 JPA 注解与 MyBatis-Plus 注解。
2. WHILE 处于 Coexistence_State，THE Persistence_Layer SHALL 通过 Mapper 进行实际数据读写，而 JPA 仅用于启动期 `ddl-auto=validate` 全局校验。
3. WHILE 处于 Coexistence_State，THE Persistence_Layer SHALL 使 MyBatis-Plus 复用 Spring Boot 自动装配的同一 `DataSource`，不新建第二个连接池。

### Requirement 12: 构建环境与回归基线

**User Story:** 作为迁移执行者，我希望在 JDK21 下构建并保持基线测试全绿，以便每个迁移提交点都有可验证的质量门禁。

#### Acceptance Criteria

1. THE Build_System SHALL 以 `JAVA_HOME=corretto-21.0.5`（JDK21）执行编译与测试。
2. WHEN 迁移全部完成，THE Build_System SHALL 使约 535 个基线测试全部通过。
3. WHEN 任一迁移提交点完成，THE Build_System SHALL 使 `mvn -o compile` 通过并使相关域测试通过。

### Requirement 13: Schema 一致性保障

**User Story:** 作为后端维护者，我希望在 JPA 启动校验消失后仍能保障 PO 与表结构一致，以便字段错配能被及时拦截。

#### Acceptance Criteria

1. THE Persistence_Layer SHALL 以 Flyway `db/migration` 脚本作为表结构唯一权威，迁移不改动任何 schema。
2. THE Persistence_Layer SHALL 提供连接真实/测试 PostgreSQL 的集成测试（`*IT`），覆盖 CRUD、`@TableLogic` 软删除过滤与写入量纲、分页方言、原子自增/CAS 与 PG 保留字列转义。
3. IF PO 字段或列名与库表错配，THEN THE 集成测试或往返测试 SHALL 在 CI 中暴露该错配。

### Requirement 14: JdbcTemplate 依赖留存与连接池约束

**User Story:** 作为后端维护者，我希望移除 JPA starter 后原子适配器仍可运行且连接池约束不变，以便生产稳定性不受迁移影响。

#### Acceptance Criteria

1. WHEN 移除 JPA starter 后仍保留 JdbcTemplate 实现，THE Build_System SHALL 确保 `spring-boot-starter-jdbc` 在依赖树中。
2. THE Persistence_Layer SHALL 复用现有 HikariCP 连接池（`max-pool-size=5`），不引入第二个连接池。
3. THE Persistence_Layer SHALL 保持单事务单连接模型，不增加每请求连接占用。

### Requirement 15: 动态 SQL 注入防护

**User Story:** 作为安全维护者，我希望动态 SQL 仅通过占位符传参，以便防止 SQL 注入。

#### Acceptance Criteria

1. THE Persistence_Layer SHALL 通过 wrapper 占位符（`eq` / `like` / `apply({0})`）传递所有动态值。
2. WHERE 使用 `apply` 或 `last` 拼接，THE Persistence_Layer SHALL 仅拼接已 clamp 为可信范围的整数（如 `LIMIT n`），而不拼接用户输入。
