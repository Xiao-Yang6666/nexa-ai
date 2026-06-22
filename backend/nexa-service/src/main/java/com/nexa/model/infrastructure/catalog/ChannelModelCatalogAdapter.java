package com.nexa.model.infrastructure.catalog;

import com.nexa.channel.domain.model.Channel;
import com.nexa.channel.domain.repository.ChannelRepository;
import com.nexa.model.application.port.ChannelModelCatalog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠道模型目录端口的实现（基础设施层适配器，F-3021/F-3024/F-3025）。
 *
 * <p>跨 bounded context 只读适配：模型上下文经 {@link ChannelModelCatalog} 端口声明所需的渠道侧
 * 模型信息，本 adapter 用 {@code com.nexa.channel} 的领域仓储 {@link ChannelRepository} 实现，
 * 仅把 Channel 聚合「投影」成字符串/映射弱契约返回，不让 Channel 领域对象渗出端口（context 间防腐，
 * backend-engineer §2.5）。Channel↔Model 是两个上下文，这里是允许的「上下文间集成」点。</p>
 *
 * <p><b>客户视图铁律</b>：仅产出对外模型名 A（渠道 models 字段是渠道支持的模型名集），绝不暴露
 * 渠道 key / 上游模型 B / 成本（产品三道闸之一）。</p>
 */
@Component
public class ChannelModelCatalogAdapter implements ChannelModelCatalog {

    private final ChannelRepository channelRepository;

    /** @param channelRepository 渠道领域仓储（跨上下文只读集成） */
    public ChannelModelCatalogAdapter(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> referencedModelNames() {
        // 全部渠道引用的模型名去重（保序）。
        Set<String> names = new LinkedHashSet<>();
        for (Channel c : channelRepository.findAll()) {
            names.addAll(splitModels(c.models()));
        }
        return new ArrayList<>(names);
    }

    /** {@inheritDoc} */
    @Override
    public Map<Long, List<String>> channelIdToModels() {
        // 渠道 id → 支持模型名列表（F-3024 DashboardListModels）。
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Channel c : channelRepository.findAll()) {
            if (c.id() != null) {
                result.put(c.id(), splitModels(c.models()));
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> visibleModelsForGroup(String group) {
        // F-3025：聚合「该分组下启用渠道」的模型，去重合并。渠道 group 字段可为逗号分隔多分组。
        String target = (group == null || group.isBlank()) ? "default" : group.trim();
        Set<String> models = new LinkedHashSet<>();
        for (Channel c : channelRepository.findAll()) {
            if (!c.status().isEnabled()) {
                continue; // 仅启用渠道可见。
            }
            if (channelServesGroup(c.group(), target)) {
                models.addAll(splitModels(c.models()));
            }
        }
        return new ArrayList<>(models);
    }

    /**
     * 渠道 group 字段（可逗号分隔多分组）是否覆盖目标分组。
     *
     * @param channelGroup 渠道分组串
     * @param target       用户分组
     * @return 是否覆盖
     */
    private boolean channelServesGroup(String channelGroup, String target) {
        if (channelGroup == null || channelGroup.isBlank()) {
            return false;
        }
        for (String g : channelGroup.split(",")) {
            if (g.trim().equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渠道 models 字段（逗号分隔串）拆分为去空白去空模型名列表（保序）。
     *
     * @param models models 字段
     * @return 模型名列表
     */
    private List<String> splitModels(String models) {
        if (models == null || models.isBlank()) {
            return List.of();
        }
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
