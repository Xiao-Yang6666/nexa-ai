package com.nexa.domain.ops.compliance;

import com.nexa.domain.ops.exception.PaymentComplianceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PaymentComplianceConfirmation 单元测试（F-4030 合规确认领域护栏）。
 *
 * <p>纯领域规则测试（零框架依赖），覆盖正常确认 + 两类异常护栏（403 非 dashboard 会话 / 400 未确认）。
 * 校验来源 API-ENDPOINTS §9.5。</p>
 */
class PaymentComplianceConfirmationTest {

    @Test
    void confirmInDashboardSessionSucceeds() {
        // 正常：dashboard 会话 + confirmed=true → 产生确认，带当前条款版本 + 署名 + IP + 时间。
        PaymentComplianceConfirmation c = PaymentComplianceConfirmation.confirm(
                true, true, "rootUser", "203.0.113.7");

        assertEquals(PaymentComplianceConfirmation.CURRENT_TERMS_VERSION, c.termsVersion());
        assertEquals("rootUser", c.confirmedBy());
        assertEquals("203.0.113.7", c.confirmedIp());
        assertTrue(c.confirmedAt() > 0, "confirmedAt 应为正 epoch 秒");
        assertNotNull(c.termsVersion());
    }

    @Test
    void nonDashboardSessionRejectedWith403() {
        // 异常：access_token / API token（非 dashboard 会话）→ 403「requires dashboard session」。
        PaymentComplianceException ex = assertThrows(PaymentComplianceException.class,
                () -> PaymentComplianceConfirmation.confirm(true, false, "rootUser", "203.0.113.7"));
        assertEquals(403, ex.httpStatus());
        assertTrue(ex.getMessage().contains("dashboard session"));
    }

    @Test
    void notConfirmedRejectedWith400() {
        // 异常：dashboard 会话但 confirmed=false → 400「请确认合规声明」。
        PaymentComplianceException ex = assertThrows(PaymentComplianceException.class,
                () -> PaymentComplianceConfirmation.confirm(false, true, "rootUser", "203.0.113.7"));
        assertEquals(400, ex.httpStatus());
        assertTrue(ex.getMessage().contains("确认"));
    }

    @Test
    void sessionGuardTakesPrecedenceOverConfirmedFlag() {
        // 边界：会话护栏先于 confirmed 校验（避免泄露「会话合法但未勾选」细节）——
        // 非 dashboard 且未确认时，优先抛 403 而非 400。
        PaymentComplianceException ex = assertThrows(PaymentComplianceException.class,
                () -> PaymentComplianceConfirmation.confirm(false, false, "rootUser", "203.0.113.7"));
        assertEquals(403, ex.httpStatus());
    }
}
