package com.nexa.application.compliance.port;

/**
 * 账号注销级联处置端口（应用层定义，基础设施层实现）——F-5020 账号注销级联删除，DC-003/DC-011。
 *
 * <p>账号注销需跨多个 bounded context 处置该用户的关联数据（令牌 / OAuth 绑定 / passkey / 2FA /
 * 日志归属），这些数据分属 {@code token} / {@code account} / {@code passkey} / {@code twofa} /
 * {@code log} 等 BC。compliance 用例不直接依赖各 BC 的具体仓储（避免横向耦合 + 循环依赖），而是依赖
 * 本端口抽象「把某用户的级联数据按合规要求处置掉」这一能力，基础设施层用一个 adapter 注入各 BC 的
 * repository 来实现（依赖倒置，backend-engineer §2.3）。</p>
 *
 * <p>处置语义（按数据分级 DC-001）：
 * <ul>
 *   <li>凭证级（令牌 key / OAuth secret / 2FA secret / passkey）→ 物理删除或软删（PURGE）；</li>
 *   <li>内容级（日志正文 / 日志 user_id）→ 匿名化，解除与自然人的关联（ANONYMIZE）；</li>
 *   <li>计量级（聚合用量）→ 保留（不在本端口处置，已不含 PII）。</li>
 * </ul>
 * 实现须在<b>同一事务</b>内与用户聚合的匿名化一起提交（由用例的 {@code @Transactional} 边界统一），
 * 保证「要么全注销、要么全回滚」，杜绝半注销态（部分凭证残留可被滥用）。</p>
 */
public interface AccountDeactivationCascade {

    /**
     * 级联处置某用户的全部关联数据（注销时调用）。
     *
     * <p>实现应处置（按存在与否各自幂等，注销可重试）：
     * <ol>
     *   <li>软删/物理删该用户名下所有 API 令牌（token BC）；</li>
     *   <li>删除该用户的全部 OAuth 第三方绑定（account BC OAuthBinding）；</li>
     *   <li>删除该用户的 passkey 凭证（passkey BC）；</li>
     *   <li>删除/失效该用户的 2FA 配置与备份码（twofa BC）；</li>
     *   <li>匿名化该用户的历史日志归属（log BC：把 user_id 置为匿名占位 / 清空 username 字段），
     *       不删日志本体（保留计量审计），只解除与自然人的关联。</li>
     * </ol>
     * 调用方（用例）已在事务内先对 User 聚合做 {@code anonymize()} + 软删除；本方法负责其余 BC 的级联。</p>
     *
     * @param userId   被注销用户的 id（&gt; 0）
     * @param username 被注销用户的原用户名（用于日志归属匿名化时的精确定位，可空）
     * @return 级联处置结果摘要（各类数据处置条数），供用例记审计 / 返回不含 PII 的回执
     */
    CascadeResult purgeUserData(long userId, String username);

    /**
     * 级联处置结果摘要（不含任何 PII，仅条数统计，供审计与回执）。
     *
     * @param tokensPurged       删除的令牌数
     * @param oauthBindingsPurged 删除的 OAuth 绑定数
     * @param passkeysPurged     删除的 passkey 数
     * @param twoFaPurged        删除/失效的 2FA 配置数
     * @param logsAnonymized     匿名化的日志条数
     */
    record CascadeResult(int tokensPurged,
                         int oauthBindingsPurged,
                         int passkeysPurged,
                         int twoFaPurged,
                         long logsAnonymized) {
    }
}
