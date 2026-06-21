package com.nexa.relay.domain.repository;

import com.nexa.relay.domain.model.PlatformModelMapping;

import java.util.List;
import java.util.Optional;

/**
 * 超管底仓映射仓储接口（domain 定接口，infrastructure 实现，backend-engineer §2.3）。
 *
 * <p>RL-7 第②步 L2 解析 + Admin CRUD + 缓存预热。</p>
 */
public interface PlatformModelMappingRepository {

    /** 按公开名 A 查启用的映射（L2 链式解析单步）。 */
    Optional<String> findUpstreamByPublicName(String publicName);

    /** 按 ID 查（Admin 编辑用）。 */
    Optional<PlatformModelMapping> findById(Long id);

    /** 全量查（Admin 列表/缓存预热）。 */
    List<PlatformModelMapping> findAll();

    void save(PlatformModelMapping mapping);
    void deleteById(Long id);
}
