package com.nexa.application.sensitiveverify;

import com.nexa.application.sensitiveverify.port.PasskeyVerificationPort;
import com.nexa.application.sensitiveverify.port.PasswordVerificationPort;
import com.nexa.application.sensitiveverify.port.TotpVerificationPort;
import com.nexa.domain.sensitiveverify.service.SensitiveActionVerifier;
import com.nexa.domain.sensitiveverify.vo.VerificationMethod;
import com.nexa.domain.sensitiveverify.vo.VerificationOutcome;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用敏感动作二次验证用例（应用服务，F-1038，sessionAuth）。
 *
 * <p>对齐 openapi {@code POST /api/verify}。编排逻辑：按命令携带的因子分发到对应端口校验，
 * 汇总各因子 {@link VerificationOutcome} 后交领域服务 {@link SensitiveActionVerifier} 裁决
 * 「任一通过即放行」。用例层只做编排（薄），裁决规则在领域服务、各因子校验在基础设施端口
 * （backend-engineer §2.1 分层、应用层不含领域规则）。</p>
 *
 * <p><b>短路优化</b>：因子按 passkey → totp → password 顺序校验，一旦命中通过即不再校验后续因子
 * （避免无谓 IO/哈希计算）。顺序仅为性能取向，不影响裁决结果（任一通过即可）。</p>
 *
 * <p>本用例同时是<b>可复用二次验证检查点</b>的内核：改密/解绑等敏感操作的应用服务可注入
 * {@link SensitiveActionGuard}（其内部委托本用例）在动作前置守卫，避免各处重复实现验证。</p>
 */
@Service
public class VerifySensitiveActionUseCase {

    private final PasswordVerificationPort passwordPort;
    private final TotpVerificationPort totpPort;
    private final PasskeyVerificationPort passkeyPort;

    /**
     * @param passwordPort 密码因子校验端口
     * @param totpPort     TOTP 因子校验端口
     * @param passkeyPort  passkey 因子校验端口
     */
    public VerifySensitiveActionUseCase(PasswordVerificationPort passwordPort,
                                        TotpVerificationPort totpPort,
                                        PasskeyVerificationPort passkeyPort) {
        this.passwordPort = passwordPort;
        this.totpPort = totpPort;
        this.passkeyPort = passkeyPort;
    }

    /**
     * 执行二次验证（F-1038）。
     *
     * <p>验证通过则正常返回（放行）；未提供任何因子抛
     * {@link com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException}（400）；
     * 提供了因子但无一通过抛
     * {@link com.nexa.domain.sensitiveverify.exception.SensitiveActionVerificationFailedException}（403）。
     * 三态裁决全部委托领域服务，保持单一规则源。</p>
     *
     * @param command 验证命令（会话用户 + 各因子凭据）
     * @throws com.nexa.domain.sensitiveverify.exception.InvalidVerificationRequestException        未提供任何因子
     * @throws com.nexa.domain.sensitiveverify.exception.SensitiveActionVerificationFailedException 因子均未通过
     */
    public void verify(VerifySensitiveActionCommand command) {
        List<VerificationOutcome> outcomes = collectOutcomes(command);
        // 裁决规则（任一通过即放行 / 全不通过即 403 / 无因子即 400）统一在领域服务，应用层不复制规则。
        SensitiveActionVerifier.requireAnyPassed(outcomes);
    }

    /**
     * 按命令携带的因子逐一校验并收集结果（短路：命中即停）。
     *
     * <p>顺序 passkey → totp → password 仅为减少无谓 IO；任何一个通过都足以放行，故命中即提前返回，
     * 不再校验剩余因子。未提交的因子不产生 outcome（不计入裁决，避免\"未提交\"被当成\"未通过\"）。</p>
     *
     * @param command 验证命令
     * @return 已校验因子的结果列表（仅含用户提交的因子；可能为空 → 由领域服务判请求非法）
     */
    private List<VerificationOutcome> collectOutcomes(VerifySensitiveActionCommand command) {
        List<VerificationOutcome> outcomes = new ArrayList<>(3);

        if (command.hasPasskey()) {
            boolean ok = passkeyPort.verifyPasskeyAssertion(command.userId(), command.passkeyAssertionJson());
            outcomes.add(new VerificationOutcome(VerificationMethod.PASSKEY, ok));
            if (ok) {
                return outcomes; // 短路：已通过，无需再算其余因子。
            }
        }
        if (command.hasTotp()) {
            boolean ok = totpPort.verifyTotp(command.userId(), command.totpCode());
            outcomes.add(new VerificationOutcome(VerificationMethod.TOTP, ok));
            if (ok) {
                return outcomes;
            }
        }
        if (command.hasPassword()) {
            boolean ok = passwordPort.verifyPassword(command.userId(), command.password());
            outcomes.add(new VerificationOutcome(VerificationMethod.PASSWORD, ok));
            if (ok) {
                return outcomes;
            }
        }
        return outcomes;
    }
}
