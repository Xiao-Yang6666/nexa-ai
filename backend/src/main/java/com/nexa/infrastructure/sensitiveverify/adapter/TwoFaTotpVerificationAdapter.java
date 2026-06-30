package com.nexa.infrastructure.sensitiveverify.adapter;

import com.nexa.application.sensitiveverify.port.TotpSecretProvider;
import com.nexa.application.sensitiveverify.port.TotpVerificationPort;
import com.nexa.domain.account.twofa.exception.TwoFAException;
import com.nexa.domain.account.twofa.vo.TotpSecret;
import com.nexa.domain.account.twofa.vo.TotpVerifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * TOTP 二次验证端口的 twofa 域桥接适配器（基础设施层，F-1038）。
 *
 * <p>实现 {@link TotpVerificationPort}：通过 {@link TotpSecretProvider} 取本人已启用的 TOTP 密钥，
 * 再用 twofa 域<b>已就绪</b>的纯算法领域服务 {@link TotpVerifier#verify} 校验提交口令（RFC 6238 容忍窗口）。
 * 校验算法与密钥来源解耦——算法真实可用，密钥来源由 {@link TotpSecretProvider} 抽象，其真实实现随 twofa
 * 持久化 wave 落地（backend-engineer §2.3 依赖倒置）。</p>
 *
 * <p>安全默认：用户未启用 2FA（provider 返回空）/ 密钥非法 / 口令格式或值不匹配一律返回 {@code false}，
 * 不抛异常、不区分原因（防探测）。</p>
 */
@Component
public class TwoFaTotpVerificationAdapter implements TotpVerificationPort {

    private final TotpSecretProvider secretProvider;

    /**
     * @param secretProvider 用户 TOTP 密钥来源端口（twofa 持久化 wave 提供真实实现）
     */
    public TwoFaTotpVerificationAdapter(TotpSecretProvider secretProvider) {
        this.secretProvider = secretProvider;
    }

    /**
     * {@inheritDoc}
     *
     * <p>流程：取本人启用密钥（无则判否）→ 解析 Base32（非法则判否）→ 用 {@link TotpVerifier} 校验当前时刻
     * 容忍窗口内是否命中。</p>
     */
    @Override
    public boolean verifyTotp(long userId, String code) {
        Optional<String> secretBase32 = secretProvider.findEnabledSecret(userId);
        if (secretBase32.isEmpty()) {
            // 用户未启用 2FA / 无密钥：该因子不可用，判否（不当成\"通过\"，也不报错）。
            return false;
        }
        try {
            TotpSecret secret = TotpSecret.of(secretBase32.get());
            return TotpVerifier.verify(secret, code, Instant.now().getEpochSecond());
        } catch (TwoFAException e) {
            // 落库密钥非法（理论不应发生，防御式）：判否，不向上抛。
            return false;
        }
    }
}
