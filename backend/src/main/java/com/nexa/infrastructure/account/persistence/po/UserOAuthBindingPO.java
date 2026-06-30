package com.nexa.infrastructure.account.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.account.model.OAuthBinding;
import com.nexa.domain.account.vo.OAuthProvider;

import java.time.Instant;

/**
 * 用户 OAuth 绑定持久化实体（基础设施层，对齐 DB-SCHEMA §11/§13 {@code user_oauth_bindings}）。
 *
 * <p>本实体是<b>持久化映射</b>，与领域实体 {@link OAuthBinding} 分离（DDD：domain 不感知 PO）。映射由
 * 本类的就近工厂方法 {@link #toDomain()} / {@link #of(OAuthBinding)} 承载。</p>
 *
 * <p>设计说明（对 DB-SCHEMA §13 的合理偏离，已在 {@link com.nexa.domain.account.vo.OAuthProvider}
 * 注释中说明）：DB-SCHEMA §13 的 {@code provider_id} 为整数外键指向现网 {@code CustomOAuthProvider} 表
 * （自定义 provider，非本批 PRD 范围）。本切片绑定的是 4 个内建 provider（github/discord/oidc/linuxdo），
 * 没有 CustomOAuthProvider 行可引用，故 {@code provider} 列用<b>字符串 provider 标识</b>
 * （{@code OAuthProvider.code()}）落库，语义直接、零外键依赖。复合唯一索引相应改为
 * {@code (user_id, provider)} 与 {@code (provider, provider_user_id)}（语义等价 §13 的 ux 约束）。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：本 PO 同时保留 JPA 注解（{@code @Entity/@Table/@Column/@Id}，
 * 满足并存期 {@code ddl-auto=validate} 全局启动校验）与 MyBatis-Plus 注解
 * （{@code @TableName/@TableId/@TableField}，供 Mapper 实际读写）。两套注解命名空间独立、互不读取。
 * 阶段4 统一移除 JPA 注解。</p>
 */
@TableName("user_oauth_bindings")
public class UserOAuthBindingPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定归属的本站用户 id（DB-SCHEMA §13 user_id，not null；逻辑外键 → users.id）。 */
    @TableField("user_id")
    private Long userId;

    /** 第三方 provider 标识串（github/discord/oidc/linuxdo，本切片以字符串落库，见类注释）。 */
    @TableField("provider")
    private String provider;

    /** 第三方账号在该 provider 下的唯一标识（DB-SCHEMA §13 provider_user_id，not null）。 */
    @TableField("provider_user_id")
    private String providerUserId;

    /**
     * 自定义 OAuth provider 整数主键引用（V5 {@code provider_ref_id}，可空）。
     *
     * <p>自定义 provider 绑定时存 {@code custom_oauth_providers.id}；内建 provider 绑定为 {@code null}。
     * 对齐 openapi {@code OAuthBindingVO.provider_id} 与解绑端点 {@code {provider_id}}（F-1025/1026/1027）。</p>
     */
    @TableField("provider_ref_id")
    private Long providerRefId;

    /** 绑定建立时间 epoch 秒（DB-SCHEMA §13 created_at）。 */
    @TableField("created_at")
    private Long createdAt;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
    public UserOAuthBindingPO() {
    }

    // ---- 访问器（getter/setter；映射在就近工厂方法，领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public Long getProviderRefId() {
        return providerRefId;
    }

    public void setProviderRefId(Long providerRefId) {
        this.providerRefId = providerRefId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 计算落库 {@code provider} 列的标识串：内建用枚举 code，自定义用 {@code custom:<id>}（V5 约定）。
     *
     * @param binding 领域绑定实体
     * @return 落库 provider 标识串
     */
    private static String providerColumn(OAuthBinding binding) {
        Long refId = binding.providerRefId();
        if (refId != null) {
            // 自定义 provider：provider 列存 custom:<id>，不同自定义 provider 天然区分，满足复合唯一索引语义。
            return OAuthBinding.CUSTOM_PROVIDER_CODE_PREFIX + refId;
        }
        return binding.provider().code();
    }

    /**
     * PO → 领域实体（重建方向，走 {@link OAuthBinding#rehydrate}）。
     *
     * <p>据 {@code provider_ref_id} 是否非空区分两类绑定：非空走自定义 provider 重建（保留整数引用，
     * provider 枚举用占位 {@link OAuthProvider#OIDC} 承载，领域侧以 providerRefId 区分自定义）；为空走
     * 内建 provider 重建（枚举由 {@code provider} 列经 {@link OAuthProvider#fromCode} 解析）。{@code created_at}
     * null 兜底为 {@link Instant#now()}。</p>
     *
     * @return 重建的领域绑定实体
     */
    public OAuthBinding toDomain() {
        Instant createdAtInstant = createdAt == null
                ? Instant.now()
                : Instant.ofEpochSecond(createdAt);
        if (providerRefId != null) {
            // 自定义 provider 绑定：provider 列为 custom:<id>，不在内建枚举中。用占位枚举（OIDC）承载，
            // 领域侧以 providerRefId 区分自定义；客户/管理视图据 providerRefId 暴露整数 provider_id。
            return OAuthBinding.rehydrate(
                    id,
                    userId,
                    OAuthProvider.OIDC,
                    providerUserId,
                    providerRefId,
                    createdAtInstant);
        }
        return OAuthBinding.rehydrate(
                id,
                userId,
                OAuthProvider.fromCode(provider),
                providerUserId,
                createdAtInstant);
    }

    /**
     * 领域实体 → PO（持久化方向）。{@code provider} 列经 {@link #providerColumn} 计算（内建枚举 code /
     * 自定义 custom:&lt;id&gt;）；{@code created_at} 取领域打点 Instant 的 epoch 秒，未打点兜底为
     * {@link Instant#now()}。无副作用于入参。
     *
     * @param binding 领域绑定实体（非空）
     * @return 待持久化的 PO
     */
    public static UserOAuthBindingPO of(OAuthBinding binding) {
        UserOAuthBindingPO po = new UserOAuthBindingPO();
        po.id = binding.id();
        po.userId = binding.userId();
        po.provider = providerColumn(binding);
        po.providerUserId = binding.providerUserId();
        // 自定义 provider 的整数主键引用；内建 provider 为 null（对齐 V5 provider_ref_id 列）。
        po.providerRefId = binding.providerRefId();
        // 创建时间：领域实体已在 create() 打点 Instant，落库为 epoch 秒（DB-SCHEMA §13 created_at）。
        po.createdAt = binding.createdAt() == null
                ? Instant.now().getEpochSecond()
                : binding.createdAt().getEpochSecond();
        return po;
    }
}
