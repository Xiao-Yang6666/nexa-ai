package com.nexa.application.model;

import com.nexa.application.model.port.ChannelModelCatalog;
import com.nexa.application.model.port.UserGroupQuery;
import com.nexa.domain.model.exception.InvalidModelParameterException;
import com.nexa.domain.model.repository.PublicModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 模型广场用例（应用层，F-3024/F-3025）。
 *
 * <p>F-3024 DashboardListModels：渠道→模型映射（运维诊断视图，渠道为真源，沿用 channel 投影）。
 * F-3025 用户可见模型：返回上架对外模型公开名 A 全集（与超管 {@link ManagePublicModelUseCase}
 * 写入、C 端 /api/pricing 读取同一权威源 {@link PublicModelRepository}，单一数据源）。
 * F-3022（排行榜）/F-3023（公开价格页）属横切/交叉引用（非本片 REST 端点），跨计费/用量统计
 * 上下文实现，本片仅落地 REST 端点明确归属的 F-3024/F-3025。</p>
 *
 * <p><b>单一数据源（修 r6c 超管配置与广场脱节）</b>：F-3025「用户可见模型」此前经
 * {@link ChannelModelCatalog#visibleModelsForGroup} 读 channel.models 串，与超管配的 PublicModel
 * 完全脱节——超管上/下架对外模型，用户可见列表不反映。按 F-6004 / COMPAT §5「上架即全员可用，
 * 不再用分组圈定可见模型」，可见性唯一裁决 = PublicModel.enabled，故改读 {@link PublicModelRepository}。
 * 分组查询仅用于校验用户存在（会话契约），不再据此过滤模型。</p>
 */
@Service
public class ModelSquareUseCase {

    private final ChannelModelCatalog channelCatalog;
    private final UserGroupQuery userGroupQuery;
    private final PublicModelRepository publicModelRepository;

    /**
     * @param channelCatalog        渠道模型目录端口（F-3024 dashboard 运维诊断）
     * @param userGroupQuery        用户分组查询端口（F-3025 校验用户存在）
     * @param publicModelRepository 对外模型商品目录仓储（F-3025 可见模型唯一权威源，与超管写入同源）
     */
    public ModelSquareUseCase(ChannelModelCatalog channelCatalog, UserGroupQuery userGroupQuery,
                              PublicModelRepository publicModelRepository) {
        this.channelCatalog = channelCatalog;
        this.userGroupQuery = userGroupQuery;
        this.publicModelRepository = publicModelRepository;
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
     * 用户可见模型列表（F-3025）。
     *
     * <p>返回上架对外模型公开名 A 全集（{@code PublicModel.enabled=true}），与超管
     * {@link ManagePublicModelUseCase} 写入、C 端 /api/pricing 读取同源。先校验用户存在
     * （会话契约，不信任入参 user_id 必然有效），分组本身不再过滤模型（F-6004 上架即全员可用）。</p>
     *
     * @param userId 用户 id
     * @return 该用户可见的去重模型名 A 列表（上架全集）
     * @throws InvalidModelParameterException 用户不存在
     */
    @Transactional(readOnly = true)
    public List<String> visibleModels(long userId) {
        userGroupQuery.groupOf(userId)
                .orElseThrow(() -> new InvalidModelParameterException("用户不存在"));
        return publicModelRepository.findEnabledNames();
    }
}
