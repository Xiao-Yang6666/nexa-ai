package com.nexa.application.account;

import com.nexa.application.account.port.OAuthStateStore;
import com.nexa.domain.account.vo.OAuthState;
import org.springframework.stereotype.Service;
import com.nexa.application.account.command.GenerateOAuthStateCommand;

/**
 * 生成 OAuth state 用例（应用服务，F-1015）。
 *
 * <p>编排 F-1015「生成 OAuth state（CSRF）暂存 aff」：领域生成不可预测的 state（含 aff 暂存）→
 * 经 {@link OAuthStateStore} 暂存（带 TTL）→ 返回 token 给前端，前端带它跳转第三方授权页。
 * 业务规则（token 生成、aff 归一）在领域值对象 {@link OAuthState}，本类只编排（backend-engineer §2.1）。</p>
 *
 * <p>对齐 openapi {@code GET /api/oauth/state}（返回 {@code data} 为 state 字符串）。</p>
 */
@Service
public class GenerateOAuthStateUseCase {

    private final OAuthStateStore stateStore;

    /**
     * @param stateStore state 暂存端口（基础设施层注入内存/Redis 实现）
     */
    public GenerateOAuthStateUseCase(OAuthStateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * 生成并暂存 state，返回 state token。
     *
     * @param command 生成命令（含可选 aff）
     * @return 暂存后的 state token（前端跳转授权页时回带，回调校验 CSRF 用）
     */
    public String generate(GenerateOAuthStateCommand command) {
        // 领域生成 state（高熵 token + 归一化 aff），不在应用层堆逻辑。
        OAuthState state = OAuthState.generate(command.aff());
        // 暂存供回调一次性消费比对（防 CSRF / 重放，TTL 由 store 实现负责）。
        stateStore.save(state);
        return state.token();
    }
}
