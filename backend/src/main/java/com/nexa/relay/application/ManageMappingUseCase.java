package com.nexa.relay.application;

import com.nexa.relay.domain.model.UserModelAlias;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.AliasScope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 用户层模型别名 CRUD 用例（L1 别名自助管理，F-6011）。
 *
 * <p>应用层编排事务边界，越权护栏（L1 self-scope）由领域聚合 {@link UserModelAlias#belongsTo} 守护，
 * 本层负责注入调用者作用域（从鉴权主体取 userId/group）。</p>
 *
 * <p>L2 全局底仓映射（A→B）已废弃——A→B 下沉为渠道级（{@code Channel.modelMapping}，由
 * {@code RelayForwardUseCase} 选渠后解析），不再有全局 {@code platform_model_mappings} 表与管理端点。</p>
 */
@Service
public class ManageMappingUseCase {

    private final UserModelAliasRepository l1Repo;

    public ManageMappingUseCase(UserModelAliasRepository l1Repo) {
        this.l1Repo = l1Repo;
    }

    // ---- L1 别名（用户自助） ----

    public List<UserModelAlias> listL1Aliases(AliasScope scope) {
        return l1Repo.findByScope(scope);
    }

    public void createL1Alias(AliasScope scope, String alias, String target) {
        long now = Instant.now().getEpochSecond();
        UserModelAlias a = UserModelAlias.create(scope, alias, target, now);
        l1Repo.save(a);
    }

    public void deleteL1Alias(Long id) {
        l1Repo.deleteById(id);
    }
}
