package com.nexa.application.token;

import com.nexa.domain.token.exception.TokenAccessDeniedException;
import com.nexa.domain.token.exception.TokenNotFoundException;
import com.nexa.domain.token.model.Token;
import com.nexa.domain.token.repository.TokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nexa.application.token.command.UpdateTokenCommand;

/**
 * 更新令牌用例（应用层，F-3006 PUT /api/token/，含 status_only 分支）。
 *
 * <p>用例编排（事务边界）：按 id 加载令牌 → self-scope 校验（非本人→403）→ 依 statusOnly 走
 * 充血状态迁移 {@code applyStatus} 或覆盖式 {@code update} → 保存。领域规则（状态合法性、字段校验、
 * 减法约束派生）全在 {@link Token} 聚合（backend-engineer §2.1/2.2）。越权/缺失由领域异常表达，
 * 接口层翻译为 403/404。</p>
 */
@Service
public class UpdateTokenUseCase {

    private final TokenRepository tokenRepository;

    /** @param tokenRepository 令牌仓储 */
    public UpdateTokenUseCase(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * 更新令牌（F-3006）。
     *
     * @param command 更新命令（含 statusOnly 分支与各覆盖式字段）
     * @return 更新后的令牌聚合（接口层裁剪为 TokenUserVO）
     * @throws TokenNotFoundException     令牌不存在
     * @throws TokenAccessDeniedException 越权操作他人令牌
     */
    @Transactional
    public Token update(UpdateTokenCommand command) {
        Token token = tokenRepository.findById(command.id())
                .orElseThrow(() -> new TokenNotFoundException(command.id()));
        if (!token.belongsTo(command.actorUserId())) {
            // self-scope 护栏在聚合（belongsTo），用例只负责把越权翻译成领域异常。
            throw new TokenAccessDeniedException(command.id());
        }
        if (command.statusOnly()) {
            // status_only=true：只切换启用/禁用，其余字段一律不动（避免脱敏占位回写）。
            token.applyStatus(command.status());
        } else {
            token.update(
                    command.name(),
                    command.remainQuota(),
                    command.unlimitedQuota(),
                    command.expiredTime(),
                    command.modelLimitsEnabled(),
                    command.modelLimits(),
                    command.allowIps(),
                    command.group(),
                    command.crossGroupRetry(),
                    command.endpointLimits());
        }
        Token saved = tokenRepository.save(token);
        // 写穿失效：禁用立即生效；编辑（额度/过期/分组）也清缓存避免 /v1/* 鉴权读到旧值（T12/CR-05）。
        tokenRepository.evictAuthCache(saved.key());
        return saved;
    }
}
