package com.nexa.infrastructure.account.security;

import com.nexa.application.account.port.TokenIssuer;
import com.nexa.domain.account.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 访问令牌签发端口 {@link TokenIssuer} 的 JWT 实现（基础设施层）。
 *
 * <p>登录成功后签发无状态 JWT，承载会话内 {@code sub=用户id} 与 {@code role} 声明
 * （API-ENDPOINTS §1.1 登录建立会话）。domain/application 仅依赖 {@link TokenIssuer} 端口，
 * 不感知 jjwt（DDD §2.3），单测可注入桩。</p>
 *
 * <p>密钥：从配置 {@code jwt.secret} 注入（HMAC-SHA256 对称密钥）；生产应通过环境变量/密管下发，
 * 切勿硬编码。密钥不足 256 位时 jjwt 会拒绝签名，故配置侧需保证 ≥ 32 字节。</p>
 *
 * <p>线程安全：{@link SecretKey} 与有效期为不可变字段，签名过程无共享可变状态，
 * 可被虚拟线程并发复用。</p>
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final SecretKey signingKey;
    private final long ttlSeconds;

    /**
     * @param secret     JWT HMAC 签名密钥（≥ 32 字节；配置 {@code jwt.secret}）
     * @param ttlSeconds 令牌有效期秒数（配置 {@code jwt.ttl-seconds}，默认 7 天）
     */
    public JwtTokenIssuer(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.ttl-seconds:604800}") long ttlSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 不吞错：弱密钥会让 HS256 签名可被暴力破解，启动期即失败比上线后泄露安全。
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes for HS256, got " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * {@inheritDoc}
     *
     * <p>声明集：{@code sub=用户id}、{@code role=角色编码}、{@code username}、标准 iat/exp。
     * 不放入任何敏感字段（密码哈希、邮箱等不入 token）。</p>
     */
    @Override
    public String issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("role", user.role().code())
                .claim("username", user.username().value())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }
}
