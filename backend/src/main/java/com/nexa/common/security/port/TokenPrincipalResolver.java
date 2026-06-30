package com.nexa.common.security.port;

import com.nexa.common.security.rbac.AuthenticatedActor;

import java.util.Optional;

/**
 * 令牌主体解析端口（应用层，DDD §2.3 接口抽象）。
 *
 * <p>抽象「把请求携带的凭据（无状态 JWT / 会话）解析成已认证操作者」这一动作，使
 * 应用层 / 鉴权用例<b>不</b>感知 jjwt、cookie、HMAC 等基础设施细节。基础设施层
 * （{@code infrastructure}）提供 JWT 实现，单测可注入桩。</p>
 *
 * <p>语义约定：
 * <ul>
 *   <li>凭据缺失（无 token/会话）→ 返回 {@link Optional#empty()}（由调用方按端点是否需鉴权决定 401/放行）；</li>
 *   <li>凭据存在但<b>非法</b>（签名错/过期/声明缺失/角色编码未知）→ 抛
 *       {@link com.nexa.common.security.exception.AuthenticationRequiredException}
 *       （视为认证失败，不静默放行——安全默认）。</li>
 * </ul>
 * 区分「缺失」与「非法」很关键：缺失是匿名（可能合法访问公开端点），非法是攻击/损坏信号。</p>
 *
 * <p>领域规则来源：openapi.yaml securitySchemes（sessionAuth/adminAuth/rootAuth 同一会话凭据 + 角色级别区分）
 * + ROLE-PERMISSION-MATRIX §6 F-5031。</p>
 */
public interface TokenPrincipalResolver {

    /**
     * 从原始凭据解析出已认证操作者。
     *
     * @param rawCredential 原始凭据串（JWT 紧凑串；调用方已从 Authorization 头或 session cookie 提取，可空）
     * @return 解析成功的操作者；凭据缺失（null/空白）返回 {@link Optional#empty()}
     * @throws com.nexa.common.security.exception.AuthenticationRequiredException 凭据存在但非法
     */
    Optional<AuthenticatedActor> resolve(String rawCredential);
}
