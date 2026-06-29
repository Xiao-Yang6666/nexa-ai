package com.nexa.infrastructure.account.pricing;

import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.relay.port.UserDiscountPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 用户专属折扣端口适配器（account BC 实现 relay 域定义的 {@link UserDiscountPort}，REQ-05 计费）。
 *
 * <p>依赖倒置落地：relay 域只依赖 {@code UserDiscountPort}，本适配器在 account BC 内按 userId 查
 * {@link com.nexa.domain.account.model.User} 取其专属折扣 {@code discountRatio}。售价侧在分组倍率之后
 * 再乘这道折扣。用户不存在（已软删/无记录）时回落 {@code 1.0}（不打折，保持旧行为、不阻断计费）。</p>
 */
@Component
public class UserDiscountAdapter implements UserDiscountPort {

    private final UserRepository userRepository;

    /**
     * @param userRepository 用户仓储（按 id 查 discountRatio）
     */
    public UserDiscountAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal discountOf(long userId) {
        return userRepository.findById(userId)
                .map(u -> u.discountRatio())
                .orElse(BigDecimal.ONE);
    }
}
