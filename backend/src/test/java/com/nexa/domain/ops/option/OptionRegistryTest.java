package com.nexa.domain.ops.option;

import com.nexa.domain.ops.exception.InvalidOptionValueException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OptionRegistry 单元测试（F-4018 选项更新校验 + F-4032 限流分组 / F-4035 主题 / F-4030 合规键禁改）。
 *
 * <p>纯领域规则测试（零框架依赖），覆盖正常 / 边界 / 异常三类。校验来源 API-ENDPOINTS §9.2。</p>
 */
class OptionRegistryTest {

    // ---- 规则 1：支付合规键禁改（§9.5，→ 应走专用确认端点） ----

    @Test
    void complianceKeyRejectedViaOptionUpdate() {
        // 合规键经选项接口写入 → 禁改（防绕过合规闸门）。
        InvalidOptionValueException ex = assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("payment_setting.compliance_confirmed", "true"));
        assertTrue(ex.getMessage().contains("合规"));
    }

    @Test
    void complianceTermsVersionKeyAlsoRejected() {
        // 前缀匹配：所有 compliance_* 子键统一禁改。
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("payment_setting.compliance_terms_version", "v2"));
    }

    // ---- 规则 2：主题白名单（F-4035 default/classic 二选一） ----

    @Test
    void themeDefaultAndClassicAccepted() {
        assertDoesNotThrow(() -> OptionRegistry.validate("theme.frontend", "default"));
        assertDoesNotThrow(() -> OptionRegistry.validate("theme.frontend", "classic"));
    }

    @Test
    void themeInvalidValueRejected() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("theme.frontend", "fancy"));
    }

    @Test
    void themeNullValueRejected() {
        // 边界：主题值为 null → 非法（白名单不含 null）。
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("theme.frontend", null));
    }

    // ---- 规则 3：限流分组结构（F-4032，JSON {group:[count,duration]}） ----

    @Test
    void rateLimitGroupValidJsonAccepted() {
        assertDoesNotThrow(() ->
                OptionRegistry.validate("ModelRequestRateLimitGroup", "{\"vip\":[10,60],\"free\":[3,60]}"));
    }

    @Test
    void rateLimitGroupEmptyObjectAcceptedAsClear() {
        // 边界：空对象 = 清空限流分组，合法。
        assertDoesNotThrow(() -> OptionRegistry.validate("ModelRequestRateLimitGroup", "{}"));
        assertDoesNotThrow(() -> OptionRegistry.validate("ModelRequestRateLimitGroup", ""));
    }

    @Test
    void rateLimitGroupNonObjectRejected() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ModelRequestRateLimitGroup", "[10,60]"));
    }

    @Test
    void rateLimitGroupPairWithWrongArityRejected() {
        // 异常：每项必须恰为 [count,duration] 两个值。
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ModelRequestRateLimitGroup", "{\"vip\":[10,60,5]}"));
    }

    @Test
    void rateLimitGroupNonIntegerRejected() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ModelRequestRateLimitGroup", "{\"vip\":[ten,60]}"));
    }

    @Test
    void rateLimitGroupNegativeRejected() {
        // 边界：count/duration 不可为负。
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ModelRequestRateLimitGroup", "{\"vip\":[-1,60]}"));
    }

    @Test
    void rateLimitGroupObjectWithoutArrayRejected() {
        // 异常：形似对象却无任何 [count,duration] 项。
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ModelRequestRateLimitGroup", "{\"vip\":10}"));
    }

    // ---- 规则 4：其他键直通（无强制结构约束） ----

    @Test
    void unconstrainedKeyPassesThrough() {
        // 敏感词 / 自动分组 / 法务文案等格式宽松键直通（F-4033/F-4034 运行时解析）。
        assertDoesNotThrow(() -> OptionRegistry.validate("SensitiveWords", "word1\nword2"));
        assertDoesNotThrow(() -> OptionRegistry.validate("UserAgreement", "<p>any html</p>"));
        assertDoesNotThrow(() -> OptionRegistry.validate("auto_group", "default,vip"));
    }

    @Test
    void blankKeyRejectedAsDirtyInput() {
        // 边界：空白键由 OptionKey 构造期拦住（脏键不进领域）。
        assertThrows(IllegalArgumentException.class,
                () -> OptionRegistry.validate("  ", "x"));
    }

    // ---- 规则 5：R3-01 系统配置面板布尔键（值须 true/false） ----

    @Test
    void booleanKeysAcceptTrueFalse() {
        assertDoesNotThrow(() -> OptionRegistry.validate("register.invite_only", "true"));
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.tls", "false"));
        assertDoesNotThrow(() -> OptionRegistry.validate("security.force_2fa", "true"));
        assertDoesNotThrow(() -> OptionRegistry.validate("advanced.maintenance_mode", "false"));
        assertDoesNotThrow(() -> OptionRegistry.validate("advanced.debug_log", "true"));
    }

    @Test
    void booleanKeyRejectsNonBooleanValue() {
        InvalidOptionValueException ex = assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("register.invite_only", "maybe"));
        assertTrue(ex.getMessage().contains("true"));
    }

    // ---- 规则 6：R3-01 非负整数键（≥0） ----

    @Test
    void nonNegativeIntKeysAcceptZeroAndPositive() {
        assertDoesNotThrow(() -> OptionRegistry.validate("ratelimit.default_rpm", "0"));
        assertDoesNotThrow(() -> OptionRegistry.validate("ratelimit.default_tpm", "40000"));
        assertDoesNotThrow(() -> OptionRegistry.validate("security.lockout_threshold", "5"));
        assertDoesNotThrow(() -> OptionRegistry.validate("advanced.log_retention_days", "90"));
    }

    @Test
    void nonNegativeIntKeyRejectsNegative() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ratelimit.default_rpm", "-1"));
    }

    @Test
    void nonNegativeIntKeyRejectsNonInteger() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("ratelimit.default_tpm", "lots"));
    }

    // ---- 规则 7：R3-01 正整数键（≥1） ----

    @Test
    void positiveIntKeysAcceptOneAndAbove() {
        assertDoesNotThrow(() -> OptionRegistry.validate("security.session_ttl_minutes", "1"));
        assertDoesNotThrow(() -> OptionRegistry.validate("advanced.request_timeout_sec", "120"));
    }

    @Test
    void positiveIntKeyRejectsZero() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("security.session_ttl_minutes", "0"));
    }

    // ---- 规则 8：R3-01 计费货币白名单（CNY/USD） ----

    @Test
    void currencyAcceptsWhitelistedValues() {
        assertDoesNotThrow(() -> OptionRegistry.validate("billing.currency", "CNY"));
        assertDoesNotThrow(() -> OptionRegistry.validate("billing.currency", "USD"));
    }

    @Test
    void currencyRejectsUnknownValue() {
        InvalidOptionValueException ex = assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("billing.currency", "EUR"));
        assertTrue(ex.getMessage().contains("货币"));
    }

    // ---- 规则 9：R3-01 SMTP 端口（1-65535） ----

    @Test
    void smtpPortAcceptsValidRange() {
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.port", "587"));
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.port", "1"));
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.port", "65535"));
    }

    @Test
    void smtpPortRejectsOutOfRange() {
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("smtp.port", "0"));
        assertThrows(InvalidOptionValueException.class,
                () -> OptionRegistry.validate("smtp.port", "70000"));
    }

    // ---- R3-01 直通键（无强约束）：站点描述 / SMTP host / 账号 / IP 白名单 ----

    @Test
    void r301PassThroughKeysAccepted() {
        assertDoesNotThrow(() -> OptionRegistry.validate("site.description", "统一多模型 API 网关"));
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.host", "smtp.mailgun.org"));
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.username", "noreply@nexa.ai"));
        assertDoesNotThrow(() -> OptionRegistry.validate("security.ip_whitelist", "10.0.0.0/8,118.24.6.0/24"));
    }

    // ---- R3-01 SMTP 密码键为敏感键（键名以 Secret 结尾→GET 列表自动剔除值） ----

    @Test
    void smtpPasswordSecretKeyIsSensitive() {
        // 复用 OptionKey.isSensitive() 后缀机制：passwordSecret 以 Secret 结尾 → 敏感。
        assertTrue(OptionKey.of("smtp.passwordSecret").isSensitive());
    }

    @Test
    void smtpPasswordSecretValuePassesValidation() {
        // 敏感键本身无领域结构约束（直通），剔除发生在 GET 列表查询层。
        assertDoesNotThrow(() -> OptionRegistry.validate("smtp.passwordSecret", "s3cr3t"));
    }
}
