package com.nexa.channel.domain.model;

import com.nexa.channel.domain.exception.ChannelOperationNotSupportedException;
import com.nexa.channel.domain.exception.InvalidChannelParameterException;
import com.nexa.channel.domain.vo.ChannelInfo;
import com.nexa.channel.domain.vo.ChannelStatus;
import com.nexa.channel.domain.vo.ChannelType;
import com.nexa.channel.domain.vo.CodexKeyCredential;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 渠道聚合根（充血领域模型，渠道管理一致性边界，F-2016~F-2028）。
 *
 * <p>承载上游渠道的全部可管理状态：类型、凭证（key，敏感）、状态、名称、权重、BaseURL、模型集、
 * 分组、优先级、自动禁用开关、余额、配额、模型映射、状态码映射、标签、附加设置、多 Key 信息、
 * 测试时间/响应时间。本聚合是渠道限界上下文的一致性边界，所有不变量与状态迁移在聚合方法上守护
 * （backend-engineer §2.2 充血、§2.4 战术完整）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring/Jackson），与 JPA 实体 {@code ChannelJpaEntity} 分离，可纯单测。
 * 字段对齐 DB-SCHEMA §3 channels 表 + openapi ChannelAdminView/ChannelCreateRequest。</p>
 *
 * <p>不变量：
 * <ul>
 *   <li>{@code type} 非空（渠道类型值对象，承载异步/Ollama 判定）。</li>
 *   <li>{@code key} 非空（上游凭证；敏感，绝不下发视图——视图 DTO 不读取本字段原文）。</li>
 *   <li>{@code models} 非空（渠道至少声明支持的模型集，逗号分隔；F-2016 创建要求 models 必填）。</li>
 *   <li>{@code status} 非空（启用/手动禁用/自动禁用三态）。</li>
 *   <li>{@code weight}/{@code priority}/{@code usedQuota} >=0；{@code balance} 用 BigDecimal（禁裸 float）。</li>
 *   <li>{@code statusCodeMapping} 长度 ≤1024（对齐 DB varchar(1024)）。</li>
 * </ul></p>
 */
public class Channel {

    /** status_code_mapping 最大长度，对齐 DB-SCHEMA §3 varchar(1024)。 */
    public static final int STATUS_CODE_MAPPING_MAX_LENGTH = 1024;

    /** group 最大长度，对齐 DB varchar(64)。 */
    public static final int GROUP_MAX_LENGTH = 64;

    /** 自增主键，未持久化为 null。 */
    private Long id;

    /** 渠道类型（含异步/Ollama 判定）。 */
    private ChannelType type;

    /** 上游凭证（敏感，绝不下发视图）。 */
    private String key;

    /** 渠道状态（启用/手动禁用/自动禁用）。 */
    private ChannelStatus status;

    /** 渠道名（可空，索引）。 */
    private String name;

    /** 权重（>=0）。 */
    private int weight;

    /** 上游 BaseURL（可空，缺省空串）。 */
    private String baseUrl;

    /** 支持模型集（逗号分隔串）。 */
    private String models;

    /** 分组（缺省 default）。 */
    private String group;

    /** 优先级（>=0）。 */
    private long priority;

    /** 是否自动禁用（1=是 0=否，与现网整数语义兼容）。 */
    private int autoBan;

    /** 余额（USD，BigDecimal，禁裸 float）。 */
    private BigDecimal balance;

    /** 已用配额（>=0）。 */
    private long usedQuota;

    /** 最近测试响应耗时 ms（可空）。 */
    private Integer responseTime;

    /** 最近测试时间 epoch 秒（可空）。 */
    private Long testTime;

    /** 模型映射 JSON（A→B，可空）。 */
    private String modelMapping;

    /** 状态码映射 JSON（≤1024，缺省空串）。 */
    private String statusCodeMapping;

    /** 标签（可空，索引；按 tag 批量启停）。 */
    private String tag;

    /** 附加设置 JSON（含 param/header 覆写，可空）。 */
    private String setting;

    /** 多 Key 信息值对象。 */
    private ChannelInfo channelInfo;

    /** 创建时间 epoch 秒。 */
    private final Long createdTime;

    private Channel(Long id, ChannelType type, String key, ChannelStatus status, String name,
                    int weight, String baseUrl, String models, String group, long priority,
                    int autoBan, BigDecimal balance, long usedQuota, Integer responseTime,
                    Long testTime, String modelMapping, String statusCodeMapping, String tag,
                    String setting, ChannelInfo channelInfo, Long createdTime) {
        this.id = id;
        this.type = type;
        this.key = key;
        this.status = status;
        this.name = name;
        this.weight = weight;
        this.baseUrl = baseUrl;
        this.models = models;
        this.group = group;
        this.priority = priority;
        this.autoBan = autoBan;
        this.balance = balance;
        this.usedQuota = usedQuota;
        this.responseTime = responseTime;
        this.testTime = testTime;
        this.modelMapping = modelMapping;
        this.statusCodeMapping = statusCodeMapping;
        this.tag = tag;
        this.setting = setting;
        this.channelInfo = channelInfo;
        this.createdTime = createdTime;
    }

    /**
     * 创建新渠道（工厂方法，充血行为，校验全部不变量，F-2016 create）。
     *
     * <p>领域规则：type/key/models 为必填（对齐 openapi ChannelCreateRequest required）；
     * 创建即启用（Status=ENABLED，openapi「Status=1 默认启用」）；balance 初始为 0、usedQuota 为 0；
     * group 缺省 default；statusCodeMapping 缺省空串并校验长度；打 createdTime。
     * 唯一性等跨聚合约束由仓储/DB 兜底，本工厂只保证字段合法。</p>
     *
     * @param type              渠道 type 整数码（必填）
     * @param key               上游凭证（必填，敏感）
     * @param models            支持模型集（必填，逗号分隔）
     * @param name              渠道名（可空）
     * @param group             分组（可空→default）
     * @param priority          优先级（<0→0）
     * @param weight            权重（<0→0）
     * @param autoBan           自动禁用开关（缺省 1）
     * @param baseUrl           上游 BaseURL（可空→空串）
     * @param modelMapping      模型映射 JSON（可空）
     * @param statusCodeMapping 状态码映射 JSON（可空→空串，≤1024）
     * @param tag               标签（可空）
     * @param setting           附加设置 JSON（含 param/header 覆写，可空）
     * @param channelInfo       多 Key 信息（可空→单 Key 缺省）
     * @return 待持久化的新渠道（id 由仓储保存后回填）
     * @throws InvalidChannelParameterException 字段非法
     */
    public static Channel create(Integer type, String key, String models, String name, String group,
                                 Long priority, Integer weight, Integer autoBan, String baseUrl,
                                 String modelMapping, String statusCodeMapping, String tag,
                                 String setting, ChannelInfo channelInfo) {
        ChannelType t = new ChannelType(requireType(type));
        String k = requireKey(key);
        String m = requireModels(models);
        return new Channel(
                null, t, k, ChannelStatus.ENABLED, normalizeName(name),
                normalizeNonNegativeInt(weight), normalizeBaseUrl(baseUrl), m, normalizeGroup(group),
                normalizeNonNegativeLong(priority), normalizeAutoBan(autoBan), BigDecimal.ZERO, 0L,
                null, null, normalizeJson(modelMapping), requireStatusCodeMapping(statusCodeMapping),
                normalizeTag(tag), normalizeJson(setting),
                channelInfo == null ? ChannelInfo.single() : channelInfo,
                Instant.now().getEpochSecond());
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发创建不变量与时间打点）。
     *
     * @return 重建的聚合（参数语义见各字段注释）
     */
    public static Channel rehydrate(Long id, int type, String key, int status, String name,
                                    int weight, String baseUrl, String models, String group,
                                    long priority, int autoBan, BigDecimal balance, long usedQuota,
                                    Integer responseTime, Long testTime, String modelMapping,
                                    String statusCodeMapping, String tag, String setting,
                                    ChannelInfo channelInfo, Long createdTime) {
        return new Channel(
                id, new ChannelType(type), key, ChannelStatus.fromCode(status), name, weight,
                baseUrl == null ? "" : baseUrl, models == null ? "" : models,
                group == null ? "default" : group, priority, autoBan,
                balance == null ? BigDecimal.ZERO : balance, usedQuota, responseTime, testTime,
                modelMapping, statusCodeMapping == null ? "" : statusCodeMapping, tag, setting,
                channelInfo == null ? ChannelInfo.single() : channelInfo, createdTime);
    }

    /**
     * 覆盖式编辑渠道（充血行为，F-2016 update，含 F-2020/F-2021/F-2022/F-2025）。
     *
     * <p>领域规则：openapi PUT 渠道为「覆盖式」。type/models 必填校验；key 为<b>可选更新</b>——
     * 传 null/空白表示「保留原 key 不变」（避免视图脱敏回显后回写空值清空凭证，与 oauthprovider 同思路）；
     * name/group/priority/weight/autoBan/baseUrl/modelMapping/statusCodeMapping/tag/setting/channelInfo
     * 全量覆盖并经各自校验/归一。status 不在编辑路径改动（启停走专门方法）。</p>
     *
     * @param type              新 type（必填）
     * @param newKey            新 key（null/空白=保留原 key）
     * @param models            新模型集（必填）
     * @param name              新名称（可空）
     * @param group             新分组（可空→default）
     * @param priority          新优先级
     * @param weight            新权重
     * @param autoBan           新自动禁用开关
     * @param baseUrl           新 BaseURL
     * @param modelMapping      新模型映射 JSON
     * @param statusCodeMapping 新状态码映射 JSON（≤1024）
     * @param tag               新标签
     * @param setting           新附加设置 JSON
     * @param channelInfo       新多 Key 信息（可空→单 Key 缺省）
     * @throws InvalidChannelParameterException 字段非法
     */
    public void update(Integer type, String newKey, String models, String name, String group,
                       Long priority, Integer weight, Integer autoBan, String baseUrl,
                       String modelMapping, String statusCodeMapping, String tag,
                       String setting, ChannelInfo channelInfo) {
        this.type = new ChannelType(requireType(type));
        this.models = requireModels(models);
        if (newKey != null && !newKey.isBlank()) {
            // 仅当显式给出新 key 时替换；否则保留原 key（不被脱敏回显的占位值覆盖）。
            this.key = requireKey(newKey);
        }
        this.name = normalizeName(name);
        this.group = normalizeGroup(group);
        this.priority = normalizeNonNegativeLong(priority);
        this.weight = normalizeNonNegativeInt(weight);
        this.autoBan = normalizeAutoBan(autoBan);
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.modelMapping = normalizeJson(modelMapping);
        this.statusCodeMapping = requireStatusCodeMapping(statusCodeMapping);
        this.tag = normalizeTag(tag);
        this.setting = normalizeJson(setting);
        this.channelInfo = channelInfo == null ? ChannelInfo.single() : channelInfo;
    }

    /**
     * 启用渠道（充血状态迁移，F-2019 按 tag enable / 批量启用）。
     *
     * <p>领域规则：置 Status=ENABLED。幂等——已启用再启用无副作用。</p>
     */
    public void enable() {
        this.status = ChannelStatus.ENABLED;
    }

    /**
     * 手动禁用渠道（充血状态迁移，F-2019 按 tag disable / 批量禁用）。
     *
     * <p>领域规则：置 Status=MANUALLY_DISABLED（手动禁用，不被自动恢复机制启用，
     * 区别于自动禁用）。幂等。</p>
     */
    public void disable() {
        this.status = ChannelStatus.MANUALLY_DISABLED;
    }

    /**
     * 记录一次连通性测试结果（充血行为，F-2017，写 test_time/response_time）。
     *
     * <p>领域规则：测试完成后更新最近测试时间与响应耗时（openapi「写 test_time/response_time」）。
     * 不在此处依据结果自动启停（自动禁用为独立选渠路由职责，超出本片范围）。</p>
     *
     * @param responseTimeMs 本次测试响应耗时 ms（>=0）
     * @param testedAt       测试时刻
     */
    public void recordTestResult(int responseTimeMs, Instant testedAt) {
        this.responseTime = Math.max(0, responseTimeMs);
        this.testTime = (testedAt == null ? Instant.now() : testedAt).getEpochSecond();
    }

    /**
     * 更新余额（充血行为，F-2018，写 balance）。
     *
     * <p>领域规则：余额来自上游查询，单位 USD，用 BigDecimal（禁裸 float）。null 视为非法。</p>
     *
     * @param newBalance 上游返回的最新余额（非空）
     * @throws InvalidChannelParameterException 余额为 null
     */
    public void updateBalance(BigDecimal newBalance) {
        if (newBalance == null) {
            throw new InvalidChannelParameterException("balance must not be null");
        }
        this.balance = newBalance;
    }

    /**
     * 应用上游探测出的模型集到渠道（充血行为，F-2026 覆盖式应用，更新 models）。
     *
     * <p>领域规则：openapi「探测结果应用到渠道」为覆盖式——将勾选的上游模型集去重、保序后
     * 覆盖渠道 models（逗号分隔串）。空集合视为非法（应用必须至少一个模型）。</p>
     *
     * @param upstreamModels 勾选的上游模型集（非空、至少一个非空白模型名）
     * @throws InvalidChannelParameterException 模型集为空或全为空白
     */
    public void applyUpstreamModels(java.util.List<String> upstreamModels) {
        if (upstreamModels == null || upstreamModels.isEmpty()) {
            throw new InvalidChannelParameterException("models to apply must not be empty");
        }
        // 去重 + 保序 + 去空白，覆盖式拼为逗号分隔串。
        Set<String> normalized = upstreamModels.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new InvalidChannelParameterException("models to apply must not be all blank");
        }
        this.models = String.join(",", normalized);
    }

    /**
     * 护栏：要求本渠道支持同步连通性测试（F-2017）。
     *
     * <p>领域规则：七类异步渠道（MJ/Suno/Kling/Jimeng/DoubaoVideo/Vidu）走异步任务、无同步 chat
     * 探测语义，不支持连通性测试，抛 {@link ChannelOperationNotSupportedException}（→400）。</p>
     *
     * @throws ChannelOperationNotSupportedException 当渠道为异步类型
     */
    public void ensureSyncTestable() {
        if (type.isAsync()) {
            throw new ChannelOperationNotSupportedException(
                    "channel type does not support sync connectivity test: " + type.code());
        }
    }

    /**
     * 护栏：要求本渠道为 Ollama 类型（F-2027 Ollama 模型管理前置）。
     *
     * @throws ChannelOperationNotSupportedException 当渠道非 Ollama 类型
     */
    public void ensureOllama() {
        if (!type.isOllama()) {
            throw new ChannelOperationNotSupportedException(
                    "ollama management is only applicable to ollama channels, got type: " + type.code());
        }
    }

    /**
     * 护栏：要求本渠道为 Codex 类型（F-4045 Codex 上游用量查询前置）。
     *
     * <p>领域规则来源：API-ENDPOINTS §5.8 / BACKLOG F-4045——非 Codex 类型查询用量返回
     * 「channel type is not Codex」。文案与契约逐字对齐（前端据此提示）。</p>
     *
     * @throws ChannelOperationNotSupportedException 当渠道非 Codex 类型
     */
    public void ensureCodex() {
        if (!type.isCodex()) {
            throw new ChannelOperationNotSupportedException("channel type is not Codex");
        }
    }

    /**
     * 护栏：要求本渠道为单 Key 渠道（F-4045 Codex 用量查询不支持多 Key 渠道）。
     *
     * <p>领域规则来源：API-ENDPOINTS §5.8 / BACKLOG F-4045——multi-key 渠道返回
     * 「multi-key channel is not supported」。Codex 用量按单一 OAuth 凭证查询并可能回写该凭证，
     * 多 Key 渠道无单一确定凭证可解析/回写，故拒绝。文案与契约逐字对齐。</p>
     *
     * @throws ChannelOperationNotSupportedException 当渠道为多 Key 渠道
     */
    public void ensureSingleKey() {
        if (channelInfo != null && channelInfo.multiKey()) {
            throw new ChannelOperationNotSupportedException("multi-key channel is not supported");
        }
    }

    /**
     * 解析本渠道 key 为 Codex OAuth 凭证（充血行为，F-4045 用量查询前置）。
     *
     * <p>领域规则：先经 {@link #ensureCodex()} + {@link #ensureSingleKey()} 护栏（调用方应已校验），
     * 再把敏感 key 原文解析为 {@link CodexKeyCredential}（access_token/account_id 缺失抛 400）。
     * 凭证解析逻辑在领域内表达不变量，凭证明文不外泄（值对象 toString 已脱敏）。</p>
     *
     * @return 解析后的 Codex 凭证值对象
     * @throws com.nexa.channel.domain.exception.InvalidChannelParameterException key 缺失必填段
     */
    public CodexKeyCredential codexCredential() {
        return CodexKeyCredential.parse(key);
    }

    /**
     * 在凭证自动刷新后回写渠道 key（充血行为，F-4045 副作用：上游 401/403 刷新 token 后回写）。
     *
     * <p>领域规则来源：API-ENDPOINTS §5.8——上游 401/403 且有 refresh_token 时自动
     * RefreshCodexOAuthToken 重试并回写渠道 key + InitChannelCache（仅 status∈{1,3} 渠道）。
     * 本方法只负责「回写新 key」这一聚合内状态变更；缓存重建（InitChannelCache）由应用层依据
     * {@link #shouldReinitCacheAfterKeyRefresh()} 判定后触发（缓存属基础设施关注点，不在聚合内）。</p>
     *
     * @param newKey 刷新后的新 key 原文（非空白）
     * @throws InvalidChannelParameterException 新 key 为空白
     */
    public void refreshCodexKey(String newKey) {
        this.key = requireKey(newKey);
    }

    /**
     * 判断凭证回写后是否应重建渠道缓存（F-4045 副作用条件）。
     *
     * <p>领域规则：仅 status∈{ENABLED(1), AUTO_DISABLED(3)} 的渠道在 key 回写后需 InitChannelCache
     * （这两态渠道仍可能参与/恢复选渠路由，缓存须与新凭证一致）；手动禁用(2)不参与路由、无需重建。</p>
     *
     * @return status 为启用或自动禁用时返回 true
     */
    public boolean shouldReinitCacheAfterKeyRefresh() {
        return status == ChannelStatus.ENABLED || status == ChannelStatus.AUTO_DISABLED;
    }

    /**
     * 判断渠道 tag 是否匹配给定 tag（F-2019 按 tag 批量启停的筛选条件）。
     *
     * @param targetTag 目标 tag（非空白）
     * @return 渠道 tag 等于目标 tag 返回 true
     */
    public boolean hasTag(String targetTag) {
        return tag != null && tag.equals(targetTag);
    }

    /** 由仓储在保存后回填数据库主键。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    // ---- 校验/归一私有方法（不变量守护） ----

    private static int requireType(Integer type) {
        if (type == null) {
            throw new InvalidChannelParameterException("type is required");
        }
        return type;
    }

    private static String requireKey(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidChannelParameterException("key is required");
        }
        return v;
    }

    private static String requireModels(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidChannelParameterException("models is required");
        }
        // 去重保序归一逗号分隔串（容错多余空白/空段）。
        Set<String> normalized = Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new InvalidChannelParameterException("models is required");
        }
        return String.join(",", normalized);
    }

    private static String requireStatusCodeMapping(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.length() > STATUS_CODE_MAPPING_MAX_LENGTH) {
            throw new InvalidChannelParameterException(
                    "status_code_mapping length must be <= " + STATUS_CODE_MAPPING_MAX_LENGTH);
        }
        return v;
    }

    private static String normalizeGroup(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            return "default";
        }
        if (v.length() > GROUP_MAX_LENGTH) {
            throw new InvalidChannelParameterException("group length must be <= " + GROUP_MAX_LENGTH);
        }
        return v;
    }

    private static String normalizeName(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String normalizeTag(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String normalizeBaseUrl(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null) ? "" : v;
    }

    private static String normalizeJson(String raw) {
        String v = raw == null ? null : raw.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static int normalizeNonNegativeInt(Integer raw) {
        return (raw == null || raw < 0) ? 0 : raw;
    }

    private static long normalizeNonNegativeLong(Long raw) {
        return (raw == null || raw < 0) ? 0L : raw;
    }

    private static int normalizeAutoBan(Integer raw) {
        // 缺省 1（开启自动禁用）；非 0 值归一为 1，0 为关闭（与现网整数语义兼容）。
        if (raw == null) {
            return 1;
        }
        return raw == 0 ? 0 : 1;
    }

    // ---- 只读访问器（聚合状态对外只读；key 仅基础设施层持久化用，不进视图） ----

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 渠道类型值对象 */
    public ChannelType type() {
        return type;
    }

    /** @return 上游凭证（敏感，仅基础设施层使用，绝不下发到任何视图） */
    public String key() {
        return key;
    }

    /** @return 渠道状态 */
    public ChannelStatus status() {
        return status;
    }

    /** @return 渠道名（可空） */
    public String name() {
        return name;
    }

    /** @return 权重 */
    public int weight() {
        return weight;
    }

    /** @return 上游 BaseURL */
    public String baseUrl() {
        return baseUrl;
    }

    /** @return 支持模型集（逗号分隔串） */
    public String models() {
        return models;
    }

    /** @return 分组 */
    public String group() {
        return group;
    }

    /** @return 优先级 */
    public long priority() {
        return priority;
    }

    /** @return 自动禁用开关（1=是 0=否） */
    public int autoBan() {
        return autoBan;
    }

    /** @return 余额（USD，BigDecimal） */
    public BigDecimal balance() {
        return balance;
    }

    /** @return 已用配额 */
    public long usedQuota() {
        return usedQuota;
    }

    /** @return 最近测试响应耗时 ms（可空） */
    public Integer responseTime() {
        return responseTime;
    }

    /** @return 最近测试时间 epoch 秒（可空） */
    public Long testTime() {
        return testTime;
    }

    /** @return 模型映射 JSON（可空） */
    public String modelMapping() {
        return modelMapping;
    }

    /** @return 状态码映射 JSON */
    public String statusCodeMapping() {
        return statusCodeMapping;
    }

    /** @return 标签（可空） */
    public String tag() {
        return tag;
    }

    /** @return 附加设置 JSON（可空） */
    public String setting() {
        return setting;
    }

    /** @return 多 Key 信息值对象 */
    public ChannelInfo channelInfo() {
        return channelInfo;
    }

    /** @return 创建时间 epoch 秒（可空） */
    public Long createdTime() {
        return createdTime;
    }
}
