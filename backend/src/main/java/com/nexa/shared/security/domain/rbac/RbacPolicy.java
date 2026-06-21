package com.nexa.shared.security.domain.rbac;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 角色×操作域授权矩阵（领域服务，F-5034 矩阵配置化/可审计）。
 *
 * <p>把 ROLE-PERMISSION-MATRIX §3 的「角色×操作域」授权关系固化为<b>领域内的不可变数据 + 判定逻辑</b>，
 * 供方法级权限注解 / 鉴权过滤器在「最低角色级别（粗粒度三级）」之上做<b>细粒度操作域</b>判定。
 * 这是纯领域逻辑，零框架依赖，可单测。</p>
 *
 * <p>设计说明（取舍）：矩阵以「每个系统角色（common/admin/root）允许的操作域集合」表达。注意矩阵 §3
 * 的产品角色（运营/运维等）是 admin 之上的「功能权限组画像」（§1 取舍：非新增系统角色），属 F-5030 范畴；
 * 本基础设施落 §6 F-5031 的<b>三级系统角色</b>底座判定 + F-5034 矩阵可查，功能权限组细分留作后续 wave。
 * self-scope（🟡）的「仅本人资源」由 {@link AuthenticatedActor#requireSelfScopeOrAdmin} 在运行期按
 * targetUserId 判定，不在静态矩阵内表达。</p>
 *
 * <p>矩阵编码依据（ROLE-PERMISSION-MATRIX §3）：
 * <ul>
 *   <li>common：O01 公开、O02~O06 自身资源、O10 自助查询（self-scope）；</li>
 *   <li>admin ：common 全集 + O07 渠道、O08 计费、O09 用户管理、O10 全量日志、O11 运维；</li>
 *   <li>root  ：admin 全集 + O12 系统设置（root 专属）。</li>
 * </ul>
 * 权限单调递增（高角色 ⊇ 低角色允许集），符合三级层级语义。</p>
 */
public final class RbacPolicy {

    /** 不可变授权矩阵：角色 → 允许的操作域集合。 */
    private static final Map<ActorRole, Set<OperationDomain>> MATRIX = buildMatrix();

    /**
     * 构建授权矩阵（高角色继承低角色允许集，单调递增）。
     *
     * @return 角色→允许操作域集合
     */
    private static Map<ActorRole, Set<OperationDomain>> buildMatrix() {
        // common：公开浏览 + 自身资源域 + 自助日志（self-scope，运行期再按 user_id 收窄）。
        Set<OperationDomain> common = EnumSet.of(
                OperationDomain.O01_PUBLIC_BROWSE,
                OperationDomain.O02_ACCOUNT_IDENTITY,
                OperationDomain.O03_TOKEN_SELF,
                OperationDomain.O04_QUOTA_SELF,
                OperationDomain.O05_GROWTH_SELF,
                OperationDomain.O06_ASYNC_TASK_SELF,
                OperationDomain.O10_LOG_AUDIT);

        // admin = common ∪ {渠道、计费、用户管理、运维}（O10 升级为全量，但操作域同一枚举）。
        Set<OperationDomain> admin = EnumSet.copyOf(common);
        admin.add(OperationDomain.O07_CHANNEL_MANAGE);
        admin.add(OperationDomain.O08_BILLING_CONFIG);
        admin.add(OperationDomain.O09_USER_MANAGE);
        admin.add(OperationDomain.O11_OPS);

        // root = admin ∪ {系统设置}（O12 root 专属）。
        Set<OperationDomain> root = EnumSet.copyOf(admin);
        root.add(OperationDomain.O12_SYSTEM_SETTINGS);

        Map<ActorRole, Set<OperationDomain>> m = new EnumMap<>(ActorRole.class);
        m.put(ActorRole.COMMON, EnumSet.copyOf(common));
        m.put(ActorRole.ADMIN, EnumSet.copyOf(admin));
        m.put(ActorRole.ROOT, EnumSet.copyOf(root));
        return m;
    }

    /**
     * 判定给定角色是否被授权访问某操作域（不抛异常的查询版）。
     *
     * @param role      操作者角色
     * @param operation 目标操作域
     * @return 授权返回 {@code true}
     */
    public boolean isAllowed(ActorRole role, OperationDomain operation) {
        Set<OperationDomain> allowed = MATRIX.get(role);
        return allowed != null && allowed.contains(operation);
    }

    /**
     * 判定给定操作者是否被授权访问某操作域。
     *
     * @param actor     已认证操作者
     * @param operation 目标操作域
     * @return 授权返回 {@code true}
     */
    public boolean isAllowed(AuthenticatedActor actor, OperationDomain operation) {
        return isAllowed(actor.role(), operation);
    }

    /**
     * 返回某角色被授权的操作域集合（只读快照，F-5034 矩阵可后台查看/审计）。
     *
     * @param role 操作者角色
     * @return 不可变的允许操作域集合（防御性拷贝，外部不可篡改矩阵）
     */
    public Set<OperationDomain> allowedOperations(ActorRole role) {
        Set<OperationDomain> allowed = MATRIX.get(role);
        return allowed == null ? EnumSet.noneOf(OperationDomain.class) : EnumSet.copyOf(allowed);
    }
}
