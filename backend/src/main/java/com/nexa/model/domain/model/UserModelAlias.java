package com.nexa.model.domain.model;

import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.vo.AliasScopeType;

import java.time.Instant;

/**
 * 客户层自助映射聚合根（C→A，分组/用户级，充血领域模型，F-6003）。
 *
 * <p>领域规则来源：COMPAT-BILLING-DECISIONS §2「两层模型映射」L1 层（C→A）+ DB-SCHEMA §18
 * UserModelAlias。生效顺序 L1（在 L2 A→B 之前），1对1 纯字符串替换。</p>
 *
 * <p>关键铁律（COMPAT §2）：
 * <ul>
 *   <li><b>不强制白名单</b>：{@code target}（A）写入<b>不</b>校验是否在 PublicModel 全集中（铁律），
 *       客户可硬输平台没有的名字（调用时可能失败，那是客户的事）；候选由前端从公开模型全集联想但落库不拦。</li>
 *   <li><b>优先级 user &gt; group</b>：同 C 命中多 scope 时由仓储查询排序保证。</li>
 *   <li><b>越权护栏</b>（DB-SCHEMA §18）：user 路由写入强制 {@code scope_type=user AND scope_id=:caller_user_id}，
 *       禁跨 scope 写。本约束在应用层落地（写入用例），聚合层只校验值合法性。</li>
 * </ul>
 * </p>
 *
 * <p>本聚合守护的不变量：
 * <ul>
 *   <li>{@code scopeType} 非空（user/group）。</li>
 *   <li>{@code scopeId} 必填非空白、≤64（DB-SCHEMA §18 not null）。</li>
 *   <li>{@code alias}（C）必填非空白、≤255（DB-SCHEMA §18 not null）。</li>
 *   <li>{@code target}（A）必填非空白、≤255（DB-SCHEMA §18 not null；不强制白名单）。</li>
 *   <li>复合唯一 {@code (scope_type, scope_id, alias)}（uk_scope_alias）由 DB 索引保证；应用层查重。</li>
 * </ul>
 * </p>
 *
 * <p>DDD：domain 零框架依赖。行为在聚合方法上（create/update），非贫血。</p>
 */
public class UserModelAlias {

    /** scope_id 最大长度（对齐 DB-SCHEMA §18 varchar(64)）。 */
    public static final int SCOPE_ID_MAX_LENGTH = 64;

    /** alias / target 最大长度（对齐 DB-SCHEMA §18 varchar(255)）。 */
    public static final int NAME_MAX_LENGTH = 255;

    private Long id;
    private AliasScopeType scopeType;
    private String scopeId;
    private String alias;
    private String target;
    private Boolean enabled;
    private Long createdTime;
    private Long updatedTime;

    private UserModelAlias() {
    }

    /**
     * 创建新 C→A 映射（F-6003 POST，未持久化，id 为 null）。
     *
     * @param scopeType 作用域类型（user/group，必填）
     * @param scopeId   作用域 id（user→user_id 字符串化 / group→分组名，必填非空白，≤64）
     * @param alias     客户别名 C（必填非空白，≤255）
     * @param target    目标公开名 A（必填非空白，≤255；<b>不校验白名单</b>）
     * @param enabled   是否启用（可空 → true）
     * @return 新建映射聚合
     * @throws InvalidModelParameterException 任一字段非法
     */
    public static UserModelAlias create(AliasScopeType scopeType, String scopeId,
                                        String alias, String target, Boolean enabled) {
        UserModelAlias a = new UserModelAlias();
        if (scopeType == null) {
            throw new InvalidModelParameterException("scope_type 不能为空");
        }
        a.scopeType = scopeType;
        a.scopeId = normalize(scopeId, "scope_id", SCOPE_ID_MAX_LENGTH);
        a.alias = normalize(alias, "客户别名 alias", NAME_MAX_LENGTH);
        // 铁律 COMPAT §2：target 写入不校验白名单，仅做长度/非空护栏。
        a.target = normalize(target, "目标公开名 target", NAME_MAX_LENGTH);
        a.enabled = enabled == null || enabled;
        long now = Instant.now().getEpochSecond();
        a.createdTime = now;
        a.updatedTime = now;
        return a;
    }

    /**
     * 从持久化重建聚合（基础设施层用，绕过创建校验）。
     *
     * @param id           主键
     * @param scopeType    作用域类型
     * @param scopeId      作用域 id
     * @param alias        客户别名 C
     * @param target       目标 A
     * @param enabled      是否启用
     * @param createdTime  创建时间 epoch 秒
     * @param updatedTime  更新时间 epoch 秒
     * @return 重建的聚合
     */
    public static UserModelAlias rehydrate(Long id, AliasScopeType scopeType, String scopeId,
                                           String alias, String target, Boolean enabled,
                                           Long createdTime, Long updatedTime) {
        UserModelAlias a = new UserModelAlias();
        a.id = id;
        a.scopeType = scopeType;
        a.scopeId = scopeId;
        a.alias = alias;
        a.target = target;
        a.enabled = enabled == null || enabled;
        a.createdTime = createdTime;
        a.updatedTime = updatedTime;
        return a;
    }

    /**
     * 覆盖式更新映射（F-6003 PUT）。
     *
     * <p>幂等键 {@code (scope_type, scope_id, alias)}是别名定义本身，更新不可改这三者
     * （对齐 openapi UserModelAliasUpdateRequest 仅 target/enabled）；改了等价新建一条。</p>
     *
     * @param target  新目标 A（可空 → 不改；非空白则校验；<b>不校验白名单</b>）
     * @param enabled 新启用态（可空 → 不改）
     * @throws InvalidModelParameterException target 非法
     */
    public void update(String target, Boolean enabled) {
        if (target != null && !target.isBlank()) {
            this.target = normalize(target, "目标公开名 target", NAME_MAX_LENGTH);
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        touch();
    }

    /** 持久化后回填自增 id（仅基础设施层 save 后调用）。 @param id 自增主键 */
    public void assignId(Long id) {
        this.id = id;
    }

    /**
     * 是否归属指定用户（self-scope 越权判定，F-6003 安全护栏）。
     *
     * <p>规则：scopeType=USER 时，scopeId 等于 user_id 字符串化才算本人映射；scopeType=GROUP 时，
     * 由调用方校验当前用户是否属于该 group（聚合无组成员视图，故仅 USER 维由本方法判定，
     * GROUP 维在应用层判定）。</p>
     *
     * @param userId 当前操作用户 id
     * @return 当且仅当 scope_type=user 且 scope_id 与本用户 id 匹配时返回 true
     */
    public boolean isOwnedBy(long userId) {
        return scopeType == AliasScopeType.USER && String.valueOf(userId).equals(scopeId);
    }

    private void touch() {
        this.updatedTime = Instant.now().getEpochSecond();
    }

    private static String normalize(String raw, String label, int max) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            throw new InvalidModelParameterException(label + "不能为空");
        }
        if (n.length() > max) {
            throw new InvalidModelParameterException(label + "长度不能超过 " + max);
        }
        return n;
    }

    // ---- 访问器（读侧，无 setter） ----

    /** @return 主键（未持久化为 null） */
    public Long id() {
        return id;
    }

    /** @return 作用域类型 */
    public AliasScopeType scopeType() {
        return scopeType;
    }

    /** @return 作用域 id */
    public String scopeId() {
        return scopeId;
    }

    /** @return 客户别名 C */
    public String alias() {
        return alias;
    }

    /** @return 目标公开名 A */
    public String target() {
        return target;
    }

    /** @return 是否启用 */
    public Boolean enabled() {
        return enabled;
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
