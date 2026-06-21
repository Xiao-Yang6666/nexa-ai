package com.nexa.account.domain.model;

import com.nexa.account.domain.event.UserRegistered;
import com.nexa.account.domain.exception.InvalidCredentialException;
import com.nexa.account.domain.exception.RoleHierarchyViolationException;
import com.nexa.account.domain.exception.UserDisabledException;
import com.nexa.account.domain.vo.Email;
import com.nexa.account.domain.vo.PasswordHasher;
import com.nexa.account.domain.vo.RawPassword;
import com.nexa.account.domain.vo.Role;
import com.nexa.account.domain.vo.UserStatus;
import com.nexa.account.domain.vo.Username;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户聚合根（充血领域模型）。
 *
 * <p>账号子域的一致性边界。注册、密码校验、封禁闸门等业务行为<b>都在本聚合的方法上</b>，
 * 应用层只编排（调用 {@code User.register(...)} 后存仓储），不在 service 里堆逻辑
 * （backend-engineer §2.2 充血、禁贫血）。本类零框架依赖（不 import JPA/Spring/Web），
 * 可纯单测——这是 DDD 铁律（§2.1 domain 依赖只向内）。</p>
 *
 * <p>不变量（聚合根守护）：
 * <ul>
 *   <li>用户名/密码哈希必非空（注册时强制）。</li>
 *   <li>密码以单向哈希持久化，明文绝不入聚合状态、不落库（{@link PasswordHasher}）。</li>
 *   <li>邀请人 ID 缺省为 0（PRD AC-1 R12「无/无效 aff_code → InviterId=0」）。</li>
 * </ul></p>
 *
 * <p>业务规则来源：PRD prd-account.md AC-1（注册）/ AC-2（登录）；字段对齐 DB-SCHEMA §1 User。</p>
 */
public class User {

    /** aff_code 长度，对齐 PRD AC-1 R13「生成 4 位 aff_code」与 DB-SCHEMA §1 aff_code 唯一。 */
    private static final int AFF_CODE_LENGTH = 4;

    private static final char[] AFF_CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // 去掉易混 0/O/1/I

    /** 展示名最大长度，对齐 DB-SCHEMA §1 {@code display_name @Column(length = 20)}。 */
    public static final int DISPLAY_NAME_MAX_LENGTH = 20;

    /** 自增主键，未持久化的新用户为 null。 */
    private Long id;

    private Username username;

    /** 密码单向哈希（落库值，绝非明文）。 */
    private String passwordHash;

    private Email email;

    private Role role;

    private UserStatus status;

    /** 初始/当前额度（整数额度单位，对齐 DB-SCHEMA §1 quota 为整数）。 */
    private long quota;

    /** 个人邀请码（4 位，全局唯一，DB-SCHEMA §1 aff_code uniqueIndex）。 */
    private String affCode;

    /** 邀请人 ID（PRD AC-1：有效 aff_code 解析得到，否则 0）。 */
    private long inviterId;

    /** 最近登录时间（epoch 秒，对齐 DB-SCHEMA §1 last_login_at default 0）。 */
    private long lastLoginAt;

    /** 展示名（DB-SCHEMA §1 display_name，可空，长度上限同用户名 20）。 */
    private String displayName;

    /**
     * 个人设置原始 JSON（DB-SCHEMA §1 setting / openapi {@code UserSetting}）。
     *
     * <p>领域不解析其内部结构（语言/边栏/额度预警等键值由上层契约定义且可扩展，
     * openapi 标 {@code additionalProperties: true}），聚合只把它当作不透明的本人设置载荷
     * 整存整取，避免领域层耦合易变的设置 schema（F-1014）。{@code null} 表示未设置。</p>
     */
    private String setting;

    /**
     * 用户分组（DB-SCHEMA §1 {@code group}，PG 保留字，缺省 {@code "default"}）。
     *
     * <p>F-1013 用户分组绑定：管理员把用户划入某分组以套用分组级倍率/可用渠道等策略。
     * 领域只保存分组标识字符串，分组是否存在/合法由上层（分组域）约束。</p>
     */
    private String group;

    /**
     * 管理员备注（DB-SCHEMA §1 {@code remark}，max=255，仅管理端可见）。
     *
     * <p>F-1014 用户备注：管理员对用户的内部备注。<b>对普通用户置空、绝不下发</b>
     * （API-ENDPOINTS §1.3「remark 对普通用户置空」），只在 AdminUserView 回显。
     * {@code null} 表示无备注。</p>
     */
    private String remark;

    /** 已用额度（DB-SCHEMA §1 used_quota，管理端视图只读回显）。 */
    private long usedQuota;

    /** 请求计数（DB-SCHEMA §1 request_count，管理端视图只读回显）。 */
    private long requestCount;

    /** 创建时间 epoch 秒（DB-SCHEMA §1 created_at，管理端视图只读回显）。 */
    private long createdAt;

    /** 备注最大长度，对齐 DB-SCHEMA §1 {@code remark max=255}。 */
    public static final int REMARK_MAX_LENGTH = 255;

    /** 分组标识最大长度，对齐 DB-SCHEMA §1 {@code group varchar(64)}。 */
    public static final int GROUP_MAX_LENGTH = 64;

    /** 默认分组标识（DB-SCHEMA §1 {@code group default 'default'}）。 */
    public static final String DEFAULT_GROUP = "default";

    /** 注册成功时产生的领域事件（一次性，由应用层取出后清理）。 */
    private transient UserRegistered registeredEvent;

    /** 供基础设施层（持久化重建）与测试使用的全参重建构造器。 */
    private User() {
    }

    /**
     * 注册新用户（聚合根工厂方法，充血行为）。
     *
     * <p>在此完成所有注册不变量校验与初始状态装配（PRD AC-1 R13）：
     * 角色 = common、状态 = 启用、配额 = 传入的新用户初始额度、生成 4 位 aff_code、
     * 绑定邀请人。密码在此处即时哈希，明文不入聚合状态。成功后产生
     * {@link UserRegistered} 领域事件供应用层取用。</p>
     *
     * <p>注意：本方法<b>不</b>做用户名查重——查重需要仓储（IO），属应用层职责
     * （应用层先 {@code existsByUsername} 再调本方法），聚合保持纯内存可单测。</p>
     *
     * @param username        用户名值对象（已通过长度/非空校验）
     * @param rawPassword     明文密码值对象（已通过长度校验，仅用于即时哈希）
     * @param email           邮箱值对象，可为 {@code null}（注册时 email 为可选，见 openapi register schema）
     * @param hasher          密码哈希器（领域端口，基础设施层注入实现）
     * @param initialQuota    新用户初始额度（QuotaForNewUser，由配置传入，PRD AC-1 R13）
     * @param inviterId       邀请人 ID（无有效 aff_code 时传 0，PRD AC-1 R12）
     * @return 处于待持久化状态的新用户聚合（id 由仓储保存后回填）
     * @throws InvalidCredentialException 当哈希器返回空哈希（不应发生，防御式）
     */
    public static User register(Username username,
                                RawPassword rawPassword,
                                Email email,
                                PasswordHasher hasher,
                                long initialQuota,
                                long inviterId) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(hasher, "hasher");

        String hash = hasher.hash(rawPassword.value());
        if (hash == null || hash.isBlank()) {
            // 防御式：哈希器实现异常返回空哈希时绝不允许落一个"空密码"账号。
            throw new InvalidCredentialException("password hashing produced empty hash");
        }

        User u = new User();
        u.username = username;
        u.passwordHash = hash;
        u.email = email;
        u.role = Role.COMMON;          // PRD AC-1 R13：创建 common 用户
        u.status = UserStatus.ENABLED; // 注册即启用（DB-SCHEMA §1 status default 1）
        u.quota = initialQuota;        // PRD AC-1 R13：Quota=QuotaForNewUser
        u.inviterId = Math.max(inviterId, 0L);
        u.affCode = generateAffCode(); // PRD AC-1 R13：生成 4 位 aff_code
        u.lastLoginAt = 0L;
        u.group = DEFAULT_GROUP;        // DB-SCHEMA §1 group default 'default'
        u.registeredEvent = UserRegistered.now(null, username.value(), u.inviterId);
        return u;
    }

    /** OAuth 建号时随机密码的字节数（仅用于占位哈希，OAuth 用户不以本地密码登录）。 */
    private static final int OAUTH_RANDOM_PASSWORD_BYTES = 24;

    /**
     * 经第三方 OAuth 首次登录时建号（聚合根工厂方法，充血行为，F-1016~1020）。
     *
     * <p>领域规则：OAuth 用户没有用户自设密码——授权由第三方完成，本地账号仍需一个<b>不可用的占位
     * 密码哈希</b>（password 列 NOT NULL，DB-SCHEMA §1）。这里生成一段高熵随机串即时哈希作为占位，
     * 用户后续可走找回密码流程设真正的本地密码。其余初始状态与 {@link #register} 一致：
     * 角色 common、状态启用、初始额度、生成 aff_code、绑定邀请人。建号产生
     * {@link UserRegistered} 事件供应用层取用（与普通注册一致的下游归因）。</p>
     *
     * <p>与 {@link #register} 的区别：不接收用户明文密码（用随机占位），不走验证码——身份已由
     * 第三方 OAuth 提供方背书。用户名查重/邀请解析仍属应用层职责（IO）。</p>
     *
     * @param username     候选用户名值对象（已规范化/查重由应用层负责）
     * @param email        邮箱值对象，可为 {@code null}（provider 未返回邮箱）
     * @param hasher       密码哈希器（领域端口，用于哈希随机占位密码）
     * @param initialQuota 新用户初始额度（QuotaForNewUser）
     * @param inviterId    邀请人 ID（无有效 aff_code 时传 0）
     * @return 处于待持久化状态的新用户聚合
     * @throws InvalidCredentialException 当哈希器返回空哈希（防御式）
     */
    public static User registerViaOAuth(Username username,
                                        Email email,
                                        PasswordHasher hasher,
                                        long initialQuota,
                                        long inviterId) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(hasher, "hasher");

        // OAuth 用户无自设密码：用强随机占位串即时哈希落库（NOT NULL 约束 + 不可被猜测）。
        byte[] random = new byte[OAUTH_RANDOM_PASSWORD_BYTES];
        ThreadLocalRandom.current().nextBytes(random);
        String placeholder = java.util.HexFormat.of().formatHex(random);
        String hash = hasher.hash(placeholder);
        if (hash == null || hash.isBlank()) {
            throw new InvalidCredentialException("password hashing produced empty hash");
        }

        User u = new User();
        u.username = username;
        u.passwordHash = hash;
        u.email = email;
        u.role = Role.COMMON;
        u.status = UserStatus.ENABLED;
        u.quota = initialQuota;
        u.inviterId = Math.max(inviterId, 0L);
        u.affCode = generateAffCode();
        u.lastLoginAt = 0L;
        u.group = DEFAULT_GROUP;
        u.registeredEvent = UserRegistered.now(null, username.value(), u.inviterId);
        return u;
    }

    /**
     * 登录密码校验 + 状态闸门（充血行为）。
     *
     * <p>对应 PRD AC-2 L2/L3 两关：先比对密码哈希（不匹配 → 账号或密码错误态），
     * 再校验账号启用状态（被封禁 → 账号已封禁拒绝态）。顺序刻意为"先验密码再验状态"，
     * 但两关失败都不建会话；状态校验失败抛专属异常以便接口层区分提示。</p>
     *
     * @param rawPassword 待校验明文密码值对象
     * @param hasher      密码哈希器（领域端口）
     * @throws InvalidCredentialException 密码不匹配（不区分用户是否存在，防枚举）
     * @throws UserDisabledException      密码正确但账号非启用态（被封禁）
     */
    public void authenticate(RawPassword rawPassword, PasswordHasher hasher) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(hasher, "hasher");
        if (!hasher.matches(rawPassword.value(), this.passwordHash)) {
            throw new InvalidCredentialException("invalid username or password");
        }
        if (!this.status.isEnabled()) {
            throw new UserDisabledException();
        }
    }

    /**
     * 非抛出的密码匹配查询（充血只读行为，供二次验证等场景，F-1038）。
     *
     * <p>与 {@link #authenticate} 区别：本方法<b>只判密码是否匹配</b>且返回布尔（不抛异常、不校验启用状态），
     * 用于「敏感动作二次验证」这类只需确认\"本人知道密码\"的场景——封禁判定属鉴权层职责，不在此重复。
     * 密码哈希比对封装在聚合内（外部不触碰 {@code passwordHash}），保持充血与不变量边界
     * （backend-engineer §2.2 行为在领域对象上）。</p>
     *
     * @param rawPassword 待校验明文密码值对象
     * @param hasher      密码哈希器（领域端口）
     * @return 明文匹配现有哈希返回 {@code true}，否则 {@code false}
     */
    public boolean matchesPassword(RawPassword rawPassword, PasswordHasher hasher) {
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(hasher, "hasher");
        return hasher.matches(rawPassword.value(), this.passwordHash);
    }

    /**
     * 标记一次成功登录，刷新最近登录时间（PRD AC-2 §5「写 LastLoginAt」）。
     *
     * @param epochSeconds 登录时刻 epoch 秒
     */
    public void markLoggedIn(long epochSeconds) {
        this.lastLoginAt = epochSeconds;
    }

    /**
     * 修改密码（本人改密充血行为）。
     *
     * <p>领域规则：先校验当前密码正确（防止会话被盗后他人改密），再以新明文即时哈希替换。
     * 旧哈希被覆盖即失效。当前密码校验失败抛 {@link InvalidCredentialException}；不区分
     * 「账号是否存在」（此处账号必然存在，沿用统一异常类型保持错误语义一致）。</p>
     *
     * <p>注意：本方法<b>不</b>校验账号启用状态——改密是本人会话内操作，封禁判定属鉴权层职责，
     * 不在改密这一步重复。新明文长度等约束已由 {@link RawPassword} 在构造时保证。</p>
     *
     * @param currentPassword 当前明文密码值对象（须与现有哈希匹配）
     * @param newPassword     新明文密码值对象（已通过长度校验）
     * @param hasher          密码哈希器（领域端口）
     * @throws InvalidCredentialException 当前密码不匹配，或新哈希为空（防御式）
     */
    public void changePassword(RawPassword currentPassword, RawPassword newPassword, PasswordHasher hasher) {
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(hasher, "hasher");
        if (!hasher.matches(currentPassword.value(), this.passwordHash)) {
            // 改密前必须验明当前密码：会话有效不等于知道旧密码，挡住会话被劫持后的静默改密。
            throw new InvalidCredentialException("current password is incorrect");
        }
        applyNewPassword(newPassword, hasher);
    }

    /**
     * 重置密码（找回密码流程的最终落地，充血行为）。
     *
     * <p>领域规则来源：PRD AC-3「令牌有效且未过期则更新 Password，旧哈希失效」（F-1007）。
     * 与 {@link #changePassword} 的区别：重置场景<b>不</b>校验旧密码——身份已由邮箱重置令牌
     * 在应用层校验通过（令牌有效性/时效是应用层用 PasswordResetTokenService 校验的，属 IO 范畴，
     * 不进聚合）。聚合在此只负责「把密码换成新哈希」这一不变量动作。</p>
     *
     * @param newPassword 新明文密码值对象（已通过长度校验）
     * @param hasher      密码哈希器（领域端口）
     * @throws InvalidCredentialException 新哈希为空（防御式，不应发生）
     */
    public void resetPassword(RawPassword newPassword, PasswordHasher hasher) {
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(hasher, "hasher");
        applyNewPassword(newPassword, hasher);
    }

    /**
     * 计算并替换密码哈希（{@link #changePassword}/{@link #resetPassword} 共用的私有不变量动作）。
     *
     * @param newPassword 新明文密码
     * @param hasher      哈希器
     * @throws InvalidCredentialException 当哈希器返回空哈希时（绝不允许覆写为空密码）
     */
    private void applyNewPassword(RawPassword newPassword, PasswordHasher hasher) {
        String newHash = hasher.hash(newPassword.value());
        if (newHash == null || newHash.isBlank()) {
            // 防御式：哈希异常时绝不把账号密码改成空，宁可抛错让上层回滚。
            throw new InvalidCredentialException("password hashing produced empty hash");
        }
        this.passwordHash = newHash;
    }

    // ============================================================================
    // 管理端用户管理充血行为（F-1008~1014，AdminAuth 鉴权，角色越权护栏为领域规则）。
    // 护栏铁律（PRD AC-10）：操作者只能操作「角色严格低于自己」的目标用户；
    // 角色优先级 root(100) > admin(10) > common(1)。护栏在聚合内守护，零框架依赖可纯单测。
    // ============================================================================

    /**
     * 管理端创建用户（聚合根工厂方法，带角色越权护栏，F-1009）。
     *
     * <p>领域规则来源：API-ENDPOINTS §1.4 {@code POST /api/user/}「role 不可高于自身角色」、
     * AC-10 越权护栏。校验：目标初始角色必须<b>严格低于</b>操作者角色（{@code newRole >= operatorRole}
     * 即拒绝，不可创建同级/更高角色用户）。密码即时哈希，初始状态启用、缺省分组。</p>
     *
     * <p>与 {@link #register} 的区别：管理端创建<b>不</b>走注册开关/验证码，且角色可由操作者指定
     * （在护栏内），不产生注册领域事件（非用户自助注册）。用户名查重仍属应用层职责（IO）。</p>
     *
     * @param username     用户名值对象
     * @param rawPassword  明文密码值对象（仅用于即时哈希）
     * @param email        邮箱值对象，可为 {@code null}
     * @param displayName  展示名，可为 {@code null}/空白（清空）
     * @param newRole      目标用户角色（须严格低于 {@code operatorRole}）
     * @param hasher       密码哈希器（领域端口）
     * @param initialQuota 初始额度
     * @param operatorRole 操作者（创建者）角色，用于护栏比较
     * @return 待持久化的新用户聚合
     * @throws RoleHierarchyViolationException 当 {@code newRole >= operatorRole}（越权创建）
     * @throws InvalidCredentialException      展示名超长 / 哈希为空（防御式）
     */
    public static User createByAdmin(Username username,
                                     RawPassword rawPassword,
                                     Email email,
                                     String displayName,
                                     Role newRole,
                                     PasswordHasher hasher,
                                     long initialQuota,
                                     Role operatorRole) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(rawPassword, "rawPassword");
        Objects.requireNonNull(newRole, "newRole");
        Objects.requireNonNull(hasher, "hasher");
        Objects.requireNonNull(operatorRole, "operatorRole");

        // 护栏：不可创建角色 >= 自身的用户（API-ENDPOINTS §1.4 / AC-10 越权创建拒绝）。
        if (newRole.isAtLeast(operatorRole)) {
            throw new RoleHierarchyViolationException(
                    "cannot create a user with role >= operator role");
        }

        String hash = hasher.hash(rawPassword.value());
        if (hash == null || hash.isBlank()) {
            throw new InvalidCredentialException("password hashing produced empty hash");
        }

        User u = new User();
        u.username = username;
        u.passwordHash = hash;
        u.email = email;
        u.role = newRole;
        u.status = UserStatus.ENABLED;
        u.quota = initialQuota;
        u.inviterId = 0L;
        u.affCode = generateAffCode();
        u.lastLoginAt = 0L;
        u.group = DEFAULT_GROUP;
        u.applyDisplayName(displayName); // 复用展示名规范化/长度校验
        return u;
    }

    /**
     * 守卫：操作者必须有权操作本目标用户（角色越权护栏，PRD AC-10 M3）。
     *
     * <p>护栏铁律：操作者角色必须<b>严格高于</b>目标（本用户）角色；目标角色 ≥ 操作者角色一律拒绝
     * （不可操作同级或更高角色，含操作 root / 平级 admin 的场景）。一切管理端写操作
     * （启用/禁用/提升/降级/改资料/改分组/改备注）入口处先过此关。</p>
     *
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 当目标（本用户）角色 ≥ 操作者角色
     */
    public void ensureOperableBy(Role operatorRole) {
        Objects.requireNonNull(operatorRole, "operatorRole");
        if (this.role.isAtLeast(operatorRole)) {
            // 不回显具体角色细节，仅给稳定越权语义（接口层映射 403）。
            throw new RoleHierarchyViolationException(
                    "cannot operate on a user whose role >= operator role");
        }
    }

    /**
     * 启用账号（管理端，F-1010 enable 动作）。
     *
     * <p>领域规则：先过角色护栏（{@link #ensureOperableBy}），再把状态置启用。
     * 幂等——已启用再启用无副作用（AC-10 幂等键 {@code (id, action)}）。</p>
     *
     * @param operatorRole 操作者角色（护栏比较）
     * @throws RoleHierarchyViolationException 越权
     */
    public void enable(Role operatorRole) {
        ensureOperableBy(operatorRole);
        this.status = UserStatus.ENABLED;
    }

    /**
     * 禁用账号（管理端，F-1010 disable 动作）。
     *
     * <p>领域规则：过护栏后把状态置禁用，目标随后无法登录（对齐 AC-2 封禁闸门）。幂等。</p>
     *
     * @param operatorRole 操作者角色（护栏比较）
     * @throws RoleHierarchyViolationException 越权
     */
    public void disable(Role operatorRole) {
        ensureOperableBy(operatorRole);
        this.status = UserStatus.DISABLED;
    }

    /**
     * 提升角色一级（管理端，F-1010 promote / F-1012 角色分级）。
     *
     * <p>领域规则（AC-10 M7 提升越界护栏）：先过操作者护栏，再校验「提升后角色仍严格低于操作者」，
     * 否则拒绝（提升越界拒绝态）。提升路径 common→admin→admin（已是 admin 时不再提升，幂等）。
     * 注意：本聚合不允许提升到 root（仅 root 操作者可借 {@link #changeRole} 直接指派 admin，
     * 提升语义按「升一级且不越界」实现）。</p>
     *
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 越权操作目标 / 提升后越界
     */
    public void promote(Role operatorRole) {
        ensureOperableBy(operatorRole);
        Role next = switch (this.role) {
            case COMMON -> Role.ADMIN;
            case ADMIN, ROOT -> this.role; // 已 admin/root 不再向上提（root 不经此路径产生）
        };
        // M7：提升后角色不得 >= 操作者角色。
        if (next.isAtLeast(operatorRole)) {
            throw new RoleHierarchyViolationException(
                    "promotion would raise role to >= operator role");
        }
        this.role = next;
    }

    /**
     * 降级角色一级（管理端，F-1010 demote / F-1012 角色分级）。
     *
     * <p>领域规则：先过操作者护栏（目标当前角色须严格低于操作者），再降一级
     * （admin→common，common 保持，幂等）。降级天然不越界，无需 M7 校验。</p>
     *
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 越权
     */
    public void demote(Role operatorRole) {
        ensureOperableBy(operatorRole);
        this.role = switch (this.role) {
            case ADMIN -> Role.COMMON;
            case COMMON, ROOT -> this.role; // common 已最低；root 不经此路径降（护栏已挡）
        };
    }

    /**
     * 直接指派目标用户角色（管理端角色分级，F-1012）。
     *
     * <p>领域规则（AC-10 护栏）：① 先过操作者护栏（不可改角色 ≥ 自身的用户）；
     * ② 目标新角色必须<b>严格低于</b>操作者角色（不可把人提到同级/更高，含提到 root）。
     * 二者均满足才落地新角色。这是 promote/demote 之外的「精确指派」入口。</p>
     *
     * @param newRole      目标新角色
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 操作越权 / 新角色越界
     */
    public void changeRole(Role newRole, Role operatorRole) {
        Objects.requireNonNull(newRole, "newRole");
        ensureOperableBy(operatorRole);
        if (newRole.isAtLeast(operatorRole)) {
            throw new RoleHierarchyViolationException(
                    "cannot set role >= operator role");
        }
        this.role = newRole;
    }

    /**
     * 绑定/变更用户分组（管理端，F-1013）。
     *
     * <p>领域规则：先过角色护栏；分组标识 trim 后空白归一为缺省分组 {@code "default"}，
     * 长度超限抛 {@link InvalidCredentialException}（对齐 DB-SCHEMA §1 {@code group varchar(64)}）。</p>
     *
     * @param rawGroup     分组标识（可为 null/空白 → 归一为 default）
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 越权
     * @throws InvalidCredentialException      分组标识超长
     */
    public void assignGroup(String rawGroup, Role operatorRole) {
        ensureOperableBy(operatorRole);
        this.group = normalizeGroup(rawGroup);
    }

    /**
     * 更新管理员备注（管理端，F-1014）。
     *
     * <p>领域规则：先过护栏；备注 trim 后空白归一为 {@code null}（无备注），
     * 超长抛 {@link InvalidCredentialException}（DB-SCHEMA §1 {@code remark max=255}）。</p>
     *
     * @param rawRemark    备注文本（可为 null/空白 → 清空）
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 越权
     * @throws InvalidCredentialException      备注超长
     */
    public void updateRemark(String rawRemark, Role operatorRole) {
        ensureOperableBy(operatorRole);
        this.remark = normalizeRemark(rawRemark);
    }

    /**
     * 管理端更新用户资料（F-1011 一次性覆盖式更新，带护栏）。
     *
     * <p>领域规则来源：API-ENDPOINTS §1.4 {@code PUT /api/user/}（可选 display_name/email/group/
     * quota/remark/status）。先过角色护栏，再对<b>非 null</b> 入参逐项更新（null 表示该项不改，
     * 实现「部分更新」语义）。各字段沿用单项行为的规范化/长度校验。</p>
     *
     * @param displayName  新展示名（null=不改；空白=清空）
     * @param email        新邮箱值对象（null=不改）
     * @param group        新分组（null=不改）
     * @param quota        新额度（null=不改）
     * @param remark       新备注（null=不改；空白=清空）
     * @param status       新状态（null=不改）
     * @param operatorRole 操作者角色
     * @throws RoleHierarchyViolationException 越权
     * @throws InvalidCredentialException      展示名/分组/备注超长，或额度为负
     */
    public void updateProfileByAdmin(String displayName,
                                     Email email,
                                     String group,
                                     Long quota,
                                     String remark,
                                     UserStatus status,
                                     Role operatorRole) {
        ensureOperableBy(operatorRole);
        if (displayName != null) {
            applyDisplayName(displayName);
        }
        if (email != null) {
            this.email = email;
        }
        if (group != null) {
            this.group = normalizeGroup(group);
        }
        if (quota != null) {
            if (quota < 0) {
                throw new InvalidCredentialException("quota must be >= 0");
            }
            this.quota = quota;
        }
        if (remark != null) {
            this.remark = normalizeRemark(remark);
        }
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * 展示名规范化 + 长度校验（{@link #updateDisplayName}/管理端创建与更新共用）。
     *
     * @param rawDisplayName 原始展示名
     * @throws InvalidCredentialException 超长
     */
    private void applyDisplayName(String rawDisplayName) {
        String normalized = rawDisplayName == null ? null : rawDisplayName.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "display name length must be <= " + DISPLAY_NAME_MAX_LENGTH);
        }
        this.displayName = normalized;
    }

    /**
     * 分组标识规范化：空白归一为缺省分组，校验长度。
     *
     * @param rawGroup 原始分组
     * @return 规范化后的分组标识
     * @throws InvalidCredentialException 超长
     */
    private static String normalizeGroup(String rawGroup) {
        String normalized = rawGroup == null ? null : rawGroup.trim();
        if (normalized == null || normalized.isEmpty()) {
            return DEFAULT_GROUP;
        }
        if (normalized.length() > GROUP_MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "group length must be <= " + GROUP_MAX_LENGTH);
        }
        return normalized;
    }

    /**
     * 备注规范化：空白归一为 null，校验长度。
     *
     * @param rawRemark 原始备注
     * @return 规范化后的备注（可为 null）
     * @throws InvalidCredentialException 超长
     */
    private static String normalizeRemark(String rawRemark) {
        String normalized = rawRemark == null ? null : rawRemark.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (normalized != null && normalized.length() > REMARK_MAX_LENGTH) {
            throw new InvalidCredentialException(
                    "remark length must be <= " + REMARK_MAX_LENGTH);
        }
        return normalized;
    }

    /**
     * 更新展示名（个人信息修改，充血行为）。
     *
     * <p>规范化：trim 后空白串视为「清空展示名」（置 null）。长度上限对齐 DB-SCHEMA §1
     * {@code display_name @Column(length = 20)}，超长抛 {@link InvalidCredentialException}
     * 挡在领域边界（F-1014 个人信息修改的一部分）。</p>
     *
     * @param rawDisplayName 原始展示名（可为 null/空白 → 清空）
     * @throws InvalidCredentialException 展示名超过 {@link #DISPLAY_NAME_MAX_LENGTH}
     */
    public void updateDisplayName(String rawDisplayName) {
        applyDisplayName(rawDisplayName);
    }

    /**
     * 覆盖式保存本人设置（个人设置保存，充血行为）。
     *
     * <p>领域规则来源：API-ENDPOINTS §1.3「PUT /api/user/self/setting 覆盖式写入，幂等」（F-1014）。
     * 聚合把 setting 当不透明 JSON 整存（见字段注释），不解析键值——具体如 {@code warning_threshold}
     * 必须为正数这类设置项语义校验，由上层（接口/应用）在序列化前做，避免领域耦合易变 schema。</p>
     *
     * @param settingJson 设置的 JSON 字符串（{@code null} 表示清空设置）
     */
    public void updateSetting(String settingJson) {
        // trim 后空串归一为 null，避免存「空对象/空白」语义歧义。
        String normalized = (settingJson == null || settingJson.isBlank()) ? null : settingJson;
        this.setting = normalized;
    }

    /** 注销匿名化用户名前缀，配合 id 生成 ≤20 字符的不可逆占位（DB-SCHEMA §1 username length 20）。 */
    public static final String ANONYMIZED_USERNAME_PREFIX = "deleted_";

    /**
     * 账号注销时的 PII 匿名化（充血行为，F-5020 / DC-003 / DC-011）。
     *
     * <p>领域规则来源：API-ENDPOINTS §14.5 F-5020「级联删除令牌/OAuth 绑定/PII 并匿名化日志」、
     * Compliance 验收「注销后 PII 清空或匿名」。本方法只负责把<b>用户聚合自身</b>的 PII 字段做不可逆
     * 处置（其他 BC 的级联删除由注销用例编排，见 {@code com.nexa.compliance.application}）：
     * <ul>
     *   <li>用户名 → 替换为 {@code deleted_<id>} 不可逆占位（保留唯一性，满足 username 唯一索引，
     *       同时不再含原始 PII）；</li>
     *   <li>邮箱 → 清空（{@code null}）；</li>
     *   <li>展示名 / 备注 → 清空；</li>
     *   <li>密码哈希 → 替换为不可登录占位（注销后绝不能再以原密码登录）；</li>
     *   <li>OAuth 第三方 id 等绑定信息不在本聚合内（在 OAuthBinding / 各 BC），由用例级联删除；</li>
     *   <li>状态 → 置禁用，并由仓储 {@code softDelete} 写 deleted_at 使其从所有查询消失。</li>
     * </ul>
     * 计量类字段（usedQuota/requestCount/createdAt）保留——它们是不含可识别个人信息的聚合计量，
     * 计费/审计需要（计量级数据按聚合保留，分级处置规则由 compliance BC 的 DataClassification 定义）。</p>
     *
     * <p>不变量：本方法要求聚合已持久化（{@code id != null}）——注销的是已存在账号，匿名占位依赖 id 保唯一。
     * 幂等：重复匿名化无害（再次生成相同占位）。本方法<b>不</b>校验操作者权限（self-scope 鉴权在接口/应用层），
     * 只负责领域状态的不可逆处置。</p>
     *
     * @throws IllegalStateException 聚合尚未持久化（无 id），无法生成唯一匿名占位
     */
    public void anonymize() {
        if (this.id == null) {
            // 防御式：未持久化账号不存在注销语义；无 id 无法保证匿名用户名唯一，拒绝。
            throw new IllegalStateException("cannot anonymize a non-persisted user (id is null)");
        }
        // 用户名替换为 deleted_<id>：不可逆、保唯一（id 唯一）、长度受控（id 远短于 12 位）。
        this.username = Username.of(ANONYMIZED_USERNAME_PREFIX + this.id);
        this.email = null;            // PII 清空
        this.displayName = null;      // PII 清空
        this.remark = null;           // 管理员备注（可能含 PII）清空
        this.setting = null;          // 个人设置（可能含偏好类 PII）清空
        // 密码哈希置不可登录占位：以一段固定非 bcrypt 串覆盖，任何明文都无法匹配，封死注销后登录。
        this.passwordHash = "ANONYMIZED-NO-LOGIN";
        this.status = UserStatus.DISABLED; // 注销后禁用（叠加仓储软删除，双保险不可再用）。
    }

    /**
     * 由仓储在持久化重建时回填数据库主键。
     *
     * <p>仅供基础设施层在从 JPA 实体重建聚合、或保存后回填 id 时调用。</p>
     *
     * @param id 数据库自增主键
     */
    public void assignId(Long id) {
        this.id = id;
    }

    /**
     * 取出并清空一次性的注册领域事件（应用层在事务提交后取用）。
     *
     * @return 注册事件，若已被取走或非注册产生则为 {@code null}
     */
    public UserRegistered pullRegisteredEvent() {
        UserRegistered e = this.registeredEvent;
        this.registeredEvent = null;
        return e;
    }

    /**
     * 生成 4 位 aff_code。
     *
     * <p>领域规则来源：PRD AC-1 R13「生成 4 位 aff_code」、DB-SCHEMA §1「aff_code uniqueIndex」。
     * 这里只负责生成候选；<b>全局唯一性</b>由仓储/数据库唯一索引兜底（碰撞极低，本切片不做重试循环，
     * 唯一索引冲突会在保存时暴露，后续 wave 可加重试）。用 ThreadLocalRandom 避免虚拟线程下争用。</p>
     *
     * @return 4 位大写字母数字码
     */
    private static String generateAffCode() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(AFF_CODE_LENGTH);
        for (int i = 0; i < AFF_CODE_LENGTH; i++) {
            sb.append(AFF_CODE_ALPHABET[rnd.nextInt(AFF_CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * 基础设施层持久化重建专用工厂：从已存数据装配聚合（不触发注册不变量与事件）。
     *
     * <p>不含 {@code displayName}/{@code setting} 的精简重载，等价于二者为 {@code null}。
     * 保留以兼容仅关心注册/登录字段的旧调用点与单测。</p>
     *
     * @param id           主键
     * @param username     用户名
     * @param passwordHash 密码哈希
     * @param email        邮箱（可空）
     * @param role         角色
     * @param status       状态
     * @param quota        额度
     * @param affCode      邀请码
     * @param inviterId    邀请人 ID
     * @param lastLoginAt  最近登录时间
     * @return 重建的用户聚合
     */
    public static User rehydrate(Long id, Username username, String passwordHash, Email email,
                                 Role role, UserStatus status, long quota, String affCode,
                                 long inviterId, long lastLoginAt) {
        return rehydrate(id, username, passwordHash, email, role, status, quota, affCode,
                inviterId, lastLoginAt, null, null);
    }

    /**
     * 基础设施层持久化重建专用工厂（全字段）：从已存数据装配聚合（不触发注册不变量与事件）。
     *
     * @param id           主键
     * @param username     用户名
     * @param passwordHash 密码哈希
     * @param email        邮箱（可空）
     * @param role         角色
     * @param status       状态
     * @param quota        额度
     * @param affCode      邀请码
     * @param inviterId    邀请人 ID
     * @param lastLoginAt  最近登录时间
     * @param displayName  展示名（可空）
     * @param setting      个人设置 JSON（可空）
     * @return 重建的用户聚合
     */
    public static User rehydrate(Long id, Username username, String passwordHash, Email email,
                                 Role role, UserStatus status, long quota, String affCode,
                                 long inviterId, long lastLoginAt, String displayName, String setting) {
        return rehydrate(id, username, passwordHash, email, role, status, quota, affCode,
                inviterId, lastLoginAt, displayName, setting,
                DEFAULT_GROUP, null, 0L, 0L, 0L);
    }

    /**
     * 基础设施层持久化重建专用工厂（含管理端字段全集）：从已存数据装配聚合（不触发不变量与事件）。
     *
     * <p>在 12 参重载基础上补齐管理端视图需要的字段：分组（F-1013）、备注（F-1014）、
     * 已用额度 / 请求计数 / 创建时间（管理端 AdminUserView 只读回显）。</p>
     *
     * @param id           主键
     * @param username     用户名
     * @param passwordHash 密码哈希
     * @param email        邮箱（可空）
     * @param role         角色
     * @param status       状态
     * @param quota        额度
     * @param affCode      邀请码
     * @param inviterId    邀请人 ID
     * @param lastLoginAt  最近登录时间
     * @param displayName  展示名（可空）
     * @param setting      个人设置 JSON（可空）
     * @param group        分组标识（可空 → 归一为 default）
     * @param remark       管理员备注（可空）
     * @param usedQuota    已用额度
     * @param requestCount 请求计数
     * @param createdAt    创建时间 epoch 秒
     * @return 重建的用户聚合
     */
    public static User rehydrate(Long id, Username username, String passwordHash, Email email,
                                 Role role, UserStatus status, long quota, String affCode,
                                 long inviterId, long lastLoginAt, String displayName, String setting,
                                 String group, String remark, long usedQuota, long requestCount,
                                 long createdAt) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.passwordHash = passwordHash;
        u.email = email;
        u.role = role;
        u.status = status;
        u.quota = quota;
        u.affCode = affCode;
        u.inviterId = inviterId;
        u.lastLoginAt = lastLoginAt;
        u.displayName = displayName;
        u.setting = setting;
        u.group = (group == null || group.isBlank()) ? DEFAULT_GROUP : group;
        u.remark = remark;
        u.usedQuota = usedQuota;
        u.requestCount = requestCount;
        u.createdAt = createdAt;
        return u;
    }

    // ---- 只读访问器（聚合状态对外只读，不提供 setter，外部不能绕过行为方法改状态） ----

    /** @return 主键，未持久化为 null */
    public Long id() {
        return id;
    }

    /** @return 用户名值对象 */
    public Username username() {
        return username;
    }

    /** @return 密码哈希（仅基础设施层持久化用，不得下发） */
    public String passwordHash() {
        return passwordHash;
    }

    /** @return 邮箱值对象，可为 null */
    public Email email() {
        return email;
    }

    /** @return 角色 */
    public Role role() {
        return role;
    }

    /** @return 账号状态 */
    public UserStatus status() {
        return status;
    }

    /** @return 当前额度 */
    public long quota() {
        return quota;
    }

    /** @return 个人邀请码 */
    public String affCode() {
        return affCode;
    }

    /** @return 邀请人 ID（无则 0） */
    public long inviterId() {
        return inviterId;
    }

    /** @return 最近登录时间 epoch 秒 */
    public long lastLoginAt() {
        return lastLoginAt;
    }

    /** @return 展示名，可为 null */
    public String displayName() {
        return displayName;
    }

    /** @return 个人设置原始 JSON，可为 null */
    public String setting() {
        return setting;
    }

    /** @return 用户分组标识（缺省 {@code "default"}），F-1013 */
    public String group() {
        return group;
    }

    /** @return 管理员备注，可为 null（仅管理端可见），F-1014 */
    public String remark() {
        return remark;
    }

    /** @return 已用额度（管理端视图回显） */
    public long usedQuota() {
        return usedQuota;
    }

    /** @return 请求计数（管理端视图回显） */
    public long requestCount() {
        return requestCount;
    }

    /** @return 创建时间 epoch 秒（管理端视图回显） */
    public long createdAt() {
        return createdAt;
    }
}
