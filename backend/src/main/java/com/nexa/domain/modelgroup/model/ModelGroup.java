package com.nexa.domain.modelgroup.model;

import com.nexa.domain.billing.vo.Ratio;
import com.nexa.domain.modelgroup.exception.InvalidModelGroupParameterException;
import com.nexa.domain.modelgroup.vo.AccessPolicy;
import com.nexa.domain.modelgroup.vo.ModelGroupStatus;
import com.nexa.domain.modelgroup.vo.ModelNames;

import java.math.BigDecimal;

/**
 * 模型组聚合根（充血领域模型，灵活模型组管理的一致性边界）。
 *
 * <p>解决「模型组与用户等级强绑定」的痛点：模型组成为<b>独立可售卖单元</b>——管理员把任意供应商模型
 * 纳入一个模型组，给模型组设<b>模型组级倍率</b>（{@link #basePriceRatio}）与<b>可用模型集</b>
 * （{@link #models}），并通过<b>访问策略</b>（{@link #accessPolicy} 公开/私有/按等级自动）灵活控制
 * 谁能用，而不是把分组写死在账号等级上（backend-engineer §2.2 充血、§2.4 战术完整）。</p>
 *
 * <p>零框架依赖（不 import JPA/Spring/Jackson），与 JPA 实体分离，可纯单测。倍率复用计费域
 * {@link Ratio} 值对象（跨 BC 共享纯值对象，不引入耦合）。</p>
 *
 * <p>不变量（聚合根守护）：
 * <ul>
 *   <li>{@code name} 非空且 ≤64（展示名）。</li>
 *   <li>{@code code} 非空、≤64、仅 [a-z0-9_-]（全局唯一业务标识，中继按 code 选组；唯一性由仓储/DB 兜底）。</li>
 *   <li>{@code basePriceRatio} 非空且 ≥0（复用 {@link Ratio} 不变量）。</li>
 *   <li>{@code accessPolicy}/{@code status} 非空。</li>
 *   <li>{@code description} ≤255（可空）。</li>
 * </ul></p>
 */
public final class ModelGroup {

    /** name 最大长度。 */
    public static final int NAME_MAX_LENGTH = 64;

    /** code 最大长度。 */
    public static final int CODE_MAX_LENGTH = 64;

    /** description 最大长度。 */
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    /** code 合法字符集正则（小写字母/数字/下划线/连字符；中继 URL/Header 安全）。 */
    private static final String CODE_PATTERN = "[a-z0-9_-]+";

    private Long id;
    private String name;
    private final String code;
    private Ratio basePriceRatio;
    private ModelNames models;
    private AccessPolicy accessPolicy;
    private ModelGroupStatus status;
    private String description;
    private final Long createdTime;
    private Long updatedTime;

    private ModelGroup(Long id, String name, String code, Ratio basePriceRatio, ModelNames models,
                       AccessPolicy accessPolicy, ModelGroupStatus status, String description,
                       Long createdTime, Long updatedTime) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.basePriceRatio = basePriceRatio;
        this.models = models;
        this.accessPolicy = accessPolicy;
        this.status = status;
        this.description = description;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    /**
     * 创建新模型组（工厂方法，充血行为，校验全部不变量）。
     *
     * <p>领域规则：name/code 必填并校验格式；code 创建后<b>不可变</b>（中继按 code 选组，改 code 等价
     * 新建一个组，避免在途请求引用失效）；倍率缺省 1.0（不打折）；models 缺省空集；创建即启用；
     * 打 createdTime/updatedTime。code 全局唯一性由应用层查库 + DB 唯一索引兜底，本工厂只保证格式合法。</p>
     *
     * @param name           展示名（必填，≤64）
     * @param code           唯一编码（必填，≤64，仅 [a-z0-9_-]）
     * @param basePriceRatio 模型组基础倍率（可空→1.0；须 ≥0）
     * @param models         可用模型集（可空→空集）
     * @param accessPolicy   访问策略（必填）
     * @param description    描述（可空，≤255）
     * @param nowEpochSec    当前时间（epoch 秒，应用层注入，便于测试）
     * @return 待持久化的新模型组（id 由仓储保存后回填）
     * @throws InvalidModelGroupParameterException 字段非法
     */
    public static ModelGroup create(String name, String code, BigDecimal basePriceRatio,
                                    ModelNames models, AccessPolicy accessPolicy,
                                    String description, long nowEpochSec) {
        String validName = requireName(name);
        String validCode = requireCode(code);
        Ratio ratio = normalizeRatio(basePriceRatio);
        if (accessPolicy == null) {
            throw new InvalidModelGroupParameterException("access policy is required");
        }
        String validDesc = normalizeDescription(description);
        ModelNames validModels = models == null ? ModelNames.EMPTY : models;
        return new ModelGroup(null, validName, validCode, ratio, validModels,
                accessPolicy, ModelGroupStatus.ENABLED, validDesc, nowEpochSec, nowEpochSec);
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用，不触发创建不变量与时间打点）。
     *
     * @return 新的模型组重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 模型组聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>与 {@code create} 一致——本入口<b>不</b>触发创建不变量校验，纯还原已存状态（库中数据视为已合法）。
     * 倍率由 BigDecimal 经 {@link Ratio#of(BigDecimal)} 装配；models 由基础设施层从 JSON 反序列化后传入。</p>
     */
    public static final class Builder {
        private Long id;
        private String name;
        private String code;
        private Ratio basePriceRatio = Ratio.ONE;
        private ModelNames models = ModelNames.EMPTY;
        private AccessPolicy accessPolicy;
        private ModelGroupStatus status;
        private String description;
        private Long createdTime;
        private Long updatedTime;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param name 展示名 */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** @param code 唯一编码 */
        public Builder code(String code) {
            this.code = code;
            return this;
        }

        /** @param basePriceRatio 基础倍率（null 归一为 1.0） */
        public Builder basePriceRatio(BigDecimal basePriceRatio) {
            this.basePriceRatio = basePriceRatio == null ? Ratio.ONE : Ratio.of(basePriceRatio);
            return this;
        }

        /** @param models 可用模型集（null 归一为空集） */
        public Builder models(ModelNames models) {
            this.models = models == null ? ModelNames.EMPTY : models;
            return this;
        }

        /** @param accessPolicy 访问策略 */
        public Builder accessPolicy(AccessPolicy accessPolicy) {
            this.accessPolicy = accessPolicy;
            return this;
        }

        /** @param status 状态 */
        public Builder status(ModelGroupStatus status) {
            this.status = status;
            return this;
        }

        /** @param description 描述，可为 null */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** @param createdTime 创建时间（epoch 秒） */
        public Builder createdTime(Long createdTime) {
            this.createdTime = createdTime;
            return this;
        }

        /** @param updatedTime 更新时间（epoch 秒） */
        public Builder updatedTime(Long updatedTime) {
            this.updatedTime = updatedTime;
            return this;
        }

        /**
         * 装配并返回重建的模型组聚合（不触发创建不变量校验）。
         *
         * @return 重建的模型组聚合
         */
        public ModelGroup build() {
            return new ModelGroup(id, name, code, basePriceRatio, models,
                    accessPolicy, status, description, createdTime, updatedTime);
        }
    }

    /**
     * 覆盖式更新模型组可变属性（充血行为）。
     *
     * <p>领域规则：name/倍率/模型集/访问策略/描述全量覆盖并各自校验；{@code code} 不在此变更
     * （创建后不可变，见 {@link #create}）；{@code status} 启停走 {@link #applyStatus}。非 null 入参
     * 才覆盖（部分更新语义）：传 null 表示该项不改。刷新 updatedTime。</p>
     *
     * @param name           新展示名（null=不改；非 null 须 ≤64）
     * @param basePriceRatio 新基础倍率（null=不改；非 null 须 ≥0）
     * @param models         新可用模型集（null=不改）
     * @param accessPolicy   新访问策略（null=不改）
     * @param description    新描述（null=不改；空白=清空）
     * @param nowEpochSec    当前时间（epoch 秒）
     * @throws InvalidModelGroupParameterException 字段非法
     */
    public void update(String name, BigDecimal basePriceRatio, ModelNames models,
                       AccessPolicy accessPolicy, String description, long nowEpochSec) {
        if (name != null) {
            this.name = requireName(name);
        }
        if (basePriceRatio != null) {
            this.basePriceRatio = normalizeRatio(basePriceRatio);
        }
        if (models != null) {
            this.models = models;
        }
        if (accessPolicy != null) {
            this.accessPolicy = accessPolicy;
        }
        if (description != null) {
            // 空白描述归一为清空（null），与创建期 normalizeDescription 一致语义。
            String trimmed = description.trim();
            this.description = trimmed.isEmpty() ? null : normalizeDescription(trimmed);
        }
        this.updatedTime = nowEpochSec;
    }

    /**
     * 切换启用/禁用状态（充血状态迁移）。
     *
     * <p>幂等——同态再切无副作用。禁用后中继链路不可选用本组（即便仍有访问授权）。刷新 updatedTime。</p>
     *
     * @param status      目标状态
     * @param nowEpochSec 当前时间（epoch 秒）
     * @throws InvalidModelGroupParameterException status 为 null
     */
    public void applyStatus(ModelGroupStatus status, long nowEpochSec) {
        if (status == null) {
            throw new InvalidModelGroupParameterException("status is required");
        }
        this.status = status;
        this.updatedTime = nowEpochSec;
    }

    /**
     * 判定本模型组当前是否可被中继链路选用（启用 + 模型集非空）。
     *
     * <p>禁用组或空模型集组都不可选（空模型集等价无可用模型，选了也无模型可转发）。</p>
     *
     * @return 可选用返回 {@code true}
     */
    public boolean isSelectable() {
        return status.isEnabled() && !models.isEmpty();
    }

    /**
     * 判定指定模型名是否属于本模型组（中继按客户请求模型判定模型组归属）。
     *
     * @param modelName 客户请求的模型名
     * @return 属于返回 {@code true}
     */
    public boolean containsModel(String modelName) {
        return models.contains(modelName);
    }

    /**
     * 回填数据库生成的主键（save 后调用）。
     *
     * @param assignedId 数据库自增 id
     */
    public void assignId(Long assignedId) {
        this.id = assignedId;
    }

    // ---- 校验/归一私有方法（领域规则集中，复用于 create/update） ----

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidModelGroupParameterException("model group name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new InvalidModelGroupParameterException(
                    "model group name too long (max " + NAME_MAX_LENGTH + ")");
        }
        return trimmed;
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidModelGroupParameterException("model group code is required");
        }
        String trimmed = code.trim().toLowerCase();
        if (trimmed.length() > CODE_MAX_LENGTH) {
            throw new InvalidModelGroupParameterException(
                    "model group code too long (max " + CODE_MAX_LENGTH + ")");
        }
        if (!trimmed.matches(CODE_PATTERN)) {
            throw new InvalidModelGroupParameterException(
                    "model group code must match [a-z0-9_-], got: " + code);
        }
        return trimmed;
    }

    /**
     * 倍率归一与校验：null → 1.0（不打折）；否则经 {@link Ratio#of} 校验 ≥0。
     */
    private static Ratio normalizeRatio(BigDecimal raw) {
        return raw == null ? Ratio.ONE : Ratio.of(raw);
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidModelGroupParameterException(
                    "model group description too long (max " + DESCRIPTION_MAX_LENGTH + ")");
        }
        return trimmed;
    }

    // ---- 只读访问器（聚合状态对外只读，无 setter；状态变更只走行为方法） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 展示名 */
    public String name() {
        return name;
    }

    /** @return 唯一编码（中继按此选组） */
    public String code() {
        return code;
    }

    /** @return 基础倍率值对象 */
    public Ratio basePriceRatio() {
        return basePriceRatio;
    }

    /** @return 可用模型集值对象 */
    public ModelNames models() {
        return models;
    }

    /** @return 访问策略 */
    public AccessPolicy accessPolicy() {
        return accessPolicy;
    }

    /** @return 状态 */
    public ModelGroupStatus status() {
        return status;
    }

    /** @return 描述（可空） */
    public String description() {
        return description;
    }

    /** @return 创建时间（epoch 秒） */
    public Long createdTime() {
        return createdTime;
    }

    /** @return 更新时间（epoch 秒） */
    public Long updatedTime() {
        return updatedTime;
    }
}
