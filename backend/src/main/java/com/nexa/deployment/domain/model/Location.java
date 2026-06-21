package com.nexa.deployment.domain.model;

import java.util.Map;

/**
 * 部署地域只读视图（io.net location，F-3050）。
 *
 * <p>领域模型，零框架依赖。承载「部署地域查询」单条记录。地域字段（国家/城市/数据驻地等）
 * 透传上游原始键值，{@code id}/{@code name} 提取为强类型便于前端展示与选择。</p>
 *
 * <p>数据驻地（地域）与合规模块 F-5019「数据出境告知与驻地标注」相关，故保留 attributes 完整透传。</p>
 *
 * @param id         地域 ID（可空，上游可能仅给名称）
 * @param name       地域名称（可空）
 * @param attributes 上游原始属性透传（只读）
 */
public record Location(String id, String name, Map<String, Object> attributes) {

    /**
     * 紧凑构造器：防御式拷贝 attributes 为不可变映射，保证值对象不可变。
     */
    public Location {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
