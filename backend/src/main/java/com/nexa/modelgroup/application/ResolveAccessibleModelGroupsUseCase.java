package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.model.ModelGroup;
import com.nexa.modelgroup.domain.repository.ModelGroupAccessRepository;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessPolicy;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型组访问解析用例（中继链路核心：解析某用户/令牌可访问哪些模型组）。
 *
 * <p>这是「灵活分组」落到运行期的关键——不再按 {@code User.group} 单值映射，而是把四个来源的可访问组
 * 合并去重：
 * <ol>
 *   <li><b>公开组</b>（{@link AccessPolicy#PUBLIC}）：所有人可见；</li>
 *   <li><b>令牌级私有授权</b>：{@code model_group_access} 中 subject=TOKEN 的记录；</li>
 *   <li><b>用户级私有授权</b>：subject=USER 的记录（覆盖该用户名下所有令牌）；</li>
 *   <li><b>按等级自动组</b>（{@link AccessPolicy#AUTO_LEVEL}）：由调用方传入「该用户等级映射到的 code 集」
 *       （等级→code 映射是易变配置，放调用方/KV，本用例不感知 Role，保持 BC 解耦）。</li>
 * </ol>
 * 仅返回<b>可选用</b>的组（启用 + 模型集非空，见 {@link ModelGroup#isSelectable()}）。结果按
 * 模型组 id 去重，保持稳定顺序（公开组优先，便于默认选组）。</p>
 */
@Service
public class ResolveAccessibleModelGroupsUseCase {

    private final ModelGroupRepository modelGroupRepository;
    private final ModelGroupAccessRepository accessRepository;

    /**
     * @param modelGroupRepository 模型组仓储
     * @param accessRepository     授权仓储
     */
    public ResolveAccessibleModelGroupsUseCase(ModelGroupRepository modelGroupRepository,
                                               ModelGroupAccessRepository accessRepository) {
        this.modelGroupRepository = modelGroupRepository;
        this.accessRepository = accessRepository;
    }

    /**
     * 解析可访问模型组。
     *
     * @param userId        归属用户主键（&gt;0）
     * @param tokenId       发起令牌主键（&gt;0）
     * @param autoLevelCodes 该用户等级自动映射到的模型组 code 集（可空；由调用方按 KV 配置 + Role 计算）
     * @return 去重后的可选用模型组列表（公开组优先，私有/自动组随后）
     */
    @Transactional(readOnly = true)
    public List<ModelGroup> resolve(long userId, long tokenId, List<String> autoLevelCodes) {
        // id → group，保序去重（LinkedHashMap 保持插入顺序：公开组先入）。
        Map<Long, ModelGroup> accessible = new LinkedHashMap<>();

        // 1) 公开组（所有人可见）。
        for (ModelGroup g : modelGroupRepository.findByAccessPolicy(AccessPolicy.PUBLIC)) {
            if (g.isSelectable()) {
                accessible.put(g.id(), g);
            }
        }

        // 2)+3) 私有授权组（令牌级 + 用户级），合并 id 后批量查。
        List<Long> grantedIds = new ArrayList<>();
        grantedIds.addAll(accessRepository.findGroupIdsBySubject(AccessSubjectType.TOKEN, tokenId));
        grantedIds.addAll(accessRepository.findGroupIdsBySubject(AccessSubjectType.USER, userId));
        if (!grantedIds.isEmpty()) {
            for (ModelGroup g : modelGroupRepository.findByIds(grantedIds)) {
                // 私有授权命中的组即便不是 PRIVATE 策略也放行（授权显式优先），但仍要求可选用。
                if (g.isSelectable()) {
                    accessible.putIfAbsent(g.id(), g);
                }
            }
        }

        // 4) 按等级自动组：调用方给定 code 集，逐个按 code 查 AUTO_LEVEL 组。
        if (autoLevelCodes != null) {
            for (String code : autoLevelCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                modelGroupRepository.findByCode(code.trim()).ifPresent(g -> {
                    if (g.accessPolicy() == AccessPolicy.AUTO_LEVEL && g.isSelectable()) {
                        accessible.putIfAbsent(g.id(), g);
                    }
                });
            }
        }

        return new ArrayList<>(accessible.values());
    }
}
