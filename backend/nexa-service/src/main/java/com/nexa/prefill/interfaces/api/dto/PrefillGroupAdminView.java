package com.nexa.prefill.interfaces.api.dto;

import com.nexa.prefill.domain.model.PrefillGroup;

import java.util.List;

/**
 * 预填分组管理视图（管理端出参，对齐 openapi {@code PrefillGroupAdminView}）。
 *
 * <p>AdminAuth 端点（F-2012~F-2015 全为管理端）出参。预填分组承载模型/标签/端点的下拉预填配置，
 * 非客户计费/路由资源，<b>不含</b>成本/利润/上游模型 B/供应商等敏感字段（产品铁律），可安全展示
 * 全部业务字段。字段名经全局 Jackson SNAKE_CASE 序列化为 {@code created_time}（对齐 openapi）。</p>
 *
 * @param id          主键
 * @param name        分组名称
 * @param type        类型字面量（model/tag/endpoint）
 * @param items       条目列表（字符串数组）
 * @param createdTime 创建时间（epoch 秒，序列化为 created_time）
 */
public record PrefillGroupAdminView(Long id, String name, String type, List<String> items, Long createdTime) {

    /**
     * 由领域聚合投影为管理视图。
     *
     * @param g 预填分组聚合
     * @return 管理视图 DTO
     */
    public static PrefillGroupAdminView from(PrefillGroup g) {
        return new PrefillGroupAdminView(
                g.id(),
                g.name(),
                g.type().wireValue(),
                g.items().values(),
                g.createdTime());
    }
}
