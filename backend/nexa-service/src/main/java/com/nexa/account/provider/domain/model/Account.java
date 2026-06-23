package com.nexa.account.provider.domain.model;

import com.nexa.account.provider.domain.exception.InvalidAccountParameterException;
import com.nexa.account.provider.domain.vo.AccountGroupRef;
import com.nexa.account.provider.domain.vo.AccountStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商账号聚合根（充血领域模型，账号管理一致性边界）。
 *
 * <p>把「供应商」建成结构化账号：承载平台/类型、凭证（credentials，敏感 JSON，绝不进视图）、
 * 并发度、优先级、状态（启用/禁用/限流）、限流与过载时间窗、过期与自动暂停、所属分组集合。
 * 本聚合是账号限界上下文的一致性边界，不变量与状态迁移在聚合方法上守护。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring/Jackson），与 JPA 实体 {@code AccountJpaEntity} 分离，可纯单测。
 * 时间字段统一 epoch 秒 Long（对齐 channel 现网习惯）。状态以字符串码持久化（对齐参考表）。</p>
 *
 * <p>不变量：name/platform 非空；type 非空；concurrency>=1；priority>=0；status 非空。
 * AccountGroupRef 集合为聚合内关联（仓储 fan-out 到 account_groups）。</p>
 */
public class Account {

    /** 默认并发度。 */
    public static final int DEFAULT_CONCURRENCY = 3;

    /** 默认优先级。 */
    public static final int DEFAULT_PRIORITY = 50;

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** 账号名（必填）。 */
    private String name;

    /** 供应商平台（必填，如 openai/anthropic）。 */
    private String platform;

    /** 账号类型（必填，如 api_key/oauth）。 */
    private String type;

    /** 凭证 JSON（敏感，绝不下发视图）。 */
    private String credentials;

    /** 并发度（>=1）。 */
    private int concurrency;

    /** 优先级（>=0）。 */
    private int priority;

    /** 账号状态（启用/禁用/限流）。 */
    private AccountStatus status;

    /** 进入限流的时刻 epoch 秒（可空）。 */
    private Long rateLimitedAt;

    /** 限流恢复时刻 epoch 秒（可空）。 */
    private Long rateLimitResetAt;

    /** 过载冷却截止 epoch 秒（可空）。 */
    private Long overloadUntil;

    /** 账号过期时刻 epoch 秒（可空）。 */
    private Long expiresAt;

    /** 过期是否自动暂停（缺省 true）。 */
    private boolean autoPauseOnExpired;

    /** 所属分组关联集合（聚合内成员）。 */
    private List<AccountGroupRef> groups;

    /** 创建时间 epoch 秒。 */
    private final Long createdTime;

    /** 更新时间 epoch 秒。 */
    private Long updatedTime;

    private Account(Long id, String name, String platform, String type, String credentials,
                    int concurrency, int priority, AccountStatus status, Long rateLimitedAt,
                    Long rateLimitResetAt, Long overloadUntil, Long expiresAt,
                    boolean autoPauseOnExpired, List<AccountGroupRef> groups,
                    Long createdTime, Long updatedTime) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.type = type;
        this.credentials = credentials;
        this.concurrency = concurrency;
        this.priority = priority;
        this.status = status;
        this.rateLimitedAt = rateLimitedAt;
        this.rateLimitResetAt = rateLimitResetAt;
        this.overloadUntil = overloadUntil;
        this.expiresAt = expiresAt;
        this.autoPauseOnExpired = autoPauseOnExpired;
        this.groups = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    /**
     * 创建新账号（工厂方法，充血行为，校验全部不变量）。
     *
     * <p>领域规则：name/platform/type 必填；concurrency<1→默认 3；priority<0→0；
     * 创建即启用（status=ACTIVE）；autoPauseOnExpired 缺省 true；打 createdTime/updatedTime。</p>
     *
     * @param name               账号名（必填）
     * @param platform           供应商平台（必填）
     * @param type               账号类型（必填）
     * @param credentials        凭证 JSON（敏感，可空）
     * @param concurrency        并发度（可空/&lt;1→默认 3）
     * @param priority           优先级（可空/&lt;0→0；缺省 50）
     * @param expiresAt          过期时刻 epoch 秒（可空）
     * @param autoPauseOnExpired 过期自动暂停（可空→true）
     * @param groups             所属分组集合（可空）
     * @return 待持久化的新账号（id 由仓储保存后回填）
     * @throws InvalidAccountParameterException 字段非法
     */
    public static Account create(String name, String platform, String type, String credentials,
                                 Integer concurrency, Integer priority, Long expiresAt,
                                 Boolean autoPauseOnExpired, List<AccountGroupRef> groups) {
        long now = Instant.now().getEpochSecond();
        return new Account(
                null,
                requireText(name, "name"),
                requireText(platform, "platform"),
                requireText(type, "type"),
                normalizeJson(credentials),
                normalizeConcurrency(concurrency),
                normalizePriority(priority),
                AccountStatus.ACTIVE,
                null, null, null,
                expiresAt,
                autoPauseOnExpired == null || autoPauseOnExpired,
                groups,
                now, now);
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发创建不变量与时间打点）。
     *
     * @return 重建的账号聚合
     */
    public static Account rehydrate(Long id, String name, String platform, String type,
                                    String credentials, int concurrency, int priority, String status,
                                    Long rateLimitedAt, Long rateLimitResetAt, Long overloadUntil,
                                    Long expiresAt, boolean autoPauseOnExpired,
                                    List<AccountGroupRef> groups, Long createdTime, Long updatedTime) {
        return new Account(id, name, platform, type, credentials, concurrency, priority,
                AccountStatus.fromCode(status), rateLimitedAt, rateLimitResetAt, overloadUntil,
                expiresAt, autoPauseOnExpired, groups, createdTime, updatedTime);
    }

    /**
     * 覆盖式编辑账号（充血行为，PUT 语义）。
     *
     * <p>领域规则：name/platform/type 必填校验；credentials 为<b>可选更新</b>——传 null/空白表示
     * 「保留原 credentials 不变」（避免视图脱敏后回写空值清空凭证）；其余字段全量覆盖并归一。
     * status 不在编辑路径改动（启停/限流走专门方法）。刷新 updatedTime。</p>
     */
    public void update(String name, String platform, String type, String newCredentials,
                       Integer concurrency, Integer priority, Long expiresAt,
                       Boolean autoPauseOnExpired, List<AccountGroupRef> groups) {
        this.name = requireText(name, "name");
        this.platform = requireText(platform, "platform");
        this.type = requireText(type, "type");
        if (newCredentials != null && !newCredentials.isBlank()) {
            this.credentials = newCredentials.trim();
        }
        this.concurrency = normalizeConcurrency(concurrency);
        this.priority = normalizePriority(priority);
        this.expiresAt = expiresAt;
        if (autoPauseOnExpired != null) {
            this.autoPauseOnExpired = autoPauseOnExpired;
        }
        this.groups = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
        touch();
    }

    /**
     * 启用账号（充血状态迁移）。置 status=ACTIVE 并清空限流痕迹。幂等。
     */
    public void enable() {
        this.status = AccountStatus.ACTIVE;
        this.rateLimitedAt = null;
        this.rateLimitResetAt = null;
        touch();
    }

    /**
     * 手动禁用账号（充血状态迁移）。置 status=DISABLED。幂等。
     */
    public void disable() {
        this.status = AccountStatus.DISABLED;
        touch();
    }

    /**
     * 标记账号进入限流（充血状态迁移，上游 429 触发）。
     *
     * <p>记录进入限流时刻与恢复时刻，置 status=RATE_LIMITED。</p>
     *
     * @param resetAt 限流恢复时刻 epoch 秒（可空，未知恢复时间）
     */
    public void markRateLimited(Long resetAt) {
        this.status = AccountStatus.RATE_LIMITED;
        this.rateLimitedAt = Instant.now().getEpochSecond();
        this.rateLimitResetAt = resetAt;
        touch();
    }

    /**
     * 从限流恢复（充血状态迁移）。仅当前为 RATE_LIMITED 才迁回 ACTIVE 并清痕迹；否则无副作用。
     *
     * @return true=本次发生了恢复迁移；false=非限流态无需恢复
     */
    public boolean recoverFromRateLimit() {
        if (status != AccountStatus.RATE_LIMITED) {
            return false;
        }
        this.status = AccountStatus.ACTIVE;
        this.rateLimitedAt = null;
        this.rateLimitResetAt = null;
        touch();
        return true;
    }

    /**
     * 判断账号在给定时刻是否可调度（充血查询）。
     *
     * <p>领域规则：status=ACTIVE 且未过期（autoPauseOnExpired 时 expiresAt 已过则不可调度）
     * 且未在过载冷却窗内。限流态(RATE_LIMITED)/禁用态(DISABLED)直接不可调度。</p>
     *
     * @param now 判定时刻 epoch 秒
     * @return 可调度返回 true
     */
    public boolean isSchedulable(long now) {
        if (status != AccountStatus.ACTIVE) {
            return false;
        }
        if (autoPauseOnExpired && expiresAt != null && now >= expiresAt) {
            return false;
        }
        return overloadUntil == null || now >= overloadUntil;
    }

    /** 由仓储在保存后回填数据库主键。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    // ---- 校验/归一私有方法（不变量守护） ----

    private static String requireText(String raw, String field) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidAccountParameterException(field + " is required");
        }
        return v;
    }

    private static String normalizeJson(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static int normalizeConcurrency(Integer raw) {
        return (raw == null || raw < 1) ? DEFAULT_CONCURRENCY : raw;
    }

    private static int normalizePriority(Integer raw) {
        return (raw == null || raw < 0) ? DEFAULT_PRIORITY : raw;
    }

    // ---- 只读访问器（聚合状态对外只读；credentials 仅基础设施层持久化用，不进视图） ----

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 账号名 */
    public String name() {
        return name;
    }

    /** @return 供应商平台 */
    public String platform() {
        return platform;
    }

    /** @return 账号类型 */
    public String type() {
        return type;
    }

    /** @return 凭证 JSON（敏感，仅基础设施层使用，绝不下发任何视图） */
    public String credentials() {
        return credentials;
    }

    /** @return 并发度 */
    public int concurrency() {
        return concurrency;
    }

    /** @return 优先级 */
    public int priority() {
        return priority;
    }

    /** @return 账号状态 */
    public AccountStatus status() {
        return status;
    }

    /** @return 进入限流时刻 epoch 秒（可空） */
    public Long rateLimitedAt() {
        return rateLimitedAt;
    }

    /** @return 限流恢复时刻 epoch 秒（可空） */
    public Long rateLimitResetAt() {
        return rateLimitResetAt;
    }

    /** @return 过载冷却截止 epoch 秒（可空） */
    public Long overloadUntil() {
        return overloadUntil;
    }

    /** @return 过期时刻 epoch 秒（可空） */
    public Long expiresAt() {
        return expiresAt;
    }

    /** @return 过期是否自动暂停 */
    public boolean autoPauseOnExpired() {
        return autoPauseOnExpired;
    }

    /** @return 所属分组关联集合（只读副本） */
    public List<AccountGroupRef> groups() {
        return List.copyOf(groups);
    }

    /** @return 创建时间 epoch 秒 */
    public Long createdTime() {
        return createdTime;
    }

    /** @return 更新时间 epoch 秒 */
    public Long updatedTime() {
        return updatedTime;
    }
}
