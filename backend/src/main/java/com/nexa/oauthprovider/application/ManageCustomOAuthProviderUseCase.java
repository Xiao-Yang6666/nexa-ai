package com.nexa.oauthprovider.application;

import com.nexa.oauthprovider.domain.exception.CustomOAuthProviderNotFoundException;
import com.nexa.oauthprovider.domain.model.CustomOAuthProvider;
import com.nexa.oauthprovider.domain.repository.CustomOAuthProviderRepository;
import com.nexa.oauthprovider.domain.vo.OAuthEndpoints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自定义 OAuth provider 管理用例（应用服务，F-1024 CRUD，RootAuth）。
 *
 * <p>编排 provider 配置的增删改查事务边界：创建/更新经聚合 {@link CustomOAuthProvider} 充血校验后落库；
 * 列表/删除直接走仓储。业务不变量（字段校验、端点合法、密钥保留语义）全在 domain，本类只编排
 * （backend-engineer §2.1）。对齐 openapi {@code /api/custom-oauth-provider}（GET/POST/PUT）与
 * {@code /api/custom-oauth-provider/{id}}（DELETE）。</p>
 */
@Service
public class ManageCustomOAuthProviderUseCase {

    private final CustomOAuthProviderRepository repository;

    /**
     * @param repository 自定义 provider 仓储（domain 接口）
     */
    public ManageCustomOAuthProviderUseCase(CustomOAuthProviderRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建或更新 provider（F-1024 POST/PUT）。
     *
     * <p>{@code command.id()} 为空走创建（聚合 {@code create} 校验全字段、secret 必填）；非空走更新
     * （先按 id 取出聚合，调 {@code update} 应用新配置，secret 可空=保留原值）。端点三元组由
     * {@link OAuthEndpoints} 值对象校验。name 唯一冲突由仓储实现兜底翻译。</p>
     *
     * @param command 保存命令
     * @return 持久化后的 provider（创建含新 id）
     * @throws CustomOAuthProviderNotFoundException 更新时目标 id 不存在
     */
    @Transactional
    public CustomOAuthProvider save(SaveCustomOAuthProviderCommand command) {
        OAuthEndpoints endpoints = OAuthEndpoints.of(
                command.authorizationEndpoint(),
                command.tokenEndpoint(),
                command.userinfoEndpoint());

        if (command.id() == null) {
            // —— 创建 ——（secret 必填，由聚合校验非空）
            CustomOAuthProvider created = CustomOAuthProvider.create(
                    command.name(), command.clientId(), command.clientSecret(), endpoints, command.scopes());
            // 创建命令的 enabled 默认遵循聚合（创建即启用）；若显式停用，立刻应用一次 update 落地启用态。
            if (!command.enabled()) {
                created.update(command.name(), command.clientId(), null, endpoints, command.scopes(), false);
            }
            return repository.save(created);
        }

        // —— 更新 ——（先取出再充血更新；secret 空=保留原值）
        CustomOAuthProvider existing = repository.findById(command.id())
                .orElseThrow(() -> new CustomOAuthProviderNotFoundException(String.valueOf(command.id())));
        existing.update(command.name(), command.clientId(), command.clientSecret(),
                endpoints, command.scopes(), command.enabled());
        return repository.save(existing);
    }

    /**
     * 列出全部 provider（F-1024 GET）。
     *
     * @return provider 列表（只读）
     */
    @Transactional(readOnly = true)
    public List<CustomOAuthProvider> list() {
        return repository.findAll();
    }

    /**
     * 删除 provider（F-1024 DELETE）。
     *
     * <p>幂等友好：先确认存在再删（不存在抛 404，避免静默成功掩盖误操作）。删除后用 该 provider 的
     * 已有绑定不再能登录（绑定记录残留由后续清理策略处理，本切片只删 provider 配置本身）。</p>
     *
     * @param id provider 主键
     * @throws CustomOAuthProviderNotFoundException 目标不存在
     */
    @Transactional
    public void delete(long id) {
        repository.findById(id)
                .orElseThrow(() -> new CustomOAuthProviderNotFoundException(String.valueOf(id)));
        repository.deleteById(id);
    }
}
