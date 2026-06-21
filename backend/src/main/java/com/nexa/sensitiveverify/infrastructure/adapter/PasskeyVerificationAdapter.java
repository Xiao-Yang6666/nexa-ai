package com.nexa.sensitiveverify.infrastructure.adapter;

import com.nexa.passkey.application.VerifyPasskeyUseCase;
import com.nexa.passkey.domain.exception.PasskeyException;
import com.nexa.sensitiveverify.application.port.PasskeyVerificationPort;
import org.springframework.stereotype.Component;

/**
 * Passkey 二次验证端口的 passkey 域桥接适配器（基础设施层，F-1038）。
 *
 * <p>实现 {@link PasskeyVerificationPort}，委托 passkey 限界上下文已就绪的
 * {@link VerifyPasskeyUseCase#finish}（F-1030 二次验证 finish：验签 + 归属本人校验，不签发会话）。
 * 把 passkey 域抛出的领域异常吸收为布尔结果，使本上下文的「任一因子通过即放行」裁决统一在
 * 领域服务进行（backend-engineer §2.5 跨域桥接在基础设施层）。</p>
 *
 * <p>安全默认：本人无 passkey / 验签失败 / 凭据非本人（passkey 域抛 {@link PasskeyException}）一律
 * 捕获并判否（{@code false}），不向上抛、不区分原因（防探测）。</p>
 *
 * <p>注意：本适配器只覆盖 passkey 二次验证的 <b>finish</b>（直接提交断言响应）。begin 阶段（生成
 * assertion options）仍由 passkey 域自身端点 {@code /api/user/self/passkey/verify/begin} 提供——
 * F-1038 通用入口接收的是已完成的 assertion 响应 JSON。</p>
 */
@Component
public class PasskeyVerificationAdapter implements PasskeyVerificationPort {

    private final VerifyPasskeyUseCase verifyPasskeyUseCase;

    /**
     * @param verifyPasskeyUseCase passkey 域二次验证用例（复用，F-1030）
     */
    public PasskeyVerificationAdapter(VerifyPasskeyUseCase verifyPasskeyUseCase) {
        this.verifyPasskeyUseCase = verifyPasskeyUseCase;
    }

    /**
     * {@inheritDoc}
     *
     * <p>委托 {@link VerifyPasskeyUseCase#finish}；其正常返回视为通过，抛 {@link PasskeyException}
     * （无凭据/验签失败/非本人）视为未通过。</p>
     */
    @Override
    public boolean verifyPasskeyAssertion(long userId, String assertionResponseJson) {
        try {
            verifyPasskeyUseCase.finish(userId, assertionResponseJson);
            return true;
        } catch (PasskeyException e) {
            // 验签失败/无凭据/非本人：判否。不区分具体原因（防探测），不向上抛（验证失败是预期分支）。
            return false;
        }
    }
}
