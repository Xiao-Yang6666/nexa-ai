package com.nexa.infrastructure.account.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * 用户 JPA 持久化实体（基础设施层）。
 *
 * <p>对齐 DB-SCHEMA §1 User / 表 {@code users}。本实体是<b>持久化映射</b>，与领域聚合
 * {@link com.nexa.domain.account.model.User} 分离（DDD：domain 不感知 JPA）。映射转换在
 * {@code UserRepositoryImpl}。本切片只声明注册/登录涉及的列子集 + 关键索引；其余 DB-SCHEMA
 * 列（github_id 等 OAuth 绑定、setting JSONB 等）在后续账号 wave 补齐，迁移脚本已建全表。</p>
 *
 * <p>软删除：沿用 DB-SCHEMA §1 的 deleted_at 时间戳 + {@code @SQLRestriction} 过滤未删行。</p>
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_aff_code", columnList = "aff_code", unique = true),
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_inviter_id", columnList = "inviter_id"),
        @Index(name = "idx_users_deleted_at", columnList = "deleted_at")
})
@SQLRestriction("deleted_at IS NULL")
public class UserPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, length = 20)
    private String username;

    /** 密码哈希落库（BCrypt 串 60 字符，长度放宽至 100 以容纳算法前缀）。 */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "display_name", length = 20)
    private String displayName;

    @Column(name = "role", columnDefinition = "integer default 1")
    private Integer role;

    @Column(name = "status", columnDefinition = "integer default 1")
    private Integer status;

    @Column(name = "email", length = 50)
    private String email;

    @Column(name = "quota", columnDefinition = "bigint default 0")
    private Long quota;

    @Column(name = "used_quota", columnDefinition = "bigint default 0")
    private Long usedQuota;

    @Column(name = "request_count", columnDefinition = "integer default 0")
    private Integer requestCount;

    @Column(name = "aff_code", length = 32, unique = true)
    private String affCode;

    @Column(name = "aff_count", columnDefinition = "integer default 0")
    private Integer affCount;

    @Column(name = "aff_quota", columnDefinition = "bigint default 0")
    private Long affQuota;

    @Column(name = "aff_history", columnDefinition = "bigint default 0")
    private Long affHistoryQuota;

    @Column(name = "inviter_id")
    private Long inviterId;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "last_login_at", columnDefinition = "bigint default 0")
    private Long lastLoginAt;

    @Column(name = "deleted_at")
    private Long deletedAt;

    /** 用户分组（F-1013，PG 保留字 group 需双引号转义；DB-SCHEMA §1 default 'default'）。 */
    @Column(name = "\"group\"", length = 64)
    private String group;

    /** 管理员备注（F-1014，仅管理端可见；DB-SCHEMA §1 remark max=255）。 */
    @Column(name = "remark", length = 255)
    private String remark;

    /** 个人设置 JSON（F-1014；DB-SCHEMA §1 setting，本切片以 text 承载，列类型 text）。 */
    @Column(name = "setting", columnDefinition = "text")
    private String setting;

    /** 用户专属折扣系数（售价侧，缺省 1.0=不打折；numeric 承载小数）。 */
    @Column(name = "discount_ratio", columnDefinition = "numeric(10,4) default 1.0")
    private java.math.BigDecimal discountRatio;

    /** JPA 规范要求的无参构造器。 */
    public UserPO() {
    }

    // ---- 访问器（JPA 需要 getter/setter；映射在 RepositoryImpl，领域逻辑不在此） ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getQuota() {
        return quota;
    }

    public void setQuota(Long quota) {
        this.quota = quota;
    }

    public Long getUsedQuota() {
        return usedQuota;
    }

    public void setUsedQuota(Long usedQuota) {
        this.usedQuota = usedQuota;
    }

    public Integer getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(Integer requestCount) {
        this.requestCount = requestCount;
    }

    public String getAffCode() {
        return affCode;
    }

    public void setAffCode(String affCode) {
        this.affCode = affCode;
    }

    public Integer getAffCount() {
        return affCount;
    }

    public void setAffCount(Integer affCount) {
        this.affCount = affCount;
    }

    public Long getAffQuota() {
        return affQuota;
    }

    public void setAffQuota(Long affQuota) {
        this.affQuota = affQuota;
    }

    public Long getAffHistoryQuota() {
        return affHistoryQuota;
    }

    public void setAffHistoryQuota(Long affHistoryQuota) {
        this.affHistoryQuota = affHistoryQuota;
    }

    public Long getInviterId() {
        return inviterId;
    }

    public void setInviterId(Long inviterId) {
        this.inviterId = inviterId;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSetting() {
        return setting;
    }

    public void setSetting(String setting) {
        this.setting = setting;
    }

    public java.math.BigDecimal getDiscountRatio() {
        return discountRatio;
    }

    public void setDiscountRatio(java.math.BigDecimal discountRatio) {
        this.discountRatio = discountRatio;
    }
}
