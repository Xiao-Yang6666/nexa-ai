package com.nexa.ops.application.option;

import com.nexa.ops.domain.option.OptionRepository;
import org.springframework.stereotype.Service;

/**
 * 公开内容/选项读取用例（应用层，F-4027~F-4029 公开内容 + F-4035 主题暴露）。
 *
 * <p>承载匿名可读的公开内容键读取（user_agreement / privacy_policy / Notice / About /
 * HomePageContent，API-ENDPOINTS §9.4）。这些键非敏感、对外公开，按键读值，缺失返回空串
 * （契约：未设置为空串）。薄编排，无业务规则。</p>
 *
 * <p>安全：仅允许读取「公开白名单键」，防止经本用例越权读取敏感配置（如 *Secret）。白名单在
 * 接口层按端点固定键名调用，本用例提供按键读值的通用能力 + 缺失归空串语义。</p>
 */
@Service
public class GetPublicContentUseCase {

    private final OptionRepository optionRepository;

    /**
     * @param optionRepository 选项仓储
     */
    public GetPublicContentUseCase(OptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    /**
     * 读取公开内容键的值（缺失或空 → 空串）。
     *
     * @param key 公开内容键（由接口层按端点固定传入，非客户端任意指定）
     * @return 值（未设置为空串，对齐契约）
     */
    public String execute(String key) {
        return optionRepository.findByKey(key)
                .map(option -> option.value() == null ? "" : option.value())
                .orElse("");
    }
}
