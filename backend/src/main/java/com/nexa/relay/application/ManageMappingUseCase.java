package com.nexa.relay.application;

import com.nexa.relay.domain.model.PlatformModelMapping;
import com.nexa.relay.domain.model.UserModelAlias;
import com.nexa.relay.domain.repository.PlatformModelMappingRepository;
import com.nexa.relay.domain.repository.UserModelAliasRepository;
import com.nexa.relay.domain.vo.AliasScope;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 映射 CRUD 用例（Admin L2 底仓管理 + 用户 L1 别名自助管理，F-6011）。
 *
 * <p>应用层编排事务边界，越权护栏（L1 self-scope）由领域聚合 {@link UserModelAlias#belongsTo} 守护，
 * 本层负责注入调用者作用域（从鉴权主体取 userId/group）。</p>
 */
@Service
public class ManageMappingUseCase {

    private final PlatformModelMappingRepository l2Repo;
    private final UserModelAliasRepository l1Repo;

    public ManageMappingUseCase(PlatformModelMappingRepository l2Repo, UserModelAliasRepository l1Repo) {
        this.l2Repo = l2Repo;
        this.l1Repo = l1Repo;
    }

    // ---- L2 底仓（Admin/Root） ----

    public List<PlatformModelMapping> listL2Mappings() {
        return l2Repo.findAll();
    }

    public Optional<PlatformModelMapping> getL2Mapping(Long id) {
        return l2Repo.findById(id);
    }

    public void createL2Mapping(String publicName, String upstreamName, String remark) {
        long now = Instant.now().getEpochSecond();
        PlatformModelMapping mapping = PlatformModelMapping.create(publicName, upstreamName, remark, now);
        l2Repo.save(mapping);
    }

    public void deleteL2Mapping(Long id) {
        l2Repo.deleteById(id);
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
