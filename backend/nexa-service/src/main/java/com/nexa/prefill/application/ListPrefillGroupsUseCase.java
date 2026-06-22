package com.nexa.prefill.application;

import com.nexa.prefill.domain.model.PrefillGroup;
import com.nexa.prefill.domain.repository.PrefillGroupRepository;
import com.nexa.prefill.domain.vo.PrefillType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 列出预填分组用例（F-2014「下拉填充」，openapi {@code GET /api/prefill_group}）。
 *
 * <p>应用层编排：按可选 type 过滤（缺省返回全部类型）查存活分组，供前端下拉填充。type 字面量
 * 非法在此经 {@link PrefillType#fromWire} 抛 400（对齐 openapi「type 非法枚举 → 400 invalid type」）。
 * 只读用例，{@code @Transactional(readOnly = true)}。</p>
 */
@Service
public class ListPrefillGroupsUseCase {

    private final PrefillGroupRepository repository;

    /**
     * @param repository 预填分组仓储
     */
    public ListPrefillGroupsUseCase(PrefillGroupRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出预填分组（可选按 type 过滤）。
     *
     * @param typeFilter 类型字面量（null/空白 = 全部类型；非法枚举 → 400）
     * @return 匹配的分组列表（id 升序）
     * @throws com.nexa.prefill.domain.exception.InvalidPrefillParameterException type 非法枚举（→400）
     */
    @Transactional(readOnly = true)
    public List<PrefillGroup> list(String typeFilter) {
        // 缺省（null/空白）= 不过滤，返回全部；非法字面量在 fromWire 抛 400。
        PrefillType type = (typeFilter == null || typeFilter.isBlank())
                ? null
                : PrefillType.fromWire(typeFilter);
        return repository.findByType(type);
    }
}
