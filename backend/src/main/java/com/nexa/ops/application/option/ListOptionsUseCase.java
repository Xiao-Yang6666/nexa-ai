package com.nexa.ops.application.option;

import com.nexa.ops.domain.option.Option;
import com.nexa.ops.domain.option.OptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全站选项列表查询用例（应用层，F-4017 GET /api/option/）。
 *
 * <p>编排：全量读选项 → 剔除敏感键（值不下发客户视图）。敏感判定收敛在领域值对象
 * {@link Option#isSensitive()}（以 Token/Secret/Key/secret/api_key 结尾），用例只做过滤编排。</p>
 *
 * <p>安全铁律：敏感键（API 密钥/OAuth secret 等）的值绝不进列表响应，即便请求方是 root
 * （API-ENDPOINTS §9.2「自动剔除以 ... 结尾的键」）。{@code CompletionRatioMeta} 等派生项由
 * 接口层视图按需追加，不在此处。</p>
 */
@Service
public class ListOptionsUseCase {

    private final OptionRepository optionRepository;

    /**
     * @param optionRepository 选项仓储
     */
    public ListOptionsUseCase(OptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    /**
     * 查询全站选项（已剔除敏感键）。
     *
     * @return 非敏感选项列表
     */
    public List<Option> execute() {
        // 领域值对象自带敏感判定，过滤掉敏感键（值不外泄）。
        return optionRepository.findAll().stream()
                .filter(option -> !option.isSensitive())
                .toList();
    }
}
