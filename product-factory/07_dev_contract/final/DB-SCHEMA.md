# DB-SCHEMA — JPA Entity 开发契约（S7，Java/JPA + PostgreSQL 单库修订版）

> 项目：Nexa（AI API 网关 SaaS，**从零自研**，Java 21+ / Spring Boot 3.2+ / JPA(Hibernate 6) / **PostgreSQL 单库**）。
> 唯一上游权威：`03_flow_prd/final/DATA-MODEL.md`。本文件**逐字段对齐 DATA-MODEL**，不自创字段；DATA-MODEL 没写的，绝不补。
> **本版为 Java/JPA + PG 单库修订版**（S2.5 技术栈冻结 2026-06-20，后端从 Go 改 Java）。原 GORM + 三库兼容版已废弃（见 git 历史）。**表结构逻辑与原版完全一致——仅 ORM 表达层从 GORM struct 换成 JPA Entity，字段/索引/约束/关系一个不变。**

---

## 0. 翻译范围与表清单（与 DATA-MODEL 逐一对齐）

DATA-MODEL 共 19 个 `##` 编号小节，其中 §8 Subscription 内含 3 张子表、§12 TwoFA 内含 2 张子表，因此**物理表共计 22 张**：

| # | DATA-MODEL 小节 | 物理表 | 表名 | 新/复用 | DDD 核心域 |
|---|---|---|---|---|---|
| 1 | §1 User | User | `users` | 复用 | 账号(轻量) |
| 2 | §2 Token | Token | `tokens` | 复用（+2 可选新列） | 账号(轻量) |
| 3 | §3 Channel | Channel | `channels` | 复用 | **选渠路由(战术完整)** |
| 4 | §4 Ability | Ability | `abilities` | 复用 | **选渠路由(战术完整)** |
| 5 | §5 Log | Log | `logs` | 复用（+10 新列） | **对账(战术完整)** |
| 6 | §6 Redemption | Redemption | `redemptions` | 复用 | **计费(战术完整)** |
| 7 | §7 TopUp | TopUp | `top_ups` | 复用 | **计费(战术完整)** |
| 8 | §8.1 SubscriptionPlan | SubscriptionPlan | `subscription_plans` | 复用 | **计费(战术完整)** |
| 9 | §8.2 SubscriptionOrder | SubscriptionOrder | `subscription_orders` | 复用 | **计费(战术完整)** |
| 10 | §8.3 UserSubscription | UserSubscription | `user_subscriptions` | 复用 | **计费(战术完整)** |
| 11 | §9 Task | Task | `tasks` | 复用 | **异步任务(战术完整)** |
| 12 | §10 Checkin | Checkin | `checkins` | 复用 | 增长(轻量) |
| 13 | §11 UserOAuthBinding | UserOAuthBinding | `user_oauth_bindings` | 复用 | 账号(轻量) |
| 14 | §12 TwoFA | TwoFA | `two_fas` | 复用 | 账号(轻量) |
| 15 | §12 TwoFABackupCode | TwoFABackupCode | `two_fa_backup_codes` | 复用 | 账号(轻量) |
| 16 | §13 PasskeyCredential | PasskeyCredential | `passkey_credentials` | 复用 | 账号(轻量) |
| 17 | §14 PrefillGroup | PrefillGroup | `prefill_groups` | 复用 | 配置(轻量) |
| 18 | §15 Option | Option | `options` | 复用 | 配置(轻量) |
| 19 | §16 PublicModel | PublicModel | `public_models` | **本轮新增** | **选渠路由(战术完整)** |
| 20 | §17 PlatformModelMapping | PlatformModelMapping | `platform_model_mappings` | **本轮新增** | **选渠路由(战术完整)** |
| 21 | §18 UserModelAlias | UserModelAlias | `user_model_aliases` | **本轮新增** | 配置(轻量) |
| 22 | §19 ChannelModelCost | ChannelModelCost | `channel_model_costs` | **本轮新增** | **对账(战术完整)** |

本轮新增 4 张表（# 19–22），扩展列：Log +10、Token +2（可选）。

> **DDD 核心域标注说明**：标"战术完整"的表属于 5 个核心子域（Relay转发/计费/选渠路由/异步任务/对账），S8 这些表的 JPA Entity 会被领域层用充血聚合根/值对象包装（金额=值对象），DB Entity 仅是基础设施层持久化映射。标"轻量"的表外围 CRUD，轻量 DDD 即可。

**Java/JPA + PostgreSQL 单库通用惯例（贯穿全文，替代原三库兼容惯例）：**
- 主键自增交 JPA：`@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`（PG 用 BIGSERIAL，由 Hibernate 生成）。
- **JSON 字段用 PostgreSQL JSONB**（不再用 TEXT）：`@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`，配合 `Map`/DTO 映射；需要 GIN 索引时显式建。
- **金额/配额/比率一律 `BigDecimal`（PG NUMERIC）**，禁 double/float——精度安全。原 GORM 版用 int 存配额(quota)的，保持 Long（配额是整数额度单位），但价格倍率/利润等小数一律 BigDecimal。
- 布尔字段：Java `Boolean` → PG `boolean`，无需常量 hack（PG 原生支持）。
- 软删除：Hibernate 6.4+ `@SoftDelete(columnName = "deleted", strategy = SoftDeleteType.DELETED)`，或保留 `deletedAt` 时间戳 + `@SQLRestriction("deleted_at IS NULL")`（本契约统一用后者保留时间戳语义，对齐原 gorm.DeletedAt）。
- 时间字段：epoch 整数时间戳保留 `Long`（对齐原 int64，避免语义漂移）；JPA 审计时间用 `Instant`。
- 保留字列名（`group`/`key`）：`@Column(name = "\"group\"")` 双引号转义（PG 标识符）。
- 大表（`logs`）建议**按时间范围分区**（PG 原生分区表），高频查询列建**部分索引/复合索引**。
- 实体放基础设施层 `infrastructure/persistence/entity/`，与领域模型分离（DDD）。

---

## 1. User（用户）— 表 `users`

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_username", columnList = "username", unique = true),
    @Index(name = "idx_users_access_token", columnList = "access_token", unique = true),
    @Index(name = "idx_users_aff_code", columnList = "aff_code", unique = true),
    @Index(name = "idx_users_display_name", columnList = "display_name"),
    @Index(name = "idx_users_email", columnList = "email"),
    @Index(name = "idx_users_github_id", columnList = "github_id"),
    @Index(name = "idx_users_discord_id", columnList = "discord_id"),
    @Index(name = "idx_users_oidc_id", columnList = "oidc_id"),
    @Index(name = "idx_users_wechat_id", columnList = "wechat_id"),
    @Index(name = "idx_users_telegram_id", columnList = "telegram_id"),
    @Index(name = "idx_users_linux_do_id", columnList = "linux_do_id"),
    @Index(name = "idx_users_inviter_id", columnList = "inviter_id"),
    @Index(name = "idx_users_stripe_customer", columnList = "stripe_customer"),
    @Index(name = "idx_users_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String username;                       // 唯一+索引，max=20

    @Column(nullable = false, length = 20)
    private String password;                       // not null，min=8/max=20

    @Transient
    private String originalPassword;               // gorm:"-:all" 仅改密校验，不落库

    @Column(name = "display_name", length = 20)
    private String displayName;                    // 索引，max=20

    @Column(columnDefinition = "integer default 1")
    private Integer role;                           // default 1

    @Column(columnDefinition = "integer default 1")
    private Integer status;                         // default 1

    @Column(length = 50)
    private String email;                          // 索引，max=50

    @Column(name = "github_id")
    private String githubId;                       // 索引

    @Column(name = "discord_id")
    private String discordId;                      // 索引

    @Column(name = "oidc_id")
    private String oidcId;                          // 索引

    @Column(name = "wechat_id")
    private String wechatId;                        // 索引

    @Column(name = "telegram_id")
    private String telegramId;                      // 索引

    @Column(name = "linux_do_id")
    private String linuxDoId;                       // 索引

    @Column(name = "access_token", columnDefinition = "char(32)", unique = true)
    private String accessToken;                     // *string，nullable，唯一索引

    @Column(columnDefinition = "integer default 0")
    private Integer quota;                          // 配额=整数额度，Long/Integer 保整数；default 0

    @Column(name = "used_quota", columnDefinition = "integer default 0")
    private Integer usedQuota;                       // 配额整数，default 0

    @Column(name = "request_count", columnDefinition = "integer default 0")
    private Integer requestCount;                    // default 0

    @Column(name = "\"group\"", columnDefinition = "varchar(64) default 'default'")
    private String group;                            // 保留字转义；default 'default'

    @Column(name = "aff_code", columnDefinition = "varchar(32)", unique = true)
    private String affCode;                          // 唯一索引

    @Column(name = "aff_count", columnDefinition = "integer default 0")
    private Integer affCount;                         // default 0

    @Column(name = "aff_quota", columnDefinition = "integer default 0")
    private Integer affQuota;                          // 配额整数，default 0

    @Column(name = "aff_history", columnDefinition = "integer default 0")
    private Integer affHistoryQuota;                   // 落库列名 aff_history（gorm column 重映射）

    @Column(name = "inviter_id")
    private Integer inviterId;                          // 索引，自关联本表

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String setting;                            // 原 text 存 JSON(dto.UserSetting)→JSONB

    @Column(columnDefinition = "varchar(255)")
    private String remark;                             // max=255

    @Column(name = "stripe_customer", columnDefinition = "varchar(64)")
    private String stripeCustomer;                      // 索引

    @Column(name = "created_at")
    private Long createdAt;                             // epoch 时间戳，autoCreateTime

    @Column(name = "last_login_at", columnDefinition = "bigint default 0")
    private Long lastLoginAt;                            // epoch 时间戳，default 0

    @Column(name = "deleted_at")
    private Long deletedAt;                              // 软删除：配合 @SQLRestriction

    @Transient
    private String verificationCode;                    // gorm:"-:all" 仅邮箱验证，不落库
}
```

- 枚举：Role `common=普通(默认)/admin/root`（护栏：不可操作目标角色 ≥ 操作者角色）；Status `1=启用`，≠1=禁用。
- 索引：唯一 `username`、`access_token(char32)`、`aff_code(varchar32)`；普通 `display_name`、`email`、`github_id`、`discord_id`、`oidc_id`、`wechat_id`、`telegram_id`、`linux_do_id`、`inviter_id`、`stripe_customer`；软删除 `deleted_at`。
- PG 注意：`setting` 用 JSONB（JSON 序列化 dto.UserSetting）；`affHistoryQuota` 落库列名 `aff_history`（用 `@Column(name=...)` 重映射）；`password`/`originalPassword`/`verificationCode` 含敏感语义，后两者 `@Transient` 不落库。
- 关联：被 Token/Channel/Log/Redemption/TopUp/UserSubscription/Checkin/UserOAuthBinding/TwoFA/PasskeyCredential/UserModelAlias(scope_id) 逻辑外键 `user_id` 引用；`InviterId` 自关联本表。

---

## 2. Token（API 令牌）— 表 `tokens`

```java
@Entity
@Table(name = "tokens", indexes = {
    @Index(name = "idx_tokens_key", columnList = "\"key\"", unique = true),
    @Index(name = "idx_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_tokens_name", columnList = "name"),
    @Index(name = "idx_tokens_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                          // 索引，→User

    @Column(name = "\"key\"", columnDefinition = "varchar(128)", unique = true)
    private String key;                              // 保留字转义；唯一索引

    @Column(columnDefinition = "integer default 1")
    private Integer status;                           // default 1

    @Column
    private String name;                             // 索引，≤50

    @Column(name = "created_time", columnDefinition = "bigint")
    private Long createdTime;                          // epoch 时间戳

    @Column(name = "accessed_time", columnDefinition = "bigint")
    private Long accessedTime;                          // epoch 时间戳

    @Column(name = "expired_time", columnDefinition = "bigint default -1")
    private Long expiredTime;                            // epoch 时间戳，default -1

    @Column(name = "remain_quota", columnDefinition = "integer default 0")
    private Integer remainQuota;                          // 配额整数，default 0

    @Column(name = "unlimited_quota")
    private Boolean unlimitedQuota;

    @Column(name = "model_limits_enabled")
    private Boolean modelLimitsEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_limits", columnDefinition = "jsonb")
    private String modelLimits;                           // 原 text 存 JSON→JSONB

    @Column(name = "allow_ips", columnDefinition = "text default ''")
    private String allowIps;                              // *string，nullable，default ''

    @Column(name = "used_quota", columnDefinition = "integer default 0")
    private Integer usedQuota;                              // 配额整数，default 0

    @Column(name = "\"group\"", columnDefinition = "varchar(255) default ''")
    private String group;                                  // 保留字转义；default ''

    @Column(name = "cross_group_retry")
    private Boolean crossGroupRetry;

    // ↓ 本轮可选新增 2 列（DATA-MODEL「Token 端点级减法约束扩展」）
    @Column(name = "endpoint_limits_enabled", columnDefinition = "boolean default false")
    private Boolean endpointLimitsEnabled;                  // default false

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoint_limits", columnDefinition = "jsonb default '\"\"'::jsonb")
    private String endpointLimits;                          // 原 text 存 JSON→JSONB

    @Column(name = "deleted_at")
    private Long deletedAt;                                 // 软删除：配合 @SQLRestriction
}
```

- 枚举：Status `1=启用` / 禁用；派生态：已过期（`ExpiredTime<=now 且 ≠-1`）、额度耗尽、已删除。
- 校验：`Name`≤50（`MsgTokenNameTooLong`）；`RemainQuota` ∈ [0, `10亿*QuotaPerUnit`]（`maxQuotaValue`）。
- 索引：唯一 `key(varchar128)`；普通 `user_id`、`name`；软删除 `deleted_at`。
- PG 注意：`modelLimits`、`endpointLimits` 用 JSONB 存 JSON（`GetModelLimitsMap()`、入站协议集 `["openai","claude"]`）；`allowIps` 为 nullable `String` default ''，按 `\n` 切分；`endpointLimitsEnabled` 给 default false，`endpointLimits` 给 default '' 避存量 NULL。
- 关联：`UserId` → User。
- 语义提示（不改字段）：`ModelLimits` 语义由「加法授权」收窄为「减法约束」（模型全开背景）；`EndpointLimits` 在 TokenAuth 后、L1 前校验，纯减法自我约束，非权限闸门。

---

## 3. Channel（上游渠道）— 表 `channels`

```java
@Entity
@Table(name = "channels", indexes = {
    @Index(name = "idx_channels_name", columnList = "name"),
    @Index(name = "idx_channels_tag", columnList = "tag")
})
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "integer default 0")
    private Integer type;                             // default 0

    @Column(name = "\"key\"", nullable = false)
    private String key;                              // 保留字转义；not null

    @Column(columnDefinition = "integer default 1")
    private Integer status;                           // default 1

    @Column
    private String name;                             // 索引

    @Column(columnDefinition = "integer default 0")
    private Integer weight;                           // *uint，nullable，default 0

    @Column(name = "base_url", columnDefinition = "text default ''")
    private String baseURL;                           // *string，nullable，default ''

    @Column
    private String models;

    @Column(name = "\"group\"", columnDefinition = "varchar(64) default 'default'")
    private String group;                             // 保留字转义；default 'default'

    @Column(columnDefinition = "bigint default 0")
    private Long priority;                            // *int64，nullable，default 0

    @Column(name = "auto_ban", columnDefinition = "integer default 1")
    private Integer autoBan;                          // *int，nullable，default 1

    @Column(precision = 30, scale = 6)
    private BigDecimal balance;                       // 金额→BigDecimal（原 float64）

    @Column(name = "used_quota", columnDefinition = "bigint default 0")
    private Long usedQuota;                           // 配额整数，default 0

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(name = "test_time", columnDefinition = "bigint")
    private Long testTime;                            // epoch 时间戳

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_mapping", columnDefinition = "jsonb")
    private String modelMapping;                      // *string，原 text 存 JSON→JSONB

    @Column(name = "status_code_mapping", columnDefinition = "varchar(1024) default ''")
    private String statusCodeMapping;                 // *string，default ''

    @Column
    private String tag;                              // *string，nullable，索引

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String setting;                           // *string，原 text 存 JSON→JSONB

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_info", columnDefinition = "jsonb")
    private ChannelInfo channelInfo;                  // 原 text+serializer:json 承载 JSON→JSONB（嵌入对象）

    @Column(name = "created_time", columnDefinition = "bigint")
    private Long createdTime;                          // epoch 时间戳
}

// 嵌入 JSON 对象（非独立表），映射为 channel_info JSONB
public class ChannelInfo {
    private Boolean isMultiKey;                        // is_multi_key
    private Integer multiKeySize;                      // multi_key_size
    private Map<Integer, Integer> multiKeyStatusList;  // multi_key_status_list
    private Map<Integer, String> multiKeyDisabledReason; // multi_key_disabled_reason
    private Integer multiKeyPollingIndex;              // multi_key_polling_index
    private MultiKeyMode multiKeyMode;                 // multi_key_mode（枚举）
}
```

- 索引：普通 `name`、`tag`；软删除：DATA-MODEL 未列出 Channel 的 `DeletedAt`，不补（保持原表）。
- PG 注意：`modelMapping`、`setting` 用 JSONB；`channelInfo` 用 JSONB 承载 JSON（DATA-MODEL 标 `json`，PG 用 JSONB + `@JdbcTypeCode(SqlTypes.JSON)` 落地嵌入对象）；`statusCodeMapping` `varchar(1024) default ''`。
- 关联：被 Ability(`channel_id`)、Log(`channel`)、Task(`channel_id`)、ChannelModelCost(`channel_id`) 逻辑外键引用。

> [GAP: DATA-MODEL §3 未标注 Channel.DeletedAt 软删除字段，本契约按 DATA-MODEL 不补软删除；若现网 Channel 实际有软删除，请架构师回查确认是否补列。]

---

## 4. Ability（分组×模型→渠道路由能力）— 表 `abilities`

```java
@Entity
@Table(name = "abilities", indexes = {
    @Index(name = "idx_abilities_channel_id", columnList = "channel_id"),
    @Index(name = "idx_abilities_priority", columnList = "priority"),
    @Index(name = "idx_abilities_weight", columnList = "weight"),
    @Index(name = "idx_abilities_tag", columnList = "tag")
})
@IdClass(AbilityId.class)
public class Ability {

    @Id
    @Column(name = "\"group\"", columnDefinition = "varchar(64)")
    private String group;                             // 复合主键之一，保留字转义

    @Id
    @Column(columnDefinition = "varchar(255)")
    private String model;                             // 复合主键之一

    @Id
    @Column(name = "channel_id")
    private Integer channelId;                         // 复合主键之一，索引，→Channel

    @Column
    private Boolean enabled;                           // 选渠只取 enabled=true

    @Column(columnDefinition = "bigint default 0")
    private Long priority;                             // *int64，nullable，default 0，索引

    @Column(columnDefinition = "integer default 0")
    private Integer weight;                            // uint，default 0，索引

    @Column
    private String tag;                               // *string，nullable，索引
}

// 复合主键类 (group, model, channel_id)
public class AbilityId implements Serializable {
    private String group;
    private String model;
    private Integer channelId;
}

// 派生视图（运行期 join channels.type，非独立表）
public class AbilityWithChannel extends Ability {
    private Integer channelType;                       // channel_type
}
```

- 复合主键：`(group, model, channel_id)`。索引：`channel_id`、`priority`、`weight`、`tag`。
- 选渠只取 `enabled=true`。
- 关联：`channel_id` → Channel；`group`/`model` 逻辑关联路由。`AbilityWithChannel` 为运行期 join 视图，不建表。

---

## 5. Log（用量/操作日志）— 表 `logs`

```java
@Entity
@Table(name = "logs", indexes = {
    @Index(name = "idx_created_at_id", columnList = "created_at, id"),
    @Index(name = "idx_user_id_id", columnList = "user_id, id"),
    @Index(name = "idx_logs_username", columnList = "username"),
    @Index(name = "idx_logs_token_name", columnList = "token_name"),
    @Index(name = "idx_logs_model_name", columnList = "model_name"),
    @Index(name = "idx_logs_channel", columnList = "channel"),
    @Index(name = "idx_logs_token_id", columnList = "token_id"),
    @Index(name = "idx_logs_group", columnList = "\"group\""),
    @Index(name = "idx_logs_ip", columnList = "ip"),
    @Index(name = "idx_logs_requested_model", columnList = "requested_model"),
    @Index(name = "idx_logs_resolved_public_model", columnList = "resolved_public_model"),
    @Index(name = "idx_logs_actual_upstream_model", columnList = "actual_upstream_model")
})
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                            // 索引，→User

    @Column(name = "created_at", columnDefinition = "bigint")
    private Long createdAt;                            // epoch 时间戳

    @Column
    private Integer type;                              // Log Type 枚举

    @Column
    private String content;

    @Column(columnDefinition = "varchar(255) default ''")
    private String username;                           // 索引，default ''

    @Column(name = "token_name", columnDefinition = "varchar(255) default ''")
    private String tokenName;                          // 索引，default ''

    @Column(name = "model_name", columnDefinition = "varchar(255) default ''")
    private String modelName;                          // 索引，default ''；= requested_model(C)

    @Column(columnDefinition = "integer default 0")
    private Integer quota;                             // 配额整数，default 0；= quota_sell 口径

    @Column(name = "prompt_tokens", columnDefinition = "integer default 0")
    private Integer promptTokens;                      // default 0

    @Column(name = "completion_tokens", columnDefinition = "integer default 0")
    private Integer completionTokens;                  // default 0

    @Column(name = "use_time", columnDefinition = "integer default 0")
    private Integer useTime;                           // default 0

    @Column(name = "is_stream")
    private Boolean isStream;

    @Column(name = "channel")
    private Integer channelId;                         // json:"channel"，落库列名 channel，索引，→Channel

    @Column(name = "channel_name", insertable = false, updatable = false)
    private String channelName;                        // gorm:"->" 只读 join 列，不写物理列

    @Column(name = "token_id", columnDefinition = "integer default 0")
    private Integer tokenId;                           // default 0，索引，→Token

    @Column(name = "\"group\"")
    private String group;                              // 保留字转义；索引

    @Column(columnDefinition = "varchar(255) default ''")
    private String ip;                                 // 索引，default ''

    @Column(name = "request_id", columnDefinition = "varchar(64)")
    private String requestId;

    @Column(name = "upstream_request_id", columnDefinition = "varchar(128)")
    private String upstreamRequestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String other;                              // 附加 JSON（原 text→JSONB）

    // ↓ 本轮新增 10 列（三段模型 + 协议 + UA + 售价/成本/利润）
    @Column(name = "requested_model", columnDefinition = "varchar(255) default ''")
    private String requestedModel;                     // C 客户输入名（客户可见），索引

    @Column(name = "resolved_public_model", columnDefinition = "varchar(255) default ''")
    private String resolvedPublicModel;                // A 平台公开名（客户可见），索引

    @Column(name = "actual_upstream_model", columnDefinition = "varchar(255) default ''")
    private String actualUpstreamModel;                // B 真实上游名（仅 admin/root），索引

    @Column(name = "inbound_protocol", columnDefinition = "varchar(32) default ''")
    private String inboundProtocol;                    // 入站协议

    @Column(name = "upstream_protocol", columnDefinition = "varchar(32) default ''")
    private String upstreamProtocol;                   // 目标供应商协议

    @Column(name = "protocol_converted", columnDefinition = "boolean default false")
    private Boolean protocolConverted;                 // 是否头尾协议转换

    @Column(name = "user_agent", columnDefinition = "varchar(512) default ''")
    private String userAgent;                          // 调用方 UA

    @Column(name = "quota_sell", columnDefinition = "integer default 0")
    private Integer quotaSell;                          // 本笔售价（客户可见），配额整数口径，default 0

    @Column(name = "quota_cost", columnDefinition = "integer default 0")
    private Integer quotaCost;                          // 本笔成本（仅 admin/root），配额整数口径，default 0

    @Column(name = "quota_profit", columnDefinition = "integer default 0")
    private Integer quotaProfit;                        // 本笔利润=sell-cost（仅 admin/root），配额整数口径，default 0
}
```

- 复合索引（DATA-MODEL §5 Id 行标注）：`idx_created_at_id`（`created_at, id`）、`idx_user_id_id`（`user_id, id`）。JPA 用 class 上 `@Table(indexes=...)` 声明复合索引，列顺序即 `created_at,id` / `user_id,id`。
- 枚举 Log Type（int，值固定）：`0=Unknown / 1=Topup / 2=Consume / 3=Manage / 4=System / 5=Error / 6=Refund / 7=Login`。
- 索引：普通 `user_id`、`username`、`token_name`、`model_name`、`channel`、`token_id`、`group`、`ip`，新增 `requested_model`、`resolved_public_model`、`actual_upstream_model`。
- PG 注意：`other` 存 JSON 用 JSONB；新增 10 列全部带 `default`（字符串 `''`、bool `false`、int `0`）避存量 NULL；`channelName` 为只读 join 列（`@Column(insertable=false, updatable=false)`），不落物理列。
- 字段口径（DATA-MODEL §5 落 DECISIONS）：
  - `model_name` 取值 = `requested_model`(C)，保留现网报表语义；渠道级 L3 重定向名 B' 不单设列，写入 `Other` JSON 的 `{"channel_redirect":"B'"}` 仅诊断。
  - `quota_sell` 与 `Quota` 同口径（= BasePriceRatio(A)×GroupRatio×tokens）；`quota_cost`=tokens×CostRatio(channel,B) 不乘折扣；`quota_profit`=`quota_sell`−`quota_cost`（可为负=亏损告警）。
  - 成本行缺失：`quota_cost=0`、`quota_profit=quota_sell`，`Other` 写 `{"cost_missing":true,...}`。
- 视图裁剪 DTO（非表，运行期）：`UserLogView` 仅 C/A + `quota_sell`（无 B、无成本/利润）；`AdminLogView` 给全链 C→A→B + 协议 + 售价/成本/利润 + `channel_id`。
- 关联：`user_id`→User、`channel`→Channel、`token_id`→Token；`resolved_public_model`→PublicModel.public_name（A 同键，逻辑关联）。
- Log 写自有库（`LOG_DB`，DATA-MODEL/main.go `migrateLOGDB` 单独 AutoMigrate `&Log{}`），见 MIGRATION-PLAN。

---

## 6. Redemption（兑换码）— 表 `redemptions`

```java
@Entity
@Table(name = "redemptions", indexes = {
    @Index(name = "idx_redemptions_key", columnList = "\"key\"", unique = true),
    @Index(name = "idx_redemptions_name", columnList = "name"),
    @Index(name = "idx_redemptions_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                            // 创建者，→User

    @Column(name = "\"key\"", columnDefinition = "char(32)", unique = true)
    private String key;                                // 保留字转义；唯一索引

    @Column(columnDefinition = "integer default 1")
    private Integer status;                             // default 1

    @Column
    private String name;                                // 索引

    @Column(columnDefinition = "integer default 100")
    private Integer quota;                              // 配额整数，default 100

    @Column(name = "created_time", columnDefinition = "bigint")
    private Long createdTime;                            // epoch 时间戳

    @Column(name = "redeemed_time", columnDefinition = "bigint")
    private Long redeemedTime;                            // epoch 时间戳

    @Transient
    private Integer count;                                // gorm:"-:all" 仅 API 批量数量，不落库

    @Column(name = "used_user_id")
    private Integer usedUserId;                           // 核销人，→User

    @Column(name = "expired_time", columnDefinition = "bigint")
    private Long expiredTime;                             // epoch 时间戳

    @Column(name = "deleted_at")
    private Long deletedAt;                               // 软删除：配合 @SQLRestriction
}
```

- 枚举 Status：`1=未使用 / 已使用 / 已禁用`。
- 索引：唯一 `key(char32)`；普通 `name`；软删除 `deleted_at`。
- 关联：`user_id`（创建者）→User、`used_user_id`（核销人）→User。

---

## 7. TopUp（充值订单）— 表 `top_ups`

```java
@Entity
@Table(name = "top_ups", indexes = {
    @Index(name = "idx_top_ups_trade_no", columnList = "trade_no", unique = true),
    @Index(name = "idx_top_ups_user_id", columnList = "user_id")
})
public class TopUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                            // 索引，→User

    @Column
    private Long amount;                               // 配额整数额度（原 int64），Long

    @Column(precision = 30, scale = 6)
    private BigDecimal money;                          // 金额→BigDecimal（原 float64）

    @Column(name = "trade_no", columnDefinition = "varchar(255)", unique = true)
    private String tradeNo;                            // 唯一索引

    @Column(name = "payment_method", columnDefinition = "varchar(50)")
    private String paymentMethod;

    @Column(name = "payment_provider", columnDefinition = "varchar(50) default ''")
    private String paymentProvider;                    // default ''

    @Column(name = "create_time")
    private Long createTime;                           // epoch 时间戳

    @Column(name = "complete_time")
    private Long completeTime;                          // epoch 时间戳

    @Column
    private String status;                             // pending/success 等
}
```

- 枚举：PaymentMethod `stripe/creem/waffo/waffo_pancake/balance`；PaymentProvider `epay/stripe/creem/waffo/waffo_pancake/balance`；Status `pending/success 等`。
- 索引：唯一 `trade_no(varchar255)`；普通 `user_id`。
- 关联：`user_id`→User。

---

## 8. SubscriptionPlan（订阅套餐）— 表 `subscription_plans`

```java
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "varchar(128)", nullable = false)
    private String title;                              // not null

    @Column(columnDefinition = "varchar(255) default ''")
    private String subtitle;                           // default ''

    @Column(name = "price_amount", precision = 10, scale = 6, columnDefinition = "decimal(10,6) default 0")
    private BigDecimal priceAmount;                    // 金额→BigDecimal（原 float64，decimal(10,6)），default 0

    @Column(columnDefinition = "varchar(8) default 'USD'")
    private String currency;                           // default 'USD'

    @Column(name = "duration_unit", columnDefinition = "varchar(255) default 'month'")
    private String durationUnit;                       // default 'month'

    @Column(name = "duration_value", columnDefinition = "integer default 1")
    private Integer durationValue;                      // default 1

    @Column(name = "custom_seconds")
    private Long customSeconds;                          // 秒数（原 int64）

    @Column(columnDefinition = "boolean default true")
    private Boolean enabled;                             // default true

    @Column(name = "sort_order", columnDefinition = "integer default 0")
    private Integer sortOrder;                           // default 0

    @Column(name = "allow_balance_pay", columnDefinition = "boolean default true")
    private Boolean allowBalancePay;                     // *bool，nullable，default true

    @Column(name = "allow_wallet_overflow", columnDefinition = "boolean default true")
    private Boolean allowWalletOverflow;                 // *bool，nullable，default true

    @Column(name = "total_amount", columnDefinition = "bigint default 0")
    private Long totalAmount;                            // 配额整数额度（原 int64），0=无限，default 0

    @Column(name = "quota_reset_period", columnDefinition = "varchar(255) default 'never'")
    private String quotaResetPeriod;                     // default 'never'

    @Column(name = "max_purchase_per_user", columnDefinition = "integer default 0")
    private Integer maxPurchasePerUser;                  // 0=不限，default 0

    @Column(name = "upgrade_group")
    private String upgradeGroup;

    @Column(name = "downgrade_group")
    private String downgradeGroup;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(name = "creem_product_id")
    private String creemProductId;

    @Column(name = "waffo_pancake_product_id")
    private String waffoPancakeProductId;
}
```

- 枚举：DurationUnit `year/month/day/hour/custom`；QuotaResetPeriod `never/daily/weekly/monthly/custom`。`TotalAmount=0` 无限；`MaxPurchasePerUser=0` 不限。
- PG 注意：`priceAmount` 用 `BigDecimal` + `decimal(10,6)`（精度安全，禁 float/double）；PG 单库走 Hibernate DDL 生成（见 MIGRATION-PLAN）。
- 关联：被 SubscriptionOrder(`plan_id`)、UserSubscription(`plan_id`) 引用。

> [GAP: DATA-MODEL §8.1 末行 `StripePriceId / CreemProductId / WaffoPancakeProductId | string | 各支付渠道商品号` 未给单独 json tag 与约束，本契约按字段名 snake_case 推导 json tag（stripe_price_id 等），未自创额外约束；若实际列名/长度不同，请架构师回查。]

---

## 9. SubscriptionOrder（订阅下单）— 表 `subscription_orders`

```java
@Entity
@Table(name = "subscription_orders", indexes = {
    @Index(name = "idx_subscription_orders_trade_no", columnList = "trade_no", unique = true)
})
public class SubscriptionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                            // →User

    @Column(name = "plan_id")
    private Integer planId;                            // →SubscriptionPlan

    @Column(precision = 30, scale = 6)
    private BigDecimal money;                          // 金额→BigDecimal（原 float64）

    @Column(name = "trade_no", unique = true)
    private String tradeNo;                            // 唯一索引

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_provider")
    private String paymentProvider;

    @Column
    private String status;

    @Column(name = "create_time")
    private Long createTime;                           // epoch 时间戳

    @Column(name = "complete_time")
    private Long completeTime;                         // epoch 时间戳

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", columnDefinition = "jsonb")
    private String providerPayload;                    // 原 text 存 JSON→JSONB
}
```

- 索引：唯一 `trade_no`。PG 注意：`providerPayload` 用 JSONB（原 text 存 JSON，PG 用 JSONB + `@JdbcTypeCode(SqlTypes.JSON)`）。
- 关联：`user_id`→User、`plan_id`→SubscriptionPlan。

> [GAP: DATA-MODEL §8.2 以一句话内联列出字段（无类型/约束表），PaymentMethod/PaymentProvider 未给类型与枚举、UserId/PlanId 未标 index。本契约按内联顺序逐字段翻译，未自创类型长度/索引；若需与 §7 一致的 varchar 长度或 index，请架构师回查补齐。]

---

## 10. UserSubscription（用户订阅实例）— 表 `user_subscriptions`

```java
@Entity
@Table(name = "user_subscriptions", indexes = {
    @Index(name = "idx_user_subscriptions_user_id", columnList = "user_id"),
    @Index(name = "idx_user_subscriptions_plan_id", columnList = "plan_id")
})
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;                            // 索引，→User

    @Column(name = "plan_id")
    private Integer planId;                            // 索引，→SubscriptionPlan

    @Column(name = "amount_total")
    private Long amountTotal;                          // 配额整数额度（原 int64），Long

    @Column(name = "amount_used")
    private Long amountUsed;                            // 配额整数额度（原 int64），Long

    @Column(name = "start_time")
    private Long startTime;                            // epoch 时间戳

    @Column(name = "end_time")
    private Long endTime;                              // epoch 时间戳

    @Column(columnDefinition = "varchar(32)")
    private String status;                             // active/expired/cancelled

    @Column(columnDefinition = "varchar(255) default 'order'")
    private String source;                             // default 'order'，order/admin

    @Column(name = "last_reset_time")
    private Long lastResetTime;                        // epoch 时间戳

    @Column(name = "next_reset_time")
    private Long nextResetTime;                        // epoch 时间戳

    @Column(name = "upgrade_group")
    private String upgradeGroup;

    @Column(name = "prev_user_group")
    private String prevUserGroup;

    @Column(name = "downgrade_group")
    private String downgradeGroup;

    @Column(name = "allow_wallet_overflow", columnDefinition = "boolean default true")
    private Boolean allowWalletOverflow;                // default true
}
```

- 枚举：Status `active/expired/cancelled`；Source `order/admin`。
- 索引：普通 `user_id`、`plan_id`。
- 关联：`user_id`→User、`plan_id`→SubscriptionPlan。

> [GAP: DATA-MODEL §8.3 `LastResetTime / NextResetTime` 与 `UpgradeGroup / PrevUserGroup / DowngradeGroup` 以斜杠合并行列出，未给独立 json tag。本契约按字段名 snake_case 推导（last_reset_time 等），未自创额外约束。]

---

## 11. Task（异步任务）— 表 `tasks`

```java
@Entity
@Table(name = "tasks", indexes = {
    @Index(name = "idx_tasks_task_id", columnList = "task_id"),
    @Index(name = "idx_tasks_platform", columnList = "platform"),
    @Index(name = "idx_tasks_user_id", columnList = "user_id"),
    @Index(name = "idx_tasks_channel_id", columnList = "channel_id"),
    @Index(name = "idx_tasks_action", columnList = "action"),
    @Index(name = "idx_tasks_status", columnList = "status"),
    @Index(name = "idx_tasks_submit_time", columnList = "submit_time"),
    @Index(name = "idx_tasks_start_time", columnList = "start_time"),
    @Index(name = "idx_tasks_finish_time", columnList = "finish_time")
})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                   // 原 int64

    @Column(name = "task_id", columnDefinition = "varchar(191)")
    private String taskId;                             // 索引

    @Column(columnDefinition = "varchar(30)")
    private String platform;                           // 索引，原 constant.TaskPlatform（string 枚举）

    @Column(name = "user_id")
    private Integer userId;                            // 索引，→User

    @Column(name = "\"group\"", columnDefinition = "varchar(50)")
    private String group;                              // 保留字，PG 标识符双引号转义

    @Column(name = "channel_id")
    private Integer channelId;                          // 索引，→Channel

    @Column
    private Long quota;                                // 配额整数额度（原 int），Long

    @Column(columnDefinition = "varchar(40)")
    private String action;                             // 索引

    @Column(columnDefinition = "varchar(20)")
    private String status;                             // 索引，TaskStatus（string 枚举）

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "submit_time")
    private Long submitTime;                           // 索引，epoch 时间戳

    @Column(name = "start_time")
    private Long startTime;                            // 索引，epoch 时间戳

    @Column(name = "finish_time")
    private Long finishTime;                           // 索引，epoch 时间戳

    @Column(columnDefinition = "varchar(20)")
    private String progress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String properties;                         // 原 text+serializer:json→JSONB

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;                               // 原 json.RawMessage+text→JSONB

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "private_data", columnDefinition = "jsonb")
    private String privateData;                        // 含 key 等隐私，不下发；原 text+serializer:json→JSONB

    @Column(name = "created_at")
    private Long createdAt;                            // epoch 时间戳

    @Column(name = "updated_at")
    private Long updatedAt;                            // epoch 时间戳
}
```

- 枚举 TaskStatus（string）：`NOT_START/SUBMITTED/QUEUED/IN_PROGRESS/FAILURE/SUCCESS/UNKNOWN`。
- 索引：普通 `task_id(varchar191)`、`platform(varchar30)`、`user_id`、`channel_id`、`action(varchar40)`、`status(varchar20)`、`submit_time`、`start_time`、`finish_time`。
- PG 注意：`properties`/`data`/`privateData` 三个 JSON 字段 PG 用 JSONB（原 text+serializer:json）+ `@JdbcTypeCode(SqlTypes.JSON)` 落地。
- 关联：`user_id`→User、`channel_id`→Channel。

---

## 12. Checkin（签到记录）— 表 `checkins`

```java
@Entity
@Table(name = "checkins", uniqueConstraints = {
    @UniqueConstraint(name = "idx_user_checkin_date", columnNames = {"user_id", "checkin_date"})
})
public class Checkin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;                            // not null，复合唯一索引 idx_user_checkin_date，→User

    @Column(name = "checkin_date", columnDefinition = "varchar(10)", nullable = false)
    private String checkinDate;                        // not null，格式 YYYY-MM-DD，复合唯一索引 idx_user_checkin_date

    @Column(name = "quota_awarded", nullable = false)
    private Long quotaAwarded;                          // not null，配额整数额度（原 int），Long

    @Column(name = "created_at", columnDefinition = "bigint")
    private Long createdAt;                            // epoch 时间戳
}
```

- 复合唯一索引 `idx_user_checkin_date (user_id, checkin_date)`，`checkin_date` 格式 `YYYY-MM-DD`。表名固定 `checkins`。
- 脱敏返回 `CheckinRecord` 仅 `checkin_date`/`quota_awarded`（非表，DTO）。
- CheckinSetting 走 KV（`operation_setting/checkin_setting.go`，非表）：`enabled(默认false)`/`min_quota(默认1000)`/`max_quota(默认10000)`，奖励为 [min,max] 随机。
- 关联：`user_id`→User。

---

## 13. UserOAuthBinding（自定义 OAuth 绑定）— 表 `user_oauth_bindings`

```java
@Entity
@Table(name = "user_oauth_bindings", uniqueConstraints = {
    @UniqueConstraint(name = "ux_user_provider", columnNames = {"user_id", "provider_id"}),
    @UniqueConstraint(name = "ux_provider_userid", columnNames = {"provider_id", "provider_user_id"})
})
public class UserOAuthBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;                            // not null，复合唯一 ux_user_provider，→User

    @Column(name = "provider_id", nullable = false)
    private Integer providerId;                         // not null，复合唯一 ux_user_provider + ux_provider_userid，→CustomOAuthProvider

    @Column(name = "provider_user_id", columnDefinition = "varchar(256)", nullable = false)
    private String providerUserId;                      // not null，复合唯一 ux_provider_userid

    @Column(name = "created_at")
    private Instant createdAt;                           // 审计时间（原 time.Time）
}
```

- 复合唯一索引：`ux_user_provider (user_id, provider_id)`（每用户每 provider 一条）、`ux_provider_userid (provider_id, provider_user_id)`（每 provider 一账号唯一）。表名 `user_oauth_bindings`。
- 关联：`user_id`→User、`provider_id`→CustomOAuthProvider（现网表，非本批 PRD 范围）。

---

## 14. TwoFA（双因子）— 表 `two_fas`

```java
@Entity
@Table(name = "two_fas", indexes = {
    @Index(name = "idx_two_fas_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_two_fas_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class TwoFA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Integer userId;                            // 唯一+not null，→User

    @Column(columnDefinition = "varchar(255)")
    private String secret;                             // TOTP 密钥，json:"-" 不下发

    @Column(name = "is_enabled")
    private Boolean isEnabled;

    @Column(name = "failed_attempts", columnDefinition = "integer default 0")
    private Integer failedAttempts;                     // default 0

    @Column(name = "locked_until")
    private Instant lockedUntil;                        // nullable（原 *time.Time）

    @Column(name = "last_used_at")
    private Instant lastUsedAt;                         // nullable（原 *time.Time）

    @Column(name = "created_at")
    private Instant createdAt;                          // 审计时间（原 time.Time）

    @Column(name = "updated_at")
    private Instant updatedAt;                          // 审计时间（原 time.Time）

    @Column(name = "deleted_at")
    private Long deletedAt;                             // 软删除：配合 @SQLRestriction（原 gorm.DeletedAt），索引
}
```

- 索引：唯一 `user_id`；软删除 `deleted_at`。`secret` 含敏感语义，`json:"-"` 不下发。
- 关联：`user_id`→User。

---

## 15. TwoFABackupCode（双因子备用码）— 表 `two_fa_backup_codes`

```java
@Entity
@Table(name = "two_fa_backup_codes", indexes = {
    @Index(name = "idx_two_fa_backup_codes_user_id", columnList = "user_id"),
    @Index(name = "idx_two_fa_backup_codes_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class TwoFABackupCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;                            // not null，索引，→User

    @Column(name = "code_hash", columnDefinition = "varchar(255)")
    private String codeHash;                            // 备用码哈希，json:"-" 不下发

    @Column(name = "is_used")
    private Boolean isUsed;

    @Column(name = "used_at")
    private Instant usedAt;                             // nullable（原 *time.Time）

    @Column(name = "created_at")
    private Instant createdAt;                           // 审计时间（原 time.Time）

    @Column(name = "deleted_at")
    private Long deletedAt;                             // 软删除：配合 @SQLRestriction（原 gorm.DeletedAt），索引
}
```

- 索引：普通 `user_id`；软删除 `deleted_at`。`codeHash` 不下发。
- 关联：`user_id`→User。

---

## 16. PasskeyCredential（无密码凭据）— 表 `passkey_credentials`

```java
@Entity
@Table(name = "passkey_credentials", indexes = {
    @Index(name = "idx_passkey_credentials_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_passkey_credentials_credential_id", columnList = "credential_id", unique = true)
})
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Integer userId;                          // 唯一+not null，→User

    @Column(name = "credential_id", length = 512, unique = true, nullable = false)
    private String credentialId;                     // 唯一+not null，varchar(512)

    @Column(name = "public_key", columnDefinition = "text", nullable = false)
    private String publicKey;                        // not null，text（base64）

    @Column(name = "attestation_type", length = 255)
    private String attestationType;

    @Column(name = "aaguid", length = 512)
    private String aaguid;

    @Column(name = "sign_count", columnDefinition = "bigint default 0")
    private Long signCount;                           // 原 uint32，default 0

    @Column(name = "clone_warning")
    private Boolean cloneWarning;

    @Column(name = "user_present")
    private Boolean userPresent;

    @Column(name = "user_verified")
    private Boolean userVerified;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backup_state")
    private Boolean backupState;

    @Column(name = "transports", columnDefinition = "text")
    private String transports;                        // text（base64）

    @Column(name = "attachment", length = 32)
    private String attachment;
}
```

- 索引：唯一 `user_id`、`credential_id(varchar512)`。
- PG 注意：`PublicKey`/`Transports` 用 `text`（base64）。错误常量 `ErrPasskeyNotFound`/`ErrFriendlyPasskeyNotFound`。
- 关联：`user_id`→User。

---

## 17. PrefillGroup（预填充组）— 表 `prefill_groups`

```java
@Entity
@Table(name = "prefill_groups", indexes = {
    @Index(name = "uk_prefill_name", columnList = "name", unique = true),
    @Index(name = "idx_prefill_groups_type", columnList = "type"),
    @Index(name = "idx_prefill_groups_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class PrefillGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 64, nullable = false, unique = true)
    private String name;                              // not null，唯一索引 uk_prefill_name(name)（软删除条件唯一）

    @Column(name = "type", length = 32, nullable = false)
    private String type;                              // not null，索引；枚举 model/tag/endpoint

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "jsonb")
    private String items;                             // 原 text 存 JSON 字符串数组→JSONB

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_time")
    private Long createdTime;                         // epoch 时间戳

    @Column(name = "updated_time")
    private Long updatedTime;                         // epoch 时间戳

    @Column(name = "deleted_at")
    private Long deletedAt;                           // 软删除，对齐 gorm.DeletedAt
}
```

- 枚举 Type：`model/tag/endpoint`。唯一索引 `uk_prefill_name (name)`（软删除条件唯一）；普通 `type`；软删除 `deleted_at`。
- PG 注意：`Items` JSON 字符串数组（如 `["gpt-4o","gpt-3.5-turbo"]`），用 JSONB。
- 关联：逻辑承载模型组/标签组/端点组配置，无强外键。

---

## 18. Option（系统配置 KV）— 表 `options`

```java
@Entity
@Table(name = "options")
public class Option {

    @Id
    @Column(name = "\"key\"")
    private String key;                               // 主键；key 为保留字，双引号转义

    @Column(name = "value")
    private String value;
}
```

- 主键 `Key`。启动 `InitOptionMap()` 装载内存。
- 本批 PRD 涉及开关键：`RegisterEnabled`、`PasswordLoginEnabled`、`PasswordRegisterEnabled`、`EmailVerificationEnabled`、`GitHubOAuthEnabled`、`LinuxDOOAuthEnabled`、`TelegramOAuthEnabled`、`WeChatAuthEnabled`、`TelegramBotToken`（HMAC key=`SHA256(BotToken)`）。
- PG 注意：`key` 为保留字，`@Column(name = "\"key\"")` 双引号转义。
- 关联：无强关联，全局 KV。

---

## 19. PublicModel（对外模型商品目录）— 表 `public_models` 【本轮新增】

```java
@Entity
@Table(name = "public_models", indexes = {
    @Index(name = "idx_public_models_quality_tier", columnList = "quality_tier"),
    @Index(name = "idx_public_models_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_public_name", columnNames = {"public_name"})
})
@SQLRestriction("deleted_at IS NULL")
public class PublicModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                  // JPA 自增（PG IDENTITY），禁手写 SERIAL

    @Column(name = "public_name", length = 255)
    private String publicName;                        // A 平台公开名，唯一约束 uk_public_name

    @Column(name = "quality_tier", length = 32, columnDefinition = "varchar(32) default 'full'")
    private String qualityTier;                       // full/max/air/自定义，索引，纯展示

    @Column(name = "base_price_ratio", columnDefinition = "numeric default 0")
    private BigDecimal basePriceRatio;                // 基准售价倍率(=model_ratio)，BigDecimal 精度安全

    @Column(name = "use_price", columnDefinition = "boolean default false")
    private Boolean usePrice;                          // true=按次固定价

    @Column(name = "base_price", columnDefinition = "numeric default 0")
    private BigDecimal basePrice;                      // UsePrice=true 时固定单价，BigDecimal

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;                           // 上下架

    @Column(name = "display_name", length = 255, columnDefinition = "varchar(255) default ''")
    private String displayName;

    @Column(name = "sort_order", columnDefinition = "integer default 0")
    private Integer sortOrder;

    @Column(name = "description", length = 1024, columnDefinition = "varchar(1024) default ''")
    private String description;

    @Column(name = "created_time")
    private Long createdTime;                          // epoch 时间戳，autoCreateTime

    @Column(name = "updated_time")
    private Long updatedTime;                          // epoch 时间戳，autoUpdateTime

    @Column(name = "deleted_at")
    private Long deletedAt;                            // 软删除，对齐 gorm.DeletedAt
}
```

- 唯一约束 `uk_public_name (public_name)`；普通 `quality_tier`；软删除 `deleted_at`。
- 业务规则：一个对外模型（公开名 A）一条记录；品质不同拆独立记录分别定价（`opus-4.8`/`opus-4.8-增强`/`opus-4.8-经济` = 三条，quality_tier=full/max/air）。对外全集 = `enabled=true AND deleted_at IS NULL` 的 public_name 集（商品目录唯一权威）。售价对客户恒定（与渠道无关）。
- 同步刷 KV：`BasePriceRatio`→`model_ratio`、`UsePrice=true` 时 `BasePrice`→`model_price`。
- 内存缓存：key=`public_name`，`InitPublicModelMap()` 装载，写时失效。
- PG 注意：主键交 JPA 自增；新增列均带 `default`。
- 关联：`public_name`(A) 与 PlatformModelMapping.public_name 同键；被 Log.resolved_public_model、UserModelAlias.target 逻辑关联。

---

## 20. PlatformModelMapping（超管底仓映射 A→B，全局）— 表 `platform_model_mappings` 【本轮新增】

```java
@Entity
@Table(name = "platform_model_mappings", indexes = {
    @Index(name = "idx_platform_model_mappings_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_public_name", columnNames = {"public_name"})
})
@SQLRestriction("deleted_at IS NULL")
public class PlatformModelMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                  // JPA 自增

    @Column(name = "public_name", length = 255)
    private String publicName;                        // A，1对1，唯一约束 uk_public_name

    @Column(name = "upstream_name", length = 255, nullable = false)
    private String upstreamName;                      // B 真实上游名，not null，客户绝不可见

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;                           // false=回落直通或404

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_time")
    private Long createdTime;                          // epoch 时间戳，autoCreateTime

    @Column(name = "updated_time")
    private Long updatedTime;                          // epoch 时间戳，autoUpdateTime

    @Column(name = "deleted_at")
    private Long deletedAt;                            // 软删除，对齐 gorm.DeletedAt
}
```

- 生效顺序 L2（A→B），选渠之前。作用域全局，无 group/user 维。
- 唯一约束 `uk_public_name (public_name)`（保证 1对1）；软删除 `deleted_at`。
- B 不可见三道闸：数据层无客户读 B 接口 / 序列化层客户视图只给 C→A / 候选层只返公开模型 A 全集。
- 内存缓存：key=`public_name`，`InitPlatformModelMappingMap()` 装载，写时失效。
- PG 注意：主键 JPA 自增。
- 关联：`public_name`(A) 与 PublicModel.public_name 同键；`upstream_name`(B) 与 ChannelModelCost.upstream_model 同键（逻辑关联）。

---

## 21. UserModelAlias（客户层自助映射 C→A，分组/用户级）— 表 `user_model_aliases` 【本轮新增】

```java
@Entity
@Table(name = "user_model_aliases", indexes = {
    @Index(name = "idx_user_model_aliases_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_scope_alias", columnNames = {"scope_type", "scope_id", "alias"})
})
@SQLRestriction("deleted_at IS NULL")
public class UserModelAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                  // JPA 自增

    @Column(name = "scope_type", length = 16, nullable = false)
    private String scopeType;                         // user/group，not null，复合唯一 uk_scope_alias priority:1

    @Column(name = "scope_id", length = 64, nullable = false)
    private String scopeId;                           // user→user_id 字符串/group→分组名，not null，复合唯一 priority:2

    @Column(name = "alias", length = 255, nullable = false)
    private String alias;                             // C 客户别名，not null，复合唯一 priority:3

    @Column(name = "target", length = 255, nullable = false)
    private String target;                            // A 目标公开名，not null，不强制白名单

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;

    @Column(name = "created_time")
    private Long createdTime;                          // epoch 时间戳，autoCreateTime

    @Column(name = "updated_time")
    private Long updatedTime;                          // epoch 时间戳，autoUpdateTime

    @Column(name = "deleted_at")
    private Long deletedAt;                            // 软删除，对齐 gorm.DeletedAt
}
```

- 生效顺序 L1（C→A），在 L2 之前。
- 复合唯一约束 `uk_scope_alias (scope_type, scope_id, alias)`（同作用域内别名 C 唯一，1对1）；软删除 `deleted_at`。
- 枚举 ScopeType：`user/group`。`ScopeId`：user→user_id 字符串化，group→分组名。
- 优先级：同 C 命中多 scope 时 **user > group**。
- 输入约束：`Target` 写入**不校验白名单**（铁律，可硬输平台没有的名）；候选由前端从公开模型全集联想但落库不拦。
- 越权护栏：user 路由写入强制 `scope_type=user AND scope_id=:caller_user_id`，禁跨 scope 写。
- PG 注意：主键 JPA 自增。
- 关联：`scope_id`（scope_type=user 时）→User.id；`target`(A) 逻辑关联 PublicModel.public_name（不强制）。

---

## 22. ChannelModelCost（供应商成本倍率，渠道×真实模型 B）— 表 `channel_model_costs` 【本轮新增】

```java
@Entity
@Table(name = "channel_model_costs", indexes = {
    @Index(name = "idx_channel_model_costs_channel_id", columnList = "channel_id"),
    @Index(name = "idx_channel_model_costs_deleted_at", columnList = "deleted_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_channel_model", columnNames = {"channel_id", "upstream_model"})
})
@SQLRestriction("deleted_at IS NULL")
public class ChannelModelCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                  // JPA 自增

    @Column(name = "channel_id")
    private Integer channelId;                        // =Channel.Id，索引，复合唯一 uk_channel_model priority:1

    @Column(name = "upstream_model", length = 255)
    private String upstreamModel;                     // B，客户不可见，复合唯一 priority:2

    @Column(name = "cost_ratio", columnDefinition = "numeric default 0")
    private BigDecimal costRatio;                     // 成本倍率(输入token)，BigDecimal 精度安全

    @Column(name = "completion_cost_ratio", columnDefinition = "numeric default 0")
    private BigDecimal completionCostRatio;           // 成本补全倍率(输出token)，0=回落，BigDecimal

    @Column(name = "enabled", columnDefinition = "boolean default true")
    private Boolean enabled;                           // false=视为缺失(记0+告警)

    @Column(name = "effective_time", columnDefinition = "bigint default 0")
    private Long effectiveTime;                        // epoch 时间戳，取最新生效且enabled一条

    @Column(name = "source_unit_price", columnDefinition = "numeric default 0")
    private BigDecimal sourceUnitPrice;               // 扩展位:进货单价，本期不参与计算，BigDecimal

    @Column(name = "remark", length = 255, columnDefinition = "varchar(255) default ''")
    private String remark;

    @Column(name = "created_time")
    private Long createdTime;                          // epoch 时间戳，autoCreateTime

    @Column(name = "updated_time")
    private Long updatedTime;                          // epoch 时间戳，autoUpdateTime

    @Column(name = "deleted_at")
    private Long deletedAt;                            // 软删除，对齐 gorm.DeletedAt
}
```

- 结算阶段取值（链路第 17 步）。
- 复合唯一约束 `uk_channel_model (channel_id, upstream_model)`（一渠道对一 B 只一条生效成本）；普通 `channel_id`；软删除 `deleted_at`。
- 计费口径：`CostRatio` 口径同 model_ratio（输入 token）；`CompletionCostRatio=0` 回落 `CostRatio×现网 CompletionRatio`。
- 多供应商落表：同一 A→B 下，每个挂 Ability 的渠道各一行（售价同挂 A，成本分行挂 channel×B）。
- 取值时机：结算阶段，主键 `(实际选中 ChannelId, L2 后 B)` 精确取一行（兜底切换后 ChannelId 变→自动取新渠道行）。
- 内存缓存：`[channelId][upstreamModel]→CostRatio`，`InitChannelModelCostMap()` 装载，写时失效。
- PG 注意：主键 JPA 自增。
- 关联：`channel_id`→Channel.Id；`upstream_model`(B) 逻辑关联 PlatformModelMapping.upstream_name。

---

## 附：运行期内存结构（不落库，仅说明，DATA-MODEL「计费/映射运行期内存结构」）

以下为逐笔快照/运行期结构，**不建表**，列此仅供开发知晓字段来源：
- **PriceData**（`types/price_data.go`）新增 `CostRatio`/`CompletionCostRatio`/`CostMissing`（第 17 步从 ChannelModelCost 取）；现有 ModelRatio/ModelPrice/GroupRatioInfo/UsePrice 复用承载售价端。
- **RelayInfo**（`relay/common/relay_info.go`）新增 `RequestedModel(C)`/`ResolvedPublicModel(A)`/`TargetProtocol`/`Passthrough`/`QuotaSell`/`QuotaCost`/`QuotaProfit`；`UpstreamModelName(B)`/`UsingGroup`/`ChannelId`/`InboundFormat` 复用。
- **IR 中间表示**（`relay/compat/ir`）：ChatIR/ChatRespIR/ChatDeltaIR/StreamState，覆盖 OpenAI⇄Anthropic 五差异点。

---

## GAP 汇总（待架构师回查）

1. **Channel 软删除**：DATA-MODEL §3 未列 `DeletedAt`，本契约不补（见第 3 节）。
2. **SubscriptionPlan 支付商品号字段**：DATA-MODEL §8.1 末行三字段未给独立 json tag/约束，按 snake_case 推导（见第 8 节）。
3. **SubscriptionOrder**：DATA-MODEL §8.2 为内联描述无类型/约束表，PaymentMethod/PaymentProvider 类型与枚举、UserId/PlanId 索引缺标注（见第 9 节）。
4. **UserSubscription 合并行字段**：DATA-MODEL §8.3 `LastResetTime/NextResetTime`、`UpgradeGroup/PrevUserGroup/DowngradeGroup` 斜杠合并，json tag 按推导（见第 10 节）。

以上均为「DATA-MODEL 表述不完整」而非「字段缺失」，未自创新字段，等架构师确认。
