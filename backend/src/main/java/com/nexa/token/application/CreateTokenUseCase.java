package com.nexa.token.application;

import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.repository.TokenRepository;
import org.springframework.stereotype.Service;

/**
 * 创建令牌用例（应用层，F-3001 POST /api/token/）。
 *
 * <p>用例编排：用领域工厂 {@code Token.create}（充血校验 name/quota/group，系统安全生成明文 key、
 * 默认启用）→ 仓储保存（回填自增 id）。薄编排，无领域规则（规则全在 {@link Token} 聚合，
 * backend-engineer §2.1）。归属用户取命令的 {@code actorUserId}（认证主体，杜绝伪造他人归属）。</p>
 */
@Service
public class CreateTokenUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public CreateTokenUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 创建令牌（F-3001）。
     *
     * @param command 创建命令（归属用户 + 各字段）
     * @return 持久化后的令牌聚合（含 id；接口层裁剪为 TokenUserView，key 脱敏）
     */
    public Token create(CreateTokenCommand command) {
        Token token = Token.create(
                command.actorUserId(),
                command.name(),
                command.remainQuota(),
                command.unlimitedQuota(),
                command.expiredTime(),
                command.modelLimitsEnabled(),
                command.modelLimits(),
                command.allowIps(),
                command.group(),
                command.crossGroupRetry());
        return tokenRepository.save(token);
    }
}
