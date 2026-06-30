package com.nexa.interfaces.api.ops.dto;

import com.nexa.domain.ops.option.Option;

/**
 * 全站选项视图（接口层出参 DTO，F-4017 GET /api/option/ 列表项）。
 *
 * <p>对齐 API-ENDPOINTS §9.2 列表项形态 {@code { key, value }}。敏感键已在用例层剔除
 * （值不出现在列表），本视图只承载非敏感选项的 key/value。</p>
 *
 * @param key   配置键
 * @param value 配置值（可空）
 */
public record OptionVO(String key, String value) {

    /**
     * 由领域选项裁剪为视图。
     *
     * @param option 领域选项
     * @return 视图
     */
    public static OptionVO from(Option option) {
        return new OptionVO(option.keyName(), option.value());
    }
}
