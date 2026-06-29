package com.nexa.domain.sensitiveverify.service;

import com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException;
import com.nexa.domain.sensitiveverify.exception.SensitiveActionVerificationFailedException;
import com.nexa.domain.sensitiveverify.vo.VerificationOutcome;

import java.util.Collection;
import java.util.Objects;

/**
 * 敏感动作二次验证领域服务（F-1038 核心裁决，纯算法、零框架依赖）。
 *
 * <p>F-1038 业务规则：通用敏感动作二次验证「密码 / 2FA(TOTP) / passkey <b>任一通过即放行</b>」。
 * 本服务汇总各因子的 {@link VerificationOutcome}，按「任一通过即放行、全不通过即拒绝、无任何因子即请求非法」
 * 三态裁决，是 F-1038 的领域规则归宿。</p>
 *
 * <p>领域服务而非聚合方法：裁决逻辑跨「多个验证因子结果」无单一聚合状态，属无状态纯函数
 * （backend-engineer §2.4 领域服务）。设计为静态纯方法便于单测（给定一组 outcome，输出确定）。
 * 不依赖任何框架/IO——因子校验本身的 IO（查库、验签）在应用层端口完成，本服务只对结果做判定，
 * 这是 DDD「domain 纯业务可单测」铁律（§2.1 依赖只向内）。</p>
 */
public final class SensitiveActionVerifier {

    private SensitiveActionVerifier() {
        // 纯静态工具，不实例化。
    }

    /**
     * 裁决一组因子验证结果是否放行敏感动作（F-1038）。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code outcomes} 为空 → {@link InvalidVerificationRequestException}（无任何凭据可验，请求非法 400）。</li>
     *   <li>存在至少一个 {@code passed=true} 的因子 → 放行（正常返回，不抛异常）。</li>
     *   <li>有因子但<b>无一</b>通过 → {@link SensitiveActionVerificationFailedException}（验证失败 403）。</li>
     * </ul>
     * 失败时刻意<b>不</b>暴露「是哪个因子未过」（防探测，安全默认）——这与 passkey/账号域\"不区分用户是否存在\"
     * 的防枚举取向一致。</p>
     *
     * @param outcomes 各因子验证结果集合（不可为 null；元素非空由 {@link VerificationOutcome} 保证）
     * @throws InvalidVerificationRequestException          未提供任何因子（无从验证）
     * @throws SensitiveActionVerificationFailedException   提供了因子但无一通过
     */
    public static void requireAnyPassed(Collection<VerificationOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        if (outcomes.isEmpty()) {
            // 无任何可识别凭据：属请求错误（缺参），而非验证失败，便于前端区分引导。
            throw new InvalidVerificationRequestException(
                    "no verification credential provided (password / totp / passkey required)");
        }
        boolean anyPassed = outcomes.stream().anyMatch(VerificationOutcome::passed);
        if (!anyPassed) {
            // 全部因子均未通过 → 拒绝放行（403）。中性提示，不指明具体失败因子。
            throw new SensitiveActionVerificationFailedException();
        }
        // 命中至少一个通过的因子：放行（正常返回）。
    }

    /**
     * 同 {@link #requireAnyPassed(Collection)} 的非抛出版本：返回布尔裁决（供调用方按需自取判定）。
     *
     * <p>注意：与抛出版语义略有差异——本方法对「无任何因子」也返回 {@code false}（不区分\"请求非法\"与\"验证失败\"）。
     * 需要区分 400/403 时用 {@link #requireAnyPassed(Collection)}。</p>
     *
     * @param outcomes 各因子验证结果集合（不可为 null）
     * @return 至少一个因子通过返回 {@code true}；空集合或全不通过返回 {@code false}
     */
    public static boolean anyPassed(Collection<VerificationOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "outcomes");
        return outcomes.stream().anyMatch(VerificationOutcome::passed);
    }
}
