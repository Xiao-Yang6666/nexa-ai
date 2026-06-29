package com.nexa.infrastructure.sensitiveverify.adapter;

import com.nexa.application.sensitiveverify.port.TotpSecretProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link TotpSecretProvider} 的<b>待就绪桩实现</b>（基础设施层，F-1038）。
 *
 * <p><b>状态声明</b>：twofa 限界上下文当前只落地了领域 VO（{@code TotpSecret}/{@code TotpVerifier} 等纯算法）
 * 与异常，<b>尚无聚合/仓储/持久化</b>（密钥存取属<b>后续 wave</b>）。在 twofa 持久化就绪前，本桩一律返回
 * {@link Optional#empty()}——语义上等价于「所有用户暂未启用 2FA」，TOTP 因子因而不可用（{@code false}），
 * 这是<b>安全默认</b>（绝不在密钥未就绪时误判通过）。</p>
 *
 * <p>TODO(twofa 持久化 wave): 用真实适配器替换——通过 twofa 域的 TwoFA 聚合仓储按 userId 取本人已启用的
 * {@code TotpSecret}，返回其 Base32 串。端口契约已按真实语义设计，替换实现不动 {@code TwoFaTotpVerificationAdapter}
 * 与应用层（backend-engineer §2.3）。当真实实现注册为 {@code @Component} 后，可移除本桩或用
 * {@code @ConditionalOnMissingBean} 让真实实现优先。</p>
 */
@Component
public class PendingTotpSecretProvider implements TotpSecretProvider {

    /**
     * {@inheritDoc}
     *
     * <p>桩：始终返回空（2FA 持久化未就绪 → 视为未启用，TOTP 因子判否）。</p>
     */
    @Override
    public Optional<String> findEnabledSecret(long userId) {
        return Optional.empty();
    }
}
