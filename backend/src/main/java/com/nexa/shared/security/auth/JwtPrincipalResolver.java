package com.nexa.shared.security.auth;

import com.nexa.shared.security.port.TokenPrincipalResolver;
import com.nexa.shared.security.exception.AuthenticationRequiredException;
import com.nexa.shared.security.rbac.ActorRole;
import com.nexa.shared.security.rbac.AuthenticatedActor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 令牌主体解析端口 {@link TokenPrincipalResolver} 的 JWT 实现（基础设施层）。
 *
 * <p>解析登录时由 {@code com.nexa.infrastructure.account.security.JwtTokenIssuer} 签发的无状态 JWT，
 * 还原操作者身份（{@code sub=用户id}、{@code role=角色编码}、{@code username}）为
 * {@link AuthenticatedActor}，供三级鉴权过滤器注入 SecurityContext。jjwt 细节只活在本类，
 * domain/application 仅依赖 {@link TokenPrincipalResolver} 端口（DDD §2.3）。</p>
 *
 * <p>密钥一致性：必须与签发方共用 {@code jwt.secret}（HMAC-SHA256 对称密钥），否则验签失败。
 * 同样校验 ≥ 32 字节（HS256 安全下限），不足则启动期失败而非上线后被破解。</p>
 *
 * <p>安全：验签失败/过期/声明缺失/角色编码未知一律视为<b>认证失败</b>抛
 * {@link AuthenticationRequiredException}（不静默放行、不回显失败细节）。线程安全：
 * {@link SecretKey} 不可变，解析无共享可变状态，可并发复用。</p>
 */
@Component
public class JwtPrincipalResolver implements TokenPrincipalResolver {

    private final SecretKey verificationKey;

    /**
     * @param secret JWT HMAC 验签密钥（≥ 32 字节，配置 {@code jwt.secret}，须与签发方一致）
     * @throws IllegalStateException 密钥不足 32 字节（HS256 安全下限）
     */
    public JwtPrincipalResolver(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // 与签发方同一硬约束：弱密钥下 HS256 可被暴力破解，启动期即失败。
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes for HS256, got " + keyBytes.length);
        }
        this.verificationKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AuthenticatedActor> resolve(String rawCredential) {
        // 凭据缺失 = 匿名（可能合法访问公开端点），返回 empty 让调用方按端点要求决定 401/放行。
        if (rawCredential == null || rawCredential.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(verificationKey)
                    .build()
                    .parseSignedClaims(rawCredential)
                    .getPayload();
            return Optional.of(toActor(claims));
        } catch (JwtException | IllegalArgumentException e) {
            // 凭据存在但非法（签名错/过期/格式坏）：视为攻击/损坏信号，认证失败而非放行。
            // wrap 保留错误链便于服务端排障；对客户的 message 仍为稳定通用提示（不泄露细节）。
            throw new AuthenticationRequiredException("invalid or expired credential");
        }
    }

    /**
     * 把 JWT 声明集映射为认证主体。
     *
     * <p>读取 {@code sub}（用户 id）、{@code role}（角色编码）、{@code username}。任一关键声明缺失/格式非法
     * 视为令牌损坏 → 认证失败。角色编码未知由 {@link ActorRole#fromCode} 抛出，统一兜成认证失败
     * （脏角色不得被静默当作某角色）。</p>
     *
     * @param claims 已验签的声明集
     * @return 认证主体
     */
    private AuthenticatedActor toActor(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthenticationRequiredException("invalid credential: missing subject");
        }
        long userId;
        try {
            userId = Long.parseLong(sub.trim());
        } catch (NumberFormatException e) {
            throw new AuthenticationRequiredException("invalid credential: malformed subject");
        }

        Integer roleCode = claims.get("role", Integer.class);
        if (roleCode == null) {
            throw new AuthenticationRequiredException("invalid credential: missing role claim");
        }
        ActorRole role;
        try {
            role = ActorRole.fromCode(roleCode);
        } catch (IllegalArgumentException e) {
            // 未知角色编码 = 脏令牌，不放行。
            throw new AuthenticationRequiredException("invalid credential: unknown role");
        }

        String username = claims.get("username", String.class);

        try {
            return new AuthenticatedActor(userId, username, role);
        } catch (IllegalArgumentException e) {
            // 非正 userId 等聚合根不变量违反 → 认证失败。
            throw new AuthenticationRequiredException("invalid credential: malformed principal");
        }
    }
}
