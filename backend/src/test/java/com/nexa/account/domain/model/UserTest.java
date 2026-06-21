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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link User} 聚合根纯领域单测（JUnit5，<b>不起 Spring</b>，验证充血行为的不变量）。
 *
 * <p>用桩 {@link PasswordHasher} 替代 BCrypt，使测试不依赖任何加密库/框架，符合 domain
 * 零框架依赖、可纯单测的 DDD 铁律（backend-engineer §2.1）。覆盖：register 初始状态与 aff_code 生成、
 * authenticate 的密码对/错/账号禁用三态。</p>
 */
@DisplayName("User 聚合根 - 注册与认证充血行为")
class UserTest {

    /**
     * 可控密码哈希桩：哈希为明文加前缀，matches 反向比对。仅用于测试，绝非真实算法。
     */
    private static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "HASH(" + rawPassword + ")";
        }

        @Override
        public boolean matches(String rawPassword, String hashed) {
            return hashed != null && hashed.equals("HASH(" + rawPassword + ")");
        }
    }

    private final PasswordHasher hasher = new FakeHasher();

    @Nested
    @DisplayName("register 工厂方法")
    class Register {

        @Test
        @DisplayName("正常注册：生成 4 位 aff_code、初始角色 common、状态启用、配额=入参、产生注册事件")
        void register_setsInitialStateAndGeneratesAffCode() {
            User user = User.register(
                    Username.of("alice"),
                    RawPassword.of("password123"),
                    Email.of("alice@example.com"),
                    hasher,
                    500L,
                    0L);

            assertNull(user.id(), "未持久化时 id 应为 null");
            assertEquals("alice", user.username().value());
            assertEquals(Role.COMMON, user.role(), "注册默认 common 角色（AC-1 R13）");
            assertEquals(UserStatus.ENABLED, user.status(), "注册即启用");
            assertEquals(500L, user.quota(), "配额应为传入的初始额度");
            assertEquals(0L, user.inviterId(), "无 aff_code 时 inviterId=0（AC-1 R12）");
            assertEquals(0L, user.lastLoginAt());

            assertNotNull(user.affCode(), "应生成 aff_code");
            assertEquals(4, user.affCode().length(), "aff_code 长度应为 4（AC-1 R13）");
            assertTrue(user.affCode().matches("[A-Z2-9]+"),
                    "aff_code 应仅含去混淆后的大写字母数字字符集");

            // 密码以哈希入聚合，明文绝不留存。
            assertEquals("HASH(password123)", user.passwordHash());
        }

        @Test
        @DisplayName("注册产生一次性 UserRegistered 事件，pull 后清空")
        void register_producesPullableEventOnce() {
            User user = User.register(
                    Username.of("bob"), RawPassword.of("password123"), null, hasher, 0L, 7L);

            UserRegistered first = user.pullRegisteredEvent();
            assertNotNull(first, "首次应取到注册事件");
            assertEquals("bob", first.username());
            assertEquals(7L, first.inviterId());

            assertNull(user.pullRegisteredEvent(), "事件为一次性，二次 pull 应为 null");
        }

        @Test
        @DisplayName("email 可空：不提供邮箱仍可注册")
        void register_allowsNullEmail() {
            User user = User.register(
                    Username.of("carol"), RawPassword.of("password123"), null, hasher, 0L, 0L);
            assertNull(user.email());
        }

        @Test
        @DisplayName("负数 inviterId 被规整为 0")
        void register_normalizesNegativeInviterIdToZero() {
            User user = User.register(
                    Username.of("dave"), RawPassword.of("password123"), null, hasher, 0L, -5L);
            assertEquals(0L, user.inviterId());
        }
    }

    @Nested
    @DisplayName("authenticate 充血校验")
    class Authenticate {

        private User enabledUser() {
            return User.register(
                    Username.of("eve"), RawPassword.of("password123"), null, hasher, 0L, 0L);
        }

        @Test
        @DisplayName("密码正确且账号启用：认证通过不抛异常")
        void authenticate_correctPasswordEnabled_passes() {
            User user = enabledUser();
            assertDoesNotThrow(() -> user.authenticate(RawPassword.of("password123"), hasher));
        }

        @Test
        @DisplayName("密码错误：抛 InvalidCredentialException（不区分用户是否存在）")
        void authenticate_wrongPassword_throwsInvalidCredential() {
            User user = enabledUser();
            assertThrows(InvalidCredentialException.class,
                    () -> user.authenticate(RawPassword.of("wrongpassword"), hasher));
        }

        @Test
        @DisplayName("密码正确但账号被禁用：抛 UserDisabledException")
        void authenticate_disabledAccount_throwsUserDisabled() {
            // 用 rehydrate 造一个 status=DISABLED 的账号（模拟封禁态）。
            User disabled = User.rehydrate(
                    1L,
                    Username.of("frank"),
                    hasher.hash("password123"),
                    null,
                    Role.COMMON,
                    UserStatus.DISABLED,
                    0L,
                    "ABCD",
                    0L,
                    0L);
            assertThrows(UserDisabledException.class,
                    () -> disabled.authenticate(RawPassword.of("password123"), hasher));
        }

        @Test
        @DisplayName("先验密码再验状态：禁用账号密码也错时优先抛凭证错误（防枚举）")
        void authenticate_disabledAndWrongPassword_throwsInvalidCredentialFirst() {
            User disabled = User.rehydrate(
                    1L, Username.of("grace"), hasher.hash("password123"), null,
                    Role.COMMON, UserStatus.DISABLED, 0L, "ABCD", 0L, 0L);
            // 密码不对时应先抛 InvalidCredential，不泄露"该账号已被封禁"。
            assertThrows(InvalidCredentialException.class,
                    () -> disabled.authenticate(RawPassword.of("wrongpassword"), hasher));
        }

        @Test
        @DisplayName("markLoggedIn 刷新最近登录时间")
        void markLoggedIn_updatesLastLoginAt() {
            User user = enabledUser();
            user.markLoggedIn(1_700_000_000L);
            assertEquals(1_700_000_000L, user.lastLoginAt());
        }
    }

    @Nested
    @DisplayName("resetPassword 充血行为（F-1007 找回密码落地）")
    class ResetPassword {

        private User userWithPassword(String rawPassword) {
            return User.register(
                    Username.of("heidi"), RawPassword.of(rawPassword), null, hasher, 0L, 0L);
        }

        @Test
        @DisplayName("重置密码：换成新哈希，旧密码立即失效、新密码可认证通过")
        void resetPassword_replacesHashAndOldPasswordFails() {
            User user = userWithPassword("oldpassword1");
            assertEquals("HASH(oldpassword1)", user.passwordHash());

            user.resetPassword(RawPassword.of("newpassword2"), hasher);

            // 新哈希落地。
            assertEquals("HASH(newpassword2)", user.passwordHash());
            // 新密码可认证通过。
            assertDoesNotThrow(() -> user.authenticate(RawPassword.of("newpassword2"), hasher));
            // 旧密码立即失效（PRD AC-3「旧哈希失效」）。
            assertThrows(InvalidCredentialException.class,
                    () -> user.authenticate(RawPassword.of("oldpassword1"), hasher));
        }

        @Test
        @DisplayName("重置密码不校验旧密码：身份已由令牌在应用层校验，聚合直接换哈希")
        void resetPassword_doesNotRequireOldPassword() {
            User user = userWithPassword("oldpassword1");
            // 重置无需提供旧密码（与 changePassword 的关键区别），直接成功。
            assertDoesNotThrow(() -> user.resetPassword(RawPassword.of("brandnew3"), hasher));
            assertEquals("HASH(brandnew3)", user.passwordHash());
        }

        @Test
        @DisplayName("哈希器返回空哈希：防御式拒绝，绝不把密码改成空")
        void resetPassword_emptyHash_throwsAndKeepsOld() {
            PasswordHasher blankHasher = new PasswordHasher() {
                @Override
                public String hash(String rawPassword) {
                    return ""; // 模拟哈希器异常返回空。
                }

                @Override
                public boolean matches(String rawPassword, String hashed) {
                    return false;
                }
            };
            User user = userWithPassword("oldpassword1");
            assertThrows(InvalidCredentialException.class,
                    () -> user.resetPassword(RawPassword.of("newpassword2"), blankHasher));
            // 防御式：异常时旧哈希保持不变，账号不会被改成空密码。
            assertEquals("HASH(oldpassword1)", user.passwordHash());
        }

        @Test
        @DisplayName("null 参数防御：newPassword / hasher 为 null 抛 NPE")
        void resetPassword_nullArgs_throwNpe() {
            User user = userWithPassword("oldpassword1");
            assertThrows(NullPointerException.class,
                    () -> user.resetPassword(null, hasher));
            assertThrows(NullPointerException.class,
                    () -> user.resetPassword(RawPassword.of("newpassword2"), null));
        }
    }

    @Nested
    @DisplayName("changePassword 充血行为（对照重置：需验旧密码）")
    class ChangePassword {

        private User userWithPassword(String rawPassword) {
            return User.register(
                    Username.of("ivan"), RawPassword.of(rawPassword), null, hasher, 0L, 0L);
        }

        @Test
        @DisplayName("旧密码正确：改成新哈希，新密码可认证")
        void changePassword_correctOldPassword_replacesHash() {
            User user = userWithPassword("oldpassword1");
            user.changePassword(RawPassword.of("oldpassword1"), RawPassword.of("newpassword2"), hasher);
            assertEquals("HASH(newpassword2)", user.passwordHash());
            assertDoesNotThrow(() -> user.authenticate(RawPassword.of("newpassword2"), hasher));
        }

        @Test
        @DisplayName("旧密码错误：抛 InvalidCredentialException，密码不变（挡住会话劫持后的静默改密）")
        void changePassword_wrongOldPassword_throwsAndKeepsOld() {
            User user = userWithPassword("oldpassword1");
            assertThrows(InvalidCredentialException.class,
                    () -> user.changePassword(RawPassword.of("wrongpassword"), RawPassword.of("newpassword2"), hasher));
            assertEquals("HASH(oldpassword1)", user.passwordHash());
        }
    }

    /**
     * 管理端角色越权护栏（PRD AC-10）的纯领域单测。
     *
     * <p>护栏铁律：操作者只能操作「角色<b>严格低于</b>自己」的目标用户；目标角色 ≥ 操作者角色一律拒绝
     * （含 admin 操作 root、admin 操作平级 admin）。提升动作还不得把目标提到 ≥ 操作者（M7）；
     * 直接指派角色不得 ≥ 操作者。这些规则全在聚合根内守护，违反抛
     * {@link RoleHierarchyViolationException}。本组覆盖正常放行 + 越权拒绝 + 提升越界三类。</p>
     */
    @Nested
    @DisplayName("ManageGuard 角色越权护栏（AC-10）")
    class ManageGuard {

        /** 造一个指定角色/状态的目标用户（rehydrate 不触发注册不变量，便于精确造态）。 */
        private User userWithRole(Role role, UserStatus status) {
            return User.rehydrate(
                    100L,
                    Username.of("target"),
                    hasher.hash("password123"),
                    null,
                    role,
                    status,
                    0L,
                    "ABCD",
                    0L,
                    0L);
        }

        @Test
        @DisplayName("admin 启用 common（目标角色严格低于操作者）：放行，状态置启用")
        void enable_adminOperatesCommon_passes() {
            User target = userWithRole(Role.COMMON, UserStatus.DISABLED);
            assertDoesNotThrow(() -> target.enable(Role.ADMIN));
            assertEquals(UserStatus.ENABLED, target.status());
        }

        @Test
        @DisplayName("admin 禁用 root（目标角色高于操作者）：抛 RoleHierarchyViolationException，状态不变")
        void disable_adminOperatesRoot_throws() {
            User root = userWithRole(Role.ROOT, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> root.disable(Role.ADMIN));
            // 越权拒绝时状态保持原样，无副作用。
            assertEquals(UserStatus.ENABLED, root.status());
        }

        @Test
        @DisplayName("admin 禁用平级 admin（目标角色 == 操作者）：抛 RoleHierarchyViolationException（不可操作同级）")
        void disable_adminOperatesPeerAdmin_throws() {
            User peer = userWithRole(Role.ADMIN, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> peer.disable(Role.ADMIN));
        }

        @Test
        @DisplayName("root 禁用 admin（目标角色严格低于操作者）：放行")
        void disable_rootOperatesAdmin_passes() {
            User admin = userWithRole(Role.ADMIN, UserStatus.ENABLED);
            assertDoesNotThrow(() -> admin.disable(Role.ROOT));
            assertEquals(UserStatus.DISABLED, admin.status());
        }

        @Test
        @DisplayName("changeRole：admin 把 common 提到 admin（新角色 == 自身）：抛越权（不可提到 >= 自身）")
        void changeRole_adminPromotesToPeer_throws() {
            User common = userWithRole(Role.COMMON, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> common.changeRole(Role.ADMIN, Role.ADMIN));
            // 越权拒绝时角色保持原样。
            assertEquals(Role.COMMON, common.role());
        }

        @Test
        @DisplayName("changeRole：root 把 common 指派为 admin（新角色严格低于自身）：放行")
        void changeRole_rootPromotesCommonToAdmin_passes() {
            User common = userWithRole(Role.COMMON, UserStatus.ENABLED);
            assertDoesNotThrow(() -> common.changeRole(Role.ADMIN, Role.ROOT));
            assertEquals(Role.ADMIN, common.role());
        }

        @Test
        @DisplayName("promote：admin 提升 common（提升后 admin == 自身）：M7 提升越界，抛越权，角色不变")
        void promote_adminPromotesCommonToPeer_throwsM7() {
            User common = userWithRole(Role.COMMON, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> common.promote(Role.ADMIN));
            assertEquals(Role.COMMON, common.role());
        }

        @Test
        @DisplayName("promote：root 提升 common（提升后 admin 严格低于 root）：放行，角色升至 admin")
        void promote_rootPromotesCommon_passes() {
            User common = userWithRole(Role.COMMON, UserStatus.ENABLED);
            assertDoesNotThrow(() -> common.promote(Role.ROOT));
            assertEquals(Role.ADMIN, common.role());
        }

        @Test
        @DisplayName("promote：admin 提升 root（目标角色高于操作者）：先过不了操作者护栏，抛越权")
        void promote_adminPromotesRoot_throwsOperatorGuard() {
            User root = userWithRole(Role.ROOT, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> root.promote(Role.ADMIN));
        }

        @Test
        @DisplayName("createByAdmin：admin 创建 admin（角色 == 自身）：抛越权（不可创建 >= 自身角色用户）")
        void createByAdmin_adminCreatesPeerAdmin_throws() {
            assertThrows(RoleHierarchyViolationException.class,
                    () -> User.createByAdmin(
                            Username.of("newadmin"),
                            RawPassword.of("password123"),
                            null,
                            null,
                            Role.ADMIN,
                            hasher,
                            0L,
                            Role.ADMIN));
        }

        @Test
        @DisplayName("createByAdmin：admin 创建 common（角色严格低于自身）：放行，新用户角色为 common")
        void createByAdmin_adminCreatesCommon_passes() {
            User created = User.createByAdmin(
                    Username.of("newcommon"),
                    RawPassword.of("password123"),
                    null,
                    null,
                    Role.COMMON,
                    hasher,
                    0L,
                    Role.ADMIN);
            assertEquals(Role.COMMON, created.role());
            assertEquals(UserStatus.ENABLED, created.status());
        }

        @Test
        @DisplayName("updateProfileByAdmin：admin 改 root 资料（目标角色高于操作者）：抛越权，资料不变")
        void updateProfile_adminOnRoot_throws() {
            User root = userWithRole(Role.ROOT, UserStatus.ENABLED);
            assertThrows(RoleHierarchyViolationException.class,
                    () -> root.updateProfileByAdmin(
                            "newname", null, "vip", 999L, "remark", UserStatus.DISABLED, Role.ADMIN));
            // 护栏先于任何字段写入：越权时资料/状态保持原样。
            assertEquals(UserStatus.ENABLED, root.status());
            assertNull(root.displayName());
        }
    }
}
