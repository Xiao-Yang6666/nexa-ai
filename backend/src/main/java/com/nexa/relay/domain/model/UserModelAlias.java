package com.nexa.relay.domain.model;

import com.nexa.relay.domain.exception.InvalidRelayParameterException;
import com.nexa.relay.domain.vo.AliasScope;

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
 * <p>零框架依赖，与 {@code UserModelAliasJpaEntity} 分离。</p>
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
        return new UserModelAlias(id, scope, alias, target, enabled, createdTime, updatedTime);
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
