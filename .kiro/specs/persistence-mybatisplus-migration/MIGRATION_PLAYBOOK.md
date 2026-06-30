# MyBatis-Plus 迁移 Playbook（每个域 agent 必读）

本文件是 JPA→MyBatis-Plus 单域迁移的统一操作规范。样板域 `billing/BalanceTransaction` 已迁完并固化范式，**严格照抄其结构**。你只负责被分配的那一个域，只改该域 `persistence/` 文件夹下的文件。

## 绝对禁止触碰
- `domain/` `application/` `interfaces/` 三层（任何文件都不动）。
- `Jdbc*` 原子适配器（`JdbcUserQuotaAccount`、`JdbcAffiliateAccountRepository`）——保留 JdbcTemplate，不迁。
- 共享构件 `infrastructure/persistence/PageQueries.java`、`MybatisPlusConfig.java`（已就绪，勿改）。
- 不改任何测试文件。

## 锁定的样板（billing/BalanceTransaction，照此结构）

### PO（双注解并存：JPA 原注解全保留 + 新增 MyBatis-Plus 注解 + 就近工厂方法）
```java
@Entity(name = "BillingBalanceTransactionJpaEntity")           // 原 JPA 注解保留
@Table(name = "balance_transactions", indexes = {...})         // 原 JPA 注解保留
@TableName("balance_transactions")                             // 新增：表名（取自 @Table name）
public class BalanceTransactionPO {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)     // 原 JPA 保留
    @TableId(type = IdType.AUTO)                                // 新增：自增主键
    private Long id;

    @Column(name = "user_id") @TableField("user_id")           // 原 @Column 保留 + 新增 @TableField
    private Long userId;
    // ... 每个非主键列都加 @TableField("<exact_column_name>")，列名取自原 @Column(name=...)

    public BalanceTransactionPO() {}                            // 无参构造保留

    // getter/setter 全保留

    // ---- 就近工厂方法：把 RepositoryImpl 里的私有 toEntity/toDomain 逻辑搬到这里 ----
    public BalanceTransaction toDomain() { /* 原 toDomain 逻辑，含 null→0L 兜底、枚举 fromWire */ }
    public static BalanceTransactionPO of(BalanceTransaction t) { /* 原 toEntity 逻辑 */ }
}
```

### Mapper（public 接口，放 persistence/ 包，取代 SpringData*JpaRepository）
```java
public interface BalanceTransactionMapper extends BaseMapper<BalanceTransactionPO> {
    // 基础 CRUD 由 BaseMapper 提供。复杂 SQL（CAS/聚合/软删写）用 @Update/@Select 注解方法声明在这里。
}
```

### RepositoryImpl（注入 Mapper，不再注入 SpringData 接口；删私有 toEntity/toDomain）
```java
@Repository
public class BalanceTransactionRepositoryImpl implements BalanceTransactionRepository {
    private final BalanceTransactionMapper mapper;
    public BalanceTransactionRepositoryImpl(BalanceTransactionMapper mapper) { this.mapper = mapper; }

    @Override public BalanceTransaction save(BalanceTransaction tx) {
        BalanceTransactionPO po = BalanceTransactionPO.of(tx);
        mapper.insert(po);                 // 回填自增 id
        tx.assignId(po.getId());
        return po.toDomain();
    }
    @Override public List<BalanceTransaction> findByUser(long userId, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        LambdaQueryWrapper<BalanceTransactionPO> w = Wrappers.<BalanceTransactionPO>lambdaQuery()
                .eq(BalanceTransactionPO::getUserId, userId)
                .orderByDesc(BalanceTransactionPO::getCreatedTime)
                .last("LIMIT " + safeLimit);   // safeLimit 已 clamp，无注入
        return mapper.selectList(w).stream().map(BalanceTransactionPO::toDomain).toList();
    }
}
```

## 收尾
- **删除** 该域的 `SpringData*JpaRepository.java`（或 `SpringData*Repository.java`）文件。
- 清理 RepositoryImpl 里不再使用的 import（Spring Data `Pageable/Page/PageRequest/Sort`、`@Query`、`@Param` 等）。
- 保留 RepositoryImpl 上的异常翻译（如捕获 `DataIntegrityViolationException` → 领域异常），原样照搬，MyBatis-Plus 同样抛 Spring `DataIntegrityViolationException`。

## 软删除域（PO 上有 `@SQLRestriction("deleted_at IS NULL")`）——固定方案

**方案已锁定（不要改用 delval 表达式，本机无 DB 无法实测，统一用安全回退）：**

PO 上 `deleted_at` 字段加 `@TableLogic(value = "null")`（只控制 select 自动追加 `deleted_at IS NULL` 过滤），**保留** 原 `@SQLRestriction`（并存期 JPA 仍需它）：
```java
@Column(name = "deleted_at") @TableField("deleted_at")
@TableLogic(value = "null")    // 新增：未删条件 deleted_at IS NULL，select 自动过滤
private Long deletedAt;
```
> 注意：`@TableLogic(value="null")` 不声明 delval，故 MyBatis-Plus 的 `deleteById`/`delete(wrapper)` 行为不可靠（默认 delval 是 0/1，与本项目 epoch 秒不符）。因此**软删除写操作一律用 Mapper 显式 `@Update`**，不用 `deleteById`。

软删除写（取代原 `@Modifying UPDATE ... SET deleted_at`），声明在 Mapper：
```java
@Update("UPDATE <table> SET deleted_at = #{deletedAt} WHERE id = #{id} AND deleted_at IS NULL")
int softDeleteById(@Param("id") long id, @Param("deletedAt") long deletedAt);
```
RepositoryImpl 调用 `mapper.softDeleteById(id, Instant.now().getEpochSecond())`，与原 JPA 行为 1:1。

## 派生查询 / JPQL → MyBatis-Plus 翻译对照
- `findByX(x)` → `mapper.selectOne(lqw.eq(PO::getX, x))`（返回 Optional 的：`Optional.ofNullable(selectOne(...))`）
- `findByXOrderByYDesc` → `lqw.eq(...).orderByDesc(PO::getY)` + `selectList`
- `existsByX` → `mapper.selectCount(lqw.eq(PO::getX, x)) > 0`
- `countByX` → `mapper.selectCount(lqw.eq(...))`（返回 Long，按需转 long）
- `findByXIn(list)` → `lqw.in(PO::getX, list)`
- `deleteByX`（@Modifying，非软删表）→ `mapper.delete(lqw.eq(PO::getX, x))`（物理删，原表无软删才用）
- `@Query JPQL SELECT ... WHERE (:p IS NULL OR col = :p)`（动态可选过滤）→ wrapper 条件入参版 `.eq(p != null, PO::getCol, p)`
- `@Query JPQL ... ORDER BY a DESC, b ASC` → `.orderByDesc(PO::getA).orderByAsc(PO::getB)`
- `@Query 聚合 SELECT COALESCE(SUM(c.x),0) ...` → 用 Mapper `@Select("SELECT COALESCE(SUM(x),0) FROM <t> WHERE ...")` 注解方法，返回 long
- `@Query 大小写不敏感 LIKE`（username/email/group）→ `.apply("LOWER(<col>) LIKE LOWER({0})", kw)`，`{0}` 占位防注入；PG 保留字列（"group"/"key"）在 apply 字符串里写 `"group"` 双引号转义
- 分页 `@Query + Pageable` → `Page<PO> page = PageQueries.mpOf(page1Based, pageSize); mapper.selectPage(page, wrapper);` 然后 `page.getRecords()` / `page.getTotal()`
- 派生 `Page<PO> findAllBy(Pageable)` → 同上 `selectPage(page, Wrappers.lambdaQuery())`
- merge 式 `save`（String 主键 upsert，如 ops/Option）→ 先 `selectById` 判断，存在 `updateById` 否则 `insert`；或保留语义用 `mapper.insertOrUpdate(po)`（MP 3.5.x BaseMapper 无此方法时走判断分支）

## PG 保留字列
原 `@Column(name = "\"group\"")` / `@Column(name = "\"key\"")` → `@TableField("\"group\"")` / `@TableField(value = "\"key\"")` 双引号转义照搬。

## jsonb 列（关键！原 PO 上有 `@JdbcTypeCode(SqlTypes.JSON)`）——必须用 TypeHandler
Hibernate 自动把 String↔jsonb 互转；**MyBatis-Plus 不会**，直接绑 String 会运行期抛
`column is of type jsonb but expression is of type character varying`。已建共享 Handler
`infrastructure/persistence/JsonbStringTypeHandler`。处理方式：
1. 该 PO 类上 `@TableName(value = "<table>", autoResultMap = true)`（autoResultMap 让读取也走 typeHandler）。
2. 每个 jsonb 字段：`@TableField(value = "<col>", typeHandler = JsonbStringTypeHandler.class)`，**保留**原 `@JdbcTypeCode(SqlTypes.JSON)`（并存期 JPA 仍需）。
涉及 jsonb 的 PO：account/AccountPO(credentials,model_mapping)、modelgroup/ModelGroupPO(models)、token/TokenPO(model_limits,endpoint_limits)、log/LogReadPO(other)、relay/LogPO(other)、task/TaskPO(progress?,data,private_data 共3个jsonb)、routing/AffinityRulePO(key_sources,pass_headers)。

## PG 保留字列

## 主键非自增的特殊情形
- String 主键（ops/Option，key）：`@TableId(value = "\"key\"", type = IdType.INPUT)`。
- 固定 Integer 主键单行哨兵（ops/Setup，id 由领域指定）：`@TableId(value = "id", type = IdType.INPUT)`。
- INPUT 表示主键由调用方提供，MyBatis-Plus 不自增。

