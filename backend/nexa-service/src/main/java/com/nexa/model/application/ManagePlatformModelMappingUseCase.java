package com.nexa.model.application;

import com.nexa.model.domain.exception.InvalidModelParameterException;
import com.nexa.model.domain.exception.PlatformModelMappingNotFoundException;
import com.nexa.model.domain.model.PlatformModelMapping;
import com.nexa.model.domain.repository.PlatformModelMappingRepository;
import com.nexa.model.domain.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 超管底仓映射 CRUD 用例（应用层，A→B，F-6002）。
 *
 * <p>本用例 <b>仅</b> admin/root 调用（接口层 {@code @RequireRole(ADMIN)} 守门）——B 不可见三道闸之
 * 应用层闸：客户路由不引用本用例。1对1 幂等键 public_name 的全局唯一在本用例 + uk_pmm_public_name
 * 索引双重保证。</p>
 */
@Service
public class ManagePlatformModelMappingUseCase {

    private final PlatformModelMappingRepository repository;

    /** @param repository 底仓映射仓储 */
    public ManagePlatformModelMappingUseCase(PlatformModelMappingRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页列表（F-6002）。
     *
     * @param pagination 分页参数
     * @return 当前页映射
     */
    @Transactional(readOnly = true)
    public List<PlatformModelMapping> list(Pagination pagination) {
        return repository.findPage(pagination);
    }

    /** @return 映射总数 */
    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    /**
     * 创建 A→B 映射（F-6002）。
     *
     * @param publicName   A
     * @param upstreamName B
     * @param enabled      是否启用（可空 → true）
     * @param remark       备注
     * @return 创建后的映射
     * @throws InvalidModelParameterException A/B 非法或 A 已存在
     */
    @Transactional
    public PlatformModelMapping create(String publicName, String upstreamName, Boolean enabled, String remark) {
        PlatformModelMapping mapping = PlatformModelMapping.create(publicName, upstreamName, enabled, remark);
        // 1对1 唯一：A 已存在则拒（uk_pmm_public_name 兜底；先查询友好提示）。
        repository.findByPublicName(mapping.publicName()).ifPresent(existing -> {
            throw new InvalidModelParameterException("A→B 映射已存在（同 public_name）");
        });
        return repository.save(mapping);
    }

    /**
     * 更新 A→B 映射（F-6002 覆盖式）。
     *
     * @param id           映射 id
     * @param upstreamName 新 B（可空 → 不改）
     * @param enabled      新启用态（可空 → 不改）
     * @param remark       新备注（可空 → 不改）
     * @return 更新后的映射
     * @throws InvalidModelParameterException             缺 id / B 非法
     * @throws PlatformModelMappingNotFoundException      不存在
     */
    @Transactional
    public PlatformModelMapping update(Long id, String upstreamName, Boolean enabled, String remark) {
        if (id == null || id <= 0) {
            throw new InvalidModelParameterException("缺少映射 ID");
        }
        PlatformModelMapping mapping = repository.findById(id)
                .orElseThrow(() -> new PlatformModelMappingNotFoundException(id));
        mapping.update(upstreamName, enabled, remark);
        return repository.save(mapping);
    }

    /**
     * 软删除 A→B 映射（F-6002，删后 A 回落直通或 404）。
     *
     * @param id 映射 id
     * @throws PlatformModelMappingNotFoundException 不存在
     */
    @Transactional
    public void delete(long id) {
        if (repository.findById(id).isEmpty()) {
            throw new PlatformModelMappingNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
