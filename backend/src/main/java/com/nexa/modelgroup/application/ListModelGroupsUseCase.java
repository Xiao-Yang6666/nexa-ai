package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 列出模型组用例（管理端）。
 *
 * <p>只读用例：可选按 access_policy 过滤（缺省返回全部存活组，按 id 升序）。非法策略字面量 → 400。</p>
 */
@Service
public class ListModelGroupsUseCase {

    private final ModelGroupRepository repository;

    /**
     * @param repository 模型组仓储
     */
    public ListModelGroupsUseCase(ModelGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出模型组。
     *
     * @param accessPolicy 可选访问策略字面量过滤（null/空白 = 全部）
     * @return 模型组列表
     * @throws com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException 策略非法（→400）
     */
    @Transactional(readOnly = true)
    public List<ModelGroup> list(String accessPolicy) {
        if (accessPolicy == null || accessPolicy.isBlank()) {
            return repository.findAll();
        }
        return repository.findByAccessPolicy(AccessPolicy.fromWire(accessPolicy));
    }
}
