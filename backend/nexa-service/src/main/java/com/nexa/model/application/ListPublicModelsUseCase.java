package com.nexa.model.application;

import com.nexa.model.domain.repository.PublicModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对外模型全集查询用例（应用层只读，F-3034 / ML-6「对外全集=PublicModel Enabled=true」）。
 *
 * <p>承载 {@code GET /v1/models} 列表的领域读编排：返回上架对外模型公开名 A 全集
 * （{@code enabled=true AND deleted_at IS NULL}，按 sort_order/id 升序）。
 * 下架（{@code enabled=false}）或软删（{@code deleted_at} 非空）的模型不在结果中。</p>
 *
 * <p><b>零泄露铁律（COMPAT §2 候选层 B 不可见闸）</b>：本用例仅经
 * {@link PublicModelRepository#findEnabledNames()} 取公开名 A，<b>绝不触碰 upstream_name(B)</b>
 * ——B 只存在于 PlatformModelMapping/Channel 域，本查询根本不读那些表，从源头杜绝泄露。</p>
 *
 * <p>DDD：薄应用层（{@link Transactional}(readOnly) 只读事务），无领域规则；A 全集语义由
 * {@link PublicModelRepository} 守护。OpenAI {@code /v1/models} 信封格式化在接口层 DTO 完成。</p>
 */
@Service
public class ListPublicModelsUseCase {

    private final PublicModelRepository repository;

    /** @param repository 对外模型商品目录仓储（A 全集来源） */
    public ListPublicModelsUseCase(PublicModelRepository repository) {
        this.repository = repository;
    }

    /**
     * 列出对外模型公开名 A 全集（F-3034）。
     *
     * <p>来源：{@code enabled=true AND deleted_at IS NULL} 上架全集，按 sort_order 升序、id 升序。
     * 仅返回公开名 A，不含任何 upstream_name(B)。</p>
     *
     * @return 上架对外模型公开名 A 列表（可能为空，永不为 null）
     */
    @Transactional(readOnly = true)
    public List<String> listEnabledPublicNames() {
        return repository.findEnabledNames();
    }
}
