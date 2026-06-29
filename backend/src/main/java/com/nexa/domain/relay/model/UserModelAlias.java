package com.nexa.domain.relay.model;

import com.nexa.domain.relay.exception.InvalidRelayParameterException;
import com.nexa.domain.relay.vo.AliasScope;

import java.util.Objects;

/**
 * 客户层自助映射聚合根（L1 层 C→A，分组/用户级，F-6011 / RL-7 第②步）。
 *
 * <p>领域规则来源：COMPAT-LAYER-ARCHITECTURE §4.2 + DB-SCHEMA §21。充血模型：
 * <ul>
 *   <li>复合唯一键 (scope_type, scope_id, alias) 保证同作用域内别名 C 唯一（1对1，仓储 {@code uk_scope_alias} 守护）；</li>
 *   <li>{@code target}(A) **不强制白名单**（ADR-COMPAT-06）：客户可硬输入平台没有的名字，调用期 L2 查不到自然 404；</li>
 *   <li>越权护栏（ARCHITECTURE-REVIEW §6）：user 路由写入时强制 scope=本人，由应用层注入 scopeId，本聚合校验 scope 合法性。</li>
 * </ul>
 * </p>
 *
 * <p>零框架依赖，与 {@code UserModelAliasPO} 分离。</p>
 */
public class UserModelAlias {

    /** alias/target 最大长度，对齐 DB varchar(255)。 */
    public static final int NAME_MAX_LENGTH = 255;

    /** scopeId 最大长度，对齐 DB varchar(64)。 */
    public static final int SCOPE_ID_MAX_LENGTH = 64;

    private Long id;
    private AliasScope scope;
    private String alias;
    private String target;
    private boolean enabled;
    private final Long createdTime;
    private Long updatedTime;

    private UserModelAlias(Long id, AliasScope scope, String alias, String target, boolean enabled,
                           Long createdTime, Long updatedTime) {
        this.id = id;
        this.scope = scope;
        this.alias = alias;
        this.target = target;
        this.enabled = enabled;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
    }

    /**
     * 创建别名（校验：scope 合法、alias/target 非空且 ≤255）。
     *
     * @param scope    作用域（user/group + scopeId）
     * @param alias    C 客户别名
     * @param target   A 目标公开名（不强制白名单）
     * @param nowEpoch 当前 epoch 秒
     * @return 新别名聚合
     */
    public static UserModelAlias create(AliasScope scope, String alias, String target, long nowEpoch) {
        Objects.requireNonNull(scope, "scope must not be null");
        validateName(alias, "alias");
        validateName(target, "target");
        return new UserModelAlias(null, scope, alias.trim(), target.trim(), true, nowEpoch, nowEpoch);
    }

    /** 从持久化重建。 */
    public static UserModelAlias rehydrate(Long id, AliasScope scope, String alias, String target,
                                           boolean enabled, Long createdTime, Long updatedTime) {
        // 委托 Builder 装配：字段名自解释，enabled 的 null 归一逻辑收敛在 Builder 一处。
        return builder()
                .id(id)
                .scope(scope)
                .alias(alias)
                .target(target)
                .enabled(enabled)
                .createdTime(createdTime)
                .updatedTime(updatedTime)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>校验、不触发领域行为，纯还原已存状态。</p>
     *
     * @return 新的别名重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 客户层别名聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：原始类型列 {@link #enabled(Boolean)} 接受<b>包装类型</b>并把 {@code null} 归一为
     * {@code false}——JPA 实体里可空列的兜底逻辑收敛在此，{@code UserModelAliasRepositoryImpl.toDomain}
     * 不再散落 {@code ?:} 三元。{@code createdTime}/{@code updatedTime} 为 {@code Long} 包装类型，按原样透传可空。</p>
     */
    public static final class Builder {
        private Long id;
        private AliasScope scope;
        private String alias;
        private String target;
        private boolean enabled;
        private Long createdTime;
        private Long updatedTime;

        private Builder() {
        }

        /** @param id 主键（新建未持久化为 null） */
        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        /** @param scope 作用域值对象（user/group + scopeId） */
        public Builder scope(AliasScope scope) {
            this.scope = scope;
            return this;
        }

        /** @param alias C 客户别名 */
        public Builder alias(String alias) {
            this.alias = alias;
            return this;
        }

        /** @param target A 目标公开名（不强制白名单） */
        public Builder target(String target) {
            this.target = target;
            return this;
        }

        /** @param enabled 是否启用（null 归一为 false） */
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled != null && enabled;
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
         * 装配并返回重建的别名聚合（不校验、不触发领域行为）。
         *
         * @return 重建的别名聚合
         */
        public UserModelAlias build() {
            return new UserModelAlias(id, scope, alias, target, enabled, createdTime, updatedTime);
        }
    }

    /** 改 target（A）。 */
    public void changeTarget(String newTarget, long nowEpoch) {
        validateName(newTarget, "target");
        this.target = newTarget.trim();
        this.updatedTime = nowEpoch;
    }

    /** 启用/停用。 */
    public void setEnabled(boolean enabled, long nowEpoch) {
        this.enabled = enabled;
        this.updatedTime = nowEpoch;
    }

    /**
     * 越权校验：本别名是否属于给定调用者作用域（self-scope 护栏）。
     *
     * <p>领域规则：user 路由只能读写自己的别名（scope_type=user AND scope_id=本人 user_id），
     * 或本人所属 group 的别名。应用层在删除/编辑前调用本方法防越权。</p>
     *
     * @param callerScope 调用者允许的作用域
     * @return 是否同作用域
     */
    public boolean belongsTo(AliasScope callerScope) {
        return this.scope.equals(callerScope);
    }

    private static void validateName(String name, String field) {
        if (name == null || name.isBlank()) {
            throw new InvalidRelayParameterException(field + " must not be blank");
        }
        if (name.trim().length() > NAME_MAX_LENGTH) {
            throw new InvalidRelayParameterException(field + " exceeds max length " + NAME_MAX_LENGTH);
        }
    }

    public Long id() { return id; }
    public AliasScope scope() { return scope; }
    public String alias() { return alias; }
    public String target() { return target; }
    public boolean isEnabled() { return enabled; }
    public Long createdTime() { return createdTime; }
    public Long updatedTime() { return updatedTime; }
}
