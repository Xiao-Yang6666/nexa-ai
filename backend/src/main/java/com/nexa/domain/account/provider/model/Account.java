package com.nexa.domain.account.provider.model;

import com.nexa.domain.account.provider.exception.InvalidAccountParameterException;
import com.nexa.domain.account.provider.vo.AccountGroupRef;
import com.nexa.domain.account.provider.vo.AccountStatus;

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
 * <p>零框架依赖（不 import JPA/Spring/Jackson），与 JPA 实体 {@code AccountPO} 分离，可纯单测。
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

    /** 上游 API base url（可空；转发用，空则回落 channel.baseUrl）。 */
    private String baseUrl;

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

    /** 账号级售价倍率（>=0，默认 1.0；售价 = A倍率 × group倍率 × account倍率）。 */
    private java.math.BigDecimal rateMultiplier;

    /** 模型映射 JSON（A→B，可空；如 {"gpt-4":"gpt-4-turbo"}）。 */
    private String modelMapping;

    /** 路由权重（>=0，默认 0）。 */
    private int weight;

    /** 标签（可空，批量操作用）。 */
    private String tag;

    /** 自动封禁标志（失败达阈值自动禁用）。 */
    private boolean autoBan;

    /** 上次测试响应时间（毫秒，可空）。 */
    private Integer responseTime;

    /** 上次测试时间 epoch 秒（可空）。 */
    private Long testTime;

    /** 账户余额 USD（可空）。 */
    private java.math.BigDecimal balance;

    /** 已用配额（可空）。 */
    private java.math.BigDecimal usedQuota;

    /** 支持的模型列表（逗号分隔，可空）。 */
    private String models;

    /** 所属分组关联集合（聚合内成员）。 */
    private List<AccountGroupRef> groups;

    /** 创建时间 epoch 秒。 */
    private final Long createdTime;

    /** 更新时间 epoch 秒。 */
    private Long updatedTime;

    private Account(Long id, String name, String platform, String type, String credentials,
                    String baseUrl, int concurrency, int priority, AccountStatus status, Long rateLimitedAt,
                    Long rateLimitResetAt, Long overloadUntil, Long expiresAt,
                    boolean autoPauseOnExpired, java.math.BigDecimal rateMultiplier,
                    String modelMapping, int weight, String tag, boolean autoBan,
                    Integer responseTime, Long testTime, java.math.BigDecimal balance,
                    java.math.BigDecimal usedQuota, String models,
                    List<AccountGroupRef> groups, Long createdTime, Long updatedTime) {
        this.id = id;
        this.name = name;
        this.platform = platform;
        this.type = type;
        this.credentials = credentials;
        this.baseUrl = baseUrl;
        this.concurrency = concurrency;
        this.priority = priority;
        this.status = status;
        this.rateLimitedAt = rateLimitedAt;
        this.rateLimitResetAt = rateLimitResetAt;
        this.overloadUntil = overloadUntil;
        this.expiresAt = expiresAt;
        this.autoPauseOnExpired = autoPauseOnExpired;
        this.rateMultiplier = normalizeMultiplier(rateMultiplier);
        this.modelMapping = normalizeJson(modelMapping);
        this.weight = weight < 0 ? 0 : weight;
        this.tag = normalizeText(tag);
        this.autoBan = autoBan;
        this.responseTime = responseTime;
        this.testTime = testTime;
        this.balance = balance;
        this.usedQuota = usedQuota;
        this.models = normalizeText(models);
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
     * @param baseUrl            上游 API base url（可空）
     * @param concurrency        并发度（可空/&lt;1→默认 3）
     * @param priority           优先级（可空/&lt;0→0；缺省 50）
     * @param expiresAt          过期时刻 epoch 秒（可空）
     * @param autoPauseOnExpired 过期自动暂停（可空→true）
     * @param rateMultiplier     账号级售价倍率（可空/&lt;0→1.0）
     * @param modelMapping       模型映射 JSON（可空）
     * @param weight             路由权重（可空/&lt;0→0）
     * @param tag                标签（可空）
     * @param autoBan            自动封禁标志
     * @param models             支持的模型列表（可空）
     * @param groups             所属分组集合（可空）
     * @return 待持久化的新账号（id 由仓储保存后回填）
     * @throws InvalidAccountParameterException 字段非法
     */
    public static Account create(String name, String platform, String type, String credentials,
                                 String baseUrl, Integer concurrency, Integer priority, Long expiresAt,
                                 Boolean autoPauseOnExpired, java.math.BigDecimal rateMultiplier,
                                 String modelMapping, Integer weight, String tag, Boolean autoBan,
                                 String models, List<AccountGroupRef> groups) {
        long now = Instant.now().getEpochSecond();
        return new Account(
                null,
                requireText(name, "name"),
                requireText(platform, "platform"),
                requireText(type, "type"),
                normalizeJson(credentials),
                normalizeText(baseUrl),
                normalizeConcurrency(concurrency),
                normalizePriority(priority),
                AccountStatus.ACTIVE,
                null, null, null,
                expiresAt,
                autoPauseOnExpired == null || autoPauseOnExpired,
                normalizeMultiplier(rateMultiplier),
                modelMapping,
                weight == null || weight < 0 ? 0 : weight,
                tag,
                autoBan != null && autoBan,
                null, null, null, null,
                models,
                groups,
                now, now);
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发创建不变量与时间打点）。
     *
     * @return 重建的账号聚合
     */
    public static Account rehydrate(Long id, String name, String platform, String type,
                                    String credentials, String baseUrl, int concurrency, int priority,
                                    String status, Long rateLimitedAt, Long rateLimitResetAt,
                                    Long overloadUntil, Long expiresAt, boolean autoPauseOnExpired,
                                    java.math.BigDecimal rateMultiplier,
                                    String modelMapping, int weight, String tag, boolean autoBan,
                                    Integer responseTime, Long testTime, java.math.BigDecimal balance,
                                    java.math.BigDecimal usedQuota, String models,
                                    List<AccountGroupRef> groups, Long createdTime, Long updatedTime) {
        return new Account(id, name, platform, type, credentials, baseUrl, concurrency, priority,
                AccountStatus.fromCode(status), rateLimitedAt, rateLimitResetAt, overloadUntil,
                expiresAt, autoPauseOnExpired, rateMultiplier,
                modelMapping, weight, tag, autoBan, responseTime, testTime, balance, usedQuota, models,
                groups, createdTime, updatedTime);
    }

    /**
     * 覆盖式编辑账号（充血行为，PUT 语义）。
     *
     * <p>领域规则：name/platform/type 必填校验；credentials 为<b>可选更新</b>——传 null/空白表示
     * 「保留原 credentials 不变」（避免视图脱敏后回写空值清空凭证）；其余字段全量覆盖并归一。
     * status 不在编辑路径改动（启停/限流走专门方法）。刷新 updatedTime。</p>
     */
    public void update(String name, String platform, String type, String newCredentials,
                       String baseUrl, Integer concurrency, Integer priority, Long expiresAt,
                       Boolean autoPauseOnExpired, java.math.BigDecimal rateMultiplier,
                       String modelMapping, Integer weight, String tag, Boolean autoBan,
                       String models, List<AccountGroupRef> groups) {
        this.name = requireText(name, "name");
        this.platform = requireText(platform, "platform");
        this.type = requireText(type, "type");
        if (newCredentials != null && !newCredentials.isBlank()) {
            this.credentials = newCredentials.trim();
        }
        this.baseUrl = normalizeText(baseUrl);
        this.concurrency = normalizeConcurrency(concurrency);
        this.priority = normalizePriority(priority);
        this.expiresAt = expiresAt;
        if (autoPauseOnExpired != null) {
            this.autoPauseOnExpired = autoPauseOnExpired;
        }
        this.rateMultiplier = normalizeMultiplier(rateMultiplier);
        this.modelMapping = normalizeJson(modelMapping);
        this.weight = weight == null || weight < 0 ? 0 : weight;
        this.tag = normalizeText(tag);
        this.autoBan = autoBan != null && autoBan;
        this.models = normalizeText(models);
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
     * 标记账号进入过载冷却（充血状态迁移，上游 529 触发）。
     *
     * <p>设置过载冷却截止时刻 overloadUntil；status 保持 ACTIVE（过载是临时冷却，非限流/禁用）。
     * 冷却窗内 {@link #isSchedulable(long)} 返回 false，窗口过后自动恢复可调度，无需显式 recover。</p>
     *
     * @param until 过载冷却截止时刻 epoch 秒（应 &gt; now）
     */
    public void markOverloaded(Long until) {
        this.overloadUntil = until;
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
     * 应用模型映射（A→B，充血查询）。
     *
     * <p>从 modelMapping JSON 解析公开模型 A 映射到上游模型 B。
     * 无映射或解析失败返回原值（身份映射 B=A）。</p>
     *
     * @param publicModel 公开模型名 A
     * @return 上游模型名 B（未映射返回 A）
     */
    public String applyModelMapping(String publicModel) {
        if (modelMapping == null || modelMapping.isBlank()) {
            return publicModel;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(modelMapping)
                    .path(publicModel);
            if (node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        } catch (Exception ignored) {
        }
        return publicModel;
    }

    /**
     * 记录测试结果（充血行为）。
     *
     * @param responseTimeMs 响应时间（毫秒）
     */
    public void recordTestResult(int responseTimeMs) {
        this.responseTime = responseTimeMs;
        this.testTime = Instant.now().getEpochSecond();
        touch();
    }

    /**
     * 更新余额（充血行为）。
     *
     * @param delta 余额变化量（可正可负）
     */
    public void updateBalance(java.math.BigDecimal delta) {
        if (delta == null) {
            return;
        }
        this.balance = (this.balance == null ? java.math.BigDecimal.ZERO : this.balance).add(delta);
        touch();
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

    /** 文本归一：去空白；空 → null。 */
    private static String normalizeText(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static int normalizeConcurrency(Integer raw) {
        return (raw == null || raw < 1) ? DEFAULT_CONCURRENCY : raw;
    }

    private static int normalizePriority(Integer raw) {
        return (raw == null || raw < 0) ? DEFAULT_PRIORITY : raw;
    }

    /** 倍率归一：null 或负 → 1.0（不打折/不加价）。 */
    private static java.math.BigDecimal normalizeMultiplier(java.math.BigDecimal raw) {
        return (raw == null || raw.signum() < 0) ? java.math.BigDecimal.ONE : raw;
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

    /** @return 上游 API base url（可空；转发用，空则回落 channel.baseUrl） */
    public String baseUrl() {
        return baseUrl;
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

    /** @return 账号级售价倍率（>=0，默认 1.0） */
    public java.math.BigDecimal rateMultiplier() {
        return rateMultiplier;
    }

    /** @return 模型映射 JSON（可空） */
    public String modelMapping() {
        return modelMapping;
    }

    /** @return 路由权重（>=0） */
    public int weight() {
        return weight;
    }

    /** @return 标签（可空） */
    public String tag() {
        return tag;
    }

    /** @return 自动封禁标志 */
    public boolean autoBan() {
        return autoBan;
    }

    /** @return 上次测试响应时间（毫秒，可空） */
    public Integer responseTime() {
        return responseTime;
    }

    /** @return 上次测试时间 epoch 秒（可空） */
    public Long testTime() {
        return testTime;
    }

    /** @return 账户余额 USD（可空） */
    public java.math.BigDecimal balance() {
        return balance;
    }

    /** @return 已用配额（可空） */
    public java.math.BigDecimal usedQuota() {
        return usedQuota;
    }

    /** @return 支持的模型列表（逗号分隔，可空） */
    public String models() {
        return models;
    }

    /**
     * 是否精确支持某模型名（方案乙：选账号按模型 A 反查的精确判定，充血行为）。
     *
     * <p>{@link #models} 是逗号分隔串，本方法按逗号切分后<b>精确</b>比对（去首尾空白），避免 SQL LIKE
     * 粗筛的子串误命中（如 "gpt-4" 误命中 "gpt-4o"）。models 为空（账号未声明可服务模型）时返回
     * {@code false}（无声明即不可按模型选中，避免空账号被误选）。</p>
     *
     * @param model 待判定模型名 A（null/空白→false）
     * @return 精确命中返回 {@code true}
     */
    public boolean supportsModel(String model) {
        if (model == null || model.isBlank() || models == null || models.isBlank()) {
            return false;
        }
        String target = model.trim();
        for (String m : models.split(",")) {
            if (target.equals(m.trim())) {
                return true;
            }
        }
        return false;
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
