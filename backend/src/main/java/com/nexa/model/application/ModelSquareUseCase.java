package com.nexa.model.application;

import com.nexa.model.application.port.ChannelModelCatalog;
import com.nexa.model.application.port.UserGroupQuery;
import com.nexa.model.domain.exception.InvalidModelParameterException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 模型广场用例（应用层，F-3024/F-3025）。
 *
 * <p>F-3024 DashboardListModels：渠道→模型映射。F-3025 用户可见模型：按用户分组聚合去重。
 * F-3022（排行榜）/F-3023（公开价格页）属横切/交叉引用（非本片 REST 端点），跨计费/用量统计
 * 上下文实现，本片仅落地 REST 端点明确归属的 F-3024/F-3025。</p>
 */
@Service
public class ModelSquareUseCase {

    private final ChannelModelCatalog channelCatalog;
    private final UserGroupQuery userGroupQuery;

    /**
     * @param channelCatalog 渠道模型目录端口
     * @param userGroupQuery 用户分组查询端口
     */
    public ModelSquareUseCase(ChannelModelCatalog channelCatalog, UserGroupQuery userGroupQuery) {
        this.channelCatalog = channelCatalog;
        this.userGroupQuery = userGroupQuery;
    }

    /**
     * 渠道→模型映射（F-3024 DashboardListModels）。
     *
     * @return channelId → models[]
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> channelToModels() {
        return channelCatalog.channelIdToModels();
    }

    /**
     * 用户可见模型列表（F-3025，按用户分组聚合去重）。
     *
     * @param userId 用户 id
     * @return 该用户可见的去重模型名列表
     * @throws InvalidModelParameterException 用户不存在
     */
    @Transactional(readOnly = true)
    public List<String> visibleModels(long userId) {
        String group = userGroupQuery.groupOf(userId)
                .orElseThrow(() -> new InvalidModelParameterException("用户不存在"));
        return channelCatalog.visibleModelsForGroup(group);
    }
}
