package com.nexa.model.domain.model;

import com.nexa.model.domain.exception.InvalidModelParameterException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 对外模型商品目录聚合根（充血领域模型，F-6001）。
 *
 * <p>领域规则来源：COMPAT-BILLING-DECISIONS §3「模型分级」+ §4「成本/售价分离（售价端）」+
 * DB-SCHEMA §16 PublicModel。一个对外模型（公开名 A）一条记录；品质不同拆独立记录分别定价
 * （如 {@code opus-4.8}/{@code opus-4.8-max}/{@code opus-4.8-air} = 三条，quality_tier=full/max/air）。
 * 对外全集 = {@code enabled=true AND deleted_at IS NULL} 的 public_name 集（商品目录唯一权威）。
 * <b>售价对客户恒定</b>（与渠道无关，COMPAT §4）。</p>
 *
 * <p>本聚合守护的不变量：
 * <ul>
 *   <li>{@code publicName}（A）必填非空白、≤255——空 → 「对外模型名不能为空」。</li>
 *   <li>{@code basePriceRatio}/{@code basePrice} 非负（用 {@link BigDecimal} 防精度漂移，
 *       DB-SCHEMA §16 numeric）——负值 → 「价格倍率不能为负」。</li>
 *   <li>{@code publicName} 全局唯一（uk_public_name）由应用层 + DB 唯一索引双重保证（跨聚合不变量）。</li>
 * </ul>
 * </p>
 *
 * <p><b>F-6004 模型权限全开</b>：可用性唯一裁决 = {@code enabled=true}（上架即全员可用），
 * 不再用分组圈定可见模型（COMPAT §5），故无独立权限字段。</p>
 *
 * <p>DDD：domain 零框架依赖（纯 Java，可单测无需起 Spring/DB）。行为在聚合方法上（create/update/
 * enable/disable），非贫血 getter/setter。</p>
 */
public class PublicModel {

    /** 对外模型名（A）最大长度（对齐 DB-SCHEMA §16 {@code public_name varchar(255)}）。 */
    public static final int PUBLIC_NAME_MAX_LENGTH = 255;

    /** 品质档默认值（DB-SCHEMA §16 {@code quality_tier default 'full'}，纯展示，不限枚举）。 */
    public static final String DEFAULT_QUALITY_TIER = "full";

    private Long id;
    private String publicName;
    private String qualityTier;
    private BigDecimal basePriceRatio;
    private Boolean usePrice;
    private BigDecimal basePrice;
    private Boolean enabled;
    private String displayName;
    private Integer sortOrder;
    private String description;
    private Long createdTime;
    private Long updatedTime;

    private PublicModel() {
    }

    /**
     * 创建新对外模型（F-6001 POST，未持久化，id 为 null）。
     *
     * @param publicName     对外名 A（必填非空白，≤255）
     * @param qualityTier    品质档（可空 → 缺省 {@code full}；纯展示不限枚举）
     * @param basePriceRatio 基准售价倍率（可空 → 0；非负）
     * @param usePrice       是否按次固定价（可空 → false）
     * @param basePrice      固定单价（usePrice=true 时生效；可空 → 0；非负）
     * @param enabled        是否上架（可空 → true，上架即全员可用 F-6004）
     * @param displayName    展示名（可空）
     * @param sortOrder      排序（可空 → 0）
     * @param description    描述（可空）
     * @return 新建对外模型聚合
     * @throws InvalidModelParameterException A 为空白/超长，或价格为负
     */
    public static PublicModel create(String publicName, String qualityTier, BigDecimal basePriceRatio,
                                     Boolean usePrice, BigDecimal basePrice, Boolean enabled,
                                     String displayName, Integer sortOrder, String description) {
        PublicModel m = new PublicModel();
        m.publicName = normalizePublicName(publicName);
        m.qualityTier = normalizeTier(qualityTier);
        m.basePriceRatio = normalizePrice(basePriceRatio, "基准售价倍率");
        m.usePrice = usePrice != null && usePrice;
        m.basePrice = normalizePrice(basePrice, "固定单价");
        m.enabled = enabled == null || enabled;
        m.displayName = blankToEmpty(displayName);
        m.sortOrder = sortOrder == null ? 0 : sortOrder;
        m.description = blankToEmpty(description);
        long now = Instant.now().getEpochSecond();
        m.createdTime = now;
        m.updatedTime = now;
        return m;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验，信任已落库数据）。
     *
     * @param id             主键
     * @param publicName     对外名 A
     * @param qualityTier    品质档
     * @param basePriceRatio 基准售价倍率
     * @param usePrice       是否按次固定价
     * @param basePrice      固定单价
     * @param enabled        是否上架
     * @param displayName    展示名
     * @param sortOrder      排序
     * @param description    描述
     * @param createdTime    创建时间 epoch 秒
     * @param updatedTime    更新时间 epoch 秒
     * @return 重建的聚合
     */
    public static PublicModel rehydrate(Long id, String publicName, String qualityTier,
                                        BigDecimal basePriceRatio, Boolean usePrice, BigDecimal basePrice,
                                        Boolean enabled, String displayName, Integer sortOrder,
                                        String description, Long createdTime, Long updatedTime) {
        PublicModel m = new PublicModel();
        m.id = id;
        m.publicName = publicName;
        m.qualityTier = qualityTier;
        m.basePriceRatio = basePriceRatio == null ? BigDecimal.ZERO : basePriceRatio;
        m.usePrice = usePrice != null && usePrice;
        m.basePrice = basePrice == null ? BigDecimal.ZERO : basePrice;
        m.enabled = enabled == null || enabled;
        m.displayName = displayName == null ? "" : displayName;
        m.sortOrder = sortOrder == null ? 0 : sortOrder;
        m.description = description == null ? "" : description;
        m.createdTime = createdTime;
        m.updatedTime = updatedTime;
        return m;
    }

    /**
     * 覆盖式更新对外模型（F-6001 PUT，含上下架）。
     *
     * <p>A（public_name）是品质拆分后的稳定商品键，更新不可改名（改名等价新商品，应新建）；
     * 故本方法不接受 publicName 入参（对齐 openapi PublicModelUpdateRequest 无 public_name）。</p>
     *
     * @param qualityTier    新品质档（可空 → 不改）
     * @param basePriceRatio 新基准售价倍率（可空 → 不改；非负）
     * @param usePrice       新是否按次固定价（可空 → 不改）
     * @param basePrice      新固定单价（可空 → 不改；非负）
     * @param enabled        新上下架（可空 → 不改）
     * @param displayName    新展示名（可空 → 不改）
     * @param sortOrder      新排序（可空 → 不改）
     * @throws InvalidModelParameterException 价格为负
     */
    public void update(String qualityTier, BigDecimal basePriceRatio, Boolean usePrice,
                       BigDecimal basePrice, Boolean enabled, String displayName, Integer sortOrder) {
        if (qualityTier != null && !qualityTier.isBlank()) {
            this.qualityTier = qualityTier.trim();
        }
        if (basePriceRatio != null) {
            this.basePriceRatio = normalizePrice(basePriceRatio, "基准售价倍率");
        }
        if (usePrice != null) {
            this.usePrice = usePrice;
        }
        if (basePrice != null) {
            this.basePrice = normalizePrice(basePrice, "固定单价");
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (displayName != null) {
            this.displayName = displayName.trim();
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        touch();
    }

    /** 持久化后回填自增 id（仅基础设施层 save 后调用）。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    /**
     * 对外名归一与校验（去空白、非空、长度护栏）。
     *
     * @param raw 原始名
     * @return 归一后名称
     * @throws InvalidModelParameterException 空白或超长
     */
    private static String normalizePublicName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            // 领域规则来源：F-6001 对外模型必须有公开名 A（商品目录主键语义）。
            throw new InvalidModelParameterException("对外模型名不能为空");
        }
        if (n.length() > PUBLIC_NAME_MAX_LENGTH) {
            throw new InvalidModelParameterException("对外模型名长度不能超过 " + PUBLIC_NAME_MAX_LENGTH);
        }
        return n;
    }

    private static String normalizeTier(String raw) {
        String t = raw == null ? "" : raw.trim();
        return t.isEmpty() ? DEFAULT_QUALITY_TIER : t;
    }

    /**
     * 价格归一与非负校验（COMPAT §4 售价；BigDecimal 防精度漂移）。
     *
     * @param raw   原始价格
     * @param label 字段中文名（错误信息用）
     * @return 归一后价格（null → 0）
     * @throws InvalidModelParameterException 负值
     */
    private static BigDecimal normalizePrice(BigDecimal raw, String label) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw.signum() < 0) {
            throw new InvalidModelParameterException(label + "不能为负");
        }
        return raw;
    }

    private static String blankToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    // ---- 访问器（读侧，无 setter；状态变更经聚合方法） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 对外名 A */
    public String publicName() {
        return publicName;
    }

    /** @return 品质档（full/max/air/自定义） */
    public String qualityTier() {
        return qualityTier;
    }

    /** @return 基准售价倍率 */
    public BigDecimal basePriceRatio() {
        return basePriceRatio;
    }

    /** @return 是否按次固定价 */
    public Boolean usePrice() {
        return usePrice;
    }

    /** @return 固定单价（usePrice=true 时生效） */
    public BigDecimal basePrice() {
        return basePrice;
    }

    /** @return 是否上架（F-6004 上架即全员可用） */
    public Boolean enabled() {
        return enabled;
    }

    /** @return 展示名 */
    public String displayName() {
        return displayName;
    }

    /** @return 排序 */
    public Integer sortOrder() {
        return sortOrder;
    }

    /** @return 描述 */
    public String description() {
        return description;
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
