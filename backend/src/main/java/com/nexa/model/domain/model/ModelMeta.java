package com.nexa.model.domain.model;

import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.vo.ModelStatus;
import com.nexa.model.domain.vo.UpstreamModel;

import java.time.Instant;

/**
 * 模型元数据聚合根（充血领域模型，F-3013~F-3021）。
 *
 * <p>领域规则来源：DB-SCHEMA 模块四 §4.1 + PRD ML-1/ML-2。模型元数据承载对外模型名（业务唯一
 * 幂等键 model_name）、状态、描述/图标/标签、归属供应商、支持端点、命名规则，以及同步来源标记
 * sync_official（1=官方同步维护、0=本地自建）。本聚合守护：
 * <ul>
 *   <li>model_name 必填非空白（创建校验）——空 → 「模型名称不能为空」（F-3015）。</li>
 *   <li>model_name 唯一由应用层 + DB 唯一索引 uk_model_name_alive 双重保证（跨聚合不变量落应用层）。</li>
 *   <li>{@link #updateStatusOnly} 仅改 status，绝不动其他字段（防误清，F-3016 status_only=true）。</li>
 *   <li>{@link #overwriteFromUpstream} 同步覆盖（F-3019）：sync_official=0 的本地自建模型拒绝覆盖
 *       （{@link #isLocalManaged()}），由领域服务据此计入 skipped。</li>
 * </ul>
 * </p>
 *
 * <p>DDD：domain 零框架依赖。行为在聚合方法上（create/updateMeta/updateStatusOnly/
 * overwriteFromUpstream），非贫血。RefreshPricing() 等副作用（F-3015/F-3016/F-3017）属应用层
 * 编排，不在领域聚合内（领域只管自身一致性）。</p>
 */
public class ModelMeta {

    /** 模型名最大长度（对齐 V10 {@code model_name varchar(255)}）。 */
    public static final int NAME_MAX_LENGTH = 255;

    /** sync_official=1：由上游官方同步维护。 */
    public static final int SYNC_OFFICIAL = 1;

    /** sync_official=0：本地自建（同步覆盖跳过）。 */
    public static final int SYNC_LOCAL = 0;

    private Long id;
    private String modelName;
    private ModelStatus status;
    private String description;
    private String icon;
    private String tags;
    private Long vendorId;
    private String endpoints;
    private String nameRule;
    private int syncOfficial;
    private Long createdTime;
    private Long updatedTime;

    private ModelMeta() {
    }

    /**
     * 创建本地模型元数据（F-3015 POST，未持久化，id 为 null）。
     *
     * <p>本地手工创建默认 sync_official=0（自建，后续同步不覆盖）。</p>
     *
     * @param modelName   模型名（必填非空白，≤255）
     * @param description 描述（可空）
     * @param icon        图标（可空）
     * @param tags        标签串（可空）
     * @param vendorId    归属供应商 id（可空）
     * @param endpoints   支持端点串（可空）
     * @param nameRule    命名规则（可空）
     * @return 新建模型聚合
     * @throws InvalidModelParameterException modelName 为空白或超长
     */
    public static ModelMeta create(String modelName, String description, String icon, String tags,
                                   Long vendorId, String endpoints, String nameRule) {
        ModelMeta m = new ModelMeta();
        m.modelName = normalizeName(modelName);
        m.status = ModelStatus.ENABLED;
        m.description = blankToNull(description);
        m.icon = blankToNull(icon);
        m.tags = blankToNull(tags);
        m.vendorId = vendorId;
        m.endpoints = blankToNull(endpoints);
        m.nameRule = blankToNull(nameRule);
        m.syncOfficial = SYNC_LOCAL;
        long now = Instant.now().getEpochSecond();
        m.createdTime = now;
        m.updatedTime = now;
        return m;
    }

    /**
     * 由上游同步条目创建模型（F-3019，sync_official=1）。
     *
     * @param upstream 上游模型条目
     * @param vendorId 解析出的本地供应商 id（可空）
     * @return 新建（官方同步）模型聚合
     * @throws InvalidModelParameterException 上游 modelName 非法
     */
    public static ModelMeta fromUpstream(UpstreamModel upstream, Long vendorId) {
        ModelMeta m = new ModelMeta();
        m.modelName = normalizeName(upstream.modelName());
        m.status = ModelStatus.ENABLED;
        m.description = blankToNull(upstream.description());
        m.icon = blankToNull(upstream.icon());
        m.tags = blankToNull(upstream.tags());
        m.vendorId = vendorId;
        m.endpoints = blankToNull(upstream.endpoints());
        m.nameRule = null;
        m.syncOfficial = SYNC_OFFICIAL;
        long now = Instant.now().getEpochSecond();
        m.createdTime = now;
        m.updatedTime = now;
        return m;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验）。
     *
     * @param id            主键
     * @param modelName     模型名
     * @param status        状态码
     * @param description   描述
     * @param icon          图标
     * @param tags          标签串
     * @param vendorId      供应商 id
     * @param endpoints     端点串
     * @param nameRule      命名规则
     * @param syncOfficial  同步来源标记
     * @param createdTime   创建时间 epoch 秒
     * @param updatedTime   更新时间 epoch 秒
     * @return 重建的聚合
     */
    public static ModelMeta rehydrate(Long id, String modelName, int status, String description,
                                      String icon, String tags, Long vendorId, String endpoints,
                                      String nameRule, int syncOfficial,
                                      Long createdTime, Long updatedTime) {
        // 委托 Builder 装配：字段名自解释、状态码 fromCode 归一逻辑收敛在 build() 一处。
        return builder()
                .id(id)
                .modelName(modelName)
                .status(status)
                .description(description)
                .icon(icon)
                .tags(tags)
                .vendorId(vendorId)
                .endpoints(endpoints)
                .nameRule(nameRule)
                .syncOfficial(syncOfficial)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发创建校验，纯还原已存状态。</p>
     *
     * @return 新的模型元数据重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 模型元数据聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：状态码 setter 接受<b>包装类型</b>并把 {@code null} 归一为 0，
     * build() 内经 {@link ModelStatus#fromCode(int)} 转值对象；同步来源标记同样 null 归一为 0。
     * 这样 JPA 实体里可空列的兜底逻辑全部收敛在此，{@code ModelMetaRepositoryImpl.toDomain} 不再散落 {@code ?:} 三元。</p>
     */
    public static final class Builder {
        private Long id;
        private String modelName;
        private int status;
        private String description;
        private String icon;
        private String tags;
        private Long vendorId;
        private String endpoints;
        private String nameRule;
        private int syncOfficial;
        private Long createdTime;
        private Long updatedTime;

        private Builder() {
        }

        /** @param id 主键（未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param modelName 模型名（幂等键） */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** @param status 状态码（null 归一为 0，build() 经 fromCode 转值对象） */
        public Builder status(Integer status) {
            this.status = status == null ? 0 : status;
            return this;
        }

        /** @param description 描述，可为 null */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** @param icon 图标，可为 null */
        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        /** @param tags 标签串，可为 null */
        public Builder tags(String tags) {
            this.tags = tags;
            return this;
        }

        /** @param vendorId 归属供应商 id，可为 null */
        public Builder vendorId(Long vendorId) {
            this.vendorId = vendorId;
            return this;
        }

        /** @param endpoints 支持端点串，可为 null */
        public Builder endpoints(String endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        /** @param nameRule 命名规则，可为 null */
        public Builder nameRule(String nameRule) {
            this.nameRule = nameRule;
            return this;
        }

        /** @param syncOfficial 同步来源标记（null 归一为 0，即本地自建） */
        public Builder syncOfficial(Integer syncOfficial) {
            this.syncOfficial = syncOfficial == null ? 0 : syncOfficial;
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
         * 装配并返回重建的模型元数据聚合（不触发创建校验）。
         *
         * @return 重建的聚合
         */
        public ModelMeta build() {
            ModelMeta m = new ModelMeta();
            m.id = id;
            m.modelName = modelName;
            m.status = ModelStatus.fromCode(status);
            m.description = description;
            m.icon = icon;
            m.tags = tags;
            m.vendorId = vendorId;
            m.endpoints = endpoints;
            m.nameRule = nameRule;
            m.syncOfficial = syncOfficial;
            m.createdTime = createdTime;
            m.updatedTime = updatedTime;
            return m;
        }
    }

    /**
     * 覆盖式全量更新模型元数据（F-3016，status_only=false）。
     *
     * @param modelName   新模型名（必填非空白）
     * @param status      新状态码（可空 → 不改）
     * @param description 新描述（可空 → 清空）
     * @param icon        新图标（可空 → 清空）
     * @param tags        新标签（可空 → 清空）
     * @param vendorId    新供应商 id（可空 → 清空归属）
     * @param endpoints   新端点（可空 → 清空）
     * @param nameRule    新命名规则（可空 → 清空）
     * @throws InvalidModelParameterException modelName 非法
     */
    public void updateMeta(String modelName, Integer status, String description, String icon,
                           String tags, Long vendorId, String endpoints, String nameRule) {
        this.modelName = normalizeName(modelName);
        if (status != null) {
            this.status = ModelStatus.fromCode(status);
        }
        this.description = blankToNull(description);
        this.icon = blankToNull(icon);
        this.tags = blankToNull(tags);
        this.vendorId = vendorId;
        this.endpoints = blankToNull(endpoints);
        this.nameRule = blankToNull(nameRule);
        touch();
    }

    /**
     * 仅更新状态（F-3016 status_only=true，防误清其他字段）。
     *
     * <p>领域规则来源：BACKLOG T-118「status_only 模式不清空其他字段」。本方法只触碰 status，
     * 其余字段一律保持原值（充血聚合守护该不变量，应用层无需关心字段保留逻辑）。</p>
     *
     * @param status 新状态码
     * @throws InvalidModelParameterException 状态码非法
     */
    public void updateStatusOnly(int status) {
        this.status = ModelStatus.fromCode(status);
        touch();
    }

    /**
     * 用上游条目覆盖现有模型（F-3019 同步执行 overwrite）。
     *
     * <p>仅覆盖上游可维护字段（description/icon/tags/endpoints/vendor），保留本地 status/name_rule。
     * 调用前应由领域服务保证 {@link #isLocalManaged()} 为 false（本地自建模型计入 skipped 不进此路径）。</p>
     *
     * @param upstream 上游模型条目
     * @param vendorId 解析出的本地供应商 id（可空）
     */
    public void overwriteFromUpstream(UpstreamModel upstream, Long vendorId) {
        this.description = blankToNull(upstream.description());
        this.icon = blankToNull(upstream.icon());
        this.tags = blankToNull(upstream.tags());
        this.endpoints = blankToNull(upstream.endpoints());
        this.vendorId = vendorId;
        this.syncOfficial = SYNC_OFFICIAL;
        touch();
    }

    /**
     * @return 是否本地自建模型（sync_official=0）——同步覆盖时跳过，计入 skipped_models（PRD ML-2）
     */
    public boolean isLocalManaged() {
        return syncOfficial == SYNC_LOCAL;
    }

    /** 持久化后回填自增 id（仅基础设施层 save 后调用）。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    /**
     * 模型名归一与校验（去空白、非空、长度护栏）。
     *
     * @param raw 原始名
     * @return 归一后名称
     * @throws InvalidModelParameterException 空白或超长
     */
    private static String normalizeName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            // 领域规则来源：F-3015 BACKLOG T-117「ModelName 为空返回模型名称不能为空」。
            throw new InvalidModelParameterException("模型名称不能为空");
        }
        if (n.length() > NAME_MAX_LENGTH) {
            throw new InvalidModelParameterException("模型名称长度不能超过 " + NAME_MAX_LENGTH);
        }
        return n;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ---- 访问器（读侧，无 setter） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 模型名（幂等键） */
    public String modelName() {
        return modelName;
    }

    /** @return 状态值对象 */
    public ModelStatus status() {
        return status;
    }

    /** @return 描述（可空） */
    public String description() {
        return description;
    }

    /** @return 图标（可空） */
    public String icon() {
        return icon;
    }

    /** @return 标签串（可空） */
    public String tags() {
        return tags;
    }

    /** @return 归属供应商 id（可空） */
    public Long vendorId() {
        return vendorId;
    }

    /** @return 支持端点串（可空） */
    public String endpoints() {
        return endpoints;
    }

    /** @return 命名规则（可空） */
    public String nameRule() {
        return nameRule;
    }

    /** @return 同步来源标记（1=官方 0=本地） */
    public int syncOfficial() {
        return syncOfficial;
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
