package com.nexa.prefill.application;

import com.nexa.prefill.domain.exception.PrefillGroupNotFoundException;
import com.nexa.prefill.domain.repository.PrefillGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 软删除预填分组用例（F-2015，PRD 模块十五 §14；openapi {@code DELETE /api/prefill_group/{id}}）。
 *
 * <p>应用层编排：调仓储软删除（写 {@code deleted_at} 时间戳，保留历史不物理移除），命中返回、
 * id 不存在/已删除则抛 404。事务边界在此。</p>
 */
@Service
public class DeletePrefillGroupUseCase {

    private final PrefillGroupRepository repository;

    /**
     * @param repository 预填分组仓储
     */
    public DeletePrefillGroupUseCase(PrefillGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 软删除指定预填分组。
     *
     * @param id 分组主键
     * @throws PrefillGroupNotFoundException id 不存在或已被软删除（→404）
     */
    @Transactional
    public void delete(long id) {
        long now = Instant.now().getEpochSecond();
        boolean deleted = repository.softDelete(id, now);
        if (!deleted) {
            // 幂等性 vs 契约：openapi 对不存在 id 明确要求 404（非静默成功），故未命中抛 404。
            throw new PrefillGroupNotFoundException(id);
        }
    }
}
