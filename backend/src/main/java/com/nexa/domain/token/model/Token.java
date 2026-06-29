package com.nexa.domain.token.model;

import com.nexa.domain.token.exception.InvalidTokenParameterException;
import com.nexa.domain.token.vo.TokenKey;
import com.nexa.domain.token.vo.TokenStatus;
import com.nexa.domain.token.vo.UsageSummary;

import java.security.SecureRandom;
import java.io.Serializable;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * 令牌聚合根（充血领域模型，API 令牌管理一致性边界，F-3001~F-3012）。
 *
 * <p>承载 API 令牌的全部可管理状态：归属用户、明文 key（敏感）、状态、名称、过期时间、剩余/已用配额、
 * 无限额度开关、模型限制（减法约束）、IP 白名单、调用分组、跨组重试开关、端点级减法约束。本聚合是
 * 令牌限界上下文的一致性边界，所有不变量与状态迁移在聚合方法上守护（backend-engineer §2.2 充血、
 * §2.4 战术完整）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring/Jackson），与 JPA 实体 {@code TokenPO} 分离，可纯单测。
 * 字段对齐 DB-SCHEMA §2 tokens 表 + openapi TokenUserVO/TokenCreateRequest。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code userId} 必为正（令牌必归属一个用户，self-scope 鉴权依据）。</li>
 *   <li>{@code key} 非空（令牌凭证；敏感，客户视图默认脱敏，仅受控端点取明文）。</li>
 *   <li>{@code name} 非空且 ≤50（DB-SCHEMA §2 校验 {@code MsgTokenNameTooLong}）。</li>
 *   <li>{@code status} 非空（启用/禁用二态；已过期/耗尽/已删除为派生态不落 status）。</li>
 *   <li>{@code remainQuota}/{@code usedQuota} ∈ [0, maxQuotaValue]（DB-SCHEMA §2，禁负配额）。</li>
 *   <li>{@code group} ≤255、{@code expiredTime} 为 epoch 秒或 -1（永不过期）。</li>
 * </ul></p>
 *
 * <p>领域规则补充（DB-SCHEMA §2 语义提示，不改字段）：{@code modelLimits} 语义由「加法授权」收窄为
 * 「减法约束」（模型全开背景）；{@code endpointLimits} 在 TokenAuth 后、L1 前校验，纯减法自我约束，
 * 非权限闸门。本聚合只负责字段合法与状态迁移，运行期减法校验属转发链路职责（超出本片范围）。</p>
 */
public class Token implements Serializable {

    /** Redis 鉴权缓存以 JDK 序列化承载本聚合（T12/CR-05），声明序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** name 最大长度，对齐 DB-SCHEMA §2 校验 {@code MsgTokenNameTooLong}（≤50）。 */
    public static final int NAME_MAX_LENGTH = 50;

    /** group 最大长度，对齐 DB varchar(255)。 */
    public static final int GROUP_MAX_LENGTH = 255;

    /**
     * 配额上限（DB-SCHEMA §2「RemainQuota ∈ [0, 10亿*QuotaPerUnit]」）。
     *
     * <p>QuotaPerUnit 为系统配置（现网默认 500000），随部署不同。领域层不依赖外部配置，故以
     * 「10亿 × 默认 QuotaPerUnit(500000)」推导一个保守上限做兜底硬校验，防溢出/异常大额度；
     * 业务侧更精确的配额倍率换算由计费域负责（不在本聚合）。</p>
     */
    public static final long MAX_QUOTA_VALUE = 1_000_000_000L * 500_000L;

    /** 永不过期标记（expired_time=-1）。 */
    public static final long NEVER_EXPIRE = -1L;

    /** 生成明文 key 的随机字节数（48 字节 → base64url 64 字符，落 varchar(128) 充裕）。 */
    private static final int KEY_RANDOM_BYTES = 48;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** 归属用户 id（必 > 0，self-scope 鉴权依据）。 */
    private final long userId;

    /** 明文凭证（敏感，客户视图默认脱敏）。 */
    private final String key;

    /** 状态（启用/禁用）。 */
    private TokenStatus status;

    /** 令牌名（非空，≤50）。 */
    private String name;

    /** 过期时间 epoch 秒，-1=永不过期。 */
    private long expiredTime;

    /** 剩余配额（>=0，整数额度单位）。 */
    private long remainQuota;

    /** 是否无限额度。 */
    private boolean unlimitedQuota;

    /** 是否启用模型限制（减法约束开关）。 */
    private boolean modelLimitsEnabled;

    /** 允许模型列表 JSON 串（减法约束，可空）。 */
    private String modelLimits;

    /** IP 白名单（按换行切分，空=不限）。 */
    private String allowIps;

    /** 已用配额（>=0，整数额度单位）。 */
    private long usedQuota;

    /** 调用分组（≤255，缺省空串）。 */
    private String group;

    /** 跨组重试开关（group=auto 时生效）。 */
    private boolean crossGroupRetry;

    /** 是否启用端点级减法约束。 */
    private boolean endpointLimitsEnabled;

    /** 端点级减法约束 JSON 串（入站协议集，可空）。 */
    private String endpointLimits;

    /** 最近访问时间 epoch 秒（可空）。 */
    private Long accessedTime;

    /** 创建时间 epoch 秒。 */
    private final Long createdTime;

    private Token(Long id, long userId, String key, TokenStatus status, String name,
                  long expiredTime, long remainQuota, boolean unlimitedQuota,
                  boolean modelLimitsEnabled, String modelLimits, String allowIps, long usedQuota,
                  String group, boolean crossGroupRetry, boolean endpointLimitsEnabled,
                  String endpointLimits, Long accessedTime, Long createdTime) {
        this.id = id;
        this.userId = userId;
        this.key = key;
        this.status = status;
        this.name = name;
        this.expiredTime = expiredTime;
        this.remainQuota = remainQuota;
        this.unlimitedQuota = unlimitedQuota;
        this.modelLimitsEnabled = modelLimitsEnabled;
        this.modelLimits = modelLimits;
        this.allowIps = allowIps;
        this.usedQuota = usedQuota;
        this.group = group;
        this.crossGroupRetry = crossGroupRetry;
        this.endpointLimitsEnabled = endpointLimitsEnabled;
        this.endpointLimits = endpointLimits;
        this.accessedTime = accessedTime;
        this.createdTime = createdTime;
    }

    /**
     * 创建新令牌（工厂方法，充血行为，校验全部不变量，F-3001 create）。
     *
     * <p>领域规则：name 必填且 ≤50（openapi TokenCreateRequest required name）；明文 key 由系统安全生成
     * （形如 {@code sk-<base64url>}，客户不可指定，避免碰撞/伪造）；创建即启用（Status=ENABLED）；
     * usedQuota 初始为 0；group/allow_ips 缺省空串；expiredTime 缺省 -1（永不过期）；
     * unlimited_quota 时 remain_quota 不做上限校验（额度无意义）、否则校验 [0, maxQuotaValue]；
     * 打 createdTime。唯一性（key）由仓储/DB 唯一索引兜底，本工厂只保证字段合法。</p>
     *
     * @param userId             归属用户 id（必 > 0，取自认证主体）
     * @param name               令牌名（必填，≤50）
     * @param remainQuota        剩余配额（unlimited=false 时校验 [0, maxQuotaValue]）
     * @param unlimitedQuota     是否无限额度（可空→false）
     * @param expiredTime        过期时间 epoch 秒（可空→-1 永不过期）
     * @param modelLimitsEnabled 是否启用模型限制（可空→false）
     * @param modelLimits        允许模型 JSON 串（可空）
     * @param allowIps           IP 白名单（可空→空串）
     * @param group              调用分组（可空→空串，≤255）
     * @param crossGroupRetry    跨组重试开关（可空→false）
     * @return 待持久化的新令牌（id 由仓储保存后回填）
     * @throws InvalidTokenParameterException 字段非法
     */
    public static Token create(long userId, String name, Long remainQuota, Boolean unlimitedQuota,
                               Long expiredTime, Boolean modelLimitsEnabled, String modelLimits,
                               String allowIps, String group, Boolean crossGroupRetry) {
        long uid = requireUserId(userId);
        String n = requireName(name);
        boolean unlimited = Boolean.TRUE.equals(unlimitedQuota);
        long quota = normalizeQuota(remainQuota, unlimited);
        return new Token(
                null, uid, generateKey(), TokenStatus.ENABLED, n,
                normalizeExpiredTime(expiredTime), quota, unlimited,
                Boolean.TRUE.equals(modelLimitsEnabled), normalizeJson(modelLimits),
                normalizeAllowIps(allowIps), 0L, normalizeGroup(group),
                Boolean.TRUE.equals(crossGroupRetry), false, "",
                null, Instant.now().getEpochSecond());
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发创建不变量与时间打点）。
     *
     * @return 重建的聚合（参数语义见各字段注释）
     */
    public static Token rehydrate(Long id, long userId, String key, int status, String name,
                                  long expiredTime, long remainQuota, boolean unlimitedQuota,
                                  boolean modelLimitsEnabled, String modelLimits, String allowIps,
                                  long usedQuota, String group, boolean crossGroupRetry,
                                  boolean endpointLimitsEnabled, String endpointLimits,
                                  Long accessedTime, Long createdTime) {
        // 委托 Builder 装配：字段名自解释、null 归一逻辑（name/allowIps/group/endpointLimits → ""）
        // 收敛在 Builder 一处。状态由整数码经 TokenStatus.fromCode 解析后传入（脏码归并禁用）。
        return builder()
                .id(id)
                .userId(userId)
                .key(key)
                .status(TokenStatus.fromCode(status))
                .name(name)
                .expiredTime(expiredTime)
                .remainQuota(remainQuota)
                .unlimitedQuota(unlimitedQuota)
                .modelLimitsEnabled(modelLimitsEnabled)
                .modelLimits(modelLimits)
                .allowIps(allowIps)
                .usedQuota(usedQuota)
                .group(group)
                .crossGroupRetry(crossGroupRetry)
                .endpointLimitsEnabled(endpointLimitsEnabled)
                .endpointLimits(endpointLimits)
                .accessedTime(accessedTime)
                .createdTime(createdTime)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表（18 个参数）：调用处以具名链式方法装配，
     * 可读性与抗重构性更好（第 6 个 {@code long} 到底是 expiredTime 还是 remainQuota，
     * 位置参数看不出，Builder 一目了然）。与 {@code rehydrate} 一致——本入口<b>不</b>触发
     * 创建不变量与时间打点，纯还原已存状态。</p>
     *
     * @return 新的令牌重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 令牌聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：原始类型列的 setter 接受<b>包装类型</b>并把 {@code null} 归一为默认
     * （数值→0、布尔→false）；name/allowIps/group/endpointLimits 把 {@code null} 归一为空串
     * ——这些与原 {@code rehydrate} 构造器一致的兜底逻辑全部收敛在此，
     * {@code TokenRepositoryImpl.toDomain} 不再散落 {@code ?:} 三元。</p>
     */
    public static final class Builder {
        private Long id;
        private long userId;
        private String key;
        private TokenStatus status;
        private String name = "";
        private long expiredTime;
        private long remainQuota;
        private boolean unlimitedQuota;
        private boolean modelLimitsEnabled;
        private String modelLimits;
        private String allowIps = "";
        private long usedQuota;
        private String group = "";
        private boolean crossGroupRetry;
        private boolean endpointLimitsEnabled;
        private String endpointLimits = "";
        private Long accessedTime;
        private Long createdTime;

        private Builder() {
        }

        /** @param id 主键（新建未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param userId 归属用户 id（null 归一为 0） */
        public Builder userId(Long userId) {
            this.userId = userId == null ? 0L : userId;
            return this;
        }

        /** @param key 明文凭证 */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /** @param status 状态值对象 */
        public Builder status(TokenStatus status) {
            this.status = status;
            return this;
        }

        /** @param name 令牌名（null 归一为空串） */
        public Builder name(String name) {
            this.name = name == null ? "" : name;
            return this;
        }

        /** @param expiredTime 过期时间 epoch 秒，-1=永不过期（null 归一为 0） */
        public Builder expiredTime(Long expiredTime) {
            this.expiredTime = expiredTime == null ? 0L : expiredTime;
            return this;
        }

        /** @param remainQuota 剩余配额（null 归一为 0） */
        public Builder remainQuota(Long remainQuota) {
            this.remainQuota = remainQuota == null ? 0L : remainQuota;
            return this;
        }

        /** @param unlimitedQuota 是否无限额度（null 归一为 false） */
        public Builder unlimitedQuota(Boolean unlimitedQuota) {
            this.unlimitedQuota = Boolean.TRUE.equals(unlimitedQuota);
            return this;
        }

        /** @param modelLimitsEnabled 是否启用模型限制（null 归一为 false） */
        public Builder modelLimitsEnabled(Boolean modelLimitsEnabled) {
            this.modelLimitsEnabled = Boolean.TRUE.equals(modelLimitsEnabled);
            return this;
        }

        /** @param modelLimits 允许模型 JSON 串，可为 null（与原 rehydrate 一致不归一空串） */
        public Builder modelLimits(String modelLimits) {
            this.modelLimits = modelLimits;
            return this;
        }

        /** @param allowIps IP 白名单（null 归一为空串） */
        public Builder allowIps(String allowIps) {
            this.allowIps = allowIps == null ? "" : allowIps;
            return this;
        }

        /** @param usedQuota 已用配额（null 归一为 0） */
        public Builder usedQuota(Long usedQuota) {
            this.usedQuota = usedQuota == null ? 0L : usedQuota;
            return this;
        }

        /** @param group 调用分组（null 归一为空串） */
        public Builder group(String group) {
            this.group = group == null ? "" : group;
            return this;
        }

        /** @param crossGroupRetry 跨组重试开关（null 归一为 false） */
        public Builder crossGroupRetry(Boolean crossGroupRetry) {
            this.crossGroupRetry = Boolean.TRUE.equals(crossGroupRetry);
            return this;
        }

        /** @param endpointLimitsEnabled 是否启用端点级减法约束（null 归一为 false） */
        public Builder endpointLimitsEnabled(Boolean endpointLimitsEnabled) {
            this.endpointLimitsEnabled = Boolean.TRUE.equals(endpointLimitsEnabled);
            return this;
        }

        /** @param endpointLimits 端点级减法约束 JSON 串（null 归一为空串） */
        public Builder endpointLimits(String endpointLimits) {
            this.endpointLimits = endpointLimits == null ? "" : endpointLimits;
            return this;
        }

        /** @param accessedTime 最近访问时间 epoch 秒，可为 null */
        public Builder accessedTime(Long accessedTime) {
            this.accessedTime = accessedTime;
            return this;
        }

        /** @param createdTime 创建时间 epoch 秒，可为 null */
        public Builder createdTime(Long createdTime) {
            this.createdTime = createdTime;
            return this;
        }

        /**
         * 装配并返回重建的令牌聚合（不触发创建不变量与时间打点）。
         *
         * @return 重建的令牌聚合
         */
        public Token build() {
            return new Token(
                    id, userId, key, status, name,
                    expiredTime, remainQuota, unlimitedQuota,
                    modelLimitsEnabled, modelLimits, allowIps, usedQuota,
                    group, crossGroupRetry, endpointLimitsEnabled,
                    endpointLimits, accessedTime, createdTime);
        }
    }

    /**
     * 覆盖式编辑令牌（充血行为，F-3006 update 全量更新分支，含 F-3008~F-3012 各约束字段）。
     *
     * <p>领域规则：openapi PUT 令牌为「覆盖式」（区别于 status_only）。name 必填校验；
     * remain_quota/unlimited_quota/expired_time/model_limits/allow_ips/group/cross_group_retry/
     * endpoint_limits 全量覆盖并经各自校验/归一。key 不可改（凭证不轮换走编辑路径）；status 不在编辑
     * 路径改动（启停走 {@link #applyStatus(Integer)}）。endpoint_limits_enabled 由 endpoint_limits
     * 是否非空派生（减法约束有内容即视为启用，F-3012）。</p>
     *
     * @param name               新令牌名（必填，≤50）
     * @param remainQuota        新剩余配额
     * @param unlimitedQuota     新无限额度开关（可空→false）
     * @param expiredTime        新过期时间（可空→-1）
     * @param modelLimitsEnabled 新模型限制开关（可空→false）
     * @param modelLimits        新允许模型 JSON 串（可空）
     * @param allowIps           新 IP 白名单（可空→空串）
     * @param group              新调用分组（可空→空串）
     * @param crossGroupRetry    新跨组重试开关（可空→false）
     * @param endpointLimits     新端点级减法约束 JSON 串（可空→空串；非空即启用）
     * @throws InvalidTokenParameterException 字段非法
     */
    public void update(String name, Long remainQuota, Boolean unlimitedQuota, Long expiredTime,
                       Boolean modelLimitsEnabled, String modelLimits, String allowIps, String group,
                       Boolean crossGroupRetry, String endpointLimits) {
        this.name = requireName(name);
        boolean unlimited = Boolean.TRUE.equals(unlimitedQuota);
        this.unlimitedQuota = unlimited;
        this.remainQuota = normalizeQuota(remainQuota, unlimited);
        this.expiredTime = normalizeExpiredTime(expiredTime);
        this.modelLimitsEnabled = Boolean.TRUE.equals(modelLimitsEnabled);
        this.modelLimits = normalizeJson(modelLimits);
        this.allowIps = normalizeAllowIps(allowIps);
        this.group = normalizeGroup(group);
        this.crossGroupRetry = Boolean.TRUE.equals(crossGroupRetry);
        String el = normalizeJson(endpointLimits);
        this.endpointLimits = el;
        // 端点级减法约束有内容即视为启用（F-3012：纯减法自我约束开关随内容派生）。
        this.endpointLimitsEnabled = !el.isEmpty();
    }

    /**
     * 仅更新状态（充血状态迁移，F-3006 status_only=true 分支）。
     *
     * <p>领域规则：openapi PUT 携 {@code status_only=true} 时只切换启用/禁用，其余字段一律不动
     * （避免脱敏回显的占位字段被误回写）。目标状态仅允许 1(启用)/2(禁用)，由 {@link TokenStatus#requireValid}
     * 守护；幂等——同态再切无副作用。</p>
     *
     * @param status 目标状态码（1 或 2）
     * @throws InvalidTokenParameterException 非法目标状态
     */
    public void applyStatus(Integer status) {
        this.status = TokenStatus.requireValid(status);
    }

    /**
     * 记录一次访问（充血行为，写 accessed_time）。
     *
     * <p>领域规则：取明文 key / 用量查询等读操作可刷新最近访问时间（运维可观测）。</p>
     *
     * @param accessedAt 访问时刻（null→now）
     */
    public void recordAccess(Instant accessedAt) {
        this.accessedTime = (accessedAt == null ? Instant.now() : accessedAt).getEpochSecond();
    }

    /**
     * 派生令牌用量摘要（充血行为，F-3012，对齐 openapi UsageCreditSummary）。
     *
     * <p>领域规则：totalGranted = remain + used；totalUsed = used；无限额度时 available 置 -1 标记无限，
     * 否则 = remain；expiresAt 在永不过期(-1)时按 openapi 归零为 0。</p>
     *
     * @return 用量摘要值对象
     */
    public UsageSummary usageSummary() {
        long available = unlimitedQuota ? UsageSummary.UNLIMITED_AVAILABLE : remainQuota;
        long expiresAt = expiredTime == NEVER_EXPIRE ? 0L : expiredTime;
        return new UsageSummary(
                UsageSummary.OBJECT_TYPE,
                remainQuota + usedQuota,
                usedQuota,
                available,
                expiresAt,
                unlimitedQuota,
                modelLimits,
                modelLimitsEnabled);
    }

    /**
     * 校验本令牌归属指定操作者（self-scope 护栏，充血行为，F-3004/F-3006/F-3007）。
     *
     * <p>领域规则来源：ROLE-PERMISSION-MATRIX §3「self-scope 按 user_id 强制过滤，越权他人资源 403」。
     * 越权判定下沉到聚合根（不在 controller 散落 {@code if (token.userId != actor)} 裸比较）。</p>
     *
     * @param actorUserId 当前操作者用户 id
     * @return 本令牌是否归属该操作者
     */
    public boolean belongsTo(long actorUserId) {
        return this.userId == actorUserId;
    }

    /** @return 脱敏后的可下发 key（客户视图默认用，F-3002） */
    public String maskedKey() {
        return TokenKey.mask(key);
    }

    // ---- 校验/归一私有方法（领域规则集中，复用于 create/update） ----

    private static long requireUserId(long userId) {
        if (userId <= 0) {
            throw new InvalidTokenParameterException("token userId must be positive, got " + userId);
        }
        return userId;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidTokenParameterException("token name must not be blank");
        }
        String n = name.trim();
        if (n.length() > NAME_MAX_LENGTH) {
            // 对齐 DB-SCHEMA §2 MsgTokenNameTooLong。
            throw new InvalidTokenParameterException("token name too long (max " + NAME_MAX_LENGTH + ")");
        }
        return n;
    }

    /**
     * 配额归一与校验：无限额度时配额无意义直接归 0；否则要求 [0, maxQuotaValue]（DB-SCHEMA §2）。
     */
    private static long normalizeQuota(Long remainQuota, boolean unlimited) {
        if (unlimited) {
            return 0L;
        }
        long q = remainQuota == null ? 0L : remainQuota;
        if (q < 0 || q > MAX_QUOTA_VALUE) {
            throw new InvalidTokenParameterException(
                    "remain_quota out of range [0, " + MAX_QUOTA_VALUE + "], got " + q);
        }
        return q;
    }

    private static long normalizeExpiredTime(Long expiredTime) {
        // 缺省/非正（除 -1 外）一律视作永不过期，避免歧义的 0/负值穿透。
        if (expiredTime == null || expiredTime <= 0) {
            return NEVER_EXPIRE;
        }
        return expiredTime;
    }

    private static String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return "";
        }
        String g = group.trim();
        if (g.length() > GROUP_MAX_LENGTH) {
            throw new InvalidTokenParameterException("group too long (max " + GROUP_MAX_LENGTH + ")");
        }
        return g;
    }

    private static String normalizeAllowIps(String allowIps) {
        return allowIps == null ? "" : allowIps.trim();
    }

    private static String normalizeJson(String json) {
        return json == null ? "" : json.trim();
    }

    /**
     * 生成系统级明文 key（{@code sk-<base64url>}，客户不可指定）。
     *
     * <p>用 {@link SecureRandom} 48 字节熵 → base64url，避免可预测/碰撞。前缀 {@code sk-} 与现网/
     * OpenAI 风格一致，便于客户端识别。</p>
     */
    private static String generateKey() {
        byte[] buf = new byte[KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(buf);
        return "sk-" + URL_ENCODER.encodeToString(buf);
    }

    /**
     * 基础设施层回填自增主键（仅保存后调用一次）。
     *
     * @param assignedId 仓储分配的主键
     */
    public void assignId(Long assignedId) {
        this.id = Objects.requireNonNull(assignedId, "assigned id must not be null");
    }

    // ---- 访问器（领域查询；无 setter，状态变更只走充血行为方法） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 归属用户 id */
    public long userId() {
        return userId;
    }

    /**
     * @return 明文 key（敏感！仅基础设施持久化与受控取明文端点可用，绝不进默认客户视图）
     */
    public String key() {
        return key;
    }

    /** @return 状态 */
    public TokenStatus status() {
        return status;
    }

    /** @return 令牌名 */
    public String name() {
        return name;
    }

    /** @return 过期时间 epoch 秒（-1=永不过期） */
    public long expiredTime() {
        return expiredTime;
    }

    /** @return 剩余配额 */
    public long remainQuota() {
        return remainQuota;
    }

    /** @return 是否无限额度 */
    public boolean unlimitedQuota() {
        return unlimitedQuota;
    }

    /** @return 是否启用模型限制 */
    public boolean modelLimitsEnabled() {
        return modelLimitsEnabled;
    }

    /** @return 允许模型 JSON 串（减法约束） */
    public String modelLimits() {
        return modelLimits;
    }

    /** @return IP 白名单（按换行切分） */
    public String allowIps() {
        return allowIps;
    }

    /** @return 已用配额 */
    public long usedQuota() {
        return usedQuota;
    }

    /** @return 调用分组 */
    public String group() {
        return group;
    }

    /** @return 跨组重试开关 */
    public boolean crossGroupRetry() {
        return crossGroupRetry;
    }

    /** @return 是否启用端点级减法约束 */
    public boolean endpointLimitsEnabled() {
        return endpointLimitsEnabled;
    }

    /** @return 端点级减法约束 JSON 串 */
    public String endpointLimits() {
        return endpointLimits;
    }

    /** @return 最近访问时间 epoch 秒（可空） */
    public Long accessedTime() {
        return accessedTime;
    }

    /** @return 创建时间 epoch 秒 */
    public Long createdTime() {
        return createdTime;
    }
}
