package com.nexa.modelgroup.infrastructure.access;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupAccessRepository;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import com.nexa.relay.domain.port.ModelGroupAccessPort;
import org.springframework.stereotype.Component;

/**
 * 模型组访问校验端口适配器（modelgroup BC 实现 relay 域定义的 {@link ModelGroupAccessPort}，REQ-05 闸门）。
 *
 * <p>依赖倒置落地：relay 域只依赖 {@code ModelGroupAccessPort}，本适配器在 modelgroup BC 内按分组 code
 * 查模型组 + 查授权记录判定可访问性。私有组的访问授权（USER/TOKEN 两维）即 Phase1/用户视角授权写入的
 * {@code model_group_access} 记录。</p>
 *
 * <p>放行规则（与端口契约一致）：无对应存活组 / PUBLIC / AUTO_LEVEL → 放行；PRIVATE → 查 token 级或
 * user 级授权，命中才放行。AUTO_LEVEL 在闸门处放行（按等级的可见性收窄由解析用例
 * {@code ResolveAccessibleModelGroupsUseCase} 负责，闸门不重复——避免在缺等级映射时误杀）。</p>
 */
@Component
public class ModelGroupAccessAdapter implements ModelGroupAccessPort {

    private final ModelGroupRepository modelGroupRepository;
    private final ModelGroupAccessRepository accessRepository;

    /**
     * @param modelGroupRepository 模型组仓储（按 code 查组 + 读访问策略）
     * @param accessRepository     授权仓储（查 token/user 级授权）
     */
    public ModelGroupAccessAdapter(ModelGroupRepository modelGroupRepository,
                                   ModelGroupAccessRepository accessRepository) {
        this.modelGroupRepository = modelGroupRepository;
        this.accessRepository = accessRepository;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAccessible(String groupCode, long userId, Long tokenId) {
        if (groupCode == null || groupCode.isBlank()) {
            // 无分组 code：不受模型组管控，放行（回落旧行为）。
            return true;
        }
        ModelGroup group = modelGroupRepository.findByCode(groupCode.trim().toLowerCase()).orElse(null);
        if (group == null) {
            // 分组 code 无对应存活模型组：放行（该分组不被模型组体系管控）。
            return true;
        }
        if (group.accessPolicy() != AccessPolicy.PRIVATE) {
            // PUBLIC / AUTO_LEVEL：闸门放行。
            return true;
        }
        // PRIVATE：需显式授权——优先查 token 级（更细），再查 user 级（覆盖名下所有 token）。
        long groupId = group.id();
        if (tokenId != null
                && accessRepository.exists(groupId, AccessSubjectType.TOKEN, tokenId)) {
            return true;
        }
        return accessRepository.exists(groupId, AccessSubjectType.USER, userId);
    }
}
