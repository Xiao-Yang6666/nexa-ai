package com.nexa.application.account;

import com.nexa.application.account.port.OAuthClient;
import com.nexa.domain.account.exception.InvalidCredentialException;
import com.nexa.domain.account.vo.OAuthProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 客户端注册表（按 provider 路由到对应 {@link OAuthClient} 实现）。
 *
 * <p>Spring 自动注入所有 {@link OAuthClient} bean（各 provider 实现），本注册表按
 * {@link OAuthClient#provider()} 建索引，供 {@link OAuthLoginUseCase} 据命令里的 provider
 * 选择实现（策略模式的运行时分发）。这样新增/移除 provider 只需增删实现类，用例与注册表零改动。</p>
 *
 * <p>定义在应用层（仅依赖 application 端口），不引入框架细节到 domain。</p>
 */
@Component
public class OAuthClientRegistry {

    private final Map<OAuthProvider, OAuthClient> clients = new EnumMap<>(OAuthProvider.class);

    /**
     * @param oauthClients Spring 收集到的全部 provider 客户端实现（可为空列表）
     */
    public OAuthClientRegistry(List<OAuthClient> oauthClients) {
        for (OAuthClient client : oauthClients) {
            // 同一 provider 重复注册视为配置错误，后注册的覆盖（保持确定性，避免静默歧义留待启动期暴露）。
            clients.put(client.provider(), client);
        }
    }

    /**
     * 取指定 provider 的客户端实现。
     *
     * @param provider 第三方 provider
     * @return 对应的客户端实现
     * @throws InvalidCredentialException 当该 provider 未配置实现（未启用）时
     */
    public OAuthClient resolve(OAuthProvider provider) {
        OAuthClient client = clients.get(provider);
        if (client == null) {
            // provider 合法但未配置/未启用：明确拒绝而非 NPE，接口层映射 400。
            throw new InvalidCredentialException("oauth provider not configured: " + provider.code());
        }
        return client;
    }
}
