package com.nexa.application.model;

import com.nexa.domain.model.model.ModelMeta;
import com.nexa.domain.model.repository.ModelMetaRepository;
import com.nexa.domain.model.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 模型元数据搜索用例（应用层，F-3014）。
 *
 * <p>用例编排：关键词 + 供应商双条件分页搜索 + 总数（BACKLOG T-116「双条件过滤生效；分页 total
 * 正确」）。搜索结果不再 enrich vendor_counts（搜索语义按命中集，counts 留空 map 表达「不适用」）。</p>
 */
@Service
public class SearchModelMetasUseCase {

    private final ModelMetaRepository modelRepository;

    /** @param modelRepository 模型仓储 */
    public SearchModelMetasUseCase(ModelMetaRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    /**
     * 双条件搜索（关键词 + 供应商）。
     *
     * @param keyword    关键词（可空白 → 不按关键词过滤）
     * @param vendorId   供应商 id（可空 → 不过滤）
     * @param pagination 分页参数
     * @return 搜索结果（vendor_counts 为空 map）
     */
    @Transactional(readOnly = true)
    public ModelMetaPage search(String keyword, Long vendorId, Pagination pagination) {
        List<ModelMeta> items = modelRepository.search(keyword, vendorId, pagination);
        long total = modelRepository.countSearch(keyword, vendorId);
        return new ModelMetaPage(items, total, Map.of());
    }
}
