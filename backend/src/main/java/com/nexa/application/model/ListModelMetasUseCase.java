package com.nexa.application.model;

import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型元数据列表用例（应用层，F-3013）。
 *
 * <p>用例编排：分页查询 + 总数统计 + 供应商计数 enrich（一次查全 vendor 计数避免 N+1，
 * BACKLOG T-115「enrichModels 避免 N+1 填充」）。enrich 结果作为视图侧增强字段，原始模型聚合不变。</p>
 */
@Service
public class ListModelMetasUseCase {

    private final ModelMetaRepository modelRepository;

    /** @param modelRepository 模型仓储 */
    public ListModelMetasUseCase(ModelMetaRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    /**
     * 列表查询（分页 + 供应商计数）。
     *
     * @param pagination 分页参数
     * @return 模型分页结果（含 vendor_counts）
     */
    @Transactional(readOnly = true)
    public ModelMetaPage list(Pagination pagination) {
        List<ModelMeta> items = modelRepository.findPage(pagination);
        long total = modelRepository.count();
        // 供应商计数：vendor_id（可空）→ 模型数。enrich 字段名按 openapi additionalProperties: integer。
        Map<Long, Long> raw = modelRepository.countByVendor();
        Map<String, Long> counts = new HashMap<>();
        raw.forEach((vendorId, cnt) -> counts.put(vendorId == null ? "0" : vendorId.toString(), cnt));
        return new ModelMetaPage(items, total, counts);
    }
}
