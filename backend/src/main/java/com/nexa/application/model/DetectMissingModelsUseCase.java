package com.nexa.application.model;

import com.nexa.application.model.port.ChannelModelCatalog;
import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缺失模型检测用例（应用层，F-3021）。
 *
 * <p>领域规则来源：PRD ML-2。返回「被渠道引用但无 ModelMeta 的模型名」列表——用于驱动同步
 * （F-3019），确保渠道所引用模型在模型元数据表有对应记录（否则选渠/定价无法正确匹配）。</p>
 *
 * <p>实现：集合差运算（渠道引用模型 − 本地已有模型名）。两侧均大小写敏感（模型名是精确键）。
 * 失败（如仓储异常）不吞，向上抛，接口层返回 success=false+message（BACKLOG T-123）。</p>
 */
@Service
public class DetectMissingModelsUseCase {

    private final ModelMetaRepository modelRepository;
    private final ChannelModelCatalog channelCatalog;

    /**
     * @param modelRepository 模型仓储
     * @param channelCatalog  渠道模型目录端口
     */
    public DetectMissingModelsUseCase(ModelMetaRepository modelRepository,
                                       ChannelModelCatalog channelCatalog) {
        this.modelRepository = modelRepository;
        this.channelCatalog = channelCatalog;
    }

    /**
     * 检测缺失模型。
     *
     * @return 缺失模型名列表（渠道有引用但本地无元数据）
     */
    @Transactional(readOnly = true)
    public List<String> detect() {
        Set<String> referenced = new HashSet<>(channelCatalog.referencedModelNames());
        Set<String> existing = modelRepository.findAll().stream()
                .map(ModelMeta::modelName)
                .collect(Collectors.toSet());
        // 差集：被引用但不在 existing 中。
        referenced.removeAll(existing);
        return List.copyOf(referenced);
    }
}
