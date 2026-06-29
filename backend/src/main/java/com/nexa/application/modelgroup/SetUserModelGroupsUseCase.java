package com.nexa.application.modelgroup;

import com.nexa.domain.modelgroup.exception.ModelGroupCodeNotFoundException;
import com.nexa.domain.modelgroup.model.ModelGroup;
import com.nexa.domain.modelgroup.model.ModelGroupAccess;
import com.nexa.domain.modelgroup.repository.ModelGroupAccessRepository;
import com.nexa.domain.modelgroup.repository.ModelGroupRepository;
import com.nexa.domain.modelgroup.vo.AccessSubjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 覆盖式设置某用户的私有模型组授权用例（管理端，用户列表里「给某用户配私有组」）。
 *
 * <p>覆盖式语义（前端勾选一批组后整体提交）：以目标 code 集为准，与该用户当前 USER 级授权做 diff——
 * <b>删多余</b>（当前有、目标没有的撤销）、<b>加缺失</b>（目标有、当前没有的新增），目标已有的保持不动。
 * 整个 diff 在单事务内完成，保证一致（中途失败回滚，不出现半授权态）。</p>
 *
 * <p>校验：目标 code 集中每个 code 必须对应一个存活模型组，否则抛 {@link ModelGroupNotFoundException}
 * （→404，前端可提示哪个组无效）。空目标集 = 撤销该用户全部 USER 级授权（合法，表示"清空私有组"）。
 * 任意访问策略的组都可授权（用户决策：显式授权优先于策略，见会话约定）。</p>
 */
@Service
public class SetUserModelGroupsUseCase {

    private final ModelGroupRepository modelGroupRepository;
    private final ModelGroupAccessRepository accessRepository;

    /**
     * @param modelGroupRepository 模型组仓储（按 code 解析目标组、校验存在）
     * @param accessRepository     授权仓储
     */
    public SetUserModelGroupsUseCase(ModelGroupRepository modelGroupRepository,
                                     ModelGroupAccessRepository accessRepository) {
        this.modelGroupRepository = modelGroupRepository;
        this.accessRepository = accessRepository;
    }

    /**
     * 覆盖式设置用户的私有模型组授权。
     *
     * @param userId    用户主键
     * @param codes     目标模型组 code 集（覆盖式：最终该用户 USER 级授权 == 这些 code 对应的组；可空/空 = 清空）
     * @return 设置后该用户授权命中的模型组列表（按 id）
     * @throws ModelGroupCodeNotFoundException 某 code 无对应存活模型组（→404）
     */
    @Transactional
    public List<ModelGroup> setForUser(long userId, List<String> codes) {
        long now = Instant.now().getEpochSecond();

        // 1) 解析目标 code → 模型组，逐个校验存在（去重、跳过空白）。
        Set<Long> targetGroupIds = new HashSet<>();
        if (codes != null) {
            for (String rawCode : codes) {
                if (rawCode == null || rawCode.isBlank()) {
                    continue;
                }
                String code = rawCode.trim().toLowerCase();
                ModelGroup group = modelGroupRepository.findByCode(code)
                        .orElseThrow(() -> new ModelGroupCodeNotFoundException(code));
                targetGroupIds.add(group.id());
            }
        }

        // 2) 当前该用户 USER 级授权命中的组 id 集。
        Set<Long> currentGroupIds = new HashSet<>(
                accessRepository.findGroupIdsBySubject(AccessSubjectType.USER, userId));

        // 3) 删多余：当前有、目标没有 → 撤销。
        for (Long gid : currentGroupIds) {
            if (!targetGroupIds.contains(gid)) {
                accessRepository.delete(gid, AccessSubjectType.USER, userId);
            }
        }

        // 4) 加缺失：目标有、当前没有 → 新增授权。
        for (Long gid : targetGroupIds) {
            if (!currentGroupIds.contains(gid)) {
                ModelGroupAccess access = ModelGroupAccess.grant(gid, AccessSubjectType.USER, userId, now);
                accessRepository.save(access);
            }
        }

        // 5) 回显设置后的结果（目标集对应的存活组）。
        return targetGroupIds.isEmpty()
                ? List.of()
                : modelGroupRepository.findByIds(List.copyOf(targetGroupIds));
    }
}
