package com.nexa.infrastructure.account.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexa.domain.account.model.User;
import com.nexa.domain.account.vo.Email;
import com.nexa.domain.account.vo.Role;
import com.nexa.domain.account.vo.UserStatus;
import com.nexa.domain.account.vo.Username;

/**
 * 用户持久化实体（基础设施层）。
 *
 * <p>对齐 DB-SCHEMA §1 User / 表 {@code users}。本实体是<b>持久化映射</b>，与领域聚合
 * {@link com.nexa.domain.account.model.User} 分离（DDD：domain 不感知持久化框架）。映射转换在
 * {@code UserRepositoryImpl}。</p>
 *
 * <p>软删除：{@code deleted_at} 为可空 epoch 秒时间戳。MyBatis-Plus 侧以 {@code @TableLogic(value = "null")}
 * 让 {@code select} 自动追加 {@code deleted_at IS NULL} 过滤（等价 JPA {@code @SQLRestriction}）；
 * 软删除<b>写</b>操作不依赖 {@code deleteById}（本项目删除值是 epoch 秒而非 0/1），改由 Mapper 显式
 * {@code @Update} 打时间戳。并存期保留 {@code @SQLRestriction}。</p>
 *
 * <p><b>迁移中间态（双注解）</b>：保留全部 JPA 注解满足 {@code ddl-auto=validate}，新增 MyBatis-Plus 注解供 Mapper 读写。
 * {@code group} 为 PG 保留字，{@code @TableField} 以双引号显式转义。</p>
 */
@TableName("users")
public class UserPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    /** 密码哈希落库（BCrypt 串 60 字符，长度放宽至 100 以容纳算法前缀）。 */
    @TableField("password")
    private String password;

    @TableField("display_name")
    private String displayName;

    @TableField("role")
    private Integer role;

    @TableField("status")
    private Integer status;

    @TableField("email")
    private String email;

    @TableField("quota")
    private Long quota;

    @TableField("used_quota")
    private Long usedQuota;

    @TableField("request_count")
    private Integer requestCount;

    @TableField("aff_code")
    private String affCode;

    @TableField("aff_count")
    private Integer affCount;

    @TableField("aff_quota")
    private Long affQuota;

    @TableField("aff_history")
    private Long affHistoryQuota;

    @TableField("inviter_id")
    private Long inviterId;

    @TableField("created_at")
    private Long createdAt;

    @TableField("last_login_at")
    private Long lastLoginAt;

    @TableField("deleted_at")
    @TableLogic(value = "null")
    private Long deletedAt;

    /** 用户分组（F-1013，PG 保留字 group 需双引号转义；DB-SCHEMA §1 default 'default'）。 */
    @TableField("\"group\"")
    private String group;

    /** 管理员备注（F-1014，仅管理端可见；DB-SCHEMA §1 remark max=255）。 */
    @TableField("remark")
    private String remark;

    /** 个人设置 JSON（F-1014；DB-SCHEMA §1 setting，本切片以 text 承载，列类型 text）。 */
    @TableField("setting")
    private String setting;

    /** 用户专属折扣系数（售价侧，缺省 1.0=不打折；numeric 承载小数）。 */
    @TableField("discount_ratio")
    private java.math.BigDecimal discountRatio;

    /** 框架（JPA / MyBatis-Plus）实例化所需的无参构造器。 */
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

    // ---- 就近映射工厂方法（方案 1）：映射逻辑收敛在 PO，domain 仍零感知 PO ----

    /**
     * 领域聚合 → PO（持久化方向）。新用户（id 为 null）首次落库时打 {@code created_at} 当前 epoch 秒，
     * 并把非空数值列初始化为 0，避免 NOT NULL/约束在首存时报错；已有 id 的更新沿用既有时间（合并保留）。
     *
     * @param user 用户聚合
     * @return 待持久化的 PO
     */
    public static UserPO of(User user) {
        UserPO e = new UserPO();
        e.id = user.id();
        e.username = user.username().value();
        e.password = user.passwordHash();
        e.email = user.email() == null ? null : user.email().value();
        e.role = user.role().code();
        e.status = user.status().code();
        e.quota = user.quota();
        e.affCode = user.affCode();
        e.inviterId = user.inviterId();
        e.lastLoginAt = user.lastLoginAt();
        e.group = user.group();           // F-1013 分组随聚合落库
        e.remark = user.remark();         // F-1014 备注随聚合落库
        e.setting = user.setting();       // F-1014 个人设置随聚合落库
        e.discountRatio = user.discountRatio();   // 用户专属折扣随聚合落库
        if (user.id() == null) {
            // 仅新建时落 created_at；非空数值列给默认 0，避免 NOT NULL/约束在首存时报错。
            e.createdAt = java.time.Instant.now().getEpochSecond();
            e.usedQuota = 0L;
            e.requestCount = 0;
            e.affCount = 0;
            e.affQuota = 0L;
            e.affHistoryQuota = 0L;
        }
        return e;
    }

    /**
     * PO → 领域聚合（重建方向，走 {@link User#builder()}）。数值列 null 兜底与 group 空白归一统一收敛在
     * {@code User.Builder} 内，这里只做枚举解析与 email 归一。
     *
     * @return 重建的用户聚合
     */
    public User toDomain() {
        Email parsedEmail = (email == null || email.isBlank()) ? null : Email.of(email);
        return User.builder()
                .id(id)
                .username(Username.of(username))
                .passwordHash(password)
                .email(parsedEmail)
                .role(Role.fromCode(role == null ? Role.COMMON.code() : role))
                .status(UserStatus.fromCode(status == null ? UserStatus.ENABLED.code() : status))
                .quota(quota)
                .affCode(affCode)
                .inviterId(inviterId)
                .lastLoginAt(lastLoginAt)
                .displayName(displayName)
                .setting(setting)
                .group(group)
                .remark(remark)
                .usedQuota(usedQuota)
                .requestCount(requestCount == null ? null : requestCount.longValue())
                .createdAt(createdAt)
                .discountRatio(discountRatio)
                .build();
    }
}
