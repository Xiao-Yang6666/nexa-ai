package com.nexa.domain.account.vo;

import com.nexa.domain.account.exception.InvalidCredentialException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * OAuth state 值对象（CSRF 防护令牌 + 暂存邀请码 aff）。
 *
 * <p>领域规则来源：openapi.yaml {@code GET /api/oauth/state}（F-1015）「生成 OAuth state（CSRF）
 * 暂存 aff」。OAuth 授权码流程在跳转到第三方授权页前，先由后端生成一个不可预测的随机 state，
 * 客户端带着它跳转；第三方回调时原样带回 state，后端比对一致才接受回调，挡住 CSRF 攻击
 * （攻击者无法伪造合法 state）。同时把发起授权时携带的邀请码 {@code aff} 与 state 绑定暂存，
 * 回调建号时据此归因邀请人（PRD 邀请归因，对齐注册 R12）。</p>
 *
 * <p>不可变、按值相等（值对象铁律）。{@code token} 是高熵随机串（{@link #generate} 用
 * {@link SecureRandom} 生成 32 字节十六进制 = 64 字符），{@code aff} 为可空的邀请码暂存。
 * 本类零框架依赖，纯领域。state 的<b>存取与时效</b>由应用层端口（StateStore）负责（属 IO/基础设施），
 * 本值对象只负责「生成合法 token」与「承载 aff」。</p>
 *
 * @see com.nexa.application.account.GenerateOAuthStateUseCase
 */
public final class OAuthState {

    /** state token 随机字节数（32 字节 → 64 位十六进制串，足够抗猜测）。 */
    private static final int TOKEN_BYTES = 32;

    /** 线程安全的强随机源（CSRF token 必须不可预测）。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String token;

    /** 暂存的邀请码（可空；回调建号时据此解析邀请人）。 */
    private final String aff;

    private OAuthState(String token, String aff) {
        this.token = token;
        this.aff = aff;
    }

    /**
     * 生成新的 state（CSRF token + 暂存 aff）。
     *
     * <p>token 用 {@link SecureRandom} 生成 32 字节并十六进制编码，保证不可预测。
     * aff trim 后空白归一为 {@code null}（无邀请码），避免「空白邀请码」误归因。</p>
     *
     * @param rawAff 发起授权时携带的邀请码（可空 / 空白 → 不暂存）
     * @return 新生成的 state 值对象
     */
    public static OAuthState generate(String rawAff) {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        String aff = (rawAff == null || rawAff.isBlank()) ? null : rawAff.trim();
        return new OAuthState(token, aff);
    }

    /**
     * 以既有 token + aff 重建 state（基础设施层从 StateStore 取回时用）。
     *
     * @param token 已存在的 state token（不可空）
     * @param aff   暂存的邀请码（可空）
     * @return 重建的 state 值对象
     * @throws InvalidCredentialException 当 token 为空时（防御式：无效 state 不应被重建）
     */
    public static OAuthState rehydrate(String token, String aff) {
        if (token == null || token.isBlank()) {
            throw new InvalidCredentialException("oauth state token must not be blank");
        }
        String normalizedAff = (aff == null || aff.isBlank()) ? null : aff.trim();
        return new OAuthState(token, normalizedAff);
    }

    /** @return 不可预测的 state token（CSRF 比对用） */
    public String token() {
        return token;
    }

    /** @return 暂存的邀请码，可为 {@code null}（无邀请码） */
    public String aff() {
        return aff;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OAuthState other)) {
            return false;
        }
        return token.equals(other.token) && Objects.equals(aff, other.aff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, aff);
    }

    @Override
    public String toString() {
        // 不打印 token 全文（虽非长期凭证，仍避免日志泄露），仅标识类型 + 是否携带 aff。
        return "OAuthState{aff=" + (aff != null) + "}";
    }
}
