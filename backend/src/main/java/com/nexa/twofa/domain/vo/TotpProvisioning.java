package com.nexa.twofa.domain.vo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * TOTP 配置 URI 构造领域服务（otpauth:// 标准，F-1033 setup 第一步的 QR 载荷）。
 *
 * <p>{@code otpauth://totp/{issuer}:{account}?secret=...&issuer=...&algorithm=SHA1&digits=6&period=30}
 * 是 Key URI Format（Google Authenticator 规范）——验证器 App 扫码即导入。openapi
 * {@code /api/user/self/2fa/setup} 的 {@code qr_code} 字段返回本 URI 串（前端用任意 QR 库渲染图形），
 * 后端<b>不引入图像编码依赖</b>（保持依赖最小，backend-engineer §3.4）。</p>
 *
 * <p>纯静态工具：给定 issuer/account/secret 输出确定 URI，无状态、可单测。</p>
 */
public final class TotpProvisioning {

    private TotpProvisioning() {
        // 纯静态工具，不实例化。
    }

    /**
     * 构造 otpauth TOTP 配置 URI（QR 载荷）。
     *
     * @param issuer      签发方（产品名，展示在验证器 App，如 {@code Nexa}）
     * @param accountName 账户标识（用户名/邮箱，展示在验证器 App）
     * @param secret      TOTP 共享密钥
     * @return otpauth:// 配置 URI 串
     */
    public static String buildUri(String issuer, String accountName, TotpSecret secret) {
        String safeIssuer = issuer == null ? "Nexa" : issuer;
        String safeAccount = accountName == null ? "user" : accountName;
        String label = enc(safeIssuer) + ":" + enc(safeAccount);
        return "otpauth://totp/" + label
                + "?secret=" + secret.value()
                + "&issuer=" + enc(safeIssuer)
                + "&algorithm=SHA1"
                + "&digits=" + TotpVerifier.DIGITS
                + "&period=" + TotpVerifier.TIME_STEP_SECONDS;
    }

    /**
     * URL 编码（otpauth label/参数需转义空格等特殊字符）。
     *
     * @param raw 原始串
     * @return 编码串
     */
    private static String enc(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}
