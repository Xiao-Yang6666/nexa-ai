package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.exception.ModelGroupNotFoundException;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 软删除模型组用例（管理端）。
 *
 * <p>写 deleted_at 时间戳软删（保留历史与配置，不物理移除）。id 不存在/已删 → 404。
 * 关联的访问授权记录保留（模型组删除后授权失效但不级联清理，简化语义；如需可后续补级联）。</p>
 */
@Service
public class DeleteModelGroupUseCase {

    private final ModelGroupRepository repository;

    /**
     * @param repository 模型组仓储
     */
    public DeleteModelGroupUseCase(ModelGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 软删除模型组。
     *
     * @param id 模型组主键
     * @throws ModelGroupNotFoundException id 不存在/已删（→404）
     */
    @Transactional
    public void delete(long id) {
        boolean deleted = repository.softDelete(id, Instant.now().getEpochSecond());
        if (!deleted) {
            throw new ModelGroupNotFoundException(id);
        }
    }
}
