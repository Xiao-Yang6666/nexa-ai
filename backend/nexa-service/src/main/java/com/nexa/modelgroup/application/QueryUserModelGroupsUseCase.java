package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupAccessRepository;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 查询某用户已被授权的模型组用例（管理端，用户列表回显「该用户的私有组」）。
 *
 * <p>只读：查 USER 级授权记录命中的模型组 id → 批量取存活模型组（已被软删的组自动过滤）。用于在用户
 * 列表/编辑弹窗里展示该用户当前挂了哪些私有组，供 {@link SetUserModelGroupsUseCase} 做覆盖式 diff 的基线。</p>
 */
@Service
public class QueryUserModelGroupsUseCase {

    private final ModelGroupRepository modelGroupRepository;
    private final ModelGroupAccessRepository accessRepository;

    /**
     * @param modelGroupRepository 模型组仓储
     * @param accessRepository     授权仓储
     */
    public QueryUserModelGroupsUseCase(ModelGroupRepository modelGroupRepository,
                                       ModelGroupAccessRepository accessRepository) {
        this.modelGroupRepository = modelGroupRepository;
        this.accessRepository = accessRepository;
    }

    /**
     * 查询用户已授权模型组。
     *
     * @param userId 用户主键
     * @return 该用户 USER 级授权命中的存活模型组列表（按 id）
     */
    @Transactional(readOnly = true)
    public List<ModelGroup> listForUser(long userId) {
        List<Long> groupIds = accessRepository.findGroupIdsBySubject(AccessSubjectType.USER, userId);
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return modelGroupRepository.findByIds(groupIds);
    }
}
