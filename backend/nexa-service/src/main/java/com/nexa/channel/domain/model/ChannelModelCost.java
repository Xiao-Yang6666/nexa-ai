package com.nexa.channel.domain.model;

import com.nexa.channel.domain.exception.InvalidChannelParameterException;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 供应商成本倍率聚合根（渠道×真实模型 B，充血领域模型，F-6006）。
 *
 * <p>领域规则来源：COMPAT-BILLING-DECISIONS §4「成本/售价分离（成本端）」+ DB-SCHEMA §19
 * ChannelModelCost。「成本」挂在渠道×B 维度，每笔请求实际走了哪个供应商，就用那个供应商的成本记账；
 * 计费口径：{@code costRatio} 同 model_ratio（输入 token），{@code completionCostRatio=0} 时回落
 * {@code costRatio×现网 CompletionRatio}。多供应商落表：同一 A→B 下，每个挂 Ability 的渠道各一行。</p>
 *
 * <p>本聚合守护的不变量：
 * <ul>
 *   <li>{@code channelId} &gt; 0（关联 Channel.Id 逻辑外键）。</li>
 *   <li>{@code upstreamModel}（B）必填非空白、≤255（DB-SCHEMA §19 客户绝不可见）。</li>
 *   <li>{@code costRatio}/{@code completionCostRatio} 非负（{@link BigDecimal} 防精度漂移）；
 *       0 是合法值（成本缺失兜底 BL-7、completion 回落语义）。</li>
 *   <li>复合唯一 {@code (channel_id, upstream_model)} 由应用层 + DB 索引双重保证。</li>
 * </ul>
 * </p>
 *
 * <p>DDD：domain 零框架依赖（纯 Java，可单测无需起 Spring/DB）。</p>
 */
public class ChannelModelCost {

    /** B 名最大长度（对齐 DB-SCHEMA §19 varchar(255)）。 */
    public static final int UPSTREAM_NAME_MAX_LENGTH = 255;

    private Long id;
    private Integer channelId;
    private String upstreamModel;
    private BigDecimal costRatio;
    private BigDecimal completionCostRatio;
    private Boolean enabled;
    private Long effectiveTime;
    private BigDecimal sourceUnitPrice;
    private String remark;
    private Long createdTime;
    private Long updatedTime;

    private ChannelModelCost() {
    }

    /**
     * 创建新的成本配置（F-6006 POST，未持久化，id 为 null）。
     *
     * @param channelId           渠道 id（必填，&gt; 0）
     * @param upstreamModel       真实上游模型 B（必填非空白）
     * @param costRatio           成本倍率（输入 token；可空 → 0；非负）
     * @param completionCostRatio 成本补全倍率（输出 token；可空 → 0=回落，BL-7）
     * @param enabled             是否启用（可空 → true；false=视为缺失记 0+告警）
     * @param effectiveTime       生效时间 epoch 秒（可空 → 当前时间）
     * @param sourceUnitPrice     扩展位：进货单价（可空 → 0；本期不参与计算）
     * @param remark              备注（可空）
     * @return 新建成本配置聚合
     * @throws InvalidChannelParameterException 任一字段非法
     */
    public static ChannelModelCost create(Integer channelId, String upstreamModel,
                                          BigDecimal costRatio, BigDecimal completionCostRatio,
                                          Boolean enabled, Long effectiveTime,
                                          BigDecimal sourceUnitPrice, String remark) {
        ChannelModelCost c = new ChannelModelCost();
        c.channelId = requirePositive(channelId, "channel_id");
        c.upstreamModel = normalizeUpstream(upstreamModel);
        c.costRatio = nonNegative(costRatio, "cost_ratio");
        c.completionCostRatio = nonNegative(completionCostRatio, "completion_cost_ratio");
        c.enabled = enabled == null || enabled;
        long now = Instant.now().getEpochSecond();
        c.effectiveTime = effectiveTime == null ? now : effectiveTime;
        c.sourceUnitPrice = nonNegative(sourceUnitPrice, "source_unit_price");
        c.remark = blankToEmpty(remark);
        c.createdTime = now;
        c.updatedTime = now;
        return c;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验）。
     *
     * <p>方法体委托 {@link #builder()} 链式装配：原散落在此处的 null 归一兜底（成本/补全/进货价 →
     * {@link BigDecimal#ZERO}、enabled→true、effectiveTime→0L、remark→空串）全部收敛进
     * {@link Builder} 对应 setter。签名保留以兼容既有调用点与单测。</p>
     */
    public static ChannelModelCost rehydrate(Long id, Integer channelId, String upstreamModel,
                                             BigDecimal costRatio, BigDecimal completionCostRatio,
                                             Boolean enabled, Long effectiveTime, BigDecimal sourceUnitPrice,
                                             String remark, Long createdTime, Long updatedTime) {
        return builder()
                .id(id)
                .channelId(channelId)
                .upstreamModel(upstreamModel)
                .costRatio(costRatio)
                .completionCostRatio(completionCostRatio)
                .enabled(enabled)
                .effectiveTime(effectiveTime)
                .sourceUnitPrice(sourceUnitPrice)
                .remark(remark)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的 11 个位置参数：调用处具名链式装配，可读性与抗重构性更好。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发创建校验，纯还原已存状态。</p>
     *
     * @return 新的成本配置重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 成本配置聚合的持久化重建构建器（聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：把 {@link #rehydrate} 里散落的兜底逻辑收敛到对应 setter——三个 {@link BigDecimal}
     * 倍率/单价的 null 归一为 {@link BigDecimal#ZERO}，{@code enabled} 的 null 归一为 {@code true}
     * （{@code enabled == null || enabled}，缺省启用），{@code effectiveTime} 的 null 归一为 0L，
     * {@code remark} 的 null 归一为空串。这样 {@code ChannelModelCostRepositoryImpl.toDomain}
     * 不再散落 {@code ?:} 三元。</p>
     */
    public static final class Builder {
        private Long id;
        private Integer channelId;
        private String upstreamModel;
        private BigDecimal costRatio = BigDecimal.ZERO;
        private BigDecimal completionCostRatio = BigDecimal.ZERO;
        private Boolean enabled = Boolean.TRUE;
        private Long effectiveTime = 0L;
        private BigDecimal sourceUnitPrice = BigDecimal.ZERO;
        private String remark = "";
        private Long createdTime;
        private Long updatedTime;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param channelId 渠道 id（关联 Channel.Id） */
        public Builder channelId(Integer channelId) {
            this.channelId = channelId;
            return this;
        }

        /** @param upstreamModel 真实上游模型 B（客户绝不可见） */
        public Builder upstreamModel(String upstreamModel) {
            this.upstreamModel = upstreamModel;
            return this;
        }

        /** @param costRatio 成本倍率（null 归一为 {@link BigDecimal#ZERO}） */
        public Builder costRatio(BigDecimal costRatio) {
            this.costRatio = costRatio == null ? BigDecimal.ZERO : costRatio;
            return this;
        }

        /** @param completionCostRatio 成本补全倍率（null 归一为 {@link BigDecimal#ZERO}） */
        public Builder completionCostRatio(BigDecimal completionCostRatio) {
            this.completionCostRatio = completionCostRatio == null ? BigDecimal.ZERO : completionCostRatio;
            return this;
        }

        /** @param enabled 是否启用（null 归一为 true，缺省启用） */
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled == null || enabled;
            return this;
        }

        /** @param effectiveTime 生效时间 epoch 秒（null 归一为 0L） */
        public Builder effectiveTime(Long effectiveTime) {
            this.effectiveTime = effectiveTime == null ? 0L : effectiveTime;
            return this;
        }

        /** @param sourceUnitPrice 进货单价（null 归一为 {@link BigDecimal#ZERO}） */
        public Builder sourceUnitPrice(BigDecimal sourceUnitPrice) {
            this.sourceUnitPrice = sourceUnitPrice == null ? BigDecimal.ZERO : sourceUnitPrice;
            return this;
        }

        /** @param remark 备注（null 归一为空串） */
        public Builder remark(String remark) {
            this.remark = remark == null ? "" : remark;
            return this;
        }

        /** @param createdTime 创建时间 epoch 秒，可为 null */
        public Builder createdTime(Long createdTime) {
            this.createdTime = createdTime;
            return this;
        }

        /** @param updatedTime 更新时间 epoch 秒，可为 null */
        public Builder updatedTime(Long updatedTime) {
            this.updatedTime = updatedTime;
            return this;
        }

        /**
         * 装配并返回重建的成本配置聚合（不触发创建校验）。
         *
         * @return 重建的成本配置聚合
         */
        public ChannelModelCost build() {
            ChannelModelCost c = new ChannelModelCost();
            c.id = id;
            c.channelId = channelId;
            c.upstreamModel = upstreamModel;
            c.costRatio = costRatio;
            c.completionCostRatio = completionCostRatio;
            c.enabled = enabled;
            c.effectiveTime = effectiveTime;
            c.sourceUnitPrice = sourceUnitPrice;
            c.remark = remark;
            c.createdTime = createdTime;
            c.updatedTime = updatedTime;
            return c;
        }
    }

    /**
     * 覆盖式更新（F-6006 upsert 语义）。
     *
     * <p>（channel_id, upstream_model）是幂等键，更新不可改这两者；改了等价新建一条。</p>
     *
     * @param costRatio           新成本倍率（可空 → 不改）
     * @param completionCostRatio 新成本补全倍率（可空 → 不改）
     * @param enabled             新启用态（可空 → 不改）
     * @param sourceUnitPrice     新进货单价（可空 → 不改）
     * @param remark              新备注（可空 → 不改）
     * @throws InvalidChannelParameterException 价格为负
     */
    public void update(BigDecimal costRatio, BigDecimal completionCostRatio,
                       Boolean enabled, BigDecimal sourceUnitPrice, String remark) {
        if (costRatio != null) {
            this.costRatio = nonNegative(costRatio, "cost_ratio");
        }
        if (completionCostRatio != null) {
            this.completionCostRatio = nonNegative(completionCostRatio, "completion_cost_ratio");
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        if (sourceUnitPrice != null) {
            this.sourceUnitPrice = nonNegative(sourceUnitPrice, "source_unit_price");
        }
        if (remark != null) {
            this.remark = remark.trim();
        }
        // 写时刷新 effective_time（与 GORM autoUpdateTime 一致：upsert 后取最新生效）。
        this.effectiveTime = Instant.now().getEpochSecond();
        touch();
    }

    /** 持久化后回填自增 id。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    private static Integer requirePositive(Integer raw, String label) {
        if (raw == null || raw <= 0) {
            throw new InvalidChannelParameterException(label + " 必须为正整数");
        }
        return raw;
    }

    private static String normalizeUpstream(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            throw new InvalidChannelParameterException("upstream_model 不能为空");
        }
        if (n.length() > UPSTREAM_NAME_MAX_LENGTH) {
            throw new InvalidChannelParameterException("upstream_model 长度不能超过 " + UPSTREAM_NAME_MAX_LENGTH);
        }
        return n;
    }

    private static BigDecimal nonNegative(BigDecimal raw, String label) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw.signum() < 0) {
            throw new InvalidChannelParameterException(label + " 不能为负");
        }
        return raw;
    }

    private static String blankToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    // ---- 访问器（读侧，无 setter） ----

    /** @return 主键（未持久化为 null） */
    public Long id() { return id; }

    /** @return 渠道 id（关联 Channel.Id） */
    public Integer channelId() { return channelId; }

    /** @return 真实上游模型 B（客户绝不可见） */
    public String upstreamModel() { return upstreamModel; }

    /** @return 成本倍率（输入 token） */
    public BigDecimal costRatio() { return costRatio; }

    /** @return 成本补全倍率（输出 token；0=回落 cost_ratio×现网 completion_ratio） */
    public BigDecimal completionCostRatio() { return completionCostRatio; }

    /** @return 是否启用（false=视为缺失记 0+告警） */
    public Boolean enabled() { return enabled; }

    /** @return 生效时间 epoch 秒 */
    public Long effectiveTime() { return effectiveTime; }

    /** @return 进货单价（扩展位，本期不参与计算） */
    public BigDecimal sourceUnitPrice() { return sourceUnitPrice; }

    /** @return 备注 */
    public String remark() { return remark; }

    /** @return 创建时间 epoch 秒 */
    public Long createdTime() { return createdTime; }

    /** @return 更新时间 epoch 秒 */
    public Long updatedTime() { return updatedTime; }
}
