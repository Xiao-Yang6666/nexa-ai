package com.nexa.infrastructure.modelgroup.validation;

import com.nexa.application.modelgroup.ResolveAccessibleModelGroupsUseCase;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.application.token.port.TokenGroupValidationPort;
import org.springframework.stereotype.Component;

/**
 * 令牌分组校验端口适配器（modelgroup BC 实现 token 域定义的 {@link TokenGroupValidationPort}）。
 *
 * <p>依赖倒置落地：token 域只依赖 {@code TokenGroupValidationPort}，本适配器复用
 * {@link ResolveAccessibleModelGroupsUseCase} 解析「该用户可选用的套餐分组」（公开 + 已授权私有，
 * 且启用 + 模型集非空），判定待绑定 group 是否在其中。tokenId 传 0（建 key 时令牌尚不存在，
 * 只按 user 维 + 公开组解析）。</p>
 */
@Component
public class TokenGroupValidationAdapter implements TokenGroupValidationPort {

    private final ResolveAccessibleModelGroupsUseCase resolveUseCase;

    /**
     * @param resolveUseCase 可访问模型组解析用例
     */
    public TokenGroupValidationAdapter(ResolveAccessibleModelGroupsUseCase resolveUseCase) {
        this.resolveUseCase = resolveUseCase;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canBind(long userId, String group) {
        if (group == null || group.isBlank()) {
            return false;
        }
        String target = group.trim();
        return resolveUseCase.resolve(userId, 0L, null).stream()
                .map(ModelGroup::code)
                .anyMatch(target::equals);
    }
}
