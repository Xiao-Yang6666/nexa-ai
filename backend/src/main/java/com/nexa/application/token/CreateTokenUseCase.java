package com.nexa.application.token;

import com.nexa.application.token.port.TokenGroupValidationPort;
import com.nexa.domain.token.exception.InvalidTokenParameterException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;

/**
 * 创建令牌用例（应用层，F-3001 POST /api/token/）。
 *
 * <p>用例编排：① 校验绑定分组合法（套餐制：必须是该用户有权限且存在的套餐分组，杜绝孤儿分组）→
 * ② 用领域工厂 {@code Token.create}（充血校验 name/quota/group，系统安全生成明文 key、默认启用）→
 * ③ 仓储保存（回填自增 id）。归属用户取命令的 {@code actorUserId}（认证主体，杜绝伪造他人归属）。</p>
 */
@Service
public class CreateTokenUseCase {

    private final TokenRepository tokenRepository;
    private final TokenGroupValidationPort groupValidationPort;

    /**
     * @param tokenRepository     令牌仓储
     * @param groupValidationPort 分组校验端口（modelgroup BC 实现，判定 group 是否有权限且存在）
     */
    public CreateTokenUseCase(TokenRepository tokenRepository,
                              TokenGroupValidationPort groupValidationPort) {
        this.tokenRepository = tokenRepository;
        this.groupValidationPort = groupValidationPort;
    }

    /**
     * 创建令牌（F-3001）。
     *
     * <p>套餐制约束：apikey 必须绑定一个<b>有权限且存在</b>的套餐分组（公开 + 该用户被授权的私有组，
     * 且启用 + 模型集非空）。group 为空或不在可选集合 → 抛 {@link InvalidTokenParameterException}（400），
     * 杜绝绑定不存在/无权限的孤儿分组导致计价回落、闸门失效。</p>
     *
     * @param command 创建命令（归属用户 + 各字段）
     * @return 持久化后的令牌聚合（含 id；接口层裁剪为 TokenUserVO，key 脱敏）
     * @throws InvalidTokenParameterException group 为空/无权限/不存在
     */
    public Token create(CreateTokenCommand command) {
        // 套餐制闸门：绑定分组必须是该用户可选用的套餐（杜绝孤儿分组）。
        if (!groupValidationPort.canBind(command.actorUserId(), command.group())) {
            throw new InvalidTokenParameterException(
                    "invalid or unauthorized group: must bind an accessible package group");
        }
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
